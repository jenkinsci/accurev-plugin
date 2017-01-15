package hudson.plugins.accurev.extensions.impl;

import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author josp
 */
public class AccurevDepot extends AccurevSCMExtension {

    @DataBoundConstructor
    public AccurevDepot() {

    }

    public static class DescriptorImpl extends AccurevSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Accurev Depot";
        }
    }
}
