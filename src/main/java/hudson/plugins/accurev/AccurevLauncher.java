package hudson.plugins.accurev;

import static jenkins.plugins.accurev.util.AccurevUtils.workspaceToNode;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.plugins.accurev.AccurevTool;
import org.apache.commons.lang.StringUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Utility class that knows how to run AccuRev commands and (optionally) have something parse their
 * output.
 */
public final class AccurevLauncher {

  private static final Logger LOGGER = Logger.getLogger(AccurevLauncher.class.getName());

  /**
   * Runs a command and returns <code>true</code> if it passed, <code>false</code> if it failed, and
   * logs the errors.
   *
   * @param humanReadableCommandName Human-readable text saying what this command is. This appears
   *     in the logs if there is a failure.
   * @param accurevTool Which tool to find
   * @param launcher Means of executing the command.
   * @param machineReadableCommand The command to be executed.
   * @param synchronizationLockObjectOrNull The {@link ReentrantLock} object to be used to prevent
   *     concurrent execution on the same machine, or <code>null</code> if no synchronization is
   *     required.
   * @param environmentVariables The environment variables to be passed to the command.
   * @param directoryToRunCommandFrom The direction that the command should be run in.
   * @param listenerToLogFailuresTo One possible place to log failures, or <code>null</code>.
   * @param loggerToLogFailuresTo Another place to log failures, or <code>null</code>.
   * @param optionalFlagToCopyAllOutputToTaskListener Optional: If present and <code>true</code>,
   *     all command output will also be copied to the listener if the command is successful.
   * @return <code>true</code> if the command succeeded.
   * @throws IOException handle it above
   */
  public static boolean runCommand( //
      @NonNull final String humanReadableCommandName, //
      String accurevTool,
      @NonNull final Launcher launcher, //
      @NonNull final ArgumentListBuilder machineReadableCommand, //
      @Nullable final ReentrantLock synchronizationLockObjectOrNull, //
      @NonNull final EnvVars environmentVariables, //
      @NonNull final FilePath directoryToRunCommandFrom, //
      @NonNull final TaskListener listenerToLogFailuresTo, //
      @NonNull final Logger loggerToLogFailuresTo, //
      @Nullable final boolean... optionalFlagToCopyAllOutputToTaskListener)
      throws IOException {
    final Boolean result;
    final boolean shouldLogEverything =
        optionalFlagToCopyAllOutputToTaskListener != null
            && optionalFlagToCopyAllOutputToTaskListener.length > 0
            && optionalFlagToCopyAllOutputToTaskListener[0];
    if (shouldLogEverything) {
      result =
          runCommand(
              humanReadableCommandName,
              accurevTool,
              launcher,
              machineReadableCommand,
              synchronizationLockObjectOrNull,
              environmentVariables,
              directoryToRunCommandFrom,
              listenerToLogFailuresTo,
              loggerToLogFailuresTo,
              new ParseOutputToStream(),
              listenerToLogFailuresTo.getLogger());
    } else {
      result =
          runCommand(
              humanReadableCommandName,
              accurevTool,
              launcher,
              machineReadableCommand,
              synchronizationLockObjectOrNull,
              environmentVariables,
              directoryToRunCommandFrom,
              listenerToLogFailuresTo,
              loggerToLogFailuresTo,
              new ParseIgnoreOutput(),
              null);
    }
    if (result == null) {
      return false;
    }
    return result;
  }

