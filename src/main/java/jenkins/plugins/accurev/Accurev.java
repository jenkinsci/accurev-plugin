package jenkins.plugins.accurev;

import java.io.File;

import org.apache.commons.lang.StringUtils;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;

/**
 * Initialized by josep on 04-03-2017.
 */
public class Accurev {
    private FilePath workspace;
    private TaskListener listener;
    private EnvVars env;
    private String exe;
    private String url;

    public Accurev(TaskListener listener, EnvVars env) {
        this.listener = listener;
        this.env = env;
    }

    public static Accurev with(TaskListener listener, EnvVars env) {
        return new Accurev(listener, env);
    }

    public Accurev in(File workspace) {
        return in(new FilePath(workspace));
    }

    public Accurev in(FilePath workspace) {
        this.workspace = workspace;
        return this;
    }

    public Accurev using(String exe) {
        this.exe = exe;
        return this;
    }

    public Accurev on(String url) {
        this.url = url;
        return this;
    }

    public AccurevClient getClient() {
        if (listener == null) listener = TaskListener.NULL;
        if (env == null) env = new EnvVars();
        if (exe == null) exe = AccurevTool.getDefaultInstallation().getHome(); // TODO Resolve for node/environment?
        if (StringUtils.isBlank(exe)) exe = "accurev";
        // At some point we could enable more implementation for now we settle for our own cli impl
        return new CliAccurevAPIImpl(exe, workspace, listener, env, url);
    }
}
