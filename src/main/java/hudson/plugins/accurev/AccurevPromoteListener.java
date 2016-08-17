package hudson.plugins.accurev;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.eclipse.paho.client.mqttv3.*;

import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by josp on 16/08/16.
 */
public class AccurevPromoteListener implements MqttCallback {
    private static final Logger LOGGER = Logger.getLogger(AccurevPromoteListener.class.getName());
    private MqttAsyncClient client;
    private MqttConnectOptions conOpt;
    private HashSet<AccurevPromoteTrigger> triggers = new HashSet<>();

    public AccurevPromoteListener (String host) {
        try {
            client = new MqttAsyncClient("tcp://"+host, "AccurevJenkinsClient");
            conOpt = new MqttConnectOptions();
            client.setCallback(this);
            conOpt.setCleanSession(true);
            IMqttToken conToken = client.connect(conOpt,null, null);
            conToken.waitForCompletion();
            LOGGER.log(Level.INFO, "Connected successfully to Mosquitto Server");
            IMqttToken subToken = client.subscribe("ci/build", 0, null, null);
            subToken.waitForCompletion();
            LOGGER.log(Level.INFO, "Subscribed successfully to CI/Build Topic");
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void addTrigger(AccurevPromoteTrigger t) { triggers.add(t); }

    public void removeTrigger(AccurevPromoteTrigger t) { triggers.remove(t); }

    public HashSet<AccurevPromoteTrigger> getTriggers() { return triggers; }

    public void connectionLost(Throwable throwable) {
        LOGGER.log(Level.WARNING, "Connection Lost");
    }

    public void messageArrived(String topic, MqttMessage message) throws Exception {
        LOGGER.log(Level.INFO, "Incoming Message: "+ message.toString());
        JSONObject json = (JSONObject) JSONSerializer.toJSON(message.toString());
        String promoteAuthor = json.getString("principal");
        String promoteDepot = json.getString("depot");
        String promoteStream = json.getString("stream");

        for (AccurevPromoteTrigger t : triggers) {
            LOGGER.info("Trigger project name: "+t.getProjectName());
            if (t.getStream().equals(promoteStream)) {
                LOGGER.info("Trigger stream: "+t.getStream() + " incoming stream: "+json.getString("stream"));
                t.scheduleBuild(promoteAuthor, promoteStream);
            } else if (t.getDepot().equals(promoteDepot)) {
                if (t.checkForParentStream(promoteStream)) {
                    t.scheduleBuild(promoteAuthor, promoteStream);
                }
            }
        }
    }

    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        LOGGER.log(Level.INFO, "Successfully sent publish");
    }
}
