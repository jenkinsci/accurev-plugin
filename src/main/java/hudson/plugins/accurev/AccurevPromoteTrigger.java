package hudson.plugins.accurev;

import com.google.common.base.Charsets;
import hudson.*;
import hudson.console.AnnotatedLargeText;
import hudson.model.*;
import hudson.model.listeners.ItemListener;
import hudson.plugins.accurev.cmd.Login;
import hudson.plugins.accurev.cmd.ShowStreams;
import hudson.plugins.accurev.cmd.Synctime;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.jelly.XMLOutput;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by josp on 16/08/16.
 */
public class AccurevPromoteTrigger extends Trigger<AbstractProject<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(AccurevPromoteTrigger.class.getName());
    private static final HashMap<String, AccurevPromoteListener> listeners = new HashMap<>();

    @DataBoundConstructor
    public AccurevPromoteTrigger() {
        super();
    }

    public static void initServer(String host) {
        if (!listeners.containsKey(host)) {
            listeners.put(host, new AccurevPromoteListener(host));
        }
    }

    @Override
    public void start(AbstractProject<?, ?> project, boolean newInstance) {
        super.start(project, newInstance);
        String host = getHost();
        if (StringUtils.isNotEmpty(host)) {
            initServer(host);
            AccurevPromoteListener listener = listeners.get(host);

            if (listener != null) {
                listener.addTrigger(this);
                removeDuplicatedTriggers(listener.getTriggers());
            }
        }
    }

    private void removeDuplicatedTriggers(HashSet<AccurevPromoteTrigger> triggers) {
        Map<String, AccurevPromoteTrigger> temp = new HashMap<>();
        for (AccurevPromoteTrigger trigger : triggers) {
            temp.put(trigger.getProjectName(), trigger);
        }
        triggers.clear();
        triggers.addAll(temp.values());
    }

    @Override
    public void stop() {
        String host = getHost();
        if (StringUtils.isNotEmpty(host)) {
            AccurevPromoteListener listener = listeners.get(host);
            listener.removeTrigger(this);
        }
        super.stop();
    }

    public String getProjectName() {
        if (job != null) {
            return job.getName();
        }
        return "";
    }

    public String getDepot() {
        if (getScm() != null) {
            return getScm().getDepot();
        }
        return "";
    }

    public String getStream() {
        if (getScm() != null) {
            return getScm().getStream();
        }
        return "";
    }

    public String getHost() {
        if (getScm() != null) {
            return getScm().getServer().getHost();
        }
        return "";
    }

    public void scheduleBuild(String author, String stream) {
        job.scheduleBuild2(10, new AccurevPromoteCause(author, stream));
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public AccurevSCM getScm() {
        if (job != null && job.getScm() instanceof AccurevSCM) {
            return (AccurevSCM) job.getScm();
        }
        return null;
    }

    public boolean checkForChanges(String promoteDepot, String promoteStream) {
        try {
            final Jenkins jenkins = Jenkins.getInstance();
            if (jenkins == null) {
                throw new IOException("Jenkins instance is not ready");
            }
            try (StreamTaskListener listener = new StreamTaskListener(getLogFile())) {
                PrintStream logger = listener.getLogger();
                AccurevSCM scm = getScm();
                if (scm == null) {
                    return false;
                }
                if (scm.getStream().equals(promoteStream)) {
                    logger.println("Matching stream");
                    return true;
                } else if (scm.getDepot().equals(promoteDepot)) {
                    final File projectDir = job.getRootDir();
                    FilePath jenkinsWorkspace = new FilePath(projectDir);

                    String accurevPath = jenkinsWorkspace.act(new FindAccurevClientExe(scm.getServer()));
                    Launcher launcher = jenkins.createLauncher(listener);
                    final EnvVars accurevEnv = new EnvVars();
                    accurevEnv.put("ACCUREV_CLIENT_PATH", accurevPath);
                    String localStream = scm.getStream();
                    AccurevSCM.AccurevServer server = scm.getServer();

                    if (!Login.ensureLoggedInToAccurev(server, accurevEnv, jenkinsWorkspace, listener, accurevPath, launcher)) {
                        throw new IllegalArgumentException("Authentication failure");
                    }

                    if (scm.isSynctime()) {
                        logger.println("Synchronizing clock with the server...");
                        if (!Synctime.synctime(scm, server, accurevEnv, jenkinsWorkspace, listener, accurevPath, launcher)) {
                            throw new IllegalArgumentException("Synchronizing clock failure");
                        }
                    }

                    final Map<String, AccurevStream> streams = ShowStreams.getStreams(scm, localStream, server, accurevEnv, jenkinsWorkspace, listener, accurevPath, launcher);
                    if (streams == null) {
                        listener.fatalError("streams EMPTY");
                        return false;
                    }
                    if (!streams.containsKey(localStream)) {
                        listener.fatalError("The specified stream, '" + localStream + "' does not appear to exist!");
                        return false;
                    }
                    AccurevStream stream = streams.get(localStream);
                    do {
                        if (stream.getName().equals(promoteStream)) {
                            logger.println("Found matching parent stream");
                            return true;
                        }
                        stream = stream.getParent();
                    } while (stream != null && stream.isReceivingChangesFromParent());
                }
                logger.println("No matching parent stream found");
            } catch (InterruptedException | IllegalArgumentException ex) {
                LOGGER.warning(ex.getMessage());
            }
        } catch (IOException ex) {
            LOGGER.warning(ex.getMessage());

        }
        return false;
    }

    private File getLogFile() {
        return new File(job.getRootDir(), "accurev-promote-trigger.log");
    }

    public Collection<? extends Action> getProjectActions() {
        if (job == null) {
            return Collections.emptyList();
        }
        return Collections.singleton(new AccurevPromoteAction());
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {

        @Override
        public boolean isApplicable(Item item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Build when a change is promoted to AccuRev";
        }

        @Extension
        public static class ItemListenerImpl extends ItemListener {

            @Override
            public void onLoaded() {
                AccurevSCM.AccurevSCMDescriptor descriptor = Jenkins.getInstance().getDescriptorByType(AccurevSCM.AccurevSCMDescriptor.class);
                for (AccurevSCM.AccurevServer server : descriptor.getServers()) {
                    initServer(server.getHost());
                }
                for (Project<?, ?> p : Jenkins.getInstance().getAllItems(Project.class)) {
                    AccurevPromoteTrigger t = p.getTrigger(AccurevPromoteTrigger.class);
                    if (t != null) {
                        String host = t.getHost();
                        if (StringUtils.isNotEmpty(host)) {
                            AccurevPromoteListener listener = listeners.get(host);
                            listener.addTrigger(t);
                        }
                    }
                }
            }
        }
    }

    public final class AccurevPromoteAction implements Action {

        public Job<?, ?> getOwner() {
            return job;
        }

        @Override
        public String getIconFileName() {
            return "clipboard.png";
        }

        @Override
        public String getDisplayName() {
            return "Accurev Promote Log";
        }

        @Override
        public String getUrlName() {
            return "AccurevPromoteLog";
        }

        public String getLog() throws IOException {
            return Util.loadFile(getLogFile());
        }

        public void writeLogTo(XMLOutput out) throws IOException {
            new AnnotatedLargeText<>(getLogFile(), Charsets.UTF_8, true, this)
                    .writeHtmlTo(0, out.asWriter());
        }
    }

}
