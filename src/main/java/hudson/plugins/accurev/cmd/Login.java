package hudson.plugins.accurev.cmd;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM.AccurevSCMDescriptor;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.parsers.output.ParseInfoToLoginName;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class Login extends Command {
    private static final Logger logger = Logger.getLogger(Login.class.getName());

    /**
     * @return The currently logged in user "Principal" name, which may be
     * "(not logged in)" if not logged in.<br>
     * Returns null on failure.
     */
    private static String getLoggedInUsername(//
                                              final AccurevServer server, //
                                              final Map<String, String> accurevEnv, //
                                              final FilePath workspace, //
                                              final TaskListener listener, //
                                              final Launcher launcher) throws IOException {
        final String commandDescription = "info command";
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add("info");
        addServer(cmd, server);
        final String username = AccurevLauncher.runCommand(commandDescription, launcher, cmd, null, accurevEnv,
                workspace, listener, logger, new ParseInfoToLoginName(), null);
        return username;
    }

    public static boolean ensureLoggedInToAccurev(AccurevServer server, Map<String, String> accurevEnv, FilePath pathToRunCommandsIn, TaskListener listener,
                                                  Launcher launcher) throws IOException, InterruptedException {

        if (server == null) {
            return false;
        }
        final String requiredUsername = server.getUsername();
        if (StringUtils.isBlank(requiredUsername)) {
            listener.getLogger().println("Authentication failure");
            return false;
        }
        AccurevSCMDescriptor.lock();
        try {
            final boolean loginRequired;
            if (server.isMinimiseLogins()) {
                final String currentUsername = getLoggedInUsername(server, accurevEnv, pathToRunCommandsIn, listener, launcher);
                if (currentUsername == null) {
                    loginRequired = true;
                    listener.getLogger().println("Not currently authenticated with Accurev server");
                } else {
                    loginRequired = !currentUsername.equals(requiredUsername);
                    listener.getLogger().println(
                            "Currently authenticated with Accurev server as '" + currentUsername + (loginRequired ? "', login required" : "', not logging in again."));
                }
            } else {
                loginRequired = true;
            }
            if (loginRequired) {
                return accurevLogin(server, accurevEnv, pathToRunCommandsIn, listener, launcher);
            }
        } finally {
            AccurevSCMDescriptor.unlock();
        }
        return true;
    }

    private static boolean accurevLogin(//
                                        final AccurevServer server, //
                                        final Map<String, String> accurevEnv, //
                                        final FilePath workspace, //
                                        final TaskListener listener, //
                                        final Launcher launcher) throws IOException, InterruptedException {
        listener.getLogger().println("Authenticating with Accurev server...");
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add("login");
        addServer(cmd, server);
        if (server.isUseNonexpiringLogin()) {
            cmd.add("-n");
        }
        cmd.add(server.getUsername());
        if (StringUtils.isEmpty(server.getPassword())) {
            cmd.add('"' + '"', true);
        } else {
            cmd.add(server.getPassword(), true);
        }
        final boolean success = AccurevLauncher.runCommand("login", launcher, cmd, null, accurevEnv, workspace, listener, logger);
        if (success) {
            listener.getLogger().println("Authentication completed successfully.");
            return true;
        } else {
            return false;
        }
    }


    /**
     * @param server Accurev Server
     * @return boolean whether am successful
     * @throws IOException          failing IO
     * @throws InterruptedException failing interrupt
     *                              This method is called from dofillstreams and dofilldepots while configuring the job
     */
    public static boolean accurevLoginFromGlobalConfig(//
                                                       final AccurevServer server) throws IOException, InterruptedException {

        Jenkins jenkins = Jenkins.getInstance();
        TaskListener listener = TaskListener.NULL;
        Launcher launcher = jenkins.createLauncher(listener);
        Map<String, String> accurevEnv = new HashMap<>();

        return ensureLoggedInToAccurev(server, accurevEnv, jenkins.getRootPath(), listener, launcher);
    }
}
