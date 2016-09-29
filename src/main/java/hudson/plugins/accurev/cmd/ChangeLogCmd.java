package hudson.plugins.accurev.cmd;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.GetConfigWebURL;
import hudson.plugins.accurev.XmlParserFactory;
import hudson.plugins.accurev.parsers.output.ParseOutputToFile;
import hudson.plugins.accurev.parsers.xml.ParseGetConfig;
import hudson.util.ArgumentListBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

public class ChangeLogCmd {

    public static boolean captureChangelog(AccurevServer server,
                                           Map<String, String> accurevEnv,
                                           FilePath workspace,
                                           TaskListener listener,
                                           String accurevPath,
                                           Launcher launcher,
                                           Date buildDate,
                                           Date startDate,
                                           String stream,
                                           File changelogFile,
                                           Logger logger,
                                           AccurevSCM scm) throws IOException, InterruptedException {

        final String accurevACSYNCEnvVar = "AC_SYNC";
        if (!accurevEnv.containsKey(accurevACSYNCEnvVar)) {
            final String accurevACSYNC = "IGNORE";
            accurevEnv.put(accurevACSYNCEnvVar, accurevACSYNC);
            listener.getLogger().println("Setting " + accurevACSYNCEnvVar + " to \"" + accurevACSYNC + '"');
        }
        ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("hist");
        Command.addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-a");
        cmd.add("-s");
        cmd.add(stream);
        cmd.add("-t");
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        String dateRange = formatter.format(buildDate);
        if (startDate != null) {
            dateRange += "-" + formatter.format(startDate);
        } else {
            dateRange += ".100";
        }
        cmd.add(dateRange); // if this breaks windows there's going to be fun
        final String commandDescription = "Changelog command";
        final Boolean success = AccurevLauncher.runCommand(commandDescription, launcher, cmd, null, scm.getOptionalLock(),
                accurevEnv, workspace, listener, logger, new ParseOutputToFile(), changelogFile);
        if (success == null || !success) {
            return false;
        }
        //==============================================================================================
        //See the content of changelogfile

        if (changelogFile != null) {
            applyWebURL(server, accurevEnv, workspace, listener, accurevPath, launcher, changelogFile, logger, scm);
        }
        //=============================================================================================
        listener.getLogger().println("Changelog calculated successfully.");
        return true;
    }

    /**
     * Parse the settings.xml file to get the webui url and apply it to the changelog.
     *
     * @param server        Server
     * @param accurevEnv    Accurev Enviroment
     * @param workspace     Workspace
     * @param listener      listener
     * @param accurevPath   AccurevPath
     * @param launcher      Launcher
     * @param changelogFile Change log file
     * @param logger        logger
     * @param scm           Accurev SCM
     */
    private static void applyWebURL(AccurevServer server,
                                    Map<String, String> accurevEnv,
                                    FilePath workspace,
                                    TaskListener listener,
                                    String accurevPath,
                                    Launcher launcher,
                                    File changelogFile,
                                    Logger logger,
                                    AccurevSCM scm) {

        final ArgumentListBuilder getConfigcmd = new ArgumentListBuilder();
        getConfigcmd.add(accurevPath);
        getConfigcmd.add("getconfig");
        Command.addServer(getConfigcmd, server);
        getConfigcmd.add("-s");
        getConfigcmd.add("-r");
        getConfigcmd.add("settings.xml");
        GetConfigWebURL webuiURL;
        Map<String, GetConfigWebURL> webURL = null;

        try {
            webURL = AccurevLauncher.runCommand("Get config to fetch webURL",
                    launcher, getConfigcmd, null, scm.getOptionalLock(), accurevEnv, workspace, listener, logger,
                    XmlParserFactory.getFactory(), new ParseGetConfig(), null);
        } catch (Exception e) {
            logger.warning("Error loading settings.xml");
            // Error getting settings.xml file.
        }

        if (webURL == null || webURL.isEmpty()) {
            return;
        }

        webuiURL = webURL.get("webuiURL");
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder;
        try {
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(changelogFile);

            NodeList nodes = document.getElementsByTagName("transaction");

            Element depotElement = document.createElement("depot");
            if (nodes != null && nodes.getLength() > 0)
                nodes.item(0).getParentNode().insertBefore(depotElement, nodes.item(0));

            depotElement.appendChild(document.createTextNode(scm.getDepot()));

            Element webuiElement = document.createElement("webuiURL");
            if (nodes != null && nodes.getLength() > 0)
                nodes.item(0).getParentNode().insertBefore(webuiElement, nodes.item(0));

            if (webuiURL != null)
                webuiElement.appendChild(document.createTextNode((webuiURL.getWebURL().endsWith("/") ? (webuiURL.getWebURL().substring(0, webuiURL.getWebURL().length() - 1)) : (webuiURL.getWebURL()))));
            else
                webuiElement.appendChild(document.createTextNode(""));

            DOMSource source = new DOMSource(document);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            StreamResult result = new StreamResult(changelogFile);
            transformer.transform(source, result);
        } catch (ParserConfigurationException | IOException | SAXException | TransformerException e) {

        }
    }
}
