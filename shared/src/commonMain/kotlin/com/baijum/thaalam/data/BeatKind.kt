package com.baijum.thaalam.data

/**
 * Dynamic level assigned to a single pulse of a bar.
 *
 * [ACCENT] is the strong pulse (sam / downbeat), [NORMAL] a regular pulse,
 * [GHOST] a softer "shadow" pulse used for khali (open-hand) tala beats and
 * ghost-note practice, and [MUTE] a silent pulse that still advances the clock.
 */
enum class BeatKind {
    ACCENT,
    NORMAL,
    GHOST,
    MUTE;

    /** Relative click velocity, independent of the master volume. */
    val velocity: Float
        get() = when (this) {
            ACCENT -> 1.0f
            NORMAL -> 0.72f
            GHOST -> 0.34f
            MUTE -> 0.0f
        }

    val isAudible: Boolean get() = this != MUTE

    /** Human-readable name used by accessibility labels and settings. */
    val displayName: String
        get() = when (this) {
            ACCENT -> "Accent"
            NORMAL -> "Normal"
            GHOST -> "Ghost"
            MUTE -> "Mute"
        }
}
