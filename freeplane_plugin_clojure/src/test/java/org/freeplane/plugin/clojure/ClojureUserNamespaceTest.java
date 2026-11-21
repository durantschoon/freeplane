package org.freeplane.plugin.clojure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.script.ScriptException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import clojure.java.api.Clojure;
import clojure.lang.Symbol;

public class ClojureUserNamespaceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

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

    @Test
    public void setupFailureIsReportedOnEveryCallWithStructuredCause() throws Exception {
        String foundVersion = (String) Clojure.var("clojure.core", "clojure-version").invoke();
        File overrideDir = temporaryFolder.newFolder("override");
        File fences = new File(overrideDir, "freeplane/script/fences.clj");
        fences.getParentFile().mkdirs();
        Files.write(fences.toPath(), flooredAt99Fences().getBytes(StandardCharsets.UTF_8));

        List<URL> urls = new ArrayList<URL>();
        urls.add(overrideDir.toURI().toURL());
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            urls.add(new File(entry).toURI().toURL());
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader isolated = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getSystemClassLoader().getParent())) {
            Thread.currentThread().setContextClassLoader(isolated);
            Method get = Class.forName(ClojureUserNamespace.class.getName(), true, isolated).getDeclaredMethod("get");
            get.setAccessible(true);
            List<Throwable> failures = new ArrayList<Throwable>();
            for (int call = 0; call < 3; call++) {
                try {
                    get.invoke(null);
                    throw new AssertionError("get() should fail");
                } catch (InvocationTargetException e) {
                    failures.add(e.getCause());
                }
            }
            for (Throwable failure : failures) {
                assertThat(failure).isInstanceOf(ScriptException.class);
                assertThat(failure.getMessage())
                        .isEqualTo("clojure " + foundVersion + " is older than the supported minimum 99.0.0");
                Throwable cause = failure.getCause();
                assertThat(cause.getClass().getName()).isEqualTo("clojure.lang.ExceptionInfo");
                assertThat(dataValue(isolated, cause, ":fence")).hasToString(":floor");
                assertThat(dataValue(isolated, cause, ":needed")).isEqualTo("99.0.0");
            }
            assertThat(failures.get(0)).isNotSameAs(failures.get(1));
            assertThat(failures.get(1)).isNotSameAs(failures.get(2));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static String flooredAt99Fences() throws Exception {
        try (InputStream stream = ClojureUserNamespaceTest.class.getClassLoader()
                .getResourceAsStream("freeplane/script/fences.clj")) {
            ByteArrayOutputStreamHolder copy = new ByteArrayOutputStreamHolder();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                copy.out.write(buffer, 0, read);
            }
            String original = new String(copy.out.toByteArray(), StandardCharsets.UTF_8);
            String patched = original.replace(":clojure   {:floor \"1.12.0\"", ":clojure   {:floor \"99.0.0\"");
            assertThat(patched).isNotEqualTo(original);
            return patched;
        }
    }

    private static final class ByteArrayOutputStreamHolder {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    }

    private static Object dataValue(ClassLoader isolated, Throwable exceptionInfo, String keyName) throws Exception {
        Object data = exceptionInfo.getClass().getMethod("getData").invoke(exceptionInfo);
        Object key = Class.forName("clojure.java.api.Clojure", true, isolated)
                .getMethod("read", String.class).invoke(null, keyName);
        Method valAt = Class.forName("clojure.lang.ILookup", true, isolated).getMethod("valAt", Object.class);
        return valAt.invoke(data, key);
    }
}
