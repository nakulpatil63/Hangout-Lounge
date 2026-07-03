/**
 * Database Abstraction Layer for Hangout Rooms.
 * Powered fully and exclusively by Supabase PostgreSQL.
 * Provides a clean, Promise-based async API.
 */

const { createClient } = require('@supabase/supabase-js');

// Configs
const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_PUBLISHABLE_KEY = process.env.SUPABASE_PUBLISHABLE_KEY;
const SUPABASE_SECRET_KEY = process.env.SUPABASE_SECRET_KEY;

// Validation for required environment variables
const missingVars = [];
if (!SUPABASE_URL) missingVars.push('SUPABASE_URL');
if (!SUPABASE_PUBLISHABLE_KEY) missingVars.push('SUPABASE_PUBLISHABLE_KEY');
if (!SUPABASE_SECRET_KEY) missingVars.push('SUPABASE_SECRET_KEY');

if (missingVars.length > 0) {
  const errorMessage = `[DB Error] Missing required Supabase environment variables: ${missingVars.join(', ')}. Please configure them in your environment or .env file.`;
  console.error(errorMessage);
  throw new Error(errorMessage);
}

// We initialize the Supabase client using the Secret Key for administrative bypass
const supabase = createClient(SUPABASE_URL, SUPABASE_SECRET_KEY);

console.log('[DB] Supabase PostgreSQL client configured and initialized successfully.');

// ==========================================
// EXPORTED PROMISE-BASED METHODS
// ==========================================

