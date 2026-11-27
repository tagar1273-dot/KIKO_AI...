package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.KikoLanguage
import com.example.ui.theme.KikoAmber
import com.example.ui.theme.KikoAzure
import com.example.ui.theme.KikoCyan
import com.example.ui.theme.KikoCyanDim
import com.example.ui.theme.KikoCyanGlow
import com.example.ui.theme.KikoEmerald
import com.example.ui.theme.KikoOutline
import com.example.ui.theme.KikoSurface
import com.example.ui.theme.KikoSurfaceElevated
import com.example.ui.theme.KikoTextMuted
import com.example.ui.theme.KikoTextPrimary
import com.example.ui.theme.KikoTextSecondary
import com.example.ui.theme.KikoVoid
import com.example.viewmodel.CoreReactorPhase
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HolographicBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KikoVoid)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Subtle top-center cyan holographic aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        KikoCyan.copy(alpha = 0.14f),
                        KikoAzure.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.22f),
                    radius = size.maxDimension * 0.55f,
                ),
                radius = size.maxDimension * 0.55f,
                center = Offset(size.width * 0.5f, size.height * 0.22f),
            )
            // Subtle tactical grid lines
            val step = 44.dp.toPx()
            val gridColor = KikoCyan.copy(alpha = 0.045f)
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
        content()
    }
}

