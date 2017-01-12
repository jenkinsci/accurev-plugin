package hudson.plugins.accurev;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.cmd.History;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CheckForChanges {

    //checkStreamForChanges is overloaded method
    //TODO: Reduce Complexity/Duplication.

    /**
     * @param server     Server
     * @param accurevEnv Accurev Environment
     * @param workspace  workspace
     * @param listener   listener
     * @param launcher   launcher
     * @param stream     stream
     * @param buildDate  build date
     * @param logger     Logger
     * @param scm        Accurev SCM
     * @return if there are any new transactions in the stream since the last build was done
     * @throws IOException          if there is issues with files
     * @throws InterruptedException if failed to interrupt properly
     */
    //stream param is of type String
    public static boolean checkStreamForChanges(AccurevServer server,
                                                Map<String, String> accurevEnv,
                                                FilePath workspace,
                                                TaskListener listener,
                                                Launcher launcher,
                                                String stream,
                                                Date buildDate,
                                                Logger logger,
                                                AccurevSCM scm)
            throws IOException, InterruptedException {

        AccurevTransaction latestCodeChangeTransaction = new AccurevTransaction();
        String filterForPollSCM = scm.getFilterForPollSCM();
        String subPath = scm.getSubPath();
        latestCodeChangeTransaction.setDate(AccurevSCM.NO_TRANS_DATE);

        //query AccuRev for the latest transactions of each kind defined in transactionTypes using getTimeOfLatestTransaction
        String[] validTransactionTypes = AccurevServer.DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES.split(AccurevServer.VTT_DELIM);


        listener.getLogger().println(//
                "Checking transactions of type " + Arrays.asList(validTransactionTypes) + //
                        " in stream [" + stream + "]");

        Collection<String> serverPaths;
        List<String> pollingFilters = getListOfPollingFilters(filterForPollSCM, subPath);

        for (final String transactionType : validTransactionTypes) {
            try {
                final AccurevTransaction tempTransaction = History.getLatestTransaction(scm, server, accurevEnv, workspace,
                        listener, launcher, stream, transactionType);
                if (tempTransaction != null) {
                    listener.getLogger().println(
                            "Last transaction of type [" + transactionType + "] is " + tempTransaction);

                    if (latestCodeChangeTransaction.getDate().before(tempTransaction.getDate())) {
                        //check the affected
                        serverPaths = tempTransaction.getAffectedPaths();
                        if (tempTransaction.getAffectedPaths().size() > 0) {
                            listener.getLogger().println("This transaction seems to have happened after the latest build!!!");
                            if (!changesMatchFilter(listener, serverPaths, pollingFilters)) {
                                // Continue to next transaction (that may have a match)
                                continue;
                            }
                        }
                    }
                    latestCodeChangeTransaction = tempTransaction;

                    //log last transaction information if retrieved
                    if (latestCodeChangeTransaction.getDate().equals(AccurevSCM.NO_TRANS_DATE)) {
                        listener.getLogger().println("No last transaction found.");
                    }
                    if (buildDate.before(latestCodeChangeTransaction.getDate())) {
                        listener.getLogger().println("Last valid trans " + latestCodeChangeTransaction);
                        return true;
                    }

                } else {
                    listener.getLogger().println("No transactions of type [" + transactionType + "]");
                }
            } catch (Exception e) {
                final String msg = "getLatestTransaction failed when checking the stream " + stream + " for changes with transaction type " + transactionType;
                listener.getLogger().println(msg);
                e.printStackTrace(listener.getLogger());
                logger.log(Level.WARNING, msg, e);
            }
        }
        return false;
    }

    /**
     * @param server     server
     * @param accurevEnv accurev environment
     * @param workspace  workspace
     * @param listener   listener
     * @param launcher   launcher
     * @param stream     stream
     * @param buildDate  build Date
     * @param logger     logger
     * @param scm        Accurev SCm
     * @return if there are any new transactions in the stream since the last build was done
     * @throws IOException          if there is issues with files
     * @throws InterruptedException if failed to interrupt properly
     */
    //stream param is of type AccurevStream
    public static boolean checkStreamForChanges(AccurevServer server,
                                                Map<String, String> accurevEnv,
                                                FilePath workspace,
                                                TaskListener listener,
                                                Launcher launcher,
                                                AccurevStream stream,
                                                Date buildDate,
                                                Logger logger,
                                                AccurevSCM scm)
            throws IOException, InterruptedException {
        AccurevTransaction latestCodeChangeTransaction = new AccurevTransaction();
        String filterForPollSCM = scm.getFilterForPollSCM();
        String subPath = scm.getSubPath();
        latestCodeChangeTransaction.setDate(AccurevSCM.NO_TRANS_DATE);

        //query AccuRev for the latest transactions of each kind defined in transactionTypes using getTimeOfLatestTransaction
        String[] validTransactionTypes;
        if (stream.getType().name().equalsIgnoreCase("workspace")) {
            validTransactionTypes = AccurevServer.DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES.split(AccurevServer.VTT_DELIM);
            listener.getLogger().println(//
                    "Checking transactions of type " + Arrays.asList(validTransactionTypes) + //
                            " in workspace [" + stream.getName() + "]");
        } else {

            validTransactionTypes = AccurevServer.DEFAULT_VALID_STREAM_TRANSACTION_TYPES.split(AccurevServer.VTT_DELIM);
            listener.getLogger().println(//
                    "Checking transactions of type " + Arrays.asList(validTransactionTypes) + //
                            " in stream [" + stream.getName() + "]");
        }
        boolean isTransLatestThanBuild = false;

        Collection<String> serverPaths;
        List<String> pollingFilters = getListOfPollingFilters(filterForPollSCM, subPath);

        for (final String transactionType : validTransactionTypes) {
            try {
                final AccurevTransaction tempTransaction = History.getLatestTransaction(scm, server, accurevEnv, workspace,
                        listener, launcher, stream.getName(), transactionType);
                if (tempTransaction != null) {
                    listener.getLogger().println(
                            "Last transaction of type [" + transactionType + "] is " + tempTransaction);

                    if (latestCodeChangeTransaction.getDate().before(tempTransaction.getDate())) {
                        //check the affected
                        serverPaths = tempTransaction.getAffectedPaths();
                        if (tempTransaction.getAffectedPaths().size() > 0) {
                            if (!changesMatchFilter(listener, serverPaths, pollingFilters)) {
                                // Continue to next transaction (that may have a match)
                                continue;
                            }
                        }
                    }
                    latestCodeChangeTransaction = tempTransaction;
                    if (latestCodeChangeTransaction.getDate().equals(AccurevSCM.NO_TRANS_DATE)) {
                        listener.getLogger().println("No last transaction found.");
                    }
                    //log last transaction information if retrieved
                    if (buildDate != null && buildDate.before(latestCodeChangeTransaction.getDate())) {
                        listener.getLogger().println("Last valid trans " + latestCodeChangeTransaction);
                        isTransLatestThanBuild = true;
                    }

                } else {
                    listener.getLogger().println("No transactions of type [" + transactionType + "]");
                }
            } catch (Exception e) {
                final String msg = "getLatestTransaction failed when checking the stream " + stream.getName() + " for changes with transaction type " + transactionType;
                listener.getLogger().println(msg);
                e.printStackTrace(listener.getLogger());
                logger.log(Level.WARNING, msg, e);
            }
        }
        return isTransLatestThanBuild;
    }

    private static boolean changesMatchFilter(TaskListener listener, Collection<String> serverPaths, List<String> filters) {
        if (filters == null || filters.isEmpty()) {
            // No filters, so always a match.
            return true;
        }

        for (String filterPath : filters) {
            // Paths are sanitized using java.nio.file.Paths (makes sure that the same path separators are used)
            final Path fp = Paths.get(filterPath);
            for (String serverPath : serverPaths) {
                final Path sp = Paths.get(serverPath);
                if (sp.toString().contains(fp.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> getListOfPollingFilters(String filterForPollSCM, String subPath) {
        final String DELIMITER = ",";
        List<String> result = new ArrayList<>();

        if (filterForPollSCM != null && !(filterForPollSCM.isEmpty())) {
            for (String filter : filterForPollSCM.split(DELIMITER)) {
                result.add(filter.trim());
            }
        } else {
            if (subPath != null && !(subPath.isEmpty())) {
                for (String filter : subPath.split(DELIMITER)) {
                    result.add(filter.trim());
                }
            }
        }

        return result;
    }
}
