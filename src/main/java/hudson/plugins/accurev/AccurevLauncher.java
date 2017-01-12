package hudson.plugins.accurev;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.plugins.accurev.parsers.output.ParseIgnoreOutput;
import hudson.plugins.accurev.parsers.output.ParseLastFewLines;
import hudson.plugins.accurev.parsers.output.ParseOutputToStream;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class that knows how to run AccuRev commands and (optionally) have
 * something parse their output.
 */
public final class AccurevLauncher {
    private static final Logger LOGGER = Logger.getLogger(AccurevLauncher.class.getName());
    /**
     * The default search paths for Windows clients.
     */
    private static final List<String> DEFAULT_WIN_CMD_LOCATIONS = Arrays.asList(
            "C:\\opt\\accurev\\bin\\accurev.exe",
            "C:\\Program Files\\AccuRev\\bin\\accurev.exe",
            "C:\\Program Files (x86)\\AccuRev\\bin\\accurev.exe");
    /**
     * The default search paths for *nix clients
     */
    private static final List<String> DEFAULT_NIX_CMD_LOCATIONS = Arrays.asList(
            "/usr/local/bin/accurev",
            "/usr/bin/accurev",
            "/bin/accurev",
            "/local/bin/accurev",
            "/opt/accurev/bin/accurev",
            "/Applications/AccuRev/bin/accurev",
            "/Applications/AccuRevClient/bin/accurev");
    private static final Map<String, String> executables = new HashMap<>();

    /**
     * Runs a command and returns <code>true</code> if it passed,
     * <code>false</code> if it failed, and logs the errors.
     *
     * @param humanReadableCommandName                  Human-readable text saying what this command is. This appears
     *                                                  in the logs if there is a failure.
     * @param launcher                                  Means of executing the command.
     * @param machineReadableCommand                    The command to be executed.
     * @param synchronizationLockObjectOrNull           The {@link Lock} object to be used to prevent concurrent
     *                                                  execution on the same machine, or <code>null</code> if no
     *                                                  synchronization is required.
     * @param environmentVariables                      The environment variables to be passed to the command.
     * @param directoryToRunCommandFrom                 The direction that the command should be run in.
     * @param listenerToLogFailuresTo                   One possible place to log failures, or <code>null</code>.
     * @param loggerToLogFailuresTo                     Another place to log failures, or <code>null</code>.
     * @param optionalFlagToCopyAllOutputToTaskListener Optional: If present and <code>true</code>, all command output
     *                                                  will also be copied to the listener if the command is
     *                                                  successful.
     * @return <code>true</code> if the command succeeded.
     * @throws IOException handle it above
     */
    public static boolean runCommand(//
                                     @Nonnull final String humanReadableCommandName, //
                                     @Nonnull final Launcher launcher, //
                                     @Nonnull final ArgumentListBuilder machineReadableCommand, //
                                     @Nullable final Lock synchronizationLockObjectOrNull, //
                                     @Nonnull final EnvVars environmentVariables, //
                                     @Nonnull final FilePath directoryToRunCommandFrom, //
                                     @Nonnull final TaskListener listenerToLogFailuresTo, //
                                     @Nonnull final Logger loggerToLogFailuresTo, //
                                     @Nullable final boolean... optionalFlagToCopyAllOutputToTaskListener) throws IOException {
        final Boolean result;
        final boolean shouldLogEverything = optionalFlagToCopyAllOutputToTaskListener != null
                && optionalFlagToCopyAllOutputToTaskListener.length > 0 && optionalFlagToCopyAllOutputToTaskListener[0];
        if (shouldLogEverything) {
            result = runCommand(humanReadableCommandName, launcher, machineReadableCommand,
                    synchronizationLockObjectOrNull, environmentVariables, directoryToRunCommandFrom,
                    listenerToLogFailuresTo, loggerToLogFailuresTo, new ParseOutputToStream(),
                    listenerToLogFailuresTo.getLogger());
        } else {
            result = runCommand(humanReadableCommandName, launcher, machineReadableCommand,
                    synchronizationLockObjectOrNull, environmentVariables, directoryToRunCommandFrom,
                    listenerToLogFailuresTo, loggerToLogFailuresTo, new ParseIgnoreOutput(), null);
        }
        if (result == null) return false;
        return result;
    }

