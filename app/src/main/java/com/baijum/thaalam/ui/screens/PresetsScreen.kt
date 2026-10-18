package com.baijum.thaalam.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baijum.thaalam.data.MetronomePreset
import com.baijum.thaalam.domain.TalaLibrary
import com.baijum.thaalam.domain.TempoMath
import com.baijum.thaalam.ui.components.SectionCard
import com.baijum.thaalam.viewmodel.MainViewModel

@Composable
fun PresetsScreen(
    viewModel: MainViewModel,
    onOpenMetronome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets by viewModel.presets.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var saveDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    val pendingDelete = presets.firstOrNull { it.id == pendingDeleteId }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Presets",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Save a tempo, meter, groove and practice setup for the next session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionCard(title = "Current setup") {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = TempoMath.formatBpm(settings.bpm),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = " BPM",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(
                    text = buildString {
                        append(settings.meter.label)
                        append(" · ")
                        append(settings.subdivision.shortLabel)
                        if (settings.swingPercent > 50.5) {
                            append(" · ${settings.swingPercent.toInt()}% swing")
                        }
                        TalaLibrary.find(settings.talaId)?.let {
                            append(" · ")
                            append(it.name)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { saveDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save current setup")
                }
            }
        }
        item {
            Text(
                text = "Saved · ${presets.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (presets.isEmpty()) {
            item {
                SectionCard {
                    Text(
                        text = "No presets yet",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Save your first setup above. Presets are stored on this device only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(presets, key = { it.id }) { preset ->
            PresetCard(
                preset = preset,
                onLoad = {
                    viewModel.applyPreset(preset)
                    onOpenMetronome()
                },
                onDelete = { pendingDeleteId = preset.id },
            )
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }

    if (saveDialog) {
        SavePresetDialog(
            suggestedName = "${TempoMath.formatBpm(settings.bpm)} BPM · ${settings.meter.label}",
            onDismiss = { saveDialog = false },
            onConfirm = { name ->
                viewModel.savePreset(name)
                saveDialog = false
            },
        )
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete preset?") },
            text = { Text("\"${pendingDelete.name}\" will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePreset(pendingDelete)
                        pendingDeleteId = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PresetCard(
    preset: MetronomePreset,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    val tala = remember(preset.settings.talaId) { TalaLibrary.find(preset.settings.talaId) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${preset.bpmLabel} BPM · ${preset.settings.meter.label} · ${preset.settings.subdivision.shortLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (tala != null) {
                        Text(
                            text = tala.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${preset.name}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onLoad,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Load")
                }
            }
        }
    }
}

@Composable
private fun SavePresetDialog(
    suggestedName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(suggestedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save preset") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(48) },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
