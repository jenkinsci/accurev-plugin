package hudson.plugins.accurev;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.cmd.History;
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
     * @throws IOException          if there is issues with files
     * @throws InterruptedException if failed to interrupt properly
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
                                                AccurevSCM scm)
            throws IOException, InterruptedException {
        AccurevTransaction latestCodeChangeTransaction = new AccurevTransaction();
        String filterForPollSCM = scm.getFilterForPollSCM();
        String subPath = scm.getSubPath();
        latestCodeChangeTransaction.setDate(AccurevSCM.NO_TRANS_DATE);

        //query AccuRev for the latest transactions of each kind defined in transactionTypes using getTimeOfLatestTransaction
        String[] validTransactionTypes;
        if (stream.getType().name().equalsIgnoreCase("workspace")) {
            validTransactionTypes = AccurevServer.DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES;
            listener.getLogger().println(//
                    "Checking transactions of type " + Arrays.toString(validTransactionTypes) + //
                            " in workspace [" + stream.getName() + "]");
        } else {

            validTransactionTypes = AccurevServer.DEFAULT_VALID_STREAM_TRANSACTION_TYPES;
            listener.getLogger().println(//
                    "Checking transactions of type " + Arrays.toString(validTransactionTypes) + //
                            " in stream [" + stream.getName() + "]");
        }
        boolean isTransLatestThanBuild = false;

        Collection<String> serverPaths;

        final String FFPSCM_DELIM = ",";

        Collection<String> Filter_For_Poll_SCM = null;
        String FFPSCM_LIST = "";
        if (StringUtils.isNotEmpty(filterForPollSCM)) {
            FFPSCM_LIST = filterForPollSCM;
        } else if (StringUtils.isNotEmpty(subPath)) {
            FFPSCM_LIST = subPath;
        }
        FFPSCM_LIST = FFPSCM_LIST.replace(", ", ",");
        Filter_For_Poll_SCM = new ArrayList<>(Arrays.asList(FFPSCM_LIST.split(FFPSCM_DELIM)));
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
                            if (filterForPollSCM != null && !(filterForPollSCM.isEmpty())) {
                                if (!isBuildRequired(Filter_For_Poll_SCM, serverPaths)) {
                                    return false;
                                }
                            } else {
                                if (subPath != null && !(subPath.isEmpty())) {
                                    if (!isBuildRequired(Filter_For_Poll_SCM, serverPaths)) {
                                        return false;
                                    }
                                }
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

    private static boolean isBuildRequired(Collection<String> Filter_For_Poll_SCM, Collection<String> serverPaths) {
        for (String filterPath : Filter_For_Poll_SCM) {
            for (String serverPath : serverPaths) {
                if (serverPath.contains(filterPath)) {
                    return true;
                }
            }
        }
        return false;
    }
}
