package hudson.plugins.accurev;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by josp on 16/08/16.
 */
public class AccurevPromoteListener implements MqttCallback {
    private static final Logger LOGGER = Logger.getLogger(AccurevPromoteListener.class.getName());
    private final String host;
    private final HashSet<AccurevPromoteTrigger> triggers = new HashSet<>();
    private MqttAsyncClient client;
    private MqttConnectOptions conOpt;

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
        LOGGER.log(Level.WARNING, "Connection Lost", throwable);
        LOGGER.severe(throwable.getMessage());
        setupConnection();
    }

    public void messageArrived(String topic, MqttMessage message) {
        LOGGER.fine("Incoming Message: " + message.toString());
        try {
            JSONObject json = (JSONObject) JSONSerializer.toJSON(message.toString());
            String promoteAuthor = json.getString("principal");
            String promoteDepot = json.getString("depot");
            String promoteStream = json.getString("stream");

            triggers.stream()
                    //Filter promote triggers based on matching depot and stream
                    .filter(t -> t.checkForChanges(promoteDepot, promoteStream))
                    //Schedule triggers with matching depot and stream
                    .forEach(t -> t.scheduleBuild(promoteAuthor, promoteStream));
        } catch (JSONException ex) {
            LOGGER.warning("Failed to convert to JSON: " + ex.getMessage());
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
            LOGGER.fine("Attempting to connect Mosquitto Server: " + host);
            IMqttToken conToken = client.connect(conOpt, null, null);
            conToken.waitForCompletion();
            LOGGER.fine("Connected successfully to Mosquitto Server: " + host);
            IMqttToken subToken = client.subscribe("ci/build", 0, null, null);
            subToken.waitForCompletion();
            LOGGER.fine("Subscribed successfully to CI/Build Topic");
        } catch (MqttException e) {
            LOGGER.warning("MQTT Connection failed: " + e.getMessage());
        }
    }
}
