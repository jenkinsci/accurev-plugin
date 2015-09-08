package hudson.plugins.accurev.cmd;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.XmlParserFactory;
import hudson.plugins.accurev.parsers.output.ParseOutputToFile;
import hudson.plugins.accurev.parsers.xml.ParseUpdate;
import hudson.util.ArgumentListBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 *
 * @author raymond
 */
public class Update extends Command {

    private static final Logger logger = Logger.getLogger(Update.class.getName());
    private static final String FFPSCM_DELIM = ",";

    private static ArgumentListBuilder createCommand(final AccurevSCM.AccurevServer server, //
            final String accurevPath, //
            final boolean preview,
            final String reftree,
            final boolean minus9) {
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("update");
        addServer(cmd, server);
        cmd.add("-fx");
        if (reftree != null) {
            cmd.add("-r");
            cmd.add(reftree);
        }
        if (minus9) {
            cmd.add("-9");
        }

        if (preview) {
            cmd.add("-i");
        }
        return cmd;
    }

    public static Boolean hasChanges(AccurevSCM scm, //
            AccurevSCM.AccurevServer server, //
            Map<String, String> accurevEnv, //
            FilePath workspace, //
            TaskListener listener, //
            String accurevPath, //
            Launcher launcher, //
            String reftree) throws IOException, InterruptedException {

        List<String> files = new ArrayList<String>();
        final ArgumentListBuilder cmd = createCommand(server, accurevPath, true, reftree, false);
        boolean transactionFound = AccurevLauncher.runCommand("Update command", launcher, cmd, null, scm.getOptionalLock(), accurevEnv, workspace, listener,
                logger, XmlParserFactory.getFactory(), new ParseUpdate(), files);
        if (transactionFound) {
            String filterForPollSCM = scm.getFilterForPollSCM();
            String subPath = scm.getSubPath();
            Collection<String> filterPaths = null;
            String filterList = null;
            if (filterForPollSCM != null && !(filterForPollSCM.isEmpty())) {
                filterList = filterForPollSCM;
            } else if (subPath != null && !(subPath.isEmpty())) {
                filterList = subPath;
            }

            if (filterList != null && !filterList.isEmpty()) {
                filterList = filterList.replace(", ", ",");
                filterPaths = new ArrayList<String>(Arrays.asList(filterList.split(FFPSCM_DELIM)));
            }

            if (filterPaths != null) {
                transactionFound = false;
                for (String Filter_For_Poll_SCM1 : filterPaths) {
                    for (String file : files) {
                        if (file.contains(Filter_For_Poll_SCM1)) {
                            transactionFound = true;
                            break;
                        }
                    }
                    if (transactionFound) {
                        break;
                    }
                }
            }
        }
        return transactionFound;
    }

    public static boolean performUpdate(final AccurevSCM scm, //
            final AccurevSCM.AccurevServer server, //
            final Map<String, String> accurevEnv, //
            final FilePath workspace, //
            final TaskListener listener, //
            final String accurevPath, //
            final Launcher launcher, //
            final String reftree,
            File changelogFile) throws IOException, InterruptedException {
        final ArgumentListBuilder cmd = createCommand(server, accurevPath, false, reftree, false);
        boolean success = AccurevLauncher.runCommand("Update command", launcher,
                cmd, null, scm.getOptionalLock(), accurevEnv, workspace, listener,
                logger, new ParseOutputToFile(), changelogFile);

        return success;
    }
}
