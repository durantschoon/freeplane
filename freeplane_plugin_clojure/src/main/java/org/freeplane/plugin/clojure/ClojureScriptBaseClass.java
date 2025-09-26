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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import javax.swing.Icon;

import org.freeplane.api.ControllerRO;
import org.freeplane.api.LengthUnit;
import org.freeplane.api.NodeRO;
import org.freeplane.api.Quantity;
import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.TimePeriodUnits;
import org.freeplane.core.util.Hyperlink;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.format.FormatController;
import org.freeplane.features.format.ScannerController;
import org.freeplane.features.link.LinkController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.clojure.proxy.ClojureProxyFactory;
import org.freeplane.plugin.script.ClojureScriptException;
import org.freeplane.plugin.script.ExecuteScriptException;
import org.freeplane.plugin.script.ScriptContext;

/**
 * Base class for Clojure scripts in Freeplane.
 * Provides global objects and utility functions available in all Clojure scripts.
 */
public class ClojureScriptBaseClass {
    
    private final Pattern nodeIdPattern = Pattern.compile("ID_\\d+");
    private Object script;
    private Map<Object, Object> boundVariables;
    private NodeRO node;
    private ControllerRO controller;

    public ClojureScriptBaseClass() {
        super();
    }

    void setScript(Object script) {
        this.script = script;
    }

