package jenkins.plugins.accurev;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;

/**
 * Initialized by josep on 17-02-2017.
 */
public class CliAccurevAPIImpl implements AccurevClient {
    final String exe;
    final FilePath workspace;
    final TaskListener listener;
    final EnvVars env;
    public CliAccurevAPIImpl(String exe, FilePath workspace, TaskListener listener, EnvVars env) {
        this.exe = exe;
        this.workspace = workspace;
        this.listener = listener;
        this.env = env;
    }
}
