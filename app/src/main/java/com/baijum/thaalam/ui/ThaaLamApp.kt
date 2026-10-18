package com.baijum.thaalam.ui

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baijum.thaalam.ui.screens.MetronomeScreen
import com.baijum.thaalam.ui.screens.PresetsScreen
import com.baijum.thaalam.ui.screens.SettingsScreen
import com.baijum.thaalam.ui.screens.TalasScreen
import com.baijum.thaalam.ui.theme.LocalReduceMotion
import com.baijum.thaalam.ui.theme.ThaaLamTheme
import com.baijum.thaalam.viewmodel.MainViewModel

private enum class Tab(val label: String, val icon: ImageVector) {
    METRONOME("Metronome", Icons.Filled.GraphicEq),
    TALAS("Talas", Icons.Filled.MusicNote),
    PRESETS("Presets", Icons.Filled.Bookmarks),
    SETTINGS("Settings", Icons.Filled.Settings),
}

/** Root composable: theme providers, keep-screen-on, snackbar and bottom tabs. */
@Composable
fun ThaaLamApp() {
    val viewModel: MainViewModel = viewModel()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val preferences by viewModel.uiPreferences.collectAsState()
    val view = LocalView.current
    val reduceMotion = rememberReduceMotion()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    DisposableEffect(preferences.keepScreenOn) {
        view.keepScreenOn = preferences.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        ThaaLamTheme(darkTheme = isSystemInDarkTheme()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        Tab.entries.forEachIndexed { index, entry ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(entry.icon, contentDescription = null) },
                                label = { Text(entry.label) },
                            )
                        }
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    when (Tab.entries[tab]) {
                        Tab.METRONOME -> MetronomeScreen(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Tab.TALAS -> TalasScreen(
                            viewModel = viewModel,
                            onOpenMetronome = { tab = 0 },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Tab.PRESETS -> PresetsScreen(
                            viewModel = viewModel,
                            onOpenMetronome = { tab = 0 },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Tab.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Android exposes the system animator duration scale as the closest signal for
 * "remove animations"; 0 means the user has turned animations off.
 */
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
