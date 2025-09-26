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

import org.freeplane.core.resources.ResourceController;

/**
 * Configuration for Clojure scripting support.
 * Manages Clojure-specific settings and preferences.
 */
public class ClojureScriptingConfiguration {
    
    private static final String CLOJURE_SCRIPT_CACHE_SIZE = "clojure_script_cache_size";
    private static final String CLOJURE_SCRIPT_TIMEOUT = "clojure_script_timeout";
    private static final String CLOJURE_SCRIPT_SECURITY_ENABLED = "clojure_script_security_enabled";
    
    /**
     * Register Clojure scripting configuration
     */
    public static void register() {
        // Set default values for Clojure-specific properties
        ResourceController resourceController = ResourceController.getResourceController();
        
        if (resourceController.getProperty(CLOJURE_SCRIPT_CACHE_SIZE) == null) {
            resourceController.setDefaultProperty(CLOJURE_SCRIPT_CACHE_SIZE, "100");
        }
        
        if (resourceController.getProperty(CLOJURE_SCRIPT_TIMEOUT) == null) {
            resourceController.setDefaultProperty(CLOJURE_SCRIPT_TIMEOUT, "30000"); // 30 seconds
        }
        
        if (resourceController.getProperty(CLOJURE_SCRIPT_SECURITY_ENABLED) == null) {
            resourceController.setDefaultProperty(CLOJURE_SCRIPT_SECURITY_ENABLED, "true");
        }
    }
    
    /**
     * Get the Clojure script cache size
     */
    public static int getClojureScriptCacheSize() {
        return ResourceController.getResourceController().getIntProperty(CLOJURE_SCRIPT_CACHE_SIZE, 100);
    }
    
    /**
     * Get the Clojure script timeout in milliseconds
     */
    public static long getClojureScriptTimeout() {
        return ResourceController.getResourceController().getLongProperty(CLOJURE_SCRIPT_TIMEOUT, 30000);
    }
    
    /**
     * Check if Clojure script security is enabled
     */
    public static boolean isClojureScriptSecurityEnabled() {
        return ResourceController.getResourceController().getBooleanProperty(CLOJURE_SCRIPT_SECURITY_ENABLED, true);
    }
}