    /**
     * As
     * {@link #runCommand(String, Launcher, ArgumentListBuilder, Lock, EnvVars, FilePath, TaskListener, Logger, ICmdOutputParser, Object)}
     * but uses an {@link ICmdOutputXmlParser} instead.
     *
     * @param <TResult>                       The type of the result returned by the parser.
     * @param <TContext>                      The type of data to be passed to the parser. Can be
     * @param humanReadableCommandName        Human readable command
     * @param launcher                        launcher
     * @param machineReadableCommand          Machine readable command
     * @param synchronizationLockObjectOrNull Synchronization lock
     * @param environmentVariables            Environment Variables
     * @param directoryToRunCommandFrom       Where to run commands from
     * @param listenerToLogFailuresTo         logging failures to listener
     * @param loggerToLogFailuresTo           logging failures to logger
     * @param xmlParserFactory                The {@link XmlPullParserFactory} to be used to create the
     *                                        parser. If this is <code>null</code> then no command will be
     *                                        executed and the function will return <code>null</code>
     *                                        immediately.
     * @param commandOutputParser             Command output parser
     * @param commandOutputParserContext      Context of Command output parser
     * @return See above.
     * @throws IOException handle it above
     */
    public static <TResult, TContext> TResult runCommand(//
                                                         @Nonnull final String humanReadableCommandName, //
                                                         @Nonnull final Launcher launcher, //
                                                         @Nonnull final ArgumentListBuilder machineReadableCommand, //
                                                         @Nullable final Lock synchronizationLockObjectOrNull, //
                                                         @Nonnull final EnvVars environmentVariables, //
                                                         @Nonnull final FilePath directoryToRunCommandFrom, //
                                                         @Nonnull final TaskListener listenerToLogFailuresTo, //
                                                         @Nonnull final Logger loggerToLogFailuresTo, //
                                                         @Nonnull final XmlPullParserFactory xmlParserFactory, //
                                                         @Nonnull final ICmdOutputXmlParser<TResult, TContext> commandOutputParser, //
                                                         @Nullable final TContext commandOutputParserContext) throws IOException {
        return runCommand(humanReadableCommandName, launcher, machineReadableCommand,
                synchronizationLockObjectOrNull, environmentVariables, directoryToRunCommandFrom,
                listenerToLogFailuresTo, loggerToLogFailuresTo, (cmdOutput, context) -> {
                    XmlPullParser parser = null;
                    try {
                        parser = xmlParserFactory.newPullParser();
                        parser.setInput(cmdOutput, null);
                        final TResult result = commandOutputParser.parse(parser, context);
                        parser.setInput(null);
                        parser = null;
                        return result;
                    } catch (XmlPullParserException ex) {
                        logCommandException(machineReadableCommand, directoryToRunCommandFrom,
                                humanReadableCommandName, ex, loggerToLogFailuresTo, listenerToLogFailuresTo);
                        return null;
                    } finally {
                        if (parser != null) {
                            try {
                                parser.setInput(null);
                            } catch (XmlPullParserException ex) {
                                logCommandException(machineReadableCommand, directoryToRunCommandFrom,
                                        humanReadableCommandName, ex, loggerToLogFailuresTo,
                                        listenerToLogFailuresTo);
                            }
                            cmdOutput.close();
                        }
                    }
                }, commandOutputParserContext);
    }

