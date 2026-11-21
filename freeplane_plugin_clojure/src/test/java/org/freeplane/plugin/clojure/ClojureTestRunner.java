package org.freeplane.plugin.clojure;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;

public class ClojureTestRunner {
    private static final List<String> TEST_NAMESPACES = Arrays.asList(
            "org.freeplane.plugin.clojure.test-main",
            "freeplane.script.fences-test");

    @Test
    public void runClojureTests() {
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

        IFn require = Clojure.var("clojure.core", "require");
        IFn runTests = Clojure.var("clojure.test", "run-tests");

        StringBuilder summary = new StringBuilder();
        boolean failed = false;
        for (String namespace : TEST_NAMESPACES) {
            Object namespaceSymbol = Clojure.read(namespace);
            require.invoke(namespaceSymbol);
            IPersistentMap result = (IPersistentMap) runTests.invoke(namespaceSymbol);
            long tests = (Long) result.valAt(Clojure.read(":test"));
            long fails = (Long) result.valAt(Clojure.read(":fail"));
            long errors = (Long) result.valAt(Clojure.read(":error"));
            failed |= fails + errors > 0;
            summary.append(namespace).append(": ").append(tests).append(" tests, ")
                    .append(fails).append(" failures, ").append(errors).append(" errors; ");
        }

        if (failed) {
            throw new RuntimeException("Clojure tests failed: " + summary);
        }
        System.out.println("Clojure tests passed: " + summary);
    }
}
