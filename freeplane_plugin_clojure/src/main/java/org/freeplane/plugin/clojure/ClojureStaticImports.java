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

import java.util.Date;

import org.freeplane.features.format.FormatController;
import org.freeplane.features.format.IFormattedObject;
import org.freeplane.features.format.ScannerController;
import org.freeplane.plugin.script.proxy.Convertible;

/**
 * Static utility functions available in Clojure scripts.
 * These functions provide common operations for script development.
 */
public class ClojureStaticImports {

    /** returns valueIfNull if value is null and value otherwise. */
    public static Object ifNull(Object value, Object valueIfNull) {
        return value == null ? valueIfNull : value;
    }

    /** rounds a number to integral type. */
    public static Long round(final Double d) {
        return d == null ? null : Math.round(d);
    }

    /** round to the given number of decimal places: <code>round(0.1234, 2) &rarr; 0.12</code> */
    public static Double round(final Double d, final int precision) {
        if (d == null) return null;
        final double factor = Math.pow(10, precision);
        return Math.round(d * factor) / factor;
    }

    /** parses text to the proper data type, if possible, setting format to the standard. */
    public static Object parse(final String text) {
        if (text == null) return null;
        return ScannerController.getController().parse(text);
    }

    /** uses formatString to return a FormattedObject. */
    public static Object format(final Object object, final String formatString) {
        if (object == null) return null;
        return FormatController.getController().formatUsing(object, formatString);
    }

    /** Applies default date-time format for dates or default number format for numbers. */
    public static Object format(final Object object) {
        if (object == null) return null;
        return FormatController.getController().formatUsingDefault(object);
    }

    /** Applies default date format (instead of standard date-time) format on the given date. */
    public static Object formatDate(final Date date) {
        if (date == null) return null;
        return FormatController.getController().formatUsingDefault(date);
    }

    /** formats according to the internal standard, that is the conversion will be reversible */
    public static String toString(final Object o) {
        if (o == null) return null;
        if (o instanceof IFormattedObject) {
            return ((IFormattedObject) o).getObject().toString();
        }
        if (o instanceof Convertible) {
            return ((Convertible) o).toString();
        }
        return o.toString();
    }
}