@Composable
fun ArcCoreReactor(
    phase: CoreReactorPhase,
    statusLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 212.dp,
    onClick: (() -> Unit)? = null,
) {
    val transition = rememberInfiniteTransition(label = "arc_core")
    val outerAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (phase) {
                    CoreReactorPhase.THINKING -> 4200
                    CoreReactorPhase.LISTENING, CoreReactorPhase.SPEAKING -> 6000
                    else -> 14000
                },
                easing = LinearEasing,
            )
        ),
        label = "outer_rot",
    )
    val innerAngle by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (phase) {
                    CoreReactorPhase.THINKING -> 3000
                    CoreReactorPhase.LISTENING, CoreReactorPhase.SPEAKING -> 4800
                    else -> 10000
                },
                easing = LinearEasing,
            )
        ),
        label = "inner_rot",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (phase == CoreReactorPhase.IDLE) 1800 else 750,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "core_pulse",
    )

    val primaryColor = when (phase) {
        CoreReactorPhase.OFFLINE -> KikoAmber
        CoreReactorPhase.SPEAKING -> KikoCyanGlow
        CoreReactorPhase.LISTENING -> KikoEmerald
        CoreReactorPhase.THINKING -> KikoAzure
        CoreReactorPhase.IDLE -> KikoCyan
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(onClick = onClick)
                        .testTag("arc_core_button")
                } else Modifier
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = this.size.minDimension / 2f

            // Ambient core glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.28f * pulse),
                        primaryColor.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = maxR,
                ),
                radius = maxR,
                center = center,
            )

            // Outer dashed telemetry ring
            val outerR = maxR * 0.90f
            for (i in 0 until 36) {
                val deg = i * 10f + outerAngle * 0.35f
                val rad = Math.toRadians(deg.toDouble())
                val len = if (i % 3 == 0) 10.dp.toPx() else 5.dp.toPx()
                val start = Offset(
                    center.x + (outerR - len) * cos(rad).toFloat(),
                    center.y + (outerR - len) * sin(rad).toFloat(),
                )
                val end = Offset(
                    center.x + outerR * cos(rad).toFloat(),
                    center.y + outerR * sin(rad).toFloat(),
                )
                drawLine(
                    color = primaryColor.copy(alpha = if (i % 3 == 0) 0.75f else 0.3f),
                    start = start,
                    end = end,
                    strokeWidth = if (i % 3 == 0) 2.2f else 1.2f,
                )
            }

            // Segmented Holographic Ring 1
            val r1 = maxR * 0.76f
            listOf(0f, 120f, 240f).forEach { offset ->
                drawArc(
                    color = primaryColor.copy(alpha = 0.85f),
                    startAngle = outerAngle + offset,
                    sweepAngle = 78f,
                    useCenter = false,
                    topLeft = Offset(center.x - r1, center.y - r1),
                    size = Size(r1 * 2, r1 * 2),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // Counter-rotating Ring 2
            val r2 = maxR * 0.61f
            listOf(30f, 150f, 270f).forEach { offset ->
                drawArc(
                    color = KikoAzure.copy(alpha = 0.9f),
                    startAngle = innerAngle + offset,
                    sweepAngle = 64f,
                    useCenter = false,
                    topLeft = Offset(center.x - r2, center.y - r2),
                    size = Size(r2 * 2, r2 * 2),
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // Inner reactor ring
            val r3 = maxR * 0.45f * pulse.coerceAtMost(1.04f)
            drawCircle(
                color = primaryColor.copy(alpha = 0.45f),
                radius = r3,
                center = center,
                style = Stroke(width = 1.6.dp.toPx()),
            )

            // Core nucleus
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        primaryColor.copy(alpha = 0.8f),
                        primaryColor.copy(alpha = 0.15f),
                    ),
                    center = center,
                    radius = maxR * 0.34f,
                ),
                radius = maxR * 0.32f * pulse,
                center = center,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "KIKO",
                style = MaterialTheme.typography.titleLarge,
                color = KikoVoid,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = KikoVoid.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun VoiceWaveformBar(
    bars: List<Float>,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        val count = bars.size.coerceAtLeast(1)
        val slotWidth = size.width / count
        val barWidth = (slotWidth * 0.52f).coerceAtLeast(3.dp.toPx())
        val midY = size.height / 2f

        bars.forEachIndexed { index, amplitude ->
            val clamped = amplitude.coerceIn(0.08f, 1.0f)
            val barHeight = size.height * clamped
            val left = index * slotWidth + (slotWidth - barWidth) / 2f
            val color = if (isActive) {
                if (index % 2 == 0) KikoCyan else KikoAzure
            } else {
                KikoCyanDim.copy(alpha = 0.7f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(left, midY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
fun HudGlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = KikoOutline,
    containerColor: Color = KikoSurface.copy(alpha = 0.86f),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content,
        )
    }
}

@Composable
fun KikoLogoHeader(
    subtitle: String,
    isOnline: Boolean,
    providerLabel: String,
    selectedLanguage: KikoLanguage,
    onCycleLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_kiko_hud_emblem),
                contentDescription = "KIKO AI Holographic Emblem",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, KikoCyan, CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "KIKO AI",
                        style = MaterialTheme.typography.titleLarge,
                        color = KikoTextPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusIndicatorPill(
                        label = if (isOnline) "ONLINE" else "OFFLINE",
                        color = if (isOnline) KikoEmerald else KikoAmber,
                    )
                }
                Text(
                    text = "$subtitle • $providerLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = KikoTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Surface(
            onClick = onCycleLanguage,
            shape = RoundedCornerShape(12.dp),
            color = KikoSurfaceElevated,
            border = BorderStroke(1.dp, KikoCyan.copy(alpha = 0.55f)),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("language_cycle_button"),
        ) {
            Text(
                text = when (selectedLanguage) {
                    KikoLanguage.AUTO -> "EN/اردو"
                    KikoLanguage.ENGLISH -> "EN"
                    KikoLanguage.URDU -> "اردو"
                },
                style = MaterialTheme.typography.labelLarge,
                color = KikoCyan,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun StatusIndicatorPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun CodePreviewCard(
    code: String,
    onCopy: () -> Unit,
    onStageProposal: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = Color(0xFF030812),
        border = BorderStroke(1.dp, KikoCyan.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "GENERATED CODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = KikoCyan,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onStageProposal != null) {
                        TextButton(onClick = onStageProposal) {
                            Icon(
                                imageVector = Icons.Filled.Upgrade,
                                contentDescription = "Stage as Development Proposal",
                                modifier = Modifier.size(16.dp),
                                tint = KikoAmber,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Stage",
                                style = MaterialTheme.typography.labelMedium,
                                color = KikoAmber,
                            )
                        }
                    }
                    IconButton(onClick = onCopy) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy generated code",
                            tint = KikoCyan,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = code,
                style = MaterialTheme.typography.labelMedium,
                color = KikoTextPrimary,
            )
        }
    }
}

@Composable
fun TelemetryStatChip(
    label: String,
    value: String,
    accent: Color = KikoCyan,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = KikoSurfaceElevated.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = KikoTextMuted,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
