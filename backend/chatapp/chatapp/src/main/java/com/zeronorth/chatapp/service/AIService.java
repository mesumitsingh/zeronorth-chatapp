package com.zeronorth.chatapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeronorth.chatapp.model.Message;
import com.zeronorth.chatapp.model.User;
import com.zeronorth.chatapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class AIService {

        private final ChatService chatService;
        private final UserRepository userRepository;
        private final RestClient restClient;
        private final ObjectMapper objectMapper;

        @Value("${gemini.api-key}")
        private String apiKey;

        @Value("${gemini.model}")
        private String model;

        public AIService(
                        ChatService chatService,
                        UserRepository userRepository) {

                this.chatService = chatService;
                this.userRepository = userRepository;

                this.restClient = RestClient.builder()
                                .baseUrl("https://generativelanguage.googleapis.com")
                                .build();

                this.objectMapper = new ObjectMapper();
        }

        public String chat(String message) {

                if (message == null || message.isBlank()) {
                        throw new IllegalArgumentException(
                                        "Message is required");
                }

                return callGemini(message);
        }

        public String summarize(
                        String email,
                        String conversationId) {

                List<Message> messages = chatService.getRecentMessages(
                                email,
                                conversationId);

                if (messages.isEmpty()) {
                        return "There are no messages to summarize.";
                }

                StringBuilder conversationText = new StringBuilder();

                for (Message message : messages) {

                        User sender = userRepository
                                        .findById(message.getSenderId())
                                        .orElse(null);

                        String senderName;

                        if (sender != null
                                        && sender.getName() != null
                                        && !sender.getName().isBlank()) {

                                senderName = sender.getName();

                        } else {

                                senderName = "Unknown User";
                        }

                        conversationText
                                        .append(senderName)
                                        .append(": ")
                                        .append(message.getContent())
                                        .append("\n");
                }

                String prompt = "Summarize the following chat conversation "
                                + "in a concise and easy-to-understand way. "
                                + "Mention the main topics and important points. "
                                + "Use the participant names provided in the conversation "
                                + "instead of IDs. Do not mention internal user IDs.\n\n"
                                + conversationText;

                return callGemini(prompt);
        }

        private String callGemini(String prompt) {

                try {

                        String requestBody = """
                                        {
                                          "contents": [
                                            {
                                              "parts": [
                                                {
                                                  "text": %s
                                                }
                                              ]
                                            }
                                          ]
                                        }
                                        """.formatted(
                                        objectMapper.writeValueAsString(prompt));

                        String response = restClient.post()
                                        .uri(uriBuilder -> uriBuilder
                                                        .path(
                                                                        "/v1beta/models/{model}:generateContent")
                                                        .queryParam(
                                                                        "key",
                                                                        apiKey)
                                                        .build(model))
                                        .header(
                                                        "Content-Type",
                                                        "application/json")
                                        .body(requestBody)
                                        .retrieve()
                                        .body(String.class);

                        JsonNode root = objectMapper.readTree(response);

                        JsonNode text = root.path("candidates")
                                        .path(0)
                                        .path("content")
                                        .path("parts")
                                        .path(0)
                                        .path("text");

                        if (text.isMissingNode()) {
                                return "Gemini did not return a response.";
                        }

                        return text.asText();

                } catch (Exception e) {

                        return "AI service error: " + e.getMessage();
                }
        }
}