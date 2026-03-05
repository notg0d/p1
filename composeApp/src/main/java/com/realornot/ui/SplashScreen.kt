package com.realornot.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realornot.theme.*
import com.realornot.ui.components.PulsatingProgressBar
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
) {
    var startAnimation by remember { mutableStateOf(false) }

    // Logo scale animation
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )

    // Logo alpha
    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(800),
        label = "logoAlpha"
    )

    // Text slide up
    val textOffset by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 30.dp,
        animationSpec = tween(800, delayMillis = 400),
        label = "textOffset"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(800, delayMillis = 400),
        label = "textAlpha"
    )

    // Bar visibility
    val barAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(600, delayMillis = 1000),
        label = "barAlpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2500)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(48.dp),
        ) {
            // Logo icon placeholder (shield)
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha)
                    .clip(RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(com.realornot.R.drawable.app_logo_icon),
                    contentDescription = "REALorNOT logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // REALorNOT wordmark
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .offset(y = textOffset)
                    .alpha(textAlpha),
            ) {
                Text(
                    "REAL",
                    fontFamily = SpaceGrotesk,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = SageLight,
                )
                Text(
                    "or",
                    fontFamily = SpaceGrotesk,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = SageOlive,
                )
                Text(
                    "NOT",
                    fontFamily = SpaceGrotesk,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = SageGray,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                "Deepfake Detection",
                fontFamily = DmSans,
                fontSize = 13.sp,
                color = TextDim,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(y = textOffset)
                    .alpha(textAlpha),
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Loading bar
            PulsatingProgressBar(
                modifier = Modifier
                    .width(200.dp)
                    .alpha(barAlpha),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Loading...",
                fontFamily = DmSans,
                fontSize = 12.sp,
                color = TextDim,
                modifier = Modifier.alpha(barAlpha),
            )
        }
    }
}
