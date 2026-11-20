package org.freeplane.plugin.clojure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Symbol;

public class ClojureFunctionFormTest {

    private static Object read(String text) {
        return Clojure.var("clojure.core", "read-string").invoke(text);
    }

    @Test
    public void evaluatesToFunctionOverBindingsMap() throws Exception {
        Object form = ClojureFunctionForm.of(
                Collections.singletonList(read("(+ node c)")), Arrays.asList("node", "c"));
        IFn function = (IFn) Clojure.var("clojure.core", "eval").invoke(form);
        Map<String, Object> bindings = new HashMap<String, Object>();
        bindings.put("node", 1);
        bindings.put("c", 2);
        assertThat(function.invoke(bindings)).isEqualTo(3L);
    }

    @Test
    public void withoutNamesAndFormsReturnsNil() throws Exception {
        Object form = ClojureFunctionForm.of(Collections.<Object>emptyList(), Collections.<String>emptyList());
        IFn function = (IFn) Clojure.var("clojure.core", "eval").invoke(form);
        assertThat(function.invoke(new HashMap<String, Object>())).isNull();
    }

    @Test
    public void shapeIsFnOfBindingsAroundLetWithNamePairs() {
        List<Object> forms = Collections.singletonList(read("1"));
        Object form = ClojureFunctionForm.of(forms, Arrays.asList("node"));
        assertThat(((Symbol) RT.first(form)).getName()).isEqualTo("fn");
        assertThat(RT.second(form).toString()).isEqualTo("[bindings]");
        Object let = RT.third(form);
        assertThat(((Symbol) RT.first(let)).getName()).isEqualTo("let");
        assertThat(RT.second(let).toString()).isEqualTo("[node (clojure.core/get bindings \"node\")]");
        assertThat(RT.third(let)).isEqualTo(1L);
    }
}
