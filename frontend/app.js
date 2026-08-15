const API_URL = "https://zeronorth-chatapp.onrender.com";

let token = localStorage.getItem("token");
let currentUser = null;
let currentConversation = null;
let stompClient = null;
let conversationSubscription = null;

let users = [];
let conversations = [];



// Page initialization


document.addEventListener("DOMContentLoaded", () => {

    if (token) {
        showChatPage();
    } else {
        showLogin();
    }
});



// Authentication


function showLogin() {

    document
        .getElementById("loginPage")
        .classList.remove("hidden");

    document
        .getElementById("registerPage")
        .classList.add("hidden");

    document
        .getElementById("chatPage")
        .classList.add("hidden");
}


function showRegister() {

    document
        .getElementById("loginPage")
        .classList.add("hidden");

    document
        .getElementById("registerPage")
        .classList.remove("hidden");

    document
        .getElementById("chatPage")
        .classList.add("hidden");
}


async function register() {

    const name =
        document.getElementById("registerName").value;

    const email =
        document.getElementById("registerEmail").value;

    const password =
        document.getElementById("registerPassword").value;

    const error =
        document.getElementById("registerError");

    error.textContent = "";

    try {

        const response = await fetch(
            `${API_URL}/api/auth/register`,
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    name,
                    email,
                    password
                })
            }
        );

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message);
        }

        alert("Registration successful");

        showLogin();

    } catch (e) {

        error.textContent = e.message;
    }
}


async function login() {

    const email =
        document.getElementById("loginEmail").value;

    const password =
        document.getElementById("loginPassword").value;

    const error =
        document.getElementById("loginError");

    error.textContent = "";

    try {

        const response = await fetch(
            `${API_URL}/api/auth/login`,
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    email,
                    password
                })
            }
        );

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message);
        }

        token = data.token;

        localStorage.setItem(
            "token",
            token
        );

        currentUser = {
            id: data.userId,
            name: data.name,
            email: data.email
        };

        localStorage.setItem(
            "currentUser",
            JSON.stringify(currentUser)
        );

        showChatPage();

    } catch (e) {

        error.textContent = e.message;
    }
}


function logout() {

    if (stompClient) {
        stompClient.deactivate();
        stompClient = null;
    }

    localStorage.removeItem("token");
    localStorage.removeItem("currentUser");

    token = null;
    currentUser = null;
    currentConversation = null;

    showLogin();
}



// Chat page


async function showChatPage() {

    document
        .getElementById("loginPage")
        .classList.add("hidden");

    document
        .getElementById("registerPage")
        .classList.add("hidden");

    document
        .getElementById("chatPage")
        .classList.remove("hidden");

    const savedUser =
        localStorage.getItem("currentUser");

    if (savedUser) {
        currentUser = JSON.parse(savedUser);
    }

    if (!currentUser) {
        logout();
        return;
    }

    document
        .getElementById("currentUserName")
        .textContent = currentUser.name;

    await loadUsers();
    await loadConversations();

    connectWebSocket();
}



// REST helper


async function apiFetch(url, options = {}) {

    options.headers = {
        ...(options.headers || {}),
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
    };

    const response =
        await fetch(
            `${API_URL}${url}`,
            options
        );

    if (response.status === 401) {
        logout();
        throw new Error("Session expired");
    }

    const data = await response.json();

    if (!response.ok) {
        throw new Error(
            data.message || "Request failed"
        );
    }

    return data;
}



// Users


async function loadUsers() {

    try {

        users = await apiFetch("/api/users");

        renderUsers();

    } catch (e) {

        console.error(e);
    }
}


function renderUsers() {

    const list =
        document.getElementById("userList");

    list.innerHTML = "";

    users.forEach(user => {

        if (user.id === currentUser.id) {
            return;
        }

        const div =
            document.createElement("div");

        div.className = "user-item";

        const status =
            user.online
                ? "● Online"
                : "○ Offline";

        div.innerHTML = `
            <strong>${escapeHtml(user.name)}</strong>
            <span class="user-status ${user.online
                ? "online"
                : "offline"
            }">
                ${status}
            </span>
        `;

        div.onclick = () =>
            createPrivateConversation(user.id);

        list.appendChild(div);
    });
}



