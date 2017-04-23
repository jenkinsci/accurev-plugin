package hudson.plugins.accurev.extensions.impl;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.UserRemoteConfig;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;
import jenkins.plugins.accurev.AccurevClient;
import jenkins.plugins.accurev.HistCommand;
import jenkins.plugins.accurev.StreamsCommand;
import org.kohsuke.stapler.DataBoundConstructor;

public class IgnoreParentStream extends AccurevSCMExtension {

    @DataBoundConstructor
    public IgnoreParentStream() {
    }

    @Override
    public void decorateStreamsCommand(AccurevSCM scm,
                                       UserRemoteConfig config, Run<?, ?> build,
                                       AccurevClient accurev,
                                       TaskListener listener,
                                       StreamsCommand cmd) {
        cmd.stream(config.getStream());
    }

    @Override
    public void decorateHistCommand(AccurevSCM scm, UserRemoteConfig config, Job project, AccurevClient accurev, TaskListener listener, HistCommand cmd) {
        cmd.stream(config.getStream());
    }

    @Extension
    public static class DescriptorImpl extends AccurevSCMExtensionDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Ignore parent changes";
        }
    }
}
