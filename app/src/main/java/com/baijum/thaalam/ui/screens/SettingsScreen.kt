package com.baijum.thaalam.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baijum.thaalam.BuildConfig
import com.baijum.thaalam.domain.TalaLibrary
import com.baijum.thaalam.ui.components.FieldLabel
import com.baijum.thaalam.ui.components.SectionCard
import com.baijum.thaalam.ui.components.ToggleRow
import com.baijum.thaalam.ui.theme.thaaSliderColors
import com.baijum.thaalam.viewmodel.MainViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val preferences by viewModel.uiPreferences.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Feel, feedback and the honest details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(
                title = "Feel",
                subtitle = "How ThaaLam responds while you play",
            ) {
                ToggleRow(
                    title = "Haptic pulse",
                    subtitle = "Feel the downbeat even in silent practice",
                    checked = preferences.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticsEnabled,
                )
                ToggleRow(
                    title = "Screen flash",
                    subtitle = "Brief flash on every main pulse",
                    checked = preferences.flashEnabled,
                    onCheckedChange = viewModel::setFlashEnabled,
                )
                ToggleRow(
                    title = "Keep screen awake",
                    subtitle = "Prevent the display from sleeping during practice",
                    checked = preferences.keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn,
                )
            }
        }

        item {
            SectionCard(
                title = "Display calibration",
                subtitle = "Bluetooth headphones can delay sound by 80–250 ms",
                trailing = {
                    Text(
                        text = "${preferences.visualLatencyMs} ms",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            ) {
                Slider(
                    value = preferences.visualLatencyMs.toFloat(),
                    onValueChange = { viewModel.setVisualLatency((it / 10f).roundToInt() * 10) },
                    valueRange = 0f..300f,
                    steps = 29,
                    colors = thaaSliderColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Delays the visual pulse so it lands with the click you actually hear. " +
                        "Leave at 0 ms for wired headphones and the built-in speaker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(
                title = "Tap calibration",
                subtitle = "Final touch-to-sound correction for the accuracy grader",
                trailing = {
                    Text(
                        text = "${preferences.tapCalibrationMs} ms",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            ) {
                Slider(
                    value = preferences.tapCalibrationMs.toFloat(),
                    onValueChange = { viewModel.setTapCalibration((it / 10f).roundToInt() * 10) },
                    valueRange = -100f..200f,
                    steps = 29,
                    colors = thaaSliderColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "The engine estimates output latency. Nudge this if the grader says your taps are " +
                        "consistently early (raise it) or late (lower it).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard(title = "Current engine") {
                InfoRow("Tempo", "${settings.bpm.roundToInt()} BPM")
                InfoRow("Meter", "${settings.meter.label} · ${settings.meter.grouping.joinToString(" + ")}")
                InfoRow("Subdivision", settings.subdivision.displayName)
                InfoRow("Sound", settings.soundSet.displayName)
                InfoRow("Count-in", "${settings.countInBars} bar(s)")
                if (settings.talaId != null) {
                    InfoRow("Tala", TalaLibrary.find(settings.talaId)?.name ?: "Unknown tala")
                }
                OutlinedButton(
                    onClick = { confirmReset = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset metronome settings")
                }
            }
        }

        item {
            SectionCard(title = "About ThaaLam") {
                Text(
                    text = "A precise, offline rhythm companion for practice rooms and stages — " +
                        "with first-class Carnatic and Hindustani tala support.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                InfoRow("Version", BuildConfig.VERSION_NAME)
                InfoRow("Platform", "Kotlin Multiplatform · Compose · SwiftUI")
                InfoRow("Audio", "48 kHz sample-accurate synthesis")
                InfoRow("Privacy", "No accounts, ads, analytics or network access")
                Text(
                    text = "Everything you save stays on this device. ThaaLam works with the radio off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset settings?") },
            text = {
                Text("Tempo, meter, accent pattern, practice options and sound will return to their defaults. Saved presets are kept.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetSettings()
                        confirmReset = false
                    },
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FieldLabel(label, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
