-- ============================================================================
-- SUPABASE PRODUCTION-READY SCHEMA FOR HANGOUT LOUNGE
-- Paste this script directly into the Supabase SQL Editor.
-- ============================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- 1. DATABASE FUNCTIONS & UTILITIES
-- ============================================================================

-- Function to automatically update the 'updated_at' column
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = timezone('utc'::text, now());
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- 2. TABLES DEFINITIONS (NORMALIZED & ROBUST)
-- ============================================================================

-- A. PROFILES (Extends Supabase Auth users)
CREATE TABLE IF NOT EXISTS public.profiles (
  id UUID REFERENCES auth.users ON DELETE CASCADE PRIMARY KEY,
  username TEXT UNIQUE NOT NULL,
  role TEXT NOT NULL DEFAULT 'Member', -- 'Member', 'Moderator', 'Admin', 'Owner'
  avatar_index INTEGER DEFAULT 0,
  is_online BOOLEAN DEFAULT false,
  last_seen TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  online_status TEXT DEFAULT 'online', -- 'online', 'idle', 'offline', 'dnd'
  who_can_message TEXT DEFAULT 'all', -- 'all', 'friends', 'none'
  who_can_invite TEXT DEFAULT 'all', -- 'all', 'friends', 'none'
  who_can_call TEXT DEFAULT 'all', -- 'all', 'friends', 'none'
  who_can_view_profile TEXT DEFAULT 'all', -- 'all', 'friends'
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- B. USER SETTINGS
CREATE TABLE IF NOT EXISTS public.user_settings (
  user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE PRIMARY KEY,
  theme TEXT NOT NULL DEFAULT 'dark',
  enable_notifications BOOLEAN NOT NULL DEFAULT true,
  enable_sound BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- C. USER DEVICES
CREATE TABLE IF NOT EXISTS public.user_devices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  device_name TEXT NOT NULL,
  fcm_token TEXT,
  last_active_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- D. USER PRESENCE
CREATE TABLE IF NOT EXISTS public.user_presence (
  user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE PRIMARY KEY,
  status TEXT NOT NULL DEFAULT 'offline', -- 'online', 'idle', 'offline', 'dnd'
  custom_status TEXT,
  last_active_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- E. FRIENDS
CREATE TABLE IF NOT EXISTS public.friends (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  friend_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending', -- 'pending', 'accepted', 'blocked'
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  UNIQUE (user_id, friend_id)
);

-- F. FRIEND_REQUESTS
CREATE TABLE IF NOT EXISTS public.friend_requests (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  sender_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  receiver_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending', -- 'pending', 'accepted', 'declined'
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  UNIQUE (sender_id, receiver_id)
);

-- G. ROOMS
CREATE TABLE IF NOT EXISTS public.rooms (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT,
  owner_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  current_video_url TEXT DEFAULT '',
  video_title TEXT DEFAULT '',
  is_video_playing BOOLEAN DEFAULT false,
  video_progress_ms INTEGER DEFAULT 0,
  last_video_sync_time BIGINT DEFAULT 0,
  vibe TEXT DEFAULT 'cozy',
  member_count INTEGER DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- H. ROOM_MEMBERS
CREATE TABLE IF NOT EXISTS public.room_members (
  room_id TEXT REFERENCES public.rooms(id) ON DELETE CASCADE NOT NULL,
  user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  role TEXT NOT NULL DEFAULT 'Member', -- 'Owner', 'Admin', 'Moderator', 'Member'
  joined_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  is_muted BOOLEAN DEFAULT false,
  is_speaking BOOLEAN DEFAULT false,
  PRIMARY KEY (room_id, user_id)
);

-- I. ROOM_BANS
CREATE TABLE IF NOT EXISTS public.room_bans (
  room_id TEXT REFERENCES public.rooms(id) ON DELETE CASCADE NOT NULL,
  user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  banned_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
  banned_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  reason TEXT,
  expires_at TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (room_id, user_id)
);

-- J. ROOM_ROLES (Allows custom role-based permissions per room)
CREATE TABLE IF NOT EXISTS public.room_roles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id TEXT REFERENCES public.rooms(id) ON DELETE CASCADE NOT NULL,
  role_name TEXT NOT NULL,
  can_invite BOOLEAN DEFAULT true,
  can_kick BOOLEAN DEFAULT false,
  can_ban BOOLEAN DEFAULT false,
  can_mute BOOLEAN DEFAULT false,
  can_control_player BOOLEAN DEFAULT false,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  UNIQUE (room_id, role_name)
);

-- K. MESSAGES
CREATE TABLE IF NOT EXISTS public.messages (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  room_id TEXT REFERENCES public.rooms(id) ON DELETE CASCADE NOT NULL,
  sender_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL, -- NULL indicates system/deleted user
  sender_name TEXT NOT NULL,
  sender_avatar_index INTEGER DEFAULT 0,
  content TEXT NOT NULL,
  is_system BOOLEAN DEFAULT false,
  meme_url TEXT DEFAULT '',
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- L. MESSAGE_REACTIONS
CREATE TABLE IF NOT EXISTS public.message_reactions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  message_id BIGINT REFERENCES public.messages(id) ON DELETE CASCADE NOT NULL,
  user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  emoji TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  UNIQUE (message_id, user_id, emoji)
);

-- M. MESSAGE_READS
CREATE TABLE IF NOT EXISTS public.message_reads (
  room_id TEXT REFERENCES public.rooms(id) ON DELETE CASCADE NOT NULL,
  user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  last_read_message_id BIGINT REFERENCES public.messages(id) ON DELETE CASCADE,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  PRIMARY KEY (room_id, user_id)
);

-- N. ATTACHMENTS
CREATE TABLE IF NOT EXISTS public.attachments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id BIGINT REFERENCES public.messages(id) ON DELETE CASCADE,
  room_id TEXT REFERENCES public.rooms(id) ON DELETE CASCADE NOT NULL,
  uploader_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
  file_name TEXT NOT NULL,
  file_type TEXT NOT NULL,
  file_size BIGINT NOT NULL,
  file_url TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- O. PINNED_MESSAGES
CREATE TABLE IF NOT EXISTS public.pinned_messages (
  room_id TEXT REFERENCES public.rooms(id) ON DELETE CASCADE NOT NULL,
  message_id BIGINT REFERENCES public.messages(id) ON DELETE CASCADE NOT NULL,
  pinned_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
  pinned_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  PRIMARY KEY (room_id, message_id)
);

-- P. NOTIFICATIONS
CREATE TABLE IF NOT EXISTS public.notifications (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  title TEXT NOT NULL,
  content TEXT NOT NULL,
  type TEXT NOT NULL DEFAULT 'general', -- 'friend_request', 'room_invite', 'mention', 'general'
  is_read BOOLEAN DEFAULT false,
  data JSONB DEFAULT '{}'::jsonb,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- Q. TYPING_STATUS
CREATE TABLE IF NOT EXISTS public.typing_status (
  room_id TEXT REFERENCES public.rooms(id) ON DELETE CASCADE NOT NULL,
  user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  is_typing BOOLEAN DEFAULT false,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  PRIMARY KEY (room_id, user_id)
);

-- R. VOICE_CALLS
CREATE TABLE IF NOT EXISTS public.voice_calls (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id TEXT REFERENCES public.rooms(id) ON DELETE CASCADE NOT NULL,
  host_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  status TEXT NOT NULL DEFAULT 'active', -- 'active', 'ended'
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  ended_at TIMESTAMP WITH TIME ZONE
);

-- S. VIDEO_CALLS
CREATE TABLE IF NOT EXISTS public.video_calls (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id TEXT REFERENCES public.rooms(id) ON DELETE CASCADE NOT NULL,
  host_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  status TEXT NOT NULL DEFAULT 'active', -- 'active', 'ended'
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  ended_at TIMESTAMP WITH TIME ZONE
);

-- T. WATCH_TOGETHER_SESSIONS
CREATE TABLE IF NOT EXISTS public.watch_together_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id TEXT REFERENCES public.rooms(id) ON DELETE CASCADE UNIQUE NOT NULL,
  video_url TEXT NOT NULL,
  video_title TEXT,
  is_playing BOOLEAN DEFAULT false,
  progress_ms INTEGER DEFAULT 0,
  updated_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- U. GAMES
CREATE TABLE IF NOT EXISTS public.games (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT,
  max_players INTEGER DEFAULT 4,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- V. GAME_SESSIONS
CREATE TABLE IF NOT EXISTS public.game_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  game_id TEXT REFERENCES public.games(id) ON DELETE CASCADE NOT NULL,
  room_id TEXT REFERENCES public.rooms(id) ON DELETE CASCADE NOT NULL,
  state JSONB DEFAULT '{}'::jsonb,
  status TEXT NOT NULL DEFAULT 'lobby', -- 'lobby', 'active', 'finished'
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- W. GAME_PLAYERS
CREATE TABLE IF NOT EXISTS public.game_players (
  session_id UUID REFERENCES public.game_sessions(id) ON DELETE CASCADE NOT NULL,
  user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  score INTEGER DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'joined', -- 'joined', 'spectator', 'left'
  joined_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
  PRIMARY KEY (session_id, user_id)
);

-- X. AUDIT_LOGS
CREATE TABLE IF NOT EXISTS public.audit_logs (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  event_type TEXT NOT NULL,
  actor_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
  description TEXT,
  ip_address TEXT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now())
);

-- ============================================================================
-- 3. AUTOMATIC UPDATED_AT TRIGGERS
-- ============================================================================

CREATE OR REPLACE TRIGGER update_profiles_updated_at BEFORE UPDATE ON public.profiles FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE OR REPLACE TRIGGER update_user_settings_updated_at BEFORE UPDATE ON public.user_settings FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE OR REPLACE TRIGGER update_friends_updated_at BEFORE UPDATE ON public.friends FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE OR REPLACE TRIGGER update_friend_requests_updated_at BEFORE UPDATE ON public.friend_requests FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE OR REPLACE TRIGGER update_rooms_updated_at BEFORE UPDATE ON public.rooms FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE OR REPLACE TRIGGER update_message_reads_updated_at BEFORE UPDATE ON public.message_reads FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE OR REPLACE TRIGGER update_typing_status_updated_at BEFORE UPDATE ON public.typing_status FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE OR REPLACE TRIGGER update_watch_together_sessions_updated_at BEFORE UPDATE ON public.watch_together_sessions FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
CREATE OR REPLACE TRIGGER update_game_sessions_updated_at BEFORE UPDATE ON public.game_sessions FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- ============================================================================
-- 4. COMPLEX DATABASE FUNCTIONS & TRIGGERS
-- ============================================================================

-- Function 1: Handle User Signup (Profiles, Settings, & Presence initialization)
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  -- 1. Create Profile
  INSERT INTO public.profiles (id, username, role, avatar_index, online_status)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'username', split_part(NEW.email, '@', 1)),
    'Member',
    COALESCE((NEW.raw_user_meta_data->>'avatar_index')::integer, 0),
    'online'
  )
  ON CONFLICT (id) DO NOTHING;

  -- 2. Create User Settings
  INSERT INTO public.user_settings (user_id, theme, enable_notifications, enable_sound)
  VALUES (NEW.id, 'dark', true, true)
  ON CONFLICT (user_id) DO NOTHING;

  -- 3. Create Presence
  INSERT INTO public.user_presence (user_id, status)
  VALUES (NEW.id, 'online')
  ON CONFLICT (user_id) DO NOTHING;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger to execute upon User Creation on auth.users table
CREATE OR REPLACE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();


-- Function 2: Update Room Member Counter
CREATE OR REPLACE FUNCTION public.update_room_member_count()
RETURNS TRIGGER AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    UPDATE public.rooms
    SET member_count = member_count + 1
    WHERE id = NEW.room_id;
  ELSIF TG_OP = 'DELETE' THEN
    UPDATE public.rooms
    SET member_count = GREATEST(0, member_count - 1)
    WHERE id = OLD.room_id;
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger on room_members table
CREATE OR REPLACE TRIGGER on_room_member_change
  AFTER INSERT OR DELETE ON public.room_members
  FOR EACH ROW EXECUTE FUNCTION public.update_room_member_count();


-- Function 3: Auto-generate Message Notifications (Mentions or System)
CREATE OR REPLACE FUNCTION public.handle_new_message_notification()
RETURNS TRIGGER AS $$
DECLARE
  room_name TEXT;
  recipient_id UUID;
BEGIN
  -- Retrieve Room Name
  SELECT name INTO room_name FROM public.rooms WHERE id = NEW.room_id;

  -- Notify other room members (except the sender itself)
  FOR recipient_id IN 
    SELECT user_id FROM public.room_members WHERE room_id = NEW.room_id AND user_id != NEW.sender_id
  LOOP
    -- Insert a notification entry
    INSERT INTO public.notifications (user_id, title, content, type, data)
    VALUES (
      recipient_id,
      'New Message in ' || COALESCE(room_name, 'Hangout Room'),
      NEW.sender_name || ': ' || substring(NEW.content from 1 for 60),
      'message',
      jsonb_build_object('room_id', NEW.room_id, 'message_id', NEW.id)
    );
  END LOOP;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger for message notification
CREATE OR REPLACE TRIGGER on_new_message_added
  AFTER INSERT ON public.messages
  FOR EACH ROW
  WHEN (NEW.is_system = false)
  EXECUTE FUNCTION public.handle_new_message_notification();

-- ============================================================================
-- 5. PERFORMANCE-OPTIMIZED INDEXES
-- ============================================================================

-- Chat History Lookups (Filtered by Room & ordered chronologically)
CREATE INDEX IF NOT EXISTS idx_messages_room_created_at ON public.messages(room_id, created_at DESC);

-- Unread Message Tracking Optimization
CREATE INDEX IF NOT EXISTS idx_message_reads_user_room ON public.message_reads(user_id, room_id);

-- Friend Relationship & Friend Request Search Lookups
CREATE INDEX IF NOT EXISTS idx_friends_user_status ON public.friends(user_id, status);
CREATE INDEX IF NOT EXISTS idx_friends_friend_status ON public.friends(friend_id, status);
CREATE INDEX IF NOT EXISTS idx_friend_requests_sender ON public.friend_requests(sender_id, status);
CREATE INDEX IF NOT EXISTS idx_friend_requests_receiver ON public.friend_requests(receiver_id, status);

-- Room Memberships Lookup
CREATE INDEX IF NOT EXISTS idx_room_members_user ON public.room_members(user_id);
CREATE INDEX IF NOT EXISTS idx_room_members_room ON public.room_members(room_id);

-- Active Voice/Video Calls in Room
CREATE INDEX IF NOT EXISTS idx_voice_calls_room_status ON public.voice_calls(room_id, status);
CREATE INDEX IF NOT EXISTS idx_video_calls_room_status ON public.video_calls(room_id, status);

-- Active Game Sessions & Players
CREATE INDEX IF NOT EXISTS idx_game_sessions_room ON public.game_sessions(room_id, status);
CREATE INDEX IF NOT EXISTS idx_game_players_session ON public.game_players(session_id);

-- Attachments and Notifications query acceleration
CREATE INDEX IF NOT EXISTS idx_attachments_room ON public.attachments(room_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON public.notifications(user_id) WHERE is_read = false;

-- Online Users query optimization
CREATE INDEX IF NOT EXISTS idx_profiles_online_status ON public.profiles(online_status) WHERE online_status = 'online';

-- ============================================================================
-- 6. SECURITY DEFINER HELPER FUNCTIONS (Bypass RLS Recursion)
-- ============================================================================

CREATE OR REPLACE FUNCTION public.sec_is_room_member(room_id_param TEXT, user_id_param UUID)
RETURNS BOOLEAN SECURITY DEFINER AS $$
BEGIN
  RETURN EXISTS (
    SELECT 1 FROM public.room_members
    WHERE room_id = room_id_param AND user_id = user_id_param
  );
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION public.sec_is_room_owner_or_admin(room_id_param TEXT, user_id_param UUID)
RETURNS BOOLEAN SECURITY DEFINER AS $$
BEGIN
  RETURN EXISTS (
    SELECT 1 FROM public.rooms
    WHERE id = room_id_param AND owner_id = user_id_param
  ) OR EXISTS (
    SELECT 1 FROM public.room_members
    WHERE room_id = room_id_param AND user_id = user_id_param AND role IN ('Owner', 'Admin')
  );
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- 7. ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================================================

-- Enable RLS for all production tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_presence ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.friends ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.friend_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_bans ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.message_reactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.message_reads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pinned_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.typing_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.voice_calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.video_calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.watch_together_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.games ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.game_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.game_players ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

-- --- Profiles Policies ---
CREATE POLICY "Allow public read access to profiles" ON public.profiles FOR SELECT USING (true);
CREATE POLICY "Allow owners to update their profile" ON public.profiles FOR UPDATE USING (auth.uid() = id);
CREATE POLICY "Allow service role full access on profiles" ON public.profiles FOR ALL USING (true);

-- --- User Settings Policies ---
CREATE POLICY "Allow users access to own settings" ON public.user_settings FOR ALL USING (auth.uid() = user_id);

-- --- User Devices Policies ---
CREATE POLICY "Allow users access to own devices" ON public.user_devices FOR ALL USING (auth.uid() = user_id);

-- --- User Presence Policies ---
CREATE POLICY "Allow public read access to presence" ON public.user_presence FOR SELECT USING (true);
CREATE POLICY "Allow owners to manage presence" ON public.user_presence FOR ALL USING (auth.uid() = user_id);

-- --- Friends Policies ---
CREATE POLICY "Allow users to read their friends list" ON public.friends FOR SELECT USING (auth.uid() = user_id OR auth.uid() = friend_id);
CREATE POLICY "Allow users to create/update friendships" ON public.friends FOR ALL USING (auth.uid() = user_id OR auth.uid() = friend_id);

-- --- Friend Requests Policies ---
CREATE POLICY "Allow users to read received or sent requests" ON public.friend_requests FOR SELECT USING (auth.uid() = sender_id OR auth.uid() = receiver_id);
CREATE POLICY "Allow users to send/respond to requests" ON public.friend_requests FOR ALL USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

-- --- Rooms Policies ---
CREATE POLICY "Allow public read access to rooms" ON public.rooms FOR SELECT USING (true);
CREATE POLICY "Allow authenticated users to create rooms" ON public.rooms FOR INSERT WITH CHECK (auth.role() = 'authenticated');
CREATE POLICY "Allow owners or admins to manage rooms" ON public.rooms FOR ALL USING (auth.uid() = owner_id OR public.sec_is_room_owner_or_admin(id, auth.uid()));

-- --- Room Members Policies ---
CREATE POLICY "Allow room members to read other members" ON public.room_members FOR SELECT USING (public.sec_is_room_member(room_id, auth.uid()));
CREATE POLICY "Allow joining or admin additions to rooms" ON public.room_members FOR ALL USING (auth.uid() = user_id OR public.sec_is_room_owner_or_admin(room_id, auth.uid()));

-- --- Room Bans Policies ---
CREATE POLICY "Allow members to read bans" ON public.room_bans FOR SELECT USING (public.sec_is_room_member(room_id, auth.uid()));
CREATE POLICY "Allow owners/admins to ban members" ON public.room_bans FOR ALL USING (public.sec_is_room_owner_or_admin(room_id, auth.uid()));

-- --- Room Roles Policies ---
CREATE POLICY "Allow members to view room roles" ON public.room_roles FOR SELECT USING (public.sec_is_room_member(room_id, auth.uid()));
CREATE POLICY "Allow owners/admins to manage room roles" ON public.room_roles FOR ALL USING (public.sec_is_room_owner_or_admin(room_id, auth.uid()));

-- --- Messages Policies ---
CREATE POLICY "Allow room members to read messages" ON public.messages FOR SELECT USING (public.sec_is_room_member(room_id, auth.uid()));
CREATE POLICY "Allow room members to insert messages" ON public.messages FOR INSERT WITH CHECK (public.sec_is_room_member(room_id, auth.uid()) AND (auth.uid() = sender_id OR sender_id IS NULL));
CREATE POLICY "Allow message authors or room admins to update/delete" ON public.messages FOR ALL USING (auth.uid() = sender_id OR public.sec_is_room_owner_or_admin(room_id, auth.uid()));

-- --- Message Reactions Policies ---
CREATE POLICY "Allow room members to view reactions" ON public.message_reactions FOR SELECT USING (EXISTS (SELECT 1 FROM public.messages WHERE id = message_id AND public.sec_is_room_member(room_id, auth.uid())));
CREATE POLICY "Allow room members to manage own reactions" ON public.message_reactions FOR ALL USING (auth.uid() = user_id);

-- --- Message Reads Policies ---
CREATE POLICY "Allow users to manage message reads" ON public.message_reads FOR ALL USING (auth.uid() = user_id);

-- --- Attachments Policies ---
CREATE POLICY "Allow room members to view attachments" ON public.attachments FOR SELECT USING (public.sec_is_room_member(room_id, auth.uid()));
CREATE POLICY "Allow room members to upload attachments" ON public.attachments FOR INSERT WITH CHECK (public.sec_is_room_member(room_id, auth.uid()));
CREATE POLICY "Allow uploader or room admins to delete attachments" ON public.attachments FOR DELETE USING (auth.uid() = uploader_id OR public.sec_is_room_owner_or_admin(room_id, auth.uid()));

-- --- Pinned Messages Policies ---
CREATE POLICY "Allow room members to view pinned messages" ON public.pinned_messages FOR SELECT USING (public.sec_is_room_member(room_id, auth.uid()));
CREATE POLICY "Allow room members to manage pins" ON public.pinned_messages FOR ALL USING (public.sec_is_room_member(room_id, auth.uid()));

-- --- Notifications Policies ---
CREATE POLICY "Allow users to view & update own notifications" ON public.notifications FOR ALL USING (auth.uid() = user_id);

-- --- Typing Status Policies ---
CREATE POLICY "Allow room members to read typing statuses" ON public.typing_status FOR SELECT USING (public.sec_is_room_member(room_id, auth.uid()));
CREATE POLICY "Allow users to set typing status" ON public.typing_status FOR ALL USING (auth.uid() = user_id);

-- --- Voice Calls Policies ---
CREATE POLICY "Allow room members to read voice calls" ON public.voice_calls FOR SELECT USING (public.sec_is_room_member(room_id, auth.uid()));
CREATE POLICY "Allow room members to host/manage voice calls" ON public.voice_calls FOR ALL USING (public.sec_is_room_member(room_id, auth.uid()));

-- --- Video Calls Policies ---
CREATE POLICY "Allow room members to read video calls" ON public.video_calls FOR SELECT USING (public.sec_is_room_member(room_id, auth.uid()));
CREATE POLICY "Allow room members to host/manage video calls" ON public.video_calls FOR ALL USING (public.sec_is_room_member(room_id, auth.uid()));

-- --- Watch Together Sessions Policies ---
CREATE POLICY "Allow room members to view watch together sessions" ON public.watch_together_sessions FOR SELECT USING (public.sec_is_room_member(room_id, auth.uid()));
CREATE POLICY "Allow room members to update watch together state" ON public.watch_together_sessions FOR ALL USING (public.sec_is_room_member(room_id, auth.uid()));

-- --- Games Policies ---
CREATE POLICY "Allow all authenticated users to read games" ON public.games FOR SELECT USING (true);

-- --- Game Sessions Policies ---
CREATE POLICY "Allow room members to view game sessions" ON public.game_sessions FOR SELECT USING (public.sec_is_room_member(room_id, auth.uid()));
CREATE POLICY "Allow room members to update game sessions" ON public.game_sessions FOR ALL USING (public.sec_is_room_member(room_id, auth.uid()));

-- --- Game Players Policies ---
CREATE POLICY "Allow game players to view state" ON public.game_players FOR SELECT USING (EXISTS (SELECT 1 FROM public.game_sessions WHERE id = session_id AND public.sec_is_room_member(room_id, auth.uid())));
CREATE POLICY "Allow game players to update own state" ON public.game_players FOR ALL USING (auth.uid() = user_id);

-- --- Audit Logs Policies ---
CREATE POLICY "Only admins or service role can view logs" ON public.audit_logs FOR SELECT USING (EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'Admin'));
CREATE POLICY "Authenticated users can insert audit logs" ON public.audit_logs FOR INSERT WITH CHECK (auth.role() = 'authenticated');

-- ============================================================================
-- 8. SUPABASE STORAGE BUCKET CREATION & STORAGE POLICIES
-- ============================================================================

-- Create 'profile-images' bucket for user avatars
INSERT INTO storage.buckets (id, name, public)
VALUES ('profile-images', 'profile-images', true)
ON CONFLICT (id) DO NOTHING;

-- Create 'chat-media' bucket for room attachments
INSERT INTO storage.buckets (id, name, public)
VALUES ('chat-media', 'chat-media', false)
ON CONFLICT (id) DO NOTHING;

-- Storage RLS Policies for 'profile-images' (Avatars)
CREATE POLICY "Allow public select access to profile avatars" ON storage.objects
  FOR SELECT USING (bucket_id = 'profile-images');

CREATE POLICY "Allow owners to upload their avatar" ON storage.objects
  FOR INSERT WITH CHECK (
    bucket_id = 'profile-images' AND
    (storage.foldername(name))[1] = auth.uid()::text
  );

CREATE POLICY "Allow owners to update/delete their avatar" ON storage.objects
  FOR ALL USING (
    bucket_id = 'profile-images' AND
    (storage.foldername(name))[1] = auth.uid()::text
  );

-- Storage RLS Policies for 'chat-media' (Room media files)
-- Structure: room_id/message_id/filename
CREATE POLICY "Allow room members to view media attachments" ON storage.objects
  FOR SELECT USING (
    bucket_id = 'chat-media' AND
    public.sec_is_room_member((storage.foldername(name))[1], auth.uid())
  );

CREATE POLICY "Allow room members to upload media attachments" ON storage.objects
  FOR INSERT WITH CHECK (
    bucket_id = 'chat-media' AND
    public.sec_is_room_member((storage.foldername(name))[1], auth.uid())
  );

CREATE POLICY "Allow room members to update/delete media attachments" ON storage.objects
  FOR ALL USING (
    bucket_id = 'chat-media' AND
    public.sec_is_room_member((storage.foldername(name))[1], auth.uid())
  );
