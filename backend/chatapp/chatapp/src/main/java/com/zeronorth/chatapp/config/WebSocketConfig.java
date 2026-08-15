package com.zeronorth.chatapp.config;

import com.zeronorth.chatapp.model.User;
import com.zeronorth.chatapp.repository.UserRepository;
import com.zeronorth.chatapp.security.JwtService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;

import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

        private final JwtService jwtService;
        private final UserRepository userRepository;
        private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

        private final Map<String, Authentication> sessions = new ConcurrentHashMap<>();

        public WebSocketConfig(
                        JwtService jwtService,
                        UserRepository userRepository,
                        ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider) {

                this.jwtService = jwtService;
                this.userRepository = userRepository;
                this.messagingTemplateProvider = messagingTemplateProvider;
        }

        private SimpMessagingTemplate messagingTemplate() {
                return messagingTemplateProvider.getObject();
        }

        @Override
        public void configureMessageBroker(
                        MessageBrokerRegistry registry) {

                registry.enableSimpleBroker("/topic");
                registry.setApplicationDestinationPrefixes("/app");
        }

        @Override
        public void registerStompEndpoints(
                        StompEndpointRegistry registry) {

                registry
                                .addEndpoint("/ws")
                                .setAllowedOriginPatterns("*")
                                .withSockJS();
        }

        @Override
        public void configureClientInboundChannel(
                        ChannelRegistration registration) {

                registration.interceptors(new ChannelInterceptor() {

                        @Override
                        public Message<?> preSend(
                                        Message<?> message,
                                        MessageChannel channel) {

                                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                                                message,
                                                StompHeaderAccessor.class);

                                if (accessor == null) {
                                        return message;
                                }

                                StompCommand command = accessor.getCommand();

                                String sessionId = accessor.getSessionId();

                                // USER CONNECTS

                                if (StompCommand.CONNECT.equals(command)) {

                                        String authorization = accessor.getFirstNativeHeader(
                                                        "Authorization");

                                        if (authorization != null
                                                        && authorization.startsWith("Bearer ")) {

                                                String token = authorization.substring(7);

                                                if (jwtService.isValid(token)) {

                                                        String email = jwtService.extractEmail(token);

                                                        User user = userRepository
                                                                        .findByEmail(email)
                                                                        .orElse(null);

                                                        if (user != null) {

                                                                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                                                                email,
                                                                                null,
                                                                                List.of(
                                                                                                new SimpleGrantedAuthority(
                                                                                                                "USER")));

                                                                sessions.put(
                                                                                sessionId,
                                                                                authentication);

                                                                accessor.setUser(authentication);

                                                                // Mark user online
                                                                user.setOnline(true);
                                                                userRepository.save(user);

                                                                System.out.println(
                                                                                "WebSocket authenticated: "
                                                                                                + email);

                                                                // Broadcast online status
                                                                messagingTemplate()
                                                                                .convertAndSend(
                                                                                                "/topic/presence",
                                                                                                Map.of(
                                                                                                                "email",
                                                                                                                email,
                                                                                                                "online",
                                                                                                                true));

                                                                // Send currently online users
                                                                List<User> users = userRepository.findAll();

                                                                for (User onlineUser : users) {

                                                                        if (onlineUser.isOnline()) {

                                                                                messagingTemplate()
                                                                                                .convertAndSend(
                                                                                                                "/topic/presence",
                                                                                                                Map.of(
                                                                                                                                "email",
                                                                                                                                onlineUser.getEmail(),
                                                                                                                                "online",
                                                                                                                                true));
                                                                        }
                                                                }
                                                        }
                                                }
                                        }
                                }

                                // RESTORE AUTHENTICATION

                                else if (sessionId != null
                                                && sessions.containsKey(sessionId)) {

                                        Authentication authentication = sessions.get(sessionId);

                                        accessor.setUser(authentication);

                                        System.out.println(
                                                        "WebSocket user restored: "
                                                                        + authentication.getName());
                                }

                                // USER DISCONNECTS

                                if (StompCommand.DISCONNECT.equals(command)) {

                                        Authentication authentication = sessions.remove(sessionId);

                                        if (authentication != null) {

                                                String email = authentication.getName();

                                                User user = userRepository
                                                                .findByEmail(email)
                                                                .orElse(null);

                                                if (user != null) {

                                                        user.setOnline(false);

                                                        user.setLastSeen(
                                                                        LocalDateTime.now());

                                                        userRepository.save(user);

                                                        // Broadcast offline status
                                                        messagingTemplate()
                                                                        .convertAndSend(
                                                                                        "/topic/presence",
                                                                                        Map.of(
                                                                                                        "email",
                                                                                                        email,
                                                                                                        "online",
                                                                                                        false));
                                                }

                                                System.out.println(
                                                                "WebSocket disconnected: "
                                                                                + email);
                                        }
                                }

                                return message;
                        }
                });
        }
}