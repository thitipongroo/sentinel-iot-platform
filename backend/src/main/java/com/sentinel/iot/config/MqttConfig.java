package com.sentinel.iot.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
public class MqttConfig {

    @Value("${mqtt.broker}")
    private String brokerUrl;

    @Value("${mqtt.client-id:sentinel-backend}")
    private String clientId;

    @Value("${INSTANCE_ID:${HOSTNAME:}}")
    private String instanceId;

    @Value("${mqtt.share-group:}")
    private String shareGroup;

    @Value("${mqtt.topic:factory/telemetry}")
    private String topic;

    @Value("${mqtt.dlq-topic:factory/telemetry/dlq}")
    private String dlqTopic;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @Value("${mqtt.auto-startup:true}")
    private boolean autoStartup;

    private String resolveInstanceSuffix() {
        if (instanceId != null && !instanceId.isBlank()) {
            return "-" + instanceId.replaceAll("[^a-zA-Z0-9_-]", "-");
        }
        return "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private String[] resolveInboundTopics() {
        String[] baseTopics = new String[]{
                topic,
                "tenants/+/telemetry",
                "tenants/+/devices/+/telemetry",
                "tenants/+/devices/+/shadow/update",
                "tenants/+/devices/+/shadow/get"
        };
        if (shareGroup != null && !shareGroup.isBlank()) {
            String[] sharedTopics = new String[baseTopics.length];
            for (int i = 0; i < baseTopics.length; i++) {
                sharedTopics[i] = "$share/" + shareGroup + "/" + baseTopics[i];
            }
            return sharedTopics;
        }
        return baseTopics;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        if (username != null && !username.isBlank()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInbound() {
        String inboundClientId = clientId + resolveInstanceSuffix() + "-inbound";
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(inboundClientId, mqttClientFactory(),
                        resolveInboundTopics());
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        adapter.setAutoStartup(autoStartup);
        return adapter;
    }

    // ── Outbound MQTT publisher channel ─────────────────────────────────────

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel", autoStartup = "${mqtt.auto-startup:true}")
    public MessageHandler mqttOutbound() {
        String pubClientId = clientId + resolveInstanceSuffix() + "-pub";
        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler(pubClientId, mqttClientFactory());
        handler.setAsync(true);
        handler.setDefaultQos(1);
        return handler;
    }

    // ── Dead Letter Queue outbound ──────────────────────────────────────────

    @Bean
    public MessageChannel mqttDlqChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttDlqChannel", autoStartup = "${mqtt.auto-startup:true}")
    public MessageHandler mqttDlqOutbound() {
        String dlqClientId = clientId + resolveInstanceSuffix() + "-dlq-pub";
        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler(dlqClientId, mqttClientFactory());
        handler.setAsync(true);
        handler.setDefaultTopic(dlqTopic);
        handler.setDefaultQos(0);
        return handler;
    }
}
