package com.baijum.thaalam.audio

import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.baijum.thaalam.data.AngaGesture
import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.LayerSettings
import com.baijum.thaalam.data.Meter
import com.baijum.thaalam.data.MetronomePreset
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.PresetRepository
import com.baijum.thaalam.data.SettingsRepository
import com.baijum.thaalam.data.SoundSet
import com.baijum.thaalam.data.Subdivision
import com.baijum.thaalam.data.Tala
import com.baijum.thaalam.data.TalaPlayback
import com.baijum.thaalam.data.UiPreferences
import com.baijum.thaalam.domain.LayerFactory
import com.baijum.thaalam.domain.LayerMutations
import com.baijum.thaalam.domain.MetronomeStateLogic
import com.baijum.thaalam.domain.TalaApplication
import com.baijum.thaalam.domain.TalaLibrary
import com.baijum.thaalam.domain.TapTempo
import com.baijum.thaalam.domain.TapTempoResult
import com.baijum.thaalam.domain.TempoMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Snapshot of what the transport is doing right now. */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val bar: Long = 0,
    val beat: Int = -1,
    val sub: Int = 0,
    val kind: BeatKind = BeatKind.NORMAL,
    val isCountIn: Boolean = false,
    val countInBarsRemaining: Int = 0,
    val effectiveBpm: Double = 0.0,
    val gesture: AngaGesture = AngaGesture.NONE,
    /** Base BPM before kalam, useful for the tempo card subtitle. */
    val baseBpm: Double = 0.0,
)

/**
 * Application-scoped owner of the metronome.
 *
 * The audio engine, live settings, playback state, tap-tempo detector and
 * persistence all live here so playback survives Activity recreation and
 * continues while the app is in the background (the foreground service only
 * keeps the process alive and shows the notification).
 */
