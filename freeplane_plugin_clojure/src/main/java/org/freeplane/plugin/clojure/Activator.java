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

import java.util.Hashtable;

import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.main.application.CommandLineOptions;
import org.freeplane.main.osgi.IModeControllerExtensionProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * OSGi Bundle Activator for the Clojure plugin.
 * Registers the Clojure scripting extension with Freeplane's mode controller.
 */
public class Activator implements BundleActivator {
    
    /*
     * (non-Javadoc)
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    @Override
    public void start(final BundleContext context) throws Exception {
        System.out.println("Clojure plugin bundle starting...");
        
        final Hashtable<String, String[]> props = new Hashtable<String, String[]>();
        props.put("mode", new String[] { MModeController.MODENAME });
        context.registerService(IModeControllerExtensionProvider.class.getName(),
            new IModeControllerExtensionProvider() {
                @Override
                public void installExtension(ModeController modeController, CommandLineOptions options) {
                    System.out.println("Clojure plugin extension installing...");
                    // TODO: Register Clojure scripting when ClojureScriptingRegistration is implemented
                    // new ClojureScriptingRegistration().register(modeController, options);
                }
            }, props);
        // TODO: Register controller service when ClojureScriptUtils is implemented
        // context.registerService(Controller.class.getName(), ClojureScriptUtils.c(), new Hashtable<String, String[]>());
        
        System.out.println("Clojure plugin bundle started successfully");
    }

    /*
     * (non-Javadoc)
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    @Override
    public void stop(final BundleContext context) throws Exception {
        // Cleanup if needed
    }
}
