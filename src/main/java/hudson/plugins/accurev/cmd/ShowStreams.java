package hudson.plugins.accurev.cmd;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.TaskListener;
import hudson.plugins.accurev.*;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.parsers.xml.ParseShowStreams;
import hudson.util.ArgumentListBuilder;
import hudson.util.ComboBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShowStreams extends Command {
    private static final Logger logger = Logger.getLogger(ShowStreams.class.getName());

    public static Map<String, AccurevStream> getStreams(//
                                                        final AccurevSCM scm, //
                                                        final String nameOfStreamRequired, //
                                                        final AccurevServer server, //
                                                        final Map<String, String> accurevEnv, //
                                                        final FilePath workspace, //
                                                        final TaskListener listener, //
                                                        final String accurevPath, //
                                                        final Launcher launcher) throws IOException, InterruptedException {
        final Map<String, AccurevStream> streams;
        if (scm.isIgnoreStreamParent()) {
            streams = getOneStream(scm, nameOfStreamRequired, server, accurevEnv, workspace, listener, accurevPath, launcher);
        } else if (server.isUseRestrictedShowStreams()) {
            streams = getAllAncestorStreams(scm, nameOfStreamRequired, server, accurevEnv, workspace, listener, accurevPath, launcher);
        } else {
            streams = getAllStreams(scm, server, accurevEnv, workspace, listener, accurevPath, launcher);
        }
        setParents(streams);
        return streams;
    }

    private static Map<String, AccurevStream> getAllStreams(//
                                                            final AccurevSCM scm, //
                                                            final AccurevServer server, //
                                                            final Map<String, String> accurevEnv, //
                                                            final FilePath workspace, //
                                                            final TaskListener listener, //
                                                            final String accurevPath, //
                                                            final Launcher launcher) {
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("show");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-p");
        cmd.add(scm.getDepot());
        cmd.add("streams");
        final Map<String, AccurevStream> streams = AccurevLauncher.runCommand("Show streams command", launcher, cmd, scm.getOptionalLock(), accurevEnv,
                workspace, listener, logger, XmlParserFactory.getFactory(), new ParseShowStreams(), scm.getDepot());
        return streams;
    }

    private static Map<String, AccurevStream> getAllAncestorStreams(//
                                                                    final AccurevSCM scm, //
                                                                    final String nameOfStreamRequired, //
                                                                    final AccurevServer server, //
                                                                    final Map<String, String> accurevEnv, //
                                                                    final FilePath workspace, //
                                                                    final TaskListener listener, //
                                                                    final String accurevPath, //
                                                                    final Launcher launcher) {
        final Map<String, AccurevStream> streams = new HashMap<>();
        String streamName = nameOfStreamRequired;
        while (streamName != null && !streamName.isEmpty()) {
            final Map<String, AccurevStream> oneStream = getOneStream(scm, streamName, server, accurevEnv, workspace, listener, accurevPath, launcher);
            final AccurevStream theStream = oneStream == null ? null : oneStream.get(streamName);
            streamName = null;
            if (theStream != null) {
                if (theStream.getBasisName() != null) {
                    streamName = theStream.getBasisName();
                } else if (theStream.getBasisNumber() != null) {
                    streamName = theStream.getBasisNumber().toString();
                }
                streams.putAll(oneStream);
            }
        }
        return streams;
    }

    private static Map<String, AccurevStream> getOneStream(//
                                                           final AccurevSCM scm, //
                                                           final String streamName, //
                                                           final AccurevServer server, //
                                                           final Map<String, String> accurevEnv, //
                                                           final FilePath workspace, //
                                                           final TaskListener listener, //
                                                           final String accurevPath, //
                                                           final Launcher launcher) {
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("show");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-p");
        cmd.add(scm.getDepot());
        cmd.add("-s");
        cmd.add(streamName);
        cmd.add("streams");
        final Map<String, AccurevStream> oneStream = AccurevLauncher.runCommand("Restricted show streams command", launcher, cmd, scm.getOptionalLock(),
                accurevEnv, workspace, listener, logger, XmlParserFactory.getFactory(), new ParseShowStreams(), scm.getDepot());
        return oneStream;
    }

    private static void setParents(Map<String, AccurevStream> streams) {
        //build the tree
        streams.values()
                .stream()
                .filter(stream -> stream.getBasisName() != null)
                .forEach(stream -> stream.setParent(streams.get(stream.getBasisName())));
    }

    //Populating streams dynamically in the global config page

    public static ComboBoxModel getStreamsForGlobalConfig(//
                                                          final AccurevServer server,
                                                          final String depot,
                                                          final String accurevPath,
                                                          final ComboBoxModel cbm,
                                                          final Logger descriptorlogger
    ) {

        final ArgumentListBuilder cmd = new ArgumentListBuilder();

        cmd.add(accurevPath);
        cmd.add("show");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-p");
        cmd.add(depot);
        cmd.add("streams");

        if (StringUtils.isEmpty(depot)) {
            return cbm;
        }

        try {
            descriptorlogger.info(cmd.toString());
            Jenkins jenkins = Jenkins.getActiveInstance();
            ProcStarter starter = Jenkins.getActiveInstance().createLauncher(TaskListener.NULL).launch().readStdout().cmds(cmd);
            starter.pwd(jenkins.getRootDir());
            Proc proc = starter.start();
            final int commandExitCode = proc.join();

            if (commandExitCode == 0) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder parser;
                parser = factory.newDocumentBuilder();
                Document doc = parser.parse(proc.getStdout());
                doc.getDocumentElement().normalize();
                NodeList nList = doc.getElementsByTagName("stream");
                for (int i = 0; i < nList.getLength(); i++) {
                    Node nNode = nList.item(i);
                    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element eElement = (Element) nNode;
                        if (!(eElement.getAttribute("type").equals("workspace"))) {
                            cbm.add(eElement.getAttribute("name"));
                        }
                    }
                }
                Collections.sort(cbm);
            } else {
                descriptorlogger.warning("AccuRev Server: " + server.getName() + ". " + convertStreamToString(proc.getStdout()));
            }
        } catch (IOException | SAXException | ParserConfigurationException | InterruptedException e) {
            descriptorlogger.log(Level.WARNING, "AccuRev Server: " + server.getName() + ". " + "Could not populate stream list.", e.getCause());
        }

        return cbm;
    }
}
