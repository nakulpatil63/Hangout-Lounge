package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.Friend
import com.example.data.MessageEntity
import com.example.data.RoomEntity
import com.example.data.SyncStream
import com.example.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.scale

val GlassYellow = Color(0xFFFFD60A)
val GlassIndigoColor = Color(0xFF5865F2)

enum class RoomTab {
    CHAT,
    WATCH_TOGETHER,
    BROWSER,
    WHITEBOARD,
    FILES,
    POLLS,
    GAMES,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    viewModel: com.example.ui.HangoutViewModel? = null,
    room: RoomEntity,
    messages: List<MessageEntity>,
    activeMembers: List<Friend>,
    isInCall: Boolean,
    isMuted: Boolean,
    isCameraOn: Boolean,
    isScreenSharing: Boolean,
    typingFriend: String?,
    videoProgressMs: Long,
    presetStreams: List<SyncStream>,
    activeVote: com.example.ui.HangoutViewModel.PlayPauseVote?,
    ticTacToe: com.example.ui.HangoutViewModel.TicTacToeState,
    videoReactionsFlow: SharedFlow<Pair<String, String>>,
    pingEventFlow: SharedFlow<String>,
    presetQuickReplies: List<String>,
    onSendMessage: (String) -> Unit,
    onToggleCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onChangeVideo: (String, String, Long) -> Unit,
    onPlayVideo: () -> Unit,
    onPauseVideo: () -> Unit,
    onSeekVideo: (Long) -> Unit,
    onChangeVibe: (String) -> Unit,
    onSendVideoReaction: (String) -> Unit,
    onStartVote: (String) -> Unit,
    onCastVote: (Boolean) -> Unit,
    onStartTicTacToe: (String, String) -> Unit,
    onMakeTicTacToeMove: (Int) -> Unit,
    onResetTicTacToe: () -> Unit,
    onShareMeme: (String) -> Unit,
    onToggleMessageReaction: (Long, String) -> Unit,
    onClaimDj: () -> Unit,
    onReleaseDj: () -> Unit,
    onPingFriend: (String) -> Unit,
    onLeaveRoom: () -> Unit,
    connectionError: String? = null,
    connectionStatus: com.example.ui.ConnectionStatus = com.example.ui.ConnectionStatus.OFFLINE,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var selectedTab by remember { mutableStateOf(RoomTab.CHAT) }
    var chatText by remember { mutableStateOf("") }
    var videoInputUrl by remember { mutableStateOf("") }

    // Dynamic state declarations for social layers
    var showVibeDropdown by remember { mutableStateOf(false) }
    var showMemeDialog by remember { mutableStateOf(false) }
    var showMiniGames by remember { mutableStateOf(false) }
    var memeInputUrl by remember { mutableStateOf("") }

    // Collect real-time video reactions
    var floatingReactions by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    LaunchedEffect(videoReactionsFlow) {
        videoReactionsFlow.collect { pair ->
            val id = System.currentTimeMillis()
            floatingReactions = floatingReactions + Pair(pair.first, id)
            coroutineScope.launch {
                delay(2500)
                floatingReactions = floatingReactions.filter { it.second != id }
            }
        }
    }

