package org.freeplane.plugin.clojure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import org.junit.Test;

import clojure.lang.Atom;

public class ClojureCompiledScriptTest {

    private final ClojureScriptEngine engine = new ClojureScriptEngine();
    private final ScriptContext context = newContext();

    private static ScriptContext newContext() {
        SimpleScriptContext context = new SimpleScriptContext();
        context.setBindings(new SimpleBindings(), ScriptContext.ENGINE_SCOPE);
        context.setBindings(new SimpleBindings(), ScriptContext.GLOBAL_SCOPE);
        return context;
    }

    private ClojureCompiledScript compile(String script) throws ScriptException {
        return (ClojureCompiledScript) engine.compile(script);
    }

    @Test
    public void syntaxErrorThrowsFromCompileWithLine() {
        assertThatThrownBy(() -> engine.compile("(+ 1"))
                .isInstanceOf(ScriptException.class)
                .satisfies(e -> assertThat(((ScriptException) e).getLineNumber()).isGreaterThan(0));
    }

    @Test
    public void sameBindingNamesCompileOnce() throws Exception {
        context.setAttribute("a", 1, ScriptContext.ENGINE_SCOPE);
        ClojureCompiledScript compiled = compile("(inc a)");
        assertThat(compiled.eval(context)).isEqualTo(2L);
        context.setAttribute("a", 10, ScriptContext.ENGINE_SCOPE);
        assertThat(compiled.eval(context)).isEqualTo(11L);
        assertThat(compiled.compiledFunctionCount()).isEqualTo(1);
    }

    @Test
    public void newBindingNameCompilesAgainAndIsVisible() throws Exception {
        engine.eval("(defmacro stage04-has-local [s] (contains? &env s))", context);
        context.setAttribute("a", 1, ScriptContext.ENGINE_SCOPE);
        ClojureCompiledScript compiled = compile("[(stage04-has-local b) a]");
        assertThat(compiled.eval(context).toString()).isEqualTo("[false 1]");
        assertThat(compiled.compiledFunctionCount()).isEqualTo(1);
        context.setAttribute("b", 5, ScriptContext.ENGINE_SCOPE);
        assertThat(compiled.eval(context).toString()).isEqualTo("[true 1]");
        assertThat(compiled.compiledFunctionCount()).isEqualTo(2);
    }

    @Test
    public void compilingDoesNotRunBodyButEachEvalDoes() throws Exception {
        Atom probe = new Atom(0L);
        context.setAttribute("probe", probe, ScriptContext.ENGINE_SCOPE);
        ClojureCompiledScript compiled = compile("(swap! probe inc)");
        assertThat(probe.deref()).isEqualTo(0L);
        compiled.eval(context);
        compiled.eval(context);
        compiled.eval(context);
        assertThat(probe.deref()).isEqualTo(3L);
    }

    @Test
    public void concurrentEvaluationsCompileOnce() throws Exception {
        context.setAttribute("a", 20, ScriptContext.ENGINE_SCOPE);
        final ClojureCompiledScript compiled = compile("(* 2 a)");
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> results = new ArrayList<Future<Object>>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        start.await();
                        return compiled.eval(context);
                    }
                }));
            }
            start.countDown();
            for (Future<Object> result : results) {
                assertThat(result.get()).isEqualTo(40L);
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(compiled.compiledFunctionCount()).isEqualTo(1);
    }

    @Test
    public void undefinedSymbolReportsLine() throws Exception {
        ClojureCompiledScript compiled = compile("1\n(stage04-undefined-symbol)");
        assertThatThrownBy(() -> compiled.eval(context))
                .isInstanceOf(ScriptException.class)
                .satisfies(e -> assertThat(((ScriptException) e).getLineNumber()).isEqualTo(2));
    }
}