    /**
     * Runs a command a parses the output, returning the result of parsing that
     * output. Returns <code>null</code> if the command failed or if parsing
     * failed. Failures are logged.
     *
     * @param <TResult>                       The type of the result returned by the parser.
     * @param <TContext>                      The type of data to be passed to the parser. Can be
     *                                        {@link Void} if no result is needed.
     * @param humanReadableCommandName        Human-readable text saying what this command is. This appears
     *                                        in the logs if there is a failure.
     * @param launcher                        Means of executing the command.
     * @param machineReadableCommand          The command to be executed.
     * @param synchronizationLockObjectOrNull The {@link Lock} object to be used to prevent concurrent
     *                                        execution on the same machine, or <code>null</code> if no
     *                                        synchronization is required.
     * @param environmentVariables            The environment variables to be passed to the command.
     * @param directoryToRunCommandFrom       The direction that the command should be run in.
     * @param listenerToLogFailuresTo         One possible place to log failures, or <code>null</code>.
     * @param loggerToLogFailuresTo           Another place to log failures, or <code>null</code>.
     * @param commandOutputParser             The code that will parse the command's output (if the command
     *                                        succeeds).
     * @param commandOutputParserContext      Data to be passed to the parser.
     * @return The data returned by the {@link ICmdOutputParser}, or
     * <code>null</code> if an error occurred.
     * @throws IOException handle it above
     */
    public static <TResult, TContext> TResult runCommand(//
                                                         @Nonnull final String humanReadableCommandName, //
                                                         @Nonnull final Launcher launcher, //
                                                         @Nonnull final ArgumentListBuilder machineReadableCommand, //
                                                         @Nullable final Lock synchronizationLockObjectOrNull, //
                                                         @Nonnull final EnvVars environmentVariables, //
                                                         @Nonnull final FilePath directoryToRunCommandFrom, //
                                                         @Nonnull final TaskListener listenerToLogFailuresTo, //
                                                         @Nonnull final Logger loggerToLogFailuresTo, //
                                                         @Nonnull final ICmdOutputParser<TResult, TContext> commandOutputParser, //
                                                         @Nullable final TContext commandOutputParserContext) throws IOException {
        try (final ByteArrayStream stdout = new ByteArrayStream();
             final ByteArrayStream stderr = new ByteArrayStream()) {
            final OutputStream stdoutStream = stdout.getOutput();
            final OutputStream stderrStream = stderr.getOutput();
            final ProcStarter starter = createProcess(launcher, machineReadableCommand,
                    environmentVariables, directoryToRunCommandFrom, listenerToLogFailuresTo, stdoutStream, stderrStream);
            logCommandExecution(humanReadableCommandName, machineReadableCommand, directoryToRunCommandFrom, loggerToLogFailuresTo,
                    listenerToLogFailuresTo);
            try {
                final int commandExitCode = runCommandToCompletion(starter, synchronizationLockObjectOrNull);
                final InputStream outputFromCommand = stdout.getInput();
                final InputStream errorFromCommand = stderr.getInput();
                if (commandExitCode != 0) {
                    logCommandFailure(machineReadableCommand, directoryToRunCommandFrom, humanReadableCommandName,
                            commandExitCode, outputFromCommand, errorFromCommand, loggerToLogFailuresTo, listenerToLogFailuresTo);
                    return null;
                }
                return commandOutputParser.parse(outputFromCommand, commandOutputParserContext);
            } catch (Exception ex) {
                logCommandException(machineReadableCommand, directoryToRunCommandFrom, humanReadableCommandName, ex,
                        loggerToLogFailuresTo, listenerToLogFailuresTo);
                return null;
            }
        } catch (InterruptedException | IOException ex) {
            logCommandException(machineReadableCommand, directoryToRunCommandFrom, humanReadableCommandName, ex, loggerToLogFailuresTo, listenerToLogFailuresTo);
            return null;
        }
    }

