package hudson.plugins.accurev.cmd;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

public class Synctime extends Command {
    private static final Logger logger = Logger.getLogger(Synctime.class.getName());

    //Analagous to the Synchronize Time option in the AccuRev GUI client.
    public static boolean synctime(//
                                   final AccurevSCM scm,
                                   final AccurevServer server, //
                                   final Map<String, String> accurevEnv, //
                                   final FilePath workspace, //
                                   final TaskListener listener, //
                                   final Launcher launcher) throws IOException, InterruptedException {
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add("synctime");
        addServer(cmd, server);
        return AccurevLauncher.runCommand("Synctime command", launcher, cmd, scm.getOptionalLock(), accurevEnv, workspace, listener, logger);
    }
}
