package org.freeplane.plugin.clojure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import javax.script.ScriptException;

import org.junit.Test;

import clojure.lang.IMeta;
import clojure.lang.Keyword;
import clojure.lang.RT;

public class ClojureScriptReaderTest {

    @Test
    public void readsAllTopLevelFormsInOrder() throws Exception {
        List<Object> forms = ClojureScriptReader.readForms("1 (+ 1 2) :k");
        assertThat(forms).hasSize(3);
        assertThat(forms.get(0)).isEqualTo(1L);
        assertThat(forms.get(2)).isEqualTo(Keyword.intern("k"));
    }

    @Test
    public void emptyAndCommentOnlyScriptsGiveNoForms() throws Exception {
        assertThat(ClojureScriptReader.readForms("")).isEmpty();
        assertThat(ClojureScriptReader.readForms("; only a comment")).isEmpty();
    }

    @Test
    public void formCarriesItsLineMetadata() throws Exception {
        Object form = ClojureScriptReader.readForms("1\n(+ 1 2)").get(1);
        assertThat(RT.get(((IMeta) form).meta(), Keyword.intern("line"))).isEqualTo(2);
    }

    @Test
    public void nonListFormIsWrappedInDoCarryingLineAndLeftUntouched() throws Exception {
        Object form = ClojureScriptReader.readForms("1\n[1 2]").get(1);
        assertThat(RT.get(((IMeta) form).meta(), Keyword.intern("line"))).isEqualTo(2);
        assertThat(RT.first(form).toString()).isEqualTo("do");
        assertThat(((IMeta) RT.second(form)).meta()).isNull();
    }

    @Test
    public void readEvalIsDisabled() {
        assertThatThrownBy(() -> ClojureScriptReader.readForms(
                "#=(java.lang.System/setProperty \"stage03.readeval\" \"ran\")"))
            .isInstanceOf(ScriptException.class);
        assertThat(System.getProperty("stage03.readeval")).isNull();
    }

    @Test
    public void readerFailureReportsLineAndCause() {
        assertThatThrownBy(() -> ClojureScriptReader.readForms("1\n2\n)"))
            .isInstanceOf(ScriptException.class)
            .satisfies(e -> assertThat(((ScriptException) e).getLineNumber()).isEqualTo(3))
            .hasCauseInstanceOf(Exception.class);
    }
}
