package bndtools.classpath;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
    private static final String BUNDLE_NAME = "bndtools.classpath.messages"; //$NON-NLS-1$
    public static String BndContainer_ContainerName;
    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {}
}
