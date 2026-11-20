package org.freeplane.plugin.clojure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

final class ClojureScriptSource {

    private static final Pattern BINDABLE_NAME = Pattern.compile("[A-Za-z_*+!?<>=][A-Za-z0-9_*+!?<>=-]*");

    private ClojureScriptSource() {
    }

    static List<String> bindableNames(Collection<String> names) {
        LinkedHashSet<String> bindable = new LinkedHashSet<String>();
        for (String name : names) {
            if (name != null && BINDABLE_NAME.matcher(name).matches()) {
                bindable.add(name);
            }
        }
        return new ArrayList<String>(bindable);
    }

    static String wrap(String script, List<String> bindableNames) {
        StringBuilder source = new StringBuilder("(fn [bindings] (let [");
        String separator = "";
        for (String name : bindableNames) {
            source.append(separator).append(name).append(" (get bindings \"").append(name).append("\")");
            separator = " ";
        }
        return source.append("]\n").append(script).append("\n))").toString();
    }
}
