package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.core.GoogleSignInButton
import com.example.core.attemptAutoSignIn
import com.example.ui.components.HolographicBackground
import com.example.ui.components.HudGlassCard
import com.example.ui.components.StatusIndicatorPill
import com.example.ui.theme.KikoAmber
import com.example.ui.theme.KikoCrimson
import com.example.ui.theme.KikoCyan
import com.example.ui.theme.KikoEmerald
import com.example.ui.theme.KikoTextSecondary

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var authError by remember { mutableStateOf<String?>(null) }
    var checkingSession by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                checkingSession = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                checkingSession = false
            },
            scope = scope,
        )
    }

    HolographicBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StatusIndicatorPill(
                    label = if (checkingSession) "VERIFYING NEURAL LINK…" else "ZERO-TRUST CORE STANDBY",
                    color = if (checkingSession) KikoAmber else KikoCyan,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Image(
                    painter = painterResource(id = R.drawable.img_kiko_hud_emblem),
                    contentDescription = "KIKO AI Core Emblem",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(156.dp)
                        .clip(CircleShape)
                        .border(2.dp, KikoCyan, CircleShape),
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "KIKO AI",
                    style = MaterialTheme.typography.displayLarge,
                    color = KikoCyan,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Holographic Personal Intelligence • انگریزی اور اردو",
                    style = MaterialTheme.typography.titleMedium,
                    color = KikoTextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                HudGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = KikoCyan.copy(alpha = 0.4f),
                ) {
                    Text(
                        text = "INITIALIZATION CLEARANCE",
                        style = MaterialTheme.typography.labelLarge,
                        color = KikoCyan,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Authenticate with Google to unlock encrypted personal memory, multi-agent orchestration across 12 KIKO Teams, live voice & CameraX vision, and Gemini AI Logic.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KikoTextSecondary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusIndicatorPill(label = "12 AI TEAMS", color = KikoEmerald)
                        StatusIndicatorPill(label = "EN / اردو", color = KikoCyan)
                        StatusIndicatorPill(label = "4GB OPTIMIZED", color = KikoAmber)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (authError != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = MaterialTheme.shapes.small,
                        color = KikoCrimson.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, KikoCrimson),
                    ) {
                        Text(
                            text = authError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                GoogleSignInButton(
                    onAuthSuccess = {
                        authError = null
                        onAuthSuccess()
                    },
                    onAuthError = { msg ->
                        authError = msg
                    },
                )
            }
        }
    }
}
