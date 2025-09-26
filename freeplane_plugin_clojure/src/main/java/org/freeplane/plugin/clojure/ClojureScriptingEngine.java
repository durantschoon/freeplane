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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

import org.apache.commons.lang.WordUtils;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.script.ConcurrentCache;
import org.freeplane.plugin.script.ExecuteScriptException;
import org.freeplane.plugin.script.FileScriptSpecification;
import org.freeplane.plugin.script.IFreeplaneScriptErrorHandler;
import org.freeplane.plugin.script.IScript;
import org.freeplane.plugin.script.ScriptContext;
import org.freeplane.plugin.script.ScriptRunner;
import org.freeplane.plugin.script.ScriptSpecification;
import org.freeplane.plugin.script.ScriptingPermissions;
import org.freeplane.plugin.script.StringScriptSpecification;

/**
 * Clojure-specific scripting engine for Freeplane.
 * Manages Clojure script compilation, caching, and execution.
 */
public class ClojureScriptingEngine {
    
    public static final String SCRIPT_PREFIX = "clojure_script";
    
    // Clojure-specific script caching
    private static Map<File, IScript> clojureFileScripts = new ConcurrentHashMap<File, IScript>();
    private static ConcurrentCache<ScriptSpecification, IScript> clojureScripts
        = new ConcurrentCache(ClojureScriptingEngine::getCompiledScriptCacheSize);
    
    private static int getCompiledScriptCacheSize() {
        return ResourceController.getResourceController().getIntProperty("compiled_script_cache_size");
    }

    /**
     * Execute a Clojure script with full error handling and output stream support
     */
    public static Object executeScript(final NodeModel node, final String script, 
                                     final IFreeplaneScriptErrorHandler pErrorHandler,
                                     final PrintStream pOutStream, final ScriptContext scriptContext,
                                     ScriptingPermissions permissions) {
        return new ScriptRunner(createClojureScript(script, permissions))
            .setErrorHandler(pErrorHandler)
            .setOutStream(pOutStream)
            .setScriptContext(scriptContext)
            .execute(node);
    }

    public static int findLineNumberInString(final String resultString, int lineNumber) {
        final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(".*@ line ([0-9]+).*",
            java.util.regex.Pattern.DOTALL);
        final Matcher matcher = pattern.matcher(resultString);
        if (matcher.matches()) {
            lineNumber = Integer.parseInt(matcher.group(1));
        }
        return lineNumber;
    }

    /**
     * Execute a simple Clojure script
     */
    public static Object executeScript(final NodeModel node, final String script) {
        return new ScriptRunner(new ClojureScript(script)).execute(node);
    }

    /**
     * Create a Clojure script from file with caching
     */
    public static IScript createScript(File scriptFile, ScriptingPermissions permissions, boolean saveForLaterUse) {
        IScript script = clojureFileScripts.get(scriptFile);
        if (script == null || ! script.hasPermissions(permissions)) {
            if(saveForLaterUse) {
                script = compile(scriptFile, permissions);
                clojureFileScripts.put(scriptFile, script);
            }
            else {
                script = clojureScripts.computeIfAbsent(new FileScriptSpecification(scriptFile, permissions),
                    () -> compile(scriptFile, permissions));
            }
        }
        return script;
    }

    private static IScript compile(File scriptFile, ScriptingPermissions permissions) {
        final boolean isClojure = scriptFile.getName().endsWith(".clj");
        IScript script = isClojure ? new ClojureScript(scriptFile, permissions) : 
                                    new org.freeplane.plugin.script.GenericScript(scriptFile, permissions);
        return script;
    }

    /**
     * Create a Clojure script from string with caching
     */
    public static IScript createScript(String source, String type, ScriptingPermissions permissions) {
        return clojureScripts.computeIfAbsent(new StringScriptSpecification(source, type, permissions),
                () -> compile(source, type, permissions));
    }

    private static IScript compile(String source, String type, ScriptingPermissions permissions) {
        final boolean isClojure = type.equals("clojure");
        IScript script = isClojure ? new ClojureScript(source, permissions) : 
                                    new org.freeplane.plugin.script.GenericScript(source, type, permissions);
        return script;
    }

    /**
     * Create a Clojure script from string
     */
    public static IScript createClojureScript(String script, ScriptingPermissions permissions) {
        return createScript(script, "clojure", permissions);
    }

    /**
     * Execute a Clojure script from file
     */
    public static Object executeScript(NodeModel node, File scriptFile, ScriptingPermissions permissions) {
        final IScript script = ClojureScriptingEngine.createScript(scriptFile, permissions, false);
        return new ScriptRunner(script).execute(node);
    }

    /**
     * Execute a Clojure script from string with permissions
     */
    public static Object executeScript(NodeModel node, String script, ScriptingPermissions permissions) {
        return new ScriptRunner(createClojureScript(script, permissions)).execute(node);
    }

    /**
     * Execute a Clojure script with output stream
     */
    public static Object executeScript(NodeModel node, String script, PrintStream printStream) {
        return new ScriptRunner(createClojureScript(script, null))
            .setOutStream(printStream)
            .execute(node);
    }

    /**
     * Execute a Clojure script with script context
     */
    public static Object executeScript(final NodeModel node, final String script, final ScriptContext scriptContext,
                                       final ScriptingPermissions permissions) {
        return new ScriptRunner(createClojureScript(script, permissions))
            .setScriptContext(scriptContext)
            .execute(node);
    }

    /**
     * Process script operations recursively on nodes
     */
    static void performScriptOperationRecursive(final NodeModel node) {
        for (final NodeModel child : node.getChildren()) {
            performScriptOperationRecursive(child);
        }
        performScriptOperation(node);
    }

    /**
     * Process script operations on a single node
     */
    static void performScriptOperation(final NodeModel node) {
        final NodeAttributeTableModel attributes = NodeAttributeTableModel.getModel(node);
        if (attributes == null) {
            return;
        }
        for (int row = 0; row < attributes.getRowCount(); ++row) {
            final String attrKey = (String) attributes.getName(row);
            final Object value = attributes.getValue(row);
            if(value instanceof String){
                final String script = (String) value;
                if (attrKey.startsWith(ClojureScriptingEngine.SCRIPT_PREFIX)) {
                    executeScript(node, script);
                }
            }
        }
    }

    /**
     * Show script exception error message
     */
    static void showScriptExceptionErrorMessage(ExecuteScriptException ex) {
        if (ex.getCause() instanceof SecurityException) {
            final String message = WordUtils.wrap(ex.getCause().getMessage(), 80, "\n    ", false);
            UITools.errorMessage(TextUtils.format("ExecuteScriptSecurityError.text", message));
        }
        else {
            final String message = WordUtils.wrap(ex.getMessage(), 80, "\n    ", false);
            UITools.errorMessage(TextUtils.format("ExecuteScriptError.text", message));
        }
    }
}
