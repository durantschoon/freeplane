package org.freeplane.plugin.clojure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.Test;

public class ClojureScriptSourceTest {

    @Test
    public void bindableNamesFiltersAndDeduplicates() {
        assertThat(ClojureScriptSource.bindableNames(Arrays.asList(
                "node", "a.b", "c", "a/b", "textUtils", "1x", "", "has space", "script-context", "node", "valid?")))
            .containsExactly("node", "c", "textUtils", "script-context", "valid?");
    }
}
