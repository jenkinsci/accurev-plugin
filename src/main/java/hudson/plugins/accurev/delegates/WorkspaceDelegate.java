package hudson.plugins.accurev.delegates;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevStream;
import hudson.plugins.accurev.AccurevWorkspace;
import hudson.plugins.accurev.RemoteWorkspaceDetails;
import hudson.plugins.accurev.XmlParserFactory;
import hudson.plugins.accurev.cmd.Command;
import hudson.plugins.accurev.cmd.ShowStreams;
import hudson.plugins.accurev.delegates.Relocation.RelocationOption;
import hudson.plugins.accurev.parsers.xml.ParseShowWorkspaces;
import hudson.scm.PollingResult;
import hudson.util.ArgumentListBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 *
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
    protected PollingResult checkForChanges(AbstractProject<?, ?> project) throws IOException, InterruptedException {
        localStream = getPollingStream(project);
        return super.checkForChanges(project);
    }

    @Override
    protected Relocation checkForRelocation() throws IOException, InterruptedException {
        String depot = scm.getDepot();
        String _accurevWorkspace = scm.getWorkspace();
        Map<String, AccurevWorkspace> workspaces = getWorkspaces();
        Map<String, AccurevStream> streams = ShowStreams.getStreams(scm, localStream, server, accurevEnv, jenkinsWorkspace, listener, accurevPath,
                launcher);

        if (workspaces == null) {
            throw new IllegalArgumentException("Cannot determine workspace configuration information");
        }
        if (!workspaces.containsKey(_accurevWorkspace)) {
            throw new IllegalArgumentException("The specified workspace does not appear to exist!");
        }
        AccurevWorkspace accurevWorkspace = workspaces.get(_accurevWorkspace);
        if (!depot.equals(accurevWorkspace.getDepot())) {
            throw new IllegalArgumentException("The specified workspace, " + _accurevWorkspace + ", is based in the depot " + accurevWorkspace.getDepot() + " not " + depot);
        }

        if (scm.isIgnoreStreamParent()) {
            Map<String, AccurevStream> workspaceStreams = ShowStreams.getStreams(scm, _accurevWorkspace, server, accurevEnv, jenkinsWorkspace, listener, accurevPath,
                    launcher);
            if (!workspaceStreams.isEmpty()) {
                AccurevStream workspaceStream = workspaceStreams.values().iterator().next();
                accurevWorkspace.setStream(workspaceStream);
                workspaceStream.setParent(streams.get(workspaceStream.getBasisName()));
            }else{
                throw new IllegalArgumentException("Workspace stream not found " + _accurevWorkspace);
            }
        } else {
            for (AccurevStream accurevStream : streams.values()) {
                if (accurevWorkspace.getStreamNumber().equals(accurevStream.getNumber())) {
                    accurevWorkspace.setStream(accurevStream);
                    break;
                }
            }
        }

        final RemoteWorkspaceDetails remoteDetails = getRemoteWorkspaceDetails();

        List<RelocationOption> relocationOptions = new ArrayList<RelocationOption>();
        for (WorkspaceRelocation workspaceRelocationvalue : WorkspaceRelocation.values()) {
            if (workspaceRelocationvalue.isRequired(accurevWorkspace, remoteDetails, localStream)) {
                relocationOptions.add(workspaceRelocationvalue);
            }
        }
        return new Relocation(relocationOptions, remoteDetails.getHostName(), accurevWorkingSpace.getRemote(), localStream);
    }

    private Map<String, AccurevWorkspace> getWorkspaces() throws IOException, InterruptedException {
        listener.getLogger().println("Getting a list of workspaces...");
        String depot = scm.getDepot();
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("show");
        Command.addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-p");
        cmd.add(depot);
        cmd.add("wspaces");
        final Map<String, AccurevWorkspace> workspaces = AccurevLauncher.runCommand("Show workspaces command", launcher, cmd, null, scm.getOptionalLock(),
                accurevEnv, jenkinsWorkspace, listener, logger, XmlParserFactory.getFactory(), new ParseShowWorkspaces(), null);
        return workspaces;
    }

    @Override
    protected boolean validateCheckout(AbstractBuild<?, ?> build) {
        String workspace = scm.getWorkspace();
        if (workspace == null || workspace.isEmpty()) {
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
        chwscmd.add(accurevPath);
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
    protected void buildEnvVarsCustom(AbstractBuild<?, ?> build, Map<String, String> env) {
        env.put("ACCUREV_WORKSPACE", scm.getWorkspace());
    }

    private enum WorkspaceRelocation implements RelocationOption {

        HOST {

                    @Override
                    protected boolean isRequired(AccurevWorkspace accurevWorkspace, RemoteWorkspaceDetails remoteDetails, String localStream) {
                        return !accurevWorkspace.getHost().equals(remoteDetails.getHostName());
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
                        return !oldStorage.equals(remoteDetails.getPath());
                    }

                    public void appendCommand(ArgumentListBuilder cmd, Relocation relocation) {
                        cmd.add("-l");
                        cmd.add(relocation.getNewPath());
                    }

                },
        REPARENT {

                    @Override
                    protected boolean isRequired(AccurevWorkspace accurevWorkspace, RemoteWorkspaceDetails remoteDetails, String localStream) {
                        return !localStream.equals(accurevWorkspace.getStream().getParent().getName());
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
