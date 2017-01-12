package hudson.plugins.accurev;

import hudson.Extension;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.*;
import hudson.model.listeners.ItemListener;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.jelly.XMLOutput;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Created by josp on 16/08/16.
 */
public class AccurevPromoteTrigger extends Trigger<AbstractProject<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(AccurevPromoteTrigger.class.getName());
    private static final HashMap<String, AccurevPromoteListener> listeners = new HashMap<>();
    private static final String ACCUREVLASTTRANSFILENAME = "AccurevLastTrans.txt";

    @DataBoundConstructor
    public AccurevPromoteTrigger() {
        super();
    }

    public synchronized static void initServer(String host, AccurevSCM.AccurevServer server) {
        if (!listeners.containsKey(host) && server.isUsePromoteListen()) {
            listeners.put(host, new AccurevPromoteListener(server));
        }
    }

    public synchronized static void validateListeners() {
        AccurevSCM.AccurevSCMDescriptor descriptor = Jenkins.getInstance().getDescriptorByType(AccurevSCM.AccurevSCMDescriptor.class);
        for (AccurevSCM.AccurevServer server : descriptor.getServers()) {
            initServer(server.getHost(), server);
        }
        for (Project<?, ?> p : Jenkins.getInstance().getAllItems(Project.class)) {
            AccurevPromoteTrigger t = p.getTrigger(AccurevPromoteTrigger.class);
            if (t != null) {
                if (t.getServer().isUsePromoteListen()) {
                    String host = t.getServer().getHost();
                    if (StringUtils.isNotEmpty(host)) {
                        AccurevPromoteListener listener = listeners.get(host);
                        if (null != listener) {
                            listener.addTrigger(t);
                        }
                    }
                }
            }
        }
    }

    public static void setLastTransaction(Job<?, ?> job, String previous) throws IOException {
        File f = new File(job.getRootDir(), ACCUREVLASTTRANSFILENAME);
        try (BufferedWriter br = Files.newBufferedWriter(f.toPath(), UTF_8)) {
            br.write(previous);
        }
    }

    private String getLastTransaction(Job<?, ?> job) throws IOException {
        File f = new File(job.getRootDir(), ACCUREVLASTTRANSFILENAME);
        if (!f.exists()) {
            if (f.createNewFile()) return "";
            else throw new IOException("Failed to create file");
        }
        try (BufferedReader br = Files.newBufferedReader(f.toPath(), UTF_8)) {
            return br.readLine();
        }
    }

    @Override
    public void start(AbstractProject<?, ?> project, boolean newInstance) {
        super.start(project, newInstance);
        AccurevSCM.AccurevServer server = getServer();
        String host = server.getHost();
        if (StringUtils.isNotEmpty(host)) {
            initServer(host, server);
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
        String host = getServer().getHost();
        if (StringUtils.isNotEmpty(host)) {
            AccurevPromoteListener listener = listeners.get(host);
            if (listener != null) {
                listener.removeTrigger(this);
            }
        }
        super.stop();
    }

    public String getProjectName() {
        return job == null ? "" : job.getName();
    }

    public String getDepot() {
        AccurevSCM scm = getScm();
        return scm == null ? "" : scm.getDepot();
    }

    public String getStream() {
        AccurevSCM scm = getScm();
        return scm == null ? "" : scm.getStream();
    }

    public AccurevSCM.AccurevServer getServer() {
        AccurevSCM scm = getScm();
        if (null != scm) {
            return scm.getServer();
        }
        return null;
    }

    public void scheduleBuild(String author, String stream) {
        LOGGER.fine("schedule build: " + getProjectName());
        if (job != null) job.scheduleBuild2(10, new AccurevPromoteCause(author, stream));
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @CheckForNull
    public AccurevSCM getScm() {
        if (job != null && job.getScm() instanceof AccurevSCM) {
            return (AccurevSCM) job.getScm();
        }
        return null;
    }

    public boolean checkForChanges(String promoteDepot, String promoteStream, int promoteTrans, Map<String, AccurevStream> streams) {
        try (StreamTaskListener listener = new StreamTaskListener(getLogFile())) {
            PrintStream logger = listener.getLogger();
            AccurevSCM scm = getScm();
            if (scm == null || job == null) return false;
            if (getStream().equals(promoteStream)) {
                logger.println("Matching stream: " + promoteStream);

                int lastTrans = NumberUtils.toInt(getLastTransaction(job), 0);
                logger.println("Last build Transaction: " + lastTrans + ", promote transaction: " + promoteTrans);
                if (promoteTrans > lastTrans) {
                    setLastTransaction(job, String.valueOf(promoteTrans));
                    return true;
                }
            } else if (promoteDepot.equals(getDepot()) && !scm.isIgnoreStreamParent()) {

                String localStream = scm.getPollingStream(job, listener);

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
                        logger.println("Found matching parent stream: " + promoteStream);

                        int lastTrans = NumberUtils.toInt(getLastTransaction(job), 0);
                        logger.println("Last build Transaction: " + lastTrans + ", promote transaction: " + promoteTrans);
                        if (promoteTrans > lastTrans) {
                            setLastTransaction(job, String.valueOf(promoteTrans));
                            return true;
                        }
                    }
                    stream = stream.getParent();
                } while (stream != null && stream.isReceivingChangesFromParent());
                logger.println("No matching parent stream found");
            }
        } catch (IllegalArgumentException | IOException ex) {
            LOGGER.warning(ex.getMessage());
        }
        return false;
    }

    private File getLogFile() throws IOException {
        if (job == null) throw new IOException("No job");
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

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Build when a change is promoted to AccuRev";
        }

        @Extension
        public static class ItemListenerImpl extends ItemListener {

            @Override
            public void onLoaded() {
                validateListeners();
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

        @SuppressWarnings("unused") // Used by Jetty
        public void writeLogTo(XMLOutput out) throws IOException {
            new AnnotatedLargeText<>(getLogFile(), UTF_8, true, this)
                    .writeHtmlTo(0, out.asWriter());
        }
    }

}
