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

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.features.map.IMapSelection;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

/**
 * Action to execute a Clojure script on all selected nodes
 */
public class ExecuteClojureScriptForSelectionAction extends AFreeplaneAction {
    
    private static final long serialVersionUID = 1L;
    
    public ExecuteClojureScriptForSelectionAction() {
        super("ExecuteClojureScriptForSelectionAction");
    }

    @Override
    public void actionPerformed(org.freeplane.core.ui.AFreeplaneActionEvent e) {
        final IMapSelection selection = Controller.getCurrentController().getSelection();
        if (selection != null) {
            for (NodeModel selectedNode : selection.getSelection()) {
                // For now, execute a simple test script on each selected node
                String testScript = "(println \"Hello from Clojure for node: \" (str node))";
                ClojureScriptingEngine.executeScript(selectedNode, testScript);
            }
        }
    }
}
