package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class ConnectionStatus {
    CONNECTING,
    RECONNECTING,
    ONLINE,
    OFFLINE
}

@OptIn(ExperimentalCoroutinesApi::class)
class HangoutViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "HangoutViewModel"
    private val database = AppDatabase.getDatabase(application)
    private val repository = HangoutRepository(database.hangoutDao())
    private val webSocketManager = WebSocketManager()
    private val sharedPrefs = application.getSharedPreferences("hangout_prefs", Context.MODE_PRIVATE)

    // Server Configuration URL with standard fallback
    private val _serverUrl = MutableStateFlow(
        sharedPrefs.getString("server_url", "wss://hangout-room-backend.glitch.me") ?: "wss://hangout-room-backend.glitch.me"
    )
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    // Connection/Error states to feed to UI
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.OFFLINE)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    // Profile State
    val userProfile = repository.userProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Room List State
    val rooms = repository.allRooms.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Active Room Navigation / State
    private val _currentRoomId = MutableStateFlow<String?>(null)
    val currentRoomId: StateFlow<String?> = _currentRoomId.asStateFlow()

    // Active Room Entity
    val activeRoom = _currentRoomId.flatMapLatest { id ->
        if (id != null) repository.getRoom(id) else flowOf(null)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Chat Feed Flow (Reads from local SQLite DB which is instantly updated by server events!)
    val messages = _currentRoomId.flatMapLatest { id ->
        if (id != null) repository.getMessages(id) else flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Active Members List (Dynamic real-time presence synced across clients)
    private val _activeMembers = MutableStateFlow<List<Friend>>(emptyList())
    val activeMembers: StateFlow<List<Friend>> = _activeMembers.asStateFlow()

    // Call State (Synced with network)
    private val _isInCall = MutableStateFlow(false)
    val isInCall: StateFlow<Boolean> = _isInCall.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isCameraOn = MutableStateFlow(false)
    val isCameraOn: StateFlow<Boolean> = _isCameraOn.asStateFlow()

    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    // Live WebRTC Signaling Channel (Preparation for voice/video calls)
    private val _signalingEvents = MutableSharedFlow<WebSocketEvent.SignalingReceived>(extraBufferCapacity = 50)
    val signalingEvents: SharedFlow<WebSocketEvent.SignalingReceived> = _signalingEvents.asSharedFlow()

    // Typing Indicator ("Alex is typing...")
    private val _typingFriend = MutableStateFlow<String?>(null)
    val typingFriend: StateFlow<String?> = _typingFriend.asStateFlow()

    // Video Player Sync progress
    private val _localVideoProgress = MutableStateFlow(0L)
    val localVideoProgress: StateFlow<Long> = _localVideoProgress.asStateFlow()

    // Preset streams list
    val presetStreams = listOf(
        SyncStream("lofi_beats", "🎵 Lofi Hip Hop Radio - Beats to Relax/Study", "https://www.youtube.com/watch?v=jfKfPfyJRdk", 300000L),
        SyncStream("synthwave_retro", "🌌 Synthwave Chill Radio - Retro Beats", "https://www.youtube.com/watch?v=4xDzrJKXOOY", 420000L),
        SyncStream("space_ambient", "🚀 Deep Space Ambient Music - Cosmic Journey", "https://www.youtube.com/watch?v=M57m9gI_Ktc", 600000L),
        SyncStream("cozy_rain", "🌧️ Relaxing Rain & Thunderstorm - Cozy Cabin", "https://www.youtube.com/watch?v=mPZkdNFkNps", 500000L)
    )

    // --- NEW SOCIAL STATE FLOWS ---
    private val _videoReactions = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 50)
    val videoReactions = _videoReactions.asSharedFlow()

    data class PlayPauseVote(
        val type: String, // "play" or "pause"
        val videoUrl: String,
        val title: String,
        val yesVotes: Set<String> = emptySet(),
        val noVotes: Set<String> = emptySet()
    )
    private val _activeVote = MutableStateFlow<PlayPauseVote?>(null)
    val activeVote: StateFlow<PlayPauseVote?> = _activeVote.asStateFlow()

    data class TicTacToeState(
        val board: List<String> = List(9) { "" },
        val playerX: String = "",
        val playerO: String = "",
        val activePlayer: String = "X",
        val winner: String? = null,
        val gameOn: Boolean = false
    )
    private val _ticTacToe = MutableStateFlow(TicTacToeState())
    val ticTacToe: StateFlow<TicTacToeState> = _ticTacToe.asStateFlow()

    private val _pingEvent = MutableSharedFlow<String>(extraBufferCapacity = 50)
    val pingEvent = _pingEvent.asSharedFlow()

    val presetQuickReplies = listOf("Yo! 👋", "OMG! 😱", "BRB 🏃‍♂️", "No way! 🤯", "Agreed! 👍", "LMAO 😂")

    // 1. Shared Browser states
    data class BrowserState(
        val url: String = "https://wikipedia.org",
        val title: String = "Wikipedia, the free encyclopedia",
        val scrollPercentY: Float = 0f,
        val leaderName: String = "",
        val isLeaderMode: Boolean = false
    )
    private val _browserState = MutableStateFlow(BrowserState())
    val browserState: StateFlow<BrowserState> = _browserState.asStateFlow()

    // 2. Live Whiteboard states
    data class WhiteboardPoint(val x: Float, val y: Float)
    data class WhiteboardStroke(
        val id: String = UUID.randomUUID().toString(),
        val points: List<WhiteboardPoint>,
        val colorHex: String,
        val thickness: Float,
        val isEraser: Boolean = false,
        val shapeType: String = "pen" // "pen", "line", "rect", "circle"
    )
    data class StickyNote(
        val id: String = UUID.randomUUID().toString(),
        val text: String,
        val x: Float,
        val y: Float,
        val colorHex: String,
        val author: String
    )
    private val _whiteboardStrokes = MutableStateFlow<List<WhiteboardStroke>>(emptyList())
    val whiteboardStrokes: StateFlow<List<WhiteboardStroke>> = _whiteboardStrokes.asStateFlow()

    private val _stickyNotes = MutableStateFlow<List<StickyNote>>(emptyList())
    val stickyNotes: StateFlow<List<StickyNote>> = _stickyNotes.asStateFlow()

    // 3. Shared Cloud Files states
    data class CloudFile(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val size: String,
        val type: String, // "image", "video", "pdf", "zip"
        val uploader: String,
        val timestamp: Long = System.currentTimeMillis(),
        val url: String = ""
    )
    private val _cloudFiles = MutableStateFlow<List<CloudFile>>(emptyList())
    val cloudFiles: StateFlow<List<CloudFile>> = _cloudFiles.asStateFlow()

    // 4. Advanced Screen Share pointer & annotation states
    data class MemberCursor(
        val name: String,
        val colorHex: String,
        val x: Float,
        val y: Float,
        val timestamp: Long = System.currentTimeMillis(),
        val isLaser: Boolean = false
    )
    private val _memberCursors = MutableStateFlow<Map<String, MemberCursor>>(emptyMap())
    val memberCursors: StateFlow<Map<String, MemberCursor>> = _memberCursors.asStateFlow()

    data class AnnotationStroke(
        val points: List<WhiteboardPoint>,
        val colorHex: String,
        val author: String
    )
    private val _annotationStrokes = MutableStateFlow<List<AnnotationStroke>>(emptyList())
    val annotationStrokes: StateFlow<List<AnnotationStroke>> = _annotationStrokes.asStateFlow()

    // 5. Poll & Voting states
    data class RoomPoll(
        val id: String = UUID.randomUUID().toString(),
        val question: String,
        val options: List<String>,
        val votes: Map<Int, Int> = emptyMap(), // optionIndex -> voteCount
        val voterChoices: Map<String, Int> = emptyMap(), // voterUsername -> optionIndex
        val isAnonymous: Boolean = false,
        val isClosed: Boolean = false,
        val creator: String
    )
    private val _polls = MutableStateFlow<List<RoomPoll>>(emptyList())
    val polls: StateFlow<List<RoomPoll>> = _polls.asStateFlow()

    private var videoTickJob: Job? = null

    init {
        // Prepopulate default profile and rooms list
        viewModelScope.launch {
            val existingProfile = repository.getProfileSuspending()
            if (existingProfile == null) {
                repository.saveProfile("GamerFriend", 0, isLoggedIn = false)
            }

            // Pre-populate default Cloud files
            _cloudFiles.value = listOf(
                CloudFile(name = "lounge_beach_mockup.png", size = "1.4 MB", type = "image", uploader = "AI Assistant"),
                CloudFile(name = "space_station_blueprints.pdf", size = "4.2 MB", type = "pdf", uploader = "AI Assistant"),
                CloudFile(name = "lofi_ambient_track_v2.mp3", size = "8.9 MB", type = "video", uploader = "AI Assistant"),
                CloudFile(name = "retro_arcade_sprites.zip", size = "12.5 MB", type = "zip", uploader = "AI Assistant")
            )

            // Pre-populate a default voting poll
            _polls.value = listOf(
                RoomPoll(
                    question = "What's our next virtual theme party?",
                    options = listOf("🏖️ Retro Beach Party", "☕ Cozy Rainy Day Café", "🚀 Deep Space Cyberpunk Den"),
                    votes = mapOf(0 to 2, 1 to 3, 2 to 1),
                    voterChoices = mapOf("AI Assistant" to 1, "GamerFriend" to 0),
                    creator = "AI Assistant"
                )
            )

            repository.allRooms.first().let { currentRooms ->
                if (currentRooms.isEmpty()) {
                    repository.createOrUpdateRoom(
                        RoomEntity(
                            id = "LOFI-CHILL",
                            name = "🎵 Lofi Chill Study Lounge",
                            currentVideoUrl = "https://www.youtube.com/watch?v=jfKfPfyJRdk",
                            videoTitle = "🎵 Lofi Hip Hop Radio - Beats to Relax/Study"
                        )
                    )
                    repository.createOrUpdateRoom(RoomEntity(id = "GAMER-ZONE", name = "🎮 Gamer's Guild Den"))
                    repository.createOrUpdateRoom(RoomEntity(id = "TECH-COFFEE", name = "☕ Tech Talk & Coding Lounge"))
                    repository.createOrUpdateRoom(RoomEntity(id = "ANIME-SYNC", name = "🍿 Anime Watch Party"))
                }
            }
        }

        // Listen to WebSocket client events in viewModelScope
        viewModelScope.launch {
            webSocketManager.events.collect { event ->
                when (event) {
                    is WebSocketEvent.Connecting -> {
                        _connectionStatus.value = ConnectionStatus.CONNECTING
                    }
                    is WebSocketEvent.Reconnecting -> {
                        _connectionStatus.value = ConnectionStatus.RECONNECTING
                    }
                    is WebSocketEvent.Connected -> {
                        Log.i(TAG, "Real-time client connected successfully")
                        _connectionStatus.value = ConnectionStatus.ONLINE
                        _connectionError.value = null
                    }
                    is WebSocketEvent.Disconnected -> {
                        Log.w(TAG, "Real-time client disconnected")
                        _connectionStatus.value = ConnectionStatus.OFFLINE
                    }
                    is WebSocketEvent.ConnectionError -> {
                        Log.e(TAG, "Real-time client error: ${event.error}")
                        if (_currentRoomId.value != null) {
                            _connectionStatus.value = ConnectionStatus.RECONNECTING
                        } else {
                            _connectionStatus.value = ConnectionStatus.OFFLINE
                        }
                        _connectionError.value = "Failed to connect to backend server at ${_serverUrl.value}. Try changing URL in settings!"
                    }
                    is WebSocketEvent.RoomStateReceived -> {
                        // Core state sync: update local database cache with master room data from network
                        repository.createOrUpdateRoom(event.room)
                        _localVideoProgress.value = event.room.videoProgressMs

                        // Sync message history and active online presence list
                        _activeMembers.value = event.members
                        event.messages.forEach { msg ->
                            repository.insertMessage(msg)
                        }
                    }
                    is WebSocketEvent.NewMessageReceived -> {
                        // Persist to local database, which automatically updates our active chat flow!
                        repository.insertMessage(event.message)

                        // Process message for AI Room Assistant commands or mentions!
                        processChatMessageForAi(event.message.senderName, event.message.content)
                    }
                    is WebSocketEvent.PresenceJoinedReceived -> {
                        // Live user joined hangout room: Add to dynamic member list
                        if (_activeMembers.value.none { m -> m.id == event.friend.id }) {
                            _activeMembers.value = _activeMembers.value + event.friend
                        }
                    }
                    is WebSocketEvent.PresenceLeftReceived -> {
                        // Live user left hangout room: Remove from dynamic member list
                        _activeMembers.value = _activeMembers.value.filter { m -> m.id != event.clientId }
                    }
                    is WebSocketEvent.PresenceUpdateReceived -> {
                        // Update media streaming/speaking attributes of a remote member in real time
                        _activeMembers.value = _activeMembers.value.map { m ->
                            if (m.id == event.clientId) {
                                m.copy(
                                    isMuted = event.isMuted,
                                    isCameraOn = event.isCameraOn,
                                    isSpeaking = event.isSpeaking
                                )
                            } else m
                        }
                    }
                    is WebSocketEvent.VideoUpdateReceived -> {
                        // Synchronize YouTube watch together player action in real time
                        val currentRoom = activeRoom.value
                        if (currentRoom != null) {
                            val updatedRoom = currentRoom.copy(
                                currentVideoUrl = event.currentVideoUrl,
                                videoTitle = event.videoTitle,
                                isVideoPlaying = event.isVideoPlaying,
                                videoProgressMs = event.videoProgressMs,
                                lastVideoSyncTime = event.lastVideoSyncTime
                            )
                            repository.createOrUpdateRoom(updatedRoom)
                            _localVideoProgress.value = event.videoProgressMs
                        }
                    }
                    is WebSocketEvent.SignalingReceived -> {
                        // WebRTC Signaling ready frame forwarded to listeners
                        _signalingEvents.emit(event)
                    }
                    is WebSocketEvent.VibeUpdatedReceived -> {
                        val currentRoom = activeRoom.value
                        if (currentRoom != null) {
                            repository.createOrUpdateRoom(currentRoom.copy(vibe = event.vibe))
                        }
                    }
                    is WebSocketEvent.VideoReactionReceived -> {
                        _videoReactions.emit(Pair(event.emoji, event.senderName))
                    }
                    is WebSocketEvent.MsgReactionReceived -> {
                        addMessageReactionLocally(event.messageId, event.emoji, event.senderName)
                    }
                    is WebSocketEvent.PingReceived -> {
                        val profile = userProfile.value
                        val text = if (event.targetClientId == profile?.username) {
                            "👋 Yo! ${event.senderName} pinged you directly!"
                        } else if (event.targetClientId == "all") {
                            "📢 Yo! ${event.senderName} pinged the whole room!"
                        } else {
                            "🔔 Yo! ${event.senderName} pinged ${event.targetClientId}!"
                        }
                        _pingEvent.emit(text)
                    }
                    is WebSocketEvent.GameUpdateReceived -> {
                        when (event.subType) {
                            "tictactoe" -> {
                                try {
                                    val stateJson = org.json.JSONObject(event.state)
                                    val boardArr = stateJson.getJSONArray("board")
                                    val boardList = List(9) { boardArr.getString(it) }
                                    _ticTacToe.value = TicTacToeState(
                                        board = boardList,
                                        playerX = stateJson.optString("playerX", ""),
                                        playerO = stateJson.optString("playerO", ""),
                                        activePlayer = stateJson.optString("activePlayer", "X"),
                                        winner = if (stateJson.isNull("winner")) null else stateJson.getString("winner"),
                                        gameOn = stateJson.optBoolean("gameOn", false)
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing tictactoe state", e)
                                }
                            }
                            "browser" -> {
                                try {
                                    val stateJson = org.json.JSONObject(event.state)
                                    _browserState.value = BrowserState(
                                        url = stateJson.getString("url"),
                                        title = stateJson.optString("title", "Synchronized Page"),
                                        scrollPercentY = stateJson.optDouble("scrollPercentY", 0.0).toFloat(),
                                        leaderName = stateJson.optString("leaderName", ""),
                                        isLeaderMode = stateJson.optBoolean("isLeaderMode", false)
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing browser state", e)
                                }
                            }
                            "whiteboard" -> {
                                try {
                                    val stateJson = org.json.JSONObject(event.state)
                                    when (event.action) {
                                        "draw_stroke" -> {
                                            val strokeObj = stateJson.getJSONObject("stroke")
                                            val stroke = parseStroke(strokeObj)
                                            _whiteboardStrokes.value = _whiteboardStrokes.value + stroke
                                        }
                                        "clear" -> {
                                            _whiteboardStrokes.value = emptyList()
                                            _stickyNotes.value = emptyList()
                                        }
                                        "sticky_add" -> {
                                            val stickyObj = stateJson.getJSONObject("sticky")
                                            val sticky = parseSticky(stickyObj)
                                            _stickyNotes.value = _stickyNotes.value + sticky
                                        }
                                        "sticky_move" -> {
                                            val stickyId = stateJson.getString("id")
                                            val x = stateJson.getDouble("x").toFloat()
                                            val y = stateJson.getDouble("y").toFloat()
                                            _stickyNotes.value = _stickyNotes.value.map {
                                                if (it.id == stickyId) it.copy(x = x, y = y) else it
                                            }
                                        }
                                        "sticky_delete" -> {
                                            val stickyId = stateJson.getString("id")
                                            _stickyNotes.value = _stickyNotes.value.filter { it.id != stickyId }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing whiteboard state", e)
                                }
                            }
                            "files" -> {
                                try {
                                    val stateJson = org.json.JSONObject(event.state)
                                    when (event.action) {
                                        "upload" -> {
                                            val fileObj = stateJson.getJSONObject("file")
                                            val cloudFile = CloudFile(
                                                id = fileObj.getString("id"),
                                                name = fileObj.getString("name"),
                                                size = fileObj.getString("size"),
                                                type = fileObj.getString("type"),
                                                uploader = fileObj.getString("uploader"),
                                                timestamp = fileObj.getLong("timestamp"),
                                                url = fileObj.optString("url", "")
                                            )
                                            if (_cloudFiles.value.none { it.id == cloudFile.id }) {
                                                _cloudFiles.value = _cloudFiles.value + cloudFile
                                            }
                                        }
                                        "delete" -> {
                                            val fileId = stateJson.getString("id")
                                            _cloudFiles.value = _cloudFiles.value.filter { it.id != fileId }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing cloud files state", e)
                                }
                            }
                            "screenshare" -> {
                                try {
                                    val stateJson = org.json.JSONObject(event.state)
                                    when (event.action) {
                                        "pointer_move" -> {
                                            val name = stateJson.getString("name")
                                            val colorHex = stateJson.getString("colorHex")
                                            val x = stateJson.getDouble("x").toFloat()
                                            val y = stateJson.getDouble("y").toFloat()
                                            val isLaser = stateJson.optBoolean("isLaser", false)
                                            _memberCursors.value = _memberCursors.value + (name to MemberCursor(
                                                name = name,
                                                colorHex = colorHex,
                                                x = x,
                                                y = y,
                                                isLaser = isLaser
                                            ))
                                        }
                                        "pointer_clear" -> {
                                            val name = stateJson.getString("name")
                                            _memberCursors.value = _memberCursors.value - name
                                        }
                                        "annotate_stroke" -> {
                                            val strokeObj = stateJson.getJSONObject("stroke")
                                            val author = strokeObj.getString("author")
                                            val colorHex = strokeObj.getString("colorHex")
                                            val ptsArr = strokeObj.getJSONArray("points")
                                            val pts = mutableListOf<WhiteboardPoint>()
                                            for (i in 0 until ptsArr.length()) {
                                                val p = ptsArr.getJSONObject(i)
                                                pts.add(WhiteboardPoint(p.getDouble("x").toFloat(), p.getDouble("y").toFloat()))
                                            }
                                            _annotationStrokes.value = _annotationStrokes.value + AnnotationStroke(
                                                points = pts,
                                                colorHex = colorHex,
                                                author = author
                                            )
                                        }
                                        "annotate_clear" -> {
                                            _annotationStrokes.value = emptyList()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing screenshare state", e)
                                }
                            }
                            "polls" -> {
                                try {
                                    val stateJson = org.json.JSONObject(event.state)
                                    when (event.action) {
                                        "create" -> {
                                            val pollObj = stateJson.getJSONObject("poll")
                                            val optsArr = pollObj.getJSONArray("options")
                                            val optsList = List(optsArr.length()) { optsArr.getString(it) }
                                            val poll = RoomPoll(
                                                id = pollObj.getString("id"),
                                                question = pollObj.getString("question"),
                                                options = optsList,
                                                isAnonymous = pollObj.optBoolean("isAnonymous", false),
                                                isClosed = pollObj.optBoolean("isClosed", false),
                                                creator = pollObj.getString("creator")
                                            )
                                            if (_polls.value.none { it.id == poll.id }) {
                                                _polls.value = _polls.value + poll
                                            }
                                        }
                                        "vote" -> {
                                            val pollId = stateJson.getString("pollId")
                                            val voter = stateJson.getString("voter")
                                            val optionIndex = stateJson.getInt("optionIndex")
                                            _polls.value = _polls.value.map { poll ->
                                                if (poll.id == pollId) {
                                                    val newChoices = poll.voterChoices + (voter to optionIndex)
                                                    val newVotes = mutableMapOf<Int, Int>()
                                                    newChoices.values.forEach { choice ->
                                                        newVotes[choice] = (newVotes[choice] ?: 0) + 1
                                                    }
                                                    poll.copy(voterChoices = newChoices, votes = newVotes)
                                                } else poll
                                            }
                                        }
                                        "close" -> {
                                            val pollId = stateJson.getString("pollId")
                                            _polls.value = _polls.value.map { poll ->
                                                if (poll.id == pollId) poll.copy(isClosed = true) else poll
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing poll state", e)
                                }
                            }
                        }
                    }
                    is WebSocketEvent.DjUpdatedReceived -> {
                        val currentRoom = activeRoom.value
                        if (currentRoom != null) {
                            repository.createOrUpdateRoom(currentRoom.copy(
                                currentDjId = event.currentDjId,
                                currentDjName = event.currentDjName
                            ))
                        }
                    }
                    is WebSocketEvent.VoteUpdatedReceived -> {
                        var currentVote = _activeVote.value
                        if (event.action == "start") {
                            currentVote = PlayPauseVote(
                                type = event.voteType,
                                videoUrl = event.videoUrl,
                                title = event.title,
                                yesVotes = setOf(event.senderId)
                            )
                            _activeVote.value = currentVote
                        } else if (event.action == "vote_yes" && currentVote != null) {
                            currentVote = currentVote.copy(
                                yesVotes = currentVote.yesVotes + event.senderId,
                                noVotes = currentVote.noVotes - event.senderId
                            )
                            _activeVote.value = currentVote
                        } else if (event.action == "vote_no" && currentVote != null) {
                            currentVote = currentVote.copy(
                                noVotes = currentVote.noVotes + event.senderId,
                                yesVotes = currentVote.yesVotes - event.senderId
                            )
                            _activeVote.value = currentVote
                        }

                        // Check if majority reached:
                        currentVote?.let { v ->
                            val totalMembers = _activeMembers.value.size.coerceAtLeast(1)
                            val majorityThreshold = (totalMembers / 2) + 1
                            if (v.yesVotes.size >= majorityThreshold) {
                                // Yes majority reached!
                                if (v.type == "play") {
                                    playVideo()
                                } else {
                                    pauseVideo()
                                }
                                _activeVote.value = null
                            } else if (v.noVotes.size >= majorityThreshold || (v.yesVotes.size + v.noVotes.size >= totalMembers)) {
                                // No majority or all voted
                                _activeVote.value = null
                            }
                        }
                    }
                }
            }
        }
    }

    // Server Configuration
    fun updateServerUrl(url: String) {
        val formattedUrl = if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            if (url.startsWith("https://")) "wss://" + url.substring(8)
            else if (url.startsWith("http://")) "ws://" + url.substring(7)
            else "wss://$url"
        } else url

        sharedPrefs.edit().putString("server_url", formattedUrl).apply()
        _serverUrl.value = formattedUrl

        // Reconnect to active room with the updated URL if currently inside one
        val currentId = _currentRoomId.value
        if (currentId != null) {
            joinRoom(currentId)
        }
    }

    // User Profile Actions
    fun login(username: String, avatarIndex: Int) {
        viewModelScope.launch {
            // Guest quick entry saves profile as Guest with no active token
            repository.saveProfileWithTokens(username, avatarIndex, isLoggedIn = true, accessToken = null, refreshToken = null, role = "Guest")
        }
    }

    suspend fun registerMember(username: String, password: String, avatarIndex: Int): Result<String> {
        return AuthService.register(_serverUrl.value, username, password, avatarIndex)
    }

    suspend fun loginMember(username: String, password: String): Result<String> {
        val result = AuthService.login(_serverUrl.value, username, password)
        return if (result.isSuccess) {
            val data = result.getOrThrow()
            val accessToken = data.getString("accessToken")
            val refreshToken = data.getString("refreshToken")
            val userObj = data.getJSONObject("user")
            val name = userObj.getString("username")
            val role = userObj.getString("role")
            val avatarIndex = userObj.getInt("avatarIndex")

            repository.saveProfileWithTokens(
                username = name,
                avatarIndex = avatarIndex,
                isLoggedIn = true,
                accessToken = accessToken,
                refreshToken = refreshToken,
                role = role
            )
            Result.success("Welcome, $name! Connected as $role.")
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Login failed"))
        }
    }

    fun logout() {
        viewModelScope.launch {
            val profile = userProfile.value
            if (profile != null) {
                val decryptedAccess = profile.accessToken?.let { SecureKeyStoreHelper.decrypt(it) } ?: ""
                val decryptedRefresh = profile.refreshToken?.let { SecureKeyStoreHelper.decrypt(it) } ?: ""
                if (decryptedAccess.isNotEmpty()) {
                    AuthService.logout(_serverUrl.value, decryptedAccess, decryptedRefresh)
                }
                repository.saveProfileWithTokens(profile.username, profile.avatarIndex, isLoggedIn = false, accessToken = null, refreshToken = null, role = "Guest")
            }
            leaveRoom()
        }
    }

    // Room Systems (Online Sync)
    fun createRoom(name: String) {
        viewModelScope.launch {
            val uniqueCode = UUID.randomUUID().toString().substring(0, 8).uppercase()
            val newRoom = RoomEntity(
                id = uniqueCode,
                name = name
            )
            repository.createOrUpdateRoom(newRoom)
            joinRoom(uniqueCode)
        }
    }

    fun joinRoom(roomId: String) {
        viewModelScope.launch {
            val profile = userProfile.value ?: return@launch
            _currentRoomId.value = roomId

            // Clean up old cached messages for a clean session flow
            _activeMembers.value = emptyList()

            // Initialize local metadata
            val room = repository.getRoomSuspending(roomId)
            if (room == null) {
                repository.createOrUpdateRoom(RoomEntity(id = roomId, name = "Hangout #$roomId"))
            }

            _connectionError.value = null

            // Connect real-time WebSocket Client
            webSocketManager.disconnect()
            val decryptedToken = profile.accessToken?.let { SecureKeyStoreHelper.decrypt(it) }
            webSocketManager.connect(
                url = _serverUrl.value,
                roomId = roomId,
                username = profile.username,
                avatarIndex = profile.avatarIndex,
                token = decryptedToken
            )

            // Start locally running video progression timeline tick
            startTimelineProgression()
        }
    }

    fun leaveRoom() {
        val roomId = _currentRoomId.value
        if (roomId != null) {
            webSocketManager.disconnect()
        }
        _currentRoomId.value = null
        stopTimelineProgression()
        _activeMembers.value = emptyList()
        _isInCall.value = false
        _isCameraOn.value = false
        _isMuted.value = false
        _isScreenSharing.value = false
        _connectionError.value = null
    }

    // Real-Time Chat System Actions
    fun sendChatMessage(content: String) {
        if (content.isBlank()) return
        val profile = userProfile.value ?: return
        viewModelScope.launch {
            // If offline, insert locally right away so user sees it in single-player sandbox mode
            if (connectionStatus.value == ConnectionStatus.OFFLINE) {
                repository.insertMessage(
                    MessageEntity(
                        roomId = _currentRoomId.value ?: "sandbox",
                        senderName = profile.username,
                        senderAvatarIndex = profile.avatarIndex,
                        content = content,
                        timestamp = System.currentTimeMillis()
                    )
                )
                processChatMessageForAi(profile.username, content)
            } else {
                // Send payload straight to real-time server
                webSocketManager.sendChatMessage(content)
            }
        }
    }

    // Call Systems (Preparations for WebRTC)
    fun toggleCall() {
        if (_currentRoomId.value == null) return
        viewModelScope.launch {
            val nextState = !_isInCall.value
            _isInCall.value = nextState

            // Sync calling state flags to other active connected users
            webSocketManager.sendMediaState(
                isMuted = _isMuted.value,
                isCameraOn = _isCameraOn.value && nextState,
                isSpeaking = false
            )
        }
    }

    fun toggleMute() {
        val nextMute = !_isMuted.value
        _isMuted.value = nextMute
        syncMediaState()
    }

    fun toggleCamera() {
        if (!_isInCall.value) return
        val nextCam = !_isCameraOn.value
        _isCameraOn.value = nextCam
        syncMediaState()
    }

    fun toggleScreenSharing() {
        if (!_isInCall.value) return
        _isScreenSharing.value = !_isScreenSharing.value
    }

    private fun syncMediaState() {
        if (_isInCall.value) {
            webSocketManager.sendMediaState(
                isMuted = _isMuted.value,
                isCameraOn = _isCameraOn.value,
                isSpeaking = false
            )
        }
    }

    // WebRTC Signaling Handlers (Pre-configured architecture hooks)
    fun sendWebRTCSignaling(targetClientId: String, sdp: String?, candidate: String?) {
        webSocketManager.sendSignaling(targetClientId, sdp, candidate)
    }

    // Watch Together Playback Synchronizations
    fun changeVideo(url: String, title: String, durationMs: Long = 300000L) {
        val roomId = _currentRoomId.value ?: return
        val profile = userProfile.value ?: return
        viewModelScope.launch {
            _localVideoProgress.value = 0L
            webSocketManager.sendVideoAction(
                action = "change",
                url = url,
                title = title,
                progressMs = 0L,
                systemMsgContent = "🎬 ${profile.username} synchronized Watch Together: $title"
            )
        }
    }

    fun playVideo() {
        val roomId = _currentRoomId.value ?: return
        val profile = userProfile.value ?: return
        val room = activeRoom.value ?: return
        viewModelScope.launch {
            webSocketManager.sendVideoAction(
                action = "play",
                url = room.currentVideoUrl,
                title = room.videoTitle,
                progressMs = _localVideoProgress.value,
                systemMsgContent = "▶️ ${profile.username} resumed playback."
            )
        }
    }

    fun pauseVideo() {
        val roomId = _currentRoomId.value ?: return
        val profile = userProfile.value ?: return
        val room = activeRoom.value ?: return
        viewModelScope.launch {
            webSocketManager.sendVideoAction(
                action = "pause",
                url = room.currentVideoUrl,
                title = room.videoTitle,
                progressMs = _localVideoProgress.value,
                systemMsgContent = "⏸️ ${profile.username} paused playback."
            )
        }
    }

    fun seekVideo(progressMs: Long) {
        val roomId = _currentRoomId.value ?: return
        val profile = userProfile.value ?: return
        val room = activeRoom.value ?: return
        viewModelScope.launch {
            _localVideoProgress.value = progressMs
            val secs = progressMs / 1000L
            val mins = secs / 60L
            val remSecs = secs % 60L
            val timeString = String.format("%02d:%02d", mins, remSecs)
            webSocketManager.sendVideoAction(
                action = "seek",
                url = room.currentVideoUrl,
                title = room.videoTitle,
                progressMs = progressMs,
                systemMsgContent = "⏩ ${profile.username} synchronized time progress to $timeString."
            )
        }
    }

    // Timeline Progression Tick loop (1Hz)
    private fun startTimelineProgression() {
        stopTimelineProgression()
        videoTickJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val room = activeRoom.value
                if (room != null && room.isVideoPlaying) {
                    val maxDur = 300000L
                    val nextProgress = _localVideoProgress.value + 1000L
                    if (nextProgress < maxDur) {
                        _localVideoProgress.value = nextProgress
                    } else {
                        _localVideoProgress.value = 0L
                    }
                }
            }
        }
    }

    private fun stopTimelineProgression() {
        videoTickJob?.cancel()
        videoTickJob = null
    }

    // --- NEW SOCIAL ACTIONS ---

    fun changeVibe(vibe: String) {
        viewModelScope.launch {
            webSocketManager.sendVibeChange(vibe)
            
            // Automatically sync matching watch together video to coordinate room ambiance!
            val matchingStream = when (vibe) {
                "chill" -> presetStreams[0] // lofi beats
                "fun" -> presetStreams[1]   // synthwave
                "focus" -> presetStreams[3] // cozy rain
                else -> null
            }
            matchingStream?.let { stream ->
                changeVideo(stream.url, stream.title, stream.durationMs)
            }
        }
    }

    fun sendVideoReaction(emoji: String) {
        viewModelScope.launch {
            webSocketManager.sendVideoReaction(emoji, _localVideoProgress.value)
        }
    }

    fun toggleMessageReaction(messageId: Long, emoji: String) {
        viewModelScope.launch {
            webSocketManager.sendMsgReaction(messageId, emoji)
        }
    }

    fun shareMeme(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            webSocketManager.sendMemeDrop(url)
        }
    }

    fun pingFriend(targetId: String) {
        viewModelScope.launch {
            webSocketManager.sendPingFriend(targetId)
        }
    }

    fun startTicTacToe(playerX: String, playerO: String) {
        viewModelScope.launch {
            val stateObj = org.json.JSONObject().apply {
                put("board", org.json.JSONArray(List(9) { "" }))
                put("playerX", playerX)
                put("playerO", playerO)
                put("activePlayer", "X")
                put("winner", org.json.JSONObject.NULL)
                put("gameOn", true)
            }
            webSocketManager.sendGameAction("start", "tictactoe", stateObj.toString())
        }
    }

    fun makeTicTacToeMove(index: Int) {
        val cur = _ticTacToe.value
        if (!cur.gameOn || cur.board[index].isNotEmpty() || cur.winner != null) return

        val profile = userProfile.value ?: return
        val isX = cur.playerX == profile.username
        val isO = cur.playerO == profile.username
        if (cur.activePlayer == "X" && !isX) return
        if (cur.activePlayer == "O" && !isO) return

        viewModelScope.launch {
            val newBoard = cur.board.toMutableList()
            newBoard[index] = cur.activePlayer

            // Check winner
            var winner: String? = null
            val winPatterns = listOf(
                listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
                listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
                listOf(0, 4, 8), listOf(2, 4, 6)
            )
            for (pattern in winPatterns) {
                if (newBoard[pattern[0]].isNotEmpty() &&
                    newBoard[pattern[0]] == newBoard[pattern[1]] &&
                    newBoard[pattern[1]] == newBoard[pattern[2]]) {
                    winner = newBoard[pattern[0]]
                    break
                }
            }

            if (winner == null && newBoard.none { it.isEmpty() }) {
                winner = "Draw"
            }

            val nextPlayer = if (cur.activePlayer == "X") "O" else "X"

            val stateObj = org.json.JSONObject().apply {
                put("board", org.json.JSONArray(newBoard))
                put("playerX", cur.playerX)
                put("playerO", cur.playerO)
                put("activePlayer", nextPlayer)
                put("winner", winner ?: org.json.JSONObject.NULL)
                put("gameOn", winner == null)
            }
            webSocketManager.sendGameAction("move", "tictactoe", stateObj.toString())
        }
    }

    fun resetTicTacToe() {
        viewModelScope.launch {
            val stateObj = org.json.JSONObject().apply {
                put("board", org.json.JSONArray(List(9) { "" }))
                put("playerX", "")
                put("playerO", "")
                put("activePlayer", "X")
                put("winner", org.json.JSONObject.NULL)
                put("gameOn", false)
            }
            webSocketManager.sendGameAction("reset", "tictactoe", stateObj.toString())
        }
    }

    fun claimDj() {
        viewModelScope.launch {
            webSocketManager.sendDjAction("claim")
        }
    }

    fun releaseDj() {
        viewModelScope.launch {
            webSocketManager.sendDjAction("release")
        }
    }

    fun startPlayPauseVote(type: String) {
        val room = activeRoom.value ?: return
        viewModelScope.launch {
            webSocketManager.sendVoteAction("start", type, room.currentVideoUrl, room.videoTitle)
        }
    }

    fun castVote(yes: Boolean) {
        val vote = _activeVote.value ?: return
        viewModelScope.launch {
            webSocketManager.sendVoteAction(if (yes) "vote_yes" else "vote_no", vote.type, vote.videoUrl, vote.title)
        }
    }

    private fun addMessageReactionLocally(messageId: Long, emoji: String, senderName: String) {
        viewModelScope.launch {
            val msg = repository.getMessageById(messageId) ?: return@launch
            val currentJson = msg.reactionsJson
            val jsonMap = if (currentJson.isNullOrEmpty()) {
                org.json.JSONObject()
            } else {
                try { org.json.JSONObject(currentJson) } catch (e: Exception) { org.json.JSONObject() }
            }
            
            val namesArray = if (jsonMap.has(emoji)) {
                jsonMap.getJSONArray(emoji)
            } else {
                org.json.JSONArray().also { jsonMap.put(emoji, it) }
            }
            
            var exists = false
            val newList = mutableListOf<String>()
            for (i in 0 until namesArray.length()) {
                val name = namesArray.getString(i)
                if (name == senderName) {
                    exists = true
                } else {
                    newList.add(name)
                }
            }
            
            if (exists) {
                val updatedArray = org.json.JSONArray()
                newList.forEach { updatedArray.put(it) }
                jsonMap.put(emoji, updatedArray)
            } else {
                namesArray.put(senderName)
            }
            
            val updatedMsg = msg.copy(reactionsJson = jsonMap.toString())
            repository.insertMessage(updatedMsg)
        }
    }

    // --- COLLABORATIVE SOCIAL WORKSPACE ACTIONS ---

    // 1. Shared Browser Actions
    fun changeBrowserUrl(url: String, title: String = "") {
        val nextUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else url
        val current = _browserState.value
        val updated = current.copy(url = nextUrl, title = if (title.isEmpty()) nextUrl.substringAfter("://").substringBefore("/") else title)
        _browserState.value = updated
        
        viewModelScope.launch {
            val stateJson = org.json.JSONObject().apply {
                put("url", updated.url)
                put("title", updated.title)
                put("scrollPercentY", updated.scrollPercentY.toDouble())
                put("leaderName", updated.leaderName)
                put("isLeaderMode", updated.isLeaderMode)
            }
            webSocketManager.sendGameAction(action = "sync", subType = "browser", state = stateJson.toString())
        }
    }

    fun scrollBrowser(percentY: Float) {
        val current = _browserState.value
        if (current.isLeaderMode && current.leaderName != (userProfile.value?.username ?: "")) return
        val updated = current.copy(scrollPercentY = percentY)
        _browserState.value = updated
        
        viewModelScope.launch {
            val stateJson = org.json.JSONObject().apply {
                put("url", updated.url)
                put("title", updated.title)
                put("scrollPercentY", updated.scrollPercentY.toDouble())
                put("leaderName", updated.leaderName)
                put("isLeaderMode", updated.isLeaderMode)
            }
            webSocketManager.sendGameAction(action = "sync", subType = "browser", state = stateJson.toString())
        }
    }

    fun toggleBrowserLeaderMode(enabled: Boolean) {
        val username = userProfile.value?.username ?: "User"
        val current = _browserState.value
        val updated = current.copy(
            isLeaderMode = enabled,
            leaderName = if (enabled) username else ""
        )
        _browserState.value = updated
        
        viewModelScope.launch {
            val stateJson = org.json.JSONObject().apply {
                put("url", updated.url)
                put("title", updated.title)
                put("scrollPercentY", updated.scrollPercentY.toDouble())
                put("leaderName", updated.leaderName)
                put("isLeaderMode", updated.isLeaderMode)
            }
            webSocketManager.sendGameAction(action = "sync", subType = "browser", state = stateJson.toString())
        }
    }

    // 2. Live Whiteboard Actions
    fun drawWhiteboardStroke(stroke: WhiteboardStroke) {
        _whiteboardStrokes.value = _whiteboardStrokes.value + stroke
        viewModelScope.launch {
            val strokeJson = org.json.JSONObject().apply {
                put("id", stroke.id)
                put("colorHex", stroke.colorHex)
                put("thickness", stroke.thickness.toDouble())
                put("isEraser", stroke.isEraser)
                put("shapeType", stroke.shapeType)
                put("points", org.json.JSONArray().apply {
                    stroke.points.forEach { pt ->
                        put(org.json.JSONObject().apply {
                            put("x", pt.x.toDouble())
                            put("y", pt.y.toDouble())
                        })
                    }
                })
            }
            val stateObj = org.json.JSONObject().apply {
                put("stroke", strokeJson)
            }
            webSocketManager.sendGameAction(action = "draw_stroke", subType = "whiteboard", state = stateObj.toString())
        }
    }

    fun clearWhiteboard() {
        _whiteboardStrokes.value = emptyList()
        _stickyNotes.value = emptyList()
        viewModelScope.launch {
            webSocketManager.sendGameAction(action = "clear", subType = "whiteboard", state = "{}")
        }
    }

    fun addStickyNote(text: String, x: Float, y: Float, colorHex: String) {
        val username = userProfile.value?.username ?: "User"
        val note = StickyNote(text = text, x = x, y = y, colorHex = colorHex, author = username)
        _stickyNotes.value = _stickyNotes.value + note
        viewModelScope.launch {
            val noteJson = org.json.JSONObject().apply {
                put("id", note.id)
                put("text", note.text)
                put("x", note.x.toDouble())
                put("y", note.y.toDouble())
                put("colorHex", note.colorHex)
                put("author", note.author)
            }
            val stateObj = org.json.JSONObject().apply {
                put("sticky", noteJson)
            }
            webSocketManager.sendGameAction(action = "sticky_add", subType = "whiteboard", state = stateObj.toString())
        }
    }

    fun moveStickyNote(id: String, x: Float, y: Float) {
        _stickyNotes.value = _stickyNotes.value.map {
            if (it.id == id) it.copy(x = x, y = y) else it
        }
        viewModelScope.launch {
            val stateObj = org.json.JSONObject().apply {
                put("id", id)
                put("x", x.toDouble())
                put("y", y.toDouble())
            }
            webSocketManager.sendGameAction(action = "sticky_move", subType = "whiteboard", state = stateObj.toString())
        }
    }

    fun deleteStickyNote(id: String) {
        _stickyNotes.value = _stickyNotes.value.filter { it.id != id }
        viewModelScope.launch {
            val stateObj = org.json.JSONObject().apply {
                put("id", id)
            }
            webSocketManager.sendGameAction(action = "sticky_delete", subType = "whiteboard", state = stateObj.toString())
        }
    }

    // 3. Shared Cloud Files Actions
    fun uploadCloudFile(name: String, size: String, type: String) {
        val username = userProfile.value?.username ?: "User"
        val cloudFile = CloudFile(
            name = name,
            size = size,
            type = type,
            uploader = username
        )
        _cloudFiles.value = _cloudFiles.value + cloudFile
        
        viewModelScope.launch {
            sendMessageAsSystem("📁 $username uploaded file to cloud workspace: $name ($size)")
            
            val fileJson = org.json.JSONObject().apply {
                put("id", cloudFile.id)
                put("name", cloudFile.name)
                put("size", cloudFile.size)
                put("type", cloudFile.type)
                put("uploader", cloudFile.uploader)
                put("timestamp", cloudFile.timestamp)
                put("url", cloudFile.url)
            }
            val stateObj = org.json.JSONObject().apply {
                put("file", fileJson)
            }
            webSocketManager.sendGameAction(action = "upload", subType = "files", state = stateObj.toString())
        }
    }

    fun deleteCloudFile(id: String) {
        _cloudFiles.value = _cloudFiles.value.filter { it.id != id }
        viewModelScope.launch {
            val stateObj = org.json.JSONObject().apply {
                put("id", id)
            }
            webSocketManager.sendGameAction(action = "delete", subType = "files", state = stateObj.toString())
        }
    }

    private suspend fun sendMessageAsSystem(content: String) {
        val roomId = _currentRoomId.value ?: return
        repository.sendMessage(
            roomId = roomId,
            senderName = "System",
            senderAvatarIndex = -1,
            content = content,
            isSystem = true
        )
    }

    // 4. Advanced Screen Share pointer & annotation actions
    fun broadcastCursorMove(x: Float, y: Float, isLaser: Boolean = false) {
        val username = userProfile.value?.username ?: "User"
        val colors = listOf("#FF5722", "#E91E63", "#00BCD4", "#FFC107", "#9C27B0", "#4CAF50")
        val colorHex = colors[(userProfile.value?.avatarIndex ?: 0) % colors.size]
        
        viewModelScope.launch {
            val stateJson = org.json.JSONObject().apply {
                put("name", username)
                put("colorHex", colorHex)
                put("x", x.toDouble())
                put("y", y.toDouble())
                put("isLaser", isLaser)
            }
            webSocketManager.sendGameAction(action = "pointer_move", subType = "screenshare", state = stateJson.toString())
        }
    }

    fun clearMyCursor() {
        val username = userProfile.value?.username ?: "User"
        viewModelScope.launch {
            val stateJson = org.json.JSONObject().apply {
                put("name", username)
            }
            webSocketManager.sendGameAction(action = "pointer_clear", subType = "screenshare", state = stateJson.toString())
        }
    }

    fun drawAnnotationStroke(stroke: AnnotationStroke) {
        _annotationStrokes.value = _annotationStrokes.value + stroke
        viewModelScope.launch {
            val strokeJson = org.json.JSONObject().apply {
                put("author", stroke.author)
                put("colorHex", stroke.colorHex)
                put("points", org.json.JSONArray().apply {
                    stroke.points.forEach { pt ->
                        put(org.json.JSONObject().apply {
                            put("x", pt.x.toDouble())
                            put("y", pt.y.toDouble())
                        })
                    }
                })
            }
            val stateObj = org.json.JSONObject().apply {
                put("stroke", strokeJson)
            }
            webSocketManager.sendGameAction(action = "annotate_stroke", subType = "screenshare", state = stateObj.toString())
        }
    }

    fun clearAnnotations() {
        _annotationStrokes.value = emptyList()
        viewModelScope.launch {
            webSocketManager.sendGameAction(action = "annotate_clear", subType = "screenshare", state = "{}")
        }
    }

    // 5. Poll & Voting actions
    fun createRoomPoll(question: String, options: List<String>, isAnonymous: Boolean = false) {
        val username = userProfile.value?.username ?: "User"
        val poll = RoomPoll(
            question = question,
            options = options,
            isAnonymous = isAnonymous,
            creator = username
        )
        _polls.value = _polls.value + poll
        
        viewModelScope.launch {
            sendMessageAsSystem("📊 $username created a new poll: \"$question\"")
            
            val pollJson = org.json.JSONObject().apply {
                put("id", poll.id)
                put("question", poll.question)
                put("options", org.json.JSONArray().apply { options.forEach { put(it) } })
                put("isAnonymous", poll.isAnonymous)
                put("isClosed", poll.isClosed)
                put("creator", poll.creator)
            }
            val stateObj = org.json.JSONObject().apply {
                put("poll", pollJson)
            }
            webSocketManager.sendGameAction(action = "create", subType = "polls", state = stateObj.toString())
        }
    }

    fun castPollVote(pollId: String, optionIndex: Int) {
        val username = userProfile.value?.username ?: "User"
        _polls.value = _polls.value.map { poll ->
            if (poll.id == pollId) {
                val newChoices = poll.voterChoices + (username to optionIndex)
                val newVotes = mutableMapOf<Int, Int>()
                newChoices.values.forEach { choice ->
                    newVotes[choice] = (newVotes[choice] ?: 0) + 1
                }
                poll.copy(voterChoices = newChoices, votes = newVotes)
            } else poll
        }
        
        viewModelScope.launch {
            val stateObj = org.json.JSONObject().apply {
                put("pollId", pollId)
                put("voter", username)
                put("optionIndex", optionIndex)
            }
            webSocketManager.sendGameAction(action = "vote", subType = "polls", state = stateObj.toString())
        }
    }

    fun closeRoomPoll(pollId: String) {
        _polls.value = _polls.value.map { poll ->
            if (poll.id == pollId) poll.copy(isClosed = true) else poll
        }
        viewModelScope.launch {
            val stateObj = org.json.JSONObject().apply {
                put("pollId", pollId)
            }
            webSocketManager.sendGameAction(action = "close", subType = "polls", state = stateObj.toString())
        }
    }

    // 6. AI Assistant integration methods
    fun processChatMessageForAi(senderName: String, content: String) {
        if (senderName == "💬 AI Assistant" || senderName == "System") return // prevent infinite loops
        
        val trimmed = content.trim()
        if (trimmed.startsWith("/")) {
            // It's a command!
            val spaceIndex = trimmed.indexOf(' ')
            val command = if (spaceIndex == -1) trimmed else trimmed.substring(0, spaceIndex)
            val argument = if (spaceIndex == -1) "" else trimmed.substring(spaceIndex + 1).trim()
            handleAiCommand(command, argument)
        } else if (trimmed.contains("@AI", ignoreCase = true) || trimmed.contains("ai assistant", ignoreCase = true)) {
            // It's a direct mention!
            handleAiMention(senderName, trimmed)
        }
    }

    fun handleAiMention(sender: String, messageText: String) {
        viewModelScope.launch {
            _typingFriend.value = "💬 AI Assistant"
            delay(1000) // visual effect
            
            // Gather room chat context
            val recentMsgList = messages.value.takeLast(10)
            val contextText = recentMsgList.joinToString("\n") { "${it.senderName}: ${it.content}" }
            
            val prompt = "You are '💬 AI Assistant', an engaging virtual member hanging out in our cooperative workspace room with friends. A friend named '$sender' mentioned you in chat:\n\"$messageText\"\n\nHere is the recent chat history for context:\n$contextText\n\nRespond in a warm, helpful, conversational manner, as a friend would. Be clear and succinct (under 3 sentences)."
            
            val aiResponse = callGeminiApi(prompt, systemInstruction = "You are a friendly AI assistant joining a hangout workspace.")
            
            // Post AI response to chat
            postAiChatMessage(aiResponse)
            _typingFriend.value = null
        }
    }

    fun handleAiCommand(command: String, arg: String) {
        val username = userProfile.value?.username ?: "User"
        viewModelScope.launch {
            _typingFriend.value = "💬 AI Assistant"
            delay(800) // visual delay
            
            when (command.lowercase()) {
                "/summarize" -> {
                    val recentMsgList = messages.value.takeLast(20)
                    if (recentMsgList.isEmpty()) {
                        postAiChatMessage("No chat history available to summarize yet! Send some messages first. 😊")
                    } else {
                        val contextText = recentMsgList.joinToString("\n") { "${it.senderName}: ${it.content}" }
                        val prompt = "Please write a concise 2-3 sentence summary of what participants have discussed in this hangout room so far:\n$contextText"
                        val summary = callGeminiApi(prompt, "You are a helpful hangout summarizer assistant.")
                        postAiChatMessage("📝 **Lounge Chat Summary**:\n$summary")
                    }
                }
                "/search" -> {
                    if (arg.isEmpty()) {
                        postAiChatMessage("Please specify a search query! E.g. `/search best movies of 2026`")
                    } else {
                        val prompt = "Search Query: $arg. Answer briefly in 2-3 sentences as an AI assistant in a workspace."
                        val result = callGeminiApi(prompt, "You are a search assistant.")
                        postAiChatMessage("🔍 **Search Results for \"$arg\"**:\n$result")
                    }
                }
                "/remind" -> {
                    if (arg.isEmpty()) {
                        postAiChatMessage("How to use: `/remind <time> <text>`. E.g. `/remind 5s take a stretch!`")
                    } else {
                        val parts = arg.split(" ", limit = 2)
                        val timeStr = parts.getOrNull(0) ?: "10s"
                        val reminderText = parts.getOrNull(1) ?: "Reminder!"
                        
                        val seconds = when {
                            timeStr.endsWith("s", ignoreCase = true) -> timeStr.dropLast(1).toLongOrNull() ?: 10L
                            timeStr.endsWith("m", ignoreCase = true) -> (timeStr.dropLast(1).toLongOrNull() ?: 1L) * 60L
                            else -> timeStr.toLongOrNull() ?: 10L
                        }
                        
                        postAiChatMessage("⏰ Sure thing! I'll remind you in $timeStr: \"$reminderText\"")
                        
                        viewModelScope.launch {
                            delay(seconds * 1000L)
                            postAiChatMessage("🔔 **AI Reminder for $username**: \"$reminderText\"")
                        }
                    }
                }
                "/translate" -> {
                    if (arg.isEmpty()) {
                        postAiChatMessage("How to use: `/translate <text> to <lang>`. E.g. `/translate Hello, how are you? to French`")
                    } else {
                        val prompt = "Translate this text. Use the user instructions literally: $arg. Provide only the translated text."
                        val result = callGeminiApi(prompt, "You are an expert translator.")
                        postAiChatMessage("🌐 **Translation**:\n$result")
                    }
                }
                "/create" -> {
                    if (arg.lowercase().startsWith("poll ")) {
                        val pollArgs = arg.substring(5).trim()
                        val parts = pollArgs.split(",")
                        if (parts.size < 2) {
                            postAiChatMessage("How to use: `/create poll <question>, <option1>, <option2>...`")
                        } else {
                            val question = parts[0].trim()
                            val options = parts.drop(1).map { it.trim() }
                            createRoomPoll(question, options, isAnonymous = false)
                            postAiChatMessage("📊 Done! I've launched the poll \"$question\" for everyone in the room.")
                        }
                    } else {
                        postAiChatMessage("Unknown create command. Use `/create poll <question>, <option 1>, <option 2>...`")
                    }
                }
                "/explain" -> {
                    if (arg.isEmpty()) {
                        postAiChatMessage("Please specify a topic to explain! E.g. `/explain blockchain`")
                    } else {
                        val prompt = "Explain '$arg' clearly, interestingly, and simply in under 4 sentences."
                        val result = callGeminiApi(prompt, "You are an excellent educator assistant.")
                        postAiChatMessage("💡 **Explanation of \"$arg\"**:\n$result")
                    }
                }
                else -> {
                    postAiChatMessage("Unknown command! Available commands:\n" +
                            "• `/summarize` (summarizes current chat history)\n" +
                            "• `/search <query>` (briefly answer query)\n" +
                            "• `/remind <time> <text>` (schedule a reminder, e.g. `5s stretch`)\n" +
                            "• `/translate <text> to <lang>` (translates text)\n" +
                            "• `/create poll <question>, <opt1>, <opt2>` (launch room poll)\n" +
                            "• `/explain <topic>` (simple description)")
                }
            }
            _typingFriend.value = null
        }
    }

    private suspend fun postAiChatMessage(content: String) {
        val roomId = _currentRoomId.value ?: return
        repository.insertMessage(
            MessageEntity(
                roomId = roomId,
                senderName = "💬 AI Assistant",
                senderAvatarIndex = 5,
                content = content,
                isSystem = false
            )
        )
    }

    private suspend fun callGeminiApi(prompt: String, systemInstruction: String? = null): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: Gemini API key not configured. Please add your API key to the Secrets panel!"
        }

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val jsonRequest = org.json.JSONObject().apply {
            val contentsArray = org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            }
            put("contents", contentsArray)

            if (systemInstruction != null) {
                put("systemInstruction", org.json.JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = okhttp3.RequestBody.create(
            mediaType,
            jsonRequest.toString()
        )

        val request = okhttp3.Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "Error: AI Assistant service returned code ${response.code}. Check API key."
                }
                val bodyStr = response.body?.string() ?: ""
                val jsonObj = org.json.JSONObject(bodyStr)
                val candidates = jsonObj.getJSONArray("candidates")
                val content = candidates.getJSONObject(0).getJSONObject("content")
                val parts = content.getJSONArray("parts")
                parts.getJSONObject(0).getString("text")
            }
        } catch (e: Exception) {
            "Error calling AI Assistant: ${e.message}"
        }
    }

    // --- JSON PARSING HELPERS ---
    private fun parseStroke(obj: org.json.JSONObject): WhiteboardStroke {
        val id = obj.getString("id")
        val colorHex = obj.getString("colorHex")
        val thickness = obj.getDouble("thickness").toFloat()
        val isEraser = obj.optBoolean("isEraser", false)
        val shapeType = obj.optString("shapeType", "pen")
        val ptsArr = obj.getJSONArray("points")
        val pts = mutableListOf<WhiteboardPoint>()
        for (i in 0 until ptsArr.length()) {
            val p = ptsArr.getJSONObject(i)
            pts.add(WhiteboardPoint(p.getDouble("x").toFloat(), p.getDouble("y").toFloat()))
        }
        return WhiteboardStroke(id, pts, colorHex, thickness, isEraser, shapeType)
    }

    private fun parseSticky(obj: org.json.JSONObject): StickyNote {
        return StickyNote(
            id = obj.getString("id"),
            text = obj.getString("text"),
            x = obj.getDouble("x").toFloat(),
            y = obj.getDouble("y").toFloat(),
            colorHex = obj.getString("colorHex"),
            author = obj.getString("author")
        )
    }

    override fun onCleared() {
        super.onCleared()
        webSocketManager.disconnect()
        stopTimelineProgression()
    }
}
