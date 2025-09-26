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

import org.freeplane.plugin.script.ExecuteScriptException;

/**
 * Exception specific to Clojure script execution.
 * Provides Clojure-specific error information including namespace, line, and column.
 */
public class ClojureScriptException extends ExecuteScriptException {
    private final String namespace;
    private final int line;
    private final int column;
    
    public ClojureScriptException(String message, Throwable cause) {
        super(message, cause);
        this.namespace = null;
        this.line = -1;
        this.column = -1;
    }
    
    public ClojureScriptException(String message, Throwable cause, String namespace, int line, int column) {
        super(message, cause);
        this.namespace = namespace;
        this.line = line;
        this.column = column;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public int getLine() {
        return line;
    }
    
    public int getColumn() {
        return column;
    }
    
    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        if (namespace != null) {
            sb.append(" in namespace ").append(namespace);
        }
        if (line > 0) {
            sb.append(" at line ").append(line);
        }
        if (column > 0) {
            sb.append(", column ").append(column);
        }
        return sb.toString();
    }
}