// Conversations


async function loadConversations() {

    try {

        conversations =
            await apiFetch(
                "/api/conversations"
            );

        renderConversations();

    } catch (e) {

        console.error(e);
    }
}


function renderConversations() {

    const list =
        document.getElementById(
            "conversationList"
        );

    list.innerHTML = "";

    conversations.forEach(conversation => {

        const div =
            document.createElement("div");

        div.className =
            "conversation-item";

        div.textContent =
            conversation.name ||
            "Private Chat";

        div.onclick = () =>
            openConversation(conversation);

        list.appendChild(div);
    });
}


async function createPrivateConversation(userId) {

    try {

        const conversation =
            await apiFetch(
                "/api/conversations",
                {
                    method: "POST",
                    body: JSON.stringify({
                        type: "PRIVATE",
                        userId: userId
                    })
                }
            );

        await loadConversations();

        openConversation(conversation);

    } catch (e) {

        alert(e.message);
    }
}


// Group


function showGroupDialog() {

    const container =
        document.getElementById(
            "groupMembers"
        );

    container.innerHTML = "";

    users.forEach(user => {

        if (user.id === currentUser.id) {
            return;
        }

        container.innerHTML += `
            <div class="group-member">
                <label>
                    <input
                        type="checkbox"
                        value="${user.id}"
                    >
                    ${escapeHtml(user.name)}
                </label>
            </div>
        `;
    });

    document
        .getElementById("groupDialog")
        .classList.remove("hidden");
}


function closeGroupDialog() {

    document
        .getElementById("groupDialog")
        .classList.add("hidden");
}


async function createGroup() {

    const name =
        document.getElementById(
            "groupName"
        ).value.trim();

    const selected =
        Array.from(
            document.querySelectorAll(
                "#groupMembers input:checked"
            )
        ).map(
            checkbox => checkbox.value
        );

    if (!name) {
        alert("Enter a group name");
        return;
    }

    if (selected.length === 0) {
        alert("Select at least one member");
        return;
    }

    try {

        const conversation =
            await apiFetch(
                "/api/conversations",
                {
                    method: "POST",
                    body: JSON.stringify({
                        type: "GROUP",
                        name: name,
                        members: selected
                    })
                }
            );

        closeGroupDialog();

        await loadConversations();

        openConversation(conversation);

    } catch (e) {

        alert(e.message);
    }
}


// Open conversation


async function openConversation(conversation) {

    currentConversation = conversation;

    document
        .getElementById("chatTitle")
        .textContent =
        conversation.name ||
        "Private Chat";

    document
        .getElementById("chatStatus")
        .textContent =
        conversation.type === "GROUP"
            ? "Group"
            : "Private conversation";

    await loadMessages();

    subscribeToConversation();
}


async function loadMessages() {

    if (!currentConversation) {
        return;
    }

    try {

        const messages =
            await apiFetch(
                `/api/conversations/${currentConversation.id}/messages`
            );

        const list =
            document.getElementById(
                "messageList"
            );

        list.innerHTML = "";

        messages.forEach(
            message => renderMessage(message)
        );

        scrollMessagesToBottom();

    } catch (e) {

        console.error(e);
    }
}



// WebSocket


function connectWebSocket() {

    console.log("Starting WebSocket connection...");
    console.log("Token exists:", !!token);

    if (stompClient && stompClient.active) {
        console.log("STOMP client is already active");
        return;
    }

    stompClient = new StompJs.Client({

        webSocketFactory: () => {
            console.log("Creating SockJS connection...");
            return new SockJS(`${API_URL}/ws`);
        },

        connectHeaders: {
            Authorization: `Bearer ${token}`
        },

        reconnectDelay: 5000,

        debug: function (str) {
            console.log("[STOMP]", str);
        },

        onConnect: () => {

            console.log("================================");
            console.log("WEBSOCKET CONNECTED");
            console.log("================================");

            stompClient.subscribe(
                "/topic/presence",
                message => {

                    console.log("Presence received:", message.body);

                    const presence = JSON.parse(message.body);

                    updateUserPresence(presence);
                }
            );

            subscribeToConversation();
        },

        onStompError: frame => {
            console.error("STOMP ERROR:", frame);
            console.error("Message:", frame.headers["message"]);
            console.error("Details:", frame.body);
        },

        onWebSocketError: error => {
            console.error("WEBSOCKET ERROR:", error);
        },

        onWebSocketClose: event => {
            console.error("WEBSOCKET CLOSED:", event);
        }
    });

    stompClient.activate();
}


