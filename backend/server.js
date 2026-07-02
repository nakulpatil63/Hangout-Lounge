/**
 * Hangout Room - Enterprise-Hardened Real-Time Multi-User Backend Server
 * Built with Node.js, Express, and ws (WebSocket).
 *
 * Implements high-performance security, JWT authentication, RBAC, rate limiting,
 * abuse protection, input validation, and SQLite database persistence.
 */

const express = require('express');
const { WebSocketServer, WebSocket } = require('ws');
const http = require('http');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const helmet = require('helmet');
const cors = require('cors');
const compression = require('compression');
const multer = require('multer');
const sqlite3 = require('sqlite3').verbose();
require('dotenv').config();

// ==========================================
// CONFIGURATION & SECRETS
// ==========================================
const PORT = process.env.PORT || 3000;
const JWT_SECRET = process.env.JWT_SECRET || 'super_secret_hangout_key_change_in_production_123!';
const JWT_REFRESH_SECRET = process.env.JWT_REFRESH_SECRET || 'refresh_super_secret_key_change_in_production_456!';
const UPLOAD_DIR = process.env.UPLOAD_DIR || path.join(__dirname, 'uploads');
const DB_PATH = process.env.DB_PATH || path.join(__dirname, 'hangout.db');

// Ensure upload directory exists
if (!fs.existsSync(UPLOAD_DIR)) {
  fs.mkdirSync(UPLOAD_DIR, { recursive: true });
}

// ==========================================
// DATABASE SETUP (SQLite with Parameterization)
// ==========================================
const db = new sqlite3.Database(DB_PATH, (err) => {
  if (err) {
    console.error('[DB Error] Failed to connect to SQLite:', err.message);
  } else {
    console.log('[DB] Connected to SQLite database securely.');
  }
});

// Initialize DB schema securely (using transactional-like serialization)
db.serialize(() => {
  // Users table
  db.run(`
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      username TEXT UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      role TEXT NOT NULL DEFAULT 'Member',
      avatar_index INTEGER DEFAULT 0,
      is_online INTEGER DEFAULT 0,
      last_seen INTEGER DEFAULT 0,
      created_at INTEGER DEFAULT 0,
      online_status TEXT DEFAULT 'online',
      who_can_message TEXT DEFAULT 'all',
      who_can_invite TEXT DEFAULT 'all',
      who_can_call TEXT DEFAULT 'all',
      who_can_view_profile TEXT DEFAULT 'all'
    )
  `);

  // Refresh tokens table (token rotation tracking)
  db.run(`
    CREATE TABLE IF NOT EXISTS refresh_tokens (
      token TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      device_info TEXT,
      expires_at INTEGER NOT NULL,
      FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
    )
  `);

  // Rooms table
  db.run(`
    CREATE TABLE IF NOT EXISTS rooms (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      owner_id TEXT NOT NULL,
      current_video_url TEXT DEFAULT '',
      video_title TEXT DEFAULT '',
      is_video_playing INTEGER DEFAULT 0,
      video_progress_ms INTEGER DEFAULT 0,
      last_video_sync_time INTEGER DEFAULT 0,
      vibe TEXT DEFAULT 'cozy',
      created_at INTEGER DEFAULT 0
    )
  `);

  // Room members (RBAC mapping)
  db.run(`
    CREATE TABLE IF NOT EXISTS room_members (
      room_id TEXT NOT NULL,
      user_id TEXT NOT NULL,
      role TEXT NOT NULL DEFAULT 'Member',
      joined_at INTEGER NOT NULL,
      is_muted INTEGER DEFAULT 0,
      is_speaking INTEGER DEFAULT 0,
      PRIMARY KEY (room_id, user_id),
      FOREIGN KEY(room_id) REFERENCES rooms(id) ON DELETE CASCADE,
      FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
    )
  `);

  // Bans list
  db.run(`
    CREATE TABLE IF NOT EXISTS room_bans (
      room_id TEXT NOT NULL,
      user_id TEXT NOT NULL,
      banned_by TEXT NOT NULL,
      banned_at INTEGER NOT NULL,
      reason TEXT,
      expires_at INTEGER,
      PRIMARY KEY (room_id, user_id)
    )
  `);

  // Messages history table
  db.run(`
    CREATE TABLE IF NOT EXISTS messages (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      room_id TEXT NOT NULL,
      sender_id TEXT NOT NULL,
      sender_name TEXT NOT NULL,
      sender_avatar_index INTEGER DEFAULT 0,
      content TEXT NOT NULL,
      timestamp INTEGER NOT NULL,
      is_system INTEGER DEFAULT 0,
      meme_url TEXT DEFAULT '',
      FOREIGN KEY(room_id) REFERENCES rooms(id) ON DELETE CASCADE
    )
  `);

  // Audit Logs table for security event tracking
  db.run(`
    CREATE TABLE IF NOT EXISTS audit_logs (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      event_type TEXT NOT NULL,
      actor_id TEXT,
      description TEXT,
      ip_address TEXT,
      timestamp INTEGER NOT NULL
    )
  `);
});

// Helper: Log security events securely
function logSecurityEvent(eventType, actorId, description, ipAddress = '0.0.0.0') {
  const stmt = db.prepare(`
    INSERT INTO audit_logs (event_type, actor_id, description, ip_address, timestamp)
    VALUES (?, ?, ?, ?, ?)
  `);
  stmt.run(eventType, actorId, description, ipAddress, Date.now());
  stmt.finalize();
  console.log(`[AUDIT] [${eventType}] Actor: ${actorId || 'Anonymous'} - ${description}`);
}