    private static Integer runCommandToCompletion(//
                                                  final ProcStarter starter, //
                                                  final Lock synchronizationLockObjectOrNull) throws IOException, InterruptedException {
        try {
            if (synchronizationLockObjectOrNull != null) {
                synchronizationLockObjectOrNull.lock();
            }
            return starter.join(); // Exit Code from Command
        } finally {
            if (synchronizationLockObjectOrNull != null) {
                synchronizationLockObjectOrNull.unlock();
            }
        }
    }

    private static ProcStarter createProcess(
            @Nonnull final Launcher launcher,
            @Nonnull final ArgumentListBuilder machineReadableCommand,
            @Nonnull final EnvVars environmentVariables,
            @Nonnull final FilePath directoryToRunCommandFrom,
            @Nonnull TaskListener listener,
            @Nonnull final OutputStream stdoutStream,
            @Nonnull final OutputStream stderrStream) throws IOException, InterruptedException {
        String accurevPath = findAccurevExe(directoryToRunCommandFrom, environmentVariables, launcher);
        if (!machineReadableCommand.toString().contains(accurevPath)) machineReadableCommand.prepend(accurevPath);
        ProcStarter starter = launcher.launch().cmds(machineReadableCommand);
        Node n = workspaceToNode(directoryToRunCommandFrom);
        environmentVariables.putAll(buildEnvironment(n, listener));
        String path = null;
        FilePath filePath = null;
        if (null != n) filePath = n.getRootPath();
        if (null != filePath) path = filePath.getRemote();
        if (StringUtils.isNotBlank(path)) environmentVariables.putIfAbsent("ACCUREV_HOME", path);
        starter = starter.envs(environmentVariables);
        starter = starter.stdout(stdoutStream).stderr(stderrStream);
        starter = starter.pwd(directoryToRunCommandFrom);
        return starter;
    }

    private static void logCommandFailure(//
                                          final ArgumentListBuilder command, //
                                          final FilePath directoryToRunCommandFrom, //
                                          final String commandDescription, //
                                          final int commandExitCode, //
                                          final InputStream commandStdoutOrNull, //
                                          final InputStream commandStderrOrNull, //
                                          final Logger loggerToLogFailuresTo, //
                                          final TaskListener taskListener) throws IOException {
        final String msg = commandDescription + " (" + command.toString() + ")" + " failed with exit code " + commandExitCode;
        String stderr = null;
        try {
            stderr = getCommandErrorOutput(commandStdoutOrNull, commandStderrOrNull);
        } catch (IOException ex) {
            logCommandException(command, directoryToRunCommandFrom, commandDescription, ex, loggerToLogFailuresTo,
                    taskListener);
        }
        if (loggerToLogFailuresTo != null
                && (loggerToLogFailuresTo.isLoggable(Level.WARNING) || loggerToLogFailuresTo.isLoggable(Level.INFO))) {
            final String hostname = getRemoteHostname(directoryToRunCommandFrom);
            loggerToLogFailuresTo.warning(hostname + ": " + msg);
            if (stderr != null) {
                loggerToLogFailuresTo.info(hostname + ": " + stderr);
            }
        }
        if (taskListener != null) {
            if (stderr != null) {
                taskListener.fatalError(stderr);
            }
            taskListener.fatalError(msg);
        }
    }

    private static String getCommandErrorOutput(final InputStream commandStdoutOrNull,
                                                final InputStream commandStderrOrNull) throws IOException {
        final StringBuilder outputText = new StringBuilder();
        if (commandStdoutOrNull != null) parseCommandOutput(commandStdoutOrNull, 10, outputText);
        if (commandStderrOrNull != null) parseCommandOutput(commandStderrOrNull, 5, outputText);
        if (outputText.length() > 0) {
            return outputText.toString();
        } else {
            return null;
        }
    }

    private static void parseCommandOutput(final InputStream commandOutput,
                                           final Integer maxNumberOfLines,
                                           final StringBuilder outputText) throws IOException {
        final String newLine = System.getProperty("line.separator");
        final ParseLastFewLines tailParser = new ParseLastFewLines();
        final List<String> outputLines = tailParser.parse(commandOutput, maxNumberOfLines);
        for (final String line : outputLines) {
            if (outputText.length() > 0) {
                outputText.append(newLine);
            }
            outputText.append(line);
        }
    }

