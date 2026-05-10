package com.sentinel.iot.config;

import com.sentinel.iot.websocket.WebSocketBroadcastSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Wires Redis pub/sub for cluster-wide WebSocket broadcast.
 *
 * Broadcast flow across a horizontally scaled deployment:
 *
 *   KafkaTelemetryConsumer (any instance)
 *     → WebSocketBroadcastPublisher.publish()
 *     → Redis PUBLISH ws:telemetry <payload>
 *     → [all subscribed backend instances]
 *     → WebSocketBroadcastSubscriber.onMessage()
 *     → TelemetryWebSocketHandler.broadcastLocal()
 *     → local WebSocket sessions on that instance
 *
 * The Redis pub/sub connection used here is separate from the command connection
 * used by RedisService — Spring Data Redis manages a dedicated subscriber connection.
 */
@Configuration
public class RedisWebSocketConfig {

    @Value("${ws.broadcast.channel:ws:telemetry}")
    private String broadcastChannel;

    @Bean
    public RedisMessageListenerContainer webSocketBroadcastListenerContainer(
            RedisConnectionFactory connectionFactory,
            WebSocketBroadcastSubscriber subscriber) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber, "onMessage");

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(adapter, new ChannelTopic(broadcastChannel));
        return container;
    }
}
