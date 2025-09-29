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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.script.ExecuteScriptException;
import org.freeplane.plugin.script.IFreeplaneScriptErrorHandler;
import org.freeplane.plugin.script.IScript;
import org.freeplane.plugin.script.ScriptContext;
import org.freeplane.plugin.script.ScriptingPermissions;

import clojure.java.api.Clojure;
import clojure.lang.IFn;

/**
 * Clojure script implementation for Freeplane.
 * Handles compilation, execution, and caching of Clojure scripts.
 */
public class ClojureScript implements IScript {
    
    private final Object script; // String or File
    private final ScriptingPermissions specificPermissions;

    private IFn compiledScript;
    private Throwable errorsInScript;
    private boolean scriptCompiled = false;
    
    public ClojureScript(String script) {
        this((Object) script);
    }

    public ClojureScript(File script) {
        this((Object) script);
    }

    public ClojureScript(String script, ScriptingPermissions permissions) {
        this((Object) script, permissions);
    }

    public ClojureScript(File script, ScriptingPermissions permissions) {
        this((Object) script, permissions);
    }

    private ClojureScript(Object script, ScriptingPermissions permissions) {
        super();
        this.script = script;
        this.specificPermissions = permissions;
        this.compiledScript = null;
        this.errorsInScript = null;
        this.scriptCompiled = false;
    }

    private ClojureScript(Object script) {
        this(script, null);
    }

    @Override
    public Object execute(final NodeModel node, PrintStream outStream,
                         IFreeplaneScriptErrorHandler errorHandler, ScriptContext scriptContext) {
        if (errorsInScript != null && scriptCompiled) {
            throw new ExecuteScriptException(errorsInScript.getMessage(), errorsInScript);
        }

        final PrintStream oldOut = System.out;

        try {
            compileAndCache();

            if(oldOut != outStream)
                System.setOut(outStream);

            // Execute the compiled Clojure function with node binding
            final Object result = compiledScript.invoke(node, scriptContext);
            return result;
        } catch (final Throwable e) {
            outStream.print("message: " + e.getMessage());
            int lineNumber = findErrorLine(e);
            if (lineNumber > 0) {
                outStream.print("Line number: " + lineNumber);
                errorHandler.gotoLine(lineNumber);
                throw new ExecuteScriptException(e.getMessage() + " at line " + lineNumber, e);
            } else {
                throw new ExecuteScriptException(e.getMessage(), e);
            }
        }
        finally {
            if(oldOut != outStream)
                System.setOut(oldOut);
        }
    }

    private void compileAndCache() throws Throwable {
        if (scriptCompiled && compiledScript != null) {
            return; // Use cached compiled script
        }

        removeOldScript();
        errorsInScript = null;
        try {
            String scriptSource;
            if (script instanceof String) {
                scriptSource = (String) script;
            } else if (script instanceof File) {
                scriptSource = Files.readString(((File) script).toPath(), StandardCharsets.UTF_8);
            } else {
                throw new IllegalArgumentException("Script must be String or File");
            }

            // Wrap the script in a function that takes node and scriptContext as parameters
            String wrappedScript = "(fn [node script-context] " +
                "(binding [*ns* (find-ns 'user)] " +
                scriptSource + "))";

            // Compile the Clojure script using clojure.core/eval
            IFn evalFn = Clojure.var("clojure.core", "eval");
            IFn readStringFn = Clojure.var("clojure.core", "read-string");

            Object compiledForm = readStringFn.invoke(wrappedScript);
            compiledScript = (IFn) evalFn.invoke(compiledForm);
            scriptCompiled = true;
        } catch (Throwable e) {
            errorsInScript = e;
            throw e;
        }
    }

    private void removeOldScript() {
        if (compiledScript != null) {
            compiledScript = null;
            scriptCompiled = false;
        }
    }

    private int findErrorLine(final Throwable e) {
        String message = e.getMessage();
        if (message != null) {
            // Try to extract line number from Clojure exception message
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(".*line ([0-9]+).*", java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher matcher = pattern.matcher(message);
            if (matcher.matches()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return -1; // No line number found
    }

    @Override
    public boolean hasPermissions(ScriptingPermissions permissions) {
        if (this.specificPermissions == null) {
            return this.specificPermissions == permissions;
        } else {
            return this.specificPermissions.equals(permissions);
        }
    }
}
