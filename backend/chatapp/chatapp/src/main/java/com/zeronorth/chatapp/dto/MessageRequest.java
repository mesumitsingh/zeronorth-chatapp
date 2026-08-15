package com.zeronorth.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MessageRequest {

    private String conversationId;

    @NotBlank
    private String content;
}