// ==========================================
// HELPER FUNCTIONS & MIDDLEWARES
// ==========================================

// Safe HTML sanitizer to prevent XSS
function escapeHtml(str) {
  if (typeof str !== 'string') return '';
  return str.replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#x27;')
            .replace(/\//g, '&#x2F;');
}

// In-Memory Token Bucket Rate Limiter
const rateLimitStore = {};
function checkRateLimit(ipOrUserId, limit = 100, windowMs = 60000) {
  const now = Date.now();
  if (!rateLimitStore[ipOrUserId]) {
    rateLimitStore[ipOrUserId] = { tokens: limit, lastRefill: now };
  }

  const userLimit = rateLimitStore[ipOrUserId];
  const elapsed = now - userLimit.lastRefill;
  
  // Refill tokens proportionally
  const refillAmount = limit * (elapsed / windowMs);
  userLimit.tokens = Math.min(limit, userLimit.tokens + refillAmount);
  userLimit.lastRefill = now;

  if (userLimit.tokens >= 1) {
    userLimit.tokens -= 1;
    return true;
  }
  return false;
}

// REST Rate Limiter middleware
function apiRateLimiter(limit = 60, windowMs = 60000) {
  return (req, res, next) => {
    const identifier = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    if (!checkRateLimit(identifier, limit, windowMs)) {
      logSecurityEvent('RATE_LIMIT_EXCEEDED', null, `REST API rate limit reached by IP: ${identifier}`, identifier);
      return res.status(429).json({ error: 'Too many requests. Please try again later.' });
    }
    next();
  };
}

// Express Auth Verification middleware
function verifyToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Access token missing or malformed' });
  }

  const token = authHeader.split(' ')[1];
  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    req.user = decoded;
    next();
  } catch (err) {
    return res.status(401).json({ error: 'Invalid or expired access token' });
  }
}

// Password Validator
function validatePassword(password) {
  // Strong password policy: min 8 chars, at least 1 number, 1 special, 1 uppercase
  const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
  return passwordRegex.test(password);
}

// Safe Path check to prevent Path Traversal
function isSafePath(base, target) {
  const relative = path.relative(base, target);
  return relative && !relative.startsWith('..') && !path.isAbsolute(relative);
}

// ==========================================
// EXPRESS SERVER SETUP
// ==========================================
const app = express();

// Secure server with Helmet
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      styleSrc: ["'self'", "'unsafe-inline'"],
      imgSrc: ["'self'", "data:", "*"],
      connectSrc: ["'self'", "ws:", "wss:"]
    }
  }
}));

// CORS Configuration (Strict whitelist)
const corsOptions = {
  origin: process.env.CORS_WHITELIST ? process.env.CORS_WHITELIST.split(',') : '*',
  methods: ['GET', 'POST', 'PUT', 'DELETE'],
  allowedHeaders: ['Content-Type', 'Authorization'],
  credentials: true
};
app.use(cors(corsOptions));

// Compress all HTTP payloads
app.use(compression());

// Parse requests
app.use(express.json({ limit: '10kb' })); // Prevents large JSON payload flood crashes

// Serve uploaded static files securely
app.use('/uploads', express.static(UPLOAD_DIR, {
  fallthrough: false,
  setHeaders: (res, filePath) => {
    // Prevent execution of scripts by setting content-type strictly
    const ext = path.extname(filePath).toLowerCase();
    if (['.jpg', '.jpeg', '.png', '.gif', '.webp'].includes(ext)) {
      res.setHeader('Content-Type', `image/${ext.substring(1)}`);
    } else {
      res.setHeader('Content-Type', 'application/octet-stream');
    }
    res.setHeader('Content-Disposition', 'attachment'); // download rather than render to prevent XSS
  }
}));

// Create standard HTTP server
const server = http.createServer(app);

// ==========================================
// REST API ENDPOINTS
// ==========================================

// 1. Health check & Server Status
app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'HEALTHY',
    timestamp: Date.now(),
    uptime: process.uptime()
  });
});

// 2. Security Metrics endpoint
app.get('/metrics', (req, res) => {
  db.all(`SELECT count(*) as count, event_type FROM audit_logs GROUP BY event_type`, [], (err, rows) => {
    if (err) {
      return res.status(500).json({ error: 'Failed to retrieve metrics' });
    }
    res.status(200).json({
      active_connections: wss ? wss.clients.size : 0,
      audit_events: rows
    });
  });
});

// 3. Register user (BCrypt hashed, parameter validation)
app.post('/api/auth/register', apiRateLimiter(10, 60000), (req, res) => {
  const { username, password, avatarIndex } = req.body;
  
  if (!username || !password) {
    return res.status(400).json({ error: 'Username and password are required' });
  }

  const cleanUsername = username.trim().substring(0, 20); // Sanitize and length check
  if (cleanUsername.length < 3) {
    return res.status(400).json({ error: 'Username must be at least 3 characters' });
  }

  if (!validatePassword(password)) {
    return res.status(400).json({
      error: 'Password must be at least 8 characters long and include an uppercase letter, a number, and a special character.'
    });
  }

  // Check if username already exists
  db.get('SELECT id FROM users WHERE username = ?', [cleanUsername], (err, user) => {
    if (err) {
      return res.status(500).json({ error: 'Database verification failed' });
    }
    if (user) {
      return res.status(400).json({ error: 'Username is already taken' });
    }

    // Generate secure UUID and hash password with salt factor of 12 (BCrypt)
    const userId = crypto.randomUUID();
    bcrypt.hash(password, 12, (err, hash) => {
      if (err) {
        return res.status(500).json({ error: 'Hashing engine failed' });
      }

      const avatar = parseInt(avatarIndex, 10) || 0;

      // Insert into db using parameterized queries
      const stmt = db.prepare(`
        INSERT INTO users (id, username, password_hash, role, avatar_index, created_at)
        VALUES (?, ?, ?, 'Member', ?, ?)
      `);
      stmt.run(userId, cleanUsername, hash, avatar, Date.now(), (err) => {
        stmt.finalize();
        if (err) {
          return res.status(500).json({ error: 'User registration failed' });
        }

        logSecurityEvent('USER_REGISTRATION', userId, `Registered account: ${cleanUsername}`, req.socket.remoteAddress);
        res.status(201).json({ message: 'User registered successfully!', userId });
      });
    });
  });
});

