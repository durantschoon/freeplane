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
}
