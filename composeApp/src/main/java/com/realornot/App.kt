package com.realornot

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.realornot.data.ApiKeyManager
import com.realornot.inference.AnalysisResult
import com.realornot.theme.BgDark
import com.realornot.theme.TextMuted
import com.realornot.ui.*
import com.realornot.ui.components.FluidTabBar
import com.realornot.viewmodel.ScanViewModel

enum class Screen {
    SPLASH, API_KEY_SETUP, MAIN, SCANNING, RESULT
}

@Composable
fun App(
    viewModel: ScanViewModel = viewModel(),
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.SPLASH) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val scanState by viewModel.state.collectAsStateWithLifecycle()
    val scanHistory by viewModel.scanHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val scanCount by viewModel.scanCount.collectAsStateWithLifecycle(initialValue = 0)

    // ── 5-TAP HISTORY SECRET ───────────────────────────────────────
    // Rapidly tapping the History tab 5 times opens the API key screen
    var historyTapCount by remember { mutableIntStateOf(0) }
    var lastHistoryTapTime by remember { mutableLongStateOf(0L) }

    // Handle device back button
    BackHandler(enabled = currentScreen == Screen.API_KEY_SETUP) {
        // Only allow back from API key setup if a key already exists
        if (ApiKeyManager.hasApiKey(context)) {
            currentScreen = Screen.MAIN
        }
    }

    BackHandler(enabled = currentScreen != Screen.SPLASH && currentScreen != Screen.MAIN && currentScreen != Screen.API_KEY_SETUP) {
        when (currentScreen) {
            Screen.SCANNING, Screen.RESULT -> {
                viewModel.resetState()
                currentScreen = Screen.MAIN
            }
            else -> { /* Let system handle */ }
        }
    }

    // Handle back on history tab → go to dashboard tab
    BackHandler(enabled = currentScreen == Screen.MAIN && selectedTab != 0) {
        selectedTab = 0
    }

    // Navigate to result when scan completes
    LaunchedEffect(scanState.result) {
        if (scanState.result != null && !scanState.isScanning) {
            currentScreen = Screen.RESULT
        }
    }

    Scaffold(
        containerColor = BgDark,
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentScreen,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            transitionSpec = {
                when {
                    targetState == Screen.SPLASH -> {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    }
                    targetState == Screen.API_KEY_SETUP -> {
                        fadeIn(tween(400)) togetherWith fadeOut(tween(400))
                    }
                    initialState == Screen.API_KEY_SETUP -> {
                        fadeIn(tween(400)) togetherWith fadeOut(tween(400))
                    }
                    targetState == Screen.SCANNING -> {
                        slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                    }
                    targetState == Screen.RESULT -> {
                        slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                    }
                    initialState == Screen.RESULT || initialState == Screen.SCANNING -> {
                        slideInHorizontally { -it } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
                    }
                    else -> {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    }
                }
            },
            label = "screenTransition",
        ) { screen ->
            when (screen) {
                Screen.SPLASH -> {
                    SplashScreen(
                        onFinished = {
                            // Check if API key exists → show setup or main
                            currentScreen = if (ApiKeyManager.hasApiKey(context)) {
                                Screen.MAIN
                            } else {
                                Screen.API_KEY_SETUP
                            }
                        }
                    )
                }

                Screen.API_KEY_SETUP -> {
                    ApiKeyScreen(
                        onKeySaved = {
                            currentScreen = Screen.MAIN
                        },
                        isFirstLaunch = !ApiKeyManager.hasApiKey(context),
                    )
                }

                Screen.MAIN -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = selectedTab,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                if (targetState > initialState) {
                                    slideInHorizontally { it / 3 } + fadeIn() togetherWith
                                        slideOutHorizontally { -it / 3 } + fadeOut()
                                } else {
                                    slideInHorizontally { -it / 3 } + fadeIn() togetherWith
                                        slideOutHorizontally { it / 3 } + fadeOut()
                                }
                            },
                            label = "tabContent",
                        ) { tab ->
                            when (tab) {
                                0 -> DashboardScreen(
                                    onFilePicked = { uri, mimeType, fileName ->
                                        viewModel.startScan(uri, mimeType, fileName)
                                        currentScreen = Screen.SCANNING
                                    }
                                )
                                1 -> HistoryScreen(
                                    scanResults = scanHistory,
                                    scanCount = scanCount,
                                    onResultClick = { result ->
                                        viewModel.showHistoryResult(result)
                                        currentScreen = Screen.RESULT
                                    },
                                )
                            }
                        }

                        // Floating Tab Bar
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 0.dp)
                        ) {
                            FluidTabBar(
                                selectedIndex = selectedTab,
                                onTabSelected = { tabIndex ->
                                    // ── 5-TAP SECRET on History tab ────────────
                                    if (tabIndex == 1) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastHistoryTapTime < 500) {
                                            historyTapCount++
                                        } else {
                                            historyTapCount = 1
                                        }
                                        lastHistoryTapTime = now

                                        if (historyTapCount >= 5) {
                                            historyTapCount = 0
                                            currentScreen = Screen.API_KEY_SETUP
                                            return@FluidTabBar
                                        }
                                    } else {
                                        historyTapCount = 0
                                    }

                                    selectedTab = tabIndex
                                },
                                tabs = listOf(
                                    "Dashboard" to {
                                        Icon(
                                            Icons.Outlined.Dashboard, null,
                                            tint = if (selectedTab == 0) BgDark else TextMuted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    "History" to {
                                        Icon(
                                            Icons.Outlined.History, null,
                                            tint = if (selectedTab == 1) BgDark else TextMuted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                ),
                            )
                        }
                    }
                }

                Screen.SCANNING -> {
                    ScanningScreen(
                        state = scanState,
                        onBack = {
                            viewModel.resetState()
                            currentScreen = Screen.MAIN
                        },
                    )
                }

                Screen.RESULT -> {
                    ScanResultScreen(
                        state = scanState,
                        onBack = {
                            viewModel.resetState()
                            currentScreen = Screen.MAIN
                        },
                    )
                }
            }
        }
    }
}