function subscribeToConversation() {

    if (
        !stompClient ||
        !stompClient.connected ||
        !currentConversation
    ) {
        return;
    }

    if (conversationSubscription) {
        conversationSubscription.unsubscribe();
    }

    const destination =
        `/topic/conversations/${currentConversation.id}`;

    conversationSubscription =
        stompClient.subscribe(
            destination,
            message => {

                const received =
                    JSON.parse(message.body);

                renderMessage(received);

                scrollMessagesToBottom();
            }
        );
}



// Messages


function sendMessage() {

    if (
        !stompClient ||
        !stompClient.connected
    ) {
        alert("Chat connection is not ready");
        return;
    }

    if (!currentConversation) {
        alert("Select a conversation");
        return;
    }

    const input =
        document.getElementById(
            "messageInput"
        );

    const content =
        input.value.trim();

    if (!content) {
        return;
    }

    stompClient.publish({

        destination: "/app/chat",

        body: JSON.stringify({
            conversationId:
                currentConversation.id,
            content: content
        })
    });

    input.value = "";
}


function handleMessageKey(event) {

    if (event.key === "Enter") {

        event.preventDefault();

        sendMessage();
    }
}


function renderMessage(message) {

    const list =
        document.getElementById(
            "messageList"
        );

    const div =
        document.createElement("div");

    const mine =
        message.senderId === currentUser.id;

    div.className =
        mine
            ? "message mine"
            : "message";

    const time =
        message.timestamp
            ? new Date(
                message.timestamp
            ).toLocaleTimeString()
            : "";

    div.innerHTML = `
        <div class="message-content">

            ${!mine
            ? `<div class="message-sender">
                        ${escapeHtml(
                getUserName(
                    message.senderId
                )
            )}
                       </div>`
            : ""
        }

            <div>
                ${escapeHtml(
            message.content
        )}
            </div>

            <div class="message-time">
                ${time}
            </div>

        </div>
    `;

    list.appendChild(div);
}


function getUserName(userId) {

    if (
        currentUser &&
        userId === currentUser.id
    ) {
        return currentUser.name;
    }

    const user =
        users.find(
            item => item.id === userId
        );

    return user
        ? user.name
        : "User";
}


function scrollMessagesToBottom() {

    const list =
        document.getElementById(
            "messageList"
        );

    list.scrollTop =
        list.scrollHeight;
}



// Presence


function updateUserPresence(presence) {

    const user =
        users.find(
            item =>
                item.email === presence.email
        );

    if (!user) {
        return;
    }

    user.online =
        presence.online;

    renderUsers();
}


// AI


async function askAI() {

    const input =
        document.getElementById(
            "aiInput"
        );

    const responseBox =
        document.getElementById(
            "aiResponse"
        );

    const message =
        input.value.trim();

    if (!message) {
        return;
    }

    responseBox.textContent =
        "Thinking...";

    try {

        const data =
            await apiFetch(
                "/api/ai/chat",
                {
                    method: "POST",
                    body: JSON.stringify({
                        content: message
                    })
                }
            );

        responseBox.textContent =
            data.response;

    } catch (e) {

        responseBox.textContent =
            e.message;
    }
}


async function summarizeConversation() {

    if (!currentConversation) {

        alert(
            "Select a conversation first"
        );

        return;
    }

    const responseBox =
        document.getElementById(
            "aiResponse"
        );

    responseBox.textContent =
        "Generating summary...";

    try {

        const data =
            await apiFetch(
                `/api/ai/summarize/${currentConversation.id}`,
                {
                    method: "POST"
                }
            );

        responseBox.textContent =
            data.response;

    } catch (e) {

        responseBox.textContent =
            e.message;
    }
}



// Utility

function escapeHtml(value) {

    const div =
        document.createElement("div");

    div.textContent =
        value ?? "";

    return div.innerHTML;
}