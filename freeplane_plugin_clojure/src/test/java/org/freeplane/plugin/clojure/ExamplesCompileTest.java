package org.freeplane.plugin.clojure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import clojure.java.api.Clojure;
import clojure.lang.Namespace;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;

public class ExamplesCompileTest {

    private static final List<String> BINDING_NAMES = Arrays.asList(
            "c", "node", "logger", "ui", "htmlUtils", "textUtils", "menuUtils", "scriptUtils", "config");

    // build.gradle sets this system property to the absolute path of freeplane_plugin_clojure/examples
    private static final String EXAMPLES_DIR_PROPERTY = "clojure.examples.dir";

    private static File[] exampleFiles() {
        File directory = new File(System.getProperty(EXAMPLES_DIR_PROPERTY, "examples"));
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".clj"));
        assertThat(files).as("example scripts in " + directory).isNotNull();
        Arrays.sort(files);
        return files;
    }

    private static void ensureFpAlias(Namespace user) {
        Clojure.var("clojure.core", "require").invoke(Symbol.intern("freeplane.script"));
        Namespace script = Namespace.find(Symbol.intern("freeplane.script"));
        if (user.lookupAlias(Symbol.intern("fp")) == null) {
            user.addAlias(Symbol.intern("fp"), script);
        }
    }

    @Test
    public void everyExampleCompilesInUserNamespaceWithFpAlias() throws Exception {
        File[] files = exampleFiles();
        assertThat(files.length).isGreaterThanOrEqualTo(3);
        Namespace user = ClojureUserNamespace.get();
        List<String> compiled = new ArrayList<String>();
        Var.pushThreadBindings(RT.map(RT.CURRENT_NS, user));
        try {
            ensureFpAlias(user);
            for (File file : files) {
                String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                Object form = ClojureFunctionForm.of(ClojureScriptReader.readForms(source), BINDING_NAMES);
                try {
                    Clojure.var("clojure.core", "eval").invoke(form);
                } catch (RuntimeException e) {
                    throw new AssertionError("example " + file.getName() + " does not compile: " + e, e);
                }
                compiled.add(file.getName());
            }
        } finally {
            Var.popThreadBindings();
        }
        assertThat(compiled).contains("hello.clj", "basic.clj", "etl_sum.clj");
    }
}
