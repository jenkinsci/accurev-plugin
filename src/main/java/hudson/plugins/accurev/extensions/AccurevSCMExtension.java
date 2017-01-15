package hudson.plugins.accurev.extensions;

import hudson.model.AbstractDescribableImpl;

/**
 * @author josp
 */
public abstract class AccurevSCMExtension extends AbstractDescribableImpl<AccurevSCMExtension> {
    @Override
    public AccurevSCMExtensionDescriptor getDescriptor() {
        return (AccurevSCMExtensionDescriptor) super.getDescriptor();
    }
}
