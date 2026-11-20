package org.freeplane.plugin.clojure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import clojure.lang.PersistentList;
import clojure.lang.PersistentVector;
import clojure.lang.Symbol;

final class ClojureFunctionForm {

    private static final Symbol FN = Symbol.intern("clojure.core", "fn");
    private static final Symbol LET = Symbol.intern("clojure.core", "let");
    private static final Symbol GET = Symbol.intern("clojure.core", "get");
    private static final Symbol BINDINGS = Symbol.intern("bindings");

    private ClojureFunctionForm() {
    }

    static Object of(List<Object> forms, List<String> bindableNames) {
        List<Object> letBindings = new ArrayList<Object>();
        for (String name : bindableNames) {
            letBindings.add(Symbol.intern(name));
            letBindings.add(PersistentList.create(Arrays.<Object>asList(GET, BINDINGS, name)));
        }
        List<Object> let = new ArrayList<Object>();
        let.add(LET);
        let.add(PersistentVector.create(letBindings));
        for (Object form : forms) {
            let.add(form);
        }
        return PersistentList.create(Arrays.<Object>asList(
                FN, PersistentVector.create(BINDINGS), PersistentList.create(let)));
    }
}
