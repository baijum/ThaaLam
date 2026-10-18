package com.baijum.thaalam.ui.screens

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baijum.thaalam.data.PracticeData
import com.baijum.thaalam.data.PracticeSession
import com.baijum.thaalam.data.TapAccuracySummary
import com.baijum.thaalam.domain.PracticeDay
import com.baijum.thaalam.domain.PracticeStats
import com.baijum.thaalam.domain.PracticeStatsCalculator
import com.baijum.thaalam.ui.components.FieldLabel
import com.baijum.thaalam.ui.components.SectionCard
import com.baijum.thaalam.ui.components.Stepper
import com.baijum.thaalam.ui.theme.thaaSliderColors
import com.baijum.thaalam.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PracticeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val practice by viewModel.practice.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val tapAccuracy by viewModel.tapAccuracy.collectAsState()
    val preferences by viewModel.uiPreferences.collectAsState()
    val stats = remember(practice) {
        PracticeStatsCalculator.compute(practice, System.currentTimeMillis())
    }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Practice",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Local goals, honest feedback and a log that stays on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { TodayCard(stats = stats) }
        item { WeekCard(stats = stats) }
        item {
            GoalsCard(
                stats = stats,
                onGoals = viewModel::setPracticeGoals,
            )
        }
        item {
            TapAccuracyCard(
                isPlaying = playback.isPlaying,
                isCountIn = playback.isCountIn,
                tap = tapAccuracy.summary,
                lastOffsetMs = tapAccuracy.lastOffsetMs,
                calibrationMs = preferences.tapCalibrationMs,
                onTap = { viewModel.gradeTap(SystemClock.elapsedRealtime()) },
                onCalibration = viewModel::setTapCalibration,
            )
        }
        item { BestTempoCard(stats = stats) }
        item {
            SessionLogCard(
                data = practice,
                onClear = { confirmClear = true },
            )
        }
        item {
            ExportCard(
                onShare = {
                    sharePractice(context, viewModel.practiceCsv(), "text/csv", "ThaaLam practice CSV")
                },
                onShareJson = {
                    sharePractice(context, viewModel.practiceJson(), "application/json", "ThaaLam practice JSON")
                },
            )
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear practice history?") },
            text = { Text("This removes every recorded session and tap summary from this device. Goals are kept.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearPracticeHistory()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TodayCard(stats: PracticeStats) {
    SectionCard(
        title = "Today",
        subtitle = if (stats.hasSessions) {
            "${stats.streakDays}-day streak · ${stats.sessionCount} logged"
        } else {
            "Start the metronome to record your first session"
        },
        trailing = {
            Text(
                text = "${(stats.todayProgress * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatMinutes(stats.todayMinutes),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = " / ${stats.dailyGoalMinutes} min",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        LinearProgressIndicator(
            progress = { stats.todayProgress.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            StatPill("This week", formatMinutes(stats.weekMinutes))
            StatPill("All time", formatMinutes(stats.totalMinutes))
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun WeekCard(stats: PracticeStats) {
    val maxMinutes = stats.recentDays.maxOfOrNull { it.minutes }?.coerceAtLeast(1.0) ?: 1.0
    SectionCard(
        title = "Last seven days",
        subtitle = "Minutes by local day; overnight sessions are split at midnight",
        trailing = {
            Text(
                text = "${formatMinutes(stats.weekMinutes)} / ${stats.weeklyGoalMinutes} min",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            stats.recentDays.forEach { day ->
                DayBar(
                    day = day,
                    maxMinutes = maxMinutes,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayBar(day: PracticeDay, maxMinutes: Double, modifier: Modifier = Modifier) {
    val ratio = (day.minutes / maxMinutes).coerceIn(0.0, 1.0)
    val barHeight = (8 + 62 * ratio).dp
    val label = remember(day.date) {
        runCatching {
            LocalDate.parse(day.date)
                .dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                .take(2)
        }.getOrDefault(day.date.takeLast(2))
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    if (day.minutes > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .semantics {
                    contentDescription = "${day.date}: ${formatMinutes(day.minutes)}"
                },
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (day.isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun GoalsCard(stats: PracticeStats, onGoals: (Int, Int) -> Unit) {
    SectionCard(
        title = "Goals",
        subtitle = "A gentle target, not a streak trap",
    ) {
        Stepper(
            label = "Daily goal (min)",
            valueLabel = "${stats.dailyGoalMinutes}",
            supportingText = "Counted from local midnight",
            onDecrease = {
                onGoals((stats.dailyGoalMinutes - 5).coerceAtLeast(5), stats.weeklyGoalMinutes)
            },
            onIncrease = {
                onGoals((stats.dailyGoalMinutes + 5).coerceAtMost(240), stats.weeklyGoalMinutes)
            },
        )
        Stepper(
            label = "Weekly goal (min)",
            valueLabel = "${stats.weeklyGoalMinutes}",
            supportingText = "Monday to Sunday",
            onDecrease = {
                onGoals(
                    stats.dailyGoalMinutes,
                    (stats.weeklyGoalMinutes - 15).coerceAtLeast(stats.dailyGoalMinutes),
                )
            },
            onIncrease = {
                onGoals(
                    stats.dailyGoalMinutes,
                    (stats.weeklyGoalMinutes + 15).coerceAtMost(1_680),
                )
            },
        )
    }
}

@Composable
private fun TapAccuracyCard(
    isPlaying: Boolean,
    isCountIn: Boolean,
    tap: TapAccuracySummary?,
    lastOffsetMs: Double?,
    calibrationMs: Int,
    onTap: () -> Unit,
    onCalibration: (Int) -> Unit,
) {
    SectionCard(
        title = "Tap accuracy",
        subtitle = "Tap the pad with the main pulse while the metronome is running",
        trailing = {
            Text(
                text = if (isPlaying && !isCountIn) "LISTENING" else "IDLE",
                style = MaterialTheme.typography.labelSmall,
                color = if (isPlaying && !isCountIn) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (isPlaying && !isCountIn) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                )
                .pointerInput(isPlaying, isCountIn) {
                    detectTapGestures(
                        onPress = {
                            if (isPlaying && !isCountIn) onTap()
                            tryAwaitRelease()
                        },
                    )
                }
                .semantics {
                    contentDescription = "Tap accuracy pad"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        isPlaying && isCountIn -> "Count-in…"
                        isPlaying -> "TAP"
                        else -> "Start the metronome"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = lastOffsetLabel(lastOffsetMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (tap != null) {
            TapSummary(tap)
        } else {
            Text(
                text = "Tap along to see your signed error, consistency and early/late tendency.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FieldLabel("Calibration", modifier = Modifier.weight(1f))
            Text(
                text = "$calibrationMs ms",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Slider(
            value = calibrationMs.toFloat(),
            onValueChange = { onCalibration((it / 10f).roundToInt() * 10) },
            valueRange = -100f..200f,
            steps = 29,
            colors = thaaSliderColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Increase this if the grader reads your taps as consistently early; decrease it if they read late. " +
                "Output latency is estimated automatically; calibration is the final touch-to-sound correction.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TapSummary(tap: TapAccuracySummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            StatPill("Taps", "${tap.tapCount}")
            StatPill("Mean |error|", "${formatMs(tap.meanAbsoluteOffsetMs)} ms")
            StatPill("On time", "${tap.onTimePercent.roundToInt()}%")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            StatPill("Rating", tap.rating)
            StatPill("Tendency", tap.tendency)
            StatPill("Bias", "${formatSignedMs(tap.meanOffsetMs)} ms")
        }
        Histogram(tap.histogram, tap.toleranceMs)
    }
}

@Composable
private fun Histogram(histogram: List<Int>, toleranceMs: Double) {
    val max = histogram.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        histogram.forEachIndexed { index, count ->
            val offset = index * 10 - 100
            val barHeight = (4 + 40 * (count.toDouble() / max)).dp
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (abs(offset) <= toleranceMs) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
                        },
                    ),
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "−100 ms",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "+100 ms",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun BestTempoCard(stats: PracticeStats) {
    val entries = (stats.bestByTala.map { "Tala · ${it.label}" to it } +
        stats.bestByPreset.map { "Preset · ${it.label}" to it })
        .sortedByDescending { it.second.bpm }
        .take(5)
    SectionCard(
        title = "Best tempo",
        subtitle = "Highest effective BPM reached in a recorded session",
    ) {
        if (entries.isEmpty()) {
            Text(
                text = "Load a tala or preset, play, and your best tempo will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.forEach { (label, best) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${formatBpm(best.bpm)} BPM · ${best.sessions}×",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionLogCard(data: PracticeData, onClear: () -> Unit) {
    SectionCard(
        title = "Recent sessions",
        subtitle = if (data.sessions.isEmpty()) {
            "Nothing recorded yet"
        } else {
            "${data.sessions.size} kept on this device · newest first"
        },
        trailing = {
            if (data.sessions.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        },
    ) {
        data.sessions.take(6).forEach { session ->
            SessionRow(session)
        }
        if (data.sessions.isEmpty()) {
            Text(
                text = "Each start-to-stop run is recorded with tempo, meter or tala, practice modes and tap accuracy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionRow(session: PracticeSession) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.contextLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatDuration(session.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = buildString {
                    append(formatBpm(session.effectiveStartBpm))
                    append(" BPM · ")
                    append(session.modeLabel)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            session.tap?.let { tap ->
                Text(
                    text = "Tap ${tap.rating.lowercase()} · ${formatSignedMs(tap.meanOffsetMs)} ms · " +
                        "${tap.onTimePercent.roundToInt()}% on time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExportCard(onShare: () -> Unit, onShareJson: () -> Unit) {
    SectionCard(
        title = "Export",
        subtitle = "Share a local file; nothing is uploaded",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Text("CSV")
            }
            OutlinedButton(onClick = onShareJson, modifier = Modifier.weight(1f)) {
                Text("JSON")
            }
        }
        Text(
            text = "The CSV is spreadsheet-friendly; the JSON is a complete backup of sessions and goals.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun sharePractice(
    context: Context,
    text: String,
    mimeType: String,
    title: String,
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

private fun formatMinutes(minutes: Double): String {
    if (minutes <= 0.0) return "0m"
    if (minutes < 1.0) return "<1m"
    val total = minutes.roundToInt()
    val hours = total / 60
    val mins = total % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1_000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

private fun formatBpm(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    return if (rounded == rounded.toInt().toDouble()) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
}

private fun formatMs(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    return if (rounded == rounded.toInt().toDouble()) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
}

private fun formatSignedMs(value: Double): String {
    val formatted = formatMs(abs(value))
    return when {
        value > 0.5 -> "+$formatted"
        value < -0.5 -> "−$formatted"
        else -> "0"
    }
}

private fun lastOffsetLabel(offset: Double?): String = when {
    offset == null -> "No tap graded yet"
    offset > 15.0 -> "Late by ${formatMs(offset)} ms"
    offset < -15.0 -> "Early by ${formatMs(abs(offset))} ms"
    else -> "On time · ${formatSignedMs(offset)} ms"
}
