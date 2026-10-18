package com.baijum.thaalam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baijum.thaalam.data.AngaGesture
import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.Subdivision
import com.baijum.thaalam.data.Tala
import com.baijum.thaalam.data.TalaPlayback
import com.baijum.thaalam.data.Tradition
import com.baijum.thaalam.domain.TalaLibrary
import com.baijum.thaalam.domain.TempoMath
import com.baijum.thaalam.ui.components.FieldLabel
import com.baijum.thaalam.ui.components.SectionCard
import com.baijum.thaalam.ui.theme.LocalReduceMotion
import com.baijum.thaalam.ui.theme.thaaFilterChipColors
import com.baijum.thaalam.ui.theme.thaaSliderColors
import com.baijum.thaalam.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TalasScreen(
    viewModel: MainViewModel,
    onOpenMetronome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var traditionFilter by rememberSaveable { mutableStateOf<String>("all") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    val selected = remember(selectedId) { TalaLibrary.find(selectedId) }
    val filtered = remember(query, traditionFilter) {
        val base = when (traditionFilter) {
            "carnatic" -> TalaLibrary.carnatic
            "hindustani" -> TalaLibrary.hindustani
            else -> TalaLibrary.all
        }
        if (query.isBlank()) base else base.filter { tala ->
            tala.name.contains(query, ignoreCase = true) ||
                tala.details.contains(query, ignoreCase = true) ||
                tala.grouping.joinToString("+").contains(query.replace(" ", ""))
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Tala library",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "35 Suladi Sapta Talas, Chapu cycles and Hindustani theka.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search talas, groupings or bols") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All", "carnatic" to "Carnatic", "hindustani" to "Hindustani").forEach { (key, label) ->
                    FilterChip(
                        colors = thaaFilterChipColors(),
                        selected = traditionFilter == key,
                        onClick = { traditionFilter = key },
                        label = { Text(label) },
                    )
                }
            }
        }
        item {
            Text(
                text = "${filtered.size} cycles",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(filtered, key = { it.id }) { tala ->
            TalaCard(tala = tala, onClick = { selectedId = tala.id })
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (selected != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedId = null },
            sheetState = sheetState,
        ) {
            TalaDetailSheet(
                tala = selected,
                viewModel = viewModel,
                onDone = {
                    selectedId = null
                    onOpenMetronome()
                },
            )
        }
    }
}

@Composable
private fun TalaCard(tala: Tala, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "${tala.name}, ${tala.tradition.displayName}, ${tala.aksharas} aksharas"
                role = Role.Button
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tala.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${tala.tradition.displayName} · ${tala.aksharas} aksharas · ${tala.groupingLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tala.angas.forEachIndexed { index, anga ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = "${tala.markers[index] ?: "|"} ${anga.length}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TalaDetailSheet(
    tala: Tala,
    viewModel: MainViewModel,
    onDone: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val isLoaded = settings.talaId == tala.id
    val isActive = isLoaded && playback.isPlaying
    var nadai by remember(tala.id) {
        mutableStateOf(if (isLoaded) settings.subdivision else Subdivision.QUARTER)
    }
    var kalam by remember(tala.id) {
        mutableIntStateOf(if (isLoaded) settings.talaPlayback.kalam else 1)
    }
    var kalai by remember(tala.id) {
        mutableIntStateOf(if (isLoaded) settings.talaPlayback.kalai else 1)
    }
    var eduppu by remember(tala.id) {
        mutableDoubleStateOf(if (isLoaded) settings.talaPlayback.eduppuOffsetAksharas else 0.0)
    }

    val playbackOptions = TalaPlayback(kalam = kalam, kalai = kalai, eduppuOffsetAksharas = eduppu)
    val effectiveBpm = (settings.bpm * kalam)
        .coerceIn(MetronomeSettings.MIN_BPM, MetronomeSettings.MAX_EFFECTIVE_BPM)
    val activeGesture = if (isActive) tala.gestureAt(playback.beat) else AngaGesture.NONE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                text = tala.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${tala.tradition.displayName} · ${tala.aksharas} aksharas · ${tala.angaLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (tala.details.isNotEmpty()) {
            Text(
                text = tala.details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Cycle") {
            TalaCycle(
                tala = tala,
                activeBeat = if (isActive) playback.beat else -1,
                isPlaying = isActive,
            )
            Text(
                text = "The first pulse is sam. Clap/tali sections are accented; open khali sections are ghosted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isActive) {
                Text(
                    text = buildString {
                        append("Now: pulse ")
                        append(playback.beat + 1)
                        tala.bolAt(playback.beat)?.let {
                            append(" · ")
                            append(it)
                        }
                        if (activeGesture != AngaGesture.NONE) {
                            append(" · ")
                            append(activeGesture.displayName)
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (tala.bols.isNotEmpty()) {
            SectionCard(title = "Theka") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tala.bols.forEachIndexed { index, bol ->
                        val isCurrent = isActive && playback.beat == index
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = tala.markerAt(index) ?: " ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                            )
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ) {
                                Text(
                                    text = bol,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionCard(
            title = "Kalam",
            subtitle = "Akshara speed. The base tempo is kept, so 1x always restores it.",
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TalaPlayback.KALAM_OPTIONS.forEach { option ->
                    FilterChip(
                        colors = thaaFilterChipColors(),
                        selected = kalam == option,
                        onClick = {
                            kalam = option
                            if (isLoaded) viewModel.setKalam(option)
                        },
                        label = { Text("${option}x") },
                    )
                }
            }
            Text(
                text = "Base ${TempoMath.formatBpm(settings.bpm)} BPM → " +
                    "${TempoMath.formatBpm(effectiveBpm)} BPM at ${kalam}x",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(
            title = "Kalai",
            subtitle = "Clicks per akshara. 2-kalai doubles the current nadai.",
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TalaPlayback.KALAI_OPTIONS.forEach { option ->
                    FilterChip(
                        colors = thaaFilterChipColors(),
                        selected = kalai == option,
                        onClick = {
                            kalai = option
                            if (isLoaded) viewModel.setKalai(option)
                        },
                        label = { Text("$option-kalai") },
                    )
                }
            }
            Text(
                text = "${nadai.ticksPerBeat * kalai} click" +
                    "${if (nadai.ticksPerBeat * kalai == 1) "" else "s"} per akshara",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(
            title = "Eduppu",
            subtitle = "Where the cycle starts. The count-in ends just before this point.",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TalaPlayback.EDUPPU_OPTIONS.forEach { option ->
                    FilterChip(
                        colors = thaaFilterChipColors(),
                        selected = eduppu == option,
                        onClick = {
                            eduppu = option
                            if (isLoaded) viewModel.setEduppu(option)
                        },
                        label = { Text(TalaPlayback.eduppuLabel(option)) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FieldLabel("Custom", modifier = Modifier.weight(1f))
                Text(
                    text = TalaPlayback.eduppuLabel(eduppu),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = eduppu.toFloat(),
                onValueChange = {
                    eduppu = TalaPlayback.snapEduppu(it.toDouble())
                },
                onValueChangeFinished = {
                    if (isLoaded) viewModel.setEduppu(eduppu)
                },
                valueRange = -4f..4f,
                steps = 31,
                colors = thaaSliderColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Eduppu offset in aksharas"
                        stateDescription = TalaPlayback.eduppuLabel(eduppu)
                    },
            )
            Text(
                text = "Negative is atita (before sam); positive is anagata (after sam).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(
            title = "Subdivision",
            subtitle = if (tala.tradition == Tradition.CARNATIC) {
                "Nadai (gathi) multiplies the internal pulse of every akshara"
            } else {
                "Theka cycles usually click one pulse per akshara"
            },
        ) {
            val options = if (tala.tradition == Tradition.CARNATIC) {
                listOf(
                    Subdivision.QUARTER to "None",
                    Subdivision.TRIPLET to "Tisra 3",
                    Subdivision.SIXTEENTH to "Chatusra 4",
                    Subdivision.QUINTUPLET to "Khanda 5",
                    Subdivision.SEPTUPLET to "Misra 7",
                    Subdivision.NONUPLET to "Sankeerna 9",
                )
            } else {
                listOf(
                    Subdivision.QUARTER to "One per akshara",
                    Subdivision.EIGHTH to "Eighth",
                    Subdivision.TRIPLET to "Triplet",
                    Subdivision.SIXTEENTH to "Sixteenth",
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                options.forEach { (subdivision, label) ->
                    FilterChip(
                        colors = thaaFilterChipColors(),
                        selected = nadai == subdivision,
                        onClick = {
                            nadai = subdivision
                            if (isLoaded) viewModel.setSubdivision(subdivision)
                        },
                        label = { Text(label) },
                    )
                }
            }
        }

        SectionCard(title = "Tempo") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = TempoMath.formatBpm(settings.bpm),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = " base BPM",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (kalam > 1) {
                Text(
                    text = "Sounds at ${TempoMath.formatBpm(effectiveBpm)} BPM in ${kalam}x kalam",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "The tempo is preserved from your current session; adjust it on the metronome screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isLoaded) {
            Text(
                text = "Changes apply live · ${playbackOptions.summary}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    viewModel.applyTala(tala, nadai, autoStart = false, playback = playbackOptions)
                    onDone()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Load")
            }
            Button(
                onClick = {
                    viewModel.applyTala(tala, nadai, autoStart = true, playback = playbackOptions)
                    onDone()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isActive) "Restart" else "Start")
            }
        }
        TextButton(
            onClick = {
                if (!isLoaded) {
                    viewModel.applyTala(tala, nadai, autoStart = false, playback = playbackOptions)
                }
                viewModel.savePreset("${tala.name} · ${playbackOptions.summary}")
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save as a practice preset")
        }
        TextButton(onClick = onDone) {
            Text("Back to library")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TalaCycle(tala: Tala, activeBeat: Int, isPlaying: Boolean) {
    val grouping = tala.grouping
    val starts = tala.angaStarts
    var index = 0
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        grouping.forEach { length ->
            val groupStart = index
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(length) { offset ->
                        val beat = groupStart + offset
                        val kind = tala.beatKinds.getOrElse(beat) { BeatKind.NORMAL }
                        val active = isPlaying && activeBeat == beat
                        val color = when (kind) {
                            BeatKind.ACCENT -> MaterialTheme.colorScheme.primary
                            BeatKind.GHOST -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val marker = if (starts.contains(beat)) tala.markerAt(beat) else null
                            val gesture = tala.gestureAt(beat)
                            Text(
                                text = marker ?: gesture.symbol.ifEmpty { " " },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (marker != null) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                modifier = Modifier.height(13.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(
                                        if (active) color else color.copy(alpha = 0.14f),
                                        CircleShape,
                                    )
                                    .border(1.dp, color, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${beat + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                            Text(
                                text = tala.bolAt(beat) ?: " ",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 9.sp,
                                maxLines = 1,
                                modifier = Modifier.width(38.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            index += length
        }
    }
    Text(
        text = "Gestures: ● clap · ○ finger · ◐ wave · X tali · 0 khali",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
