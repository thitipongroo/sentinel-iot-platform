package com.sentinel.iot.config;

import com.sentinel.iot.websocket.JwtWebSocketHandshakeInterceptor;
import com.sentinel.iot.websocket.TelemetryWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TelemetryWebSocketHandler handler;
    private final JwtWebSocketHandshakeInterceptor handshakeInterceptor;

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @SuppressWarnings("null")
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/telemetry")
                .setAllowedOrigins(allowedOrigins.split(","))
                .addInterceptors(handshakeInterceptor);
    }
}
