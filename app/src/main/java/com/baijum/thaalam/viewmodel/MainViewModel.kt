package com.baijum.thaalam.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baijum.thaalam.audio.MetronomeController
import com.baijum.thaalam.audio.PlaybackState
import com.baijum.thaalam.data.LayerSettings
import com.baijum.thaalam.data.Meter
import com.baijum.thaalam.data.MetronomePreset
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.SoundSet
import com.baijum.thaalam.data.Subdivision
import com.baijum.thaalam.data.Tala
import com.baijum.thaalam.data.TalaPlayback
import com.baijum.thaalam.data.UiPreferences
import com.baijum.thaalam.domain.TalaLibrary
import com.baijum.thaalam.domain.TapTempoResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Single ViewModel for the app. It is a thin, testable façade over the
 * application-scoped [MetronomeController]; all real-time work stays off the
 * main thread and all UI state is exposed as [StateFlow].
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = MetronomeController.get(application)

    val settings: StateFlow<MetronomeSettings> = controller.settings
    val playback: StateFlow<PlaybackState> = controller.playback
    val visualPlayback: StateFlow<PlaybackState> = controller.visualPlayback
    val uiPreferences: StateFlow<UiPreferences> = controller.uiPreferences
    val tapState: StateFlow<TapTempoResult?> = controller.tapState
    val presets: StateFlow<List<MetronomePreset>> = controller.presets

    val currentTala: StateFlow<Tala?> = controller.settings
        .map { TalaLibrary.find(it.talaId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = controller.currentTala,
        )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // Transport
    fun togglePlayback() = controller.togglePlayback()
    fun stopPlayback() = controller.stopPlayback()

    // Tempo
    fun setBpm(value: Double) = controller.setBpm(value)
    fun nudgeBpm(delta: Double) = controller.nudgeBpm(delta)
    fun halfTempo() = controller.halfTempo()
    fun doubleTempo() = controller.doubleTempo()
    fun tapTempo() {
        controller.tapTempo()
    }

    fun resetTapTempo() = controller.resetTapTempo()

    // Meter and groove
    fun setMeter(meter: Meter) = controller.setMeter(meter)
    fun setSubdivision(subdivision: Subdivision) = controller.setSubdivision(subdivision)
    fun setSwing(percent: Double) = controller.setSwing(percent)
    fun cycleBeat(index: Int) = controller.cycleBeat(index)
    fun applyGroupAccents() = controller.applyGroupAccents()

    // Practice
    fun setCountInBars(bars: Int) = controller.setCountInBars(bars)
    fun setGapTrainer(playBars: Int, silentBars: Int) =
        controller.setGapTrainer(playBars, silentBars)
    fun setMuteProbability(probability: Double) = controller.setMuteProbability(probability)
    fun setProtectFirstBeat(protect: Boolean) = controller.setProtectFirstBeat(protect)
    fun setRampEnabled(enabled: Boolean) = controller.setRampEnabled(enabled)
    fun setRampTarget(bpm: Double) = controller.setRampTarget(bpm)
    fun setRampStep(step: Double) = controller.setRampStep(step)
    fun setRampEveryBars(bars: Int) = controller.setRampEveryBars(bars)

    // Sound
    fun setSoundSet(soundSet: SoundSet) {
        controller.setSoundSet(soundSet)
        controller.previewSound(soundSet)
    }

    fun setVolume(volume: Float) = controller.setVolume(volume)
    fun previewCurrentSound() = controller.previewSound()

    // Layers
    fun addSubdivisionLayer(subdivision: Subdivision) =
        controller.addSubdivisionLayer(subdivision)

    fun addPolyrhythmLayer(pulses: Int, cycleBeats: Double) =
        controller.addPolyrhythmLayer(pulses, cycleBeats)

    fun addPolymeterLayer(beats: Int, beatUnit: Int) =
        controller.addPolymeterLayer(beats, beatUnit)

    fun addLayer(layer: LayerSettings) = controller.addLayer(layer)
    fun removeLayer(id: String) = controller.removeLayer(id)
    fun clearLayers() = controller.clearLayers()
    fun setLayerEnabled(id: String, enabled: Boolean) = controller.setLayerEnabled(id, enabled)
    fun setLayerSolo(id: String, solo: Boolean) = controller.setLayerSolo(id, solo)
    fun cycleLayerStepOffset(id: String, index: Int) = controller.cycleLayerStepOffset(id, index)
    fun setLayerStepOffset(id: String, index: Int, offsetMs: Double) =
        controller.setLayerStepOffset(id, index, offsetMs)
    fun setLayerGain(id: String, gain: Float) = controller.setLayerGain(id, gain)
    fun setLayerOffset(id: String, offsetMs: Double) = controller.setLayerOffset(id, offsetMs)
    fun setLayerSound(id: String, soundSet: SoundSet) = controller.setLayerSound(id, soundSet)
    fun setLayerPulses(id: String, pulses: Int) = controller.setLayerPulses(id, pulses)
    fun setLayerCycleBeats(id: String, cycleBeats: Double) =
        controller.setLayerCycleBeats(id, cycleBeats)

    fun setLayerSwing(id: String, percent: Double) = controller.setLayerSwing(id, percent)
    fun cycleLayerAccent(id: String, index: Int) = controller.cycleLayerAccent(id, index)
    fun previewLayer(id: String) = controller.previewLayer(id)

    // Talas
    fun applyTala(
        tala: Tala,
        nadai: Subdivision,
        autoStart: Boolean,
        playback: TalaPlayback = TalaPlayback(),
    ) {
        controller.applyTala(tala, nadai, autoStart, playback)
        val action = if (autoStart) "Playing" else "Loaded"
        _messages.tryEmit("$action ${tala.name}")
    }

    fun setKalam(kalam: Int) = controller.setKalam(kalam)
    fun setKalai(kalai: Int) = controller.setKalai(kalai)
    fun setEduppu(eduppuOffsetAksharas: Double) =
        controller.setEduppu(eduppuOffsetAksharas)
    fun setTalaPlayback(playback: TalaPlayback) = controller.setTalaPlayback(playback)

    fun clearTala() = controller.clearTala()

    // Presets
    fun savePreset(name: String) {
        controller.savePreset(name)
        _messages.tryEmit("Preset saved")
    }

    fun applyPreset(preset: MetronomePreset) {
        controller.applyPreset(preset)
        _messages.tryEmit("Loaded ${preset.name}")
    }

    fun deletePreset(preset: MetronomePreset) {
        controller.deletePreset(preset.id)
        _messages.tryEmit("Deleted ${preset.name}")
    }

    // Preferences
    fun setHapticsEnabled(enabled: Boolean) = controller.setHapticsEnabled(enabled)
    fun setFlashEnabled(enabled: Boolean) = controller.setFlashEnabled(enabled)
    fun setKeepScreenOn(enabled: Boolean) = controller.setKeepScreenOn(enabled)
    fun setVisualLatency(ms: Int) = controller.setVisualLatency(ms)

    fun resetSettings() {
        controller.resetToDefaults()
        _messages.tryEmit("Settings reset")
    }
}
