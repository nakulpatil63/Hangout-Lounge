package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import kotlinx.coroutines.launch

// Standard Avatar Definitions
val AVATAR_EMOJIS = listOf("👾", "🦊", "🐱", "🐼", "🦄", "🤖")
val AVATAR_COLORS = listOf(
    Color(0xFF5865F2), // Blurple
    Color(0xFFE67E22), // Orange
    Color(0xFFEC407A), // Pink
    Color(0xFF2ECC71), // Green
    Color(0xFF9B59B6), // Amethyst
    Color(0xFF1ABC9C)  // Teal
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onLoginSuccess: (username: String, avatarIndex: Int) -> Unit,
    onMemberLogin: suspend (username: String, password: String) -> Result<String>,
    onMemberRegister: suspend (username: String, password: String, avatarIndex: Int) -> Result<String>,
    modifier: Modifier = Modifier
) {
    var isMemberTab by remember { mutableStateOf(false) } // Guest vs Member Switcher
    var isRegisterMode by remember { mutableStateOf(false) } // Log In vs Register Switcher

    // Form fields
    var guestName by remember { mutableStateOf("") }
    var selectedAvatarIndex by remember { mutableIntStateOf(0) }

    var memberUsername by remember { mutableStateOf("") }
    var memberPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Async & Validation States
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .frostedGlassBackground(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Image Card styled as a premium frosted glass pane
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .frostedGlassCard(
                        shape = RoundedCornerShape(24.dp),
                        backgroundColor = GlassFillMedium,
                        borderColor = GlassBorderHigh
                    )
                    .testTag("auth_hero_card")
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hangout_banner),
                    contentDescription = "Hangout room graphic banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.5f
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC000000))
                            )
                        )
                )
                Text(
                    text = "Hangout Lounge 🍿",
                    color = GlassTextWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Subtitle
            Text(
                text = "Join or create virtual lounge rooms to sync video playback, talk in real-time, and hang out securely!",
                color = GlassIndigoLight,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassFillLow)
                    .border(1.dp, GlassBorderLow, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (!isMemberTab) GlassIndigoAccent.copy(alpha = 0.7f) else Color.Transparent)
                        .clickable {
                            isMemberTab = false
                            errorMessage = null
                            successMessage = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Quick Guest Entry",
                        color = GlassTextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isMemberTab) GlassIndigoAccent.copy(alpha = 0.7f) else Color.Transparent)
                        .clickable {
                            isMemberTab = true
                            errorMessage = null
                            successMessage = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Secure Member Auth",
                        color = GlassTextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Feedback Badges
            errorMessage?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x33FF0055)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "⚠️ $it",
                        color = GlassRoseRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            successMessage?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x3300FF99)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "✅ $it",
                        color = Color(0xFF00FF99),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Input Form Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlassCard(
                        shape = RoundedCornerShape(24.dp),
                        backgroundColor = GlassFillLow,
                        borderColor = GlassBorderLow
                    )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (!isMemberTab) {
                        // GUEST FORM MODULE
                        Text(
                            text = "CHOOSE YOUR NICKNAME",
                            color = GlassIndigoLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = guestName,
                            onValueChange = {
                                guestName = it
                                if (it.isNotBlank()) errorMessage = null
                            },
                            placeholder = { Text("Enter a catchy nickname...", color = GlassTextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GlassIndigoAccent,
                                unfocusedBorderColor = GlassBorderLow,
                                focusedContainerColor = GlassFillLow,
                                unfocusedContainerColor = GlassFillLow,
                                focusedTextColor = GlassTextWhite,
                                unfocusedTextColor = GlassTextWhite
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("username_input")
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "CHOOSE AN AVATAR",
                            color = GlassIndigoLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AVATAR_EMOJIS.forEachIndexed { index, emoji ->
                                val isSelected = selectedAvatarIndex == index
                                val background = AVATAR_COLORS[index]

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(background)
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = if (isSelected) GlassTextWhite else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedAvatarIndex = index }
                                        .testTag("avatar_option_$index")
                                ) {
                                    Text(
                                        text = emoji,
                                        fontSize = 20.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // MEMBER SECURE AUTH MODULE
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "LOG IN",
                                color = if (!isRegisterMode) GlassTextWhite else GlassTextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { isRegisterMode = false }
                                    .padding(8.dp)
                            )
                            Spacer(modifier = Modifier.width(24.dp))
                            Text(
                                text = "REGISTER",
                                color = if (isRegisterMode) GlassTextWhite else GlassTextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { isRegisterMode = true }
                                    .padding(8.dp)
                            )
                        }

                        Text(
                            text = "USERNAME",
                            color = GlassIndigoLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = memberUsername,
                            onValueChange = { memberUsername = it },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = "User Icon", tint = GlassIndigoLight) },
                            placeholder = { Text("Your account username", color = GlassTextMuted, fontSize = 14.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GlassIndigoAccent,
                                unfocusedBorderColor = GlassBorderLow,
                                focusedTextColor = GlassTextWhite,
                                unfocusedTextColor = GlassTextWhite
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "SECURE PASSWORD",
                            color = GlassIndigoLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = memberPassword,
                            onValueChange = { memberPassword = it },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Lock Icon", tint = GlassIndigoLight) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle password visibility",
                                        tint = GlassIndigoLight
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            placeholder = { Text("••••••••", color = GlassTextMuted, fontSize = 14.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GlassIndigoAccent,
                                unfocusedBorderColor = GlassBorderLow,
                                focusedTextColor = GlassTextWhite,
                                unfocusedTextColor = GlassTextWhite
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (isRegisterMode) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "SELECT MEMBER AVATAR",
                                color = GlassIndigoLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                AVATAR_EMOJIS.forEachIndexed { index, emoji ->
                                    val isSelected = selectedAvatarIndex == index
                                    val background = AVATAR_COLORS[index]

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(background)
                                            .border(
                                                width = if (isSelected) 3.dp else 0.dp,
                                                color = if (isSelected) GlassTextWhite else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedAvatarIndex = index }
                                    ) {
                                        Text(
                                            text = emoji,
                                            fontSize = 18.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "🛡️ Policy: Minimum 8 characters, 1 capital letter, 1 digit, and 1 special symbol (@$!%*?&).",
                                color = GlassIndigoLight.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Primary Action Button
            if (isLoading) {
                CircularProgressIndicator(color = GlassIndigoAccent)
            } else {
                Button(
                    onClick = {
                        errorMessage = null
                        successMessage = null
                        if (!isMemberTab) {
                            // GUEST QUICK ENTRY TRIGGER
                            if (guestName.trim().isBlank()) {
                                errorMessage = "Nickname is required to enter as a Guest."
                            } else {
                                onLoginSuccess(guestName.trim(), selectedAvatarIndex)
                            }
                        } else {
                            // MEMBER SECURE VERIFICATION TRIGGER
                            val u = memberUsername.trim()
                            val p = memberPassword.trim()
                            if (u.isEmpty() || p.isEmpty()) {
                                errorMessage = "Username and password are required."
                                return@Button
                            }

                            if (isRegisterMode) {
                                // REGISTER COROUTINE
                                isLoading = true
                                coroutineScope.launch {
                                    val res = onMemberRegister(u, p, selectedAvatarIndex)
                                    isLoading = false
                                    if (res.isSuccess) {
                                        successMessage = res.getOrThrow()
                                        // Switch to login automatically
                                        isRegisterMode = false
                                        memberPassword = ""
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.message ?: "Registration failed."
                                    }
                                }
                            } else {
                                // LOGIN COROUTINE
                                isLoading = true
                                coroutineScope.launch {
                                    val res = onMemberLogin(u, p)
                                    isLoading = false
                                    if (res.isFailure) {
                                        errorMessage = res.exceptionOrNull()?.message ?: "Login failed."
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassIndigoAccent,
                        contentColor = GlassTextWhite
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("login_button")
                ) {
                    Text(
                        text = if (!isMemberTab) "ENTER HANGOUT ROOMS" else if (isRegisterMode) "CREATE SECURE ACCOUNT" else "SECURE SIGN IN",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
