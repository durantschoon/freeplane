package org.freeplane.plugin.clojure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import clojure.lang.IObj;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.PersistentList;
import clojure.lang.PersistentVector;
import clojure.lang.RT;
import clojure.lang.Symbol;

final class ClojureFunctionForm {

    private static final Symbol FN = Symbol.intern("clojure.core", "fn");
    private static final Symbol LET = Symbol.intern("clojure.core", "let");
    private static final Symbol GET = Symbol.intern("clojure.core", "get");
    private static final Symbol DO = Symbol.intern("do");
    private static final Keyword LINE = Keyword.intern("line");
    private static final Symbol BINDINGS = Symbol.intern("bindings");

    private ClojureFunctionForm() {
    }

    private static Object carryingLine(Object form) {
        if (!(form instanceof IObj) || form instanceof ISeq) {
            return form;
        }
        Object line = RT.get(((IObj) form).meta(), LINE);
        if (line == null) {
            return form;
        }
        return ((IObj) PersistentList.create(Arrays.<Object>asList(DO, form)))
                .withMeta(RT.map(LINE, line));
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
            let.add(carryingLine(form));
        }
        return PersistentList.create(Arrays.<Object>asList(
                FN, PersistentVector.create(BINDINGS), PersistentList.create(let)));
    }
}
