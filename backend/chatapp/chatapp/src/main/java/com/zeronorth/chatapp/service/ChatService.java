package com.zeronorth.chatapp.service;

import com.zeronorth.chatapp.model.Conversation;
import com.zeronorth.chatapp.model.Message;
import com.zeronorth.chatapp.model.User;
import com.zeronorth.chatapp.repository.ConversationRepository;
import com.zeronorth.chatapp.repository.MessageRepository;
import com.zeronorth.chatapp.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatService {

        private final UserRepository userRepository;
        private final ConversationRepository conversationRepository;
        private final MessageRepository messageRepository;

        public ChatService(
                        UserRepository userRepository,
                        ConversationRepository conversationRepository,
                        MessageRepository messageRepository) {

                this.userRepository = userRepository;
                this.conversationRepository = conversationRepository;
                this.messageRepository = messageRepository;
        }

        public List<User> getUsers() {
                return userRepository.findAll();
        }

        public List<Conversation> getConversations(String email) {

                User user = getUserByEmail(email);

                List<Conversation> conversations = conversationRepository
                                .findByMembersContaining(user.getId());

                for (Conversation conversation : conversations) {
                        formatConversationForUser(conversation, user.getId());
                }

                return conversations;
        }

        public Conversation createPrivateConversation(
                        String email,
                        String otherUserId) {

                User currentUser = getUserByEmail(email);

                User otherUser = userRepository
                                .findById(otherUserId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "User not found"));

                if (currentUser.getId().equals(otherUser.getId())) {

                        throw new IllegalArgumentException(
                                        "Cannot create conversation with yourself");
                }

                List<String> members = List.of(
                                currentUser.getId(),
                                otherUser.getId());

                List<Conversation> existing = conversationRepository
                                .findByMembersContaining(
                                                currentUser.getId());

                for (Conversation conversation : existing) {

                        if ("PRIVATE".equals(conversation.getType())
                                        && conversation.getMembers().size() == 2
                                        && conversation.getMembers()
                                                        .contains(otherUser.getId())) {

                                formatConversationForUser(conversation, currentUser.getId());
                                return conversation;
                        }
                }

                Conversation conversation = new Conversation();

                conversation.setType("PRIVATE");

                conversation.setMembers(
                                new ArrayList<>(members));

                conversation.setCreatedBy(
                                currentUser.getId());

                conversation.setCreatedAt(
                                LocalDateTime.now());

                Conversation saved = conversationRepository.save(
                                conversation);

                formatConversationForUser(saved, currentUser.getId());

                return saved;
        }

        private void formatConversationForUser(
                        Conversation conversation,
                        String currentUserId) {

                if (conversation != null && "PRIVATE".equals(conversation.getType())) {

                        for (String memberId : conversation.getMembers()) {

                                if (!memberId.equals(currentUserId)) {

                                        User otherUser = userRepository
                                                        .findById(memberId)
                                                        .orElse(null);

                                        if (otherUser != null) {

                                                conversation.setName(
                                                                otherUser.getName());
                                        }

                                        break;
                                }
                        }
                }
        }

        public Conversation createGroup(
                        String email,
                        String name,
                        List<String> memberIds) {

                User currentUser = getUserByEmail(email);

                if (name == null || name.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Group name is required");
                }

                if (memberIds == null
                                || memberIds.size() < 1) {

                        throw new IllegalArgumentException(
                                        "At least one other member is required");
                }

                List<String> members = new ArrayList<>();

                members.add(
                                currentUser.getId());

                for (String memberId : memberIds) {

                        if (!userRepository.existsById(memberId)) {

                                throw new IllegalArgumentException(
                                                "User not found: " + memberId);
                        }

                        if (!members.contains(memberId)) {

                                members.add(memberId);
                        }
                }

                if (members.size() < 2) {

                        throw new IllegalArgumentException(
                                        "Group must have at least two members");
                }

                Conversation conversation = new Conversation();

                conversation.setType("GROUP");

                conversation.setName(name);

                conversation.setMembers(members);

                conversation.setCreatedBy(
                                currentUser.getId());

                conversation.setCreatedAt(
                                LocalDateTime.now());

                return conversationRepository.save(
                                conversation);
        }

        public List<Message> getMessages(
                        String email,
                        String conversationId) {

                User user = getUserByEmail(email);

                Conversation conversation = getConversation(conversationId);

                checkMembership(
                                conversation,
                                user.getId());

                return messageRepository
                                .findByConversationIdOrderByTimestampAsc(
                                                conversationId);
        }

        public Message saveMessage(
                        String email,
                        String conversationId,
                        String content) {

                User user = getUserByEmail(email);

                Conversation conversation = getConversation(conversationId);

                checkMembership(
                                conversation,
                                user.getId());

                if (content == null
                                || content.isBlank()) {

                        throw new IllegalArgumentException(
                                        "Message cannot be empty");
                }

                Message message = new Message();

                message.setConversationId(
                                conversationId);

                message.setSenderId(
                                user.getId());

                message.setContent(
                                content);

                message.setTimestamp(
                                LocalDateTime.now());

                return messageRepository.save(
                                message);
        }

        public List<Message> getRecentMessages(
                        String email,
                        String conversationId) {

                User user = getUserByEmail(email);

                Conversation conversation = getConversation(conversationId);

                checkMembership(
                                conversation,
                                user.getId());

                List<Message> messages = messageRepository
                                .findTop50ByConversationIdOrderByTimestampDesc(
                                                conversationId);

                java.util.Collections.reverse(
                                messages);

                return messages;
        }

        private User getUserByEmail(
                        String email) {

                return userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "User not found"));
        }

        private Conversation getConversation(
                        String conversationId) {

                return conversationRepository
                                .findById(conversationId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Conversation not found"));
        }

        private void checkMembership(
                        Conversation conversation,
                        String userId) {

                if (!conversation.getMembers()
                                .contains(userId)) {

                        throw new IllegalArgumentException(
                                        "You are not a member of this conversation");
                }
        }
}