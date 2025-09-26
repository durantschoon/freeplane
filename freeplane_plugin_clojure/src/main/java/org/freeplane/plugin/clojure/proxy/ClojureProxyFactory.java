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

import org.freeplane.api.ControllerRO;
import org.freeplane.api.NodeRO;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.script.ScriptContext;
import org.freeplane.plugin.script.proxy.ProxyFactory;

/**
 * Factory for creating Clojure-specific proxies.
 * Delegates to the existing ProxyFactory for now, but can be extended
 * for Clojure-specific optimizations.
 */
public class ClojureProxyFactory {
    
    /**
     * Create a controller proxy for Clojure scripts
     */
    public static ControllerRO createController(ScriptContext scriptContext) {
        return ProxyFactory.createController(scriptContext);
    }
    
    /**
     * Create a node proxy for Clojure scripts
     */
    public static NodeRO createNode(NodeModel node, ScriptContext scriptContext) {
        return ProxyFactory.createNode(node, scriptContext);
    }
}
