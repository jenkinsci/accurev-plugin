package hudson.plugins.accurev.cmd;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.util.logging.Logger;

public class SetProperty extends Command {

    private static final Logger logger = Logger.getLogger(SetProperty.class.getName());

    public static void setproperty(
            final AccurevSCM scm,
            final FilePath workspace, //
            final TaskListener listener, //
            final Launcher launcher,
            final EnvVars accurevEnv,
            final AccurevServer server,
            final String streamOrWorkspaceName,
            final String colorCode,
            final String propertyName
    ) throws IOException {

        String propertyValue = "<style><color><background-color>" + colorCode + "</background-color></color></style>";

        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add("setproperty");
        Command.addServer(cmd, server);
        cmd.add("-s");
        cmd.add(streamOrWorkspaceName);
        cmd.add("-r");
        cmd.add(propertyName);
        cmd.add(propertyValue);
        boolean runCommand = AccurevLauncher.runCommand("setproperty background color", scm.getAccurevTool(), launcher,
                cmd, scm.getOptionalLock(), accurevEnv, workspace, listener, logger, true);
        if (!runCommand) throw new IOException("Command failed");

    }
}
