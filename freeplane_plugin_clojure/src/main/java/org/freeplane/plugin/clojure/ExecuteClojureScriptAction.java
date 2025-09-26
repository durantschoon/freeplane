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
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

/**
 * Action to execute a Clojure script on the current node
 */
public class ExecuteClojureScriptAction extends AFreeplaneAction {
    
    private static final long serialVersionUID = 1L;
    
    public ExecuteClojureScriptAction() {
        super("ExecuteClojureScriptAction");
    }

    @Override
    public void actionPerformed(org.freeplane.core.ui.AFreeplaneActionEvent e) {
        final NodeModel selectedNode = Controller.getCurrentModeController().getMapController().getSelectedNode();
        if (selectedNode != null) {
            // For now, execute a simple test script
            String testScript = "(println \"Hello from Clojure!\")";
            ClojureScriptingEngine.executeScript(selectedNode, testScript);
        }
    }
}
