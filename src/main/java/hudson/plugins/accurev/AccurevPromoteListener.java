package hudson.plugins.accurev;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.cmd.ShowStreams;
import jenkins.model.Jenkins;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.lang.StringUtils;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by josp on 16/08/16.
 */
public class AccurevPromoteListener implements MqttCallback {
    private static final Logger LOGGER = Logger.getLogger(AccurevPromoteListener.class.getName());
    private final AccurevServer server;
    private final HashSet<AccurevPromoteTrigger> triggers = new HashSet<>();

    public AccurevPromoteListener(AccurevServer server) {
        this.server = server;
        setupConnection();
    }

    public void addTrigger(AccurevPromoteTrigger t) {
        triggers.add(t);
    }

    public void removeTrigger(AccurevPromoteTrigger t) {
        triggers.remove(t);
    }

    public HashSet<AccurevPromoteTrigger> getTriggers() {
        return triggers;
    }

    public void connectionLost(Throwable throwable) {
        LOGGER.log(Level.WARNING, "Connection Lost", throwable);
        LOGGER.severe(throwable.getMessage());
        setupConnection();
    }

    public void messageArrived(String topic, MqttMessage message) {
        if (StringUtils.isNotBlank(message.toString())) {
            LOGGER.fine("Incoming Message: " + message.toString());
            try {
                JSONObject json = (JSONObject) JSONSerializer.toJSON(message.toString());
                String promoteAuthor = json.getString("principal");
                String promoteDepot = json.getString("depot");
                String promoteStream = json.getString("stream");
                int promoteTrans = json.getInt("transaction_num");
                Jenkins jenkins = Jenkins.getInstance();
                FilePath path = jenkins.getRootPath();
                TaskListener listener = TaskListener.NULL;
                Launcher launcher = jenkins.createLauncher(listener);
                EnvVars env = new EnvVars();

                Map<String, AccurevStream> streams = ShowStreams.getAllStreams(null, server, promoteDepot, null, env, path, listener, launcher);

                triggers.stream()
                        //Filter promote triggers based on matching depot and stream
                        .filter(t -> t.checkForChanges(promoteDepot, promoteStream, promoteTrans, streams))
                        //Schedule triggers with matching depot and stream
                        .forEach(t -> t.scheduleBuild(promoteAuthor, promoteStream));
            } catch (JSONException ex) {
                LOGGER.warning("Failed to convert to JSON: " + ex.getMessage());
            } catch (Exception ex) {
                LOGGER.severe(ex.getMessage());
            }
        }
    }

    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        LOGGER.info("Successfully sent publish");
    }

    private void setupConnection() {
        try {
            MemoryPersistence persistence = new MemoryPersistence();
            String clientId = "JenkinsAccurevPromoteClient" + System.nanoTime();
            String host = server.getHost();
            MqttAsyncClient client = new MqttAsyncClient("tcp://" + host, clientId, persistence);
            MqttConnectOptions conOpt = new MqttConnectOptions();
            client.setCallback(this);
            conOpt.setCleanSession(true);
            LOGGER.fine("Attempting to connect Mosquitto Server: " + host);
            IMqttToken conToken = client.connect(conOpt, null, null);
            conToken.waitForCompletion();
            LOGGER.fine("Connected successfully to Mosquitto Server: " + host);
            IMqttToken subToken = client.subscribe("ci/#", 0, null, null);
            subToken.waitForCompletion();
            LOGGER.fine("Subscribed successfully to CI/# Topic");
        } catch (MqttException e) {
            LOGGER.warning("MQTT Connection failed: " + e.getMessage());
        }
    }
}
