# ThaaLam architecture

This document explains how a settings change turns into a click at an exact
sample frame, how talas map onto the same engine, and where the platform
boundaries are.

## 1. Layers

```
UI (Compose / SwiftUI)
  │ user intent
  ▼
Controller / ViewModel            (Android: MetronomeController, iOS: MetronomeViewModel)
  │ immutable MetronomeSettings
  ▼
Shared domain                     (Kotlin, no platform imports)
  ├── MetronomeSettings.normalized()
  ├── RhythmPattern.build(settings)
  ├── PracticeLogic (gap, mute, ramp)
  └── TempoMath (BPM ↔ frames)
  │ plain pattern + settings snapshot
  ▼
Platform audio engine             (AudioTrack / AVAudioSourceNode)
  │ sample-accurate click events + UI tick snapshots
  ▼
UI beat map, haptics, flash, notification
```

The shared module never references Android or Apple frameworks. The audio
engines never call back into musical domain logic while rendering: the pattern is
precomputed on the main thread, and the render path only walks an array of
steps.

## 2. The settings snapshot

`MetronomeSettings` is an immutable, serializable value:

| Field | Meaning |
|---|---|
| `bpm` | 10–400, fractional allowed |
| `meter` | pulses per bar, beat unit, additive grouping |
| `subdivision` | ticks per pulse (1–9) |
| `accentPattern` | one `BeatKind` per pulse: accent / normal / ghost / mute |
| `swingPercent` | 50–75, applied to odd subdivisions of even grids |
| `soundSet` | click timbre |
| `volume` | master gain |
| `countInBars` | 0–4 |
| `gapPlayBars`, `gapSilentBars` | deterministic gap trainer |
| `muteProbability`, `protectFirstBeat` | stochastic muting |
| `rampEnabled`, `rampTargetBpm`, `rampStepBpm`, `rampEveryBars` | tempo automation |
| `talaId` | provenance for UI display and presets |

Every mutation goes through `normalized()`, so range checks and pattern resizing
exist in one place. Swift uses `SettingsMutations` rather than Kotlin's
all-arguments `copy()`.

## 3. The click grid

`RhythmPattern.build(settings)` produces one bar:

```kotlin
for beat in 0 until meter.beatsPerBar:
    for sub in 0 until subdivision.ticksPerBeat:
        offset = beat + sub / ticks
        if swing applies and sub is odd:
            offset += (swingRatio - 0.5) * 2 / ticks
        kind = if (sub == 0) accentPattern[beat] else NORMAL
```

The result is sorted by offset and flattened. `gapBeatsAfter(i)` returns the
distance to the next step, wrapping to the next bar for the last step. Swing is
only applied when the grid is evenly divisible (`ticks % 2 == 0`); triplets,
quintuplets and septuplets are already tuplet grids and stay straight.

**Accent edits apply live.** Accent is resolved from the current settings at tick
time (`settings.accentPattern[step.beat]`), not baked into the precomputed
pattern, so tapping a pulse never restarts the bar. Meter, subdivision and swing
changes do rebuild the grid and restart it from the current buffer.

## 4. Android render loop

`MetronomeAudioEngine` owns a `Thread`:

```text
Process.setThreadPriority(URGENT_AUDIO)
AudioTrack(48 kHz, mono, PCM_FLOAT, LOW_LATENCY, MODE_STREAM)
buffer = FloatArray(256)
framePosition = 0
nextTickFrame = 0
barIndex = 1
stepIndex = 0
```

Per iteration:

1. Read the latest immutable `EngineConfig` (volatile reference).
2. If the pattern identity changed, reset `stepIndex` and schedule the new grid
   from the current frame.
3. While `nextTickFrame < framePosition + 256`:
   - compute the sample offset inside the buffer;
   - resolve the live accent and practice gates;
   - trigger `ClickSynth` at that offset if audible;
   - emit an `EngineTick` for the controller;
   - advance `stepIndex` and `nextTickFrame += gapBeats * framesPerBeat`.
4. Render all active click voices into the buffer, clipping to [-1, 1].
5. Block-write the buffer; advance `framePosition` by 256.

The engine is app-scoped through `MetronomeController`, so rotation and Activity
recreation do not interrupt playback. The foreground service only keeps the
process alive and exposes a transport notification; it does not own the audio
thread.

### Click synthesis

`ClickSynth` keeps twelve voices. Each voice has a start delay, phase, harmonic
phase, exponential envelope, noise amount and square/sine flag. When a bell tail
is longer than the buffer, `remaining` and the envelope carry into the next
buffer. New clicks are placed at `startFrame`, so a click scheduled for frame 180
is mixed at frame 180 rather than at the start of the buffer.

Sound presets are parameter tables (base frequency, harmonic ratio, decay, noise,
waveform). Accents raise pitch and gain; subdivisions lower gain and shorten
decay; ghost notes are softer. No sample assets, no resampling latency.

## 5. iOS render loop

`MetronomeAudioEngine` attaches an `AVAudioSourceNode` to an `AVAudioEngine`.
Its render block runs on the real-time audio thread and uses the same algorithm
as Android, with plain Swift structs:

