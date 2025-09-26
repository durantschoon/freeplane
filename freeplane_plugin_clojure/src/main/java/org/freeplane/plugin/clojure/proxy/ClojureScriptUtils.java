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
package org.freeplane.plugin.clojure.proxy;

import org.freeplane.api.Controller;
import org.freeplane.plugin.script.proxy.ScriptUtils;

/**
 * Clojure-specific script utilities.
 * Delegates to the existing ScriptUtils for now, but can be extended
 * for Clojure-specific functionality.
 */
public class ClojureScriptUtils {
    
    /**
     * Get the current controller
     */
    public static Controller c() {
        return ScriptUtils.c();
    }
}