module.exports = {
  isSupabase: () => true,
  getSupabaseClient: () => supabase,

  // --- Audit Logs ---
  logSecurityEvent: async (eventType, actorId, description, ipAddress = '0.0.0.0') => {
    console.log(`[AUDIT] [${eventType}] Actor: ${actorId || 'Anonymous'} - ${description}`);
    const timestamp = Date.now();

    const { error } = await supabase
      .from('audit_logs')
      .insert([{ event_type: eventType, actor_id: actorId, description, ip_address: ipAddress, timestamp }]);
    
    if (error) {
      console.error('[Supabase DB Error] logSecurityEvent:', error.message);
    }
  },

  getAuditStats: async () => {
    const { data, error } = await supabase
      .from('audit_logs')
      .select('event_type');
    
    if (error) throw error;
    
    const counts = {};
    data.forEach(row => {
      counts[row.event_type] = (counts[row.event_type] || 0) + 1;
    });
    return Object.entries(counts).map(([event_type, count]) => ({ event_type, count }));
  },

  // --- User Profiles ---
  getUserById: async (id) => {
    const { data, error } = await supabase
      .from('profiles')
      .select('*')
      .eq('id', id)
      .maybeSingle();
    
    if (error) throw error;
    return data;
  },

  getUserByUsername: async (username) => {
    const { data, error } = await supabase
      .from('profiles')
      .select('*')
      .eq('username', username)
      .maybeSingle();
    
    if (error) throw error;
    return data;
  },

  createUserProfile: async (id, username, passwordHash, avatarIndex) => {
    const { data, error } = await supabase
      .from('profiles')
      .upsert([{
        id,
        username,
        role: 'Member',
        avatar_index: avatarIndex,
        online_status: 'online',
        created_at: new Date().toISOString()
      }], { onConflict: 'id' })
      .select()
      .single();
    
    if (error) throw error;
    return data;
  },

  updateUserPrivacy: async (id, whoCanMessage, whoCanInvite, whoCanCall, whoCanViewProfile) => {
    const { error } = await supabase
      .from('profiles')
      .update({
        who_can_message: whoCanMessage,
        who_can_invite: whoCanInvite,
        who_can_call: whoCanCall,
        who_can_view_profile: whoCanViewProfile
      })
      .eq('id', id);
    
    if (error) throw error;
    return true;
  },

  // --- Rooms ---
  upsertRoom: async (id, name, ownerId) => {
    const timestamp = Date.now();
    const { data: existing, error: selectError } = await supabase
      .from('rooms')
      .select('id')
      .eq('id', id)
      .maybeSingle();
    
    if (selectError) throw selectError;
    
    if (!existing) {
      const { error } = await supabase
        .from('rooms')
        .insert([{
          id,
          name,
          owner_id: ownerId,
          vibe: 'cozy',
          created_at: timestamp
        }]);
      if (error) throw error;
    }
  },

  getRoom: async (id) => {
    const { data, error } = await supabase
      .from('rooms')
      .select('*')
      .eq('id', id)
      .maybeSingle();
    
    if (error) throw error;
    return data;
  },

  updateRoomVideo: async (id, videoUrl, videoTitle, isPlaying, progressMs) => {
    const syncTime = Date.now();
    const { error } = await supabase
      .from('rooms')
      .update({
        current_video_url: videoUrl,
        video_title: videoTitle,
        is_video_playing: isPlaying,
        video_progress_ms: progressMs,
        last_video_sync_time: syncTime
      })
      .eq('id', id);
    
    if (error) throw error;
  },

  updateRoomVibe: async (id, vibe) => {
    const { error } = await supabase
      .from('rooms')
      .update({ vibe })
      .eq('id', id);
    
    if (error) throw error;
  },

  // --- Room Members ---
  getRoomMemberCount: async (roomId) => {
    const { count, error } = await supabase
      .from('room_members')
      .select('*', { count: 'exact', head: true })
      .eq('room_id', roomId);
    
    if (error) throw error;
    return count || 0;
  },

  upsertRoomMember: async (roomId, userId, role) => {
    const timestamp = Date.now();
    const { data: existing, error: selectError } = await supabase
      .from('room_members')
      .select('*')
      .eq('room_id', roomId)
      .eq('user_id', userId)
      .maybeSingle();

    if (selectError) throw selectError;

    if (existing) {
      const { error } = await supabase
        .from('room_members')
        .update({ role })
        .eq('room_id', roomId)
        .eq('user_id', userId);
      if (error) throw error;
    } else {
      const { error } = await supabase
        .from('room_members')
        .insert([{ room_id: roomId, user_id: userId, role, joined_at: timestamp }]);
      if (error) throw error;
    }
  },

  getRoomMembers: async (roomId) => {
    const { data, error } = await supabase
      .from('room_members')
      .select(`
        user_id,
        role,
        is_muted,
        profiles (
          username,
          avatar_index
        )
      `)
      .eq('room_id', roomId);
    
    if (error) throw error;

    return data.map(m => ({
      user_id: m.user_id,
      role: m.role,
      is_muted: m.is_muted ? 1 : 0,
      username: m.profiles ? m.profiles.username : 'Unknown',
      avatar_index: m.profiles ? m.profiles.avatar_index : 0
    }));
  },

  removeRoomMember: async (roomId, userId) => {
    const { error } = await supabase
      .from('room_members')
      .delete()
      .eq('room_id', roomId)
      .eq('user_id', userId);
    
    if (error) throw error;
  },

  // --- Bans ---
  checkRoomBan: async (roomId, userId) => {
    const { data, error } = await supabase
      .from('room_bans')
      .select('*')
      .eq('room_id', roomId)
      .eq('user_id', userId)
      .maybeSingle();
    
    if (error) throw error;
    return data;
  },

  removeRoomBan: async (roomId, userId) => {
    const { error } = await supabase
      .from('room_bans')
      .delete()
      .eq('room_id', roomId)
      .eq('user_id', userId);
    
    if (error) throw error;
  },

  addRoomBan: async (roomId, userId, bannedBy, reason, expiresAt) => {
    const timestamp = Date.now();
    const { error } = await supabase
      .from('room_bans')
      .insert([{
        room_id: roomId,
        user_id: userId,
        banned_by: bannedBy,
        banned_at: timestamp,
        reason,
        expires_at: expiresAt
      }]);
    
    if (error) throw error;
  },

  // --- Messages ---
  getRecentMessages: async (roomId, limit = 50) => {
    const { data, error } = await supabase
      .from('messages')
      .select('*')
      .eq('room_id', roomId)
      .order('id', { ascending: false })
      .limit(limit);
    
    if (error) throw error;

    return data.reverse();
  },

  saveMessage: async (roomId, senderId, senderName, senderAvatarIndex, content, isSystem = false, memeUrl = '') => {
    const timestamp = Date.now();
    const { data, error } = await supabase
      .from('messages')
      .insert([{
        room_id: roomId,
        sender_id: senderId,
        sender_name: senderName,
        sender_avatar_index: senderAvatarIndex,
        content,
        timestamp,
        is_system: isSystem,
        meme_url: memeUrl
      }])
      .select()
      .single();
    
    if (error) throw error;
    return data.id;
  },

  // --- Refresh Tokens (Legacy placeholders for backward-compatibility) ---
  saveRefreshToken: async (token, userId, deviceInfo, expiresAt) => {},
  getRefreshToken: async (token) => null,
  deleteRefreshToken: async (token) => {},
  deleteUserRefreshToken: async (token, userId) => {}
};
