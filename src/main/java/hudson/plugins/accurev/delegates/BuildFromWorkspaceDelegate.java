package hudson.plugins.accurev.delegates;

import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.XmlConsolidateStreamChangeLog;
import hudson.plugins.accurev.cmd.ShowWorkspaces;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

/**
 * Class will be used to delegate build directly from the workspace.
 * 
 * @author DLenka
 *
 */
public class BuildFromWorkspaceDelegate extends ReftreeDelegate {

    private static final Logger logger = Logger.getLogger(BuildFromWorkspaceDelegate.class.getName());
    private File updateLogFile;

    public BuildFromWorkspaceDelegate(AccurevSCM scm) {
        super(scm);
    }

    @Override
    public String getRefTree() {
        return null;
    }

    @Override
    protected boolean checkout(Run<?, ?> build, File changeLogFile) throws IOException, InterruptedException {
        if (!validateCheckout(build)) {
            return false;
        }
        updateLogFile = XmlConsolidateStreamChangeLog.getUpdateChangeLogFile(changeLogFile);
        return true;
    }

    @Override
    protected String getUpdateFileName() {
        return updateLogFile.getName();
    }

    protected boolean validateCheckout(Run<?, ?> build) {
        try {
            String workspaceName = scm.getWorkspaceName();
            if (StringUtils.isEmpty(workspaceName)) {
                listener.fatalError("Must specify a valid  workspace Name");
                return false;
            }
            String stream = ShowWorkspaces.getParentStreamOfWorkspace(scm.getServer(), scm.getDepot(), workspaceName);
            if (scm.getStream() != null && !stream.equals(scm.getStream())) {
                listener.fatalError("Must specify a valid parent stream.");
                return false;
            }
        }
        catch (IOException e) {
            logger.info("Exception happen to retrieve the parent stream of given workspace." + e);
            return false;
        }

        return true;
    }

    @Override
    protected String getPopulateFromMessage() {
        return "Build from workspace";
    }

    @Override
    protected String getChangeLogStream() {
        return scm.getWorkspaceName();
    }

    @Override
    protected String getPopulateStream() {
        return localStream;
    }

    @Override
    protected void buildEnvVarsCustom(AbstractBuild<?, ?> build, Map<String, String> env) {
        env.put("ACCUREV_WORKSPACE_NAME", scm.getWorkspaceName());
    }

    public boolean isPopRequired() {
        return true;
    }
}
