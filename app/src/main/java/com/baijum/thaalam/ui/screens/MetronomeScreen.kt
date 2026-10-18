package com.baijum.thaalam.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.baijum.thaalam.data.Meter
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.SoundSet
import com.baijum.thaalam.data.Subdivision
import com.baijum.thaalam.data.Tala
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
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

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
    var practiceExpanded by rememberSaveable { mutableStateOf(false) }

    val displayBpm = if (playback.isPlaying && settings.rampEnabled) {
        playback.effectiveBpm.takeIf { it > 0.0 } ?: settings.bpm
    } else {
        settings.bpm
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
                PracticeCard(
                    settings = settings,
                    expanded = practiceExpanded,
                    onToggleExpanded = { practiceExpanded = !practiceExpanded },
                    onCountIn = viewModel::setCountInBars,
                    onGap = viewModel::setGapTrainer,
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
                    stateDescription = "${settings.bpm.roundToInt()} beats per minute"
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
        else -> "Bar ${playback.bar} · Pulse ${playback.beat + 1} · ${TempoMath.formatBpm(playback.effectiveBpm)} BPM"
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

@Composable
private fun PracticeCard(
    settings: MetronomeSettings,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCountIn: (Int) -> Unit,
    onGap: (Int, Int) -> Unit,
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
        parts += "${settings.gapPlayBars} on / ${settings.gapSilentBars} off"
    }
    if (settings.muteProbability > 0.0) {
        parts += "${(settings.muteProbability * 100).roundToInt()}% mute"
    }
    if (settings.rampEnabled) parts += "ramp to ${TempoMath.formatBpm(settings.rampTargetBpm)}"
    return parts.joinToString(" · ")
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
