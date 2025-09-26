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

import java.net.URL;
import java.net.URLClassLoader;
import java.security.Permission;

import org.freeplane.plugin.script.ScriptingSecurityManager;

/**
 * Custom class loader for Clojure scripts.
 * Provides isolation and security for script execution.
 */
public class ClojureScriptClassLoader extends URLClassLoader {
    
    private ScriptingSecurityManager securityManager;
    
    public ClojureScriptClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }
    
    public static ClojureScriptClassLoader createClassLoader() {
        // Create a new class loader with the current classpath
        URL[] urls = new URL[0]; // Will be populated as needed
        return new ClojureScriptClassLoader(urls, ClojureScriptClassLoader.class.getClassLoader());
    }
    
    public void setSecurityManager(ScriptingSecurityManager securityManager) {
        this.securityManager = securityManager;
    }
    
    @Override
    public void checkPermission(Permission perm) {
        if (securityManager != null) {
            securityManager.checkPermission(perm);
        } else {
            super.checkPermission(perm);
        }
    }
    
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return super.findClass(name);
        } catch (ClassNotFoundException e) {
            // Try to load from Clojure runtime
            if (name.startsWith("clojure.")) {
                return Class.forName(name, true, getParent());
            }
            throw e;
        }
    }
}
