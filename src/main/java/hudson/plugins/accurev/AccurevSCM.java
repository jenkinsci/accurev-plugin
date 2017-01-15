package hudson.plugins.accurev;


import hudson.Extension;
import hudson.Util;
import hudson.model.Job;
import hudson.model.Saveable;
import hudson.plugins.accurev.browser.AccurevRepositoryBrowser;
import hudson.plugins.accurev.config.AccurevServerConfig;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;
import hudson.scm.SCMDescriptor;
import hudson.util.DescribableList;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

/**
 * @author josp
 */
public class AccurevSCM extends AccurevSCMBackwardCompatibility {

    private Long configVersion;

    /**
     * All the remote accurev servers we care about
     */
    private List<AccurevServerConfig> configs;

    /**
     * All the configured extensions attached to this.
     */
    private DescribableList<AccurevSCMExtension, AccurevSCMExtensionDescriptor> extensions;

    @DataBoundConstructor
    public AccurevSCM(
            List<AccurevServerConfig> configs,
            @Nonnull List<AccurevSCMExtension> extensions) {
        this.configs = configs;
        this.extensions = new DescribableList<>(Saveable.NOOP, Util.fixNull(extensions));
    }

    @Override
    DescribableList<AccurevSCMExtension, AccurevSCMExtensionDescriptor> getExtensions() {
        return extensions;
    }

    public Object readResolve() throws IOException {
        // Migrate

        if (serverName != null)

        if (extensions==null)
            extensions = new DescribableList<>(Saveable.NOOP);

        migrateThyLegacy();
        return this;
    }

    @Extension
    public static final class AccurevSCMDescriptor extends SCMDescriptor<AccurevSCM> {

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
        transient boolean pollOnMaster;

    }
}
