package com.example.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class WebSocketEvent {
    object Connected : WebSocketEvent()
    object Disconnected : WebSocketEvent()
    object Connecting : WebSocketEvent()
    object Reconnecting : WebSocketEvent()
    data class ConnectionError(val error: String) : WebSocketEvent()
    
    data class RoomStateReceived(
        val room: RoomEntity,
        val messages: List<MessageEntity>,
        val members: List<Friend>
    ) : WebSocketEvent()
    
    data class NewMessageReceived(val message: MessageEntity) : WebSocketEvent()
    data class PresenceJoinedReceived(val friend: Friend) : WebSocketEvent()
    data class PresenceLeftReceived(val clientId: String) : WebSocketEvent()
    data class PresenceUpdateReceived(
        val clientId: String,
        val isMuted: Boolean,
        val isCameraOn: Boolean,
        val isSpeaking: Boolean
    ) : WebSocketEvent()
    
    data class VideoUpdateReceived(
        val currentVideoUrl: String,
        val videoTitle: String,
        val isVideoPlaying: Boolean,
        val videoProgressMs: Long,
        val lastVideoSyncTime: Long,
        val systemMessage: String?
    ) : WebSocketEvent()

    data class SignalingReceived(
        val senderClientId: String,
        val sdp: String?,
        val candidate: String?
    ) : WebSocketEvent()

    data class VibeUpdatedReceived(
        val vibe: String,
        val senderName: String
    ) : WebSocketEvent()

    data class VideoReactionReceived(
        val emoji: String,
        val videoTime: Long,
        val senderName: String,
        val senderId: String
    ) : WebSocketEvent()

    data class MsgReactionReceived(
        val messageId: Long,
        val emoji: String,
        val senderId: String,
        val senderName: String
    ) : WebSocketEvent()

    data class PingReceived(
        val senderName: String,
        val senderId: String,
        val targetClientId: String
    ) : WebSocketEvent()

    data class GameUpdateReceived(
        val action: String,
        val subType: String?,
        val state: String,
        val senderId: String,
        val senderName: String
    ) : WebSocketEvent()

    data class DjUpdatedReceived(
        val currentDjId: String?,
        val currentDjName: String?,
        val systemMessage: String?
    ) : WebSocketEvent()

    data class VoteUpdatedReceived(
        val action: String,
        val voteType: String,
        val videoUrl: String,
        val title: String,
        val senderId: String,
        val senderName: String
    ) : WebSocketEvent()
}

class WebSocketManager {
    private val TAG = "WebSocketManager"
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Unlimited read timeout for WebSockets
        .pingInterval(15, TimeUnit.SECONDS) // Auto pings to keep connection alive
        .build()

    private var webSocket: WebSocket? = null
    private val _events = MutableSharedFlow<WebSocketEvent>(extraBufferCapacity = 100)
    val events: SharedFlow<WebSocketEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var isConnecting = false
    private var currentRoomId: String? = null
    private var currentUsername: String = ""
    private var currentAvatarIndex: Int = 0
    private var serverUrl: String = ""

    // Stability & Reconnection variables
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private var explicitlyDisconnected = false

