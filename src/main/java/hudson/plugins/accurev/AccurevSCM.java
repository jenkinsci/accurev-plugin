package hudson.plugins.accurev;


import hudson.Extension;
import hudson.Util;
import hudson.model.Job;
import hudson.model.Saveable;
import hudson.plugins.accurev.browser.AccurevRepositoryBrowser;
import hudson.plugins.accurev.config.AccurevServerConfig;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;
import hudson.plugins.accurev.extensions.impl.AccurevDepot;
import hudson.scm.SCMDescriptor;
import hudson.util.DescribableList;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author josp
 */
public class AccurevSCM extends AccurevSCMBackwardCompatibility {

    private static final Logger LOGGER = Logger.getLogger(AccurevSCM.class.getName());
    private Long configVersion;

    private AccurevServerConfig config;
    private AccurevDepot depot;

    /**
     * All the configured extensions attached to this.
     */
    private DescribableList<AccurevSCMExtension, AccurevSCMExtensionDescriptor> extensions;

    @DataBoundConstructor
    public AccurevSCM(
            AccurevServerConfig config,
            @Nonnull List<AccurevSCMExtension> extensions) {
        this.config = config;
        this.extensions = new DescribableList<>(Saveable.NOOP, Util.fixNull(extensions));
    }

    @Override
    DescribableList<AccurevSCMExtension, AccurevSCMExtensionDescriptor> getExtensions() {
        return extensions;
    }

    public Object readResolve() throws IOException {
        // Migrate
        LOGGER.info("hello");
        if (extensions==null)
            extensions = new DescribableList<>(Saveable.NOOP);

        return this;
    }

    public AccurevServerConfig getConfig() {
        return config;
    }

    public AccurevDepot getDepot() {
        return depot;
    }

    @Extension
    public static class AccurevSCMDescriptor extends SCMDescriptor<AccurevSCM> {

        public AccurevSCMDescriptor() {
            super(AccurevSCM.class, AccurevRepositoryBrowser.class);
        }

        @Override
        public String getDisplayName() {
            return "AccuRev";
        }

        @Override public boolean isApplicable(Job project) {
            return true;
        }

        public List<AccurevSCMExtensionDescriptor> getExtensionDescriptors() {
            return AccurevSCMExtensionDescriptor.all();
        }

        // TODO: Implement AccurevTool

        /**
         * Determine the browser from the scmData contained in the {@link StaplerRequest}.
         *
         * @param scmData data read for SCM browser
         * @return browser based on request scmData
         */
        private AccurevRepositoryBrowser getBrowserFromRequest(final StaplerRequest req, final JSONObject scmData) {
            if (scmData.containsKey("browser")) {
                return req.bindJSON(AccurevRepositoryBrowser.class, scmData.getJSONObject("browser"));
            } else {
                return null;
            }
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindJSON(this, formData);
            save();
            return true;
        }

        transient List<AccurevServer> servers;
        transient List<AccurevServer> _servers;
    }

    // --------------------------- Inner Class ---------------------------------------------------
    public static final class AccurevServer implements Serializable {

        @Deprecated
        transient String name;
        @Deprecated
        transient String host;
        @Deprecated
        transient int port;
        @Deprecated
        transient String username;
        @Deprecated
        transient String password;
        @Deprecated
        transient String uuid;
        @Deprecated
        transient String validTransactionTypes;
        @Deprecated
        transient boolean syncOperations;
        @Deprecated
        transient boolean minimiseLogins;
        @Deprecated
        transient boolean useNonexpiringLogin;
        @Deprecated
        transient boolean useRestrictedShowStreams;
        @Deprecated
        transient boolean useColor;
        @Deprecated
        transient boolean usePromoteListen;

        @Deprecated
        public String getHost() {
            return host;
        }

        public boolean isUsePromoteListen() {
            return usePromoteListen;
        }

        @Deprecated
        public String getUUID() {
            return uuid;
        }

        @Deprecated
        public String getName() {
            return name;
        }

        @Deprecated
        public boolean isUseRestrictedShowStreams() {
            return useRestrictedShowStreams;
        }

        public boolean isUseColor() {
            return useColor;
        }

        public String getUsername() {
            return username;
        }

        public int getPort() {
            return port;
        }

        public boolean isMinimiseLogins() {
            return minimiseLogins;
        }

        public boolean isUseNonexpiringLogin() {
            return useNonexpiringLogin;
        }

        @Deprecated
        public String getPassword() {
            return AccurevSCMBackwardCompatibility.deobfuscate(password);
        }
    }
}