    private static void logCommandException(//
                                            final ArgumentListBuilder command, //
                                            final FilePath directoryToRunCommandFrom, //
                                            final String commandDescription, //
                                            final Throwable exception, //
                                            final Logger loggerToLogFailuresTo, //
                                            final TaskListener taskListener) throws IOException {
        final String hostname = getRemoteHostname(directoryToRunCommandFrom);
        final String msg = hostname + ": " + commandDescription + " (" + command.toString() + ")"
                + " failed with " + exception.toString();
        logException(msg, exception, loggerToLogFailuresTo, taskListener);
    }

    static void logException(//
                             final String summary, //
                             final Throwable exception, //
                             final Logger logger, //
                             final TaskListener taskListener) throws IOException {
        if (logger != null) {
            logger.log(Level.SEVERE, summary, exception);
        }
        if (taskListener != null) {
            taskListener.fatalError(summary);
            exception.printStackTrace(taskListener.getLogger());
            throw new AbortException(exception.getMessage());
        }
    }

    private static void logCommandExecution(//
                                            final String commandDescription,
                                            final ArgumentListBuilder command, //
                                            final FilePath directoryToRunCommandFrom, //
                                            final Logger loggerToLogFailuresTo, //
                                            final TaskListener taskListener) {
        if (loggerToLogFailuresTo != null && loggerToLogFailuresTo.isLoggable(Level.FINE)) {
            final String hostname = getRemoteHostname(directoryToRunCommandFrom);
            final String msg = hostname + ": " + command.toString();
            loggerToLogFailuresTo.log(Level.FINE, msg);
        }
    }

    private static String getRemoteHostname(final FilePath directoryToRunCommandFrom) {
        try {
            final RemoteWorkspaceDetails act = directoryToRunCommandFrom.act(new DetermineRemoteHostname("."));
            return act.getHostName();
        } catch (UnknownHostException e) {
            return "Unable to determine actual hostname, ensure proper FQDN.\n" + e.toString();
        } catch (IOException | InterruptedException e) {
            return e.toString();
        }
    }

    public static EnvVars buildEnvironment(Node node, TaskListener listener) throws IOException, InterruptedException {
        EnvVars env;
        if (null != node) {
            final Computer computer = node.toComputer();
            env = (computer != null) ? computer.buildEnvironment(listener) : new EnvVars();
        } else {
            env = new EnvVars();
        }

        // servlet container may have set CLASSPATH in its launch script,
        // so don't let that inherit to the new child process.
        env.put("CLASSPATH", "");

        return env;
    }

    private static String separator(FilePath workspace) {
        return isUnix(workspace) ? "/" : "\\";
    }

    @Nonnull
    private static synchronized String findAccurevExe(FilePath workspace, EnvVars e, Launcher launcher) {
        String name = workspace.toComputer().getName();
        String binName = "accurev";
        String exe = binName;
        if (executables.containsKey(name)) {
            return executables.get(name);
        }
        if (e.containsKey("ACCUREV_BIN")) {
            exe = e.get("ACCUREV_BIN") + separator(workspace) + binName;
            if (justAccurev(launcher, exe)) {
                executables.put(name, exe);
                return exe;
            }
        }
        if (e.containsKey("PATH") && e.get("PATH").contains(binName)) {
            exe = binName;
            if (justAccurev(launcher, exe)) {
                executables.put(name, exe);
                return exe;
            }
        }
        if (isUnix(workspace)) {
            exe = getExistingPath(workspace, DEFAULT_NIX_CMD_LOCATIONS);
            if (justAccurev(launcher, exe)) {
                executables.put(name, exe);
                return exe;
            }
        } else {
            exe = getExistingPath(workspace, DEFAULT_WIN_CMD_LOCATIONS);
            if (justAccurev(launcher, exe)) {
                executables.put(name, exe);
                return exe;
            }
        }
        return exe;
    }

