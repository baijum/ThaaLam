package com.baijum.thaalam.data

import kotlinx.serialization.Serializable

/**
 * Built-in click timbres. Every sound is synthesised in real time on the audio
 * thread, so there are no large sample assets, no codec latency, and the pitch
 * and decay can follow accents and subdivisions.
 */
@Serializable
enum class SoundSet(val displayName: String, val description: String) {
    CLICK("Click", "Crisp, bright electronic click"),
    WOODBLOCK("Woodblock", "Dry wooden rim tone"),
    BEEP("Beep", "Classic square-wave studio beep"),
    BELL("Bell", "Ringing bell with a long tail"),
    COWBELL("Cowbell", "Cutting low cowbell ping");

    companion object {
        val default: SoundSet = CLICK
    }
}
