package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.Test;
import org.mockito.MockedStatic;

public class ClojureGenericScriptTest {

    private MockedStatic<ResourceController> resourceController;
    private NodeModel node;
    private List<Integer> gotoLines;

    @Before
    public void setUp() {
        resourceController = mockStatic(ResourceController.class);
        resourceController.when(ResourceController::getResourceController).thenReturn(new TestResourceController());
        if (ScriptResources.getClasspath() == null) {
            ScriptResources.setClasspath(Collections.<String>emptyList());
        }
        node = new NodeModel("node text", new MapModel((source, targetMap, withChildren) -> null, null, null));
        gotoLines = new ArrayList<Integer>();
    }

    @After
    public void tearDown() {
        resourceController.close();
    }

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    // The String constructor of GenericScript cannot run a compilable engine:
    // compileAndCache rereads a null file. Use the File constructor instead.
    private Object run(String source, ScriptContext scriptContext) throws IOException {
        File scriptFile = temporaryFolder.newFile();
        File cljFile = new File(scriptFile.getParentFile(), scriptFile.getName() + ".clj");
        Files.write(cljFile.toPath(), source.getBytes(StandardCharsets.UTF_8));
        GenericScript script = new GenericScript(cljFile, ScriptingPermissions.getPermissiveScriptingPermissions());
        return script.execute(node, new PrintStream(new ByteArrayOutputStream()), gotoLines::add, scriptContext);
    }

    @Test
    public void inMemorySourceExecutesThroughStringConstructor() {
        GenericScript script = new GenericScript("(+ 1 2)", "clj", ScriptingPermissions.getPermissiveScriptingPermissions());
        Object result = script.execute(node, new PrintStream(new ByteArrayOutputStream()), gotoLines::add, new ScriptContext(null));
        assertThat(result).isEqualTo(3L);
    }

    @Test
    public void evaluatesArithmetic() throws Exception {
        assertThat(run("(+ 1 2)", new ScriptContext(null))).isEqualTo(3L);
    }

    @Test
    public void nodeProxyIsBound() throws Exception {
        assertThat(run("(.getText node)", new ScriptContext(null))).isEqualTo("node text");
    }

    @Test
    public void controllerAndStaticPropertiesAreBound() throws Exception {
        assertThat(run("(and (some? c) (some? textUtils))", new ScriptContext(null))).isEqualTo(Boolean.TRUE);
    }

    @Test
    public void scriptContextBoundVariableIsVisible() throws Exception {
        ScriptContext scriptContext = new ScriptContext(null)
            .withBoundVariables(Collections.<String, Object>singletonMap("args", "some args"));
        assertThat(run("args", scriptContext)).isEqualTo("some args");
    }

    @Test
    public void readerErrorReportsLineThroughErrorHandler() {
        assertThatThrownBy(() -> run("1\n2\n)", new ScriptContext(null)))
            .isInstanceOf(ExecuteScriptException.class);
        assertThat(gotoLines).containsExactly(3);
    }

    // Observed on the test classpath only: every GenericScript shares one Clojure runtime and one user namespace.
    @Test
    public void scriptsShareOneClojureRuntimeWhenClojureIsOnTheTestClasspath() throws Exception {
        Object first = run("(System/identityHashCode (class clojure.lang.RT))", new ScriptContext(null));
        Object second = run("(System/identityHashCode (class clojure.lang.RT))", new ScriptContext(null));
        assertThat(first).isEqualTo(second);
        run("(def shared-between-scripts 42)", new ScriptContext(null));
        assertThat(run("shared-between-scripts", new ScriptContext(null))).isEqualTo(42L);
    }

    @Test
    public void fpHelperIsAvailableThroughGenericScript() throws Exception {
        assertThat(run("(fp/text node)", new ScriptContext(null))).isEqualTo("node text");
    }

    @Test
    public void printlnReachesExecuteOutputStream() throws Exception {
        File cljFile = temporaryFolder.newFile("out.clj");
        Files.write(cljFile.toPath(), "(println \"out\")".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        new GenericScript(cljFile, ScriptingPermissions.getPermissiveScriptingPermissions())
            .execute(node, new PrintStream(captured), gotoLines::add, new ScriptContext(null));
        assertThat(captured.toString()).isEqualTo("out" + System.lineSeparator());
    }

    private static class TestResourceController extends ResourceController {
        private final Properties unsecuredProperties = new Properties();
        private final Properties securedProperties = new Properties();

        private TestResourceController() {
            super();
            unsecuredProperties.setProperty("compiled_script_cache_size", "8");
        }

        @Override
        public String getFreeplaneUserDirectory() {
            return System.getProperty("java.io.tmpdir");
        }

        @Override
        public Properties getUnsecuredProperties() {
            return unsecuredProperties;
        }

        @Override
        public String getProperty(String key) {
            return unsecuredProperties.getProperty(key);
        }

        @Override
        public void removeUserProperty(String key) {
            unsecuredProperties.remove(key);
            securedProperties.remove(key);
        }

        @Override
        public void saveProperties() {
        }

        @Override
        public void setDefaultProperty(String key, String value) {
            unsecuredProperties.setProperty(key, value);
        }

        @Override
        public void setProperty(String property, String value) {
            unsecuredProperties.setProperty(property, value);
        }

        @Override
        public Properties getSecuredProperties() {
            return securedProperties;
        }

        @Override
        public void securePropertyForModification(String key) {
        }

        @Override
        public void securePropertyForReadingAndModification(String key) {
        }

        @Override
        public void persistPropertyInSecretsFile(String key) {
        }
    }
}
