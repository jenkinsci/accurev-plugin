package jenkins.plugins.accurev;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * Initialized by josep on 16-02-2017.
 */
public class Accurev implements Serializable {
    private FilePath workspace;
    private TaskListener listener;
    private EnvVars env;
    private String exe;

    public Accurev(TaskListener listener, EnvVars env) {
        this.listener = listener;
        this.env = env;
    }

    public static Accurev with(TaskListener listener, EnvVars env) {
        return new Accurev(listener, env);
    }

    public Accurev in(FilePath workspace) {
        this.workspace = workspace;
        return this;
    }

    public Accurev in(File workspace) {
        return in(new FilePath(workspace));
    }

    public Accurev using(String exe) {
        this.exe = exe;
        return this;
    }

    public AccurevClient getClient() throws IOException, InterruptedException {

        Jenkins jenkins = Jenkins.getInstance();
        if (listener == null) listener = TaskListener.NULL;
        if (env == null) env = new EnvVars();
        if (workspace == null) workspace = jenkins.getRootPath();
        Computer computer = workspace.toComputer();
        if (env.isEmpty() && computer != null) env = computer.getEnvironment().overrideAll(env);
        return new CliAccurevAPIImpl(exe, workspace, listener, env);
    }

    private static final long serialVersionUID = 1L;
}
