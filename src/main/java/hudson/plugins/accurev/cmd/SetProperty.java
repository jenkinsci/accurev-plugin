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

    public static boolean setproperty(
            final AccurevSCM scm,
            final FilePath accurevWorkingSpace, //
            final TaskListener listener, //
            final Launcher launcher,
            final EnvVars accurevEnv,
            final AccurevServer server,
            final String streamOrWorkspaceName,
            final String colorCode,
            final String propertyName
    ) throws IOException {

        String propertyValue = "<style><color><background-color>" + colorCode + "</background-color></color></style>";

        final ArgumentListBuilder bgColorStyleCmd = new ArgumentListBuilder();
        bgColorStyleCmd.add("setproperty");
        Command.addServer(bgColorStyleCmd, server);
        bgColorStyleCmd.add("-s");
        bgColorStyleCmd.add(streamOrWorkspaceName);
        bgColorStyleCmd.add("-r");
        bgColorStyleCmd.add(propertyName);
        bgColorStyleCmd.add(propertyValue);
        return AccurevLauncher.runCommand("setproperty background color", launcher, bgColorStyleCmd,
                scm.getOptionalLock(), accurevEnv, accurevWorkingSpace, listener, logger, true);

    }
}
