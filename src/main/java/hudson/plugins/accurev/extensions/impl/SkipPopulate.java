package hudson.plugins.accurev.extensions.impl;

import hudson.Extension;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

public class SkipPopulate extends AccurevSCMExtension {

    @DataBoundConstructor
    public SkipPopulate() {
    }

    @Extension
    public static class DescriptorImpl extends AccurevSCMExtensionDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Do not populate";
        }
    }
}
