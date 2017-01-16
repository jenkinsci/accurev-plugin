package hudson.plugins.accurev.cmd;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.config.AccurevServerConfig;
import hudson.plugins.accurev.parsers.output.ParseInfoToLoginName;
import hudson.util.ArgumentListBuilder;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.logging.Logger;

public class Login extends Command {
    private static final Logger logger = Logger.getLogger(Login.class.getName());

    /**
     * @return The currently logged in user "Principal" name, which may be
     * "(not logged in)" if not logged in.<br>
     * Returns null on failure.
     */
    private static String getLoggedInUsername(//
                                              final AccurevServerConfig server, //
                                              final EnvVars accurevEnv, //
                                              final FilePath workspace, //
                                              final TaskListener listener, //
                                              final Launcher launcher) throws IOException {
        final String commandDescription = "info command";
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add("info");
        addServer(cmd, server);
        // returns username
        return AccurevLauncher.runCommand(commandDescription, launcher, cmd, null, accurevEnv,
                workspace, listener, logger, new ParseInfoToLoginName(), null);
    }

    public static boolean ensureLoggedInToAccurev(AccurevServerConfig server, EnvVars accurevEnv, FilePath pathToRunCommandsIn, TaskListener listener,
                                                  Launcher launcher) throws IOException {

        if (server == null) {
            listener.getLogger().println("Authentication failure - Server is empty");
            return false;
        }
        final String requiredUsername = server.getUsername();
        if (StringUtils.isBlank(requiredUsername)) {
            listener.getLogger().println("Authentication failure - Username blank");
            return false;
        }
        final boolean loginRequired;
        if (server.isUseMinimiseLogin()) {
            final String currentUsername = getLoggedInUsername(server, accurevEnv, pathToRunCommandsIn, listener, launcher);
            if (StringUtils.isEmpty(currentUsername)) {
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
        return true;
    }

    private static boolean accurevLogin(//
                                        final AccurevServerConfig server, //
                                        final EnvVars accurevEnv, //
                                        final FilePath workspace, //
                                        final TaskListener listener, //
                                        final Launcher launcher) throws IOException {
        listener.getLogger().println("Authenticating with Accurev server...");
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add("login");
        addServer(cmd, server);
        if (server.isUseMinimiseLogin()) {
            cmd.add("-n");
        }
        cmd.add(server.getUsername());
        if (StringUtils.isEmpty(server.getPassword())) {
            if (AccurevLauncher.isUnix(workspace)) {
                cmd.add("", true);
            } else {
                cmd.addQuoted("", true);
            }
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

    public static boolean validateCredentials(StandardUsernamePasswordCredentials usernamePasswordCredentials) {
        boolean result = false;
        return result;
    }
}
