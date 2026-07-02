# 🚀 Production Deployment Checklist

Follow this checklist step-by-step to deploy the Hangout Lounge backend securely to **Render** and configure the **Android client** for production environments.

---

## 📋 Phase 1: Prerequisites
- [ ] **GitHub Repository**: Create a private or public repository on GitHub.
- [ ] **Render Account**: Register at [Render.com](https://render.com/).
- [ ] **Android Build Tools**: Ensure you can compile APKs locally or via Gradle tools.

---

## 🐙 Phase 2: Push to GitHub
1. Initialize Git and commit all tracking files:
   ```bash
   git init
   git add .
   git commit -m "chore: prepare project for production deployment"
   ```
2. Add your GitHub remote and push:
   ```bash
   git branch -M main
   git remote add origin https://github.com/your-username/your-repo-name.git
   git push -u origin main
   ```

---

## ☁️ Phase 3: Deploy Backend on Render (Via Blueprint)
We have included a pre-configured `render.yaml` Blueprint specification. This automates the entire setup (Web Service, Docker Runtime, Environmental Variables, and Persistent Storage Disks).

1. Log in to [Render Dashboard](https://dashboard.render.com/).
2. Click **New** -> **Blueprint**.
3. Connect your GitHub repository.
4. Render will read `render.yaml` and prompt you to verify the configuration:
   - **Service Name**: `hangout-lounge-backend`
   - **Environment**: `Docker`
   - **Region**: Select the region closest to your primary user base (e.g., US Oregon, Frankfurt, Singapore).
5. Click **Apply**.
6. Render will automatically:
   - Create a **1GB Persistent Disk** named `hangout-disk` mounted at `/data`.
   - Setup environment variables with high-entropy randomized values for `JWT_SECRET` and `JWT_REFRESH_SECRET`.
   - Build the container from `/backend/Dockerfile`.
   - Expose the public service securely over SSL (`https://your-service.onrender.com`).

---

## 🧪 Phase 4: Verify Backend Health
Once Render reports a successful deployment, verify the server is fully operational:

- [ ] **Server Status**: Open your browser and navigate to `https://your-service.onrender.com/`. You should see the secure HTML welcoming landing or API notice.
- [ ] **Security Headers Check**: Run a curl request to verify Helmet security headers:
  ```bash
  curl -I https://your-service.onrender.com/
  ```
  Verify that headers like `X-Frame-Options: SAMEORIGIN`, `Content-Security-Policy`, and `Strict-Transport-Security` are present.
- [ ] **Uploads Directory Isolation**: Verify that scripts cannot be executed inside the uploads directory.

---

## 📱 Phase 5: Configure Android Client
1. In the Android App, locate the Server settings in the main **Dashboard / Lobby Screen**.
2. Tap the **Settings (Gear Icon)** in the top right.
3. Enter your new production server URL:
   - Use `wss://your-service.onrender.com` (use `wss://` instead of `https://` for real-time WebSocket communication).
4. Tap **Save & Apply**.
5. Test registering a secure account:
   - Create a member account with a password matching the complexity rules (minimum 8 characters, 1 uppercase letter, 1 digit, and 1 special symbol).
   - Log in and verify that the app prompts you with: `Welcome, <username>! Connected as Member.`
6. Test room creation and media synchronization.

---

## 🛡️ Phase 6: Post-Deployment Maintenance & Security Audit
- [ ] **Monitor Server Logs**: Check the **Logs** tab on Render to observe handshakes, heartbeats, and rate limiter activities.
- [ ] **Rotate JWT Secrets regularly**: You can trigger a redeployment in Render with newly rotated secrets under the **Environment** tab.
- [ ] **Scale storage as needed**: If upload sizes grow, increase the persistent disk capacity in the **Disks** settings.
