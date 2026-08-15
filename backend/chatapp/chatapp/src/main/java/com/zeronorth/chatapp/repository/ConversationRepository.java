package com.zeronorth.chatapp.repository;

import com.zeronorth.chatapp.model.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    List<Conversation> findByMembersContaining(String userId);
}