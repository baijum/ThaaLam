package com.baijum.thaalam.data

import android.content.Context
import com.baijum.thaalam.domain.PracticeCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local-only practice history.
 *
 * The log is bounded by [PracticeData.MAX_SESSIONS] and stored under its own
 * preference key, so clearing history never touches the metronome settings or
 * saved presets.
 */
class PracticeRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _data = MutableStateFlow(PracticeCodec.decode(prefs.getString(KEY_PRACTICE, null)))
    val data: StateFlow<PracticeData> = _data.asStateFlow()

    fun add(session: PracticeSession) {
        update { it.withSession(session) }
    }

    fun setGoals(goals: PracticeGoals) {
        update { it.withGoals(goals) }
    }

    fun clear() {
        update { it.cleared() }
    }

    fun json(): String = PracticeCodec.encode(_data.value)

    fun csv(): String = PracticeCodec.toCsv(_data.value)

    private fun update(transform: (PracticeData) -> PracticeData) {
        val updated = transform(_data.value).normalized()
        _data.value = updated
        prefs.edit().putString(KEY_PRACTICE, PracticeCodec.encode(updated)).apply()
    }

    private companion object {
        const val PREFS = "thaalam_settings"
        const val KEY_PRACTICE = "thaalam.practice.v1"
    }
}
