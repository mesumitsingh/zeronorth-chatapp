package com.zeronorth.chatapp.repository;

import com.zeronorth.chatapp.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {

    List<Message> findByConversationIdOrderByTimestampAsc(String conversationId);

    List<Message> findTop50ByConversationIdOrderByTimestampDesc(String conversationId);
}