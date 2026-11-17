import org.eclipse.paho.client.mqttv3.*;

public class MqttPublisher {
    public static void main(String[] args) throws Exception {
        MqttClient client = new MqttClient("tcp://47.108.58.107:1883", "test-reply-" + System.currentTimeMillis());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setUserName(System.getenv().getOrDefault("MQTT_USERNAME", ""));
        options.setPassword(System.getenv().getOrDefault("MQTT_PASSWORD", "").toCharArray());
        client.connect(options);
        System.out.println("MQTT connected");

        // Simulate device reply to DEFENSE_ON command
        String topic = "human/device/human_001/reply";
        String payload = "{\"command\":\"DEFENSE_ON\",\"result\":true,\"status\":\"SUCCESS\",\"defenseStatus\":1}";

        MqttMessage msg = new MqttMessage(payload.getBytes());
        msg.setQos(0);
        client.publish(topic, msg);
        System.out.println("Sent reply -> " + topic);
        System.out.println("Body -> " + payload);

        client.disconnect();
        client.close();
        System.out.println("Done");
    }
}
