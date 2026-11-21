package org.freeplane.plugin.clojure;

import javax.script.ScriptException;

import org.freeplane.core.util.LogUtils;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.Keyword;
import clojure.lang.Namespace;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;

final class ClojureUserNamespace {

    private static final class Outcome {
        final Namespace namespace;
        final String failureMessage;
        final Throwable failureCause;

        private Outcome(Namespace namespace, String failureMessage, Throwable failureCause) {
            this.namespace = namespace;
            this.failureMessage = failureMessage;
            this.failureCause = failureCause;
        }

        static Outcome ready(Namespace namespace) {
            return new Outcome(namespace, null, null);
        }

        static Outcome failed(String message, Throwable cause) {
            return new Outcome(null, message, cause);
        }
    }

    private static final class Holder {
        static final Outcome OUTCOME = setUp();

        private static Outcome setUp() {
            try {
                return createUserNamespace();
            } catch (Throwable failure) {
                return logged(Outcome.failed("Clojure plugin setup failed: " + failure, failure));
            }
        }

        private static Outcome createUserNamespace() {
            String clojureVersion = (String) Clojure.var("clojure.core", "clojure-version").invoke();
            String problem = startupProblem(clojureVersion);
            if (problem != null) {
                return logged(Outcome.failed(problem, new IllegalStateException(problem)));
            }
            Object newerThanTested = fencesFunction("newer-than-tested").invoke(Keyword.intern("clojure"), clojureVersion);
            if (newerThanTested != null) {
                LogUtils.warn(String.valueOf(newerThanTested));
            }
            Namespace user = Namespace.findOrCreate(Symbol.intern("user"));
            Var.pushThreadBindings(RT.map(RT.CURRENT_NS, user));
            try {
                Clojure.var("clojure.core", "refer").invoke(Symbol.intern("clojure.core"));
                Clojure.var("clojure.core", "require").invoke(Symbol.intern("freeplane.script"));
                Clojure.var("clojure.core", "alias").invoke(Symbol.intern("fp"), Symbol.intern("freeplane.script"));
            } finally {
                Var.popThreadBindings();
            }
            return Outcome.ready(user);
        }

        private static Outcome logged(Outcome failure) {
            LogUtils.warn(failure.failureMessage, failure.failureCause);
            return failure;
        }
    }

    private ClojureUserNamespace() {
    }

    static Namespace get() throws ScriptException {
        Outcome outcome = Holder.OUTCOME;
        if (outcome.namespace != null) {
            return outcome.namespace;
        }
        ScriptException failure = new ScriptException(outcome.failureMessage);
        failure.initCause(outcome.failureCause);
        throw failure;
    }

    static String startupProblem(String clojureVersion) {
        Object violation = fencesFunction("floor-violation").invoke(Keyword.intern("clojure"), clojureVersion);
        return violation == null ? null : ((Throwable) violation).getMessage();
    }

    private static IFn fencesFunction(String name) {
        Clojure.var("clojure.core", "require").invoke(Symbol.intern("freeplane.script.fences"));
        return Clojure.var("freeplane.script.fences", name);
    }
}
