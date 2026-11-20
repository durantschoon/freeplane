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
import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
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
        return compile(script).eval(context);
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        return compile(reader).eval(context);
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
        } catch (ScriptException e) {
            throw e;
        } catch (Exception e) {
            throw new ScriptException(e);
        }
    }
}
