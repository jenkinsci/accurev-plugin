package hudson.plugins.accurev.cmd;

import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.ByteArrayStream;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShowDepots extends Command {

    public static List<String> getDepots(//
                                         final AccurevServer server,
                                         final String accurevPath,
                                         final Logger descriptorlogger
    ) {

        final List<String> depots = new ArrayList<>();
        final ArgumentListBuilder cmd = new ArgumentListBuilder();

        cmd.add(accurevPath);
        cmd.add("show");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("depots");

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
                NodeList nList = doc.getElementsByTagName("Element");
                for (int i = 0; i < nList.getLength(); i++) {
                    Node nNode = nList.item(i);
                    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element eElement = (Element) nNode;
                        depots.add(eElement.getAttribute("Name"));

                    }
                }

            } else {
                descriptorlogger.warning("AccuRev Server: " + server.getName() + ". " + convertStreamToString(proc.getStdout()));
            }
        } catch (IOException | ParserConfigurationException | InterruptedException e) {
            descriptorlogger.log(Level.WARNING, "AccuRev Server: " + server.getName() + ". " + "Could not populate depot list.", e.getCause());
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }

        return depots;
    }
}
