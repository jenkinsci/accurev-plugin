package hudson.plugins.accurev.extensions;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;

import jenkins.plugins.accurev.AccurevClient;
import jenkins.plugins.accurev.HistCommand;
import jenkins.plugins.accurev.PopulateCommand;
import jenkins.plugins.accurev.StreamsCommand;
import jenkins.plugins.accurev.UpdateCommand;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevTransaction;
import hudson.plugins.accurev.UserRemoteConfig;

public abstract class AccurevSCMExtension extends AbstractDescribableImpl<AccurevSCMExtension> {
    /**
     * @return <code>true</code> when this extension has a requirement to get a workspace during polling,
     * typically as it has to check for incoming changes, not just remote HEAD.
     */
    public boolean requiresWorkspaceForPolling() {
        return false;
    }

    public boolean isRevExcluded(AccurevSCM scm, AccurevClient accurev, AccurevTransaction transaction, TaskListener listener) {
        return false;
    }

    public void beforeCheckout(AccurevSCM scm, Run<?, ?> build, AccurevClient accurev, TaskListener listener) {

    }

    public void onCheckoutCompleted(AccurevSCM scm, Run<?, ?> build, AccurevClient accurev, TaskListener listener) {

    }

    public void decorateStreamsCommand(AccurevSCM scm, UserRemoteConfig config, Run<?, ?> build, AccurevClient accurev, TaskListener listener, StreamsCommand cmd) {

    }

    public void decoratePopulateCommand(AccurevSCM scm, UserRemoteConfig config, Run<?, ?> build, AccurevClient accurev, TaskListener listener, PopulateCommand cmd) {

    }

    public void decorateHistCommand(AccurevSCM scm, UserRemoteConfig config, Run<?, ?> build, AccurevClient accurev, TaskListener listener, HistCommand cmd) {
        decorateHistCommand(scm, config, build.getParent(), accurev, listener, cmd);
    }

    public void decorateHistCommand(AccurevSCM scm, UserRemoteConfig config, Job project, AccurevClient accurev, TaskListener listener, HistCommand cmd) {

    }

    public void decorateUpdateCommand(AccurevSCM scm, Job project, AccurevClient accurev, TaskListener listener, UpdateCommand cmd) {

    }

    public void decorateUpdateCommand(AccurevSCM scm, Run<?, ?> build, AccurevClient accurev, TaskListener listener, UpdateCommand cmd) {

    }

    @Override
    public AccurevSCMExtensionDescriptor getDescriptor() {
        return (AccurevSCMExtensionDescriptor) super.getDescriptor();
    }
}
