package com.zeronorth.chatapp.controller;

import com.zeronorth.chatapp.dto.MessageRequest;
import com.zeronorth.chatapp.model.Message;
import com.zeronorth.chatapp.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(
            ChatService chatService,
            SimpMessagingTemplate messagingTemplate) {

        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/api/users")
    public ResponseEntity<?> getUsers() {

        return ResponseEntity.ok(
                chatService.getUsers()
        );
    }

    @GetMapping("/api/conversations")
    public ResponseEntity<?> getConversations(
            Authentication authentication) {

        return ResponseEntity.ok(
                chatService.getConversations(
                        authentication.getName()
                )
        );
    }

    @PostMapping("/api/conversations")
    public ResponseEntity<?> createConversation(
            Authentication authentication,
            @RequestBody Map<String, Object> request) {

        try {

            String type =
                    String.valueOf(request.get("type"));

            if ("PRIVATE".equalsIgnoreCase(type)) {

                String userId =
                        String.valueOf(request.get("userId"));

                return ResponseEntity.ok(
                        chatService.createPrivateConversation(
                                authentication.getName(),
                                userId
                        )
                );
            }

            if ("GROUP".equalsIgnoreCase(type)) {

                String name =
                        String.valueOf(request.get("name"));

                @SuppressWarnings("unchecked")
                List<String> members =
                        (List<String>) request.get("members");

                return ResponseEntity.ok(
                        chatService.createGroup(
                                authentication.getName(),
                                name,
                                members
                        )
                );
            }

            throw new IllegalArgumentException(
                    "Invalid conversation type"
            );

        } catch (Exception e) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            e.getMessage()
                    ));
        }
    }

    @GetMapping(
            "/api/conversations/{conversationId}/messages"
    )
    public ResponseEntity<?> getMessages(
            Authentication authentication,
            @PathVariable String conversationId) {

        try {

            return ResponseEntity.ok(
                    chatService.getMessages(
                            authentication.getName(),
                            conversationId
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            e.getMessage()
                    ));
        }
    }

    @MessageMapping("/chat")
    public void sendMessage(
            MessageRequest request,
            Authentication authentication) {

        System.out.println("Received WebSocket message");

        if (authentication == null) {
            System.out.println("WebSocket authentication is NULL");
            return;
        }

        System.out.println(
                "WebSocket user: " + authentication.getName()
        );

        System.out.println(
                "Conversation: " + request.getConversationId()
        );

        System.out.println(
                "Content: " + request.getContent()
        );

        Message message = chatService.saveMessage(
                authentication.getName(),
                request.getConversationId(),
                request.getContent()
        );

        System.out.println(
                "Message saved: " + message.getId()
        );

        messagingTemplate.convertAndSend(
                "/topic/conversations/"
                        + request.getConversationId(),
                message
        );

        System.out.println("Message broadcasted");
    }
}