  /**
   * As {@link #runCommand(String, String, Launcher, ArgumentListBuilder, ReentrantLock, EnvVars,
   * FilePath, TaskListener, Logger, ICmdOutputParser, Object)} but uses an {@link
   * ICmdOutputXmlParser} instead.
   *
   * @param <TResult> The type of the result returned by the parser.
   * @param <TContext> The type of data to be passed to the parser. Can be
   * @param humanReadableCommandName Human readable command
   * @param accurevTool Which tool to find
   * @param launcher launcher
   * @param machineReadableCommand Machine readable command
   * @param synchronizationLockObjectOrNull Synchronization lock
   * @param environmentVariables Environment Variables
   * @param directoryToRunCommandFrom Where to run commands from
   * @param listenerToLogFailuresTo logging failures to listener
   * @param loggerToLogFailuresTo logging failures to logger
   * @param xmlParserFactory The {@link XmlPullParserFactory} to be used to create the parser. If
   *     this is <code>null</code> then no command will be executed and the function will return
   *     <code>null</code> immediately.
   * @param commandOutputParser Command output parser
   * @param commandOutputParserContext Context of Command output parser
   * @return See above.
   * @throws IOException handle it above
   */
  public static <TResult, TContext> TResult runCommand( //
      @NonNull final String humanReadableCommandName, //
      String accurevTool,
      @NonNull final Launcher launcher, //
      @NonNull final ArgumentListBuilder machineReadableCommand, //
      @Nullable final ReentrantLock synchronizationLockObjectOrNull, //
      @NonNull final EnvVars environmentVariables, //
      @NonNull final FilePath directoryToRunCommandFrom, //
      @NonNull final TaskListener listenerToLogFailuresTo, //
      @NonNull final Logger loggerToLogFailuresTo, //
      @NonNull final XmlPullParserFactory xmlParserFactory, //
      @NonNull final ICmdOutputXmlParser<TResult, TContext> commandOutputParser, //
      @Nullable final TContext commandOutputParserContext)
      throws IOException {
    return runCommand(
        humanReadableCommandName,
        accurevTool,
        launcher,
        machineReadableCommand,
        synchronizationLockObjectOrNull,
        environmentVariables,
        directoryToRunCommandFrom,
        listenerToLogFailuresTo,
        loggerToLogFailuresTo,
        (cmdOutput, context) -> {
          XmlPullParser parser = null;
          try {
            parser = xmlParserFactory.newPullParser();
            parser.setInput(cmdOutput, null);
            final TResult result = commandOutputParser.parse(parser, context);
            parser.setInput(null);
            parser = null;
            return result;
          } catch (XmlPullParserException ex) {
            logCommandException(
                machineReadableCommand,
                directoryToRunCommandFrom,
                humanReadableCommandName,
                ex,
                loggerToLogFailuresTo,
                listenerToLogFailuresTo);
            return null;
          } finally {
            if (parser != null) {
              try {
                parser.setInput(null);
              } catch (XmlPullParserException ex) {
                logCommandException(
                    machineReadableCommand,
                    directoryToRunCommandFrom,
                    humanReadableCommandName,
                    ex,
                    loggerToLogFailuresTo,
                    listenerToLogFailuresTo);
              }
              cmdOutput.close();
            }
          }
        },
        commandOutputParserContext);
  }

