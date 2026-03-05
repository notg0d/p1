package com.realornot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realornot.model.MediaType
import com.realornot.model.ScanResult
import com.realornot.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    scanResults: List<ScanResult>,
    scanCount: Int,
    onResultClick: (ScanResult) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Scan History",
                fontFamily = SpaceGrotesk,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SageOlive.copy(alpha = 0.2f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "$scanCount scans",
                    fontFamily = DmSans,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SageOlive,
                )
            }
        }

        if (scanResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = null,
                        tint = TextDim,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No scans yet",
                        fontFamily = SpaceGrotesk,
                        fontSize = 16.sp,
                        color = TextMuted,
                    )
                    Text(
                        "Start scanning to see results here",
                        fontFamily = DmSans,
                        fontSize = 13.sp,
                        color = TextDim,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(scanResults) { result ->
                    HistoryItem(result = result, onClick = { onResultClick(result) })
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(result: ScanResult, onClick: () -> Unit) {
    val isReal = result.verdict == "REAL"
    val mediaType = try { MediaType.valueOf(result.mediaType) } catch (_: Exception) { MediaType.IMAGE }
    val icon = when (mediaType) {
        MediaType.IMAGE -> Icons.Outlined.Image
        MediaType.VIDEO -> Icons.Outlined.Movie
        MediaType.AUDIO -> Icons.Outlined.Audiotrack
    }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardDark)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CardDarkLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = SageOlive, modifier = Modifier.size(22.dp))
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.fileName,
                fontFamily = DmSans,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${mediaType.displayName} • ${dateFormatter.format(Date(result.timestamp))} • ${result.confidence.toInt()}%",
                fontFamily = DmSans,
                fontSize = 11.sp,
                color = TextDim,
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isReal) VerdictReal.copy(alpha = 0.15f) else VerdictFake.copy(alpha = 0.15f)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                if (isReal) "REAL" else "FAKE",
                fontFamily = SpaceGrotesk,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isReal) VerdictReal else VerdictFake,
            )
        }
    }
}
