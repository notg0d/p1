package com.realornot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realornot.data.ApiKeyManager
import com.realornot.theme.*

@Composable
fun ApiKeyScreen(
    onKeySaved: () -> Unit,
    isFirstLaunch: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var apiKey by remember { mutableStateOf(ApiKeyManager.getApiKey(context) ?: "") }
    var isKeyVisible by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(60.dp))

        // Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(CardDarkLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Key,
                contentDescription = null,
                tint = SageOlive,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        // Title
        Text(
            if (isFirstLaunch) "Welcome to REALorNOT" else "API Key Settings",
            fontFamily = SpaceGrotesk,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            if (isFirstLaunch)
                "To get started, enter your Google Gemini API key.\nThis powers the AI deepfake detection engine."
            else
                "Update or change your Gemini API key below.",
            fontFamily = DmSans,
            fontSize = 14.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(32.dp))

        // API Key input field
        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                testResult = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Gemini API Key", fontFamily = DmSans, fontSize = 13.sp)
            },
            placeholder = {
                Text("AIzaSy...", fontFamily = DmSans, fontSize = 14.sp, color = TextDim)
            },
            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                    Icon(
                        if (isKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = "Toggle visibility",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (apiKey.isNotBlank()) {
                        ApiKeyManager.saveApiKey(context, apiKey)
                        onKeySaved()
                    }
                }
            ),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SageOlive,
                unfocusedBorderColor = CardDarkLight,
                focusedLabelColor = SageOlive,
                unfocusedLabelColor = TextMuted,
                cursorColor = SageOlive,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
            ),
        )

        Spacer(Modifier.height(8.dp))

        // Test result message
        AnimatedVisibility(visible = testResult != null) {
            Text(
                testResult ?: "",
                fontFamily = DmSans,
                fontSize = 13.sp,
                color = if (testSuccess) VerdictReal else VerdictFake,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        // Save button
        Button(
            onClick = {
                if (apiKey.isNotBlank()) {
                    ApiKeyManager.saveApiKey(context, apiKey)
                    onKeySaved()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = apiKey.length >= 10,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SageOlive,
                contentColor = BgDark,
                disabledContainerColor = CardDarkLight,
                disabledContentColor = TextDim,
            )
        ) {
            Icon(Icons.Outlined.Check, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (isFirstLaunch) "Get Started" else "Save Key",
                fontFamily = SpaceGrotesk,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(16.dp))

        // "Get a key" link
        TextButton(
            onClick = {
                uriHandler.openUri("https://aistudio.google.com/apikey")
            },
        ) {
            Icon(
                Icons.Outlined.OpenInNew,
                null,
                tint = SageLight,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Get a free API key at Google AI Studio",
                fontFamily = DmSans,
                fontSize = 13.sp,
                color = SageLight,
            )
        }

        if (!isFirstLaunch) {
            Spacer(Modifier.height(8.dp))

            // Clear key option
            TextButton(
                onClick = {
                    ApiKeyManager.clearApiKey(context)
                    apiKey = ""
                    testResult = "API key cleared."
                    testSuccess = false
                },
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    null,
                    tint = VerdictFake.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Clear stored key",
                    fontFamily = DmSans,
                    fontSize = 13.sp,
                    color = VerdictFake.copy(alpha = 0.7f),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Security notice
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Lock,
                null,
                tint = TextDim,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Your key is stored securely on-device with AES-256 encryption",
                fontFamily = DmSans,
                fontSize = 11.sp,
                color = TextDim,
            )
        }
    }
}
