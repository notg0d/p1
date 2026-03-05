package com.realornot.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realornot.theme.*
import com.realornot.ui.components.AnimatedProgressBar
import com.realornot.viewmodel.ScanState

@Composable
fun ScanningScreen(
    state: ScanState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Rotation animation for the scanning ring
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    // Pulse scale for icons
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark),
    ) {
        Spacer(Modifier.height(8.dp))

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CardDarkLight),
            ) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = TextWhite, modifier = Modifier.size(18.dp))
            }
            Text("Analyzing File", fontFamily = SpaceGrotesk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
            Spacer(Modifier.size(36.dp))
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Scanning circle with icons
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Outer rotating ring
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .rotate(if (state.error == null) rotation else 0f)
                        .border(2.dp, if (state.error == null) SageOlive.copy(alpha = 0.3f) else VerdictFake.copy(alpha=0.5f), CircleShape)
                )

                // Inner static ring
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .border(3.dp, SageOlive, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    // Media type icons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Image, "Image", tint = SageOlive, modifier = Modifier.size(32.dp))
                        Icon(Icons.Outlined.Movie, "Video", tint = SageLight, modifier = Modifier.size(32.dp))
                        Icon(Icons.Outlined.Mic, "Audio", tint = SageGray, modifier = Modifier.size(32.dp))
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Status text
            Text(
                "Scanning for AI Content...",
                fontFamily = SpaceGrotesk,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite,
            )

            Spacer(Modifier.height(6.dp))

            if (state.error != null) {
                Text(
                    "Error: ${state.error}",
                    fontFamily = DmSans,
                    fontSize = 14.sp,
                    color = VerdictFake,
                )
            } else {
                Text(
                    state.statusMessage.ifEmpty { "Analyzing features and artifacts" },
                    fontFamily = DmSans,
                    fontSize = 13.sp,
                    color = TextMuted,
                )
            }

            Spacer(Modifier.height(20.dp))

            // Progress bar
            AnimatedProgressBar(
                progress = state.progress,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "${(state.progress * 100).toInt()}%",
                fontFamily = SpaceGrotesk,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = SageOlive,
            )

            Spacer(Modifier.height(24.dp))

            // Steps card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StepItem("File uploaded successfully", state.currentStep >= 1, state.currentStep == 0)
                StepItem("Preprocessing complete", state.currentStep >= 2, state.currentStep == 1)
                StepItem("Running AI analysis...", state.currentStep >= 3, state.currentStep == 2)
                StepItem("Generating report", state.currentStep >= 3, state.currentStep == 3 && state.isScanning)
            }
        }
    }
}

@Composable
private fun StepItem(
    text: String,
    isComplete: Boolean,
    isActive: Boolean,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "step")
    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
        ),
        label = "spin"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            isComplete -> Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = SageOlive,
                modifier = Modifier.size(18.dp),
            )
            isActive -> Icon(
                Icons.Outlined.Refresh,
                contentDescription = null,
                tint = SageLight,
                modifier = Modifier.size(18.dp).rotate(spinRotation),
            )
            else -> Icon(
                Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = TextDim,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text,
            fontFamily = DmSans,
            fontSize = 13.sp,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            color = when {
                isComplete -> SageOlive
                isActive -> SageLight
                else -> TextDim
            },
        )
    }
}
