package com.huang.pdf_ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 客户端连接端点，例如 ws://localhost:8080/ws
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*")
                .withSockJS();  // 支持浏览器降级
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 服务端推送消息的前缀（客户端订阅）
        registry.enableSimpleBroker("/topic");
    }
}