  /**
   * As {@link #runCommand(String, String, Launcher, ArgumentListBuilder, ReentrantLock, EnvVars,
   * FilePath, TaskListener, Logger, ICmdOutputParser, Object)} but uses an {@link
   * ICmdOutputXmlParser} instead.
   *
   * @param <TResult> The type of the result returned by the parser.
   * @param <TContext> The type of data to be passed to the parser. Can be
   * @param humanReadableCommandName Human readable command
   * @param accurevTool Which tool to find
   * @param launcher launcher
   * @param machineReadableCommand Machine readable command
   * @param synchronizationLockObjectOrNull Synchronization lock
   * @param environmentVariables Environment Variables
   * @param directoryToRunCommandFrom Where to run commands from
   * @param listenerToLogFailuresTo logging failures to listener
   * @param loggerToLogFailuresTo logging failures to logger
   * @param xmlParserFactory The {@link XmlPullParserFactory} to be used to create the parser. If
   *     this is <code>null</code> then no command will be executed and the function will return
   *     <code>null</code> immediately.
   * @param commandOutputParser Command output parser
   * @param commandOutputParserContext Context of Command output parser
   * @return See above.
   * @throws IOException handle it above
   */
  public static <TResult, TContext> TResult runHistCommandForAll( //
      @NonNull final String humanReadableCommandName, //
      String accurevTool,
      @NonNull final Launcher launcher, //
      @NonNull final ArgumentListBuilder machineReadableCommand, //
      @Nullable final ReentrantLock synchronizationLockObjectOrNull, //
      @NonNull final EnvVars environmentVariables, //
      @NonNull final FilePath directoryToRunCommandFrom, //
      @NonNull final TaskListener listenerToLogFailuresTo, //
      @NonNull final Logger loggerToLogFailuresTo, //
      @NonNull final XmlPullParserFactory xmlParserFactory, //
      @NonNull final ICmdOutputXmlParser<TResult, TContext> commandOutputParser, //
      @Nullable final TContext commandOutputParserContext)
      throws IOException {
    return runCommand(
        humanReadableCommandName,
        accurevTool,
        launcher,
        machineReadableCommand,
        synchronizationLockObjectOrNull,
        environmentVariables,
        directoryToRunCommandFrom,
        listenerToLogFailuresTo,
        loggerToLogFailuresTo,
        (cmdOutput, context) -> {
          XmlPullParser parser = null;
          try {
            parser = xmlParserFactory.newPullParser();
            parser.setInput(cmdOutput, null);
            final TResult result = commandOutputParser.parseAll(parser, context);
            parser.setInput(null);
            parser = null;
            return result;
          } catch (XmlPullParserException ex) {
            logCommandException(
                machineReadableCommand,
                directoryToRunCommandFrom,
                humanReadableCommandName,
                ex,
                loggerToLogFailuresTo,
                listenerToLogFailuresTo);
            return null;
          } finally {
            if (parser != null) {
              try {
                parser.setInput(null);
              } catch (XmlPullParserException ex) {
                logCommandException(
                    machineReadableCommand,
                    directoryToRunCommandFrom,
                    humanReadableCommandName,
                    ex,
                    loggerToLogFailuresTo,
                    listenerToLogFailuresTo);
              }
              cmdOutput.close();
            }
          }
        },
        commandOutputParserContext);
  }

