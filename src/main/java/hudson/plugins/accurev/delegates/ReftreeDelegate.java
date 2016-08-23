package hudson.plugins.accurev.delegates;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevReferenceTree;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevStream;
import hudson.plugins.accurev.DetermineRemoteHostname;
import hudson.plugins.accurev.RemoteWorkspaceDetails;
import hudson.plugins.accurev.XmlConsolidateStreamChangeLog;
import hudson.plugins.accurev.XmlParserFactory;
import hudson.plugins.accurev.cmd.Command;
import hudson.plugins.accurev.cmd.Update;
import hudson.plugins.accurev.delegates.Relocation.RelocationOption;
import hudson.plugins.accurev.parsers.xml.ParseShowReftrees;
import hudson.scm.PollingResult;
import hudson.util.ArgumentListBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 *
 * @author raymond
 */
public class ReftreeDelegate extends AbstractModeDelegate {

    protected boolean popRequired = false;
    private File updateLogFile;
    private static final Logger logger = Logger.getLogger(ReftreeDelegate.class.getName());

    public ReftreeDelegate(AccurevSCM scm) {
        super(scm);
    }

    public String getRefTree() {
        return scm.getReftree();
    }

    @Override
    protected PollingResult checkForChanges(AbstractProject<?, ?> project) throws IOException, InterruptedException {
        try {
            Relocation relocation = checkForRelocation();
            if (relocation.isRelocationRequired()) {
                listener.getLogger().println("Relocation required triggering build");
                return PollingResult.BUILD_NOW;
            } else {
                if (Update.hasChanges(scm, server, accurevEnv, accurevWorkingSpace, listener, accurevPath, launcher, getRefTree())) {
                    return PollingResult.BUILD_NOW;
                } else {
                    return PollingResult.NO_CHANGES;
                }
            }
        } catch (IllegalArgumentException ex) {
            listener.fatalError(ex.getMessage());
            return PollingResult.NO_CHANGES;
        }
    }

    protected Relocation checkForRelocation() throws IOException, InterruptedException {
        Map<String, AccurevStream> streams = null;
        final Map<String, AccurevReferenceTree> reftrees = getReftrees();
        String reftree = scm.getReftree();
        if (reftrees == null) {
            throw new IllegalArgumentException("Cannot determine reference tree configuration information");
        }
        if (!reftrees.containsKey(reftree)) {
            throw new IllegalArgumentException("The specified reference tree does not appear to exist!");
        }
        AccurevReferenceTree accurevReftree = reftrees.get(reftree);
        if (!scm.getDepot().equals(accurevReftree.getDepot())) {
            throw new IllegalArgumentException("The specified reference tree, " + reftree + ", is based in the depot " + accurevReftree.getDepot() + " not " + scm.getDepot());
        }

//        Redundant null check of value known to be null, remove? Since comment below suggests we are not using this.
//        if (streams != null) {
//            // Dont think we really use this can avoid the call
//            for (AccurevStream accurevStream : streams.values()) {
//                if (accurevReftree.getStreamNumber().equals(accurevStream.getNumber())) {
//                    accurevReftree.setStream(accurevStream);
//                    break;
//                }
//            }
//        }

        RemoteWorkspaceDetails remoteDetails = getRemoteWorkspaceDetails();

        List<RelocationOption> relocationOptions = new ArrayList<>();

        for (RefTreeRelocation refTreeRelocation : RefTreeRelocation.values()) {
            if (refTreeRelocation.isRequired(accurevReftree, remoteDetails)) {
                relocationOptions.add(refTreeRelocation);
            }
        }
        return new Relocation(relocationOptions, remoteDetails.getHostName(), accurevWorkingSpace.getRemote(), null);

    }

    protected RemoteWorkspaceDetails getRemoteWorkspaceDetails() throws IOException, InterruptedException {
        try {
            return jenkinsWorkspace.act(new DetermineRemoteHostname(accurevWorkingSpace.getRemote()));
        } catch (IOException e) {
            e.printStackTrace(listener.getLogger());
            throw new IllegalArgumentException("Unable to validate reference tree host.");
        }
    }

