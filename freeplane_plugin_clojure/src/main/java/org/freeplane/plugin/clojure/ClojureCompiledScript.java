package org.freeplane.plugin.clojure;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.freeplane.core.util.LogUtils;

import clojure.java.api.Clojure;
import clojure.lang.Compiler;
import clojure.lang.Associative;
import clojure.lang.IFn;
import clojure.lang.Keyword;
import clojure.lang.RT;
import clojure.lang.Var;

final class ClojureCompiledScript extends CompiledScript {

    private final ClojureScriptEngine engine;
    private final List<Object> forms;
    private final Map<List<String>, IFn> functionsByBindingNames = new HashMap<List<String>, IFn>();

    ClojureCompiledScript(ClojureScriptEngine engine, String script) throws ScriptException {
        this.engine = engine;
        this.forms = ClojureScriptReader.readForms(script);
    }

    @Override
    public Object eval(ScriptContext context) throws ScriptException {
        try {
            Map<String, Object> bindings = collectBindings(context);
            List<String> bindableNames = ClojureScriptSource.bindableNames(bindings.keySet());
            Var.pushThreadBindings(threadBindings(context));
            try {
                return functionFor(bindableNames).invoke(bindings);
            } finally {
                try {
                    flush(context.getWriter());
                    flush(context.getErrorWriter());
                } finally {
                    Var.popThreadBindings();
                }
            }
        } catch (Compiler.CompilerException e) {
            ScriptException scriptException = new ScriptException(e.getMessage(), null, lineOf(e));
            scriptException.initCause(e);
            throw scriptException;
        } catch (ScriptException e) {
            throw e;
        } catch (Exception e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public ScriptEngine getEngine() {
        return engine;
    }

    int compiledFunctionCount() {
        synchronized (functionsByBindingNames) {
            return functionsByBindingNames.size();
        }
    }

    private IFn functionFor(List<String> bindableNames) {
        synchronized (functionsByBindingNames) {
            IFn function = functionsByBindingNames.get(bindableNames);
            if (function == null) {
                Object form = ClojureFunctionForm.of(forms, bindableNames);
                function = (IFn) Clojure.var("clojure.core", "eval").invoke(form);
                functionsByBindingNames.put(bindableNames, function);
            }
            return function;
        }
    }

    private static Associative threadBindings(ScriptContext context) throws ScriptException {
        Associative bindings = RT.map(RT.CURRENT_NS, ClojureUserNamespace.get());
        if (context.getWriter() != null) {
            bindings = bindings.assoc(RT.OUT, context.getWriter());
        }
        if (context.getErrorWriter() != null) {
            bindings = bindings.assoc(RT.ERR, context.getErrorWriter());
        }
        return bindings;
    }

    private static void flush(Writer writer) {
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
        } catch (IOException e) {
            LogUtils.warn("Cannot flush script output", e);
        }
    }

    private static int lineOf(Compiler.CompilerException exception) {
        Object line = RT.get(exception.getData(), Keyword.intern("clojure.error", "line"));
        return line instanceof Number ? ((Number) line).intValue() : -1;
    }

    private static Map<String, Object> collectBindings(ScriptContext context) {
        Map<String, Object> collected = new LinkedHashMap<String, Object>();
        Bindings global = context.getBindings(ScriptContext.GLOBAL_SCOPE);
        if (global != null) {
            collected.putAll(global);
        }
        Bindings engineScope = context.getBindings(ScriptContext.ENGINE_SCOPE);
        if (engineScope != null) {
            collected.putAll(engineScope);
        }
        return collected;
    }
}
