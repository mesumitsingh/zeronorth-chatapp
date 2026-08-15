package com.zeronorth.chatapp.controller;

import com.zeronorth.chatapp.dto.MessageRequest;
import com.zeronorth.chatapp.service.AIService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final AIService aiService;

    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            @Valid @RequestBody MessageRequest request) {

        try {

            String response =
                    aiService.chat(
                            request.getContent()
                    );

            return ResponseEntity.ok(
                    Map.of(
                            "response",
                            response
                    )
            );

        } catch (Exception e) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            e.getMessage()
                    ));
        }
    }

    @PostMapping(
            "/summarize/{conversationId}"
    )
    public ResponseEntity<?> summarize(
            Authentication authentication,
            @PathVariable String conversationId) {

        try {

            String summary =
                    aiService.summarize(
                            authentication.getName(),
                            conversationId
                    );

            return ResponseEntity.ok(
                    Map.of(
                            "response",
                            summary
                    )
            );

        } catch (Exception e) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            e.getMessage()
                    ));
        }
    }
}