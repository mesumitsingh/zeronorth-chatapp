# Real-Time Chat

> **Task for ZeroNorth**  
> A full-stack, enterprise grade real time messaging application with 1 on 1 chat, groups, AI assistant and summarization.

---

## Table of Contents
1. [Project Overview](#-project-overview)
2. [Key Features](#-key-features)
3. [Architecture & System Design](#-architecture--system-design)
4. [Deep Dive: WebSockets & STOMP Protocol](#-deep-dive-websockets--stomp-protocol)
5. [AI Integration (Google Gemini)](#-ai-integration-google-gemini)
6. [Data Models & Schema](#-data-models--schema)
7. [Security & Authentication Flow](#-security--authentication-flow)
8. [API Reference](#-api-reference)
9. [Project Structure](#-project-structure)
10. [Setup & Running Locally](#-setup--running-locally)
11. [Interview Talking Points & Presentation Guide](#-interview-talking-points--presentation-guide)

---

## Project Overview

This project is built to demo the real-time web application architecture. It solves the challenge of instant communication, authenticated real time, and AI assistance inside a clean single page interface.

### Tech Stack
- **Backend**: Java 17, Spring Boot 3.3.5 (Web, Data MongoDB, WebSocket / STOMP, Security, Validation)
- **Database**: MongoDB (NoSQL Document Store)
- **Security**: Spring Security 6, JWT, BCrypt Password Hashing
- **Real-Time Messaging**: Spring WebSocket, STOMP Messaging Protocol, SockJS
- **AI Engine**: Google Gemini API (`gemini-2.5-flash`)
- **Frontend**: JavaScript, Modern CSS3, HTML5, `@stomp/stompjs`

---

## Features

- **Secure Authentication**: User registration and login powered by BCrypt password encryption and signed JWT access tokens.
- **1-on-1 Direct Messaging**: Private messaging.
- **Group Chats**: Multiple user group chat.
- **Live Online/Offline**: Real time online status via WebSocket connection lifecycle hooks.
- **AI Chat & Summarization**:
  - AI assistant in the sidebar.
  - One click summarizer that parses chat history and generates points using Gemini.

---

## Architecture & System Design

```
+-----------------------------------------------------------------------------------+
|                                  CLIENT (BROWSER)                                 |
|   Vanilla JS (app.js)  |  SockJS + STOMP Client  |  Modern UI (index.html / CSS)  |
+------------------------------------------+----------------------------------------+
                                           |
                   +-----------------------+-----------------------+
                   | HTTP REST (JSON)                              | WebSocket / STOMP
                   v                                               v
+------------------------------------------+----------------------------------------+
|                                SPRING BOOT BACKEND                                |
|                                                                                   |
|  [AuthController]      [ChatController]      [AIController]     [WebSocketConfig] |
|   - /api/auth/*         - /api/users          - /api/ai/chat     - STOMP Interc.  |
|                         - /api/conversations  - /api/ai/summari. - /ws (SockJS)   |
|                         - @MessageMapping("/chat")                                |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  |                            SECURITY & MIDDLEWARE                            |  |
|  |   - OncePerRequestFilter (REST JWT Auth)                                    |  |
|  |   - ChannelInterceptor (STOMP CONNECT / DISCONNECT Auth & Presence)         |  |
|  +-----------------------------------------------------------------------------+  |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  |                               SERVICE LAYER                                 |  |
|  |   - AuthService (BCrypt, Token Generation)                                  |  |
|  |   - ChatService (Conversations, Messages, Membership Checks)                |  |
|  |   - AIService (Prompt Engineering, RestClient to Gemini API)                |  |
|  |   - JwtService (HMAC-SHA256 Token Parser & Validator)                       |  |
|  +-----------------------------------------------------------------------------+  |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  |                            DATA ACCESS (REPOSITORIES)                       |  |
|  |   - UserRepository       - ConversationRepository     - MessageRepository   |  |
|  +-----------------------------------------------------------------------------+  |
+----------------------+------------------------------------+-----------------------+
                       |                                    |
                       v                                    v
            +---------------------+              +--------------------+
            |   MongoDB Database  |              | Google Gemini API  |
            |  (Users/Chats/Msgs) |              | (gemini-2.5-flash) |
            +---------------------+              +--------------------+
```

---

## WebSockets & STOMP Protocol

### 1. Why STOMP over Raw WebSockets?
- **Raw WebSockets** provide an unopinionated TCP-like duplex connection over a single TCP socket. However, they lack higher-level message patterns (publish/subscribe, headers, destination routing).
- **STOMP (Simple Text Oriented Messaging Protocol)** adds a standardized frame format (`CONNECT`, `SUBSCRIBE`, `SEND`, `MESSAGE`, `DISCONNECT`) on top of WebSocket.
- This allows Spring to act as a **Message Broker**, cleanly routing messages to specific destination topics (`/topic/...`) and application handlers (`/app/...`).

### 2. Connection Handshake & Fallback (`WebSocketConfig.java`)
```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS(); // Fallback mechanism for restricted networks
}

@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");          // Outbound broker prefix
    registry.setApplicationDestinationPrefixes("/app"); // Inbound app prefix
}
```

### 3. Real-Time Authentication Lifecycle & Presence Management
Unlike standard HTTP where every request passes through an HTTP filter, WebSocket connections stay open indefinitely. Authentication is handled at the **STOMP frame level** using a `ChannelInterceptor`:

```
Client                             Server (WebSocketConfig ChannelInterceptor)
  |                                                  |
  |--- STOMP CONNECT [Auth: Bearer <JWT>] ---------->|
  |                                                  |-- 1. Extract & Validate JWT
  |                                                  |-- 2. Bind Principal to StompHeaderAccessor
  |                                                  |-- 3. Cache session in ConcurrentHashMap
  |                                                  |-- 4. Mark user online = true in DB
  |<-- STOMP CONNECTED ------------------------------|-- 5. Broadcast { email, online: true }
  |                                                  |      to destination "/topic/presence"
  |                                                  |
  |--- STOMP SUBSCRIBE [/topic/conversations/{id}] ->|
  |                                                  |
  |--- STOMP SEND [/app/chat] { content: "Hi" } ---->|-- Handled by @MessageMapping("/chat")
  |                                                  |-- Saves message to MongoDB
  |<-- STOMP MESSAGE [/topic/conversations/{id}] ----|-- Broadcasts to conversation subscribers
  |                                                  |
  |--- STOMP DISCONNECT (or TCP Drop) -------------->|-- 1. Invalidate session map
  |                                                  |-- 2. Mark user online = false & update lastSeen
  |                                                  |-- 3. Broadcast { email, online: false }
  |                                                  |      to destination "/topic/presence"
```

### 4. Message Flow
1. **Send Message**: Frontend executes `stompClient.publish({ destination: "/app/chat", body: JSON.stringify({ conversationId, content }) })`.
2. **Controller Processing**: Spring routes this to `ChatController.sendMessage()` via `@MessageMapping("/chat")`.
3. **Validation & Storage**: The authenticated user is extracted from `Authentication`, verified for conversation membership, and the message is saved to MongoDB.
4. **Broadcast**: `messagingTemplate.convertAndSend("/topic/conversations/" + id, message)` broadcasts the persisted message instantly to all participants subscribed to that conversation channel.

---

## AI Integration (Google Gemini)

The backend integrates directly with the **Google Generative Language API** using Spring's modern, non-blocking `RestClient`:

1. **AI Chat Assistance (`/api/ai/chat`)**:
   - Accepts ad-hoc user queries and streams prompt payloads formatted for Gemini.
2. **Smart Conversation Summarization (`/api/ai/summarize/{conversationId}`)**:
   - Fetches the last 50 messages of the active chat from MongoDB.
   - Converts raw sender IDs into actual participant names.
   - Formulates a structured prompt instructing Gemini to outline main topics, decisions, and action items concisely without exposing internal database identifiers.

---

## Data Models & Schema

### `User` Collection (`users`)
| Field | Type | Description |
|---|---|---|
| `id` | `String` (ObjectId) | Unique User Identifier |
| `name` | `String` | User Display Name |
| `email` | `String` | Unique Email (Indexed for login) |
| `password` | `String` | BCrypt Hashed Password |
| `online` | `boolean` | Current Live Presence Status |
| `lastSeen` | `LocalDateTime` | Timestamp of last disconnect |

### `Conversation` Collection (`conversations`)
| Field | Type | Description |
|---|---|---|
| `id` | `String` (ObjectId) | Conversation Identifier |
| `type` | `String` | `"PRIVATE"` or `"GROUP"` |
| `name` | `String` | Group Name or formatted peer name |
| `members` | `List<String>` | List of User IDs in conversation |
| `createdBy` | `String` | Creator User ID |
| `createdAt` | `LocalDateTime` | Creation Timestamp |

### `Message` Collection (`messages`)
| Field | Type | Description |
|---|---|---|
| `id` | `String` (ObjectId) | Message Identifier |
| `conversationId` | `String` | Target Conversation Reference |
| `senderId` | `String` | Sender User ID |
| `content` | `String` | Text Content |
| `timestamp` | `LocalDateTime` | Sent Timestamp |

---

## Security & Authentication Flow

1. **Stateless JWT**: REST endpoints use `OncePerRequestFilter` to inspect `Authorization: Bearer <token>`.
2. **WebSocket Interception**: WebSocket handshakes and STOMP frames carry the JWT in native headers, authenticating connections before any messages are processed.
3. **Password Security**: Passwords are encrypted with `BCryptPasswordEncoder` with salted hashes.
4. **Authorization**: REST and WebSocket handlers verify that the requesting user is a legitimate member of the target conversation before retrieving or sending messages.

---

## API Reference

### Authentication
- `POST /api/auth/register` — Register a new account (`{ name, email, password }`)
- `POST /api/auth/login` — Login and receive JWT (`{ email, password }`)

### Chat & Users
- `GET /api/users` — List all registered users and their presence status.
- `GET /api/conversations` — Retrieve all conversations for the authenticated user.
- `POST /api/conversations` — Create private or group conversation (`{ type: "PRIVATE"|"GROUP", userId?, name?, members? }`).
- `GET /api/conversations/{id}/messages` — Fetch message history for a conversation.

### AI Assistant
- `POST /api/ai/chat` — Ask general queries to Gemini (`{ content: "..." }`).
- `POST /api/ai/summarize/{conversationId}` — Generate an AI summary of recent chat history.

### WebSocket Topics
- `SUBSCRIBE /topic/presence` — Real-time user online/offline status updates.
- `SUBSCRIBE /topic/conversations/{conversationId}` — Real-time incoming messages for a room.
- `SEND /app/chat` — Outbound message payload (`{ conversationId, content }`).

---

## Project Structure

```
.
├── backend/
│   └── chatapp/
│       └── chatapp/
│           ├── pom.xml
│           ├── Dockerfile
│           └── src/main/java/com/zeronorth/chatapp/
│               ├── ChatAppApplication.java
│               ├── config/
│               │   ├── SecurityConfig.java       # CORS, JWT REST Filter, PasswordEncoder
│               │   └── WebSocketConfig.java      # STOMP broker, SockJS, ChannelInterceptor
│               ├── controller/
│               │   ├── AuthController.java       # Register & Login endpoints
│               │   ├── ChatController.java       # Conversations, messages & @MessageMapping
│               │   └── AIController.java         # Gemini chat & summarize endpoints
│               ├── dto/                          # Request & response data transfer objects
│               ├── exception/
│               │   └── GlobalExceptionHandler.java # ProblemDetail (RFC 7807) error handler
│               ├── model/                        # MongoDB Entities (User, Conversation, Message)
│               ├── repository/                   # Spring Data Mongo Repositories
│               ├── security/
│               │   └── JwtService.java           # HMAC JWT generator & validator
│               └── service/
│                   ├── AuthService.java          # User onboarding & auth logic
│                   ├── ChatService.java          # Room & message management
│                   └── AIService.java            # Gemini RestClient integration
└── frontend/
    ├── index.html                                # Semantic HTML5 single-page layout
    ├── style.css                                 # Modern CSS styling & responsive layout
    └── app.js                                    # SPA state, SockJS/STOMP & DOM rendering
```

---

## Setup & Running Locally

### Requirements
- Java 17+
- Maven 3.8+
- MongoDB instance (local or MongoDB Atlas)
- Google Gemini API Key

### 1. Backend 
Create an `.env` file or export environment variables in `backend/chatapp/chatapp/`:
```env
PORT=8080
MONGODB_URI=
MONGODB_DATABASE=
JWT_SECRET=
GEMINI_API_KEY=
```

Run the backend:
```bash
cd backend/chatapp/chatapp
./mvnw spring-boot:run
```

### 2. Frontend
Open `frontend/index.html` via Live Server.

---