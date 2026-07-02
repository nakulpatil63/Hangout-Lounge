package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val id: String, // e.g. "LOFI-9482"
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val currentVideoUrl: String = "",
    val videoTitle: String = "",
    val isVideoPlaying: Boolean = false,
    val videoProgressMs: Long = 0,
    val lastVideoSyncTime: Long = 0,
    val vibe: String = "chill", // "chill", "focus", "fun"
    val currentDjId: String? = null,
    val currentDjName: String? = null
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: String,
    val senderName: String,
    val senderAvatarIndex: Int,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystem: Boolean = false,
    val memeUrl: String? = null,
    val reactionsJson: String? = null // e.g. '{"😂":["user1"], "🔥":["user2"]}' or similar
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String = "current_user",
    val username: String,
    val avatarIndex: Int,
    val isLoggedIn: Boolean = false,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val role: String? = "Member"
)
