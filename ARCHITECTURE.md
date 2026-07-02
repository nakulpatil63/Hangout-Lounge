# ARCHITECTURE SPECIFICATION

This document outlines the design patterns, storage structures, and modular components governing the Hangout Rooms security-hardened social platform.

---

## 1. Clean MVVM Architecture (Android Client)

Our Android application strictly enforces **Model-View-ViewModel (MVVM)**, ensuring that structural concerns are highly segregated, maintainable, and decoupled.

```
       [ USER ACTIONS ]
+----------------------------+
|       Compose Views        | <--- Re-compositions (Reactive)
|  (RoomScreen, AuthScreen)  |
+----------------------------+
              | (Triggers Intents / Observes UI State flows)
              v
+----------------------------+
|     HangoutViewModel       | <--- Collects StateFlows & Coordinates Flows
| (Jetpack ViewModel Lifecycle)
+----------------------------+
              | (Queries repository / Dispatches Network Calls)
              v
+--------------------------------------------------------------+
|                      Repository Layer                        |
|                                                              |
|   +-----------------------+      +-----------------------+   |
|   |   WebSocketManager    |      |   HangoutRepository   |   |
|   |  (OkHttp WS Handler)  |      |   (Room DB Interface) |   |
|   +-----------------------+      +-----------------------+   |
+--------------------------------------------------------------+
              |                               |
              | (Handshakes + WSS)            | (Encrypted Queries)
              v                               v
    [ WebSockets Backend ]           [ Room SQLite Database ]
```

### Components
1. **Compose Views**: Pure visual elements written in Jetpack Compose utilizing dynamic Material 3 components and consistent, spacious layouts.
2. **ViewModel**: The state orchestration layer. Exposes immutable `StateFlow` structures, manages background coroutine scopes, and maintains system integrity.
3. **Repository**: Unified access facade consolidating network-socket bindings and Room database transactions.
4. **Local Database (Room)**: Embedded persistence layer for storing chat caches, room indexes, and user metadata.
5. **Secure KeyStore Helper**: Hardware-backed AES cryptographic helper used on-the-fly to encrypt session tokens.

---

## 2. Microservice Express / WebSocket Server (Backend)

The Node.js backend handles HTTP endpoints and persistent real-time socket connections concurrently using an event-driven loop.

```
                  +--------------------------------------+
                  |           Express Server             |
                  |                                      |
                  |   +------------------------------+   |
                  |   |      Security Headers        |   |
                  |   | (Helmet + CORS + Compression) |   |
                  |   +------------------------------+   |
                  +--------------------------------------+
                                     |
                 +-------------------+-------------------+
                 |                                       |
                 v (REST Endpoints)                      v (WebSockets Connection)
     +-----------------------+               +-----------------------+
     |     Auth / Files      |               |     ws Server Core    |
     | (BCrypt, Multer, JWT) |               |  (Heartbeat, Limits)  |
     +-----------------------+               +-----------------------+
                 |                                       |
                 v (SQL Queries)                         v (Presence Broadcasts)
     +---------------------------------------------------------------+
     |                   SQLite Parameterized Database               |
     |   - users       - rooms       - bans      - room_members      |
     +---------------------------------------------------------------+
```

### Server Execution Structure
- **REST Endpoints**: Managed securely via Express routes. Routes are protected with custom Express Rate Limiters and JWT validators.
- **WebSocket Upgrade Verification**: Connection upgrade requests are parsed, and the JWT token query parameter is decrypted. Handshakes that fail authentication are rejected with `401 Unauthorized` responses before promotion to a socket occurs.
- **SQLite dual-layer**: High performance and security are achieved by combining ephemeral in-memory references for lightweight connection tracking and robust, parameterized SQL operations on a durable `SQLite` file volume.
- **Process Supervision**: SIGTERM signals are intercepted, ensuring existing queries finish before the server process exits smoothly.