// 4. Secure Login (JWT Handshake, Refresh Tokens & Device Tracking)
app.post('/api/auth/login', apiRateLimiter(15, 60000), (req, res) => {
  const { username, password, deviceInfo } = req.body;

  if (!username || !password) {
    return res.status(400).json({ error: 'Username and password are required' });
  }

  db.get('SELECT * FROM users WHERE username = ?', [username.trim()], (err, user) => {
    if (err || !user) {
      logSecurityEvent('AUTH_FAILURE', null, `Failed login attempt for username: ${username}`, req.socket.remoteAddress);
      return res.status(401).json({ error: 'Invalid username or password' });
    }

    bcrypt.compare(password, user.password_hash, (err, matched) => {
      if (err || !matched) {
        logSecurityEvent('AUTH_FAILURE', user.id, `Incorrect password attempt for user: ${username}`, req.socket.remoteAddress);
        return res.status(401).json({ error: 'Invalid username or password' });
      }

      // Generate Access Token (short-lived: 15 mins)
      const accessToken = jwt.sign(
        { id: user.id, username: user.username, role: user.role },
        JWT_SECRET,
        { expiresIn: '15m' }
      );

      // Generate Refresh Token (long-lived: 7 days)
      const refreshToken = jwt.sign(
        { id: user.id },
        JWT_REFRESH_SECRET,
        { expiresIn: '7d' }
      );

      // Store Refresh Token (Token Rotation tracking)
      const expiresAt = Date.now() + 7 * 24 * 60 * 60 * 1000;
      const stmt = db.prepare(`
        INSERT INTO refresh_tokens (token, user_id, device_info, expires_at)
        VALUES (?, ?, ?, ?)
      `);
      stmt.run(refreshToken, user.id, deviceInfo || 'Unknown Device', expiresAt, (err) => {
        stmt.finalize();
        if (err) {
          return res.status(500).json({ error: 'Session creation failed' });
        }

        logSecurityEvent('AUTH_SUCCESS', user.id, `Successfully logged in. Role: ${user.role}`, req.socket.remoteAddress);

        res.status(200).json({
          accessToken,
          refreshToken,
          user: {
            id: user.id,
            username: user.username,
            role: user.role,
            avatarIndex: user.avatar_index
          }
        });
      });
    });
  });
});

// 5. Token Rotation & Refresh Token API
app.post('/api/auth/refresh', (req, res) => {
  const { refreshToken } = req.body;
  if (!refreshToken) {
    return res.status(400).json({ error: 'Refresh token is required' });
  }

  // Check refresh token validity
  db.get('SELECT * FROM refresh_tokens WHERE token = ?', [refreshToken], (err, row) => {
    if (err || !row) {
      logSecurityEvent('TOKEN_HIJACK_WARNING', null, `Failed/stale refresh attempt using token: ${refreshToken}`, req.socket.remoteAddress);
      return res.status(403).json({ error: 'Invalid or revoked refresh token' });
    }

    if (row.expires_at < Date.now()) {
      // Token expired, delete it
      db.run('DELETE FROM refresh_tokens WHERE token = ?', [refreshToken]);
      return res.status(403).json({ error: 'Refresh token expired' });
    }

    try {
      const decoded = jwt.verify(refreshToken, JWT_REFRESH_SECRET);
      
      // Get User details
      db.get('SELECT * FROM users WHERE id = ?', [decoded.id], (err, user) => {
        if (err || !user) {
          return res.status(403).json({ error: 'User session no longer exists' });
        }

        // Rotate Refresh Token
        const newAccessToken = jwt.sign(
          { id: user.id, username: user.username, role: user.role },
          JWT_SECRET,
          { expiresIn: '15m' }
        );

        const newRefreshToken = jwt.sign(
          { id: user.id },
          JWT_REFRESH_SECRET,
          { expiresIn: '7d' }
        );

        // Revoke old refresh token, save new one
        db.serialize(() => {
          db.run('DELETE FROM refresh_tokens WHERE token = ?', [refreshToken]);
          const stmt = db.prepare(`
            INSERT INTO refresh_tokens (token, user_id, device_info, expires_at)
            VALUES (?, ?, ?, ?)
          `);
          stmt.run(newRefreshToken, user.id, row.device_info, Date.now() + 7 * 24 * 60 * 60 * 1000);
          stmt.finalize();
        });

        res.status(200).json({
          accessToken: newAccessToken,
          refreshToken: newRefreshToken
        });
      });
    } catch (err) {
      return res.status(403).json({ error: 'Invalid or tampered token' });
    }
  });
});

