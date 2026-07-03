package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RoomEntity
import com.example.data.UserProfileEntity
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userProfile: UserProfileEntity,
    rooms: List<RoomEntity>,
    serverUrl: String,
    onUpdateServerUrl: (String) -> Unit,
    onCreateRoom: (name: String) -> Unit,
    onJoinRoom: (roomId: String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var tempServerUrl by remember(serverUrl) { mutableStateOf(serverUrl) }

    var newRoomName by remember { mutableStateOf("") }
    var joinRoomCode by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .frostedGlassBackground(),
        containerColor = Color.Transparent, // Allow frosted background to shine through
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = GlassIndigoAccent,
                contentColor = GlassTextWhite,
                shape = CircleShape,
                modifier = Modifier
                    .testTag("create_room_fab")
                    .border(1.dp, GlassBorderHigh, CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create new hangout room")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // User Profile Header Block styled as a premium frosted pane
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(24.dp),
                        backgroundColor = GlassFillLow,
                        borderColor = GlassBorderLow
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // User Avatar
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    if (userProfile.avatarIndex in AVATAR_COLORS.indices) {
                                        AVATAR_COLORS[userProfile.avatarIndex]
                                    } else {
                                        GlassIndigoAccent
                                    }
                                )
                        ) {
                            Text(
                                text = if (userProfile.avatarIndex in AVATAR_EMOJIS.indices) {
                                    AVATAR_EMOJIS[userProfile.avatarIndex]
                                } else "👾",
                                fontSize = 24.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = userProfile.username,
                                color = GlassTextWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Status: Online Lounge Guest",
                                color = GlassEmeraldGreen, // Emerald green
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Settings Button
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Backend Settings",
                                tint = GlassIndigoLight
                            )
                        }

                        // Logout Button with glowing red alert color
                        IconButton(
                            onClick = onLogout,
                            modifier = Modifier.testTag("logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Logout current user",
                                tint = GlassRoseRed
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Join Actions (Enter Code Row styled to match the glass design)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showJoinDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassFillMedium,
                        contentColor = GlassTextWhite
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorderLow),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("join_by_code_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = GlassIndigoLight,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Join Room via Code",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlassTextWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section Header in lightweight Indigo label style
            Text(
                text = "ACTIVE HANGOUT ROOMS (${rooms.size})",
                color = GlassIndigoLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (rooms.isEmpty()) {
                // Empty State with custom glass box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .frostedGlassCard(
                            shape = RoundedCornerShape(24.dp),
                            backgroundColor = GlassFillLow,
                            borderColor = GlassBorderLow
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🚪",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No active rooms available",
                            color = GlassTextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Click the + button below to create your custom hangout room, or enter a room code!",
                            color = GlassTextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                // Active Rooms List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(rooms) { room ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .frostedGlassCard(
                                    shape = RoundedCornerShape(20.dp),
                                    backgroundColor = GlassFillLow,
                                    borderColor = GlassBorderLow
                                )
                                .clickable { onJoinRoom(room.id) }
                                .testTag("room_card_${room.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(GlassFillMedium)
                                            .border(1.dp, GlassBorderLow, RoundedCornerShape(12.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MeetingRoom,
                                            contentDescription = null,
                                            tint = GlassIndigoAccent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column {
                                        Text(
                                            text = room.name,
                                            color = GlassTextWhite,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Code: ${room.id}",
                                            color = GlassIndigoLight,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Text(
                                    text = "Join ➔",
                                    color = GlassIndigoLight,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // Avoid overlap with FAB
                    }
                }
            }
        }
    }

    // CREATE ROOM DIALOG
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newRoomName = ""
            },
            containerColor = Color(0xF210101C), // Deep premium space glass
            title = {
                Text(
                    text = "Create Hangout Room",
                    color = GlassTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Give your room a fun, friendly name!",
                        color = GlassIndigoLight,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newRoomName,
                        onValueChange = { newRoomName = it },
                        placeholder = { Text("e.g. Cinema & Popcorn Lounge", color = GlassTextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GlassTextWhite,
                            unfocusedTextColor = GlassTextWhite,
                            focusedBorderColor = GlassIndigoAccent,
                            unfocusedBorderColor = GlassBorderLow,
                            focusedContainerColor = GlassFillLow,
                            unfocusedContainerColor = GlassFillLow
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_room_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newRoomName.isNotBlank()) {
                            onCreateRoom(newRoomName.trim())
                            showCreateDialog = false
                            newRoomName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlassIndigoAccent)
                ) {
                    Text("Create Room", fontWeight = FontWeight.Bold, color = GlassTextWhite)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        newRoomName = ""
                    }
                ) {
                    Text("Cancel", color = GlassIndigoLight)
                }
            }
        )
    }

    // JOIN ROOM DIALOG
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = {
                showJoinDialog = false
                joinRoomCode = ""
            },
            containerColor = Color(0xF210101C), // Deep premium space glass
            title = {
                Text(
                    text = "Join Room via Code",
                    color = GlassTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter the 8-character invite code of your friend's room (e.g., GAMER-ZONE)",
                        color = GlassIndigoLight,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = joinRoomCode,
                        onValueChange = { joinRoomCode = it.uppercase() },
                        placeholder = { Text("e.g. LOFI-CHILL", color = GlassTextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GlassTextWhite,
                            unfocusedTextColor = GlassTextWhite,
                            focusedBorderColor = GlassIndigoAccent,
                            unfocusedBorderColor = GlassBorderLow,
                            focusedContainerColor = GlassFillLow,
                            unfocusedContainerColor = GlassFillLow
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_room_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (joinRoomCode.isNotBlank()) {
                            onJoinRoom(joinRoomCode.trim())
                            showJoinDialog = false
                            joinRoomCode = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlassIndigoAccent)
                ) {
                    Text("Join Room", fontWeight = FontWeight.Bold, color = GlassTextWhite)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showJoinDialog = false
                        joinRoomCode = ""
                    }
                ) {
                    Text("Cancel", color = GlassIndigoLight)
                }
            }
        )
    }

    // Server Configuration URL Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = "🌐 Connection Settings",
                    color = GlassTextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            containerColor = Color(0xFF1E2030), // Match dark theme
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter your real-time WebSocket server URL. The server coordinates chats, presence list, and synchronized watch party playback across all connected friends.",
                        color = GlassIndigoLight,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    OutlinedTextField(
                        value = tempServerUrl,
                        onValueChange = { tempServerUrl = it },
                        label = { Text("Server WebSocket URL", color = GlassIndigoLight) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GlassTextWhite,
                            unfocusedTextColor = GlassTextWhite,
                            focusedBorderColor = GlassIndigoAccent,
                            unfocusedBorderColor = GlassBorderLow,
                            focusedContainerColor = GlassFillLow,
                            unfocusedContainerColor = GlassFillLow
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_url_input")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "QUICK PRESETS",
                        color = GlassTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    // Presets Button Row
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { tempServerUrl = "wss://hangout-lounge-backend.onrender.com" },
                            colors = ButtonDefaults.buttonColors(containerColor = GlassIndigoAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text("Preset 1: Render Production (Secure)", fontSize = 11.sp, color = GlassTextWhite)
                        }

                        Button(
                            onClick = { tempServerUrl = "wss://hangout-room-backend.glitch.me" },
                            colors = ButtonDefaults.buttonColors(containerColor = GlassFillMedium),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text("Preset 2: Public Cloud (Glitch)", fontSize = 11.sp, color = GlassTextWhite)
                        }

                        Button(
                            onClick = { tempServerUrl ="wss://hangout-lounge.onrender.com"  },
                            colors = ButtonDefaults.buttonColors(containerColor = GlassFillMedium),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text("Preset 3: Production Server", fontSize = 11.sp, color = GlassTextWhite)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempServerUrl.isNotBlank()) {
                            onUpdateServerUrl(tempServerUrl.trim())
                            showSettingsDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlassIndigoAccent)
                ) {
                    Text("Save & Apply", fontWeight = FontWeight.Bold, color = GlassTextWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel", color = GlassIndigoLight)
                }
            }
        )
    }
}
