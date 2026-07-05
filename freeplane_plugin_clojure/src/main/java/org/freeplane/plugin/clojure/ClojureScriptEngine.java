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

public class ClojureScriptEngine extends AbstractScriptEngine implements Compilable {

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
            // Get the node from the script context
            Object node = context.getAttribute("node");
            Object scriptContext = context.getAttribute("script-context");
            
            // Wrap the script in a function that receives node and script-context parameters
            String wrappedScript = "(fn [node script-context] " + script + ")";
            
            // Compile the wrapped script
            clojure.lang.IFn eval = clojure.java.api.Clojure.var("clojure.core", "eval");
            clojure.lang.IFn readString = clojure.java.api.Clojure.var("clojure.core", "read-string");
            Object form = readString.invoke(wrappedScript);
            clojure.lang.IFn compiledFunction = (clojure.lang.IFn) eval.invoke(form);
            
            // Execute the function with the node and script-context parameters
            return compiledFunction.invoke(node, scriptContext);
        } catch (Exception e) {
            throw new ScriptException(e);
        }
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