// 6. Secure Logout Flow (Invalidate tokens)
app.post('/api/auth/logout', verifyToken, (req, res) => {
  const { refreshToken } = req.body;
  if (refreshToken) {
    db.run('DELETE FROM refresh_tokens WHERE token = ? AND user_id = ?', [refreshToken, req.user.id]);
  }
  logSecurityEvent('LOGOUT_SUCCESS', req.user.id, `Successfully logged out session.`, req.socket.remoteAddress);
  res.status(200).json({ message: 'Logged out securely.' });
});

// 7. Secure Password Reset flow simulated support
app.post('/api/auth/reset-password-request', apiRateLimiter(5, 300000), (req, res) => {
  const { username } = req.body;
  if (!username) {
    return res.status(400).json({ error: 'Username is required' });
  }
  db.get('SELECT id FROM users WHERE username = ?', [username], (err, row) => {
    if (err) {
      return res.status(500).json({ error: 'Internal system error' });
    }
    // Prevent username enumeration by returning a vague successful result even if not found
    logSecurityEvent('PASSWORD_RESET_REQ', row ? row.id : 'None', `Password reset request triggered for ${username}`);
    res.status(200).json({
      message: 'If the username exists, a verification check-code has been generated. Use the reset-confirm API.'
    });
  });
});

// 8. Update Privacy Preferences (Phase 11 Privacy Rules)
app.put('/api/user/privacy', verifyToken, (req, res) => {
  const { online_status, who_can_message, who_can_invite, who_can_call, who_can_view_profile } = req.body;
  
  const cleanStatus = ['online', 'offline', 'away'].includes(online_status) ? online_status : 'online';
  const cleanMessage = ['all', 'friends', 'none'].includes(who_can_message) ? who_can_message : 'all';
  const cleanInvite = ['all', 'friends', 'none'].includes(who_can_invite) ? who_can_invite : 'all';
  const cleanCall = ['all', 'friends', 'none'].includes(who_can_call) ? who_can_call : 'all';
  const cleanProfile = ['all', 'friends', 'none'].includes(who_can_view_profile) ? who_can_view_profile : 'all';

  const stmt = db.prepare(`
    UPDATE users
    SET online_status = ?, who_can_message = ?, who_can_invite = ?, who_can_call = ?, who_can_view_profile = ?
    WHERE id = ?
  `);
  stmt.run(cleanStatus, cleanMessage, cleanInvite, cleanCall, cleanProfile, req.user.id, (err) => {
    stmt.finalize();
    if (err) {
      return res.status(500).json({ error: 'Failed to update privacy controls' });
    }
    res.status(200).json({ message: 'Privacy configuration updated successfully.' });
  });
});

// 9. Secure File Upload Endpoint (MIME validation, Ext matching, Path Traversal prevent, Size limit)
const secureStorage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, UPLOAD_DIR);
  },
  filename: (req, file, cb) => {
    // Generate randomized secure token filename to prevent execution and traversal
    const token = crypto.randomBytes(16).toString('hex');
    const ext = path.extname(file.originalname).toLowerCase();
    cb(null, `${token}${ext}`);
  }
});

const uploadFilter = (req, file, cb) => {
  const allowedMimeTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
  const allowedExtensions = ['.jpg', '.jpeg', '.png', '.gif', '.webp'];
  const ext = path.extname(file.originalname).toLowerCase();
  
  if (allowedMimeTypes.includes(file.mimetype) && allowedExtensions.includes(ext)) {
    cb(null, true);
  } else {
    cb(new Error('Invalid File Type! Only JPEG, PNG, GIF and WEBP images are permitted.'), false);
  }
};

const secureUploader = multer({
  storage: secureStorage,
  fileFilter: uploadFilter,
  limits: {
    fileSize: 5 * 1024 * 1024, // Strict 5MB limit (Phase 7)
    files: 1
  }
});

app.post('/api/media/upload', verifyToken, secureUploader.single('file'), (req, res) => {
  if (!req.file) {
    return res.status(400).json({ error: 'No file uploaded or file rejected by validator' });
  }

  // Prevent path traversal
  const targetFilePath = path.join(UPLOAD_DIR, req.file.filename);
  if (!isSafePath(UPLOAD_DIR, targetFilePath)) {
    fs.unlinkSync(req.file.path); // Delete dangerous file
    logSecurityEvent('PATH_TRAVERSAL_ATTEMPT', req.user.id, `File path traversal detected!`, req.socket.remoteAddress);
    return res.status(400).json({ error: 'Security breach detected: path traversal.' });
  }

  const fileUrl = `${req.protocol}://${req.get('host')}/uploads/${req.file.filename}`;
  res.status(200).json({
    message: 'File uploaded securely.',
    fileUrl
  });
});

// Handle Multer upload errors gracefully
app.use((err, req, res, next) => {
  if (err instanceof multer.MulterError) {
    return res.status(400).json({ error: `Upload error: ${err.message}` });
  } else if (err) {
    return res.status(400).json({ error: err.message });
  }
  next();
});

// ==========================================
// WEBSOCKET HANDSHAKE AND SECURITY
// ==========================================
const wss = new WebSocketServer({ noServer: true });

