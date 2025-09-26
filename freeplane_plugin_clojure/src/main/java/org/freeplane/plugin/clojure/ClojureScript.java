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
import java.io.PrintStream;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.script.CompileTimeStrategy;
import org.freeplane.plugin.script.IFreeplaneScriptErrorHandler;
import org.freeplane.plugin.script.IScript;
import org.freeplane.plugin.script.ScriptContext;
import org.freeplane.plugin.script.ScriptingPermissions;
import org.freeplane.plugin.script.ScriptingSecurityManager;

/**
 * Clojure script implementation for Freeplane.
 * Handles compilation, execution, and caching of Clojure scripts.
 */
public class ClojureScript implements IScript {
    
    private final Object script; // String or File
    private final ScriptingPermissions specificPermissions;
    private final CompileTimeStrategy compileTimeStrategy;
    private final ClojureScriptClassLoader scriptClassLoader;
    
    private Object compiledScript;
    private Throwable errorsInScript;
    
    public ClojureScript(String script) {
        this((Object) script);
    }

    public ClojureScript(File script) {
        this((Object) script);
        compileTimeStrategy = new CompileTimeStrategy(script);
    }

    public ClojureScript(String script, ScriptingPermissions permissions) {
        this((Object) script, permissions);
    }

    public ClojureScript(File script, ScriptingPermissions permissions) {
        this((Object) script, permissions);
        compileTimeStrategy = new CompileTimeStrategy(script);
    }

    private ClojureScript(Object script, ScriptingPermissions permissions) {
        super();
        this.script = script;
        this.specificPermissions = permissions;
        compiledScript = null;
        errorsInScript = null;
        compileTimeStrategy = new CompileTimeStrategy(null);
    }

    private ClojureScript(Object script) {
        this(script, null);
    }

    @Override
    public Object execute(final NodeModel node, PrintStream outStream, 
                         IFreeplaneScriptErrorHandler errorHandler, ScriptContext scriptContext) {
        if (errorsInScript != null && compileTimeStrategy.canUseOldCompiledScript()) {
            throw new ExecuteScriptException(errorsInScript.getMessage(), errorsInScript);
        }
        
        final PrintStream oldOut = System.out;
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<Object>(){
                @Override
                public Object run() throws Exception {
                    try {
                        final ScriptingSecurityManager scriptingSecurityManager = createScriptingSecurityManager(outStream);
                        compileAndCache(scriptingSecurityManager);
                        Thread.currentThread().setContextClassLoader(scriptClassLoader);
                        
                        ClojureScriptBaseClass scriptWithBinding = compiledScript.withBinding(node, scriptContext);
                        if(oldOut != outStream)
                            System.setOut(outStream);
                        
                        final Object result = scriptWithBinding.run();
                        return result;
                    } catch (Exception e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (final PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if(cause instanceof ClojureScriptException) {
                outStream.print("message: " + e.getMessage());
                int lineNumber = findErrorLine((ClojureScriptException) cause);
                outStream.print("Line number: " + lineNumber);
                errorHandler.gotoLine(lineNumber);
                throw new ExecuteScriptException(cause.getMessage() + " at line " + lineNumber, cause);
            }
            else
                throw new ExecuteScriptException(cause.getMessage(), cause);
        } catch (final ExecuteScriptException e) {
            throw e;
        } catch (final Throwable e) {
            throw new ExecuteScriptException(e.getMessage(), e);
        }
        finally {
            if(oldOut != outStream)
                System.setOut(oldOut);
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }

    private ScriptingSecurityManager createScriptingSecurityManager(PrintStream outStream) {
        return new ScriptSecurity(script, specificPermissions, outStream)
                .getScriptingSecurityManager();
    }

    private static boolean accessPermissionCheckerChecked = false;

    private void compileAndCache(final ScriptingSecurityManager scriptingSecurityManager) throws Throwable {
        checkAccessPermissionCheckerExists();
        if (compileTimeStrategy.canUseOldCompiledScript()) {
            scriptClassLoader.setSecurityManager(scriptingSecurityManager);
        }
        else {
            removeOldScript();
            errorsInScript = null;
            try {
                scriptClassLoader = ClojureScriptClassLoader.createClassLoader();
                scriptClassLoader.setSecurityManager(scriptingSecurityManager);
                
                compileTimeStrategy.scriptCompileStart();
                if (script instanceof String) {
                    compiledScript = ClojureScriptBaseClass.compileScript((String) script);
                } else if (script instanceof File) {
                    compiledScript = ClojureScriptBaseClass.compileScript((File) script);
                } else {
                    throw new IllegalArgumentException();
                }
                compiledScript.setScript(script);
                compileTimeStrategy.scriptCompiled();
            } catch (Throwable e) {
                errorsInScript = e;
                throw e;
            }
        }
    }

    static void checkAccessPermissionCheckerExists() {
        if(!accessPermissionCheckerChecked){
            if(System.getSecurityManager() != null){
                try {
                    ClojureScript.class.getClassLoader().loadClass("clojure.lang.RT");
                } catch (ClassNotFoundException e) {
                    throw new AccessControlException("class clojure.lang.RT not found");
                }
            }
            accessPermissionCheckerChecked = true;
        }
    }

    private void removeOldScript() {
        if (compiledScript != null) {
            // Clojure-specific cleanup if needed
            compiledScript = null;
        }
    }

    private int findErrorLine(final ClojureScriptException e) {
        // Extract line number from Clojure exception
        return e.getLine();
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