  /**
   * Runs a command a parses the output, returning the result of parsing that output. Returns <code>
   * null</code> if the command failed or if parsing failed. Failures are logged.
   *
   * @param <TResult> The type of the result returned by the parser.
   * @param <TContext> The type of data to be passed to the parser. Can be {@link Void} if no result
   *     is needed.
   * @param humanReadableCommandName Human-readable text saying what this command is. This appears
   *     in the logs if there is a failure.
   * @param accurevTool Which tool to find
   * @param launcher Means of executing the command.
   * @param machineReadableCommand The command to be executed.
   * @param synchronizationLockObjectOrNull The {@link ReentrantLock} object to be used to prevent
   *     concurrent execution on the same machine, or <code>null</code> if no synchronization is
   *     required.
   * @param environmentVariables The environment variables to be passed to the command.
   * @param directoryToRunCommandFrom The direction that the command should be run in.
   * @param listenerToLogFailuresTo One possible place to log failures, or <code>null</code>.
   * @param loggerToLogFailuresTo Another place to log failures, or <code>null</code>.
   * @param commandOutputParser The code that will parse the command's output (if the command
   *     succeeds).
   * @param commandOutputParserContext Data to be passed to the parser.
   * @return The data returned by the {@link ICmdOutputParser}, or <code>null</code> if an error
   *     occurred.
   * @throws IOException handle it above
   */
  public static <TResult, TContext> TResult runCommand( //
      @NonNull final String humanReadableCommandName, //
      String accurevTool,
      @NonNull final Launcher launcher, //
      @NonNull final ArgumentListBuilder machineReadableCommand, //
      @Nullable final ReentrantLock synchronizationLockObjectOrNull, //
      @NonNull final EnvVars environmentVariables, //
      @NonNull final FilePath directoryToRunCommandFrom, //
      @NonNull final TaskListener listenerToLogFailuresTo, //
      @NonNull final Logger loggerToLogFailuresTo, //
      @NonNull final ICmdOutputParser<TResult, TContext> commandOutputParser, //
      @Nullable final TContext commandOutputParserContext)
      throws IOException {
    try (final ByteArrayStream stdout = new ByteArrayStream();
        final ByteArrayStream stderr = new ByteArrayStream()) {
      final OutputStream stdoutStream = stdout.getOutput();
      final OutputStream stderrStream = stderr.getOutput();
      final ProcStarter starter =
          createProcess(
              launcher,
              machineReadableCommand,
              environmentVariables,
              directoryToRunCommandFrom,
              listenerToLogFailuresTo,
              stdoutStream,
              stderrStream,
              accurevTool);
      logCommandExecution(
          humanReadableCommandName,
          machineReadableCommand,
          directoryToRunCommandFrom,
          loggerToLogFailuresTo,
          listenerToLogFailuresTo);

      final int commandExitCode = runCommandToCompletion(starter, synchronizationLockObjectOrNull);
      final InputStream outputFromCommand = stdout.getInput();
      final InputStream errorFromCommand = stderr.getInput();
      if (commandExitCode != 0) {
        logCommandFailure(
            machineReadableCommand,
            directoryToRunCommandFrom,
            humanReadableCommandName,
            commandExitCode,
            outputFromCommand,
            errorFromCommand,
            loggerToLogFailuresTo,
            listenerToLogFailuresTo);
        return null;
      }
      return commandOutputParser.parse(outputFromCommand, commandOutputParserContext);
    } catch (InterruptedException | IOException | UnhandledAccurevCommandOutput ex) {
      logCommandException(
          machineReadableCommand,
          directoryToRunCommandFrom,
          humanReadableCommandName,
          ex,
          loggerToLogFailuresTo,
          listenerToLogFailuresTo);
      return null;
    }
  }

  public static AccurevTool resolveAccurevTool(
      String accurevToolValue, TaskListener listener, String command) {
    AccurevTool accurevTool =
        Jenkins.get()
            .getDescriptorByType(AccurevTool.DescriptorImpl.class)
            .getInstallation(accurevToolValue);
    boolean isValidCommand = command.equals("info") || command.equals("login");
    if (accurevTool == null) {
      accurevTool = AccurevTool.getDefaultInstallation();
      String path = accurevTool.getHome();
      handleConsoleMessage(listener, isValidCommand, path);
    } else {
      String path = accurevTool.getHome();
      handleConsoleMessage(listener, isValidCommand, path);
    }
    return accurevTool;
  }

  private static void handleConsoleMessage(
      TaskListener listener, boolean isValidCommand, String path) {
    boolean isValidPath = false;
    isValidPath = (path != null && (path.isEmpty() || path.equals("accurev")));
    if (isValidCommand && isValidPath) {
      listener.getLogger().println("No AccuRev tool configured, using default");
    }
  }

  /**
   * @param accurevTool Which tool to find
   * @param builtOn node where build was performed
   * @param env environment variables used in the build
   * @param listener build log
   * @param command AccuRev command to use
   * @return accurev exe for builtOn node, often "Default"
   */
  public static String getAccurevExe(
      String accurevTool, Node builtOn, EnvVars env, TaskListener listener, String command) {
    AccurevTool tool = resolveAccurevTool(accurevTool, listener, command);
    if (builtOn != null) {
      try {
        tool = tool.forNode(builtOn, listener);
      } catch (IOException | InterruptedException e) {
        listener.getLogger().println("Failed to get accurev executable");
      }
    }
    if (env != null) {
      tool = tool.forEnvironment(env);
    }
    return tool.getHome();
  }

