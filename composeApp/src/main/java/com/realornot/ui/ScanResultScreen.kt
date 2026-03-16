package com.realornot.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import com.realornot.model.MediaType
import com.realornot.theme.*
import com.realornot.viewmodel.ScanState

@Composable
fun ScanResultScreen(
    state: ScanState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val result = state.result ?: return
    val isReal = result.verdict == "REAL"
    val verdictColor = if (isReal) VerdictReal else VerdictFake
    val verdictBg = if (isReal) VerdictRealBg else VerdictFakeBg

    // Load media preview bitmap (image or video thumbnail)
    val previewBitmap by remember(state.selectedUri, state.mediaType) {
        mutableStateOf(
            state.selectedUri?.let { uri ->
                try {
                    when (state.mediaType) {
                        MediaType.IMAGE -> {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                                BitmapFactory.decodeStream(stream, null, opts)
                            }
                        }
                        MediaType.VIDEO -> {
                            val retriever = MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(context, uri)
                                retriever.getFrameAtTime(
                                    1_000_000,
                                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                                )
                            } finally {
                                retriever.release()
                            }
                        }
                        else -> null
                    }
                } catch (_: Exception) { null }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState()),
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
            Text("Scan Result", fontFamily = SpaceGrotesk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
            Spacer(Modifier.size(36.dp))
        }

        // ── MEDIA PREVIEW ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(CardDark),
            contentAlignment = Alignment.Center,
        ) {
            if (previewBitmap != null) {
                // Show actual image or video thumbnail
                Image(
                    bitmap = previewBitmap!!.asImageBitmap(),
                    contentDescription = "Media preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(0.dp)),
                    contentScale = ContentScale.Crop,
                )
                // Video play badge overlay
                if (state.mediaType == MediaType.VIDEO) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(BgDark.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = "Video",
                            tint = TextWhite,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                // Verdict badge overlay in corner
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(verdictBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (isReal) "REAL" else "FAKE",
                        fontFamily = SpaceGrotesk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = verdictColor,
                    )
                }
            } else if (state.mediaType == MediaType.AUDIO) {
                // Audio waveform visualization
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(SageOlive.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            tint = SageOlive,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        state.fileName,
                        fontFamily = DmSans,
                        fontSize = 14.sp,
                        color = TextWhite,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Audio File",
                        fontFamily = DmSans,
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }
            } else {
                // Fallback: generic icon (e.g. history items without URI)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val icon = when (state.mediaType) {
                        MediaType.IMAGE -> Icons.Outlined.Image
                        MediaType.VIDEO -> Icons.Outlined.Movie
                        MediaType.AUDIO -> Icons.Outlined.Audiotrack
                    }
                    Icon(icon, contentDescription = null, tint = SageOlive, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.fileName,
                        fontFamily = DmSans,
                        fontSize = 13.sp,
                        color = TextMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Verdict Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(verdictBg)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isReal) Icons.Outlined.VerifiedUser else Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = verdictColor,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (isReal) "Authentic Media" else "AI content Detected",
                        fontFamily = SpaceGrotesk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextWhite,
                    )
                    Text(
                        if (isReal) "No AI manipulation detected" else "AI-generated content detected",
                        fontFamily = DmSans,
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${result.confidence.toInt()}%",
                    fontFamily = SpaceGrotesk,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = verdictColor,
                )
                Text("score", fontFamily = DmSans, fontSize = 11.sp, color = TextMuted)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Analysis Details
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Text(
                "Analysis Details",
                fontFamily = SpaceGrotesk,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite,
            )
            Spacer(Modifier.height(12.dp))

            DetailRow(Icons.Outlined.InsertDriveFile, "File Name", state.fileName)
            DetailRow(Icons.Outlined.Category, "Media Type", state.mediaType.displayName)
            DetailRow(Icons.Outlined.SmartToy, "Model Used", result.modelUsed)
            DetailRow(Icons.Outlined.Timer, "Processing", "${result.processingTimeMs / 1000.0}s")
        }

        if (state.mediaType == MediaType.VIDEO && result.videoVerdict != null) {
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    "Detailed Analysis",
                    fontFamily = SpaceGrotesk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextWhite,
                )
                Spacer(Modifier.height(12.dp))

                // Video Analysis
                SubAnalysisCard(
                    title = "Video Frames",
                    icon = Icons.Outlined.Movie,
                    verdict = result.videoVerdict ?: "N/A",
                    confidence = result.videoConfidence ?: 0f,
                )

                // Audio Analysis (only if performed)
                if (result.audioVerdict != null) {
                    Spacer(Modifier.height(10.dp))
                    SubAnalysisCard(
                        title = "Audio Track",
                        icon = Icons.Outlined.Audiotrack,
                        verdict = result.audioVerdict ?: "N/A",
                        confidence = result.audioConfidence ?: 0f,
                    )
                }
            }
        }

        if (result.reasoning != null) {
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    "AI Reasoning",
                    fontFamily = SpaceGrotesk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextWhite,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDarkLight.copy(alpha=0.5f))
                        .padding(16.dp)
                ) {
                    Text(
                        result.reasoning,
                        fontFamily = DmSans,
                        fontSize = 14.sp,
                        color = TextMuted,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Action button
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SageOlive, contentColor = BgDark),
        ) {
            Icon(Icons.Outlined.DocumentScanner, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("New Scan", fontFamily = DmSans, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = SageOlive, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontFamily = DmSans, fontSize = 13.sp, color = TextMuted)
        }
        Text(value, fontFamily = SpaceGrotesk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
    }
}

@Composable
private fun SubAnalysisCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    verdict: String,
    confidence: Float,
) {
    val isReal = verdict == "REAL"
    val color = if (isReal) VerdictReal else if (verdict == "N/A") TextMuted else VerdictFake

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = SageOlive, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontFamily = DmSans, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextWhite)
                Text(verdict, fontFamily = DmSans, fontSize = 12.sp, color = color)
            }
        }
        Text(
            "${confidence.toInt()}%",
            fontFamily = SpaceGrotesk,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}
