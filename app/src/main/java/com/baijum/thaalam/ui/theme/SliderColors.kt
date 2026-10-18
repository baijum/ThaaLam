package com.baijum.thaalam.ui.theme

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Consistent slider colours across every screen. */
@Composable
fun thaaSliderColors(): SliderColors = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    activeTickColor = Color.Transparent,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    inactiveTickColor = Color.Transparent,
    disabledThumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
    disabledActiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
    disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
)

/** Brand-consistent filter chips: saffron when selected, quiet neutral when not. */
@Composable
fun thaaFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
)
