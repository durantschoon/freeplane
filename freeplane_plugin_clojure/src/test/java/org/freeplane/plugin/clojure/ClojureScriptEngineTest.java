package org.freeplane.plugin.clojure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import org.junit.Test;

import clojure.lang.Namespace;
import clojure.lang.Symbol;

public class ClojureScriptEngineTest {

    private final ClojureScriptEngine engine = new ClojureScriptEngine();
    private final ScriptContext context = newContext();

    private static ScriptContext newContext() {
        SimpleScriptContext context = new SimpleScriptContext();
        context.setBindings(new SimpleBindings(), ScriptContext.ENGINE_SCOPE);
        context.setBindings(new SimpleBindings(), ScriptContext.GLOBAL_SCOPE);
        return context;
    }

    @Test
    public void evaluatesArithmetic() throws Exception {
        assertThat(engine.eval("(+ 1 2)", context)).isEqualTo(3L);
    }

    @Test
    public void engineScopeNodeAndCAreVisible() throws Exception {
        Object nodeObject = new Object();
        context.setAttribute("node", nodeObject, ScriptContext.ENGINE_SCOPE);
        context.setAttribute("c", 7, ScriptContext.ENGINE_SCOPE);
        assertThat(engine.eval("node", context)).isSameAs(nodeObject);
        assertThat(engine.eval("(str c)", context)).isEqualTo("7");
    }

    @Test
    public void staticPropertyStyleNameIsVisible() throws Exception {
        context.setAttribute("textUtils", "helper", ScriptContext.ENGINE_SCOPE);
        assertThat(engine.eval("textUtils", context)).isEqualTo("helper");
    }

    @Test
    public void globalScopeVisibleAndEngineScopeWins() throws Exception {
        context.setAttribute("onlyGlobal", "g", ScriptContext.GLOBAL_SCOPE);
        context.setAttribute("both", "global", ScriptContext.GLOBAL_SCOPE);
        context.setAttribute("both", "engine", ScriptContext.ENGINE_SCOPE);
        assertThat(engine.eval("onlyGlobal", context)).isEqualTo("g");
        assertThat(engine.eval("both", context)).isEqualTo("engine");
    }

    @Test
    public void unbindableNameIsIgnored() throws Exception {
        context.setAttribute("a.b", 1, ScriptContext.ENGINE_SCOPE);
        assertThat(engine.eval("(+ 1 1)", context)).isEqualTo(2L);
    }

    @Test
    public void trailingLineCommentEvaluates() throws Exception {
        assertThat(engine.eval("(+ 1 2) ; trailing comment", context)).isEqualTo(3L);
    }

    @Test
    public void multiFormScriptReturnsLastValue() throws Exception {
        assertThat(engine.eval("1 2 (+ 1 2)", context)).isEqualTo(3L);
    }

    @Test
    public void defInternsIntoUserNamespaceNotClojureCore() throws Exception {
        assertThat(engine.eval("(def stage02-probe 42) stage02-probe", context)).isEqualTo(42L);
        Namespace user = Namespace.find(Symbol.intern("user"));
        Namespace core = Namespace.find(Symbol.intern("clojure.core"));
        assertThat(user.findInternedVar(Symbol.intern("stage02-probe"))).isNotNull();
        assertThat(core.getMapping(Symbol.intern("stage02-probe"))).isNull();
    }

    @Test
    public void unbalancedScriptThrowsScriptExceptionWithCause() {
        assertThatThrownBy(() -> engine.eval("(+ 1", context))
            .isInstanceOf(ScriptException.class)
            .satisfies(e -> assertThat(e.getCause()).isNotNull());
    }

    @Test
    public void compiledScriptEvaluatesAndSeesLateBinding() throws Exception {
        assertThat(engine.compile("(+ 40 2)").eval(context)).isEqualTo(42L);
        CompiledScript compiled = engine.compile("late");
        context.setAttribute("late", "now", ScriptContext.ENGINE_SCOPE);
        assertThat(compiled.eval(context)).isEqualTo("now");
    }

    @Test
    public void spiRegistrationFindsEngineByExtension() {
        ScriptEngine found = new ScriptEngineManager().getEngineByExtension("clj");
        assertThat(found).isInstanceOf(ClojureScriptEngine.class);
    }

