# 🍿 Hangout Lounge - Enterprise-Hardened Real-Time Sync Platform

An enterprise-hardened, real-time social platform combining a visually rich **Android client** (Jetpack Compose, MVVM, Room, AES/GCM KeyStore encryption) and a production-grade, highly secure **Node.js WebSocket/REST server** (Express, Helmet, CORS, JWT session rotation, SQLite parameterized persistence, and rate-limiting).

---

## 🏗️ Platform Architecture Overview

```
                      [ HTTPS/WSS Protocols ]
+-------------------+                          +---------------------+
|   Android App     |                          |   Node.js Backend   |
| (Jetpack Compose) | <======================> |  (Express + WS)     |
|                   |   Secure WebSockets w/   |                     |
|  - Room Local DB  |   Heartbeats & JWTs      |  - Express REST APIs|
|  - AES/GCM KeyStr |                          |  - SQLite (Secure)  |
|  - MVVM Flow      |                          |  - Token Rotation   |
+-------------------+                          +---------------------+
```

### 1. Hardened Android Application (Client)
- **Tech Stack**: Kotlin, Jetpack Compose, Coroutines/Flow, Room Database, OkHttp.
- **Visual Design**: High-fidelity Glassmorphic dark slate visuals, responsive fluid layouts, and smooth navigation animations.
- **Cryptography**: Native Android `KeyStore` provider utilizing standard AES/GCM/NoPadding block encryption to isolate JWT access and refresh tokens from extraction or local tampering.
- **API Integrity**: Strict `usesCleartextTraffic="false"` and disabled Android backup rules to lock down local database state.

### 2. Hardened Node.js & WebSocket Server (Backend)
- **Tech Stack**: Node.js, Express, `ws` library, SQLite3, JWT, BCrypt, Helmet, Multer.
- **REST Endpoints**: Secure user signup, login with BCrypt hashing, JWT token rotation, file upload with mime/size checks, and privacy control updates.
- **WS Security**: Intercepts handshakes, validates handshakes with JWT, implements heartbeats to prune inactive connections, throttles and limits message sizes (16KB), and filters spam.

---

## 🚀 Quick Start Guide

### 1. Run the Backend via Docker Compose
To run the fully-hardened, production-ready backend server locally:
```bash
cd backend
docker-compose up --build -d
```
The server will start listening securely on `http://localhost:3000`.

### 2. Compile the Android Client
Build the APK securely:
```bash
gradle assembleDebug
```

---

## 📖 System Guides
For deep technical and security walkthroughs, refer to:
- [ARCHITECTURE.md](./ARCHITECTURE.md) - Deep architectural diagram and schema details.
- [SECURITY.md](./SECURITY.md) - Security configuration, policy compliance, and threat model.
- [DEPLOYMENT.md](./DEPLOYMENT.md) - Step-by-step production setup on Railway and Render.
