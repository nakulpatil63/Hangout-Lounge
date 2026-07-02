package com.example.data

data class Friend(
    val name: String,
    val avatarIndex: Int,
    val colorHex: String,
    val status: String = "online", // "online", "idle", "dnd"
    val isSpeaking: Boolean = false,
    val isCameraOn: Boolean = false,
    val isMuted: Boolean = false,
    val isStreaming: Boolean = false,
    val id: String = ""
)

data class SyncStream(
    val id: String,
    val title: String,
    val url: String,
    val durationMs: Long = 300000L // 5 mins default for mock progress
) {
    val youtubeId: String?
        get() {
            // Extracts simple youtube video ID from youtube urls
            return if (url.contains("v=")) {
                url.substringAfter("v=").substringBefore("&")
            } else if (url.contains("youtu.be/")) {
                url.substringAfter("youtu.be/").substringBefore("?")
            } else if (url.contains("embed/")) {
                url.substringAfter("embed/").substringBefore("?")
            } else {
                null
            }
        }
}
