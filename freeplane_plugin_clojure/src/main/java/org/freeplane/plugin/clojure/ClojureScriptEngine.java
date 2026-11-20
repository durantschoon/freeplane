/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2024 Freeplane Contributors
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.plugin.clojure;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import clojure.java.api.Clojure;
import clojure.lang.Compiler;
import clojure.lang.IFn;
import clojure.lang.Keyword;
import clojure.lang.Namespace;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;

public class ClojureScriptEngine extends AbstractScriptEngine implements Compilable {

    private static final String USER_NAMESPACE = "user";

    private final ClojureScriptEngineFactory factory;

    public ClojureScriptEngine() {
        this.factory = new ClojureScriptEngineFactory();
    }

    public ClojureScriptEngine(ClojureScriptEngineFactory factory) {
        this.factory = factory;
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
        try {
            Map<String, Object> bindings = collectBindings(context);
            Namespace userNamespace = Namespace.findOrCreate(Symbol.intern(USER_NAMESPACE));
            Var.pushThreadBindings(RT.map(RT.CURRENT_NS, userNamespace));
            try {
                Clojure.var("clojure.core", "refer").invoke(Symbol.intern("clojure.core"));
                Object form = ClojureFunctionForm.of(ClojureScriptReader.readForms(script),
                        ClojureScriptSource.bindableNames(bindings.keySet()));
                IFn function = (IFn) Clojure.var("clojure.core", "eval").invoke(form);
                return function.invoke(bindings);
            } finally {
                Var.popThreadBindings();
            }
        } catch (ScriptException e) {
            throw e;
        } catch (Compiler.CompilerException e) {
            ScriptException scriptException = new ScriptException(e.getMessage(), null, lineOf(e));
            scriptException.initCause(e);
            throw scriptException;
        } catch (Exception e) {
            throw new ScriptException(e);
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
        Bindings engine = context.getBindings(ScriptContext.ENGINE_SCOPE);
        if (engine != null) {
            collected.putAll(engine);
        }
        return collected;
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        try {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1) {
                sb.append((char) c);
            }
            return eval(sb.toString(), context);
        } catch (Exception e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public Bindings createBindings() {
        return new SimpleBindings();
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return factory;
    }

    @Override
    public CompiledScript compile(String script) throws ScriptException {
        return new ClojureCompiledScript(this, script);
    }

    @Override
    public CompiledScript compile(Reader script) throws ScriptException {
        try {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = script.read()) != -1) {
                sb.append((char) c);
            }
            return compile(sb.toString());
        } catch (Exception e) {
            throw new ScriptException(e);
        }
    }

    private static class ClojureCompiledScript extends CompiledScript {
        private final ClojureScriptEngine engine;
        private final String script;

        public ClojureCompiledScript(ClojureScriptEngine engine, String script) {
            this.engine = engine;
            this.script = script;
        }

        @Override
        public Object eval(ScriptContext context) throws ScriptException {
            return engine.eval(script, context);
        }

        @Override
        public ScriptEngine getEngine() {
            return engine;
        }
    }
}