class MetronomeController private constructor(
    private val appContext: Context,
) {
    private val settingsRepository = SettingsRepository(appContext)
    private val presetRepository = PresetRepository(appContext)
    private val engine = MetronomeAudioEngine()
    private val tapTempo = TapTempo()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val vibrator = appContext.getSystemService(Vibrator::class.java)

    private val _settings = MutableStateFlow(settingsRepository.load().normalized())
    val settings: StateFlow<MetronomeSettings> = _settings.asStateFlow()

    private val _uiPreferences = MutableStateFlow(settingsRepository.loadUiPreferences())
    val uiPreferences: StateFlow<UiPreferences> = _uiPreferences.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackState())
    val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    /**
     * Beat visual state, optionally delayed by the user's Bluetooth latency
     * calibration so the flash lands with the sound reaching their ears.
     */
    private val _visualPlayback = MutableStateFlow(PlaybackState())
    val visualPlayback: StateFlow<PlaybackState> = _visualPlayback.asStateFlow()

    private val _tapState = MutableStateFlow<TapTempoResult?>(null)
    val tapState: StateFlow<TapTempoResult?> = _tapState.asStateFlow()

    val presets: StateFlow<List<MetronomePreset>> = presetRepository.presets

    private var focusRequest: AudioFocusRequest? = null
    private var focusGranted = false
    private var visualGeneration = 0

    init {
        engine.onTick = ::handleTick
        engine.onError = { message ->
            Log.e(TAG, message)
            mainHandler.post { stopPlayback() }
        }
    }

    // ---------------------------------------------------------------------
    // Transport
    // ---------------------------------------------------------------------

    fun togglePlayback() {
        if (_playback.value.isPlaying) stopPlayback() else startPlayback()
    }

    fun startPlayback() {
        if (_playback.value.isPlaying) return
        val settings = _settings.value
        if (!requestAudioFocus()) {
            Log.w(TAG, "Audio focus denied; starting anyway")
        }
        visualGeneration++
        _playback.value = PlaybackState(
            isPlaying = true,
            effectiveBpm = settings.effectiveBpm,
            baseBpm = settings.bpm,
        )
        _visualPlayback.value = _playback.value
        engine.start(settings)
        startForegroundService()
    }

    fun stopPlayback() {
        engine.stop()
        visualGeneration++
        _playback.value = PlaybackState()
        _visualPlayback.value = PlaybackState()
        abandonAudioFocus()
        appContext.stopService(Intent(appContext, MetronomeService::class.java))
    }

    fun previewSound(soundSet: SoundSet = _settings.value.soundSet) {
        engine.preview(_settings.value.copy(soundSet = soundSet))
    }

    private fun handleTick(tick: EngineTick) {
        val state = PlaybackState(
            isPlaying = true,
            bar = tick.bar,
            beat = tick.beat,
            sub = tick.sub,
            kind = tick.kind,
            isCountIn = tick.isCountIn,
            countInBarsRemaining = tick.countInBarsRemaining,
            effectiveBpm = tick.bpm,
            gesture = tick.gesture,
            baseBpm = _settings.value.bpm,
        )
        _playback.value = state
        val latency = _uiPreferences.value.visualLatencyMs
        if (latency <= 0) {
            _visualPlayback.value = state
        } else {
            val generation = visualGeneration
            mainHandler.postDelayed({
                if (generation == visualGeneration && _playback.value.isPlaying) {
                    _visualPlayback.value = state
                }
            }, latency.toLong())
        }
        if (tick.isMainBeat && tick.audible) {
            haptic(tick.kind)
        }
    }

    private fun haptic(kind: BeatKind) {
        if (!_uiPreferences.value.hapticsEnabled) return
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        val durationMs = if (kind == BeatKind.ACCENT) 16L else 9L
        val amplitude = when (kind) {
            BeatKind.ACCENT -> 255
            BeatKind.NORMAL -> 170
            BeatKind.GHOST -> 70
            BeatKind.MUTE -> 0
        }
        if (amplitude == 0) return
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        }
    }

    // ---------------------------------------------------------------------
    // Settings mutations
    // ---------------------------------------------------------------------

    fun updateSettings(transform: (MetronomeSettings) -> MetronomeSettings) {
        val updated = transform(_settings.value).normalized()
        if (updated == _settings.value) return
        _settings.value = updated
        settingsRepository.save(updated)
        engine.update(updated)
    }

    fun setBpm(value: Double) {
        updateSettings { it.copy(bpm = TempoMath.clampBpm(value)) }
    }

    fun nudgeBpm(delta: Double) {
        updateSettings { it.copy(bpm = TempoMath.clampBpm(it.bpm + delta)) }
    }

    fun halfTempo() = setBpm(TempoMath.half(_settings.value.bpm))

    fun doubleTempo() = setBpm(TempoMath.double(_settings.value.bpm))

    fun setMeter(meter: Meter) {
        val normalized = meter.normalized()
        updateSettings { current ->
            current.copy(
                meter = normalized,
                accentPattern = MetronomeStateLogic.groupAccentPattern(
                    normalized.beatsPerBar,
                    normalized.grouping,
                ),
                talaId = null,
            )
        }
    }

    fun setSubdivision(subdivision: Subdivision) {
        updateSettings { it.copy(subdivision = subdivision) }
    }

    fun setSwing(percent: Double) {
        updateSettings { it.copy(swingPercent = MetronomeStateLogic.clampSwing(percent)) }
    }

    fun cycleBeat(index: Int) {
        updateSettings { current ->
            val pattern = current.accentPattern.toMutableList()
            if (index in pattern.indices) {
                pattern[index] = MetronomeStateLogic.cycleBeatType(pattern[index])
            }
            current.copy(accentPattern = pattern)
        }
    }

    fun applyGroupAccents() {
        updateSettings { it.withGroupAccents() }
    }

    fun setSoundSet(soundSet: SoundSet) {
        updateSettings { it.copy(soundSet = soundSet) }
    }

    fun setVolume(volume: Float) {
        updateSettings { it.copy(volume = volume.coerceIn(0f, 1f)) }
    }

    // ---------------------------------------------------------------------
    // Layers
    // ---------------------------------------------------------------------

    fun addSubdivisionLayer(subdivision: Subdivision) {
        addLayer(LayerFactory.subdivision(_settings.value, subdivision))
    }

    fun addPolyrhythmLayer(pulses: Int, cycleBeats: Double) {
        addLayer(LayerFactory.polyrhythm(_settings.value, pulses, cycleBeats))
    }

    fun addPolymeterLayer(beats: Int, beatUnit: Int) {
        addLayer(LayerFactory.polymeter(_settings.value, beats, beatUnit))
    }

    fun addLayer(layer: LayerSettings) {
        if (_settings.value.layers.size >= LayerSettings.MAX_LAYERS) return
        updateSettings { it.copy(layers = it.layers + layer) }
    }

    fun removeLayer(id: String) {
        updateSettings { it.copy(layers = it.layers.filterNot { layer -> layer.id == id }) }
    }

    fun clearLayers() {
        updateSettings { it.copy(layers = emptyList()) }
    }

    fun setLayerEnabled(id: String, enabled: Boolean) {
        mutateLayer(id) { LayerMutations.setEnabled(it, enabled) }
    }

    fun setLayerSolo(id: String, solo: Boolean) {
        mutateLayer(id) { LayerMutations.setSolo(it, solo) }
    }

    fun cycleLayerStepOffset(id: String, index: Int) {
        mutateLayer(id) { LayerMutations.cycleStepOffset(it, index) }
    }

    fun setLayerStepOffset(id: String, index: Int, offsetMs: Double) {
        mutateLayer(id) { LayerMutations.setStepOffset(it, index, offsetMs) }
    }

    fun setLayerGain(id: String, gain: Float) {
        mutateLayer(id) { LayerMutations.setGain(it, gain.toDouble()) }
    }

    fun setLayerOffset(id: String, offsetMs: Double) {
        mutateLayer(id) { LayerMutations.setOffset(it, offsetMs) }
    }

    fun setLayerSound(id: String, soundSet: SoundSet) {
        mutateLayer(id) { LayerMutations.setSoundSet(it, soundSet) }
    }

    fun setLayerPulses(id: String, pulses: Int) {
        mutateLayer(id) { LayerMutations.setPulses(it, pulses) }
    }

    fun setLayerCycleBeats(id: String, cycleBeats: Double) {
        mutateLayer(id) { LayerMutations.setCycleBeats(it, cycleBeats) }
    }

    fun setLayerSwing(id: String, percent: Double) {
        mutateLayer(id) { LayerMutations.setSwing(it, percent) }
    }

    fun cycleLayerAccent(id: String, index: Int) {
        mutateLayer(id) { LayerMutations.cycleAccent(it, index) }
    }

    fun previewLayer(id: String) {
        val layer = _settings.value.layers.firstOrNull { it.id == id } ?: return
        engine.preview(_settings.value.copy(soundSet = layer.soundSet))
    }

    private fun mutateLayer(id: String, transform: (LayerSettings) -> LayerSettings) {
        updateSettings { current ->
            val index = current.layers.indexOfFirst { it.id == id }
            if (index < 0) return@updateSettings current
            val layers = current.layers.toMutableList()
            layers[index] = transform(layers[index])
            current.copy(layers = layers)
        }
    }

    fun setCountInBars(bars: Int) {
        updateSettings { it.copy(countInBars = bars.coerceIn(0, 4)) }
    }

    fun setGapTrainer(playBars: Int, silentBars: Int) {
        updateSettings {
            it.copy(
                gapPlayBars = playBars.coerceIn(0, 64),
                gapSilentBars = silentBars.coerceIn(0, 64),
            )
        }
    }

    fun setMuteProbability(probability: Double) {
        updateSettings { it.copy(muteProbability = probability.coerceIn(0.0, 1.0)) }
    }

    fun setProtectFirstBeat(protect: Boolean) {
        updateSettings { it.copy(protectFirstBeat = protect) }
    }

    fun setRampEnabled(enabled: Boolean) {
        updateSettings { it.copy(rampEnabled = enabled) }
    }

    fun setRampTarget(bpm: Double) {
        updateSettings { it.copy(rampTargetBpm = TempoMath.clampBpm(bpm)) }
    }

    fun setRampStep(step: Double) {
        updateSettings { it.copy(rampStepBpm = step.coerceIn(0.1, 20.0)) }
    }

    fun setRampEveryBars(bars: Int) {
        updateSettings { it.copy(rampEveryBars = bars.coerceIn(1, 64)) }
    }

    fun applyTala(
        tala: Tala,
        nadai: Subdivision = Subdivision.QUARTER,
        autoStart: Boolean = false,
        playback: TalaPlayback = TalaPlayback(),
    ) {
        updateSettings { current ->
            TalaApplication.apply(
                tala = tala,
                base = current,
                nadai = nadai,
                playback = playback,
            )
        }
        if (autoStart) startPlayback()
    }

    fun setKalam(kalam: Int) {
        updateSettings { TalaApplication.withKalam(it, kalam) }
    }

    fun setKalai(kalai: Int) {
        updateSettings { TalaApplication.withKalai(it, kalai) }
    }

    fun setEduppu(eduppuOffsetAksharas: Double) {
        updateSettings { TalaApplication.withEduppu(it, eduppuOffsetAksharas) }
    }

    fun setTalaPlayback(playback: TalaPlayback) {
        updateSettings { TalaApplication.withPlayback(it, playback) }
    }

    fun clearTala() {
        updateSettings { TalaApplication.clear(it) }
    }

    fun resetToDefaults() {
        updateSettings { MetronomeSettings().normalized() }
    }

    // ---------------------------------------------------------------------
    // Tap tempo
    // ---------------------------------------------------------------------

    fun tapTempo(): TapTempoResult {
        val result = tapTempo.tap(System.currentTimeMillis())
        _tapState.value = result
        result.bpm?.let { detected -> setBpm((detected * 10.0).toInt() / 10.0) }
        return result
    }

    fun resetTapTempo() {
        tapTempo.reset()
        _tapState.value = null
    }

    // ---------------------------------------------------------------------
    // Presets
    // ---------------------------------------------------------------------

    fun savePreset(name: String): MetronomePreset = presetRepository.add(name, _settings.value)

    fun applyPreset(preset: MetronomePreset) {
        updateSettings { preset.settings }
    }

    fun deletePreset(id: String) = presetRepository.delete(id)

    fun renamePreset(id: String, name: String) = presetRepository.rename(id, name)

    val currentTala: Tala? get() = TalaLibrary.find(_settings.value.talaId)

    // ---------------------------------------------------------------------
    // UI preferences
    // ---------------------------------------------------------------------

    fun updateUiPreferences(transform: (UiPreferences) -> UiPreferences) {
        val updated = transform(_uiPreferences.value)
        _uiPreferences.value = updated
        settingsRepository.saveUiPreferences(updated)
    }

    fun setHapticsEnabled(enabled: Boolean) =
        updateUiPreferences { it.copy(hapticsEnabled = enabled) }

    fun setFlashEnabled(enabled: Boolean) =
        updateUiPreferences { it.copy(flashEnabled = enabled) }

    fun setKeepScreenOn(enabled: Boolean) =
        updateUiPreferences { it.copy(keepScreenOn = enabled) }

    fun setVisualLatency(ms: Int) =
        updateUiPreferences { it.copy(visualLatencyMs = ms.coerceIn(0, 300)) }

    // ---------------------------------------------------------------------
    // Audio focus / foreground service
    // ---------------------------------------------------------------------

    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    mainHandler.post { stopPlayback() }
                }
            }
            .build()
        focusRequest = request
        focusGranted = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return focusGranted
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        val request = focusRequest ?: return
        if (focusGranted) manager.abandonAudioFocusRequest(request)
        focusGranted = false
        focusRequest = null
    }

    private fun startForegroundService() {
        runCatching {
            val intent = Intent(appContext, MetronomeService::class.java)
            appContext.startForegroundService(intent)
        }.onFailure { Log.w(TAG, "Could not start foreground service", it) }
    }

    companion object {
        private const val TAG = "ThaaLamController"

        @Volatile
        private var instance: MetronomeController? = null

        fun get(context: Context): MetronomeController =
            instance ?: synchronized(this) {
                instance ?: MetronomeController(context.applicationContext).also { instance = it }
            }
    }
}
