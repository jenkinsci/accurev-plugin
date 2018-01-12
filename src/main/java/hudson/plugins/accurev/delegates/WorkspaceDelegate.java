package hudson.plugins.accurev.delegates;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

import hudson.model.Job;
import hudson.model.Run;
import hudson.scm.PollingResult;
import hudson.util.ArgumentListBuilder;
import jenkins.plugins.accurevclient.model.AccurevStream;
import jenkins.plugins.accurevclient.model.AccurevWorkspace;

import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.RemoteWorkspaceDetails;
import hudson.plugins.accurev.cmd.Command;
import hudson.plugins.accurev.delegates.Relocation.RelocationOption;

/**
 * @author raymond
 */
public class WorkspaceDelegate extends ReftreeDelegate {

    private static final Logger logger = Logger.getLogger(WorkspaceDelegate.class.getName());

    public WorkspaceDelegate(AccurevSCM scm) {
        super(scm);
    }

    @Override
    public String getRefTree() {
        return null;
    }

    @Override
    protected PollingResult checkForChanges(Job<?, ?> project) throws IOException, InterruptedException, IllegalArgumentException {
        localStream = scm.getPollingStream(project, listener);
        return super.checkForChanges(project);
    }

    @Override
    protected Relocation checkForRelocation() throws IOException, InterruptedException {
        String depot = scm.getDepot();
        String _accurevWorkspace = scm.getWorkspace();
        Map<String, AccurevWorkspace> workspaces = client.getWorkspaces().getMap();

        if (workspaces.isEmpty()) {
            throw new IllegalArgumentException("Cannot determine workspace configuration information");
        }
        if (!workspaces.containsKey(_accurevWorkspace)) {
            throw new IllegalArgumentException("The specified workspace does not appear to exist!");
        }
        AccurevWorkspace accurevWorkspace = workspaces.get(_accurevWorkspace);
        if (!depot.equals(accurevWorkspace.getDepot())) {
            throw new IllegalArgumentException("The specified workspace, " + _accurevWorkspace + ", is based in the depot " + accurevWorkspace.getDepot() + " not " + depot);
        }

        final RemoteWorkspaceDetails remoteDetails = getRemoteWorkspaceDetails();

        List<RelocationOption> relocationOptions = new ArrayList<>();
        for (WorkspaceRelocation workspaceRelocationvalue : WorkspaceRelocation.values()) {
            if (workspaceRelocationvalue.isRequired(accurevWorkspace, remoteDetails, localStream)) {
                relocationOptions.add(workspaceRelocationvalue);
            }
        }
        return new Relocation(relocationOptions, remoteDetails.getHostName(), accurevWorkingSpace.getRemote(), localStream);
    }

    @Override
    protected boolean validateCheckout(Run<?, ?> build) {
        String workspace = scm.getWorkspace();
        if (StringUtils.isEmpty(workspace)) {
            listener.fatalError("Must specify a workspace");
            return false;
        }
        return true;
    }

    @Override
    protected String getPopulateFromMessage() {
        return "from workspace";
    }

    @Override
    protected ArgumentListBuilder getRelocateCommand() {
        ArgumentListBuilder chwscmd = new ArgumentListBuilder();
        chwscmd.add("chws");
        Command.addServer(chwscmd, server);
        chwscmd.add("-w");
        chwscmd.add(scm.getWorkspace());
        return chwscmd;
    }

    @Override
    protected boolean isSteamColorEnabled() {
        return true;
    }

    @Override
    protected String getStreamColorStream() {
        return scm.getWorkspace();
    }

    @Override
    protected String getStreamColor() {
        return "#FFEBB4";
    }

    @Override
    protected String getChangeLogStream() {
        return scm.getWorkspace();
    }

    @Override
    protected void buildEnvVarsCustom(Run<?, ?> build, Map<String, String> env) {
        env.put("ACCUREV_WORKSPACE", scm.getWorkspace());
    }

    private enum WorkspaceRelocation implements RelocationOption {

        HOST {
            @Override
            protected boolean isRequired(AccurevWorkspace accurevWorkspace, RemoteWorkspaceDetails remoteDetails, String localStream) {
                return !accurevWorkspace.getHost().equalsIgnoreCase(remoteDetails.getHostName());
            }

            public void appendCommand(ArgumentListBuilder cmd, Relocation relocation) {
                cmd.add("-m");
                cmd.add(relocation.getNewHost());
            }

        },
        STORAGE {
            @Override
            protected boolean isRequired(AccurevWorkspace accurevWorkspace, RemoteWorkspaceDetails remoteDetails, String localStream) {
                String oldStorage = accurevWorkspace.getStorage()
                    .replace("/", remoteDetails.getFileSeparator())
                    .replace("\\", remoteDetails.getFileSeparator());
                return !new File(oldStorage).equals(new File(remoteDetails.getPath()));
            }

            public void appendCommand(ArgumentListBuilder cmd, Relocation relocation) {
                cmd.add("-l");
                cmd.add(relocation.getNewPath());
            }

        },
        REPARENT {
            @Override
            protected boolean isRequired(AccurevWorkspace accurevWorkspace, RemoteWorkspaceDetails remoteDetails, String localStream) {
                String name = Optional.ofNullable(accurevWorkspace.getStream())
                    .map(AccurevStream::getParent)
                    .map(AccurevStream::getName)
                    .orElse("");
                return !localStream.equals(name);
            }

            @Override
            public boolean isPopRequired() {
                return false;
            }

            public void appendCommand(ArgumentListBuilder cmd, Relocation relocation) {
                cmd.add("-b");
                cmd.add(relocation.getNewParent());
            }

        };

        public boolean isPopRequired() {
            return true;
        }

        protected abstract boolean isRequired(AccurevWorkspace accurevWorkspace, RemoteWorkspaceDetails remoteDetails, String localStream);

    }

}