    fun connect(url: String, roomId: String, username: String, avatarIndex: Int, token: String? = null) {
        if (isConnecting || webSocket != null) return
        isConnecting = true
        this.serverUrl = url
        this.currentRoomId = roomId
        this.currentUsername = username
        this.currentAvatarIndex = avatarIndex
        this.explicitlyDisconnected = false

        Log.d(TAG, "Connecting to WebSocket: $url for room: $roomId")

        scope.launch {
            if (reconnectAttempt > 0) {
                _events.emit(WebSocketEvent.Reconnecting)
            } else {
                _events.emit(WebSocketEvent.Connecting)
            }
        }

        val wsUrl = if (!token.isNullOrEmpty()) {
            if (url.contains("?")) "$url&token=$token" else "$url?token=$token"
        } else {
            url
        }

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnecting = false
                reconnectAttempt = 0
                reconnectJob?.cancel()
                reconnectJob = null
                Log.d(TAG, "WebSocket Opened Successfully")
                scope.launch {
                    _events.emit(WebSocketEvent.Connected)
                    
                    // Immediately join the room on connect
                    joinRoom(roomId, username, avatarIndex)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "OnMessage received: $text")
                scope.launch {
                    try {
                        val json = JSONObject(text)
                        when (json.optString("type")) {
                            "room_state" -> {
                                val roomJson = json.getJSONObject("room")
                                val room = RoomEntity(
                                    id = roomJson.getString("id"),
                                    name = roomJson.getString("name"),
                                    currentVideoUrl = roomJson.optString("currentVideoUrl", ""),
                                    videoTitle = roomJson.optString("videoTitle", ""),
                                    isVideoPlaying = roomJson.optBoolean("isVideoPlaying", false),
                                    videoProgressMs = roomJson.optLong("videoProgressMs", 0L),
                                    lastVideoSyncTime = roomJson.optLong("lastVideoSyncTime", 0L)
                                )

                                val messagesJson = json.getJSONArray("messages")
                                val messagesList = mutableListOf<MessageEntity>()
                                for (i in 0 until messagesJson.length()) {
                                    val msgObj = messagesJson.getJSONObject(i)
                                    messagesList.add(parseMessageObject(msgObj))
                                }

                                val membersJson = json.getJSONArray("members")
                                val membersList = mutableListOf<Friend>()
                                for (i in 0 until membersJson.length()) {
                                    val memberObj = membersJson.getJSONObject(i)
                                    membersList.add(parseFriendObject(memberObj))
                                }

                                _events.emit(WebSocketEvent.RoomStateReceived(room, messagesList, membersList))
                            }

                            "new_message" -> {
                                val msgObj = json.getJSONObject("message")
                                _events.emit(WebSocketEvent.NewMessageReceived(parseMessageObject(msgObj)))
                            }

                            "presence_joined" -> {
                                val memberObj = json.getJSONObject("member")
                                _events.emit(WebSocketEvent.PresenceJoinedReceived(parseFriendObject(memberObj)))
                            }

                            "presence_left" -> {
                                val memberId = json.getString("memberId")
                                _events.emit(WebSocketEvent.PresenceLeftReceived(memberId))
                            }

                            "presence_update" -> {
                                val memberId = json.getString("memberId")
                                val isMuted = json.optBoolean("isMuted", false)
                                val isCameraOn = json.optBoolean("isCameraOn", false)
                                val isSpeaking = json.optBoolean("isSpeaking", false)
                                _events.emit(WebSocketEvent.PresenceUpdateReceived(memberId, isMuted, isCameraOn, isSpeaking))
                            }

                            "video_update" -> {
                                val roomJson = json.getJSONObject("room")
                                val systemMsg = json.optString("systemMessage", null)
                                _events.emit(
                                    WebSocketEvent.VideoUpdateReceived(
                                        currentVideoUrl = roomJson.optString("currentVideoUrl", ""),
                                        videoTitle = roomJson.optString("videoTitle", ""),
                                        isVideoPlaying = roomJson.optBoolean("isVideoPlaying", false),
                                        videoProgressMs = roomJson.optLong("videoProgressMs", 0L),
                                        lastVideoSyncTime = roomJson.optLong("lastVideoSyncTime", 0L),
                                        systemMessage = systemMsg
                                    )
                                )
                            }

                            "webrtc_signaling" -> {
                                val senderClientId = json.getString("senderClientId")
                                val sdp = json.optString("sdp", null)
                                val candidate = json.optString("candidate", null)
                                _events.emit(WebSocketEvent.SignalingReceived(senderClientId, sdp, candidate))
                            }

                            "vibe_update" -> {
                                val vibe = json.getString("vibe")
                                val senderName = json.getString("senderName")
                                _events.emit(WebSocketEvent.VibeUpdatedReceived(vibe, senderName))
                            }

                            "video_reaction_received" -> {
                                val emoji = json.getString("emoji")
                                val videoTime = json.optLong("videoTime", 0L)
                                val senderName = json.getString("senderName")
                                val senderId = json.getString("senderId")
                                _events.emit(WebSocketEvent.VideoReactionReceived(emoji, videoTime, senderName, senderId))
                            }

                            "msg_reaction_received" -> {
                                val messageId = json.getLong("messageId")
                                val emoji = json.getString("emoji")
                                val senderId = json.getString("senderId")
                                val senderName = json.getString("senderName")
                                _events.emit(WebSocketEvent.MsgReactionReceived(messageId, emoji, senderId, senderName))
                            }

                            "ping_friend_received" -> {
                                val senderName = json.getString("senderName")
                                val senderId = json.getString("senderId")
                                val targetClientId = json.getString("targetClientId")
                                _events.emit(WebSocketEvent.PingReceived(senderName, senderId, targetClientId))
                            }

                            "game_update" -> {
                                val action = json.getString("action")
                                val subType = json.optString("subType", null)
                                val state = json.getString("state")
                                val senderId = json.getString("senderId")
                                val senderName = json.getString("senderName")
                                _events.emit(WebSocketEvent.GameUpdateReceived(action, subType, state, senderId, senderName))
                            }

                            "dj_update" -> {
                                val currentDjId = if (json.isNull("currentDjId")) null else json.optString("currentDjId", null)
                                val currentDjName = if (json.isNull("currentDjName")) null else json.optString("currentDjName", null)
                                val systemMessage = if (json.isNull("systemMessage")) null else json.optString("systemMessage", null)
                                _events.emit(WebSocketEvent.DjUpdatedReceived(currentDjId, currentDjName, systemMessage))
                            }

                            "vote_update" -> {
                                val action = json.getString("action")
                                val voteType = json.getString("voteType")
                                val videoUrl = json.getString("videoUrl")
                                val title = json.getString("title")
                                val senderId = json.getString("senderId")
                                val senderName = json.getString("senderName")
                                _events.emit(WebSocketEvent.VoteUpdatedReceived(action, voteType, videoUrl, title, senderId, senderName))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing message JSON", e)
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closed: $code / $reason")
                webSocketCleanUp()
                scope.launch {
                    _events.emit(WebSocketEvent.Disconnected)
                    if (!explicitlyDisconnected) {
                        scheduleReconnect()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnecting = false
                Log.e(TAG, "WebSocket Failure", t)
                webSocketCleanUp()
                scope.launch {
                    _events.emit(WebSocketEvent.ConnectionError(t.message ?: "Unknown connection error"))
                    if (!explicitlyDisconnected) {
                        scheduleReconnect()
                    }
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (explicitlyDisconnected) return
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            val backoffTime = (Math.pow(2.0, reconnectAttempt.toDouble()).toLong() * 1000L)
                .coerceAtLeast(1000L)
                .coerceAtMost(30000L)
            reconnectAttempt++

            Log.d(TAG, "Scheduling reconnect in $backoffTime ms (attempt $reconnectAttempt)")
            delay(backoffTime)

            val url = serverUrl
            val roomId = currentRoomId
            val username = currentUsername
            val avatarIndex = currentAvatarIndex
            if (url.isNotEmpty() && roomId != null && !explicitlyDisconnected) {
                webSocketCleanUp()
                connect(url, roomId, username, avatarIndex)
            }
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket explicitly")
        explicitlyDisconnected = true
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        webSocket?.close(1000, "User left")
        webSocketCleanUp()
    }

    private fun webSocketCleanUp() {
        webSocket = null
        isConnecting = false
    }

    private fun joinRoom(roomId: String, username: String, avatarIndex: Int) {
        val payload = JSONObject().apply {
            put("type", "join_room")
            put("roomId", roomId)
            put("name", username)
            put("avatar", avatarIndex)
        }
        sendPayload(payload)
    }

    fun sendChatMessage(content: String) {
        val payload = JSONObject().apply {
            put("type", "send_message")
            put("content", content)
        }
        sendPayload(payload)
    }

    fun sendVideoAction(action: String, url: String, title: String, progressMs: Long, systemMsgContent: String?) {
        val payload = JSONObject().apply {
            put("type", "video_sync")
            put("action", action)
            put("url", url)
            put("title", title)
            put("progressMs", progressMs)
            if (systemMsgContent != null) {
                put("systemMsgContent", systemMsgContent)
            }
        }
        sendPayload(payload)
    }

    fun sendMediaState(isMuted: Boolean, isCameraOn: Boolean, isSpeaking: Boolean) {
        val payload = JSONObject().apply {
            put("type", "media_state")
            put("isMuted", isMuted)
            put("isCameraOn", isCameraOn)
            put("isSpeaking", isSpeaking)
        }
        sendPayload(payload)
    }

    fun sendSignaling(targetClientId: String, sdp: String?, candidate: String?) {
        val payload = JSONObject().apply {
            put("type", "webrtc_signaling")
            put("targetClientId", targetClientId)
            if (sdp != null) put("sdp", sdp)
            if (candidate != null) put("candidate", candidate)
        }
        sendPayload(payload)
    }

    fun sendVibeChange(vibe: String) {
        val payload = JSONObject().apply {
            put("type", "vibe_change")
            put("vibe", vibe)
        }
        sendPayload(payload)
    }

    fun sendVideoReaction(emoji: String, videoTime: Long) {
        val payload = JSONObject().apply {
            put("type", "video_reaction")
            put("emoji", emoji)
            put("videoTime", videoTime)
        }
        sendPayload(payload)
    }

    fun sendMsgReaction(messageId: Long, emoji: String) {
        val payload = JSONObject().apply {
            put("type", "msg_reaction")
            put("messageId", messageId)
            put("emoji", emoji)
        }
        sendPayload(payload)
    }

    fun sendMemeDrop(memeUrl: String) {
        val payload = JSONObject().apply {
            put("type", "meme_drop")
            put("memeUrl", memeUrl)
        }
        sendPayload(payload)
    }

    fun sendPingFriend(targetClientId: String) {
        val payload = JSONObject().apply {
            put("type", "ping_friend")
            put("targetClientId", targetClientId)
        }
        sendPayload(payload)
    }

    fun sendGameAction(action: String, subType: String?, state: String) {
        val payload = JSONObject().apply {
            put("type", "game_action")
            put("action", action)
            if (subType != null) put("subType", subType)
            put("state", state)
        }
        sendPayload(payload)
    }

    fun sendDjAction(action: String) {
        val payload = JSONObject().apply {
            put("type", "dj_action")
            put("action", action)
            put("djId", currentUsername)
            put("djName", currentUsername)
        }
        sendPayload(payload)
    }

    fun sendVoteAction(action: String, voteType: String, videoUrl: String, title: String) {
        val payload = JSONObject().apply {
            put("type", "vote_action")
            put("action", action)
            put("voteType", voteType)
            put("videoUrl", videoUrl)
            put("title", title)
        }
        sendPayload(payload)
    }

    private fun sendPayload(json: JSONObject) {
        val ws = webSocket
        if (ws != null) {
            val text = json.toString()
            Log.d(TAG, "Sending message: $text")
            ws.send(text)
        } else {
            Log.e(TAG, "Cannot send payload, WebSocket is not connected")
        }
    }

    // Helper: Parse Friend JSON object
    private fun parseFriendObject(obj: JSONObject): Friend {
        val name = obj.getString("name")
        val avatarIndex = obj.optInt("avatarIndex", 0)
        val id = obj.getString("id")
        val isMuted = obj.optBoolean("isMuted", false)
        val isCameraOn = obj.optBoolean("isCameraOn", false)
        val isSpeaking = obj.optBoolean("isSpeaking", false)

        // Assign a color based on avatar index dynamically
        val colors = listOf("#FF5722", "#E91E63", "#00BCD4", "#FFC107", "#9C27B0", "#4CAF50")
        val colorHex = colors[avatarIndex % colors.size]

        return Friend(
            id = id,
            name = name,
            avatarIndex = avatarIndex,
            colorHex = colorHex,
            isMuted = isMuted,
            isCameraOn = isCameraOn,
            isSpeaking = isSpeaking,
            status = "online"
        )
    }

    // Helper: Parse Message JSON object
    private fun parseMessageObject(obj: JSONObject): MessageEntity {
        val memeUrlVal = if (obj.has("memeUrl") && !obj.isNull("memeUrl")) obj.getString("memeUrl") else null
        val reactionsJsonVal = if (obj.has("reactionsJson") && !obj.isNull("reactionsJson")) obj.getString("reactionsJson") else null
        return MessageEntity(
            id = obj.optLong("id", 0L),
            roomId = obj.optString("roomId", ""),
            senderName = obj.optString("senderName", "System"),
            senderAvatarIndex = obj.optInt("senderAvatarIndex", -1),
            content = obj.getString("content"),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            isSystem = obj.optBoolean("isSystem", false),
            memeUrl = memeUrlVal,
            reactionsJson = reactionsJsonVal
        )
    }
}
