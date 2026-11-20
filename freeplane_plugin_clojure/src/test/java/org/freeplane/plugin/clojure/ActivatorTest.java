package org.freeplane.plugin.clojure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.Test;
import org.osgi.framework.BundleContext;

public class ActivatorTest {

    @Test
    public void startAndStopCompleteWithoutTouchingTheContext() throws Exception {
        BundleContext context = mock(BundleContext.class);
        Activator activator = new Activator();

        activator.start(context);
        activator.stop(context);

        verifyNoInteractions(context);
    }
}
