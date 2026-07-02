package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.ui.HangoutViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.RoomScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HangoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val snackbarHostState = remember { SnackbarHostState() }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        HangoutAppContent(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HangoutAppContent(
    viewModel: HangoutViewModel,
    modifier: Modifier = Modifier
) {
    // Collect ViewModel State Flows
    val userProfile by viewModel.userProfile.collectAsState()
    val rooms by viewModel.rooms.collectAsState()
    val currentRoomId by viewModel.currentRoomId.collectAsState()
    val activeRoom by viewModel.activeRoom.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val activeMembers by viewModel.activeMembers.collectAsState()
    val isInCall by viewModel.isInCall.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isCameraOn by viewModel.isCameraOn.collectAsState()
    val isScreenSharing by viewModel.isScreenSharing.collectAsState()
    val typingFriend by viewModel.typingFriend.collectAsState()
    val videoProgressMs by viewModel.localVideoProgress.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    val activeVote by viewModel.activeVote.collectAsState()
    val ticTacToe by viewModel.ticTacToe.collectAsState()

    // Slide-fade animations during navigation
    AnimatedContent(
        targetState = userProfile,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "auth_navigation_transition",
        modifier = modifier.fillMaxSize()
    ) { profile ->
        if (profile == null || !profile.isLoggedIn) {
            // Auth / Profile Nickname Setup Screen
            AuthScreen(
                onLoginSuccess = { username, avatarIndex ->
                    viewModel.login(username, avatarIndex)
                },
                onMemberLogin = { username, password ->
                    viewModel.loginMember(username, password)
                },
                onMemberRegister = { username, password, avatarIndex ->
                    viewModel.registerMember(username, password, avatarIndex)
                }
            )
        } else {
            // Main App Workspace Content
            AnimatedContent(
                targetState = currentRoomId,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "room_navigation_transition",
                modifier = Modifier.fillMaxSize()
            ) { roomId ->
                if (roomId == null || activeRoom == null) {
                    // Dashboard View (Select or Create Custom Lounge Room)
                    DashboardScreen(
                        userProfile = profile,
                        rooms = rooms,
                        serverUrl = serverUrl,
                        onUpdateServerUrl = { url ->
                            viewModel.updateServerUrl(url)
                        },
                        onCreateRoom = { name ->
                            viewModel.createRoom(name)
                        },
                        onJoinRoom = { id ->
                            viewModel.joinRoom(id)
                        },
                        onLogout = {
                            viewModel.logout()
                        }
                    )
                } else {
                    // Core Real-Time Friend Hangout Room Space
                    RoomScreen(
                        viewModel = viewModel,
                        room = activeRoom!!,
                        messages = messages,
                        activeMembers = activeMembers,
                        isInCall = isInCall,
                        isMuted = isMuted,
                        isCameraOn = isCameraOn,
                        isScreenSharing = isScreenSharing,
                        typingFriend = typingFriend,
                        videoProgressMs = videoProgressMs,
                        presetStreams = viewModel.presetStreams,
                        activeVote = activeVote,
                        ticTacToe = ticTacToe,
                        videoReactionsFlow = viewModel.videoReactions,
                        pingEventFlow = viewModel.pingEvent,
                        presetQuickReplies = viewModel.presetQuickReplies,
                        onSendMessage = { content ->
                            viewModel.sendChatMessage(content)
                        },
                        onToggleCall = {
                            viewModel.toggleCall()
                        },
                        onToggleMute = {
                            viewModel.toggleMute()
                        },
                        onToggleCamera = {
                            viewModel.toggleCamera()
                        },
                        onToggleScreenShare = {
                            viewModel.toggleScreenSharing()
                        },
                        onChangeVideo = { url, title, duration ->
                            viewModel.changeVideo(url, title, duration)
                        },
                        onPlayVideo = {
                            viewModel.playVideo()
                        },
                        onPauseVideo = {
                            viewModel.pauseVideo()
                        },
                        onSeekVideo = { ms ->
                            viewModel.seekVideo(ms)
                        },
                        onLeaveRoom = {
                            viewModel.leaveRoom()
                        },
                        onSendVideoReaction = { emoji ->
                            viewModel.sendVideoReaction(emoji)
                        },
                        onToggleMessageReaction = { msgId, emoji ->
                            viewModel.toggleMessageReaction(msgId, emoji)
                        },
                        onShareMeme = { url ->
                            viewModel.shareMeme(url)
                        },
                        onPingFriend = { targetId ->
                            viewModel.pingFriend(targetId)
                        },
                        onStartTicTacToe = { pX, pO ->
                            viewModel.startTicTacToe(pX, pO)
                        },
                        onMakeTicTacToeMove = { idx ->
                            viewModel.makeTicTacToeMove(idx)
                        },
                        onResetTicTacToe = {
                            viewModel.resetTicTacToe()
                        },
                        onChangeVibe = { vibe ->
                            viewModel.changeVibe(vibe)
                        },
                        onClaimDj = {
                            viewModel.claimDj()
                        },
                        onReleaseDj = {
                            viewModel.releaseDj()
                        },
                        onStartVote = { type ->
                            viewModel.startPlayPauseVote(type)
                        },
                        onCastVote = { yes ->
                            viewModel.castVote(yes)
                        },
                        connectionError = connectionError
                    )
                }
            }
        }
    }
}
