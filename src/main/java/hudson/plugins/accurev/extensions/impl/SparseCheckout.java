package hudson.plugins.accurev.extensions.impl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;

import jenkins.plugins.accurev.AccurevClient;
import jenkins.plugins.accurev.PopulateCommand;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.UserRemoteConfig;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;

public class SparseCheckout extends AccurevSCMExtension {

    private String paths;

    @DataBoundConstructor
    public SparseCheckout(String paths) {
        this.paths = Util.fixEmptyAndTrim(paths);
    }

    public String getPaths() {
        return paths;
    }

    private String[] normalize(String s) {
        return StringUtils.isBlank(s) ? null : s.split("[\\r\\n]+");
    }

    public Set<String> getPathsNormalized() {
        String[] normalize = normalize(paths);
        if (normalize == null) return null;
        return new HashSet<>(Arrays.asList(normalize));
    }

    @Override
    public void decoratePopulateCommand(AccurevSCM scm, UserRemoteConfig config, Run<?, ?> build, AccurevClient accurev, TaskListener listener, PopulateCommand cmd) {
        cmd.elements(getPathsNormalized());
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