    ClojureScriptBaseClass withBinding(final NodeModel node, ScriptContext scriptContext) {
        try {
            ClojureScriptBaseClass instance = boundVariables != null ? getClass().newInstance() : this;
            instance.script = script;
            ControllerRO controllerProxy = ClojureProxyFactory.createController(scriptContext);
            NodeRO nodeProxy = ClojureProxyFactory.createNode(node, scriptContext);
            Map<Object, Object> binding = createBinding(nodeProxy, controllerProxy);
            instance.boundVariables = binding;
            instance.node = nodeProxy;
            instance.controller = controllerProxy;
            return instance;
        }
        catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    protected Map<Object, Object> createBinding(NodeRO nodeProxy, ControllerRO controllerProxy) {
        Map<Object, Object> binding = new LinkedHashMap<>();
        binding.put("c", controllerProxy);
        binding.put("node", nodeProxy);
        binding.put("config", new ConfigProperties());
        binding.put("ui", org.freeplane.core.ui.components.UITools.class);
        binding.put("logger", LogUtils.class);
        binding.put("htmlUtils", org.freeplane.core.util.HtmlUtils.class);
        binding.put("textUtils", org.freeplane.core.util.TextUtils.class);
        binding.put("menuUtils", org.freeplane.core.util.MenuUtils.class);
        return binding;
    }

    public Object run() {
        // This will be overridden by the actual Clojure script execution
        return null;
    }

    /** Shortcut for node.map.node(id) - necessary for ids to other maps. */
    public NodeRO N(String id) {
        final NodeRO node = (NodeRO) boundVariables.get("node");
        return node.getMindMap().node(id);
    }

    /** Shortcut for node.map.node(id).text. */
    public String T(String id) {
        final NodeRO n = N(id);
        return n == null ? null : n.getText();
    }

    /** Shortcut for node.map.node(id).value. */
    public Object V(String id) {
        final NodeRO n = N(id);
        try {
            return n == null ? null : n.getValue();
        }
        catch (ExecuteScriptException e) {
            return null;
        }
    }

    /** returns valueIfNull if value is null and value otherwise. */
    public Object ifNull(Object value, Object valueIfNull) {
        return ClojureStaticImports.ifNull(value, valueIfNull);
    }

    /** rounds a number to integral type. */
    public Long round(final Double d) {
        return ClojureStaticImports.round(d);
    }

    /** round to the given number of decimal places: <code>round(0.1234, 2) &rarr; 0.12</code> */
    public Double round(final Double d, final int precision) {
        return ClojureStaticImports.round(d, precision);
    }

    /** parses text to the proper data type, if possible, setting format to the standard. */
    public Object parse(final String text) {
        return ClojureStaticImports.parse(text);
    }

    /** uses formatString to return a FormattedObject. */
    public Object format(final Object object, final String formatString) {
        return ClojureStaticImports.format(object, formatString);
    }

    /** Applies default date-time format for dates or default number format for numbers. */
    public Object format(final Object object) {
        return ClojureStaticImports.format(object);
    }

    /** Applies default date format (instead of standard date-time) format on the given date. */
    public Object formatDate(final Date date) {
        return ClojureStaticImports.format(date);
    }

    /** formats according to the internal standard, that is the conversion will be reversible */
    public String toString(final Object o) {
        return ClojureStaticImports.toString(o);
    }

    /** opens a {@link URI} */
    public void loadUri(final URI uri) {
        final NodeModel delegate = ((org.freeplane.plugin.clojure.proxy.AbstractProxy<NodeModel>)node).getDelegate();
        LinkController.getController().loadURI(delegate, new Hyperlink(uri));
    }

    /** opens a link */
    public void loadUri(final String link) {
        try {
            final NodeModel delegate = ((org.freeplane.plugin.clojure.proxy.AbstractProxy<NodeModel>)node).getDelegate();
            LinkController.getController().loadURI(delegate, LinkController.createHyperlink(link));
        } catch (URISyntaxException e) {
            LogUtils.warn(e);
        }
    }

    @Override
    public String toString() {
        return "ClojureScript [" + script + "]";
    }

    /**
     * Accessor for Freeplane's configuration: In scripts available as "global variable" {@code config}
     */
    public static class ConfigProperties {
        private final ResourceController resourceController = ResourceController.getResourceController();

        public boolean getBooleanProperty(String key) {
            return resourceController.getBooleanProperty(key);
        }

        public boolean getBooleanProperty(String key, boolean defaultValue) {
            return resourceController.getBooleanProperty(key, defaultValue);
        }

        public boolean getProperty(String key, boolean defaultValue) {
            return getBooleanProperty(key, defaultValue);
        }

        public <T extends Enum<T>> T getEnumProperty(String propertyName, Enum<T> defaultValue) {
            return resourceController.getEnumProperty(propertyName, defaultValue);
        }

        public <T extends Enum<T>> T getProperty(String propertyName, Enum<T> defaultValue) {
            return getEnumProperty(propertyName, defaultValue);
        }

        public double getDoubleProperty(String key) {
            return resourceController.getDoubleProperty(key);
        }

        public double getDoubleProperty(String key, double defaultValue) {
            return resourceController.getDoubleProperty(key, defaultValue);
        }

        public double getProperty(String key, double defaultValue) {
            return getDoubleProperty(key, defaultValue);
        }

        public int getIntProperty(String key) {
            return resourceController.getIntProperty(key);
        }

        public int getIntProperty(String key, int defaultValue) {
            return resourceController.getIntProperty(key, defaultValue);
        }

        public int getProperty(String key, int defaultValue) {
            return getIntProperty(key, defaultValue);
        }

        public long getLongProperty(String key, long defaultValue) {
            return resourceController.getLongProperty(key, defaultValue);
        }

        public long getProperty(String key, long defaultValue) {
            return getLongProperty(key, defaultValue);
        }

        public int getLengthProperty(String name) {
            return resourceController.getLengthProperty(name);
        }

        public Quantity<LengthUnit> getLengthQuantityProperty(String name) {
            return resourceController.getLengthQuantityProperty(name);
        }

        public int getTimeProperty(String name) {
            return resourceController.getTimeProperty(name);
        }

        public Quantity<TimePeriodUnits> getTimeQuantityProperty(String name) {
            return resourceController.getTimeQuantityProperty(name);
        }

        public java.awt.Color getColorProperty(String name) {
            return resourceController.getColorProperty(name);
        }

        public String getProperty(String key) {
            return resourceController.getProperty(key);
        }

        public String getProperty(String key, String value) {
            return resourceController.getProperty(key, value);
        }

        public String getDefaultProperty(String key) {
            return resourceController.getDefaultProperty(key);
        }

        public Collection<IFreeplanePropertyListener> getPropertyChangeListeners() {
            return resourceController.getPropertyChangeListeners();
        }

        public URL getResource(String resourcePath) {
            return resourceController.getResource(resourcePath);
        }

        public InputStream getResourceStream(String resFileName) throws IOException {
            return resourceController.getResourceStream(resFileName);
        }

        public String getResourceBaseDir() {
            return resourceController.getResourceBaseDir();
        }

        public String getInstallationBaseDir() {
            return resourceController.getInstallationBaseDir();
        }

        public String getLanguageCode() {
            return resourceController.getLanguageCode();
        }

        public String getDefaultLanguageCode() {
            return resourceController.getDefaultLanguageCode();
        }

        public void setDefaultProperty(String key, String value) {
            resourceController.setDefaultProperty(key, value);
        }

        public void setProperty(String property, boolean value) {
            resourceController.setProperty(property, value);
        }

        public void setProperty(String name, int value) {
            resourceController.setProperty(name, value);
        }

        public void setProperty(String name, long value) {
            resourceController.setProperty(name, value);
        }

        public void setProperty(String name, double value) {
            resourceController.setProperty(name, value);
        }

        public void setProperty(String property, String value) {
            resourceController.setProperty(property, value);
        }

        public Icon getIcon(String iconKey) {
            return resourceController.getIcon(iconKey);
        }

        public URL getIconResource(String resourcePath) {
            return resourceController.getIconResource(resourcePath);
        }

        public Icon getImageIcon(String iconKey) {
            return resourceController.getImageIcon(iconKey);
        }

        public Locale getSystemLocale() {
            return resourceController.getSystemLocale();
        }

        public String[] getArrayProperty(String key, String separator) {
            return resourceController.getArrayProperty(key, separator);
        }

        public Properties getProperties() {
            return resourceController.getProperties();
        }

        /** support config['key'] from Clojure. */
        public String getAt(final String name) {
            return getProperty(name);
        }

        public void setAt(final String name, final String value) {
            setProperty(name, value);
        }

        public ResourceBundle getResources() {
            return resourceController.getResources();
        }

        public String getFreeplaneUserDirectory() {
            return resourceController.getFreeplaneUserDirectory();
        }
    }

    /**
     * Compile a Clojure script from string
     */
    public static ClojureScriptBaseClass compileScript(String script) {
        // This will be implemented to use Clojure's eval
        ClojureScriptBaseClass instance = new ClojureScriptBaseClass();
        instance.script = script;
        return instance;
    }

    /**
     * Compile a Clojure script from file
     */
    public static ClojureScriptBaseClass compileScript(File scriptFile) {
        // This will be implemented to use Clojure's load-file
        ClojureScriptBaseClass instance = new ClojureScriptBaseClass();
        instance.script = scriptFile;
        return instance;
    }
}
