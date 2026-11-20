package org.freeplane.plugin.clojure;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.script.ScriptException;

import clojure.lang.IObj;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.LineNumberingPushbackReader;
import clojure.lang.LispReader;
import clojure.lang.RT;
import clojure.lang.Var;

final class ClojureScriptReader {

    private static final Keyword LINE = Keyword.intern("line");
    private static final Object END_OF_SCRIPT = new Object();

    private ClojureScriptReader() {
    }

    static List<Object> readForms(String script) throws ScriptException {
        LineNumberingPushbackReader reader = new LineNumberingPushbackReader(new StringReader(script));
        List<Object> forms = new ArrayList<Object>();
        Var.pushThreadBindings(RT.map(RT.READEVAL, Boolean.FALSE));
        try {
            int startLine = lineOfNextForm(reader);
            Object form = LispReader.read(reader, false, END_OF_SCRIPT, false);
            while (form != END_OF_SCRIPT) {
                forms.add(withLine(form, startLine));
                startLine = lineOfNextForm(reader);
                form = LispReader.read(reader, false, END_OF_SCRIPT, false);
            }
            return forms;
        } catch (LispReader.ReaderException e) {
            throw withCause(new ScriptException(e.getMessage(), null, e.line), e);
        } catch (Exception e) {
            throw withCause(new ScriptException(e.getMessage(), null, reader.getLineNumber()), e);
        } finally {
            Var.popThreadBindings();
        }
    }

    private static int lineOfNextForm(LineNumberingPushbackReader reader) throws IOException {
        int next = reader.read();
        while (next != -1 && (Character.isWhitespace(next) || next == ',' || next == ';')) {
            if (next == ';') {
                while (next != -1 && next != '\n') {
                    next = reader.read();
                }
            } else {
                next = reader.read();
            }
        }
        int line = reader.getLineNumber();
        if (next != -1) {
            reader.unread(next);
        }
        return line;
    }

    private static Object withLine(Object form, int line) {
        if (!(form instanceof IObj) || form instanceof ISeq) {
            return form;
        }
        IObj withMeta = (IObj) form;
        if (RT.get(withMeta.meta(), LINE) != null) {
            return form;
        }
        return withMeta.withMeta((IPersistentMap) RT.assoc(withMeta.meta(), LINE, line));
    }

    private static ScriptException withCause(ScriptException exception, Throwable cause) {
        exception.initCause(cause);
        return exception;
    }
}
