# SECURITY POLICY

## 1. Security Overview
The Hangout Lounge application adheres to rigorous security standards. Our codebase and runtime environments are hardened against common OWASP Top 10 vulnerabilities, WebSocket connection hijackings, session spoofing, and privilege escalations.

---

## 2. Threat Model & Implemented Mitigations

| Threat | Severity | Implemented Mitigation |
| :--- | :--- | :--- |
| **Impersonation & Session Spoofing** | Critical | Implemented JWT-based register/login. All passwords are encrypted with strong **BCrypt** salt factors of 12. Token rotators invalidate older refresh tokens immediately. |
| **Room Hijacking** | High | Implemented Role-Based Access Control (RBAC). Only room creators or appointed administrators/moderators can invoke control functions like video synchronization, mutes, or kicks. |
| **DoS (Resource Exhaustion)** | High | Configured strict message-rate boundaries (maximum 15 socket messages per 5s) and strict payload validation (maximum 16KB WebSocket frame limit). Express API calls are guarded with in-memory sliding-window limiters. |
| **Cross-Site Scripting (XSS)** | High | All text fields and file URLs are escaped using a strict HTML character entity encoder before distribution to other room members. |
| **Path Traversal / Arbitrary Execution** | High | File uploads are isolated, filenames are randomized using high-entropy secure tokens, and target directories are validated using path relativity checks. Script execution is fully disabled on the public uploads directory. |
| **Local Data Theft on Rooted Devices** | Medium | Access and Refresh tokens are encrypted using **AES-128 GCM** block ciphers via keys generated and isolated in the hardware-backed native **Android KeyStore**. |

---

## 3. Security Requirements

### A. Password Complexity Policies
All registering members must satisfy the following validation criteria:
- Minimum of 8 characters in length.
- At least one uppercase alphabetic character.
- At least one numeric digit.
- At least one special symbol (`@$!%*?&`).

### B. Network Traffic Security
- **Strict HTTPS / WSS**: Cleartext HTTP/WS is disabled inside the Android Manifest to safeguard transport layer security against sniffing.
- **Handshake Interception**: Connection upgrades to WebSockets verify the JWT token parameter on the HTTP upgrade request before permitting the socket promotion.

---

## 4. Reporting Vulnerabilities
If you identify any security issue, please do not open a public issue. Instead, contact the engineering team securely at `nakulpatil230804@gmail.com` with a detailed description of the exploit and reproduction steps.