    private static boolean justAccurev(Launcher launcher, String exe) {
        try {
            return launcher.launch().quiet(true).cmdAsSingleString(exe).join() == 0;
        } catch (IOException | InterruptedException e1) {
            return false;
        }
    }

    private static String getExistingPath(FilePath p, List<String> paths) {
        for (final String path : paths) {
            try {
                if (new FilePath(p.getChannel(), path).exists()) {
                    return path;
                }
            } catch (IOException | InterruptedException ignored) {
            }
        }
        return "";
    }

    @CheckForNull
    public static Node workspaceToNode(FilePath workspace) {
        Computer computer = workspace.toComputer();
        Node node = null;
        if (null != computer) node = computer.getNode();
        return null != node ? node : Jenkins.getInstance();
    }

    public static boolean isUnix(FilePath workspace) {
        // if the path represents a local path, there' no need to guess.
        if (!workspace.isRemote())
            return File.pathSeparatorChar != ';';

        String remote = workspace.getRemote();

        // note that we can't use the usual File.pathSeparator and etc., as the OS of
        // the machine where this code runs and the OS that this FilePath refers to may be different.

        // Windows absolute path is 'X:\...', so this is usually a good indication of Windows path
        if (remote.length() > 3 && remote.charAt(1) == ':' && remote.charAt(2) == '\\')
            return false;
        // Windows can handle '\' as a path separator but Unix can't,
        // so err on Unix side
        return !remote.contains("\\");
    }

    /**
     * Interface implemented by code that interprets the output of AccuRev
     * commands.
     * <p>
     * Intended to separate out the running of commands from the actual parsing
     * of their results in an attempt to reduce code duplication.
     *
     * @param <TResult>  The output of the parsing process.
     * @param <TContext> Context object that will be passed to the parser each time it
     *                   is called.
     */
    public interface ICmdOutputParser<TResult, TContext> {
        /**
         * Parses the command's output.
         *
         * @param cmdOutput The stream that contains the output of the command.
         * @param context   Context passed in when the command was run.
         * @return The result of the parsing.
         * @throws UnhandledAccurevCommandOutput if the command output was invalid.
         * @throws IOException                   on failing IO
         */
        TResult parse(InputStream cmdOutput, TContext context) throws UnhandledAccurevCommandOutput, IOException;
    }

    /**
     * Interface implemented by code that interprets the output of AccuRev
     * commands.
     * <p>
     * Intended to separate out the running of commands from the actual parsing
     * of their results in an attempt to reduce code duplication.
     *
     * @param <TResult>  The output of the parsing process.
     * @param <TContext> Context object that will be passed to the parser each time it
     *                   is called.
     */
    public interface ICmdOutputXmlParser<TResult, TContext> {
        /**
         * Parses the command's output.
         *
         * @param parser  The {@link XmlPullParser} that contains the output of the
         *                command.
         * @param context Context passed in when the command was run.
         * @return The result of the parsing.
         * @throws UnhandledAccurevCommandOutput if the command output was invalid.
         * @throws IOException                   on failing IO
         * @throws XmlPullParserException        if failed to Parse
         */
        TResult parse(XmlPullParser parser, TContext context) throws UnhandledAccurevCommandOutput, IOException, XmlPullParserException;
    }

    /**
     * Exception that can be throw if the AccuRev command's output cannot be
     * parsed or is otherwise invalid.
     */
    public static final class UnhandledAccurevCommandOutput extends Exception {
        public UnhandledAccurevCommandOutput(String message, Throwable cause) {
            super(message, cause);
        }

        public UnhandledAccurevCommandOutput(String message) {
            super(message);
        }

        public UnhandledAccurevCommandOutput(Throwable cause) {
            super(cause);
        }
    }
}
