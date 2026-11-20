package org.freeplane.plugin.clojure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ClojureScriptSourceTest {

    @Test
    public void bindableNamesFiltersAndDeduplicates() {
        assertThat(ClojureScriptSource.bindableNames(Arrays.asList(
                "node", "a.b", "c", "a/b", "textUtils", "1x", "", "has space", "script-context", "node", "valid?")))
            .containsExactly("node", "c", "textUtils", "script-context", "valid?");
    }

    @Test
    public void wrapBindsEachNameFromBindingsMap() {
        assertThat(ClojureScriptSource.wrap("(+ 1 2)", Arrays.asList("node", "c")))
            .isEqualTo("(fn [bindings] (let [node (get bindings \"node\") c (get bindings \"c\")]\n(+ 1 2)\n))");
    }

    @Test
    public void wrapWithoutNamesUsesEmptyLetVector() {
        assertThat(ClojureScriptSource.wrap("1", Collections.<String>emptyList()))
            .startsWith("(fn [bindings] (let []\n");
    }

    @Test
    public void wrapPutsScriptBetweenNewlines() {
        assertThat(ClojureScriptSource.wrap("1 ; c", Collections.<String>emptyList()))
            .contains("\n1 ; c\n))")
            .endsWith("\n))");
    }
}