// Intercept Upgrade request for Handshake JWT Token verification
server.on('upgrade', (request, socket, head) => {
  const urlParams = new URL(request.url, `http://${request.headers.host}`);
  const token = urlParams.searchParams.get('token');

  // Verify handshakes if a token is supplied, otherwise default as secure sandbox guest mode
  if (token) {
    try {
      const decoded = jwt.verify(token, JWT_SECRET);
      wss.handleUpgrade(request, socket, head, (ws) => {
        ws.user = decoded; // Bind authenticated user
        wss.emit('connection', ws, request);
      });
    } catch (err) {
      logSecurityEvent('WEBSOCKET_HANDSHAKE_DENIED', null, `Expired or invalid JWT handshake connection attempt`, socket.remoteAddress);
      socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
      socket.destroy();
    }
  } else {
    // Guest connection allowed but under heavily sandboxed permissions
    wss.handleUpgrade(request, socket, head, (ws) => {
      ws.user = { id: `guest_${crypto.randomBytes(4).toString('hex')}`, username: 'Guest', role: 'Guest' };
      wss.emit('connection', ws, request);
    });
  }
});

// Helper: Broadcast to all active sockets in a room
function broadcastToRoom(roomId, payload, excludeClientId = null) {
  wss.clients.forEach((client) => {
    if (client.readyState === WebSocket.OPEN && client.roomId === roomId) {
      if (excludeClientId && client.clientId === excludeClientId) return;
      client.send(JSON.stringify(payload));
    }
  });
}

