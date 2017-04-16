package hudson.plugins.accurev.extensions;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevTransaction;
import jenkins.plugins.accurev.AccurevClient;

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

    @Override
    public AccurevSCMExtensionDescriptor getDescriptor() {
        return (AccurevSCMExtensionDescriptor) super.getDescriptor();
    }
}
