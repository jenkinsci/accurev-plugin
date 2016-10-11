package hudson.plugins.accurev.cmd;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.util.ArgumentListBuilder;

import java.util.Map;
import java.util.logging.Logger;

public class SetProperty extends Command {

    private static final Logger logger = Logger.getLogger(SetProperty.class.getName());

    public static boolean setproperty(
            final AccurevSCM scm,
            final FilePath accurevWorkingSpace, //
            final TaskListener listener, //
            final Launcher launcher,
            final Map<String, String> accurevEnv,
            final AccurevServer server,
            final String streamOrWorkspaceName,
            final String colorCode,
            final String propertyName
    ) {

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
