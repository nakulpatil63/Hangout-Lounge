# SECURITY AUDIT REPORT - HANGOUT LOUNGE APPLICATION

**Audit Date**: 2026-07-02
**Target Systems**: Android Client (Kotlin / Jetpack Compose) & Node.js Backend Server
**Classification**: Confidentially Class: High-Risk (Untrusted, Open-By-Default Ecosystem)

---

## 1. Executive Summary
An in-depth security review of the Hangout Lounge application codebase (comprising an Android front-end and a Node.js WebSocket backend) reveals multiple high-severity vulnerabilities. Under the current implementation, the application is highly vulnerable to session hijacking, room hijacking, Denial of Service (DoS), Cross-Site Scripting (XSS), arbitrary command injection/impersonation, and total lack of transport/payload integrity. 

---

## 2. Identified Vulnerabilities & Weaknesses

### A. Authentication Flaws (Severity: CRITICAL)
- **Problem**: There is no authentication mechanism. The client enters a simple string "username" on the UI, which is forwarded directly to the WebSocket and trusted implicitly. Anyone can assume any username (even "System", "Admin", or another member's identity) without a password or verification challenge.
- **Impact**: Attackers can spoof identities, steal room administration privileges, and bypass any client-side restrictions.

### B. Authorization & Access Control Flaws (Severity: HIGH)
- **Problem**: There are no room roles or permissions enforced on the backend. Any participant in a room can change the video URL, pause/play, claim the "DJ" role, trigger game updates, and modify state parameters for everyone else.
- **Impact**: Room owners can have their sessions hijacked, or trolls can endlessly disrupt media synchronization.

### C. WebSocket Vulnerabilities (Severity: CRITICAL)
- **Problem**: 
  - Connection requests (`wss://` or `ws://`) are allowed anonymously. No JWT or token verification is made prior to or during connection.
  - Heartbeats are only simulated locally on Android (ping interval of 15s in client okhttp, but no custom ping-pong handler is enforced on the server to prune dead connections).
  - No size checks on messages, making packet flooding easy.
- **Impact**: Connection pools can be exhausted, and rogue clients can connect to arbitrary room feeds.

### D. SQL / NoSQL Injection Risks (Severity: MEDIUM)
- **Problem**: While the database is in-memory on the backend (`rooms = {}`), any plans to back this by database engines without input sanitization or parameterization present high risk of SQL/NoSQL injection.
- **Impact**: Database compromise (in future transitions).

### E. Cross-Site Scripting (XSS) & Command Injection (Severity: HIGH)
- **Problem**: User input from `send_message` and `meme_drop` is broadcast directly to other clients without sanitization or escaping. If clients render HTML/WebView, or if they render Markdown with HTML enabled, malicious scripts can execute.
- **Impact**: Client-side execution of unsolicited payloads, cookie/token theft, and session redirection.

### F. File Security & Path Traversal (Severity: HIGH)
- **Problem**: If files were to be uploaded, there are no checks on MIME types, file sizes, or target paths. Path traversal (`../../etc/passwd`) is fully possible.
- **Impact**: Arbitrary file write/overwrite on the server host.

### G. Lack of Rate Limiting (Severity: HIGH)
- **Problem**: A user can spam room creation, room join requests, and chat messages in rapid succession. There is no rate limiter in the Express or WebSocket routes.
- **Impact**: Denial of Service (DoS) through resource exhaustion.

### H. Memory Leaks & Race Conditions (Severity: MEDIUM)
- **Problem**: Stale rooms are set to clean up after 1 minute if empty, but if users reconnect repeatedly or leave stale references, memory accumulates. In-memory data structures are not thread-safe or resilient to high concurrency.
- **Impact**: Server process crash under high load.

### I. Android Privacy and Token Storage (Severity: MEDIUM)
- **Problem**: Token storage currently relies on standard unencrypted SharedPreferences (`hangout_prefs`). Network cleartext traffic limits are not explicitly enforced.
- **Impact**: Reverse-engineering and local extraction of user data from root devices.

---

## 3. Remediation Strategy (Action Plan)

1. **Phase 2 (Authentication)**: Implement a fully-functional Express endpoint for JWT Authentication (register, login, logout, refresh tokens, token rotation, BCrypt password hashing, and strong password validation).
2. **Phase 3 (Authorization)**: Introduce Role-Based Access Control (RBAC) on the backend: **Owner**, **Admin**, **Moderator**, **Member**, and **Guest**. Protect actions by verifying the caller's role.
3. **Phase 4 (WebSocket Security)**: Enforce JWT handshakes, heartbeat ping/pong pruning, message size limits (e.g. max 16KB), and duplicate packet checks.
4. **Phase 5 (Input Validation)**: Strictly check string lengths, format schemas, escape HTML characters in text fields, and validate URLs.
5. **Phase 6 (Rate Limiting)**: Add Express-rate-limit and token-bucket socket rate limiting.
6. **Phase 7 (File Security)**: Create a secure asset endpoint with strict MIME types, randomized filenames, and directory isolation.
7. **Phase 8 (Database Security)**: Use SQLite (or in-memory parameterized storage) for persistent, secure, and SQL-injection-resistant database queries.
8. **Phase 9 (Android Hardening)**: Use `EncryptedSharedPreferences`, sanitize debug logging, and configure strict Network Security rules.
9. **Phase 10 (Server Hardening)**: Configure Helmet, CORS whitelisting, HTTP compression, graceful shutdown, health endpoints, and metrics.
10. **Phase 11 (Privacy Controls)**: Implement online status, visibility controls, and preference tracking.
11. **Phase 12 (Abuse Mitigation)**: Implement mute, ban, temporary ban, report, and kick handlers on the server.
12. **Phase 13 (Production Files)**: Add `Dockerfile`, `docker-compose.yml`, `package.json`, `.env.example`, and comprehensive markdown files (`README.md`, `SECURITY.md`, `DEPLOYMENT.md`, `ARCHITECTURE.md`).
