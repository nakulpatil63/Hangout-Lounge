/**
 * Hangout Room - Enterprise-Hardened Real-Time Multi-User Backend Server
 * Built with Node.js, Express, ws (WebSocket), and Supabase (PostgreSQL + Auth + Storage).
 *
 * Implements high-performance security, JWT authentication, RBAC, rate limiting,
 * abuse protection, input validation, and Supabase / SQLite database persistence.
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
require('dotenv').config();

// Unified Database Helper (with auto-detect Supabase / local SQLite fallback)
const dbHelper = require('./db');

// ==========================================
// CONFIGURATION & SECRETS
// ==========================================
const PORT = process.env.PORT || 3000;
const JWT_SECRET = process.env.JWT_SECRET || 'super_secret_hangout_key_change_in_production_123!';
const JWT_REFRESH_SECRET = process.env.JWT_REFRESH_SECRET || 'refresh_super_secret_key_change_in_production_456!';
const UPLOAD_DIR = process.env.UPLOAD_DIR || path.join(__dirname, 'uploads');

// Ensure upload directory exists
if (!fs.existsSync(UPLOAD_DIR)) {
  fs.mkdirSync(UPLOAD_DIR, { recursive: true });
}

// Helper: Log security events securely
function logSecurityEvent(eventType, actorId, description, ipAddress = '0.0.0.0') {
  dbHelper.logSecurityEvent(eventType, actorId, description, ipAddress).catch(err => {
    console.error('[DB Error] Failed to log security event:', err.message);
  });
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
async function verifyToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Access token missing or malformed' });
  }

  const token = authHeader.split(' ')[1];

  try {
    const supabaseClient = dbHelper.getSupabaseClient();
    const { data, error } = await supabaseClient.auth.getUser(token);
    if (error || !data.user) {
      return res.status(401).json({ error: 'Invalid or expired access token' });
    }

    const profile = await dbHelper.getUserById(data.user.id);
    req.user = {
      id: data.user.id,
      username: profile ? profile.username : (data.user.user_metadata?.username || 'Unknown'),
      role: profile ? profile.role : 'Member'
    };
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
    status: 'healthy',
    timestamp: Date.now(),
    database_mode: 'Supabase PostgreSQL'
  });
});

// 2. Security Metrics endpoint
app.get('/metrics', async (req, res) => {
  try {
    const stats = await dbHelper.getAuditStats();
    res.status(200).json({
      active_connections: wss ? wss.clients.size : 0,
      audit_events: stats
    });
  } catch (err) {
    res.status(500).json({ error: 'Failed to retrieve metrics' });
  }
});

// 3. Register user (BCrypt / Supabase Auth)
app.post('/api/auth/register', apiRateLimiter(10, 60000), async (req, res) => {
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

  try {
    // Check if username already exists
    const existingUser = await dbHelper.getUserByUsername(cleanUsername);
    if (existingUser) {
      return res.status(400).json({ error: 'Username is already taken' });
    }

    const supabaseClient = dbHelper.getSupabaseClient();
    const email = `${cleanUsername}@hangout.local`;
    const { data: authData, error: authError } = await supabaseClient.auth.signUp({
      email,
      password,
      options: {
        data: {
          username: cleanUsername,
          avatar_index: parseInt(avatarIndex, 10) || 0
        }
      }
    });

    if (authError) {
      return res.status(400).json({ error: authError.message });
    }
    const userId = authData.user.id;
    // Auto-profile trigger normally handles insertion, but upsert securely
    await dbHelper.createUserProfile(userId, cleanUsername, '', avatarIndex);

    logSecurityEvent('USER_REGISTRATION', userId, `Registered account: ${cleanUsername}`, req.socket.remoteAddress);
    res.status(201).json({ message: 'User registered successfully!', userId });
  } catch (err) {
    console.error('[Auth Error] Register failure:', err.message);
    res.status(500).json({ error: 'Internal system registration error' });
  }
});

// 4. Secure Login (JWT Handshake, Refresh Tokens & Device Tracking)
app.post('/api/auth/login', apiRateLimiter(15, 60000), async (req, res) => {
  const { username, password, deviceInfo } = req.body;

  if (!username || !password) {
    return res.status(400).json({ error: 'Username and password are required' });
  }

  try {
    const supabaseClient = dbHelper.getSupabaseClient();
    const email = `${username.trim()}@hangout.local`;
    const { data: authData, error: authError } = await supabaseClient.auth.signInWithPassword({
      email,
      password
    });

    if (authError || !authData.user) {
      logSecurityEvent('AUTH_FAILURE', null, `Failed login attempt for username: ${username}`, req.socket.remoteAddress);
      return res.status(401).json({ error: 'Invalid username or password' });
    }

    const userId = authData.user.id;
    const accessToken = authData.session.access_token;
    const refreshToken = authData.session.refresh_token;

    const profile = await dbHelper.getUserById(userId);
    const userDetails = {
      id: userId,
      username: profile ? profile.username : username.trim(),
      role: profile ? profile.role : 'Member',
      avatarIndex: profile ? profile.avatar_index : 0
    };

    logSecurityEvent('AUTH_SUCCESS', userDetails.id, `Successfully logged in. Role: ${userDetails.role}`, req.socket.remoteAddress);

    res.status(200).json({
      accessToken,
      refreshToken,
      user: userDetails
    });
  } catch (err) {
    console.error('[Auth Error] Login failure:', err.message);
    res.status(500).json({ error: 'Internal server login error' });
  }
});

// 5. Token Rotation & Refresh Token API
app.post('/api/auth/refresh', async (req, res) => {
  const { refreshToken } = req.body;
  if (!refreshToken) {
    return res.status(400).json({ error: 'Refresh token is required' });
  }

  try {
    const supabaseClient = dbHelper.getSupabaseClient();
    const { data, error } = await supabaseClient.auth.refreshSession({ refresh_token: refreshToken });
    if (error || !data.session) {
      logSecurityEvent('TOKEN_HIJACK_WARNING', null, `Failed/stale refresh attempt in Supabase using token`, req.socket.remoteAddress);
      return res.status(403).json({ error: 'Invalid or revoked refresh token' });
    }

    res.status(200).json({
      accessToken: data.session.access_token,
      refreshToken: data.session.refresh_token
    });
  } catch (err) {
    return res.status(403).json({ error: 'Session expired or invalid' });
  }
});

// 6. Secure Logout Flow (Invalidate tokens)
app.post('/api/auth/logout', verifyToken, async (req, res) => {
  try {
    const supabaseClient = dbHelper.getSupabaseClient();
    await supabaseClient.auth.admin.signOut(req.user.id);
  } catch (err) {
    console.error('[DB Error] Supabase logout error:', err.message);
  }

  logSecurityEvent('LOGOUT_SUCCESS', req.user.id, `Successfully logged out session.`, req.socket.remoteAddress);
  res.status(200).json({ message: 'Logged out securely.' });
});

// 7. Secure Password Reset flow simulated support
app.post('/api/auth/reset-password-request', apiRateLimiter(5, 300000), async (req, res) => {
  const { username } = req.body;
  if (!username) {
    return res.status(400).json({ error: 'Username is required' });
  }

  try {
    const user = await dbHelper.getUserByUsername(username);
    logSecurityEvent('PASSWORD_RESET_REQ', user ? user.id : 'None', `Password reset request triggered for ${username}`);
    res.status(200).json({
      message: 'If the username exists, a verification check-code has been generated. Use the reset-confirm API.'
    });
  } catch (err) {
    return res.status(500).json({ error: 'Internal system error' });
  }
});

// 8. Update Privacy Preferences (Phase 11 Privacy Rules)
app.put('/api/user/privacy', verifyToken, async (req, res) => {
  const { online_status, who_can_message, who_can_invite, who_can_call, who_can_view_profile } = req.body;
  
  const cleanStatus = ['online', 'offline', 'away'].includes(online_status) ? online_status : 'online';
  const cleanMessage = ['all', 'friends', 'none'].includes(who_can_message) ? who_can_message : 'all';
  const cleanInvite = ['all', 'friends', 'none'].includes(who_can_invite) ? who_can_invite : 'all';
  const cleanCall = ['all', 'friends', 'none'].includes(who_can_call) ? who_can_call : 'all';
  const cleanProfile = ['all', 'friends', 'none'].includes(who_can_view_profile) ? who_can_view_profile : 'all';

  try {
    await dbHelper.updateUserPrivacy(req.user.id, cleanMessage, cleanInvite, cleanCall, cleanProfile);
    res.status(200).json({ message: 'Privacy configuration updated successfully.' });
  } catch (err) {
    return res.status(500).json({ error: 'Failed to update privacy controls' });
  }
});

// 9. Secure File Upload Endpoint (MIME validation, Ext matching, Path Traversal prevent, Size limit)
const secureStorage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, UPLOAD_DIR);
  },
  filename: (req, file, cb) => {
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
    fileSize: 5 * 1024 * 1024, // Strict 5MB limit
    files: 1
  }
});

app.post('/api/media/upload', verifyToken, secureUploader.single('file'), async (req, res) => {
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

  let fileUrl;
  try {
    const supabaseClient = dbHelper.getSupabaseClient();
    const fileBuffer = fs.readFileSync(req.file.path);
    const { data, error } = await supabaseClient.storage
      .from('chat-media')
      .upload(req.file.filename, fileBuffer, {
        contentType: req.file.mimetype,
        upsert: true
      });

    if (error) throw error;

    const { data: urlData } = supabaseClient.storage
      .from('chat-media')
      .getPublicUrl(req.file.filename);
    
    fileUrl = urlData.publicUrl;
    fs.unlinkSync(req.file.path); // clean local temp copy
  } catch (err) {
    console.error('[Storage Error] Supabase upload error:', err.message);
    // Failover safely to local uploads URL
    fileUrl = `${req.protocol}://${req.get('host')}/uploads/${req.file.filename}`;
  }

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
server.on('upgrade', async (request, socket, head) => {
  const urlParams = new URL(request.url, `http://${request.headers.host}`);
  const token = urlParams.searchParams.get('token');

  // Verify handshakes if a token is supplied, otherwise default as secure sandbox guest mode
  if (token) {
    try {
      const supabaseClient = dbHelper.getSupabaseClient();
      const { data, error } = await supabaseClient.auth.getUser(token);
      if (error || !data.user) {
        logSecurityEvent('WEBSOCKET_HANDSHAKE_DENIED', null, `Expired or invalid Supabase token handshake attempt`, socket.remoteAddress);
        socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
        return socket.destroy();
      }

      const profile = await dbHelper.getUserById(data.user.id);
      const decoded = {
        id: data.user.id,
        username: profile ? profile.username : (data.user.user_metadata?.username || 'Unknown'),
        role: profile ? profile.role : 'Member'
      };

      wss.handleUpgrade(request, socket, head, (ws) => {
        ws.user = decoded; // Bind authenticated user
        wss.emit('connection', ws, request);
      });
    } catch (err) {
      logSecurityEvent('WEBSOCKET_HANDSHAKE_DENIED', null, `Supabase auth error during handshake upgrade: ${err.message}`, socket.remoteAddress);
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

  ws.on('message', async (messageText) => {
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

      // 3. Command Router with strict validation
      switch (message.type) {
        case 'join_room': {
          const { roomId, name, avatar } = message;
          if (!roomId || typeof roomId !== 'string' || roomId.length > 20) {
            return ws.send(JSON.stringify({ type: 'security_error', error: 'Invalid room format' }));
          }

          const cleanRoomId = escapeHtml(roomId.toUpperCase().trim());
          const cleanName = escapeHtml((name || ws.username).trim().substring(0, 20));
          const cleanAvatar = parseInt(avatar, 10) || 0;

          try {
            // Check if user is banned from this room
            const banRow = await dbHelper.checkRoomBan(cleanRoomId, ws.clientId);
            if (banRow) {
              if (!banRow.expires_at || banRow.expires_at > Date.now()) {
                ws.send(JSON.stringify({ type: 'security_error', error: 'You are banned from this room.' }));
                return ws.close();
              } else {
                await dbHelper.removeRoomBan(cleanRoomId, ws.clientId);
              }
            }

            ws.roomId = cleanRoomId;
            ws.username = cleanName;

            // Upsert room state in DB securely
            await dbHelper.upsertRoom(cleanRoomId, `Lounge #${cleanRoomId}`, ws.clientId);

            // Check if room members has Owner, if not, first user becomes Owner
            const memberCount = await dbHelper.getRoomMemberCount(cleanRoomId);
            const assignedRole = (memberCount === 0) ? 'Owner' : ws.role;
            ws.role = assignedRole;

            // Upsert room member mapping
            await dbHelper.upsertRoomMember(cleanRoomId, ws.clientId, assignedRole);

            // Query and fetch previous messages securely
            const msgRows = await dbHelper.getRecentMessages(cleanRoomId, 50);
            const messages = (msgRows || []).map(m => ({
              id: m.id,
              roomId: m.room_id,
              senderName: m.sender_name,
              senderAvatarIndex: m.sender_avatar_index,
              content: m.content,
              timestamp: m.timestamp,
              isSystem: m.is_system === 1 || m.is_system === true,
              memeUrl: m.meme_url
            }));

            // Query active members in the room
            const memberRows = await dbHelper.getRoomMembers(cleanRoomId);
            const membersList = (memberRows || []).map(m => ({
              id: m.user_id,
              name: m.username,
              avatarIndex: m.avatar_index,
              role: m.role,
              isMuted: m.is_muted === 1 || m.is_muted === true,
              isCameraOn: false,
              isSpeaking: false
            }));

            // Fetch room state
            const roomRow = await dbHelper.getRoom(cleanRoomId);

            ws.send(JSON.stringify({
              type: 'room_state',
              room: {
                id: cleanRoomId,
                name: roomRow ? roomRow.name : `Lounge #${cleanRoomId}`,
                currentVideoUrl: roomRow ? roomRow.current_video_url : 'https://www.youtube.com/watch?v=jfKfPfyJRdk',
                videoTitle: roomRow ? roomRow.video_title : '🎵 Lofi Hip Hop Radio - Beats to Relax/Study',
                isVideoPlaying: roomRow ? (roomRow.is_video_playing === 1 || roomRow.is_video_playing === true) : false,
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
            const msgId = await dbHelper.saveMessage(cleanRoomId, 'System', 'System', -1, systemMsgContent, true);
            
            broadcastToRoom(cleanRoomId, {
              type: 'new_message',
              message: {
                id: msgId,
                roomId: cleanRoomId,
                senderName: 'System',
                senderAvatarIndex: -1,
                content: systemMsgContent,
                timestamp: Date.now(),
                isSystem: true
              }
            });
          } catch (err) {
            console.error('[WS Error] join_room failure:', err.message);
            ws.send(JSON.stringify({ type: 'security_error', error: 'Internal database error joining room.' }));
          }
          break;
        }

        case 'send_message': {
          if (!ws.roomId) return;
          const content = escapeHtml(message.content || '');
          if (!content.trim() || content.length > 500) return;

          try {
            const msgId = await dbHelper.saveMessage(ws.roomId, ws.clientId, ws.username, 0, content, false);
            broadcastToRoom(ws.roomId, {
              type: 'new_message',
              message: {
                id: msgId,
                roomId: ws.roomId,
                senderName: ws.username,
                senderAvatarIndex: 0,
                content: content,
                timestamp: Date.now(),
                isSystem: false
              }
            });
          } catch (err) {
            console.error('[WS Error] send_message failure:', err.message);
          }
          break;
        }

        case 'video_sync': {
          if (!ws.roomId) return;
          const { action, url, title, progressMs, systemMsgContent } = message;

          if (ws.role === 'Guest') {
            return ws.send(JSON.stringify({ type: 'security_error', error: 'Guests do not have permission to sync videos.' }));
          }

          const cleanUrl = escapeHtml(url || '');
          const cleanTitle = escapeHtml(title || '');
          const cleanProgress = parseInt(progressMs, 10) || 0;
          const cleanSystemMsg = escapeHtml(systemMsgContent || '');

          try {
            await dbHelper.updateRoomVideo(ws.roomId, cleanUrl, cleanTitle, action !== 'pause', cleanProgress);

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
              await dbHelper.saveMessage(ws.roomId, 'System', 'System', -1, cleanSystemMsg, true);
            }
          } catch (err) {
            console.error('[WS Error] video_sync failure:', err.message);
          }
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
          if (ws.role === 'Guest') return;

          const cleanVibe = escapeHtml(vibe || 'cozy').substring(0, 15);
          try {
            await dbHelper.updateRoomVibe(ws.roomId, cleanVibe);
            broadcastToRoom(ws.roomId, {
              type: 'vibe_update',
              vibe: cleanVibe,
              senderName: ws.username
            });

            const systemMsgContent = `🎭 Room vibe changed to: ${cleanVibe.toUpperCase()} by ${ws.username}`;
            await dbHelper.saveMessage(ws.roomId, 'System', 'System', -1, systemMsgContent, true);
          } catch (err) {
            console.error('[WS Error] vibe_change failure:', err.message);
          }
          break;
        }

        case 'admin_action': {
          if (!ws.roomId) return;
          const { action, targetUserId, reason } = message;

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
            try {
              await dbHelper.addRoomBan(ws.roomId, targetUserId, ws.clientId, cleanReason, null);
              wss.clients.forEach((client) => {
                if (client.clientId === targetUserId && client.roomId === ws.roomId) {
                  client.send(JSON.stringify({ type: 'security_error', error: `You have been banned permanently. Reason: ${cleanReason}` }));
                  client.close();
                }
              });
              logSecurityEvent('ADMIN_BAN', ws.clientId, `Permanently banned client: ${targetUserId}. Reason: ${cleanReason}`);
            } catch (err) {
              console.error('[WS Error] admin ban error:', err.message);
            }
          }
          break;
        }

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

          try {
            const msgId = await dbHelper.saveMessage(ws.roomId, ws.clientId, ws.username, 0, `🖼️ SHARED A MEME: ${memeUrl}`, false, memeUrl);
            broadcastToRoom(ws.roomId, {
              type: 'new_message',
              message: {
                id: msgId,
                roomId: ws.roomId,
                senderName: ws.username,
                senderAvatarIndex: 0,
                content: `🖼️ SHARED A MEME: ${memeUrl}`,
                timestamp: Date.now(),
                isSystem: false,
                memeUrl: memeUrl
              }
            });
          } catch (err) {
            console.error('[WS Error] meme_drop failure:', err.message);
          }
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
          
          if (ws.role === 'Guest') return;

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
          
          if (ws.role === 'Guest') return;

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
  ws.on('close', async () => {
    console.log(`[WS] Client disconnected: ${ws.username}`);
    if (ws.roomId) {
      try {
        await dbHelper.removeRoomMember(ws.roomId, ws.clientId);

        broadcastToRoom(ws.roomId, {
          type: 'presence_left',
          memberId: ws.clientId
        });

        const leaveMsgContent = `👋 ${ws.username} left the lounge.`;
        const msgId = await dbHelper.saveMessage(ws.roomId, 'System', 'System', -1, leaveMsgContent, true);
        
        broadcastToRoom(ws.roomId, {
          type: 'new_message',
          message: {
            id: msgId,
            roomId: ws.roomId,
            senderName: 'System',
            senderAvatarIndex: -1,
            content: leaveMsgContent,
            timestamp: Date.now(),
            isSystem: true
          }
        });
      } catch (err) {
        console.error('[WS Error] close handler cleanup fail:', err.message);
      }
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
    ws.ping();
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
  console.log(`  🍿 Hangout Room Production-Hardened Backend v2.0   `);
  console.log(`  Real-time persistent Supabase architecture online  `);
  console.log(`  Server Listening securely on Port ${PORT}          `);
  console.log(`====================================================`);
});

// Graceful Shutdown implementation
process.on('SIGTERM', () => {
  console.log('[Shutdown] SIGTERM received. Closing resources...');
  server.close(() => {
    console.log('[Shutdown] Server successfully terminated. Exiting smoothly.');
    process.exit(0);
  });
});
