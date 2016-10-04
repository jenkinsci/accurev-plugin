package hudson.plugins.accurev.cmd;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.parsers.output.ParsePopulate;
import hudson.util.ArgumentListBuilder;
import org.apache.commons.lang.StringUtils;

import java.util.Date;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Logger;

public class PopulateCmd extends Command {


    private static final Logger logger = Logger.getLogger(PopulateCmd.class.getName());

    private Date _startDateOfPopulate;

    /**
     * @return Date
     */
    public Date get_startDateOfPopulate() {
        return (Date) _startDateOfPopulate.clone();
    }

    /**
     * @param launcher             launcher
     * @param listener             listener
     * @param server               server
     * @param accurevClientExePath Accurev Client Executable Path
     * @param streamName           stream Name
     * @param fromMessage          from Messge
     * @param accurevWorkingSpace  Accurev Workspace
     * @param accurevEnv           Accurev Environment
     * @param scm                  Accurev SCm
     * @param overwrite            overwrite
     * @return boolean
     */
    public boolean populate(AccurevSCM scm, Launcher launcher, TaskListener listener,
                            AccurevServer server, String accurevClientExePath,
                            String streamName,
                            boolean overwrite,
                            String fromMessage,
                            FilePath accurevWorkingSpace, Map<String, String> accurevEnv) {
        listener.getLogger().println("Populating " + fromMessage + "...");
        final ArgumentListBuilder popcmd = new ArgumentListBuilder();
        popcmd.add(accurevClientExePath);
        popcmd.add("pop");
        addServer(popcmd, server);

        if (streamName != null) {
            popcmd.add("-v");
            popcmd.add(streamName);
        }

        popcmd.add("-L");
        popcmd.add(accurevWorkingSpace.getRemote());

        if (overwrite) popcmd.add("-O");

        popcmd.add("-R");
        if (StringUtils.isBlank(scm.getSubPath())) {
            popcmd.add(".");
        } else {
            final StringTokenizer st = new StringTokenizer(scm.getSubPath(), ",");
            while (st.hasMoreElements()) {
                popcmd.add(st.nextToken().trim());
            }
        }
        _startDateOfPopulate = new Date();
        final Boolean success = AccurevLauncher.runCommand("Populate " + fromMessage + " command", launcher, popcmd, scm.getOptionalLock(), accurevEnv,
                accurevWorkingSpace, listener, logger, new ParsePopulate(), listener.getLogger());
        if (success == null || !success) {
            return false;
        }
        listener.getLogger().println("Populate completed successfully.");
        return true;
    }
}
