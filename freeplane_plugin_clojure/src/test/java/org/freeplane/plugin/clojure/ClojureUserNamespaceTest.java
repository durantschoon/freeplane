package org.freeplane.plugin.clojure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import clojure.java.api.Clojure;
import clojure.lang.Symbol;

public class ClojureUserNamespaceTest {

    @Test
    public void startupProblemReportsOnlyVersionsBelowTheFloor() {
        assertThat(ClojureUserNamespace.startupProblem("1.11.4"))
                .isEqualTo("clojure 1.11.4 is older than the supported minimum 1.12.0");
        assertThat(ClojureUserNamespace.startupProblem("1.12.2")).isNull();
        assertThat(ClojureUserNamespace.startupProblem("weird-build")).isNull();
    }

    @Test
    public void namespaceIsSharedAndHasFpAlias() throws Exception {
        assertThat(ClojureUserNamespace.get()).isSameAs(ClojureUserNamespace.get());
        Object resolved = Clojure.var("clojure.core", "ns-resolve")
                .invoke(ClojureUserNamespace.get(), Symbol.intern("fp/children"));
        assertThat(resolved).isNotNull();
    }
}
