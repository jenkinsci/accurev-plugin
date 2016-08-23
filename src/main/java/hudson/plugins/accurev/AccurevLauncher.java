package hudson.plugins.accurev;

import hudson.plugins.accurev.parsers.output.ParseIgnoreOutput;
import hudson.plugins.accurev.parsers.output.ParseOutputToStream;
import hudson.plugins.accurev.parsers.output.ParseLastFewLines;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Utility class that knows how to run AccuRev commands and (optionally) have
 * something parse their output.
 */
public final class AccurevLauncher {

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

    /**
     * Runs a command and returns <code>true</code> if it passed,
     * <code>false</code> if it failed, and logs the errors.
     *
     * @param humanReadableCommandName                  Human-readable text saying what this command is. This appears
     *                                                  in the logs if there is a failure.
     * @param launcher                                  Means of executing the command.
     * @param machineReadableCommand                    The command to be executed.
     * @param masksOrNull                               Argument for {@link ProcStarter#masks(boolean...)}, or
     *                                                  <code>null</code> if not required.
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
     */
    public static boolean runCommand(//
            final String humanReadableCommandName, //
            final Launcher launcher, //
            final ArgumentListBuilder machineReadableCommand, //
            final boolean[] masksOrNull, //
            final Lock synchronizationLockObjectOrNull, //
            final Map<String, String> environmentVariables, //
            final FilePath directoryToRunCommandFrom, //
            final TaskListener listenerToLogFailuresTo, //
            final Logger loggerToLogFailuresTo, //
            final boolean... optionalFlagToCopyAllOutputToTaskListener) {
        final Boolean result;
        final boolean shouldLogEverything = optionalFlagToCopyAllOutputToTaskListener != null
                && optionalFlagToCopyAllOutputToTaskListener.length > 0 && optionalFlagToCopyAllOutputToTaskListener[0];
        if (shouldLogEverything) {
            result = runCommand(humanReadableCommandName, launcher, machineReadableCommand, masksOrNull,
                    synchronizationLockObjectOrNull, environmentVariables, directoryToRunCommandFrom,
                    listenerToLogFailuresTo, loggerToLogFailuresTo, new ParseOutputToStream(),
                    listenerToLogFailuresTo.getLogger());
        } else {
            result = runCommand(humanReadableCommandName, launcher, machineReadableCommand, masksOrNull,
                    synchronizationLockObjectOrNull, environmentVariables, directoryToRunCommandFrom,
                    listenerToLogFailuresTo, loggerToLogFailuresTo, new ParseIgnoreOutput(), null);
        }
        return result == Boolean.TRUE;
    }

