package hudson.plugins.accurev;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.cmd.History;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CheckForChanges {

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
     */
    //stream param is of type AccurevStream
    public static boolean checkStreamForChanges(AccurevServer server,
                                                EnvVars accurevEnv,
                                                FilePath workspace,
                                                TaskListener listener,
                                                Launcher launcher,
                                                AccurevStream stream,
                                                Date buildDate,
                                                Logger logger,
                                                AccurevSCM scm) {
        AccurevTransaction latestCodeChangeTransaction = new AccurevTransaction();
        String filterForPollSCM = scm.getFilterForPollSCM();
        String subPath = scm.getSubPath();
        latestCodeChangeTransaction.setDate(AccurevSCM.NO_TRANS_DATE);

        //query AccuRev for the latest transactions of each kind defined in transactionTypes using getTimeOfLatestTransaction
        List<String> validTransactionTypes;
        if (stream.getType().name().equalsIgnoreCase("workspace")) {
            validTransactionTypes = AccurevServer.DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES;
        } else {
            validTransactionTypes = AccurevServer.DEFAULT_VALID_STREAM_TRANSACTION_TYPES;
        }
        listener.getLogger().println(//
                "Checking transactions of type " + String.join(", ", validTransactionTypes) + //
                        " in stream [" + stream.getName() + "]");
        boolean isTransLatestThanBuild = false;

        Collection<String> serverPaths;
        Set<String> pollingFilters = getListOfPollingFilters(filterForPollSCM, subPath);

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
                            if (!changesMatchFilter(serverPaths, pollingFilters)) {
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

    private static boolean changesMatchFilter(Collection<String> serverPaths, Collection<String> filters) {
        if (CollectionUtils.isEmpty(filters)) {
            // No filters, so always a match.
            return true;
        }

        final String[] filterArray = filters.toArray(new String[filters.size()]);

        for (String path : serverPaths) {
            if (StringUtils.indexOfAny(sanitizeSlashes(path), filterArray) > -1) {
                // Path contains one of the filters
                return true;
            }
        }

        return false;
    }

    private static Set<String> getListOfPollingFilters(String filterForPollSCM, String subPath) {
        if (StringUtils.isNotBlank(filterForPollSCM)) {
            return splitAndSanitizeFilters(filterForPollSCM);
        }

        return splitAndSanitizeFilters(subPath);
    }

    private static Set<String> splitAndSanitizeFilters(String input) {
        if (StringUtils.isBlank(input)) {
            return null;
        }

        final char DELIMITER = ',';
        final String STRIP_CHARS = " \t\n\r/";
        String[] filters = StringUtils.split(sanitizeSlashes(input), DELIMITER);
        filters = StringUtils.stripAll(filters, STRIP_CHARS);

        return new HashSet<>(Arrays.asList(filters));
    }

    private static String sanitizeSlashes(String input) {
        return input.replace('\\', '/');
    }
}