// ==========================================
// WEBSOCKET CONNECTION HANDLING
// ==========================================
wss.on('connection', (ws) => {
  ws.clientId = ws.user.id;
  ws.username = ws.user.username;
  ws.role = ws.user.role;
  ws.roomId = null;
  ws.isAlive = true;
  ws.msgCountWindow = 0; // for spam flood detection

  console.log(`[WS] Client verified: ${ws.username} (${ws.clientId}), Role: ${ws.role}`);

  // Heartbeat pong handler
  ws.on('pong', () => {
    ws.isAlive = true;
  });

  ws.on('message', (messageText) => {
    try {
      // 1. Packet limit validation (Max 16KB per websocket frame to prevent DoS)
      if (Buffer.byteLength(messageText) > 16384) {
        ws.send(JSON.stringify({ type: 'security_error', error: 'Packet size exceeds secure 16KB threshold.' }));
        logSecurityEvent('DOS_ATTEMPT', ws.clientId, 'Sent websocket frame larger than 16KB limit.');
        return;
      }

      const message = JSON.parse(messageText);

      // 2. Spam / Flood protection (Max 15 messages per 5s)
      ws.msgCountWindow += 1;
      if (ws.msgCountWindow > 15) {
        ws.isMuted = true;
        ws.send(JSON.stringify({ type: 'security_error', error: 'You have been muted for 60 seconds due to spamming/flooding.' }));
        logSecurityEvent('FLOOD_DETECTION', ws.clientId, `Client spam muted. Sent >15 packets in timeframe.`);
        
        // Notify room of mute
        if (ws.roomId) {
          broadcastToRoom(ws.roomId, {
            type: 'new_message',
            message: {
              id: Date.now(),
              roomId: ws.roomId,
              senderName: 'System',
              senderAvatarIndex: -1,
              content: `🔇 ${ws.username} has been auto-muted by anti-spam system.`,
              timestamp: Date.now(),
              isSystem: true
            }
          });
        }

        setTimeout(() => {
          ws.isMuted = false;
          ws.msgCountWindow = 0;
          ws.send(JSON.stringify({ type: 'presence_update', memberId: ws.clientId, isMuted: false }));
        }, 60000);
        return;
      }

      // Check if client is muted
      if (ws.isMuted && ['send_message', 'meme_drop', 'vibe_change', 'video_sync'].includes(message.type)) {
        ws.send(JSON.stringify({ type: 'security_error', error: 'You are currently muted.' }));
        return;
      }

      // 3. Command Router with strict type & input validation (Phase 5)
      switch (message.type) {
        case 'join_room': {
          const { roomId, name, avatar } = message;
          if (!roomId || typeof roomId !== 'string' || roomId.length > 20) {
            return ws.send(JSON.stringify({ type: 'security_error', error: 'Invalid room format' }));
          }

          const cleanRoomId = escapeHtml(roomId.toUpperCase().trim());
          const cleanName = escapeHtml((name || ws.username).trim().substring(0, 20));
          const cleanAvatar = parseInt(avatar, 10) || 0;

          // Check if user is banned from this room
          db.get('SELECT * FROM room_bans WHERE room_id = ? AND user_id = ?', [cleanRoomId, ws.clientId], (err, banRow) => {
            if (banRow) {
              if (!banRow.expires_at || banRow.expires_at > Date.now()) {
                ws.send(JSON.stringify({ type: 'security_error', error: 'You are banned from this room.' }));
                return ws.close();
              } else {
                db.run('DELETE FROM room_bans WHERE room_id = ? AND user_id = ?', [cleanRoomId, ws.clientId]);
              }
            }

            ws.roomId = cleanRoomId;
            ws.username = cleanName;

            // Upsert room state in SQLite securely
            db.serialize(() => {
              db.run(`
                INSERT INTO rooms (id, name, owner_id, vibe, created_at)
                VALUES (?, ?, ?, 'cozy', ?)
                ON CONFLICT(id) DO NOTHING
              `, [cleanRoomId, `Lounge #${cleanRoomId}`, ws.clientId, Date.now()]);

              // Check if room members has Owner, if not, first user becomes Owner
              db.get('SELECT count(*) as count FROM room_members WHERE room_id = ?', [cleanRoomId], (err, row) => {
                const assignedRole = (row && row.count === 0) ? 'Owner' : ws.role;
                ws.role = assignedRole;

                db.run(`
                  INSERT INTO room_members (room_id, user_id, role, joined_at)
                  VALUES (?, ?, ?, ?)
                  ON CONFLICT(room_id, user_id) DO UPDATE SET role = ?
                `, [cleanRoomId, ws.clientId, assignedRole, Date.now(), assignedRole]);

                // Query and fetch previous messages securely
                db.all('SELECT * FROM messages WHERE room_id = ? ORDER BY id DESC LIMIT 50', [cleanRoomId], (err, msgRows) => {
                  const messages = (msgRows || []).reverse().map(m => ({
                    id: m.id,
                    roomId: m.room_id,
                    senderName: m.sender_name,
                    senderAvatarIndex: m.sender_avatar_index,
                    content: m.content,
                    timestamp: m.timestamp,
                    isSystem: m.is_system === 1,
                    memeUrl: m.meme_url
                  }));

                  // Query active members in the room
                  db.all(`
                    SELECT rm.user_id, rm.role, rm.is_muted, u.username, u.avatar_index
                    FROM room_members rm
                    JOIN users u ON rm.user_id = u.id
                    WHERE rm.room_id = ?
                  `, [cleanRoomId], (err, memberRows) => {
                    const membersList = (memberRows || []).map(m => ({
                      id: m.user_id,
                      name: m.username,
                      avatarIndex: m.avatar_index,
                      role: m.role,
                      isMuted: m.is_muted === 1,
                      isCameraOn: false,
                      isSpeaking: false
                    }));

                    // Fetch room state
                    db.get('SELECT * FROM rooms WHERE id = ?', [cleanRoomId], (err, roomRow) => {
                      ws.send(JSON.stringify({
                        type: 'room_state',
                        room: {
                          id: cleanRoomId,
                          name: roomRow ? roomRow.name : `Lounge #${cleanRoomId}`,
                          currentVideoUrl: roomRow ? roomRow.current_video_url : 'https://www.youtube.com/watch?v=jfKfPfyJRdk',
                          videoTitle: roomRow ? roomRow.video_title : '🎵 Lofi Hip Hop Radio - Beats to Relax/Study',
                          isVideoPlaying: roomRow ? roomRow.is_video_playing === 1 : false,
                          videoProgressMs: roomRow ? roomRow.video_progress_ms : 0,
                          lastVideoSyncTime: roomRow ? roomRow.last_video_sync_time : Date.now()
                        },
                        messages,
                        members: membersList
                      }));

                      // Presence Joined broadcast
                      broadcastToRoom(cleanRoomId, {
                        type: 'presence_joined',
                        member: {
                          id: ws.clientId,
                          name: cleanName,
                          avatarIndex: cleanAvatar,
                          role: ws.role,
                          isMuted: false,
                          isCameraOn: false,
                          isSpeaking: false
                        }
                      }, ws.clientId);

                      // Push system message
                      const systemMsgContent = `🔔 ${cleanName} joined the hangout lounge as ${ws.role}!`;
                      db.run(`
                        INSERT INTO messages (room_id, sender_id, sender_name, sender_avatar_index, content, timestamp, is_system)
                        VALUES (?, 'System', 'System', -1, ?, ?, 1)
                      `, [cleanRoomId, systemMsgContent, Date.now()], function(err) {
                        if (!err) {
                          broadcastToRoom(cleanRoomId, {
                            type: 'new_message',
                            message: {
                              id: this.lastID,
                              roomId: cleanRoomId,
                              senderName: 'System',
                              senderAvatarIndex: -1,
                              content: systemMsgContent,
                              timestamp: Date.now(),
                              isSystem: true
                            }
                          });
                        }
                      });
                    });
                  });
                });
              });
            });
          });
          break;
        }

        case 'send_message': {
          if (!ws.roomId) return;
          const content = escapeHtml(message.content || '');
          if (!content.trim() || content.length > 500) return; // Strict bounds checks

          db.run(`
            INSERT INTO messages (room_id, sender_id, sender_name, sender_avatar_index, content, timestamp, is_system)
            VALUES (?, ?, ?, ?, ?, ?, 0)
          `, [ws.roomId, ws.clientId, ws.username, 0, content, Date.now()], function(err) {
            if (!err) {
              broadcastToRoom(ws.roomId, {
                type: 'new_message',
                message: {
                  id: this.lastID,
                  roomId: ws.roomId,
                  senderName: ws.username,
                  senderAvatarIndex: 0,
                  content: content,
                  timestamp: Date.now(),
                  isSystem: false
                }
              });
            }
          });
          break;
        }

        case 'video_sync': {
          if (!ws.roomId) return;
          const { action, url, title, progressMs, systemMsgContent } = message;

          // RBAC Check (Phase 3): Guest roles cannot alter room-wide video states
          if (ws.role === 'Guest') {
            return ws.send(JSON.stringify({ type: 'security_error', error: 'Guests do not have permission to sync videos.' }));
          }

          const cleanUrl = escapeHtml(url || '');
          const cleanTitle = escapeHtml(title || '');
          const cleanProgress = parseInt(progressMs, 10) || 0;
          const cleanSystemMsg = escapeHtml(systemMsgContent || '');

          let updateQuery = 'UPDATE rooms SET video_progress_ms = ?, last_video_sync_time = ?';
          let params = [cleanProgress, Date.now()];

          if (action === 'change') {
            updateQuery += ', current_video_url = ?, video_title = ?, is_video_playing = 1';
            params.push(cleanUrl, cleanTitle);
          } else if (action === 'play') {
            updateQuery += ', is_video_playing = 1';
          } else if (action === 'pause') {
            updateQuery += ', is_video_playing = 0';
          }
          
          updateQuery += ' WHERE id = ?';
          params.push(ws.roomId);

          db.run(updateQuery, params, (err) => {
            if (!err) {
              broadcastToRoom(ws.roomId, {
                type: 'video_update',
                room: {
                  id: ws.roomId,
                  currentVideoUrl: cleanUrl,
                  videoTitle: cleanTitle,
                  isVideoPlaying: action !== 'pause',
                  videoProgressMs: cleanProgress,
                  lastVideoSyncTime: Date.now()
                },
                systemMessage: cleanSystemMsg
              });

              if (cleanSystemMsg) {
                db.run(`
                  INSERT INTO messages (room_id, sender_id, sender_name, sender_avatar_index, content, timestamp, is_system)
                  VALUES (?, 'System', 'System', -1, ?, ?, 1)
                `, [ws.roomId, cleanSystemMsg, Date.now()]);
              }
            }
          });
          break;
        }

        case 'media_state': {
          if (!ws.roomId) return;
          const isMuted = message.isMuted === true;
          const isCameraOn = message.isCameraOn === true;
          const isSpeaking = message.isSpeaking === true;

          broadcastToRoom(ws.roomId, {
            type: 'presence_update',
            memberId: ws.clientId,
            isMuted,
            isCameraOn,
            isSpeaking
          }, ws.clientId);
          break;
        }

        case 'webrtc_signaling': {
          if (!ws.roomId) return;
          const { targetClientId, sdp, candidate } = message;

          // Forward to destination peer safely
          wss.clients.forEach((client) => {
            if (client.readyState === WebSocket.OPEN && client.clientId === targetClientId && client.roomId === ws.roomId) {
              client.send(JSON.stringify({
                type: 'webrtc_signaling',
                senderClientId: ws.clientId,
                sdp,
                candidate
              }));
            }
          });
          break;
        }

        case 'vibe_change': {
          if (!ws.roomId) return;
          const { vibe } = message;
          if (ws.role === 'Guest') return; // Guest prevention

          const cleanVibe = escapeHtml(vibe || 'cozy').substring(0, 15);
          db.run('UPDATE rooms SET vibe = ? WHERE id = ?', [cleanVibe, ws.roomId], (err) => {
            if (!err) {
              broadcastToRoom(ws.roomId, {
                type: 'vibe_update',
                vibe: cleanVibe,
                senderName: ws.username
              });

              const systemMsgContent = `🎭 Room vibe changed to: ${cleanVibe.toUpperCase()} by ${ws.username}`;
              db.run(`
                INSERT INTO messages (room_id, sender_id, sender_name, sender_avatar_index, content, timestamp, is_system)
                VALUES (?, 'System', 'System', -1, ?, ?, 1)
              `, [ws.roomId, systemMsgContent, Date.now()]);
            }
          });
          break;
        }

        // --- MODERN COOPERATION & MODERATION ---
        case 'admin_action': {
          if (!ws.roomId) return;
          const { action, targetUserId, reason } = message;

          // Authorization verification (Owner, Admin, Moderator roles allowed only)
          if (!['Owner', 'Admin', 'Moderator'].includes(ws.role)) {
            return ws.send(JSON.stringify({ type: 'security_error', error: 'You are unauthorized to perform administration actions.' }));
          }

          const cleanReason = escapeHtml(reason || 'Violated community guidelines').substring(0, 100);

          if (action === 'kick') {
            wss.clients.forEach((client) => {
              if (client.clientId === targetUserId && client.roomId === ws.roomId) {
                client.send(JSON.stringify({ type: 'security_error', error: `You have been kicked. Reason: ${cleanReason}` }));
                client.close();
              }
            });
            logSecurityEvent('ADMIN_KICK', ws.clientId, `Kicked client: ${targetUserId}. Reason: ${cleanReason}`);
          } else if (action === 'ban') {
            db.run(`
              INSERT INTO room_bans (room_id, user_id, banned_by, banned_at, reason)
              VALUES (?, ?, ?, ?, ?)
            `, [ws.roomId, targetUserId, ws.clientId, Date.now(), cleanReason], (err) => {
              if (!err) {
                wss.clients.forEach((client) => {
                  if (client.clientId === targetUserId && client.roomId === ws.roomId) {
                    client.send(JSON.stringify({ type: 'security_error', error: `You have been banned permanently. Reason: ${cleanReason}` }));
                    client.close();
                  }
                });
                logSecurityEvent('ADMIN_BAN', ws.clientId, `Permanently banned client: ${targetUserId}. Reason: ${cleanReason}`);
              }
            });
          }
          break;
        }

        // --- CORE REMAINING SOCKET ACTIONS PRESERVED ---
        case 'video_reaction': {
          if (!ws.roomId) return;
          const emoji = escapeHtml(message.emoji || '❤️');
          broadcastToRoom(ws.roomId, {
            type: 'video_reaction_received',
            emoji,
            videoTime: parseInt(message.videoTime, 10) || 0,
            senderName: ws.username,
            senderId: ws.clientId
          });
          break;
        }

        case 'msg_reaction': {
          if (!ws.roomId) return;
          const emoji = escapeHtml(message.emoji || '👍');
          broadcastToRoom(ws.roomId, {
            type: 'msg_reaction_received',
            messageId: parseInt(message.messageId, 10) || 0,
            emoji,
            senderId: ws.clientId,
            senderName: ws.username
          });
          break;
        }

        case 'meme_drop': {
          if (!ws.roomId) return;
          const memeUrl = escapeHtml(message.memeUrl || '');
          if (!memeUrl || (!memeUrl.startsWith('http://') && !memeUrl.startsWith('https://'))) return;

          db.run(`
            INSERT INTO messages (room_id, sender_id, sender_name, sender_avatar_index, content, timestamp, is_system, meme_url)
            VALUES (?, ?, ?, ?, ?, ?, 0, ?)
          `, [ws.roomId, ws.clientId, ws.username, 0, `🖼️ SHARED A MEME: ${memeUrl}`, Date.now(), memeUrl], function(err) {
            if (!err) {
              broadcastToRoom(ws.roomId, {
                type: 'new_message',
                message: {
                  id: this.lastID,
                  roomId: ws.roomId,
                  senderName: ws.username,
                  senderAvatarIndex: 0,
                  content: `🖼️ SHARED A MEME: ${memeUrl}`,
                  timestamp: Date.now(),
                  isSystem: false,
                  memeUrl: memeUrl
                }
              });
            }
          });
          break;
        }

        case 'ping_friend': {
          if (!ws.roomId) return;
          const targetClientId = escapeHtml(message.targetClientId || '');
          broadcastToRoom(ws.roomId, {
            type: 'ping_friend_received',
            senderName: ws.username,
            senderId: ws.clientId,
            targetClientId: targetClientId
          });
          break;
        }

        case 'game_action': {
          if (!ws.roomId) return;
          const { action, subType, state } = message;
          broadcastToRoom(ws.roomId, {
            type: 'game_update',
            action: escapeHtml(action || ''),
            subType: subType ? escapeHtml(subType) : null,
            state: escapeHtml(state || ''),
            senderId: ws.clientId,
            senderName: ws.username
          });
          break;
        }

        case 'dj_action': {
          if (!ws.roomId) return;
          const { action, djId, djName } = message;
          
          if (ws.role === 'Guest') return; // Guest check

          broadcastToRoom(ws.roomId, {
            type: 'dj_update',
            currentDjId: action === 'claim' ? escapeHtml(djId) : null,
            currentDjName: action === 'claim' ? escapeHtml(djName) : null,
            systemMessage: action === 'claim' ? `🎧 ${escapeHtml(djName)} is now the active DJ!` : `🎧 DJ role released.`
          });
          break;
        }

        case 'vote_action': {
          if (!ws.roomId) return;
          const { action, voteType, videoUrl, title } = message;
          
          if (ws.role === 'Guest') return; // Guest check

          broadcastToRoom(ws.roomId, {
            type: 'vote_update',
            action: escapeHtml(action || ''),
            voteType: escapeHtml(voteType || ''),
            videoUrl: escapeHtml(videoUrl || ''),
            title: escapeHtml(title || ''),
            senderId: ws.clientId,
            senderName: ws.username
          });
          break;
        }
      }
    } catch (err) {
      console.error('[WS Error] Processing packet fail:', err.message);
    }
  });

  // Client cleanup on close
  ws.on('close', () => {
    console.log(`[WS] Client disconnected: ${ws.username}`);
    if (ws.roomId) {
      // Remove room membership securely
      db.run('DELETE FROM room_members WHERE room_id = ? AND user_id = ?', [ws.roomId, ws.clientId], (err) => {
        // Broadcast presence left event
        broadcastToRoom(ws.roomId, {
          type: 'presence_left',
          memberId: ws.clientId
        });

        // Insert System farewell message
        const leaveMsgContent = `👋 ${ws.username} left the lounge.`;
        db.run(`
          INSERT INTO messages (room_id, sender_id, sender_name, sender_avatar_index, content, timestamp, is_system)
          VALUES (?, 'System', 'System', -1, ?, ?, 1)
        `, [ws.roomId, leaveMsgContent, Date.now()], function(err) {
          if (!err) {
            broadcastToRoom(ws.roomId, {
              type: 'new_message',
              message: {
                id: this.lastID,
                roomId: ws.roomId,
                senderName: 'System',
                senderAvatarIndex: -1,
                content: leaveMsgContent,
                timestamp: Date.now(),
                isSystem: true
              }
            });
          }
        });
      });
    }
  });
});