    /**
     * As
     * {@link #runCommand(String, Launcher, ArgumentListBuilder, boolean[], Lock, Map, FilePath, TaskListener, Logger, ICmdOutputParser, Object)}
     * but uses an {@link ICmdOutputXmlParser} instead.
     *
     * @param <TResult>                       The type of the result returned by the parser.
     * @param <TContext>                      The type of data to be passed to the parser. Can be
     * @param humanReadableCommandName        Human readable command
     * @param launcher                        launcher
     * @param machineReadableCommand          Machine readable command
     * @param masksOrNull                     masks or null
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
     */
    public static <TResult, TContext> TResult runCommand(//
            final String humanReadableCommandName, //
            final Launcher launcher, //
            final ArgumentListBuilder machineReadableCommand, //
            final boolean[] masksOrNull, //
            final Lock synchronizationLockObjectOrNull, //
            final Map<String, String> environmentVariables, //
            final FilePath directoryToRunCommandFrom, //
            final TaskListener listenerToLogFailuresTo, //
            final Logger loggerToLogFailuresTo, //
            final XmlPullParserFactory xmlParserFactory, //
            final ICmdOutputXmlParser<TResult, TContext> commandOutputParser, //
            final TContext commandOutputParserContext) {
        return runCommand(humanReadableCommandName, launcher, machineReadableCommand, masksOrNull,
                synchronizationLockObjectOrNull, environmentVariables, directoryToRunCommandFrom,
                listenerToLogFailuresTo, loggerToLogFailuresTo, new ICmdOutputParser<TResult, TContext>() {
                    public TResult parse(InputStream cmdOutput, TContext context) throws UnhandledAccurevCommandOutput,
                            IOException {
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
     * @param masksOrNull                     Argument for {@link ProcStarter#masks(boolean...)}, or
     *                                        <code>null</code> if not required.
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
     */
    public static <TResult, TContext> TResult runCommand(//
            final String humanReadableCommandName, //
            final Launcher launcher, //
            final ArgumentListBuilder machineReadableCommand, //
            final boolean[] masksOrNull, //
            final Lock synchronizationLockObjectOrNull, //
            final Map<String, String> environmentVariables, //
            final FilePath directoryToRunCommandFrom, //
            final TaskListener listenerToLogFailuresTo, //
            final Logger loggerToLogFailuresTo, //
            final ICmdOutputParser<TResult, TContext> commandOutputParser, //
            final TContext commandOutputParserContext) {
        final ByteArrayStream stdout = new ByteArrayStream();
        final ByteArrayStream stderr = new ByteArrayStream();
        try {
            final OutputStream stdoutStream = stdout.getOutput();
            final OutputStream stderrStream = stderr.getOutput();
            final ProcStarter starter = createProcess(launcher, machineReadableCommand, masksOrNull,
                    environmentVariables, directoryToRunCommandFrom, stdoutStream, stderrStream);
            logCommandExecution(machineReadableCommand, directoryToRunCommandFrom, loggerToLogFailuresTo,
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
                final TResult parsedResult = commandOutputParser.parse(outputFromCommand, commandOutputParserContext);
                return parsedResult;
            } catch (Exception ex) {
                logCommandException(machineReadableCommand, directoryToRunCommandFrom, humanReadableCommandName, ex,
                        loggerToLogFailuresTo, listenerToLogFailuresTo);
                return null;
            }
        } finally {
            try {
                stdout.close();
                stderr.close();
            } catch (IOException ex) {
                logCommandException(machineReadableCommand, directoryToRunCommandFrom, humanReadableCommandName, ex,
                        loggerToLogFailuresTo, listenerToLogFailuresTo);
            }
        }
    }

    private static Integer runCommandToCompletion(//
            final ProcStarter starter, //
            final Lock synchronizationLockObjectOrNull) throws IOException, InterruptedException {
        try {
            if (synchronizationLockObjectOrNull != null) {
                synchronizationLockObjectOrNull.lock();
            }
            final int commandExitCode = starter.join();
            return commandExitCode;
        } finally {
            if (synchronizationLockObjectOrNull != null) {
                synchronizationLockObjectOrNull.unlock();
            }
        }
    }

    private static ProcStarter createProcess(//
            final Launcher launcher, //
            final ArgumentListBuilder machineReadableCommand, //
            final boolean[] masksOrNull, //
            final Map<String, String> environmentVariables, //
            final FilePath directoryToRunCommandFrom, //
            final OutputStream stdoutStream, //
            final OutputStream stderrStream) {
        ProcStarter starter = launcher.launch().cmds(machineReadableCommand);
        if (masksOrNull != null) {
            starter = starter.masks(masksOrNull);
        }
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
            final TaskListener taskListener) {
        //final String msg = commandDescription + " (" + command.toStringWithQuote() + ")" + " failed with exit code " + commandExitCode;
        final String msg = "Failed authentication. (failed with exit code " + commandExitCode+" )";
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
        final Integer maxNumberOfStderrLines = 10;
        final Integer maxNumberOfStdoutLines = 5;
        final String newLine = System.getProperty("line.separator");
        final ParseLastFewLines tailParser = new ParseLastFewLines();
        final StringBuilder outputText = new StringBuilder();
        if (commandStdoutOrNull != null) {
            final List<String> stdoutLines = tailParser.parse(commandStdoutOrNull, maxNumberOfStdoutLines);
            for (final String line : stdoutLines) {
                if (outputText.length() > 0) {
                    outputText.append(newLine);
                }
                outputText.append(line);
            }
        }
        if (commandStderrOrNull != null) {
            final List<String> stderrLines = tailParser.parse(commandStderrOrNull, maxNumberOfStderrLines);
            for (final String line : stderrLines) {
                if (outputText.length() > 0) {
                    outputText.append(newLine);
                }
                outputText.append(line);
            }
        }
        if (outputText.length() > 0) {
            return outputText.toString();
        } else {
            return null;
        }
    }

    private static void logCommandException(//
            final ArgumentListBuilder command, //
            final FilePath directoryToRunCommandFrom, //
            final String commandDescription, //
            final Throwable exception, //
            final Logger loggerToLogFailuresTo, //
            final TaskListener taskListener) {
        final String hostname = getRemoteHostname(directoryToRunCommandFrom);
        final String msg = hostname + ": " + commandDescription + " (" + command.toStringWithQuote() + ")"
                + " failed with " + exception.toString();
        logException(msg, exception, loggerToLogFailuresTo, taskListener);
    }

    static void logException(//
            final String summary, //
            final Throwable exception, //
            final Logger logger, //
            final TaskListener taskListener) {
        if (logger != null) {
            // TODO: Log the machine name to the Logger as well.
            logger.log(Level.SEVERE, exception.getMessage(), exception);
        }
        if (taskListener != null) {
            taskListener.fatalError(summary);
            exception.printStackTrace(taskListener.getLogger());
        }
    }

    private static void logCommandExecution(//
            final ArgumentListBuilder command, //
            final FilePath directoryToRunCommandFrom, //
            final Logger loggerToLogFailuresTo, //
            final TaskListener taskListener) {
        if (loggerToLogFailuresTo != null && loggerToLogFailuresTo.isLoggable(Level.FINE)) {
            final String hostname = getRemoteHostname(directoryToRunCommandFrom);
            final String msg = hostname + ": " + command.toStringWithQuote();
            loggerToLogFailuresTo.log(Level.FINE, msg);
        }
    }

    private static String getRemoteHostname(final FilePath directoryToRunCommandFrom) {
        try {
            final RemoteWorkspaceDetails act = directoryToRunCommandFrom.act(new DetermineRemoteHostname("."));
            final String hostName = act.getHostName();
            return hostName;
        } catch (UnknownHostException e) {
            return e.toString();
        } catch (IOException e) {
            return e.toString();
        } catch (InterruptedException e) {
            return e.toString();
        }
    }
}
