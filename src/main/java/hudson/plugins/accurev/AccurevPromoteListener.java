package hudson.plugins.accurev;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.HashSet;
import java.util.logging.Logger;

/**
 * Created by josp on 16/08/16.
 */
public class AccurevPromoteListener implements MqttCallback {
    private static final Logger LOGGER = Logger.getLogger(AccurevPromoteListener.class.getName());
    private MqttAsyncClient client;
    private MqttConnectOptions conOpt;
    private String host;
    private HashSet<AccurevPromoteTrigger> triggers = new HashSet<>();

    public AccurevPromoteListener(String host) {
        this.host = host;
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
        LOGGER.warning("Connection Lost");
        setupConnection();
    }

    public void messageArrived(String topic, MqttMessage message) {
        LOGGER.info("Incoming Message: " + message.toString());
        try {
            JSONObject json = (JSONObject) JSONSerializer.toJSON(message.toString());
            String promoteAuthor = json.getString("principal");
            String promoteDepot = json.getString("depot");
            String promoteStream = json.getString("stream");

            for (AccurevPromoteTrigger t : triggers) {
                if (t.checkForChanges(promoteDepot, promoteStream)) {
                    t.scheduleBuild(promoteAuthor, promoteStream);
                }
            }
        } catch (JSONException ex) {
            LOGGER.info("Failed to convert to JSON: " + ex.getMessage());
        } catch (Exception ex) {
            LOGGER.severe(ex.getMessage());
        }
    }

    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        LOGGER.info("Successfully sent publish");
    }

    private void setupConnection() {
        try {
            MemoryPersistence persistence = new MemoryPersistence();
            client = new MqttAsyncClient("tcp://" + host, "AccurevJenkinsClient", persistence);
            conOpt = new MqttConnectOptions();
            client.setCallback(this);
            conOpt.setCleanSession(true);
            LOGGER.info("Attempting to connect Mosquitto Server: " + host);
            IMqttToken conToken = client.connect(conOpt, null, null);
            conToken.waitForCompletion();
            LOGGER.info("Connected successfully to Mosquitto Server: " + host);
            IMqttToken subToken = client.subscribe("ci/build", 0, null, null);
            subToken.waitForCompletion();
            LOGGER.info("Subscribed successfully to CI/Build Topic");
        } catch (MqttException e) {
            LOGGER.warning("Connection failed: " + e.getMessage());
        }
    }
}