  private static Integer runCommandToCompletion( //
      @NonNull final ProcStarter starter, //
      final ReentrantLock synchronizationLockObjectOrNull)
      throws IOException, InterruptedException {
    if (synchronizationLockObjectOrNull != null) {
      synchronizationLockObjectOrNull.lockInterruptibly();
    }

    try {
      return starter.join(); // Exit Code from Command
    } finally {
      if (synchronizationLockObjectOrNull != null) {
        synchronizationLockObjectOrNull.unlock();
      }
    }
  }

  private static ProcStarter createProcess(
      @NonNull final Launcher launcher,
      @NonNull final ArgumentListBuilder machineReadableCommand,
      @NonNull final EnvVars environmentVariables,
      @NonNull final FilePath directoryToRunCommandFrom,
      @NonNull TaskListener listener,
      @NonNull final OutputStream stdoutStream,
      @NonNull final OutputStream stderrStream,
      String accurevTool)
      throws IllegalStateException, IOException, InterruptedException {
    String accurevPath =
        getAccurevExe(
            accurevTool,
            workspaceToNode(directoryToRunCommandFrom),
            environmentVariables,
            listener,
            machineReadableCommand.toCommandArray().length == 0
                ? ""
                : machineReadableCommand.toCommandArray()[0]);
    if (StringUtils.isBlank(accurevPath)) {
      accurevPath = "accurev";
    }
    if (machineReadableCommand.toCommandArray().length == 0
        || !accurevPath.equals(machineReadableCommand.toCommandArray()[0])) {
      machineReadableCommand.prepend(accurevPath);
    }
    if (!justAccurev(launcher, accurevPath)) {
      throw new IllegalStateException(
          "Cannot find accurev executable. Please check installation/tool");
    }
    ProcStarter starter = launcher.launch().cmds(machineReadableCommand);
    Node n = workspaceToNode(directoryToRunCommandFrom);
    environmentVariables.putAll(buildEnvironment(n, listener));
    starter = starter.envs(environmentVariables);
    starter = starter.stdout(stdoutStream).stderr(stderrStream);
    starter = starter.pwd(directoryToRunCommandFrom);
    return starter;
  }

