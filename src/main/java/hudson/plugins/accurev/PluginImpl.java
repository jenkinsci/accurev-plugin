package hudson.plugins.accurev;

import hudson.Plugin;
import hudson.scm.SCMS;

/**
 * Entry point of plugin.
 *
 * @author Stephen Connolly
 * @plugin
 */
public class PluginImpl extends Plugin {
    @Override
    public void start() throws Exception {
        super.start();
        SCMS.SCMS.add(AccurevSCM.DESCRIPTOR);
    }
}