- `EnginePattern` holds precomputed steps and gaps.
- `EngineConfig` holds the whole snapshot (pattern, accents, sound, practice
  settings, pattern key).
- `RenderState` owns the frame position, next tick, bar/step indices and LCG.
- Configuration updates happen on the main thread and replace the state inside
  an `OSAllocatedUnfairLock`; the render block takes a snapshot at buffer start.
- The latest tick is stored in a lock-protected `EngineTickSnapshot`. A 60 Hz
  main-thread timer publishes it to `@Published` properties; the audio thread
  never performs UI work or dispatch allocations.

`ClickSynth` mirrors the Android voice model with the same parameter tables. No
Kotlin call is made inside the render callback.

The audio session is `.playback` with `.mixWithOthers`, and `Info.plist` declares
`UIBackgroundModes: [audio]`. Playback stops on an audio interruption
(`AVAudioSession.interruptionNotification`).

## 6. Practice gating

For each tick the engines evaluate, in order:

```text
if count-in bar:              no gap / no stochastic mute
else if gapSilentBar:         mute every tick (clock still advances)
else if first pulse && protect: audibly play
else if random < probability:  mute
else:                         play
```

- `PracticeLogic.isGapSilentBar(bar, play, silent)` uses 1-based musical bars.
- `PracticeLogic.shouldMuteTick` receives a deterministic LCG value from the
  engine.
- `PracticeLogic.bpmForBar` computes the stepped ramp at bar boundaries.

The count-in uses a quarter-note version of the same bar and accent pattern, and
is announced separately to the UI (`isCountIn`, `countInBarsRemaining`). The
musical bar counter starts at 1 only after count-in.

## 7. Tala compilation

`TalaApplication.apply(tala, base, nadai)` produces ordinary settings:

```text
meter.beatsPerBar = tala.aksharas
meter.grouping    = tala.angas.map { it.length }
accentPattern     = tala.beatKinds
subdivision       = nadai (or quarter)
talaId            = tala.id
```

`Tala.beatKinds` walks the angas:

- every anga start is `ACCENT`;
- if the anga marker is `"0"` (khali), that start is `GHOST`;
- all other aksharas are `NORMAL`.

`TalaLibrary` generates the 35 Suladi talas from the seven canonical anga shapes
and five jathis (laghu length 3, 4, 5, 7, 9), then adds the Chapu and Hindustani
cycles. Hindustani vibhags carry explicit markers (`X`, `2`, `3`, `0`), and
theka syllables are stored one per akshara. The catalogue is data-only and
unit-tested.

Nadai is mapped to the ordinary subdivision enum:

| Nadai | Ticks per akshara |
|---|---|
| Tisra | 3 |
| Chatusra | 4 |
| Khanda | 5 |
| Misra | 7 |
| Sankeerna | 9 |

## 8. Persistence

Both platforms store JSON produced by the shared `PresetCodec`:

- settings: `MetronomeSettings.serializer()`
- presets: `List<MetronomePreset>`

Android uses `SharedPreferences`; iOS uses `UserDefaults`. The keys are
single-purpose strings (`thaalam.settings.v1`, `thaalam.presets.v1`). Unknown
JSON fields are ignored and every decoded settings object is normalized, so a
future schema can add fields without breaking old installs. Invalid JSON falls
back to defaults rather than crashing.

## 9. UI state flow

Android:

```
MetronomeAudioEngine.onTick ──▶ MetronomeController._playback (StateFlow)
                                      │
                                      ▼
                              MainViewModel ──▶ Compose
```

iOS:

```
AVAudioSourceNode render ──▶ EngineTickSnapshot (lock)
                                      │ 60 Hz poll
                                      ▼
                            MetronomeViewModel @Published ──▶ SwiftUI
```

`visualPlayback` is a second state stream delayed by the calibration value
(0–300 ms). Audio scheduling is never delayed; only the visual pulse is, so a
Bluetooth listener sees the flash when they hear the click.

## 10. Testing strategy

- **Shared unit tests** cover meter normalization, pattern offsets and swing,
  gap/stochastic/ramp logic, tap tempo, preset round-trips, all 35 Suladi talas,
  khali markers, theka lengths, and the Swift interop mutations.
- **Android lint** runs as a build gate; the app currently has warnings only.
- **Manual device/simulator checks** verify layout, transport, tala playback,
  background service, and light/dark rendering.
- A hardware latency loopback test is still recommended before release, since
  emulator and simulator audio paths are not representative of real output
  latency.

## 11. Known limitations

- Polyrhythm/polymeter layers and chained mixed-meter setlists are not yet
  implemented; the engine and tala compiler are ready to feed a multi-voice
  scheduler.
- No MIDI, Ableton Link or network sync.
- No practice log or microphone-based rhythm grading.
- No custom click sample import; the five synthesized timbres are built in.
- Background behaviour on iOS depends on the audio background mode and the
  session being active; a full device test is required before App Store release.
- The iOS UI uses fixed-size type for the stage-oriented layout; a full Dynamic
  Type audit is a good next accessibility task.