    private Map<String, AccurevReferenceTree> getReftrees()
            throws IOException, InterruptedException {
        listener.getLogger().println("Getting a list of reference trees...");
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("show");
        Command.addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("refs");
        final Map<String, AccurevReferenceTree> reftrees = AccurevLauncher.runCommand("Show ref trees command",
                launcher, cmd, null, scm.getOptionalLock(), accurevEnv, jenkinsWorkspace, listener, logger,
                XmlParserFactory.getFactory(), new ParseShowReftrees(), null);
        return reftrees;
    }

    @Override
    protected boolean checkout(AbstractBuild<?, ?> build, File changeLogFile) throws IOException, InterruptedException {
        if (!validateCheckout(build)) {
            return false;
        }
        Relocation relocation = checkForRelocation();
        if (relocation.isRelocationRequired()) {
            if (!relocate(relocation)) {
                return false;
            }
            if (!populate(popRequired)) {
                return false;
            }
        }

        return doUpdate(changeLogFile);
    }


    private boolean doUpdate(File changeLogFile) throws IOException, InterruptedException {
        updateLogFile = XmlConsolidateStreamChangeLog.getUpdateChangeLogFile(changeLogFile);
        return Update.performUpdate(scm, server, accurevEnv, accurevWorkingSpace, listener, accurevPath, launcher, getRefTree(), updateLogFile);
    }

    @Override
    protected String getUpdateFileName() {
        return updateLogFile.getName();
    }

    protected String getPopulateFromMessage() {
        return "from reftree";
    }

    @Override
    protected String getPopulateStream() {
        return null;
    }

    private boolean relocate(Relocation relocation) throws IOException, InterruptedException {
        ArgumentListBuilder relocateCommand = getRelocateCommand();
        popRequired = relocation.isPopRequired();
        if (popRequired) {
            listener.getLogger().println("Clearing path: " + accurevWorkingSpace.getRemote());
            accurevWorkingSpace.deleteContents();
        }
        relocation.appendCommands(relocateCommand);

        return (AccurevLauncher.runCommand("relocation command", launcher, relocateCommand, null,
                scm.getOptionalLock(), accurevEnv, accurevWorkingSpace, listener, logger, true));
    }

    protected ArgumentListBuilder getRelocateCommand() {
        ArgumentListBuilder chrefcmd = new ArgumentListBuilder();
        chrefcmd.add(accurevPath);
        chrefcmd.add("chref");
        Command.addServer(chrefcmd, server);
        chrefcmd.add("-r");
        chrefcmd.add(getRefTree());
        return chrefcmd;
    }

    protected boolean validateCheckout(AbstractBuild<?, ?> build) {
        String reftree = getRefTree();
        if (reftree == null || reftree.isEmpty()) {
            listener.fatalError("Must specify a reference tree");
            return false;
        }
        return true;
    }

    @Override
    protected void buildEnvVarsCustom(AbstractBuild<?, ?> build, Map<String, String> env) {
        env.put("ACCUREV_REFTREE", scm.getReftree());
    }

    private enum RefTreeRelocation implements RelocationOption {

        HOST {

                    @Override
                    protected boolean isRequired(AccurevReferenceTree accurevReftree, RemoteWorkspaceDetails remoteDetails) {
                        return !accurevReftree.getHost().equals(remoteDetails.getHostName());
                    }

                    public void appendCommand(ArgumentListBuilder cmd, Relocation relocation) {
                        cmd.add("-m");
                        cmd.add(relocation.getNewHost());
                    }

                },
        STORAGE {

                    @Override
                    protected boolean isRequired(AccurevReferenceTree accurevReftree, RemoteWorkspaceDetails remoteDetails) {
                        String oldStorage = accurevReftree.getStorage()
                        .replace("/", remoteDetails.getFileSeparator())
                        .replace("\\", remoteDetails.getFileSeparator());
                        return !new File(oldStorage).equals(new File(remoteDetails.getPath()));
                    }

                    public void appendCommand(ArgumentListBuilder cmd, Relocation relocation) {
                        cmd.add("-l");
                        cmd.add(relocation.getNewPath());
                    }

                };

        public boolean isPopRequired() {
            return true;
        }

        protected abstract boolean isRequired(AccurevReferenceTree accurevReftree, RemoteWorkspaceDetails remoteDetails);
    }
}