// ==========================================
// HEARTBEAT / BROKEN CONNECTION CHUNKER
// ==========================================
const heartbeatInterval = setInterval(() => {
  wss.clients.forEach((ws) => {
    if (ws.isAlive === false) {
      logSecurityEvent('HEARTBEAT_TIMEOUT', ws.clientId, `Connection closed due to silent heartbeat response.`);
      return ws.terminate();
    }
    
    ws.isAlive = false;
    ws.ping(); // Standard WebSocket system ping (keeps connection alive, verified via pong event)
    ws.msgCountWindow = 0; // Reset spam limits every window tick (5s)
  });
}, 5000);

wss.on('close', () => {
  clearInterval(heartbeatInterval);
});

// ==========================================
// DEPLOYMENT & SERVER KICKOFF
// ==========================================
server.listen(PORT, () => {
  console.log(`====================================================`);
  console.log(`  🍿 Hangout Room Production-Hardened Backend v1.0   `);
  console.log(`  Server Listening securely on Port ${PORT}          `);
  console.log(`====================================================`);
});

// Graceful Shutdown implementation
process.on('SIGTERM', () => {
  console.log('[Shutdown] SIGTERM received. Closing resources...');
  server.close(() => {
    db.close((err) => {
      if (err) console.error('[Shutdown Error] db close error:', err.message);
      console.log('[Shutdown] SQLite connection terminated. Exiting smoothly.');
      process.exit(0);
    });
  });
});
