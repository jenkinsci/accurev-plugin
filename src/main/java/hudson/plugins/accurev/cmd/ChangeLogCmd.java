package hudson.plugins.accurev.cmd;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevSCMBackwardCompatibility.AccurevServer;
import hudson.plugins.accurev.GetConfigWebURL;
import hudson.plugins.accurev.XmlParserFactory;
import hudson.plugins.accurev.parsers.output.ParseOutputToFile;
import hudson.plugins.accurev.parsers.xml.ParseGetConfig;
import hudson.util.ArgumentListBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParserFactory;

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
                                           EnvVars accurevEnv,
                                           FilePath workspace,
                                           TaskListener listener,
                                           Launcher launcher,
                                           Date buildDate,
                                           Date startDate,
                                           String stream,
                                           File changelogFile,
                                           Logger logger,
                                           AccurevSCM scm,
                                           Map<String, GetConfigWebURL> webURL) throws IOException {
        final String accurevACSYNCEnvVar = "AC_SYNC";
        if (!accurevEnv.containsKey(accurevACSYNCEnvVar)) {
            final String accurevACSYNC = "IGNORE";
            accurevEnv.put(accurevACSYNCEnvVar, accurevACSYNC);
            listener.getLogger().println("Setting " + accurevACSYNCEnvVar + " to \"" + accurevACSYNC + '"');
        }
        ArgumentListBuilder cmd = new ArgumentListBuilder();
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
        final Boolean success = AccurevLauncher.runCommand(commandDescription, launcher, cmd, scm.getOptionalLock(),
                accurevEnv, workspace, listener, logger, new ParseOutputToFile(), changelogFile);
        if (success == null || !success) {
            return false;
        }
        //==============================================================================================
        //See the content of changelogfile

        if (changelogFile != null) {
            applyWebURL(webURL, changelogFile, scm);
        }
        //=============================================================================================
        listener.getLogger().println("Changelog calculated successfully.");
        return true;
    }

    /**
     * Retrieve the settings.xml file to get the webURL.
     *
     * @param server     Server
     * @param accurevEnv Accurev Environment
     * @param workspace  Workspace
     * @param listener   listener
     * @param launcher   Launcher
     * @param logger     logger
     * @param scm        Accurev SCM
     * @return webURL
     * @throws IOException Handle it above
     */

    public static Map<String, GetConfigWebURL> retrieveWebURL(AccurevServer server,
                                                              EnvVars accurevEnv,
                                                              FilePath workspace,
                                                              TaskListener listener,
                                                              Launcher launcher,
                                                              Logger logger,
                                                              AccurevSCM scm) throws IOException {
        final ArgumentListBuilder getConfigCmd = new ArgumentListBuilder();
        getConfigCmd.add("getconfig");
        Command.addServer(getConfigCmd, server);
        getConfigCmd.add("-s");
        getConfigCmd.add("-r");
        getConfigCmd.add("settings.xml");
        XmlPullParserFactory parser = XmlParserFactory.getFactory();
        if (parser == null) throw new IOException("No XML Parser");

        return AccurevLauncher.runCommand("Get config to fetch webURL",
                launcher, getConfigCmd, scm.getOptionalLock(), accurevEnv, workspace, listener, logger,
                parser, new ParseGetConfig(), null);

    }

    /**
     * Parses changelog to apply the given webURL
     *
     * @param webURL        webURL
     * @param changelogFile changeLogFile
     * @param scm           Accurev SCM
     */
    private static void applyWebURL(Map<String, GetConfigWebURL> webURL,
                                    File changelogFile,
                                    AccurevSCM scm) {

        if (webURL == null || webURL.isEmpty()) {
            return;
        }

        GetConfigWebURL webuiURL = webURL.get("webuiURL");
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
        } catch (ParserConfigurationException | IOException | SAXException | TransformerException ignored) {

        }
    }
}
