package com.realornot.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realornot.R
import com.realornot.theme.*

@Composable
fun DashboardScreen(
    onFilePicked: (Uri, String?, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it)
            val cursor = context.contentResolver.query(it, null, null, null, null)
            val fileName = cursor?.use { c ->
                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                c.moveToFirst()
                if (nameIndex >= 0) c.getString(nameIndex) else "unknown_file"
            } ?: "unknown_file"
            onFilePicked(it, mimeType, fileName)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark),
    ) {
        Spacer(Modifier.height(8.dp))

        // Hero art from Pencil design
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(CardDark),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.dashboard_hero_art),
                contentDescription = "Deepfake detection illustration",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        // Text section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text(
                "See Beyond",
                fontFamily = SpaceGrotesk,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = SageLight,
            )
            Text(
                "the Fake.",
                fontFamily = SpaceGrotesk,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = SageWarm,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Detect AI-generated images, videos and audio with on-device machine learning.",
                fontFamily = DmSans,
                fontSize = 13.sp,
                color = TextDim,
                lineHeight = 18.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        // Start Scanning button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 100.dp), // Extra bottom padding to clear the floating Tab Bar
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = {
                    filePicker.launch(arrayOf("image/*", "video/*", "audio/*"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SageWarm,
                    contentColor = BgDark,
                ),
            ) {
                Icon(
                    Icons.Outlined.DocumentScanner,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Start Scanning",
                    fontFamily = DmSans,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
