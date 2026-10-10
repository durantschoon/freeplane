package org.freeplane.plugin.clojure;

import org.junit.Test;
import clojure.java.api.Clojure;
import clojure.lang.IFn;

public class ClojureTestRunner {
    @Test
    public void runClojureTests() {
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        
        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("org.freeplane.plugin.clojure.test-main"));
        
        IFn runTests = Clojure.var("clojure.test", "run-tests");
        Object result = runTests.invoke(Clojure.read("org.freeplane.plugin.clojure.test-main"));
        
        // Check verification result map { :test x, :pass y, :fail z, :error w }
        clojure.lang.IPersistentMap res = (clojure.lang.IPersistentMap) result;
        Long fails = (Long) res.valAt(Clojure.read(":fail"));
        Long errors = (Long) res.valAt(Clojure.read(":error"));
        
        if (fails + errors > 0) {
            throw new RuntimeException("Clojure tests failed: " + fails + " failures, " + errors + " errors.");
        }
    }
}
