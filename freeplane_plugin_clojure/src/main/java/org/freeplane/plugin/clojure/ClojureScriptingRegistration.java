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

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.PrintStream;

import javax.swing.ComboBoxEditor;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.menubuilders.generic.EntryVisitor;
import org.freeplane.core.ui.menubuilders.generic.PhaseProcessor.Phase;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.script.IScriptEditorStarter;
import org.freeplane.features.script.IScriptStarter;
import org.freeplane.main.application.CommandLineOptions;
import org.freeplane.plugin.script.IFreeplaneScriptErrorHandler;
import org.freeplane.plugin.script.ScriptContext;
import org.freeplane.plugin.script.ScriptingPermissions;

/**
 * Registration class for Clojure scripting support in Freeplane.
 * Integrates Clojure script execution with Freeplane's mode controller and UI.
 */
public class ClojureScriptingRegistration {

    /**
     * Register Clojure scripting support with the mode controller
     */
    void register(ModeController modeController, CommandLineOptions options) {
        // Register script editor integration
        modeController.addExtension(IScriptEditorStarter.class, new IScriptEditorStarter() {
            @Override
            public String startEditor(final String pScriptInput) {
                final ClojureScriptModel scriptModel = new ClojureScriptModel(pScriptInput);
                final ClojureScriptEditorPanel scriptEditorPanel = new ClojureScriptEditorPanel(scriptModel, false);
                scriptEditorPanel.setVisible(true);
                return scriptModel.getScript();
            }

            @Override
            public ComboBoxEditor createComboBoxEditor(Dimension minimumSize) {
                final ClojureScriptComboBoxEditor scriptComboBoxEditor = new ClojureScriptComboBoxEditor();
                if(minimumSize != null)
                    scriptComboBoxEditor.setMinimumSize(minimumSize);
                return scriptComboBoxEditor;
            }
        });

        // Register script execution integration
        modeController.addExtension(IScriptStarter.class, new IScriptStarter() {
            @Override
            public void executeScript(NodeModel node, String script) {
                ClojureScriptingEngine.executeScript(node, script);
            }
        });

        // Register Clojure-specific menu entries
        if (!GraphicsEnvironment.isHeadless()) {
            registerClojureMenuEntries(modeController);
            createClojureScriptsDirectory();
        }

        // Register Clojure script configuration
        ClojureScriptingConfiguration.register();
    }

    /**
     * Register Clojure-specific menu entries
     */
    private void registerClojureMenuEntries(ModeController modeController) {
        // Add Clojure script execution to the Tools menu
        modeController.addAction(new ExecuteClojureScriptAction());
        modeController.addAction(new ExecuteClojureScriptForSelectionAction());
        
        // Add Clojure script editor
        modeController.addAction(new OpenClojureScriptEditorAction());
    }

    /**
     * Create the user Clojure scripts directory
     */
    private void createClojureScriptsDirectory() {
        final File userScriptsDir = new File(ResourceController.getResourceController().getFreeplaneUserDirectory(), "scripts");
        final File clojureScriptsDir = new File(userScriptsDir, "clojure");
        if (!clojureScriptsDir.exists()) {
            clojureScriptsDir.mkdirs();
        }
    }

    /**
     * Simple script model for Clojure scripts
     */
    private static class ClojureScriptModel implements org.freeplane.plugin.script.ScriptEditorPanel.IScriptModel {
        private final String mOriginalScript;
        private String mScript;

        public ClojureScriptModel(final String pScript) {
            mScript = pScript;
            mOriginalScript = pScript;
        }

        @Override
        public int addNewScript() {
            return 0;
        }

        @Override
        public org.freeplane.plugin.script.ScriptEditorWindowConfigurationStorage decorateDialog(
                final org.freeplane.plugin.script.ScriptEditorPanel pPanel,
                final String pWindow_preference_storage_property) {
            final String marshalled = ResourceController.getResourceController().getProperty(
                pWindow_preference_storage_property);
            return org.freeplane.plugin.script.ScriptEditorWindowConfigurationStorage.decorateDialog(marshalled, pPanel);
        }

        @Override
        public void endDialog(final boolean pIsCanceled) {
            if (pIsCanceled) {
                mScript = mOriginalScript;
            }
        }

        @Override
        public Object executeScript(final int pIndex, final PrintStream pOutStream, 
                                  final IFreeplaneScriptErrorHandler pErrorHandler) {
            final ModeController modeController = Controller.getCurrentModeController();
            final ScriptingPermissions restrictedPermissions = ScriptingPermissions.getPermissiveScriptingPermissions();
            return ClojureScriptingEngine.executeScript(modeController.getMapController().getSelectedNode(), mScript,
                pErrorHandler, pOutStream, null, restrictedPermissions);
        }

        @Override
        public int getAmountOfScripts() {
            return 1;
        }

        public String getScript() {
            return mScript;
        }

        @Override
        public org.freeplane.plugin.script.ScriptEditorPanel.ScriptHolder getScript(final int pIndex) {
            return new org.freeplane.plugin.script.ScriptEditorPanel.ScriptHolder("Clojure Script", mScript);
        }

        @Override
        public boolean isDirty() {
            return !mScript.equals(mOriginalScript);
        }

        @Override
        public void setScript(final int pIndex, final org.freeplane.plugin.script.ScriptEditorPanel.ScriptHolder pScript) {
            mScript = pScript.getScript();
        }

        @Override
        public void storeDialogPositions(final org.freeplane.plugin.script.ScriptEditorPanel pPanel,
                                       final org.freeplane.plugin.script.ScriptEditorWindowConfigurationStorage pStorage,
                                       final String pWindow_preference_storage_property) {
            pStorage.storeDialogPositions(pPanel, pWindow_preference_storage_property);
        }

        @Override
        public String getTitle() {
            return "Clojure Script";
        }
    }
}
