package com.fitouts.communications.config;

import java.security.Principal;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.fitouts.auth.security.AuthPrincipal;
import com.fitouts.shared.error.ForbiddenException;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())
                        || StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                        || StompCommand.SEND.equals(accessor.getCommand())) {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth == null || !auth.isAuthenticated()
                            || !(auth.getPrincipal() instanceof AuthPrincipal)) {
                        // Also accept principal already attached to the STOMP session
                        Principal user = accessor.getUser();
                        if (user instanceof Authentication stompAuth
                                && stompAuth.getPrincipal() instanceof AuthPrincipal) {
                            return message;
                        }
                        throw new ForbiddenException("WebSocket authentication required");
                    }
                    if (accessor.getUser() == null) {
                        accessor.setUser(new UsernamePasswordAuthenticationToken(
                                auth.getPrincipal(), null, auth.getAuthorities()));
                    }
                }
                return message;
            }
        });
    }
}