  private static void logCommandFailure( //
      final ArgumentListBuilder command, //
      final FilePath directoryToRunCommandFrom, //
      final String commandDescription, //
      final int commandExitCode, //
      final InputStream commandStdoutOrNull, //
      final InputStream commandStderrOrNull, //
      final Logger loggerToLogFailuresTo, //
      final TaskListener taskListener)
      throws IOException {
    final String msg =
        commandDescription
            + " ("
            + command.toString()
            + ")"
            + " failed with exit code "
            + commandExitCode;
    String stderr = null;
    try {
      stderr = getCommandErrorOutput(commandStdoutOrNull, commandStderrOrNull);
    } catch (IOException ex) {
      logCommandException(
          command,
          directoryToRunCommandFrom,
          commandDescription,
          ex,
          loggerToLogFailuresTo,
          taskListener);
    }
    if (loggerToLogFailuresTo != null
        && (loggerToLogFailuresTo.isLoggable(Level.WARNING)
            || loggerToLogFailuresTo.isLoggable(Level.INFO))) {
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

  private static String getCommandErrorOutput(
      final InputStream commandStdoutOrNull, final InputStream commandStderrOrNull)
      throws IOException {
    final StringBuilder outputText = new StringBuilder();
    if (commandStdoutOrNull != null) {
      parseCommandOutput(commandStdoutOrNull, 10, outputText);
    }
    if (commandStderrOrNull != null) {
      parseCommandOutput(commandStderrOrNull, 5, outputText);
    }
    if (outputText.length() > 0) {
      return outputText.toString();
    } else {
      return null;
    }
  }

  private static void parseCommandOutput(
      final InputStream commandOutput,
      final Integer maxNumberOfLines,
      final StringBuilder outputText)
      throws IOException {
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

  private static void logCommandException( //
      final ArgumentListBuilder command, //
      final FilePath directoryToRunCommandFrom, //
      final String commandDescription, //
      final Throwable exception, //
      final Logger loggerToLogFailuresTo, //
      final TaskListener taskListener)
      throws IOException {
    final String hostname = getRemoteHostname(directoryToRunCommandFrom);
    final String msg =
        hostname
            + ": "
            + commandDescription
            + " ("
            + command.toString()
            + ")"
            + " failed with "
            + exception.toString();
    logException(msg, exception, loggerToLogFailuresTo, taskListener);
  }

  static void logException( //
      final String summary, //
      final Throwable exception, //
      final Logger logger, //
      final TaskListener taskListener)
      throws IOException {
    if (logger != null) {
      logger.log(Level.SEVERE, summary, exception);
    }
    if (taskListener != null) {
      taskListener.fatalError(summary);
      exception.printStackTrace(taskListener.getLogger());
      throw new AbortException(exception.getMessage());
    }
  }

  private static void logCommandExecution( //
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
      final RemoteWorkspaceDetails act =
          directoryToRunCommandFrom.act(new DetermineRemoteHostname("."));
      return act.getHostName();
    } catch (UnknownHostException e) {
      return "Unable to determine actual hostname, ensure proper FQDN.\n" + e.toString();
    } catch (IOException | InterruptedException e) {
      return e.toString();
    }
  }

  public static EnvVars buildEnvironment(Node node, TaskListener listener)
      throws IOException, InterruptedException {
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

  private static boolean justAccurev(Launcher launcher, String exe) {
    try {
      return launcher.launch().quiet(true).cmdAsSingleString(exe).join() == 0;
    } catch (IOException | InterruptedException e1) {
      return false;
    }
  }

  /**
   * Interface implemented by code that interprets the output of AccuRev commands.
   *
   * <p>Intended to separate out the running of commands from the actual parsing of their results in
   * an attempt to reduce code duplication.
   *
   * @param <TResult> The output of the parsing process.
   * @param <TContext> Context object that will be passed to the parser each time it is called.
   */
  public interface ICmdOutputParser<TResult, TContext> {

    /**
     * Parses the command's output.
     *
     * @param cmdOutput The stream that contains the output of the command.
     * @param context Context passed in when the command was run.
     * @return The result of the parsing.
     * @throws UnhandledAccurevCommandOutput if the command output was invalid.
     * @throws IOException on failing IO
     */
    TResult parse(InputStream cmdOutput, TContext context)
        throws UnhandledAccurevCommandOutput, IOException;
  }

  /**
   * Interface implemented by code that interprets the output of AccuRev commands.
   *
   * <p>Intended to separate out the running of commands from the actual parsing of their results in
   * an attempt to reduce code duplication.
   *
   * @param <TResult> The output of the parsing process.
   * @param <TContext> Context object that will be passed to the parser each time it is called.
   */
  public interface ICmdOutputXmlParser<TResult, TContext> {

    /**
     * Parses the command's output.
     *
     * @param parser The {@link XmlPullParser} that contains the output of the command.
     * @param context Context passed in when the command was run.
     * @return The result of the parsing.
     * @throws UnhandledAccurevCommandOutput if the command output was invalid.
     * @throws IOException on failing IO
     * @throws XmlPullParserException if failed to Parse
     */
    TResult parse(XmlPullParser parser, TContext context)
        throws UnhandledAccurevCommandOutput, IOException, XmlPullParserException;

    /**
     * Parses all the transactions from the command's output.
     *
     * @param parser The {@link XmlPullParser} that contains the output of the command.
     * @param context Context passed in when the command was run.
     * @return The result of the parsing.
     * @throws IOException on failing IO
     * @throws XmlPullParserException if failed to Parse
     */
    default TResult parseAll(XmlPullParser parser, TContext context)
        throws IOException, XmlPullParserException {
      return null;
    }
  }

  /**
   * Exception that can be throw if the AccuRev command's output cannot be parsed or is otherwise
   * invalid.
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
