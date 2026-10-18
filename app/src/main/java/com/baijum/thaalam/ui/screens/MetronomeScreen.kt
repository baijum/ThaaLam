package com.baijum.thaalam.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baijum.thaalam.audio.PlaybackState
import com.baijum.thaalam.data.LayerSettings
import com.baijum.thaalam.data.Meter
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.SoundSet
import com.baijum.thaalam.data.Subdivision
import com.baijum.thaalam.data.Tala
import com.baijum.thaalam.domain.LayerCompiler
import com.baijum.thaalam.domain.TempoMath
import com.baijum.thaalam.ui.components.BeatGrid
import com.baijum.thaalam.ui.components.ChoiceChips
import com.baijum.thaalam.ui.components.FieldLabel
import com.baijum.thaalam.ui.components.SectionCard
import com.baijum.thaalam.ui.components.Stepper
import com.baijum.thaalam.ui.components.ToggleRow
import com.baijum.thaalam.ui.theme.LocalReduceMotion
import com.baijum.thaalam.ui.theme.thaaFilterChipColors
import com.baijum.thaalam.ui.theme.thaaSliderColors
import com.baijum.thaalam.viewmodel.MainViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetronomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val visual by viewModel.visualPlayback.collectAsState()
    val preferences by viewModel.uiPreferences.collectAsState()
    val tapState by viewModel.tapState.collectAsState()
    val tala by viewModel.currentTala.collectAsState()

    var showBpmDialog by rememberSaveable { mutableStateOf(false) }
    var showMeterDialog by rememberSaveable { mutableStateOf(false) }
    var showLayerPicker by rememberSaveable { mutableStateOf(false) }
    var editingLayerId by rememberSaveable { mutableStateOf<String?>(null) }
    var practiceExpanded by rememberSaveable { mutableStateOf(false) }

    // The large readout is what the player hears: base tempo scaled by kalam
    // (and by the ramp while playing). The slider and +/- buttons still edit the
    // base tempo, so switching kalam back to 1x restores it exactly.
    val displayBpm = if (playback.isPlaying) {
        playback.effectiveBpm.takeIf { it > 0.0 } ?: settings.effectiveBpm
    } else {
        settings.effectiveBpm
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ScreenHeader(tala = tala, onClearTala = viewModel::clearTala) }
            item {
                TempoCard(
                    settings = settings,
                    displayBpm = displayBpm,
                    isPlaying = playback.isPlaying,
                    onOpenBpmDialog = { showBpmDialog = true },
                    onSetBpm = viewModel::setBpm,
                    onNudge = viewModel::nudgeBpm,
                    onHalf = viewModel::halfTempo,
                    onDouble = viewModel::doubleTempo,
                    tala = tala,
                )
            }
            item {
                TransportRow(
                    playback = playback,
                    tapState = tapState,
                    onToggle = viewModel::togglePlayback,
                    onTap = viewModel::tapTempo,
                )
            }
            item {
                SectionCard(
                    title = "Pulse map",
                    subtitle = tala?.let { active ->
                        "${active.name} · ${active.groupingLabel} · ${active.aksharas} aksharas"
                    } ?: "Tap any pulse to cycle accent → normal → ghost → mute.",
                    trailing = {
                        TextButton(onClick = viewModel::applyGroupAccents) {
                            Text("Group accents")
                        }
                    },
                ) {
                    BeatGrid(
                        settings = settings,
                        playback = visual,
                        onBeatClick = viewModel::cycleBeat,
                        markers = tala?.let { t -> List(t.aksharas) { t.markerAt(it) } } ?: emptyList(),
                        bols = tala?.bols?.takeIf { it.isNotEmpty() } ?: emptyList(),
                        gestures = tala?.gestures ?: emptyList(),
                    )
                    StatusLine(playback = playback)
                }
            }
            item {
                GrooveCard(
                    settings = settings,
                    tala = tala,
                    onSetMeter = viewModel::setMeter,
                    onSubdivision = viewModel::setSubdivision,
                    onSwing = viewModel::setSwing,
                    onCustomMeter = { showMeterDialog = true },
                )
            }
            item {
                LayersCard(
                    settings = settings,
                    onAdd = { showLayerPicker = true },
                    onEdit = { editingLayerId = it },
                    onToggle = viewModel::setLayerEnabled,
                    onSolo = viewModel::setLayerSolo,
                    onRemove = viewModel::removeLayer,
                )
            }
            item {
                PracticeCard(
                    settings = settings,
                    expanded = practiceExpanded,
                    onToggleExpanded = { practiceExpanded = !practiceExpanded },
                    onCountIn = viewModel::setCountInBars,
                    onGap = viewModel::setGapTrainer,
                    onGapPattern = viewModel::setGapPattern,
                    onGapFade = viewModel::setGapFadeBars,
                    onGapMaxSilent = viewModel::setGapMaxSilentBars,
                    onGapKeepSam = viewModel::setGapKeepSam,
                    onMuteProbability = viewModel::setMuteProbability,
                    onProtectFirst = viewModel::setProtectFirstBeat,
                    onRampEnabled = viewModel::setRampEnabled,
                    onRampTarget = viewModel::setRampTarget,
                    onRampStep = viewModel::setRampStep,
                    onRampEvery = viewModel::setRampEveryBars,
                )
            }
            item {
                SoundCard(
                    settings = settings,
                    onSound = viewModel::setSoundSet,
                    onVolume = viewModel::setVolume,
                    onPreview = viewModel::previewCurrentSound,
                )
            }
        }
        FlashOverlay(
            enabled = preferences.flashEnabled && visual.isPlaying && !visual.isCountIn,
            beat = visual.beat,
            bar = visual.bar,
        )
    }

    if (showBpmDialog) {
        BpmDialog(
            currentBpm = settings.bpm,
            onDismiss = { showBpmDialog = false },
            onConfirm = {
                viewModel.setBpm(it)
                showBpmDialog = false
            },
        )
    }
    if (showMeterDialog) {
        MeterDialog(
            current = settings.meter,
            onDismiss = { showMeterDialog = false },
            onConfirm = {
                viewModel.setMeter(it)
                showMeterDialog = false
            },
        )
    }
    if (showLayerPicker) {
        LayerPickerDialog(
            settings = settings,
            onDismiss = { showLayerPicker = false },
            onSubdivision = {
                viewModel.addSubdivisionLayer(it)
                showLayerPicker = false
            },
            onPolyrhythm = { pulses, cycle ->
                viewModel.addPolyrhythmLayer(pulses, cycle)
                showLayerPicker = false
            },
            onPolymeter = { beats, unit ->
                viewModel.addPolymeterLayer(beats, unit)
                showLayerPicker = false
            },
        )
    }
    editingLayerId?.let { id ->
        val layer = settings.layers.firstOrNull { it.id == id }
        if (layer == null) {
            editingLayerId = null
        } else {
            LayerEditorDialog(
                layer = layer,
                onDismiss = { editingLayerId = null },
                onEnabled = { viewModel.setLayerEnabled(id, it) },
                onSolo = { viewModel.setLayerSolo(id, it) },
                onPulses = { viewModel.setLayerPulses(id, it) },
                onCycle = { viewModel.setLayerCycleBeats(id, it) },
                onAccent = { viewModel.cycleLayerAccent(id, it) },
                onGain = { viewModel.setLayerGain(id, it) },
                onOffset = { viewModel.setLayerOffset(id, it) },
                onSwing = { viewModel.setLayerSwing(id, it) },
                onStepOffset = { viewModel.cycleLayerStepOffset(id, it) },
                onSound = { viewModel.setLayerSound(id, it) },
                onPreview = { viewModel.previewLayer(id) },
                onDelete = {
                    viewModel.removeLayer(id)
                    editingLayerId = null
                },
            )
        }
    }
}

