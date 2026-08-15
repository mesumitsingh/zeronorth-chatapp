package com.zeronorth.chatapp.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "conversations")
public class Conversation {

    @Id
    private String id;

    private String type;

    private String name;

    private List<String> members = new ArrayList<>();

    private String createdBy;

    private LocalDateTime createdAt;
}