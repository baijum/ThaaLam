package com.baijum.thaalam.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baijum.thaalam.audio.PlaybackState
import com.baijum.thaalam.data.AngaGesture
import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.ui.theme.LocalReduceMotion

/**
 * Group-aware beat grid. Each additive group gets its own softly raised capsule,
 * every pulse shows its section marker (for talas) and the theka bol where
 * available, and tapping a pulse cycles accent → normal → ghost → mute.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeatGrid(
    settings: MetronomeSettings,
    playback: PlaybackState,
    onBeatClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    markers: List<String?> = emptyList(),
    bols: List<String> = emptyList(),
    gestures: List<AngaGesture> = emptyList(),
    showLegend: Boolean = true,
) {
    val accentPattern = settings.accentPattern
    val grouping = settings.meter.grouping
    val groups = buildGroups(grouping, settings.meter.beatsPerBar)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { range ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        range.forEach { index ->
                            BeatPulse(
                                index = index,
                                kind = accentPattern.getOrElse(index) { BeatKind.NORMAL },
                                isActive = playback.isPlaying && playback.beat == index,
                                marker = markers.getOrNull(index),
                                bol = bols.getOrNull(index),
                                gesture = gestures.getOrNull(index) ?: AngaGesture.NONE,
                                onClick = { onBeatClick(index) },
                            )
                        }
                    }
                }
            }
        }
        if (showLegend) {
            BeatLegend()
        }
    }
}

@Composable
private fun BeatPulse(
    index: Int,
    kind: BeatKind,
    isActive: Boolean,
    marker: String?,
    bol: String?,
    gesture: AngaGesture,
    onClick: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val targetScale by animateFloatAsState(
        targetValue = if (isActive) 1.16f else 1f,
        animationSpec = if (reduceMotion) snap() else tween(90),
        label = "pulseScale",
    )
    val accentColor = MaterialTheme.colorScheme.primary
    val normalColor = MaterialTheme.colorScheme.secondary
    val ghostColor = MaterialTheme.colorScheme.tertiary
    val muteColor = MaterialTheme.colorScheme.outline

    val borderColor = when (kind) {
        BeatKind.ACCENT -> accentColor
        BeatKind.NORMAL -> normalColor
        BeatKind.GHOST -> ghostColor
        BeatKind.MUTE -> muteColor
    }
    val activeFill by animateColorAsState(
        targetValue = when {
            isActive && kind == BeatKind.ACCENT -> accentColor
            isActive && kind == BeatKind.NORMAL -> normalColor
            isActive && kind == BeatKind.GHOST -> ghostColor.copy(alpha = 0.55f)
            isActive -> muteColor.copy(alpha = 0.3f)
            kind == BeatKind.ACCENT -> accentColor.copy(alpha = 0.22f)
            kind == BeatKind.NORMAL -> normalColor.copy(alpha = 0.14f)
            kind == BeatKind.GHOST -> ghostColor.copy(alpha = 0.12f)
            else -> Color.Transparent
        },
        animationSpec = if (reduceMotion) snap() else tween(90),
        label = "pulseFill",
    )
    val textColor = when {
        isActive && kind != BeatKind.MUTE -> MaterialTheme.colorScheme.onPrimary
        kind == BeatKind.MUTE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val descriptor = buildString {
        append("Beat ${index + 1}, ${kind.displayName}")
        bol?.let { append(", $it") }
        if (gesture != AngaGesture.NONE) append(", ${gesture.displayName}")
        if (isActive) append(", current beat")
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = marker ?: " ",
            style = MaterialTheme.typography.labelSmall,
            color = borderColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            fontSize = 10.sp,
            modifier = Modifier.height(13.dp),
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .scale(targetScale)
                .background(activeFill, CircleShape)
                .border(1.5.dp, borderColor, CircleShape)
                .then(
                    if (kind == BeatKind.MUTE) {
                        Modifier.drawBehind {
                            drawLine(
                                color = muteColor,
                                start = Offset(size.width * 0.2f, size.height * 0.8f),
                                end = Offset(size.width * 0.8f, size.height * 0.2f),
                                strokeWidth = 1.5.dp.toPx(),
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = descriptor
                    role = Role.Button
                    if (isActive) stateDescription = "Current beat"
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = bol ?: gesture.shortLabel.ifEmpty { " " },
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive && (bol != null || gesture != AngaGesture.NONE)) {
                borderColor
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (isActive && bol != null) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            fontSize = 9.sp,
            modifier = Modifier
                .width(42.dp)
                .height(13.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeatLegend(modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendDot("Accent", MaterialTheme.colorScheme.primary)
        LegendDot("Normal", MaterialTheme.colorScheme.secondary)
        LegendDot("Ghost", MaterialTheme.colorScheme.tertiary)
        LegendDot("Mute", MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun buildGroups(grouping: List<Int>, beats: Int): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var start = 0
    grouping.forEach { length ->
        val end = (start + length - 1).coerceAtMost(beats - 1)
        if (start < beats) ranges += start..end
        start += length
    }
    if (ranges.isEmpty()) ranges += 0 until beats.coerceAtLeast(1)
    return ranges
}
