package hudson.plugins.accurev.cmd;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.AccurevTransaction;
import hudson.plugins.accurev.XmlParserFactory;
import hudson.plugins.accurev.parsers.xml.ParseHistory;
import hudson.util.ArgumentListBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class History extends Command {
    private static final Logger logger = Logger.getLogger(History.class.getName());

    /**
     * @param server          server
     * @param accurevEnv      Accurev Enviroment
     * @param workspace       workspace
     * @param listener        listener
     * @param accurevPath     accurevPath
     * @param launcher        launcher
     * @param stream          stream
     * @param transactionType Transaction type
     *                        Specify what type of transaction to search for (can be null)
     * @param scm             Accurev SCM
     * @return the latest transaction of the specified type from the selected
     * stream
     * @throws Exception if no transaction was found
     */
    public static AccurevTransaction getLatestTransaction(//
                                                          final AccurevSCM scm, //
                                                          final AccurevServer server, //
                                                          final Map<String, String> accurevEnv, //
                                                          final FilePath workspace, //
                                                          final TaskListener listener, //
                                                          final String accurevPath, //
                                                          final Launcher launcher, //
                                                          final String stream, //
                                                          final String transactionType) throws Exception {
        // initialize code that extracts the latest transaction of a certain
        // type using -k flag
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("hist");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-p");
        cmd.add(scm.getDepot());
        cmd.add("-s");
        cmd.add(stream);
        cmd.add("-t");
        cmd.add("now.1");
        if (transactionType != null) {
            cmd.add("-k");
            cmd.add(transactionType);
        }

        // execute code that extracts the latest transaction
        final List<AccurevTransaction> transaction = new ArrayList<>(1);
        final Boolean transactionFound = AccurevLauncher.runCommand("History command", launcher, cmd, null, scm.getOptionalLock(), accurevEnv, workspace, listener,
                logger, XmlParserFactory.getFactory(), new ParseHistory(), transaction);
        if (transactionFound == null) {
            final String msg = "History command failed when trying to get the latest transaction of type " + transactionType;
            throw new Exception(msg);
        }
        if (transactionFound) {
            return transaction.get(0);
        } else {
            return null;
        }
    }
}
