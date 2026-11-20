package org.freeplane.plugin.clojure;

import clojure.java.api.Clojure;
import clojure.lang.Namespace;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;

final class ClojureUserNamespace {

    private static final class Holder {
        static final Namespace USER = createUserNamespace();

        private static Namespace createUserNamespace() {
            Namespace user = Namespace.findOrCreate(Symbol.intern("user"));
            Var.pushThreadBindings(RT.map(RT.CURRENT_NS, user));
            try {
                Clojure.var("clojure.core", "refer").invoke(Symbol.intern("clojure.core"));
            } finally {
                Var.popThreadBindings();
            }
            return user;
        }
    }

    private ClojureUserNamespace() {
    }

    static Namespace get() {
        return Holder.USER;
    }
}
