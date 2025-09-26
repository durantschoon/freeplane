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

import javax.swing.ComboBoxEditor;
import javax.swing.JTextField;

/**
 * Clojure script combo box editor.
 * This is a placeholder implementation for Clojure script selection.
 */
public class ClojureScriptComboBoxEditor implements ComboBoxEditor {
    
    private final JTextField editor;
    
    public ClojureScriptComboBoxEditor() {
        this.editor = new JTextField();
    }
    
    @Override
    public java.awt.Component getEditorComponent() {
        return editor;
    }
    
    @Override
    public void setItem(Object anObject) {
        if (anObject != null) {
            editor.setText(anObject.toString());
        } else {
            editor.setText("");
        }
    }
    
    @Override
    public Object getItem() {
        return editor.getText();
    }
    
    @Override
    public void selectAll() {
        editor.selectAll();
    }
    
    @Override
    public void addActionListener(java.awt.event.ActionListener l) {
        editor.addActionListener(l);
    }
    
    @Override
    public void removeActionListener(java.awt.event.ActionListener l) {
        editor.removeActionListener(l);
    }
}
