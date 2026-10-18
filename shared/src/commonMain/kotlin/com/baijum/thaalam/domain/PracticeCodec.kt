package com.baijum.thaalam.domain

import com.baijum.thaalam.data.PracticeData
import kotlinx.serialization.json.Json

/**
 * JSON persistence for the local practice log and goals.
 *
 * This is a separate key from settings and presets so a corrupt history can be
 * cleared without touching the metronome configuration. Unknown fields are
 * ignored, and every decoded value is normalized.
 */
object PracticeCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encode(data: PracticeData): String =
        json.encodeToString(PracticeData.serializer(), data.normalized())

    fun decode(raw: String?): PracticeData {
        if (raw.isNullOrBlank()) return PracticeData()
        return runCatching { json.decodeFromString(PracticeData.serializer(), raw) }
            .getOrDefault(PracticeData())
            .normalized()
    }

    /** CSV for sharing or spreadsheet review. Tap history is summarised per session. */
    fun toCsv(data: PracticeData): String {
        val header = listOf(
            "startedAtEpochMs",
            "endedAtEpochMs",
            "durationMs",
            "startBpm",
            "endBpm",
            "effectiveStartBpm",
            "effectiveEndBpm",
            "meter",
            "grouping",
            "tala",
            "preset",
            "layers",
            "practiceMode",
            "tapCount",
            "tapMeanAbsMs",
            "tapBiasMs",
        ).joinToString(",")
        val rows = data.normalized().sessions.map { session ->
            listOf(
                session.startedAtEpochMs.toString(),
                session.endedAtEpochMs.toString(),
                session.durationMs.toString(),
                format(session.startBpm),
                format(session.endBpm),
                format(session.effectiveStartBpm),
                format(session.effectiveEndBpm),
                escape(session.meterLabel),
                escape(session.groupingLabel),
                escape(session.talaName.orEmpty()),
                escape(session.presetName.orEmpty()),
                session.layerCount.toString(),
                escape(session.modeLabel),
                (session.tap?.tapCount ?: 0).toString(),
                session.tap?.let { format(it.meanAbsoluteOffsetMs) } ?: "",
                session.tap?.let { format(it.meanOffsetMs) } ?: "",
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 10.0) / 10.0
        return if (rounded == rounded.toInt().toDouble()) {
            rounded.toInt().toString()
        } else {
            rounded.toString()
        }
    }

    private fun escape(value: String): String {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
            return value
        }
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
