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
}