    // Collect activity pings for high-contrast drop alerts
    var activePingMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pingEventFlow) {
        pingEventFlow.collect { message ->
            activePingMessage = message
            delay(4000)
            if (activePingMessage == message) {
                activePingMessage = null
            }
        }
    }

    // Define background gradient styling based on room.vibe
    val backgroundBrush = when (room.vibe) {
        "focus" -> Brush.verticalGradient(
            colors = listOf(Color(0xFF0F2027), Color(0xFF1B2F38), Color(0xFF243B55)) // Deep Focus Concentration Midnight
        )
        "fun" -> Brush.verticalGradient(
            colors = listOf(Color(0xFF2D0A4E), Color(0xFF140535), Color(0xFF0D0221)) // Neon Synthwave Cyber Punk Vibe
        )
        else -> Brush.verticalGradient(
            colors = listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F0F1A)) // Cozy Chill Twilight Sunset
        )
    }

    // Navigation Drawer wrapper for Discord-like collateral panel (members list)
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xF210101C), // Deep premium space glass
                modifier = Modifier.width(280.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "ROOM MEMBERS (${activeMembers.size + 1})",
                        color = Color(0xFF949BA4),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Display Current User
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF5865F2))
                        ) {
                            Text("👑", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "You (Owner)",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isInCall) "In Voice Room" else "Online",
                                color = if (isInCall) Color(0xFF5865F2) else Color(0xFF23A55A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF35363C), modifier = Modifier.padding(vertical = 12.dp))

                    // Friends with Direct Ping Button integration
                    if (activeMembers.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonOutline,
                                contentDescription = null,
                                tint = Color(0xFF747F8D),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Alone in the Lounge",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Invite friends using the code above!",
                                color = Color(0xFF747F8D),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(activeMembers, key = { it.id }) { friend ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Avatar with Status Indicator
                                    Box(
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(android.graphics.Color.parseColor(friend.colorHex)))
                                        ) {
                                            Text(
                                                text = if (friend.avatarIndex in AVATAR_EMOJIS.indices) AVATAR_EMOJIS[friend.avatarIndex] else "👾",
                                                fontSize = 18.sp
                                            )
                                        }

                                        // Online Status Badge
                                        Box(
                                            modifier = Modifier
                                                .size(11.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when (friend.status) {
                                                        "online" -> Color(0xFF23A55A)
                                                        "idle" -> Color(0xFFF0B232)
                                                        else -> Color(0xFF747F8D)
                                                    }
                                                )
                                                .border(1.5.dp, Color(0xFF2B2D31), CircleShape)
                                                .align(Alignment.BottomEnd)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = friend.name,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            if (friend.isSpeaking && isInCall) {
                                                Text(
                                                    text = "Speaking...",
                                                    color = Color(0xFF23A55A),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Direct Ping friend button
                                            IconButton(
                                                onClick = { onPingFriend(friend.id) },
                                                modifier = Modifier.size(28.dp).testTag("ping_${friend.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Notifications,
                                                    contentDescription = "Ping Friend",
                                                    tint = Color(0xFFF0B232),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            // Voice state indicators
                                            if (isInCall) {
                                                if (friend.isMuted) {
                                                    Icon(
                                                        imageVector = Icons.Default.MicOff,
                                                        contentDescription = null,
                                                        tint = Color(0xFFF23F43),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                                if (friend.isCameraOn) {
                                                    Icon(
                                                        imageVector = Icons.Default.Videocam,
                                                        contentDescription = null,
                                                        tint = Color(0xFF23A55A),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedThemeBackground(vibe = room.vibe)
            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = Color.Transparent, // Allow vibe background gradient to show
                bottomBar = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .frostedGlassCard(
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                                backgroundColor = GlassFillMedium,
                                borderColor = GlassBorderLow
                            )
                            .navigationBarsPadding()
                            .padding(vertical = 8.dp, horizontal = 12.dp)
                    ) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                RoomTabItem(
                                    selected = selectedTab == RoomTab.CHAT,
                                    icon = Icons.Default.Chat,
                                    label = "Chat",
                                    onClick = { selectedTab = RoomTab.CHAT }
                                )
                            }
                            item {
                                RoomTabItem(
                                    selected = selectedTab == RoomTab.WATCH_TOGETHER,
                                    icon = Icons.Default.Tv,
                                    label = "Watch",
                                    onClick = { selectedTab = RoomTab.WATCH_TOGETHER }
                                )
                            }
                            item {
                                RoomTabItem(
                                    selected = selectedTab == RoomTab.BROWSER,
                                    icon = Icons.Default.Language,
                                    label = "Browser",
                                    onClick = { selectedTab = RoomTab.BROWSER }
                                )
                            }
                            item {
                                RoomTabItem(
                                    selected = selectedTab == RoomTab.WHITEBOARD,
                                    icon = Icons.Default.Brush,
                                    label = "Board",
                                    onClick = { selectedTab = RoomTab.WHITEBOARD }
                                )
                            }
                            item {
                                RoomTabItem(
                                    selected = selectedTab == RoomTab.FILES,
                                    icon = Icons.Default.Folder,
                                    label = "Files",
                                    onClick = { selectedTab = RoomTab.FILES }
                                )
                            }
                            item {
                                RoomTabItem(
                                    selected = selectedTab == RoomTab.POLLS,
                                    icon = Icons.Default.Poll,
                                    label = "Polls",
                                    onClick = { selectedTab = RoomTab.POLLS }
                                )
                            }
                            item {
                                RoomTabItem(
                                    selected = selectedTab == RoomTab.GAMES,
                                    icon = Icons.Default.SportsEsports,
                                    label = "Games",
                                    onClick = { selectedTab = RoomTab.GAMES }
                                )
                            }
                            item {
                                RoomTabItem(
                                    selected = selectedTab == RoomTab.SETTINGS,
                                    icon = Icons.Default.Settings,
                                    label = "Settings",
                                    onClick = { selectedTab = RoomTab.SETTINGS }
                                )
                            }
                        }
                    }
                },
                topBar = {
                    // Custom App Bar styled as glass overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .frostedGlassCard(
                                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                                backgroundColor = GlassFillMedium,
                                borderColor = GlassBorderLow
                            )
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = onLeaveRoom,
                                    modifier = Modifier.testTag("back_button")
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Leave hangout", tint = Color.White)
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Column {
                                    Text(
                                        text = room.name,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("Invite Code", room.id)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Room Code Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text(
                                                text = "Invite: ${room.id}",
                                                color = Color(0xFF949BA4),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy invite code",
                                                tint = Color(0xFF949BA4),
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        // Glowing connection status indicator
                                        val statusColor = when (connectionStatus) {
                                            com.example.ui.ConnectionStatus.ONLINE -> Color(0xFF23A55A)
                                            com.example.ui.ConnectionStatus.CONNECTING -> Color(0xFFF0B232)
                                            com.example.ui.ConnectionStatus.RECONNECTING -> Color(0xFFFFEB3B)
                                            com.example.ui.ConnectionStatus.OFFLINE -> Color(0xFFF23F43)
                                        }
                                        val statusText = when (connectionStatus) {
                                            com.example.ui.ConnectionStatus.ONLINE -> "Online"
                                            com.example.ui.ConnectionStatus.CONNECTING -> "Connecting..."
                                            com.example.ui.ConnectionStatus.RECONNECTING -> "Reconnecting..."
                                            com.example.ui.ConnectionStatus.OFFLINE -> "Offline"
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(statusColor, shape = CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = statusText,
                                            color = statusColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Right action tools: Vibe Dropdown, Meme Dialog, Members Toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Vibe Selection Dropdown
                                Box {
                                    val currentVibeLabel = when (room.vibe) {
                                        "focus" -> "🧠 Focus"
                                        "fun" -> "🎉 Fun"
                                        else -> "😌 Chill"
                                    }
                                    TextButton(
                                        onClick = { showVibeDropdown = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(currentVibeLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    DropdownMenu(
                                        expanded = showVibeDropdown,
                                        onDismissRequest = { showVibeDropdown = false },
                                        modifier = Modifier.background(Color(0xFF1E1F22))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Chill Sunset 😌", color = Color.White) },
                                            onClick = {
                                                onChangeVibe("chill")
                                                showVibeDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Focus Flow 🧠", color = Color.White) },
                                            onClick = {
                                                onChangeVibe("focus")
                                                showVibeDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Retro Fun 🎉", color = Color.White) },
                                            onClick = {
                                                onChangeVibe("fun")
                                                showVibeDropdown = false
                                            }
                                        )
                                    }
                                }

                                // Meme Drop button trigger
                                IconButton(
                                    onClick = { showMemeDialog = true },
                                    modifier = Modifier.size(36.dp).testTag("meme_drop_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Share Meme Link",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Members side-drawer toggle
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                        }
                                    },
                                    modifier = Modifier.testTag("members_drawer_btn")
                                ) {
                                    Icon(Icons.Default.Group, contentDescription = "Show active members", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (connectionError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xE6E53935))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "⚠️",
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = connectionError,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    when (selectedTab) {
                        RoomTab.CHAT -> {
                            // Immersive Chat View (incorporating voice call panel if in call)
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Voice/Video Call workspace if call active
                                AnimatedVisibility(
                                    visible = isInCall,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    CallWorkspace(
                                        viewModel = viewModel,
                                        isCameraOn = isCameraOn,
                                        isMuted = isMuted,
                                        isScreenSharing = isScreenSharing,
                                        activeMembers = activeMembers,
                                        onToggleMute = onToggleMute,
                                        onToggleCamera = onToggleCamera,
                                        onToggleScreenShare = onToggleScreenShare,
                                        onLeaveCall = onToggleCall,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    )
                                }

                                val scrollState = rememberLazyListState()
                                LaunchedEffect(messages.size) {
                                    if (messages.isNotEmpty()) {
                                        scrollState.animateScrollToItem(messages.size - 1)
                                    }
                                }

                                if (messages.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = Color(0xFF747F8D),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No Messages Yet",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Be the first to break the ice and say hello!",
                                            color = Color(0xFF747F8D),
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        state = scrollState,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    ) {
                                        items(messages) { message ->
                                            ChatMessageItem(
                                                message = message,
                                                onToggleReaction = onToggleMessageReaction
                                            )
                                        }
                                    }
                                }

                                // Bottom Typing indicator
                                AnimatedVisibility(
                                    visible = typingFriend != null,
                                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            strokeWidth = 1.5.dp,
                                            color = Color(0xFF5865F2),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = typingFriend ?: "",
                                            color = Color(0xFFB5BAC1),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Preset quick-tap replies horizontal slider
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0x1A000000))
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    items(presetQuickReplies) { reply ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0x26FFFFFF))
                                                .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                                                .clickable { onSendMessage(reply) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(reply, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                // Call Join CTA strip if NOT in call
                                if (!isInCall) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(GlassFillMedium)
                                            .border(width = 1.dp, color = GlassBorderLow)
                                            .clickable { onToggleCall() }
                                            .padding(vertical = 10.dp, horizontal = 16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Videocam,
                                                    contentDescription = null,
                                                    tint = Color(0xFF23A55A),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = "Voice & Video Room",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "Hop in to talk, camera-share, and chill live!",
                                                        color = Color(0xFF949BA4),
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "Join Call 📞",
                                                color = Color(0xFF23A55A),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Message entry text field box
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(GlassFillMedium)
                                        .border(width = 1.dp, color = GlassBorderLow)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .imePadding(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = chatText,
                                        onValueChange = { chatText = it },
                                        placeholder = {
                                            Text(
                                                text = "Message #${room.name.take(15)}...",
                                                color = GlassTextMuted,
                                                fontSize = 14.sp
                                            )
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = GlassTextWhite,
                                            unfocusedTextColor = GlassTextWhite,
                                            focusedContainerColor = GlassFillLow,
                                            unfocusedContainerColor = GlassFillLow,
                                            focusedBorderColor = GlassIndigoAccent,
                                            unfocusedBorderColor = Color.Transparent
                                        ),
                                        maxLines = 3,
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("chat_input_field")
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    IconButton(
                                        onClick = {
                                            if (chatText.trim().isNotBlank()) {
                                                onSendMessage(chatText.trim())
                                                chatText = ""
                                            }
                                        },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = GlassIndigoAccent,
                                            contentColor = GlassTextWhite
                                        ),
                                        modifier = Modifier
                                            .size(40.dp)
                                            .testTag("chat_send_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send message",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                        RoomTab.WATCH_TOGETHER -> {
                            // Immersive dedicated YouTube Watch Together screen
                            Box(modifier = Modifier.fillMaxSize()) {
                                WatchTogetherPane(
                                    currentRoom = room,
                                    videoProgressMs = videoProgressMs,
                                    presetStreams = presetStreams,
                                    videoInputUrl = videoInputUrl,
                                    onVideoInputChange = { videoInputUrl = it },
                                    onChangeVideo = { url, title, duration ->
                                        onChangeVideo(url, title, duration)
                                        videoInputUrl = ""
                                    },
                                    onPlayVideo = onPlayVideo,
                                    onPauseVideo = onPauseVideo,
                                    onSeekVideo = onSeekVideo,
                                    activeVote = activeVote,
                                    onStartVote = onStartVote,
                                    onCastVote = onCastVote,
                                    onSendVideoReaction = onSendVideoReaction,
                                    floatingReactions = floatingReactions,
                                    messages = messages,
                                    onClaimDj = onClaimDj,
                                    onReleaseDj = onReleaseDj,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        RoomTab.GAMES -> {
                            // Dedicated Lounge Games screen (Tic Tac Toe board + status)
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "🎮 Real-Time Tic Tac Toe",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                // Game Player slots
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Player X (Starts)", color = Color(0xFF5865F2), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (ticTacToe.playerX.isNotEmpty()) "❌ ${ticTacToe.playerX}" else "Vacant",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (ticTacToe.playerX.isEmpty()) {
                                            TextButton(onClick = { onStartTicTacToe("You", ticTacToe.playerO) }) {
                                                Text("Join as X", fontSize = 11.sp, color = GlassIndigoLight)
                                            }
                                        }
                                    }

                                    Text("vs", color = Color(0xFF949BA4), fontSize = 14.sp)

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Player O", color = Color(0xFFE53935), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (ticTacToe.playerO.isNotEmpty()) "⭕ ${ticTacToe.playerO}" else "Vacant",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (ticTacToe.playerO.isEmpty()) {
                                            TextButton(onClick = { onStartTicTacToe(ticTacToe.playerX, "You") }) {
                                                Text("Join as O", fontSize = 11.sp, color = GlassIndigoLight)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Active Player Turn indicator or winner marquee
                                if (ticTacToe.gameOn) {
                                    Text(
                                        text = "Active Turn: Player ${ticTacToe.activePlayer}",
                                        color = Color(0xFF23A55A),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (ticTacToe.winner != null) {
                                    Text(
                                        text = if (ticTacToe.winner == "Draw") "🤝 It's a Draw!" else "🎉 Winner: Player ${ticTacToe.winner}!",
                                        color = Color(0xFFF0B232),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Board Grid (3x3)
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (row in 0 until 3) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            for (col in 0 until 3) {
                                                val index = row * 3 + col
                                                val cell = ticTacToe.board[index]
                                                Box(
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0x1Fffffff))
                                                        .border(1.dp, Color(0x33ffffff), RoundedCornerShape(8.dp))
                                                        .clickable { onMakeTicTacToeMove(index) }
                                                        .testTag("ttt_cell_$index"),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (cell == "X") "❌" else if (cell == "O") "⭕" else "",
                                                        fontSize = 24.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = onResetTicTacToe,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                                    modifier = Modifier.testTag("reset_ttt_btn")
                                ) {
                                    Text("Reset Board", color = Color.White)
                                }
                            }
                        }
                        RoomTab.BROWSER -> {
                            if (viewModel != null) {
                                val browserState by viewModel.browserState.collectAsState()
                                SharedBrowserPanel(
                                    browserState = browserState,
                                    onNavigate = { url -> viewModel.changeBrowserUrl(url) },
                                    onScroll = { percent -> viewModel.scrollBrowser(percent) },
                                    onToggleLeader = { enabled -> viewModel.toggleBrowserLeaderMode(enabled) },
                                    currentUsername = viewModel.userProfile.collectAsState().value?.username ?: "User"
                                )
                            } else {
                                Text("Browser state unavailable.", color = Color.White)
                            }
                        }
                        RoomTab.WHITEBOARD -> {
                            if (viewModel != null) {
                                val strokes by viewModel.whiteboardStrokes.collectAsState()
                                val stickies by viewModel.stickyNotes.collectAsState()
                                val username = viewModel.userProfile.collectAsState().value?.username ?: "User"
                                LiveWhiteboardPanel(
                                    strokes = strokes,
                                    stickies = stickies,
                                    currentUsername = username,
                                    onDrawStroke = { stroke -> viewModel.drawWhiteboardStroke(stroke) },
                                    onClear = { viewModel.clearWhiteboard() },
                                    onAddSticky = { text, x, y, col -> viewModel.addStickyNote(text, x, y, col) },
                                    onMoveSticky = { id, x, y -> viewModel.moveStickyNote(id, x, y) },
                                    onDeleteSticky = { id -> viewModel.deleteStickyNote(id) }
                                )
                            } else {
                                Text("Whiteboard state unavailable.", color = Color.White)
                            }
                        }
                        RoomTab.FILES -> {
                            if (viewModel != null) {
                                val files by viewModel.cloudFiles.collectAsState()
                                SharedCloudFilesPanel(
                                    files = files,
                                    onUpload = { name, size, type -> viewModel.uploadCloudFile(name, size, type) },
                                    onDelete = { id -> viewModel.deleteCloudFile(id) }
                                )
                            } else {
                                Text("Cloud storage state unavailable.", color = Color.White)
                            }
                        }
                        RoomTab.POLLS -> {
                            if (viewModel != null) {
                                val polls by viewModel.polls.collectAsState()
                                val username = viewModel.userProfile.collectAsState().value?.username ?: "User"
                                RoomPollsPanel(
                                    polls = polls,
                                    currentUsername = username,
                                    onCreatePoll = { question, options, isAnon -> viewModel.createRoomPoll(question, options, isAnon) },
                                    onCastVote = { pollId, idx -> viewModel.castPollVote(pollId, idx) },
                                    onClosePoll = { pollId -> viewModel.closeRoomPoll(pollId) }
                                )
                            } else {
                                Text("Polls state unavailable.", color = Color.White)
                            }
                        }
                        RoomTab.SETTINGS -> {
                            // Dedicated settings panel: vibe selection, voice settings, and general room configurations
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "⚙️ Lounge Settings",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // Vibe selection section
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = GlassFillMedium),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Select Lounge Theme / Vibe",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Change the background mood gradient and style of the room for all friends.",
                                            color = Color(0xFF949BA4),
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Button(
                                                onClick = { onChangeVibe("cozy") },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (room.vibe == "cozy") GlassIndigoAccent else Color(0x26FFFFFF)
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Cozy Chill 🌆", fontSize = 11.sp)
                                            }
                                            Button(
                                                onClick = { onChangeVibe("focus") },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (room.vibe == "focus") GlassIndigoAccent else Color(0x26FFFFFF)
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Deep Focus 🧠", fontSize = 11.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { onChangeVibe("fun") },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (room.vibe == "fun") GlassIndigoAccent else Color(0x26FFFFFF)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Retro Cyber Fun 🎉", fontSize = 11.sp)
                                        }
                                    }
                                }

                                // Voice/Video controls card
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = GlassFillMedium),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Live Voice & Video Settings",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        if (isInCall) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Button(
                                                    onClick = onToggleMute,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isMuted) Color(0xFFF23F43) else Color(0xFF23A55A)
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(if (isMuted) "Unmute Mic 🎙️" else "Mute Mic 🔇", fontSize = 11.sp)
                                                }
                                                Button(
                                                    onClick = onToggleCamera,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isCameraOn) Color(0xFF23A55A) else Color(0x26FFFFFF)
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(if (isCameraOn) "Camera On 🎥" else "Camera Off 📷", fontSize = 11.sp)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = onToggleCall,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF23F43)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Disconnect From Voice Call 🔴", fontSize = 11.sp)
                                            }
                                        } else {
                                            Text(
                                                text = "You are not currently in the room voice call.",
                                                color = Color(0xFF949BA4),
                                                fontSize = 11.sp
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = onToggleCall,
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23A55A)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Join Voice Room Call 📞", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }

                                // General Room Info Card
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = GlassFillMedium),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "About This Room",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Room Name: ${room.name}",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Unique Invite Code: ${room.id}",
                                            color = Color(0xFF949BA4),
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = onLeaveRoom,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x26FFFFFF)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Leave Lounge", color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Real-Time Activity Ping Toast Slide-down
            AnimatedVisibility(
                visible = activePingMessage != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 80.dp)
                    .zIndex(99f)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xF2F0B232)),
                    elevation = CardDefaults.cardElevation(8.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activePingMessage ?: "",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Meme Drop Dialogue Popover
            if (showMemeDialog) {
                AlertDialog(
                    onDismissRequest = { showMemeDialog = false },
                    title = { Text("Meme Drop Room 🖼️", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                "Share a direct meme or GIF URL to display it live inside our hangout lobby!",
                                color = Color(0xFFB5BAC1),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = memeInputUrl,
                                onValueChange = { memeInputUrl = it },
                                label = { Text("Meme Link URL", color = Color(0xFF949BA4)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF5865F2)
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("meme_url_field")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (memeInputUrl.trim().isNotEmpty()) {
                                    onShareMeme(memeInputUrl.trim())
                                    memeInputUrl = ""
                                    showMemeDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2))
                        ) {
                            Text("Share Live 🚀", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showMemeDialog = false }) {
                            Text("Cancel", color = Color(0xFF949BA4))
                        }
                    },
                    containerColor = Color(0xFF1E1F22),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

// 🎬 WATCH TOGETHER SUB-WORKSPACE
@Composable
fun WatchTogetherPane(
    currentRoom: RoomEntity,
    videoProgressMs: Long,
    presetStreams: List<SyncStream>,
    videoInputUrl: String,
    onVideoInputChange: (String) -> Unit,
    onChangeVideo: (String, String, Long) -> Unit,
    onPlayVideo: () -> Unit,
    onPauseVideo: () -> Unit,
    onSeekVideo: (Long) -> Unit,
    activeVote: com.example.ui.HangoutViewModel.PlayPauseVote?,
    onStartVote: (String) -> Unit,
    onCastVote: (Boolean) -> Unit,
    onSendVideoReaction: (String) -> Unit,
    floatingReactions: List<Pair<String, Long>>,
    messages: List<MessageEntity>,
    onClaimDj: () -> Unit,
    onReleaseDj: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedPresets by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .padding(12.dp)
            .frostedGlassCard(
                shape = RoundedCornerShape(24.dp),
                backgroundColor = GlassFillLow,
                borderColor = GlassBorderLow
            )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header Row: Media Sync Title & Presets Selector toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = GlassIndigoAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Watch Together 🍿",
                        color = GlassTextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = { expandedPresets = !expandedPresets },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = if (expandedPresets) "Close Presets" else "Choose Preset Stream",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlassIndigoLight
                    )
                }
            }

            // Expanded Presets Grid
            AnimatedVisibility(
                visible = expandedPresets,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "TAP TO SYNC STREAM FOR EVERYONE IN THE ROOM:",
                        color = Color(0xFF949BA4),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetStreams.forEach { stream ->
                            val isSelected = currentRoom.currentVideoUrl == stream.url
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0x336366F1) else GlassFillLow)
                                    .border(
                                        1.dp,
                                        if (isSelected) GlassIndigoAccent else GlassBorderLow,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        onChangeVideo(stream.url, stream.title, stream.durationMs)
                                        expandedPresets = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stream.title.take(1).ifEmpty { "🎬" },
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stream.title,
                                        color = if (isSelected) GlassIndigoLight else GlassTextWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // --- DJ SYSTEM BANNER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x335865F2), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x335865F2), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎧", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (currentRoom.currentDjId.isNullOrEmpty()) {
                            "No active DJ right now."
                        } else {
                            "DJ: ${currentRoom.currentDjName ?: ""} is spinning..."
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = {
                        if (!currentRoom.currentDjId.isNullOrEmpty() && currentRoom.currentDjName == "You") {
                            onReleaseDj()
                        } else {
                            onClaimDj()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(
                        text = if (!currentRoom.currentDjId.isNullOrEmpty() && currentRoom.currentDjName == "You") {
                            "Release 🚫"
                        } else {
                            "Claim DJ 👑"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlassIndigoLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // URL input to paste custom Youtube Link styled as glass
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = videoInputUrl,
                    onValueChange = onVideoInputChange,
                    placeholder = {
                        Text(
                            text = "Paste YouTube Video Link here...",
                            color = GlassTextMuted,
                            fontSize = 12.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GlassTextWhite,
                        unfocusedTextColor = GlassTextWhite,
                        focusedContainerColor = GlassFillLow,
                        unfocusedContainerColor = GlassFillLow,
                        focusedBorderColor = GlassIndigoAccent,
                        unfocusedBorderColor = GlassBorderLow
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("youtube_url_field")
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (videoInputUrl.trim().isNotBlank()) {
                            val url = videoInputUrl.trim()
                            val parsedTitle = if (url.contains("v=")) {
                                "YouTube Sync Stream: " + url.substringAfter("v=").take(5) + "..."
                            } else {
                                "Synced Custom Video Session"
                            }
                            onChangeVideo(url, parsedTitle, 300000L)
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFF35363C),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .testTag("youtube_sync_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Sync URL link",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actual Player Screen (Displays the video container/WebView)
            val currentStream = presetStreams.firstOrNull { it.url == currentRoom.currentVideoUrl }
            val youtubeId = currentStream?.youtubeId ?: "jfKfPfyJRdk"

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .frostedGlassCard(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color.Black, // Web video displays best on absolute black background
                        borderColor = GlassBorderLow
                    )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Actual embedded Iframe Web player!
                    YouTubePlayer(
                        youtubeId = youtubeId,
                        isPlaying = currentRoom.isVideoPlaying,
                        progressMs = videoProgressMs,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay information banner at the bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (currentRoom.videoTitle.isNotEmpty()) currentRoom.videoTitle else "🎵 Chill Lofi Stream",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Floating Live Chat Overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(Color(0x66000000), RoundedCornerShape(8.dp))
                            .padding(4.dp)
                            .widthIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        messages.takeLast(3).forEach { msg ->
                            Text(
                                text = "${msg.senderName}: ${msg.content}",
                                color = Color(0xD9FFFFFF),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Floating Live Timeline Reactions Layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 20.dp, end = 20.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        floatingReactions.forEach { item ->
                            key(item.second) {
                                FloatingEmojiItem(emoji = item.first)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sync controller dashboard row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Play / Pause Toggle Button or Majority Vote Button
                val isUserDj = currentRoom.currentDjId.isNullOrEmpty() || currentRoom.currentDjName == "You"
                if (isUserDj) {
                    IconButton(
                        onClick = {
                            if (currentRoom.isVideoPlaying) onPauseVideo() else onPlayVideo()
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = GlassIndigoAccent,
                            contentColor = GlassTextWhite
                        ),
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("play_pause_toggle")
                    ) {
                        Icon(
                            imageVector = if (currentRoom.isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play or Pause synchronized video",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    // Voting Trigger Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { onStartVote(if (currentRoom.isVideoPlaying) "pause" else "play") },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color(0xFF5865F2),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HowToVote,
                                contentDescription = "Vote Play/Pause",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Progress Info & Slider
                val maxDurationMs = 300000L // 5 mins cap default
                val slideProgress = videoProgressMs.coerceIn(0L, maxDurationMs)

                val elapsedSecs = slideProgress / 1000L
                val elapsedMins = elapsedSecs / 60L
                val elapsedRem = elapsedSecs % 60L

                val totalSecs = maxDurationMs / 1000L
                val totalMins = totalSecs / 60L
                val totalRem = totalSecs % 60L

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%02d:%02d", elapsedMins, elapsedRem),
                        color = GlassTextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Slider(
                        value = slideProgress.toFloat(),
                        onValueChange = { newValue ->
                            if (isUserDj) {
                                onSeekVideo(newValue.toLong())
                            }
                        },
                        valueRange = 0f..maxDurationMs.toFloat(),
                        colors = SliderDefaults.colors(
                            activeTrackColor = GlassIndigoAccent,
                            inactiveTrackColor = GlassBorderLow,
                            thumbColor = GlassTextWhite
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(28.dp)
                            .testTag("video_progress_slider")
                    )

                    Text(
                        text = String.format("%02d:%02d", totalMins, totalRem),
                        color = GlassIndigoLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Real-Time Voting overlay Banner inside WatchTogetherPane
            activeVote?.let { vote ->
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xE62F3136), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF5865F2), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🗳️ VOTE TO ${vote.type.uppercase()}:",
                            color = Color(0xFF5865F2),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = vote.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onCastVote(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23A55A)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                "YES (${vote.yesVotes.size})",
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = { onCastVote(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF23F43)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                "NO (${vote.noVotes.size})",
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- LIVE TIMELINE REACTIONS BAR 😂🔥❤️💀 ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("React Live:", color = GlassTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                listOf("😂", "🔥", "❤️", "💀", "😮", "🎉").forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clickable { onSendVideoReaction(emoji) }
                            .padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingEmojiItem(emoji: String, modifier: Modifier = Modifier) {
    val animProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(2000, easing = androidx.compose.animation.core.LinearEasing)
        )
    }
    val yOffset = (150 * (1f - animProgress.value)).dp - 50.dp
    val alpha = 1f - animProgress.value
    Text(
        text = emoji,
        fontSize = 36.sp,
        modifier = modifier
            .offset(y = yOffset)
            .alpha(alpha)
    )
}

// 🌐 REUSABLE IFRAME YOUTUBE WEB PLAYER
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(
    youtubeId: String,
    isPlaying: Boolean,
    progressMs: Long,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.apply {
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
                // Load embedded iframe URL
                val embedUrl = "https://www.youtube.com/embed/$youtubeId?autoplay=1&mute=0&controls=0&origin=https://youtube.com&enablejsapi=1"
                loadUrl(embedUrl)
            }
        },
        update = { webView ->
            val currentUrl = webView.url ?: ""
            val targetUrl = "https://www.youtube.com/embed/$youtubeId?autoplay=1&mute=0&controls=0&origin=https://youtube.com&enablejsapi=1"
            if (!currentUrl.contains(youtubeId)) {
                webView.loadUrl(targetUrl)
            }

            // Sync Play/Pause state and current seek point safely using JavaScript injections
            val progressSec = progressMs / 1000f
            if (isPlaying) {
                webView.evaluateJavascript(
                    "var vid = document.querySelector('video'); if (vid) { vid.play(); if (Math.abs(vid.currentTime - $progressSec) > 3) { vid.currentTime = $progressSec; } }",
                    null
                )
            } else {
                webView.evaluateJavascript(
                    "var vid = document.querySelector('video'); if (vid) { vid.pause(); if (Math.abs(vid.currentTime - $progressSec) > 3) { vid.currentTime = $progressSec; } }",
                    null
                )
            }
        }
    )
}

// 📞 FULL VOIP CALL GRID OVERLAY (WebRTC-like visualizer)
@Composable
fun CallWorkspace(
    viewModel: com.example.ui.HangoutViewModel? = null,
    isCameraOn: Boolean,
    isMuted: Boolean,
    isScreenSharing: Boolean,
    activeMembers: List<Friend>,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onLeaveCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .frostedGlassCard(
                shape = RoundedCornerShape(24.dp),
                backgroundColor = GlassFillLow,
                borderColor = GlassBorderLow
            )
            .testTag("call_workspace_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Live Call Session 🟢",
                color = GlassTextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Grid of 4 caller tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // USER CAMERA/AVATAR TILE
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .frostedGlassCard(
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = GlassFillLow,
                            borderColor = if (!isMuted && !isCameraOn) GlassEmeraldGreen else GlassBorderLow
                        )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isCameraOn) {
                            CameraPreviewContainer(modifier = Modifier.fillMaxSize())
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(GlassIndigoAccent)
                                ) {
                                    Text("👑", fontSize = 20.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "You",
                                    color = GlassTextWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Muted overlay icon
                        if (isMuted) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(GlassRoseRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MicOff,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }

                // SIMULATED FRENDS OVERLAY TILES
                activeMembers.take(2).forEach { friend ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                            .frostedGlassCard(
                                shape = RoundedCornerShape(16.dp),
                                backgroundColor = GlassFillLow,
                                borderColor = if (friend.isSpeaking && !friend.isMuted) GlassEmeraldGreen else GlassBorderLow
                            )
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Video stream mockup or Avatar representation
                            if (friend.isCameraOn) {
                                // Pulsing holographic mockup visual feed
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color(android.graphics.Color.parseColor(friend.colorHex)),
                                                    Color(0xFF0F1012)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "[ CAMERA ON ]",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(friend.colorHex)))
                                    ) {
                                        Text(
                                            text = if (friend.avatarIndex in AVATAR_EMOJIS.indices) AVATAR_EMOJIS[friend.avatarIndex] else "👾",
                                            fontSize = 20.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = friend.name,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Speaking soundwave visualizer overlay
                            if (friend.isSpeaking && !friend.isMuted) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "🎙️ Speaking",
                                        color = Color(0xFF23A55A),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Muted badge
                            if (friend.isMuted) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF23F43)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MicOff,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Screen Share status card row if screen sharing
            AnimatedVisibility(visible = isScreenSharing) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x2223A55A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .border(1.dp, Color(0xFF23A55A), RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = Color(0xFF23A55A)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Active Screen Sharing session! Tap/drag below to broadcast pointer/sketch annotations.",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (viewModel != null) {
                        val cursors by viewModel.memberCursors.collectAsState()
                        AdvancedScreenShareViewport(
                            cursors = cursors,
                            onSendPointer = { x, y, mode -> viewModel.broadcastCursorMove(x, y, isLaser = (mode == "laser")) },
                            currentUsername = viewModel.userProfile.collectAsState().value?.username ?: "User"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action controller buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic button
                IconButton(
                    onClick = onToggleMute,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isMuted) Color(0xFFF23F43) else Color(0xFF35363C),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("toggle_mute_btn")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute or Unmute audio"
                    )
                }

                // Camera button
                IconButton(
                    onClick = onToggleCamera,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isCameraOn) Color(0xFF23A55A) else Color(0xFF35363C),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("toggle_camera_btn")
                ) {
                    Icon(
                        imageVector = if (isCameraOn) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        contentDescription = "Camera Toggle"
                    )
                }

                // Screen share button
                IconButton(
                    onClick = onToggleScreenShare,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isScreenSharing) Color(0xFF23A55A) else Color(0xFF35363C),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("toggle_screenshare_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share screen toggle"
                    )
                }

                // Red End Call button
                IconButton(
                    onClick = onLeaveCall,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFF23F43),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("leave_call_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Leave call"
                    )
                }
            }
        }
    }
}

// 📸 FRONT-CAMERA PREVIEW COMPONENT USING CAMERAX
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPreviewContainer(modifier: Modifier = Modifier) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = modifier,
            update = { previewView ->
                val executor = ContextCompat.getMainExecutor(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, executor)
            }
        )
    } else {
        Box(
            modifier = modifier.background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Camera Permission Required",
                color = Color.White,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// 💬 SINGLE CHAT MESSAGE ITEM WIDGET
@Composable
fun ChatMessageItem(
    message: MessageEntity,
    onToggleReaction: (Long, String) -> Unit
) {
    if (message.isSystem) {
        // System message item
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2B2D31)),
                contentAlignment = Alignment.Center
            ) {
                val iconStr = when {
                    message.content.contains("Welcome", ignoreCase = true) -> "🔔"
                    message.content.contains("Joined", ignoreCase = true) -> "📞"
                    message.content.contains("Playback", ignoreCase = true) -> "▶️"
                    message.content.contains("Paused", ignoreCase = true) -> "⏸️"
                    else -> "🎬"
                }
                Text(iconStr, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message.content,
                color = Color(0xFF949BA4),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        var showEmojiRow by remember { mutableStateOf(false) }

        // User/Friend chat message item
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Sender Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (message.senderAvatarIndex in AVATAR_COLORS.indices) {
                            AVATAR_COLORS[message.senderAvatarIndex]
                        } else {
                            Color(0xFF5865F2)
                        }
                    )
            ) {
                Text(
                    text = if (message.senderAvatarIndex in AVATAR_EMOJIS.indices) {
                        AVATAR_EMOJIS[message.senderAvatarIndex]
                    } else "👾",
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showEmojiRow = !showEmojiRow }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.senderName,
                        color = if (message.senderName == "Alex") Color(0xFFE67E22)
                        else if (message.senderName == "Zoe") Color(0xFFEC407A)
                        else if (message.senderName == "Sam") Color(0xFF00BCD4)
                        else if (message.senderName == "Leo") Color(0xFFFFC107)
                        else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // Simulated simple formatted time stamp
                    val minutesAgo = (System.currentTimeMillis() - message.timestamp) / 60000L
                    val timeStr = if (minutesAgo < 1) "Just now" else "$minutesAgo mins ago"
                    Text(
                        text = timeStr,
                        color = Color(0xFF949BA4),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = message.content,
                    color = Color(0xFFDBDEE1),
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                )

                // Render direct image/meme drop if present
                if (!message.memeUrl.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = message.memeUrl,
                            contentDescription = "Meme drop image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Render inline reaction badge pills
                val parsedReactions = remember(message.reactionsJson) {
                    val map = mutableMapOf<String, List<String>>()
                    if (!message.reactionsJson.isNullOrEmpty()) {
                        try {
                            val json = org.json.JSONObject(message.reactionsJson)
                            val keys = json.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val arr = json.getJSONArray(key)
                                val list = mutableListOf<String>()
                                for (i in 0 until arr.length()) {
                                    list.add(arr.getString(i))
                                }
                                map[key] = list
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    map
                }

                if (parsedReactions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        parsedReactions.forEach { (emoji, names) ->
                            if (names.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x335865F2))
                                        .border(1.dp, Color(0x665865F2), RoundedCornerShape(8.dp))
                                        .clickable { onToggleReaction(message.id, emoji) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(emoji, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = names.size.toString(),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Expandable Tap-to-React popover emoji row
                AnimatedVisibility(
                    visible = showEmojiRow,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(Color(0x7F2B2D31), RoundedCornerShape(12.dp))
                            .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf("👍", "❤️", "😂", "🔥", "💀", "🎉", "😮").forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onToggleReaction(message.id, emoji)
                                        showEmojiRow = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoomTabItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    val activeColor = Color(0xFF5865F2)
    val inactiveColor = Color(0xFF949BA4)
    val color = if (selected) activeColor else inactiveColor

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(8.dp)
            .minimumInteractiveComponentSize()
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 6.dp, y = (-6).dp)
                        .background(Color.Red, shape = CircleShape)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// ==========================================
// PREMIUM SOCIAL WORKSPACE COMPOSABLES
// ==========================================

@Composable
fun AnimatedThemeBackground(vibe: String) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Wave animation for Beach
    val beachWaveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    // Steam animation for Cafe
    val cafeSteamOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    // Flickering projector for Cinema
    val cinemaFlicker by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    // Grid panning for Gaming
    val gamingGridOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    // Rain falling offset
    val rainOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Star twinkle alphas
    val starAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val starAlpha2 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        when (vibe) {
            "beach" -> {
                // Ocean waves and sandy glow
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw sand
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFE94E1B), Color(0xFFF18C4F), Color(0xFFF7C38B))
                        )
                    )
                    
                    // Draw beach wave lines
                    val waveHeight = 40f
                    val wavePoints = mutableListOf<androidx.compose.ui.geometry.Offset>()
                    for (x in 0..size.width.toInt() step 5) {
                        val y = size.height * 0.7f + waveHeight * kotlin.math.sin((x.toFloat() * 0.01f + beachWaveOffset).toDouble()).toFloat()
                        wavePoints.add(androidx.compose.ui.geometry.Offset(x.toFloat(), y))
                    }
                    
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, size.height)
                        wavePoints.forEach { lineTo(it.x, it.y) }
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = Color(0x334DD0E1)
                    )
                }
            }
            "cafe" -> {
                // Cozy brown background with rising warm steam bubbles
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF1E140F), Color(0xFF382319), Color(0xFF4A3428))
                        )
                    )
                    
                    // Rising steam bubbles
                    val bubbleX = listOf(0.15f, 0.35f, 0.5f, 0.7f, 0.85f)
                    val bubbleR = listOf(15f, 25f, 20f, 30f, 18f)
                    
                    for (i in bubbleX.indices) {
                        val x = size.width * bubbleX[i]
                        val rawY = size.height * (1.1f - ((cafeSteamOffset + i * 0.2f) % 1.0f))
                        val alpha = 1.0f - ((cafeSteamOffset + i * 0.2f) % 1.0f)
                        drawCircle(
                            color = Color(0x99D7CCC8).copy(alpha = alpha * 0.3f),
                            radius = bubbleR[i],
                            center = androidx.compose.ui.geometry.Offset(x, rawY)
                        )
                    }
                }
            }
            "cinema" -> {
                // Dim theater projector glowing flicker
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF100003), Color(0xFF1E0105), Color(0xFF0C0002))
                        )
                    )
                    
                    // Projected beam triangle from top-left with flicker
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width * 0.8f, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x33FFB300), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(0f, 0f),
                            radius = size.width
                        ),
                        alpha = cinemaFlicker * 0.4f
                    )
                }
            }
            "gaming" -> {
                // RGB glowing grid lines
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(Color(0xFF0A0210))
                    
                    // Draw perspective horizontal grids
                    val linesCount = 15
                    for (i in 0 until linesCount) {
                        val rawY = size.height * 0.5f + (i * 45f) + (gamingGridOffset % 45f)
                        if (rawY < size.height) {
                            drawLine(
                                color = Color(0x44E040FB),
                                start = androidx.compose.ui.geometry.Offset(0f, rawY),
                                end = androidx.compose.ui.geometry.Offset(size.width, rawY),
                                strokeWidth = 1.5f
                            )
                        }
                    }
                    
                    // Vertical perspective grid lines
                    val verticalCount = 10
                    for (i in 0 until verticalCount) {
                        val startX = size.width * (i.toFloat() / verticalCount)
                        drawLine(
                            color = Color(0x3300E5FF),
                            start = androidx.compose.ui.geometry.Offset(startX, size.height * 0.5f),
                            end = androidx.compose.ui.geometry.Offset(startX + (startX - size.width/2) * 1.5f, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }
            "space" -> {
                // Parallax space stars with animated alphas
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF03030F), Color(0xFF0B0A1A), Color(0xFF15142E))
                        )
                    )
                    
                    // Fixed set of stars
                    val starsX = listOf(0.12f, 0.28f, 0.45f, 0.61f, 0.78f, 0.91f, 0.15f, 0.38f, 0.55f, 0.72f, 0.88f)
                    val starsY = listOf(0.15f, 0.22f, 0.11f, 0.29f, 0.17f, 0.25f, 0.42f, 0.35f, 0.48f, 0.31f, 0.49f)
                    
                    for (i in starsX.indices) {
                        val x = size.width * starsX[i]
                        val y = size.height * starsY[i]
                        val starAlpha = if (i % 2 == 0) starAlpha1 else starAlpha2
                        drawCircle(
                            color = Color.White.copy(alpha = starAlpha * 0.8f),
                            radius = if (i % 3 == 0) 4f else 2.5f,
                            center = androidx.compose.ui.geometry.Offset(x, y)
                        )
                    }
                }
            }
            "rain" -> {
                // Rainy drops falling down
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF0E1319), Color(0xFF1B222C), Color(0xFF2C3949))
                        )
                    )
                    
                    // Falling rain lines
                    val rainCount = 20
                    for (i in 0 until rainCount) {
                        val startX = (i * 73f) % size.width
                        val speedY = 1.2f + (i % 3) * 0.4f
                        val rawY = ((rainOffset * speedY + i * 150f) % size.height)
                        
                        drawLine(
                            color = Color(0x66B2EBF2),
                            start = androidx.compose.ui.geometry.Offset(startX, rawY),
                            end = androidx.compose.ui.geometry.Offset(startX - 5f, rawY + 30f),
                            strokeWidth = 2f
                        )
                    }
                }
            }
            else -> {
                // Default gradient
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF131324), Color(0xFF1F1F35), Color(0xFF0D0D14))
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SharedBrowserPanel(
    browserState: com.example.ui.HangoutViewModel.BrowserState,
    onNavigate: (String) -> Unit,
    onScroll: (Float) -> Unit,
    onToggleLeader: (Boolean) -> Unit,
    currentUsername: String
) {
    var urlInput by remember { mutableStateOf(browserState.url) }
    val scrollState = rememberScrollState()
    
    // Broadcast scroll percentage
    LaunchedEffect(scrollState.value) {
        if (scrollState.maxValue > 0) {
            val percent = scrollState.value.toFloat() / scrollState.maxValue
            val diff = percent - browserState.scrollPercentY
            if (diff > 0.05f || diff < -0.05f) {
                onScroll(percent)
            }
        }
    }

    // Follow synchronized scroll
    LaunchedEffect(browserState.scrollPercentY) {
        if (scrollState.maxValue > 0) {
            val targetScroll = (browserState.scrollPercentY * scrollState.maxValue).toInt()
            val diffScroll = scrollState.value - targetScroll
            if (diffScroll > 10 || diffScroll < -10) {
                scrollState.scrollTo(targetScroll)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Address Bar and mode toggle
        Card(
            colors = CardDefaults.cardColors(containerColor = GlassFillMedium),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🌐 Shared Browser",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    
                    // Leader Mode toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Leader Mode",
                            color = Color(0xFF949BA4),
                            fontSize = 11.sp
                        )
                        Switch(
                            checked = browserState.isLeaderMode,
                            onCheckedChange = { onToggleLeader(it) },
                            modifier = Modifier.scale(0.7f).testTag("browser_leader_toggle")
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (browserState.isLeaderMode) {
                    Text(
                        text = "🔒 Leader: ${browserState.leaderName} (Only leader can control)",
                        color = GlassYellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = { onNavigate("https://google.com") }) {
                        Icon(Icons.Default.Home, "Home", tint = Color.White)
                    }
                    
                    TextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("Enter URL or search...", color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("browser_url_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0x1AFFFFFF),
                            unfocusedContainerColor = Color(0x0DFFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                    
                    IconButton(
                        onClick = { onNavigate(urlInput) },
                        modifier = Modifier.background(GlassIndigoColor, CircleShape).size(36.dp).testTag("browser_go_btn")
                    ) {
                        Icon(Icons.Default.ArrowForward, "Go", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Shortcut Quick Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shortcuts = listOf(
                "Wikipedia" to "https://wikipedia.org",
                "Google" to "https://google.com",
                "YouTube" to "https://youtube.com",
                "Reddit" to "https://reddit.com",
                "GitHub" to "https://github.com"
            )
            shortcuts.forEach { (name, url) ->
                Box(
                    modifier = Modifier
                        .background(Color(0x1FFFFFFF), RoundedCornerShape(16.dp))
                        .clickable {
                            urlInput = url
                            onNavigate(url)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Simulated high-fidelity content viewport frame
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, GlassBorderLow, RoundedCornerShape(16.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Browser viewport header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(Color.Red, CircleShape))
                            Box(modifier = Modifier.size(8.dp).background(Color.Yellow, CircleShape))
                            Box(modifier = Modifier.size(8.dp).background(Color.Green, CircleShape))
                        }
                        Text(
                            text = browserState.title,
                            color = Color(0xFF949BA4),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    HorizontalDivider(color = Color(0x1AFFFFFF))

                    // Dynamic simulated web content based on the URL
                    val host = browserState.url.substringAfter("://").substringBefore("/")
                    if (host.contains("wikipedia")) {
                        Text(
                            text = "📖 Wikipedia: The Free Encyclopedia",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Wikipedia is a free-content online encyclopedia written and maintained by a community of volunteers, known as Wikipedians, through open collaboration.",
                            color = Color(0xFFE3E5E8),
                            fontSize = 13.sp
                        )
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Today's Featured Article", color = GlassYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("The Space Station is a modular spacecraft in low Earth orbit. It is a collaborative project between five participating space agencies.", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    } else if (host.contains("google")) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "G o o g l e",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)), shape = RoundedCornerShape(24.dp)) {
                                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Search, "Search", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Search the workspace...", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    } else if (host.contains("youtube")) {
                        Text("📺 YouTube Shared Browser Hub", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f).height(100.dp).background(Color(0x33FFFFFF), RoundedCornerShape(8.dp)))
                            Box(modifier = Modifier.weight(1f).height(100.dp).background(Color(0x33FFFFFF), RoundedCornerShape(8.dp)))
                        }
                        Text("Trending Collaborative Videos:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        // Generic high-fidelity web page layout
                        Text("🌐 Web View Portal: $host", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        
                        Text(
                            text = "Collaborative co-browsing lets multiple friends view, interact, and browse any URL in absolute sync. When you scroll, type, or navigate, everyone else in the room follows along instantly!",
                            color = Color(0xFFE3E5E8),
                            fontSize = 13.sp
                        )
                        
                        // Fake article elements
                        for (i in 1..4) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(modifier = Modifier.size(48.dp).background(Color(0x1AFFFFFF), RoundedCornerShape(8.dp)))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Social Space Article #$i", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("This is an elegant article chunk in our simulated interactive viewport frame.", color = Color(0xFF949BA4), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveWhiteboardPanel(
    strokes: List<com.example.ui.HangoutViewModel.WhiteboardStroke>,
    stickies: List<com.example.ui.HangoutViewModel.StickyNote>,
    currentUsername: String,
    onDrawStroke: (com.example.ui.HangoutViewModel.WhiteboardStroke) -> Unit,
    onClear: () -> Unit,
    onAddSticky: (String, Float, Float, String) -> Unit,
    onMoveSticky: (String, Float, Float) -> Unit,
    onDeleteSticky: (String) -> Unit
) {
    var selectedColor by remember { mutableStateOf("#FF5722") }
    var selectedTool by remember { mutableStateOf("pen") } // "pen", "eraser", "rect", "circle"
    var showStickyDialog by remember { mutableStateOf(false) }
    var stickyText by remember { mutableStateOf("") }
    var stickyColor by remember { mutableStateOf("#FFEB3B") } // standard sticky yellow
    
    val context = LocalContext.current
    val currentPathPoints = remember { mutableStateListOf<com.example.ui.HangoutViewModel.WhiteboardPoint>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Toolbar controls
        Card(
            colors = CardDefaults.cardColors(containerColor = GlassFillMedium),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tool selection
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val tools = listOf(
                        "pen" to "✏️",
                        "eraser" to "🧽",
                        "rect" to "⬜",
                        "circle" to "⭕"
                    )
                    tools.forEach { (tool, emoji) ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (selectedTool == tool) GlassIndigoColor else Color(0x1FFFFFFF),
                                    CircleShape
                                )
                                .clickable { selectedTool = tool }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 14.sp)
                        }
                    }
                }

                // Colors selection
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val colors = listOf("#FF5722", "#00BCD4", "#9C27B0", "#FFC107", "#FFFFFF")
                    colors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                                .border(
                                    if (selectedColor == hex) 2.dp else 0.dp,
                                    Color.White,
                                    CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }

                // Custom actions
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { showStickyDialog = true },
                        modifier = Modifier.background(Color(0x33FFFFFF), CircleShape).size(36.dp).testTag("wb_sticky_btn")
                    ) {
                        Text("📌", fontSize = 14.sp)
                    }
                    IconButton(
                        onClick = {
                            onClear()
                            Toast.makeText(context, "Whiteboard cleared!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.background(Color(0x33FFFFFF), CircleShape).size(36.dp).testTag("wb_clear_btn")
                    ) {
                        Icon(Icons.Default.Delete, "Clear", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Button(
                        onClick = {
                            Toast.makeText(context, "🖼️ Whiteboard exported successfully to Gallery!", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GlassIndigoColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(36.dp).testTag("wb_export_btn")
                    ) {
                        Text("Export", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Main Drawing Canvas & Sticky Notes Overlay area
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141424)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, GlassBorderLow, RoundedCornerShape(16.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectedTool, selectedColor) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                currentPathPoints.clear()
                                currentPathPoints.add(com.example.ui.HangoutViewModel.WhiteboardPoint(startOffset.x, startOffset.y))
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentPathPoints.add(com.example.ui.HangoutViewModel.WhiteboardPoint(change.position.x, change.position.y))
                            },
                            onDragEnd = {
                                if (currentPathPoints.isNotEmpty()) {
                                    onDrawStroke(
                                        com.example.ui.HangoutViewModel.WhiteboardStroke(
                                            points = currentPathPoints.toList(),
                                            colorHex = if (selectedTool == "eraser") "#141424" else selectedColor,
                                            thickness = if (selectedTool == "eraser") 40f else 8f,
                                            isEraser = selectedTool == "eraser",
                                            shapeType = selectedTool
                                        )
                                    )
                                    currentPathPoints.clear()
                                }
                            }
                        )
                    }
            ) {
                // Drawing Canvas
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw existing strokes
                    strokes.forEach { stroke ->
                        val strokeColor = Color(android.graphics.Color.parseColor(stroke.colorHex))
                        if (stroke.points.size > 1) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(stroke.points[0].x, stroke.points[0].y)
                                for (i in 1 until stroke.points.size) {
                                    lineTo(stroke.points[i].x, stroke.points[i].y)
                                }
                            }
                            
                            if (stroke.shapeType == "rect" && stroke.points.size >= 2) {
                                val p1 = stroke.points[0]
                                val p2 = stroke.points.last()
                                drawRect(
                                    color = strokeColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(kotlin.math.min(p1.x, p2.x), kotlin.math.min(p1.y, p2.y)),
                                    size = androidx.compose.ui.geometry.Size(kotlin.math.abs(p2.x - p1.x), kotlin.math.abs(p2.y - p1.y)),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke.thickness)
                                )
                            } else if (stroke.shapeType == "circle" && stroke.points.size >= 2) {
                                val p1 = stroke.points[0]
                                val p2 = stroke.points.last()
                                val radius = kotlin.math.sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y)) / 2f
                                drawCircle(
                                    color = strokeColor,
                                    radius = radius,
                                    center = androidx.compose.ui.geometry.Offset((p1.x + p2.x)/2f, (p1.y + p2.y)/2f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke.thickness)
                                )
                            } else {
                                drawPath(
                                    path = path,
                                    color = strokeColor,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = stroke.thickness,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }

                    // Draw active path
                    if (currentPathPoints.size > 1) {
                        val activeColor = Color(android.graphics.Color.parseColor(if (selectedTool == "eraser") "#141424" else selectedColor))
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(currentPathPoints[0].x, currentPathPoints[0].y)
                            for (i in 1 until currentPathPoints.size) {
                                lineTo(currentPathPoints[i].x, currentPathPoints[i].y)
                            }
                        }
                        
                        if (selectedTool == "rect") {
                            val p1 = currentPathPoints[0]
                            val p2 = currentPathPoints.last()
                            drawRect(
                                color = activeColor,
                                topLeft = androidx.compose.ui.geometry.Offset(kotlin.math.min(p1.x, p2.x), kotlin.math.min(p1.y, p2.y)),
                                size = androidx.compose.ui.geometry.Size(kotlin.math.abs(p2.x - p1.x), kotlin.math.abs(p2.y - p1.y)),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f)
                            )
                        } else if (selectedTool == "circle") {
                            val p1 = currentPathPoints[0]
                            val p2 = currentPathPoints.last()
                            val radius = kotlin.math.sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y)) / 2f
                            drawCircle(
                                color = activeColor,
                                radius = radius,
                                center = androidx.compose.ui.geometry.Offset((p1.x + p2.x)/2f, (p1.y + p2.y)/2f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f)
                            )
                        } else {
                            drawPath(
                                path = path,
                                color = activeColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = if (selectedTool == "eraser") 40f else 8f,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        }
                    }
                }

                // Render Sticky Notes overlay
                stickies.forEach { sticky ->
                    var stickyOffset by remember(sticky.id) { mutableStateOf(androidx.compose.ui.geometry.Offset(sticky.x, sticky.y)) }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(android.graphics.Color.parseColor(sticky.colorHex))),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .offset(stickyOffset.x.dp, stickyOffset.y.dp)
                            .width(120.dp)
                            .border(1.dp, Color(0x33000000), RoundedCornerShape(4.dp))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val nextX = stickyOffset.x + dragAmount.x / 3f
                                    val nextY = stickyOffset.y + dragAmount.y / 3f
                                    stickyOffset = androidx.compose.ui.geometry.Offset(nextX, nextY)
                                    onMoveSticky(sticky.id, nextX, nextY)
                                }
                            }
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(sticky.author, fontSize = 8.sp, color = Color.Black.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                                Icon(
                                    Icons.Default.Close,
                                    "Delete Sticky",
                                    tint = Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier.size(10.dp).clickable { onDeleteSticky(sticky.id) }
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = sticky.text,
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Sticky dialogue popup
        if (showStickyDialog) {
            AlertDialog(
                onDismissRequest = { showStickyDialog = false },
                title = { Text("Add Sticky Note", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = stickyText,
                            onValueChange = { stickyText = it },
                            placeholder = { Text("Enter sticky message...", color = Color.Gray) },
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth().testTag("sticky_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val colors = listOf("#FFEB3B" to "🟡", "#A7FFEB" to "🟢", "#FF80AB" to "🔴", "#82B1FF" to "🔵")
                            colors.forEach { (hex, emoji) ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            if (stickyColor == hex) GlassIndigoColor else Color.Transparent,
                                            CircleShape
                                        )
                                        .clickable { stickyColor = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (stickyText.isNotBlank()) {
                                onAddSticky(stickyText, 50f + stickies.size * 20f, 150f + stickies.size * 20f, stickyColor)
                                stickyText = ""
                                showStickyDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GlassIndigoColor)
                    ) {
                        Text("Add", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStickyDialog = false }) {
                        Text("Cancel", color = Color.LightGray)
                    }
                },
                containerColor = Color(0xFF141424)
            )
        }
    }
}

@Composable
fun SharedCloudFilesPanel(
    files: List<com.example.ui.HangoutViewModel.CloudFile>,
    onUpload: (String, String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showUploadDialog by remember { mutableStateOf(false) }
    var uploadName by remember { mutableStateOf("") }
    var uploadType by remember { mutableStateOf("image") }
    
    // File Previews
    var activePreviewFile by remember { mutableStateOf<com.example.ui.HangoutViewModel.CloudFile?>(null) }
    var pdfActivePage by remember { mutableStateOf(1) }

    val filteredFiles = files.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Dropzone & Search Bar
        Card(
            colors = CardDefaults.cardColors(containerColor = GlassFillMedium),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "📁 Shared Room Cloud Files",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                
                // Drag and drop zone overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(Color(0x0DFFFFFF), RoundedCornerShape(8.dp))
                        .border(1.dp, GlassBorderLow, RoundedCornerShape(8.dp))
                        .clickable { showUploadDialog = true }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📥 Drag & Drop Files Here", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("or click to upload image, video, PDF, or ZIP", color = Color(0xFF949BA4), fontSize = 11.sp)
                    }
                }

                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search files...", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("files_search"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0x1AFFFFFF),
                        unfocusedContainerColor = Color(0x0DFFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }

        // Files List
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filteredFiles.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No cloud files found.", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                items(filteredFiles) { file ->
                    val emoji = when (file.type) {
                        "image" -> "🖼️"
                        "pdf" -> "📄"
                        "video" -> "🎬"
                        else -> "📦"
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x1FFFFFFF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                activePreviewFile = file
                                pdfActivePage = 1
                            }
                            .testTag("file_item_${file.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(emoji, fontSize = 24.sp)
                                Column {
                                    Text(file.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Size: ${file.size} • Uploaded by: ${file.uploader}", color = Color(0xFF949BA4), fontSize = 11.sp)
                                }
                            }
                            
                            IconButton(onClick = { onDelete(file.id) }) {
                                Icon(Icons.Default.Delete, "Delete File", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Active File Preview Modal
        activePreviewFile?.let { file ->
            AlertDialog(
                onDismissRequest = { activePreviewFile = null },
                title = { Text("File Preview: ${file.name}", color = Color.White, fontSize = 14.sp) },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(Color(0xFF0F0F1A), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (file.type) {
                            "image" -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Box(
                                        modifier = Modifier
                                            .size(140.dp)
                                            .background(Color(0x33FFFFFF), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🌅 Image Preview", color = Color.White, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("lounge_beach_mockup.png", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text("High-fidelity workspace artwork", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                            "pdf" -> {
                                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text("📄 PDF Reader Simulation", color = GlassYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .background(Color.White, RoundedCornerShape(4.dp))
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Space Station Blueprint - Page $pdfActivePage\n\n[SIMULATED BLUEPRINT SCHEMATICS]\n• Structural module docking points\n• Solar panel alignment arrays.",
                                            color = Color.Black,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = { if (pdfActivePage > 1) pdfActivePage-- }) {
                                            Text("Prev", color = Color.White)
                                        }
                                        Text("Page $pdfActivePage / 3", color = Color.White, fontSize = 12.sp)
                                        TextButton(onClick = { if (pdfActivePage < 3) pdfActivePage++ }) {
                                            Text("Next", color = Color.White)
                                        }
                                    }
                                }
                            }
                            "video" -> {
                                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.PlayCircle, "Play Video", tint = Color.White, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Playing lofi_ambient_track_v2.mp3", color = Color.White, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(progress = 0.35f, color = GlassIndigoColor, modifier = Modifier.fillMaxWidth(0.8f))
                                }
                            }
                            "zip" -> {
                                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📦 ZIP Archive Browser", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
                                        modifier = Modifier.weight(1f).fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("📂 retro_arcade_sprites.zip", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("  ├── 📂 characters/", color = Color(0xFF949BA4), fontSize = 11.sp)
                                            Text("  │   ├── 🖼️ hero_idle.png (24 KB)", color = Color(0xFFE3E5E8), fontSize = 11.sp)
                                            Text("  │   └── 🖼️ enemy_patrol.png (18 KB)", color = Color(0xFFE3E5E8), fontSize = 11.sp)
                                            Text("  ├── 📂 sounds/", color = Color(0xFF949BA4), fontSize = 11.sp)
                                            Text("  │   └── 🔊 game_over.wav (140 KB)", color = Color(0xFFE3E5E8), fontSize = 11.sp)
                                            Text("  └── 📄 readme.txt (1.2 KB)", color = Color(0xFFE3E5E8), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { activePreviewFile = null },
                        colors = ButtonDefaults.buttonColors(containerColor = GlassIndigoColor)
                    ) {
                        Text("Close", color = Color.White)
                    }
                },
                containerColor = Color(0xFF141424)
            )
        }

        // Upload dialog simulation
        if (showUploadDialog) {
            AlertDialog(
                onDismissRequest = { showUploadDialog = false },
                title = { Text("Upload File", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = uploadName,
                            onValueChange = { uploadName = it },
                            placeholder = { Text("Enter file name (e.g. project_idea.pdf)", color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("upload_name")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val types = listOf("image" to "🖼️", "pdf" to "📄", "video" to "🎬", "zip" to "📦")
                            types.forEach { (type, emoji) ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            if (uploadType == type) GlassIndigoColor else Color.Transparent,
                                            CircleShape
                                        )
                                        .clickable { uploadType = type },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (uploadName.isNotBlank()) {
                                val size = "${(1..15).random()}.${(0..9).random()} MB"
                                onUpload(uploadName, size, uploadType)
                                uploadName = ""
                                showUploadDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GlassIndigoColor)
                    ) {
                        Text("Upload", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUploadDialog = false }) {
                        Text("Cancel", color = Color.LightGray)
                    }
                },
                containerColor = Color(0xFF141424)
            )
        }
    }
}

@Composable
fun RoomPollsPanel(
    polls: List<com.example.ui.HangoutViewModel.RoomPoll>,
    currentUsername: String,
    onCreatePoll: (String, List<String>, Boolean) -> Unit,
    onCastVote: (String, Int) -> Unit,
    onClosePoll: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var questionInput by remember { mutableStateOf("") }
    var optionInput1 by remember { mutableStateOf("") }
    var optionInput2 by remember { mutableStateOf("") }
    var optionInput3 by remember { mutableStateOf("") }
    var isAnonInput by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Poll Head and Creation Button
        Card(
            colors = CardDefaults.cardColors(containerColor = GlassFillMedium),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📊 Room Polls & Voting",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Instant visual opinion checks",
                        color = Color(0xFF949BA4),
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = GlassIndigoColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("create_poll_btn")
                ) {
                    Text("+ Create", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Active Polls Feed
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (polls.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No active polls. Launch one above!", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                items(polls) { poll ->
                    val totalVotes = poll.votes.values.sum()
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x11FFFFFF)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, GlassBorderLow, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Asked by ${poll.creator}",
                                    color = Color(0xFF949BA4),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (poll.isClosed) Color(0xFFF04747) else Color(0xFF23A55A),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (poll.isClosed) "CLOSED" else "ACTIVE",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = poll.question,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Options Bars
                            poll.options.forEachIndexed { index, option ->
                                val optVotes = poll.votes[index] ?: 0
                                val percent = if (totalVotes > 0) (optVotes.toFloat() / totalVotes) else 0f
                                val hasVotedForThis = poll.voterChoices[currentUsername] == index

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x0AFFFFFF))
                                        .clickable(enabled = !poll.isClosed) { onCastVote(poll.id, index) }
                                ) {
                                    // Animated horizontal progress bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(percent.coerceAtLeast(0.01f))
                                            .fillMaxHeight()
                                            .align(Alignment.CenterStart)
                                            .background(if (hasVotedForThis) GlassIndigoColor.copy(alpha = 0.4f) else Color(0x1FFFFFFF))
                                            .padding(vertical = 12.dp)
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (hasVotedForThis) {
                                                Text("✅", fontSize = 12.sp)
                                            }
                                            Text(
                                                text = option,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (hasVotedForThis) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                        Text(
                                            text = "$optVotes (${(percent * 100).toInt()}%)",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                // Show voter lists if not anonymous
                                if (!poll.isAnonymous && totalVotes > 0) {
                                    val voters = poll.voterChoices.filter { it.value == index }.keys
                                    if (voters.isNotEmpty()) {
                                        Text(
                                            text = "   └─ Voters: ${voters.joinToString(", ")}",
                                            color = Color(0xFF949BA4),
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total votes: $totalVotes • Mode: ${if (poll.isAnonymous) "🔒 Anonymous" else "👥 Public"}",
                                    color = Color(0xFF949BA4),
                                    fontSize = 11.sp
                                )

                                if (poll.creator == currentUsername && !poll.isClosed) {
                                    TextButton(onClick = { onClosePoll(poll.id) }) {
                                        Text("Close Poll", color = Color(0xFFF04747), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Create Dialog
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create New Room Poll", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = questionInput,
                            onValueChange = { questionInput = it },
                            placeholder = { Text("Ask a question...", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth().testTag("poll_question"),
                            singleLine = true
                        )
                        TextField(
                            value = optionInput1,
                            onValueChange = { optionInput1 = it },
                            placeholder = { Text("Option 1", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        TextField(
                            value = optionInput2,
                            onValueChange = { optionInput2 = it },
                            placeholder = { Text("Option 2", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        TextField(
                            value = optionInput3,
                            onValueChange = { optionInput3 = it },
                            placeholder = { Text("Option 3 (Optional)", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text("Anonymous Voting", color = Color.White, fontSize = 13.sp)
                            Switch(
                                checked = isAnonInput,
                                onCheckedChange = { isAnonInput = it }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (questionInput.isNotBlank() && optionInput1.isNotBlank() && optionInput2.isNotBlank()) {
                                val opts = mutableListOf(optionInput1.trim(), optionInput2.trim())
                                if (optionInput3.isNotBlank()) {
                                    opts.add(optionInput3.trim())
                                }
                                onCreatePoll(questionInput.trim(), opts, isAnonInput)
                                questionInput = ""
                                optionInput1 = ""
                                optionInput2 = ""
                                optionInput3 = ""
                                isAnonInput = false
                                showCreateDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GlassIndigoColor)
                    ) {
                        Text("Create", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel", color = Color.LightGray)
                    }
                },
                containerColor = Color(0xFF141424)
            )
        }
    }
}

@Composable
fun AdvancedScreenShareViewport(
    cursors: Map<String, com.example.ui.HangoutViewModel.MemberCursor>,
    onSendPointer: (Float, Float, String) -> Unit,
    currentUsername: String
) {
    var selectedMode by remember { mutableStateOf("mouse") } // "mouse", "laser", "annotation"
    val annotations = remember { mutableStateListOf<Pair<androidx.compose.ui.geometry.Offset, String>>() } // path point to color
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F1A)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(vertical = 8.dp)
            .border(1.dp, GlassBorderLow, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Mode Select Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x1AFFFFFF))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🖥️ Screen Stream", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val modes = listOf("mouse" to "🖱️ Mouse", "laser" to "🔴 Laser", "annotation" to "✏️ Sketch")
                    modes.forEach { (mode, label) ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (selectedMode == mode) GlassIndigoColor else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedMode = mode }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    if (annotations.isNotEmpty()) {
                        IconButton(
                            onClick = { annotations.clear() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Delete, "Clear Sketches", tint = Color.LightGray, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            // Interactive screen-sharing canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(selectedMode) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val pos = change.position
                            onSendPointer(pos.x, pos.y, selectedMode)
                            
                            if (selectedMode == "annotation") {
                                annotations.add(Pair(pos, "#E040FB"))
                            }
                        }
                    }
            ) {
                // Shared screen simulated content (such as a modern workspace UI mockup)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f).height(45.dp).background(Color(0x0DFFFFFF), RoundedCornerShape(8.dp)))
                        Box(modifier = Modifier.weight(2f).height(45.dp).background(Color(0x19FFFFFF), RoundedCornerShape(8.dp)))
                    }
                    Text(
                        text = "📱 [ REMOTE DESKTOP WORKSPACE SCREEN ]",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    LinearProgressIndicator(progress = 0.65f, color = Color(0x3300E5FF), modifier = Modifier.fillMaxWidth())
                }

                // Render annotations
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    annotations.forEach { (pos, colorHex) ->
                        drawCircle(
                            color = Color(android.graphics.Color.parseColor(colorHex)),
                            radius = 6f,
                            center = pos
                        )
                    }
                }

                // Render other participants' floating cursors & laser trails
                for ((username, ptr) in cursors) {
                    val cursorOffset = androidx.compose.ui.geometry.Offset(ptr.x, ptr.y)
                    if (ptr.isLaser) {
                        // Draw glowing pulse for laser pointer
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFFF1744), Color.Transparent),
                                    center = cursorOffset,
                                    radius = 24f
                                ),
                                center = cursorOffset
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 4f,
                                center = cursorOffset
                            )
                        }
                    } else {
                        // Standard mouse pointer arrow with member name flag
                        Box(
                            modifier = Modifier.offset(ptr.x.dp, ptr.y.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        Color(android.graphics.Color.parseColor(ptr.colorHex)),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("🖱️", fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(username, color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
