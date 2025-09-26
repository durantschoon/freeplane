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
import org.freeplane.features.mode.Controller;

/**
 * Action to open the Clojure script editor
 */
public class OpenClojureScriptEditorAction extends AFreeplaneAction {
    
    private static final long serialVersionUID = 1L;
    
    public OpenClojureScriptEditorAction() {
        super("OpenClojureScriptEditorAction");
    }

    @Override
    public void actionPerformed(org.freeplane.core.ui.AFreeplaneActionEvent e) {
        // For now, just show a simple message
        // TODO: Implement proper Clojure script editor
        String initialScript = "(println \"Hello from Clojure!\")\n; Add your Clojure code here";
        Controller.getCurrentModeController().getExtension(org.freeplane.features.script.IScriptEditorStarter.class)
            .startEditor(initialScript);
    }
}
