package hudson.plugins.accurev.cmd;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.xmlpull.v1.XmlPullParserFactory;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;

import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.AccurevTransaction;
import hudson.plugins.accurev.XmlParserFactory;
import hudson.plugins.accurev.parsers.xml.ParseHistory;

public class History extends Command {
    private static final Logger logger = Logger.getLogger(History.class.getName());

    /**
     * @param scm             Accurev SCM
     * @param server          server
     * @param accurevEnv      Accurev Enviroment
     * @param workspace       workspace
     * @param listener        listener
     * @param launcher        launcher
     * @param stream          stream
     * @param transactionType Transaction type
     *                        Specify what type of transaction to search for (can be null)
     * @return the latest transaction of the specified type from the selected
     * stream
     * @throws IOException if no transaction was found
     */
    public static AccurevTransaction getLatestTransaction(//
                                                          final AccurevSCM scm, //
                                                          final AccurevServer server, //
                                                          final EnvVars accurevEnv, //
                                                          final FilePath workspace, //
                                                          final TaskListener listener, //
                                                          final Launcher launcher, //
                                                          final String stream, //
                                                          final String transactionType) throws IOException {
        // initialize code that extracts the latest transaction of a certain
        // type using -k flag
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
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
        XmlPullParserFactory parser = XmlParserFactory.getFactory();
        if (parser == null) throw new IOException("No XML Parser");
        final List<AccurevTransaction> transaction = new ArrayList<>(1);
        final Boolean transactionFound = AccurevLauncher.runCommand("History command", scm.getAccurevTool(), launcher, cmd, scm.getOptionalLock(), accurevEnv, workspace, listener,
            logger, parser, new ParseHistory(), transaction);
        if (transactionFound == null) {
            final String msg = "History command failed when trying to get the latest transaction of type " + transactionType;
            throw new IOException(msg);
        }
        if (transactionFound) {
            return transaction.get(0);
        } else {
            return null;
        }
    }

    /**
     * @param scm             Accurev SCM
     * @param server          server
     * @param accurevEnv      Accurev Enviroment
     * @param workspace       workspace
     * @param listener        listener
     * @param launcher        launcher
     * @param stream          stream
     * @param lastTransaction lastTransaction
     * @return all the transaction for a given stream
     * @throws IOException if no transaction was found
     */
    public static List<AccurevTransaction> getTransactionsAfterLastTransaction(//
                                                                               final AccurevSCM scm, //
                                                                               final AccurevServer server, //
                                                                               final EnvVars accurevEnv, //
                                                                               final FilePath workspace, //
                                                                               final TaskListener listener, //
                                                                               final Launcher launcher, //
                                                                               final String stream,
                                                                               final int lastTransaction) throws IOException {
        // initialize code that extracts the latest transaction of a certain
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add("hist");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-p");
        cmd.add(scm.getDepot());
        cmd.add("-s");
        cmd.add(stream);
        //Filter the history command to get all the transactions greater than the last transaction
        if (lastTransaction > 0) {
            cmd.add("-t");
            cmd.add("now-" + (lastTransaction + 1));
        }
        // execute code that extracts the latest transaction
        XmlPullParserFactory parser = XmlParserFactory.getFactory();
        if (parser == null) throw new IOException("No XML Parser");
        final List<AccurevTransaction> transactions = new ArrayList<>();
        final Boolean transactionFound = AccurevLauncher.runHistCommandForAll("History command", scm.getAccurevTool(), launcher, cmd, scm.getOptionalLock(), accurevEnv, workspace, listener,
            logger, parser, new ParseHistory(), transactions);
        if (transactionFound == null) {
            throw new IOException("History command failed when trying to get all the transactionse ");
        }
        return transactions;
    }
}
