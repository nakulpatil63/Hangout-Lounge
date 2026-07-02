# DEPLOYMENT GUIDE

This guide provides step-by-step instructions to deploy the enterprise-hardened Hangout Room backend server to **Railway** or **Render** for reliable production orchestration.

---

## 1. Environment Variable Setup
Before triggering any cloud deployment, ensure you configure the following environmental settings in your platform's dashboard:

| Variable | Description | Recommended Setting |
| :--- | :--- | :--- |
| `NODE_ENV` | Running node environment mode | `production` |
| `PORT` | Public listening port | `3000` or port assigned by platform |
| `JWT_SECRET` | Secret token for JWT generation | *Generate high-entropy random hex* |
| `JWT_REFRESH_SECRET` | Secret token for Refresh token generation | *Generate high-entropy random hex* |
| `CORS_WHITELIST` | Whitelisted clients allowed to query REST APIs | `https://your-custom-app.com` (or `*` for public APIs) |

---

## 2. Option A: Deployment to Railway (Recommended)
Railway automatically detects the `Dockerfile` inside the `/backend` folder and triggers a high-performance containerized build instantly.

### Step 1: Create a Railway Account
Sign up or log in at [Railway.app](https://railway.app/).

### Step 2: Push the Code
Push this repository (or just the `/backend` directory) to your GitHub account.

### Step 3: Instantiate Railway Project
1. Click **New Project** in the Railway Dashboard.
2. Select **Deploy from GitHub repo**.
3. Choose your repository.
4. Set the Root Directory to `backend` in the deployment settings.

### Step 4: Configure Variables
Go to the **Variables** tab of your service and input all the production variables specified in section 1 of this guide.

Railway will build, test, and expose a secure URL (e.g., `https://your-service-production.up.railway.app`) that you can copy and paste into your Android Client settings!

---

## 3. Option B: Deployment to Render
Render is an excellent, free-tier-friendly option that natively supports Docker runtime environments.

### Step 1: Initialize Render Web Service
1. Log in to [Render.com](https://render.com/).
2. Select **New** -> **Web Service**.
3. Connect your GitHub repository.

### Step 2: Set Build Settings
- **Region**: Choose the closest physical region to your client base.
- **Runtime**: Select **Docker** (this directs Render to read our custom `Dockerfile` directly).
- **Docker Build Context**: Set this to `backend` (or the folder containing the `Dockerfile`).

### Step 3: Add Variables
Click **Advanced** and add the required environment variables (see table in Section 1).

### Step 4: Add Persistent Disk (Crucial for SQLite)
Since cloud containers are ephemeral, your SQLite database (`hangout.db`) will reset on every redeployment unless a persistent disk is attached.
1. Scroll down to the **Disks** section in Render.
2. Click **Add Disk**.
3. Set the Mount Path to `/usr/src/app`.
4. Render will bind-mount SQLite data to this persistent, secure volume.

Render will trigger the container compilation and expose an SSL-secured endpoint (`https://...onrender.com`) for direct production communication!