@Composable
private fun ScreenHeader(tala: Tala?, onClearTala: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "ThaaLam",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Rhythm, precisely.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (tala != null) {
            AssistChip(
                onClick = onClearTala,
                label = { Text(tala.name) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Leave tala mode",
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun TempoCard(
    settings: MetronomeSettings,
    displayBpm: Double,
    isPlaying: Boolean,
    onOpenBpmDialog: () -> Unit,
    onSetBpm: (Double) -> Unit,
    onNudge: (Double) -> Unit,
    onHalf: () -> Unit,
    onDouble: () -> Unit,
    tala: Tala?,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = TempoMath.tempoMarking(displayBpm),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = tala?.name ?: "Free meter · ${settings.meter.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tala != null && !settings.talaPlayback.isDefault) {
                    Text(
                        text = buildString {
                            append("Base ")
                            append(TempoMath.formatBpm(settings.bpm))
                            append(" BPM · ")
                            append(settings.talaPlayback.summary)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isPlaying) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = TempoMath.formatBpm(displayBpm),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onOpenBpmDialog)
                    .semantics(mergeDescendants = false) {
                        contentDescription = "Tempo"
                        stateDescription = "${TempoMath.formatBpm(displayBpm)} beats per minute"
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            Text(
                text = "BPM",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp, bottom = 16.dp),
            )
        }
        Slider(
            value = bpmToSlider(settings.bpm),
            onValueChange = { onSetBpm(sliderToBpm(it)) },
            valueRange = 0f..1f,
            colors = thaaSliderColors(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Tempo slider"
                    stateDescription = "Base tempo ${settings.bpm.roundToInt()} beats per minute"
                },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(-1.0, 1.0).forEach { delta ->
                OutlinedButton(
                    onClick = { onNudge(delta) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    Text(if (delta < 0) "−1" else "+1")
                }
            }
            OutlinedButton(
                onClick = onHalf,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                Text("½×")
            }
            OutlinedButton(
                onClick = onDouble,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                Text("2×")
            }
        }
    }
}

@Composable
private fun StatusLine(playback: PlaybackState) {
    val text = when {
        !playback.isPlaying -> "Ready"
        playback.isCountIn -> "Count-in · ${playback.countInBarsRemaining} bar(s) to go"
        else -> buildString {
            append("Bar ")
            append(playback.bar)
            append(" · Pulse ")
            append(playback.beat + 1)
            if (playback.gesture != com.baijum.thaalam.data.AngaGesture.NONE) {
                append(" · ")
                append(playback.gesture.displayName)
            }
            append(" · ")
            append(TempoMath.formatBpm(playback.effectiveBpm))
            append(" BPM")
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TransportRow(
    playback: PlaybackState,
    tapState: com.baijum.thaalam.domain.TapTempoResult?,
    onToggle: () -> Unit,
    onTap: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (playback.isPlaying) 1.03f else 1f,
        animationSpec = if (reduceMotion) snap() else tween(140),
        label = "transportScale",
    )
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onToggle,
            shape = CircleShape,
            color = if (playback.isPlaying) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier
                .size(88.dp)
                .scale(scale),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (playback.isPlaying) "Stop metronome" else "Start metronome",
                    tint = if (playback.isPlaying) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Surface(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onTap()
            },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .weight(1f)
                .height(88.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "TAP TEMPO",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                val hint = when {
                    tapState?.bpm == null -> "Tap four or more times"
                    tapState?.tapCount == 1 -> "Keep tapping…"
                    else -> "${tapState?.tapCount ?: 0} taps · ${TempoMath.formatBpm(tapState?.bpm ?: 0.0)} BPM"
                }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GrooveCard(
    settings: MetronomeSettings,
    tala: Tala?,
    onSetMeter: (Meter) -> Unit,
    onSubdivision: (Subdivision) -> Unit,
    onSwing: (Double) -> Unit,
    onCustomMeter: () -> Unit,
) {
    SectionCard(
        title = "Groove",
        subtitle = "${settings.meter.label} · ${settings.meter.grouping.joinToString(" + ")}",
        trailing = {
            TextButton(onClick = onCustomMeter) { Text("Custom") }
        },
    ) {
        FieldLabel("Meter")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            meterPresets.forEach { meter ->
                FilterChip(
                    colors = thaaFilterChipColors(),
                    selected = settings.meter.beatsPerBar == meter.beatsPerBar &&
                        settings.meter.beatUnit == meter.beatUnit,
                    onClick = { onSetMeter(meter) },
                    label = { Text(meter.label) },
                )
            }
        }
        FieldLabel(if (tala != null) "Subdivision · Nadai" else "Subdivision")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Subdivision.entries.forEach { subdivision ->
                FilterChip(
                    colors = thaaFilterChipColors(),
                    selected = settings.subdivision == subdivision,
                    onClick = { onSubdivision(subdivision) },
                    label = { Text(subdivision.shortLabel) },
                )
            }
        }
        var swingDraft by remember(settings.swingPercent) {
            mutableFloatStateOf(settings.swingPercent.toFloat())
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FieldLabel("Swing", modifier = Modifier.weight(1f))
            Text(
                text = swingLabel(swingDraft.toDouble()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = swingDraft,
            onValueChange = { swingDraft = it },
            onValueChangeFinished = { onSwing(swingDraft.toDouble()) },
            valueRange = 50f..75f,
            enabled = settings.subdivision.ticksPerBeat >= 2,
            colors = thaaSliderColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = if (settings.subdivision.ticksPerBeat < 2) {
                "Choose an eighth-note subdivision or finer to swing."
            } else {
                "50% straight · 66.7% triplet shuffle · 75% dotted"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayersCard(
    settings: MetronomeSettings,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onSolo: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
) {
    SectionCard(
        title = "Layers",
        subtitle = if (settings.layers.isEmpty()) {
            "One pulse is playing. Add a subdivision, polyrhythm or polymeter voice."
        } else {
            "${settings.layers.size} extra voice${if (settings.layers.size == 1) "" else "s"} · shared clock and tempo"
        },
        trailing = {
            if (settings.layers.size < LayerSettings.MAX_LAYERS) {
                TextButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
        },
    ) {
        if (settings.layers.isEmpty()) {
            Text(
                text = "Layers are mixed on the audio thread at exact sample positions. " +
                    "They follow count-in, gap and tempo changes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LayerRings(settings = settings)
            settings.layers.forEach { layer ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = layer.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (layer.enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = "${layer.gridLabel} · ${layer.soundSet.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = layer.enabled,
                            onCheckedChange = { onToggle(layer.id, it) },
                            modifier = Modifier.semantics {
                                contentDescription = "Enable ${layer.name}"
                            },
                        )
                        IconButton(onClick = { onSolo(layer.id, !layer.solo) }) {
                            Icon(
                                imageVector = Icons.Filled.Headphones,
                                contentDescription = if (layer.solo) {
                                    "Unsolo ${layer.name}"
                                } else {
                                    "Solo ${layer.name}"
                                },
                                tint = if (layer.solo) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = { onEdit(layer.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit ${layer.name}")
                        }
                        IconButton(onClick = { onRemove(layer.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove ${layer.name}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerRings(settings: MetronomeSettings) {
    val voices = remember(settings) { LayerCompiler.compile(settings) }
    if (voices.isEmpty()) return
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
    )
    val description = voices.joinToString("; ") { voice ->
        val role = if (voice.isPrimary) "Primary" else voice.name
        "$role, ${voice.pulseCount} pulses"
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .semantics { contentDescription = "Composite cycle. $description" },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) / 2f - 10.dp.toPx()
        voices.forEachIndexed { index, voice ->
            val color = colors[index % colors.size]
            val radius = maxRadius * (1f - index * 0.16f)
            drawCircle(
                color = color.copy(alpha = 0.45f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            voice.steps.forEach { step ->
                val angle = (step.offsetBeats / voice.cycleBeats * 2.0 * PI - PI / 2.0)
                val dot = Offset(
                    x = center.x + cos(angle).toFloat() * radius,
                    y = center.y + sin(angle).toFloat() * radius,
                )
                drawCircle(
                    color = color,
                    radius = if (step.isMainBeat) 5.5.dp.toPx() else 4.dp.toPx(),
                    center = dot,
                )
            }
        }
    }
    Text(
        text = "Each ring is one cycle · dots are pulses. The primary ring is outside.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerPickerDialog(
    settings: MetronomeSettings,
    onDismiss: () -> Unit,
    onSubdivision: (Subdivision) -> Unit,
    onPolyrhythm: (Int, Double) -> Unit,
    onPolymeter: (Int, Int) -> Unit,
) {
    val subdivisions = listOf(
        Subdivision.EIGHTH,
        Subdivision.SIXTEENTH,
        Subdivision.TRIPLET,
        Subdivision.QUINTUPLET,
        Subdivision.SEPTUPLET,
        Subdivision.THIRTY_SECOND,
        Subdivision.NONUPLET,
    )
    val polyrhythms = listOf(
        Triple("3 over 2", 3, 2.0),
        Triple("4 over 3", 4, 3.0),
        Triple("5 over 4", 5, 4.0),
        Triple("7 over 5", 7, 5.0),
        Triple("7 over 6", 7, 6.0),
    )
    val polymeters = listOf(
        Triple("3/4", 3, 4),
        Triple("5/4", 5, 4),
        Triple("5/8", 5, 8),
        Triple("7/8", 7, 8),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a layer") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Layers share the main clock, count-in, gap training and tempo ramp.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FieldLabel("Subdivision")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    subdivisions.forEach { subdivision ->
                        FilterChip(
                            colors = thaaFilterChipColors(),
                            selected = false,
                            onClick = { onSubdivision(subdivision) },
                            label = { Text(subdivision.shortLabel) },
                        )
                    }
                }
                FieldLabel("Polyrhythm")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    polyrhythms.forEach { (label, pulses, cycle) ->
                        FilterChip(
                            colors = thaaFilterChipColors(),
                            selected = false,
                            onClick = { onPolyrhythm(pulses, cycle) },
                            label = { Text(label) },
                        )
                    }
                }
                FieldLabel("Polymeter · layer cycle")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    polymeters.forEach { (label, beats, unit) ->
                        FilterChip(
                            colors = thaaFilterChipColors(),
                            selected = false,
                            onClick = { onPolymeter(beats, unit) },
                            label = { Text(label) },
                        )
                    }
                }
                Text(
                    text = "Polyrhythm uses a shared cycle; polymeter gives the layer its own bar length. " +
                        "The main ${settings.meter.label} pulse stays the reference.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerEditorDialog(
    layer: LayerSettings,
    onDismiss: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onSolo: (Boolean) -> Unit,
    onPulses: (Int) -> Unit,
    onCycle: (Double) -> Unit,
    onAccent: (Int) -> Unit,
    onGain: (Float) -> Unit,
    onOffset: (Double) -> Unit,
    onSwing: (Double) -> Unit,
    onStepOffset: (Int) -> Unit,
    onSound: (SoundSet) -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(layer.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "${layer.gridLabel} pulses across ${formatCycle(layer.cycleBeats)} beats. " +
                        "Tap a pulse to cycle accent, normal, ghost and mute.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ToggleRow(
                    title = "Enabled",
                    subtitle = "Silence this layer without losing its settings",
                    checked = layer.enabled,
                    onCheckedChange = onEnabled,
                )
                ToggleRow(
                    title = "Solo",
                    subtitle = "Silence the other layers while keeping the primary pulse",
                    checked = layer.solo,
                    onCheckedChange = onSolo,
                )
                Stepper(
                    label = "Pulses",
                    valueLabel = "${layer.pulses}",
                    supportingText = "Events across the layer cycle",
                    onDecrease = { onPulses(layer.pulses - 1) },
                    onIncrease = { onPulses(layer.pulses + 1) },
                )
                Stepper(
                    label = "Cycle",
                    valueLabel = "${formatCycle(layer.cycleBeats)} beats",
                    supportingText = "Short cycles repeat inside a long bar",
                    onDecrease = { onCycle(layer.cycleBeats - 0.5) },
                    onIncrease = { onCycle(layer.cycleBeats + 0.5) },
                )
                FieldLabel("Step timing")
                Text(
                    text = "Tap a pulse to cycle −20, −10, 0, +10 and +20 ms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    layer.stepOffsetsMs.forEachIndexed { index, offsetMs ->
                        FilterChip(
                            colors = thaaFilterChipColors(),
                            selected = offsetMs != 0.0,
                            onClick = { onStepOffset(index) },
                            label = {
                                Text("${index + 1} ${formatStepOffset(offsetMs)}")
                            },
                        )
                    }
                }
                FieldLabel("Pulse accents")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    layer.accentPattern.forEachIndexed { index, kind ->
                        LayerAccentChip(
                            index = index,
                            kind = kind,
                            onClick = { onAccent(index) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldLabel("Gain", modifier = Modifier.weight(1f))
                    Text(
                        text = "${(layer.gain * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Slider(
                    value = layer.gain,
                    onValueChange = onGain,
                    valueRange = 0f..1f,
                    colors = thaaSliderColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldLabel("Offset", modifier = Modifier.weight(1f))
                    Text(
                        text = "${layer.offsetMs.roundToInt()} ms",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Slider(
                    value = layer.offsetMs.toFloat(),
                    onValueChange = { onOffset(it.toDouble()) },
                    valueRange = -50f..50f,
                    colors = thaaSliderColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldLabel("Swing", modifier = Modifier.weight(1f))
                    Text(
                        text = swingLabel(layer.swingPercent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = layer.swingPercent.toFloat(),
                    onValueChange = { onSwing(it.toDouble()) },
                    valueRange = 50f..75f,
                    enabled = layer.pulses % 2 == 0,
                    colors = thaaSliderColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldLabel("Sound")
                ChoiceChips(
                    items = SoundSet.entries,
                    selected = layer.soundSet,
                    label = { it.displayName },
                    onSelect = onSound,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f)) {
                        Text("Preview")
                    }
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Text("Remove")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun LayerAccentChip(
    index: Int,
    kind: com.baijum.thaalam.data.BeatKind,
    onClick: () -> Unit,
) {
    val color = when (kind) {
        com.baijum.thaalam.data.BeatKind.ACCENT -> MaterialTheme.colorScheme.primary
        com.baijum.thaalam.data.BeatKind.NORMAL -> MaterialTheme.colorScheme.secondaryContainer
        com.baijum.thaalam.data.BeatKind.GHOST -> MaterialTheme.colorScheme.surfaceVariant
        com.baijum.thaalam.data.BeatKind.MUTE -> MaterialTheme.colorScheme.outlineVariant
    }
    val content = when (kind) {
        com.baijum.thaalam.data.BeatKind.ACCENT -> MaterialTheme.colorScheme.onPrimary
        com.baijum.thaalam.data.BeatKind.NORMAL -> MaterialTheme.colorScheme.onSecondaryContainer
        com.baijum.thaalam.data.BeatKind.GHOST -> MaterialTheme.colorScheme.onSurfaceVariant
        com.baijum.thaalam.data.BeatKind.MUTE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = color,
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Layer pulse ${index + 1}"
                stateDescription = kind.displayName
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = content,
            )
        }
    }
}

private fun formatStepOffset(value: Double): String {
    val rounded = value.roundToInt()
    return if (rounded > 0) "+$rounded" else "$rounded"
}

private fun formatCycle(value: Double): String =
    if (value == value.toInt().toDouble()) {
        value.toInt().toString()
    } else {
        ((value * 100.0).roundToInt() / 100.0).toString()
    }

@Composable
private fun PracticeCard(
    settings: MetronomeSettings,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCountIn: (Int) -> Unit,
    onGap: (Int, Int) -> Unit,
    onGapPattern: (List<Boolean>) -> Unit,
    onGapFade: (Int) -> Unit,
    onGapMaxSilent: (Int) -> Unit,
    onGapKeepSam: (Boolean) -> Unit,
    onMuteProbability: (Double) -> Unit,
    onProtectFirst: (Boolean) -> Unit,
    onRampEnabled: (Boolean) -> Unit,
    onRampTarget: (Double) -> Unit,
    onRampStep: (Double) -> Unit,
    onRampEvery: (Int) -> Unit,
) {
    SectionCard(
        title = "Practice",
        subtitle = practiceSummary(settings),
        trailing = {
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse practice tools" else "Expand practice tools",
                )
            }
        },
    ) {
        Stepper(
            label = "Count-in",
            valueLabel = "${settings.countInBars}",
            supportingText = "Bars of clicks before the pattern starts",
            onDecrease = { onCountIn(settings.countInBars - 1) },
            onIncrease = { onCountIn(settings.countInBars + 1) },
        )
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(modifier = Modifier.height(2.dp))
                if (settings.gapPattern.isEmpty()) {
                    Stepper(
                        label = "Gap · play bars",
                        valueLabel = "${settings.gapPlayBars}",
                        supportingText = "Click for this many bars, then go silent",
                        onDecrease = { onGap((settings.gapPlayBars - 1).coerceAtLeast(0), settings.gapSilentBars) },
                        onIncrease = { onGap((settings.gapPlayBars + 1).coerceAtMost(16), settings.gapSilentBars) },
                    )
                    Stepper(
                        label = "Gap · silent bars",
                        valueLabel = "${settings.gapSilentBars}",
                        supportingText = "Keep the clock running without clicks",
                        onDecrease = { onGap(settings.gapPlayBars, (settings.gapSilentBars - 1).coerceAtLeast(0)) },
                        onIncrease = { onGap(settings.gapPlayBars, (settings.gapSilentBars + 1).coerceAtMost(16)) },
                    )
                    TextButton(onClick = { onGapPattern(seedGapPattern(settings)) }) {
                        Text("Use a custom play/gap pattern")
                    }
                } else {
                    GapPatternEditor(
                        pattern = settings.gapPattern,
                        onPattern = onGapPattern,
                    )
                    TextButton(onClick = { onGapPattern(emptyList()) }) {
                        Text("Back to play/silent bars")
                    }
                }
                Stepper(
                    label = "Gap · fade bars",
                    valueLabel = "${settings.gapFadeBars}",
                    supportingText = "Fade the click over the last play bars before silence",
                    onDecrease = { onGapFade((settings.gapFadeBars - 1).coerceAtLeast(0)) },
                    onIncrease = { onGapFade((settings.gapFadeBars + 1).coerceAtMost(8)) },
                )
                Stepper(
                    label = "Gap · max silent",
                    valueLabel = "${settings.gapMaxSilentBars}",
                    supportingText = "Repairs a custom pattern that stays silent too long",
                    onDecrease = { onGapMaxSilent((settings.gapMaxSilentBars - 1).coerceAtLeast(1)) },
                    onIncrease = { onGapMaxSilent((settings.gapMaxSilentBars + 1).coerceAtMost(16)) },
                )
                ToggleRow(
                    title = "Keep sam in gaps",
                    subtitle = "A soft downbeat still marks the cycle while the rest is silent",
                    checked = settings.gapKeepSam,
                    onCheckedChange = onGapKeepSam,
                )
                if (settings.isGapTraining) {
                    TextButton(onClick = { onGap(0, 0) }) { Text("Turn gap training off") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FieldLabel("Random mute", modifier = Modifier.weight(1f))
                    Text(
                        text = "${(settings.muteProbability * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Slider(
                    value = settings.muteProbability.toFloat(),
                    onValueChange = { onMuteProbability(it.toDouble()) },
                    valueRange = 0f..1f,
                    colors = thaaSliderColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                ToggleRow(
                    title = "Protect the sam",
                    subtitle = "Never drop the first pulse of a bar",
                    checked = settings.protectFirstBeat,
                    onCheckedChange = onProtectFirst,
                )
                ToggleRow(
                    title = "Tempo ramp",
                    subtitle = "Step the BPM up or down while you practise",
                    checked = settings.rampEnabled,
                    onCheckedChange = onRampEnabled,
                )
                if (settings.rampEnabled) {
                    Stepper(
                        label = "Target BPM",
                        valueLabel = TempoMath.formatBpm(settings.rampTargetBpm),
                        onDecrease = { onRampTarget(settings.rampTargetBpm - 5.0) },
                        onIncrease = { onRampTarget(settings.rampTargetBpm + 5.0) },
                    )
                    Stepper(
                        label = "Step size",
                        valueLabel = TempoMath.formatBpm(settings.rampStepBpm),
                        onDecrease = { onRampStep(settings.rampStepBpm - 1.0) },
                        onIncrease = { onRampStep(settings.rampStepBpm + 1.0) },
                    )
                    Stepper(
                        label = "Every (bars)",
                        valueLabel = "${settings.rampEveryBars}",
                        onDecrease = { onRampEvery(settings.rampEveryBars - 1) },
                        onIncrease = { onRampEvery(settings.rampEveryBars + 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundCard(
    settings: MetronomeSettings,
    onSound: (SoundSet) -> Unit,
    onVolume: (Float) -> Unit,
    onPreview: () -> Unit,
) {
    SectionCard(
        title = "Sound",
        subtitle = "Synthesised on the audio thread — no sample latency",
        trailing = {
            IconButton(onClick = onPreview) {
                Icon(Icons.Filled.VolumeUp, contentDescription = "Preview current click sound")
            }
        },
    ) {
        ChoiceChips(
            items = SoundSet.entries,
            selected = settings.soundSet,
            label = { it.displayName },
            onSelect = onSound,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FieldLabel("Volume", modifier = Modifier.weight(1f))
            Text(
                text = "${(settings.volume * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Slider(
            value = settings.volume,
            onValueChange = onVolume,
            valueRange = 0f..1f,
            colors = thaaSliderColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FlashOverlay(enabled: Boolean, beat: Int, bar: Long) {
    var alpha by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(enabled, beat, bar) {
        if (enabled) {
            alpha = 0.13f
            kotlinx.coroutines.delay(55)
            alpha = 0f
        }
    }
    if (alpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
        )
    }
}

@Composable
private fun BpmDialog(
    currentBpm: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(TempoMath.formatBpm(currentBpm)) }
    val parsed = text.toDoubleOrNull()
    val valid = parsed != null && parsed in MetronomeSettings.MIN_BPM..MetronomeSettings.MAX_BPM
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set tempo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Beats per minute") },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                )
                Text(
                    text = "10 – 400 BPM, decimals welcome",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let(onConfirm) },
                enabled = valid,
            ) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeterDialog(
    current: Meter,
    onDismiss: () -> Unit,
    onConfirm: (Meter) -> Unit,
) {
    var beatsText by remember { mutableStateOf(current.beatsPerBar.toString()) }
    var unit by remember { mutableStateOf(current.beatUnit) }
    var groupingText by remember { mutableStateOf(current.grouping.joinToString("+")) }
    val beats = beatsText.toIntOrNull()
    val grouping = parseGrouping(groupingText)
    val valid = beats != null && beats in Meter.MIN_BEATS..Meter.MAX_BEATS &&
        (grouping == null || grouping.sum() == beats)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom meter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = beatsText,
                    onValueChange = { beatsText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Pulses per bar") },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FieldLabel("Beat unit")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Meter.beatUnits.forEach { candidate ->
                        FilterChip(
                            colors = thaaFilterChipColors(),
                            selected = unit == candidate,
                            onClick = { unit = candidate },
                            label = { Text("1/$candidate") },
                        )
                    }
                }
                OutlinedTextField(
                    value = groupingText,
                    onValueChange = { groupingText = it.filter { c -> c.isDigit() || c == '+' } },
                    label = { Text("Grouping (optional)") },
                    placeholder = { Text("2+2+3") },
                    singleLine = true,
                    isError = !valid,
                    supportingText = { Text("Leave blank for the default grouping") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (beats != null) onConfirm(Meter.normalized(beats, unit, grouping))
                },
                enabled = valid,
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private val meterPresets: List<Meter> = listOf(
    Meter(2, 4),
    Meter(3, 4),
    Meter(4, 4),
    Meter(5, 4),
    Meter(6, 8),
    Meter(7, 8),
    Meter(9, 8),
    Meter(12, 8),
)

private fun parseGrouping(text: String): List<Int>? {
    if (text.isBlank()) return null
    val parts = text.split('+').map { it.trim() }
    val values = parts.map { it.toIntOrNull() ?: return null }
    if (values.any { it <= 0 }) return null
    return values
}

private fun practiceSummary(settings: MetronomeSettings): String {
    val parts = mutableListOf("${settings.countInBars} count-in")
    if (settings.isGapTraining) {
        parts += if (settings.gapPattern.isEmpty()) {
            "${settings.gapPlayBars} on / ${settings.gapSilentBars} off"
        } else {
            "${settings.gapPattern.count { it }} play / ${settings.gapPattern.count { !it }} gap"
        }
        if (settings.gapFadeBars > 0) parts += "${settings.gapFadeBars}-bar fade"
    }
    if (settings.muteProbability > 0.0) {
        parts += "${(settings.muteProbability * 100).roundToInt()}% mute"
    }
    if (settings.rampEnabled) parts += "ramp to ${TempoMath.formatBpm(settings.rampTargetBpm)}"
    return parts.joinToString(" · ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GapPatternEditor(
    pattern: List<Boolean>,
    onPattern: (List<Boolean>) -> Unit,
) {
    FieldLabel("Custom pattern")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        pattern.take(8).forEachIndexed { index, play ->
            FilterChip(
                colors = thaaFilterChipColors(),
                selected = play,
                onClick = {
                    val updated = pattern.toMutableList()
                    if (index in updated.indices) {
                        updated[index] = !updated[index]
                    }
                    onPattern(updated)
                },
                label = { Text(if (play) "Play" else "Gap") },
            )
        }
    }
    Text(
        text = "Tap a bar to flip it between play and gap; the pattern repeats. " +
            "The first bar of the cycle is a downbeat re-entry.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun seedGapPattern(settings: MetronomeSettings): List<Boolean> {
    val legacy = if (settings.gapPlayBars > 0 && settings.gapSilentBars > 0) {
        List(settings.gapPlayBars) { true } + List(settings.gapSilentBars) { false }
    } else {
        listOf(true, true, false, true, true, false)
    }
    return legacy.take(8).ifEmpty { listOf(true, true, false) }
}

private fun swingLabel(percent: Double): String = when {
    percent < 50.5 -> "Straight"
    percent < 60.0 -> "${percent.roundToInt()}% · light shuffle"
    percent < 69.0 -> "${percent.roundToInt()}% · triplet swing"
    else -> "${percent.roundToInt()}% · dotted"
}

private fun bpmToSlider(bpm: Double): Float =
    (ln(bpm.coerceIn(10.0, 400.0) / 10.0) / ln(40.0)).toFloat().coerceIn(0f, 1f)

private fun sliderToBpm(value: Float): Double {
    val raw = 10.0 * 40.0.pow(value.toDouble().coerceIn(0.0, 1.0))
    return (raw * 10.0).roundToInt() / 10.0
}