    @Test
    public void extraClosingParenDoesNotDropLaterForms() {
        assertThatThrownBy(() -> engine.eval("(+ 1 2)) (def stage03-dropped 1)", context))
            .isInstanceOf(ScriptException.class);
        assertThat(Namespace.findOrCreate(Symbol.intern("user")).findInternedVar(Symbol.intern("stage03-dropped")))
            .isNull();
    }

    @Test
    public void compilerErrorReportsScriptLine() {
        assertThatThrownBy(() -> engine.eval("1\n2\nstage03-undefined-symbol", context))
            .isInstanceOf(ScriptException.class)
            .satisfies(e -> assertThat(((ScriptException) e).getLineNumber()).isEqualTo(3));
    }

    @Test
    public void emptyScriptEvaluatesToNull() throws Exception {
        assertThat(engine.eval("", context)).isNull();
    }

    @Test
    public void vectorResultHasNoInventedMetadata() throws Exception {
        assertThat(((clojure.lang.IMeta) engine.eval("[1 2]", context)).meta()).isNull();
    }

    @Test
    public void mapResultHasNoInventedMetadata() throws Exception {
        assertThat(((clojure.lang.IMeta) engine.eval("{:a 1}", context)).meta()).isNull();
    }

    @Test
    public void userMetadataIsPreservedExactly() throws Exception {
        Object result = engine.eval("^:keep [1]", context);
        assertThat(clojure.lang.RT.meta(result).toString()).isEqualTo("{:keep true}");
    }

    @Test
    public void readerEvalKeepsScriptExceptionLine() {
        assertThatThrownBy(() -> engine.eval(new java.io.StringReader("1\n2\n)"), context))
                .isInstanceOf(ScriptException.class)
                .satisfies(e -> assertThat(((ScriptException) e).getLineNumber()).isEqualTo(3));
    }

    @Test
    public void readerCompileKeepsScriptExceptionLine() {
        assertThatThrownBy(() -> engine.compile(new java.io.StringReader("(+ 1")))
                .isInstanceOf(ScriptException.class)
                .satisfies(e -> assertThat(((ScriptException) e).getLineNumber()).isGreaterThan(0));
    }

    @Test
    public void userNamespaceIsSharedAndReferencesClojureCore() throws Exception {
        assertThat(ClojureUserNamespace.get()).isSameAs(ClojureUserNamespace.get());
        assertThat(ClojureUserNamespace.get().getName().getName()).isEqualTo("user");
        assertThat(ClojureUserNamespace.get().getMapping(Symbol.intern("inc")).toString())
                .isEqualTo("#'clojure.core/inc");
    }

    @Test
    public void fpAliasIsAvailableToScripts() throws Exception {
        org.freeplane.api.Node node = org.mockito.Mockito.mock(org.freeplane.api.Node.class);
        org.mockito.Mockito.when(node.getText()).thenReturn("t");
        context.setAttribute("node", node, ScriptContext.ENGINE_SCOPE);
        assertThat(engine.eval("(fp/text node)", context)).isEqualTo("t");
    }

    @Test
    public void printlnWritesToContextWriter() throws Exception {
        java.io.StringWriter out = new java.io.StringWriter();
        context.setWriter(out);
        engine.eval("(println \"hello\")", context);
        assertThat(out.toString()).isEqualTo("hello" + System.lineSeparator());
    }

    @Test
    public void errWritesToContextErrorWriter() throws Exception {
        java.io.StringWriter err = new java.io.StringWriter();
        context.setErrorWriter(err);
        engine.eval("(binding [*out* *err*] (println \"oops\"))", context);
        assertThat(err.toString()).isEqualTo("oops" + System.lineSeparator());
    }

    @Test
    public void outputIsFlushedWhenScriptReturnsBeforeNewline() throws Exception {
        java.io.StringWriter out = new java.io.StringWriter();
        context.setWriter(out);
        assertThat(engine.eval("(print \"a\") 1", context)).isEqualTo(1L);
        assertThat(out.toString()).isEqualTo("a");
    }

    @Test
    public void scriptMayDefineVarNamedLikeHelperFunction() throws Exception {
        assertThat(engine.eval("(def children 1) children", context)).isEqualTo(1L);
        assertThat(engine.eval("(some? (resolve 'fp/children))", context)).isEqualTo(Boolean.TRUE);
    }
}
