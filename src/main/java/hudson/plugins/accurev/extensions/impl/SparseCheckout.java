package hudson.plugins.accurev.extensions.impl;

import hudson.Extension;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

public class SparseCheckout extends AccurevSCMExtension {

    private String paths;

    @DataBoundConstructor
    public SparseCheckout(String paths) {
        this.paths = paths;
    }

    public String getPaths() {
        return paths;
    }

    @Extension
    public static class DescriptorImpl extends AccurevSCMExtensionDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Sparse checkout";
        }
    }
}
