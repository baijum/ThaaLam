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
| `layers` | up to three additional voices: pulses, cycle, accents, sound, gain, offset, step offsets, swing, solo |
| `swingPercent` | 50–75, applied to odd subdivisions of even grids |
| `soundSet` | click timbre |
| `volume` | master gain |
| `countInBars` | 0–4 |
| `gapPlayBars`, `gapSilentBars` | deterministic gap trainer |
| `muteProbability`, `protectFirstBeat` | stochastic muting |
| `rampEnabled`, `rampTargetBpm`, `rampStepBpm`, `rampEveryBars` | tempo automation |
| `talaId` | provenance for UI display and presets |
| `talaPlayback` | kalam (1/2/4×), kalai (1/2) and eduppu phase for the active tala |

`effectiveBpm = bpm × kalam` is the performance akshara rate; it is derived, not
stored, so 2× and 4× never overwrite the base tempo. `effectiveTicksPerBeat =
subdivision × kalai` is what the primary grid builder uses.

Every mutation goes through `normalized()`, so range checks and pattern resizing
exist in one place. Swift uses `SettingsMutations` rather than Kotlin's
all-arguments `copy()`.

## 3. The click grid

`RhythmPattern.build(settings)` produces one bar:

```kotlin
for beat in 0 until meter.beatsPerBar:
    for sub in 0 until settings.effectiveTicksPerBeat:
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
pattern, so tapping a pulse never restarts the bar. Meter, subdivision, kalai,
eduppu and swing changes do rebuild the grid and restart it from the current
buffer. Kalam is deliberately excluded from `gridKey` because it only changes the
tempo applied to the same grid.

**Kalai** multiplies the tick count (`subdivision × kalai`), so 2-kalai adds a
second click inside every akshara. **Eduppu** rotates the compiled step list by
the requested phase and leaves `leadInBeats` before the first click; the cycle
length and pulse identities are preserved. `gapBeatsAfter` includes that lead-in
when wrapping, so an eduppu that falls between two pulses stays on the frame
grid.

### Layers

`LayerCompiler.compile(settings)` turns the primary grid and every enabled
`LayerSettings` into a list of `VoicePattern` values. A layer is defined by
`pulses` evenly spaced across `cycleBeats`:

- a **subdivision mixer** layer uses `pulses = beats × ticks` over one bar;
- a **polyrhythm** layer uses a different pulse count over the primary cycle;
- a **polymeter** layer uses its own cycle length, so the cycles realign at
  their least common multiple.

The primary voice keeps the familiar pulse/beat metadata for the UI. Layer steps
carry a pulse index for live accent resolution and an `offsetMs` that already
includes the layer offset plus that pulse's micro-timing. `gridKey` changes only
when the grid itself changes, so gain, sound, offset, step-timing and accent
edits update the running voice without restarting it.

When any enabled layer is soloed, `LayerCompiler.compile()` keeps the primary
pulse and only the soloed layers. The primary remains the reference so soloing a
layer never removes the downbeat.

## 4. Android render loop

`MetronomeAudioEngine` owns a `Thread`:

```text
Process.setThreadPriority(URGENT_AUDIO)
AudioTrack(48 kHz, mono, PCM_FLOAT, LOW_LATENCY, MODE_STREAM)
buffer = FloatArray(256)
framePosition = 0
barIndex = 1
runtimes[4] = primary + up to three layer cursors
pendingBarAdvance = false
```

Per iteration:

1. Read the latest immutable `EngineConfig` (volatile reference).
2. Diff each voice by its stable id and `gridKey`. A changed grid resets that
   voice from the current frame; accent, gain, sound and offset changes only
   replace the pattern snapshot.
3. Repeatedly pick the voice whose next tick is earliest inside the buffer:
   - advance the primary bar only when its next tick is due (layers between the
     last tick of a bar and the next downbeat still belong to the old bar);
   - at the last primary step, compute the bar-line frame and schedule the next
     pattern at `boundary + nextPattern.leadInBeats`; this keeps a count-in →
     musical-bar transition exact even when eduppu gives the two grids different
     first-click offsets;
   - resolve the live accent, gesture and practice gates;
   - trigger `ClickSynth` at the exact sample offset if audible;
   - emit an `EngineTick` for the primary voice, including its anga gesture;
   - advance that voice's step and `nextTickFrame += gapBeats * framesPerBeat`.

Kalam changes arrive as a new `EngineConfig.bpm`; because the grid key is
unchanged, the running step cursor and bar position are preserved and only the
frame interval changes. A pending bar line is re-timed with the new tempo.
4. Render all active click voices into the buffer, clipping to [-1, 1].
5. Block-write the buffer; advance `framePosition` by 256.

Count-in uses the primary voice only. Layers advance silently through the
count-in so they enter in phase on the first musical bar. Gap bars mute every
voice; stochastic muting applies to the primary voice. The clock always advances.

The engine is app-scoped through `MetronomeController`, so rotation and Activity
recreation do not interrupt playback. The foreground service only keeps the
process alive and exposes a transport notification; it does not own the audio
thread.

### Click synthesis

`ClickSynth` keeps thirty-two voices. Each voice has a start delay, phase,
harmonic phase, exponential envelope, noise amount and square/sine flag. When a
bell tail is longer than the buffer, `remaining` and the envelope carry into the
next buffer. New clicks are placed at `startFrame`, so a click scheduled for
frame 180 is mixed at frame 180 rather than at the start of the buffer.

Sound presets are parameter tables (base frequency, harmonic ratio, decay, noise,
waveform). Accents raise pitch and gain; subdivisions lower gain and shorten
decay; ghost notes are softer. An anga gesture adds a final colour: clap adds
noise, finger count is brighter and shorter, wave is lower and longer, tali is a
slightly darker clap, and khali is quieter. No sample assets, no resampling
latency.

## 5. iOS render loop

`MetronomeAudioEngine` attaches an `AVAudioSourceNode` to an `AVAudioEngine`.
Its render block runs on the real-time audio thread and uses the same algorithm
as Android, with plain Swift structs:

- `EngineVoice` mirrors one compiled `VoicePattern` (steps, gaps, live accents,
  sound, gain, frame offset and grid key).
- `EngineConfig` holds the primary voice, count-in voice, layer voices, the
  kalam-scaled BPM and practice settings. A monotonic `revision` lets the render
  thread detect a new snapshot without comparing arrays.
- `RenderState` owns the frame position, one `VoiceRuntime` per voice, the bar
  index and the LCG. `pendingBarAdvance` moves the primary bar only when its next
  tick is due.
- Configuration updates happen on the main thread and replace the state inside
  an `OSAllocatedUnfairLock`; the render block takes a snapshot at buffer start.
- The latest tick is stored in a lock-protected `EngineTickSnapshot`. A 60 Hz
  main-thread timer publishes it to `@Published` properties; the audio thread
  never performs UI work or dispatch allocations.

`ClickSynth` mirrors the Android voice model with the same parameter tables and a
thirty-two voice pool. No Kotlin call is made inside the render callback.

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

Layer rules:

- Count-in voices only the primary; layers advance silently so they enter in
  phase on the first musical bar.
- Gap bars mute every voice. Stochastic muting applies to the primary voice.
- A new layer starts at the current frame, never at frame zero, so adding a
  voice mid-performance cannot emit a burst of catch-up clicks.
- Removing or disabling a layer drops its runtime; re-enabling starts a fresh
  phase from the next buffer.

## 7. Tala compilation

`TalaApplication.apply(tala, base, nadai, playback)` produces ordinary settings:

```text
meter.beatsPerBar = tala.aksharas
meter.grouping    = tala.angas.map { it.length }
accentPattern     = tala.beatKinds
subdivision       = nadai (or quarter)
talaId            = tala.id
talaPlayback      = kalam, kalai, eduppu
```

Kalam, kalai and eduppu are kept on the settings value rather than folded into
`bpm`. That gives the UI a stable base tempo, a live effective-BPM readout and a
grid that can be rotated without changing the underlying cycle.

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

### Kalam, kalai and eduppu

- **Kalam** multiplies the akshara rate (`effectiveBpm`). The base `bpm` is
  unchanged, and kalam is not part of `primaryGridKey`, so a live change does not
  restart the bar.
- **Kalai** multiplies the primary tick count. The count-in deliberately keeps
  one click per akshara so the count stays unambiguous; the main grid may have
  more clicks than the count-in, which the bar-line re-timing handles.
- **Eduppu** is a phase in aksharas, snapped to 0.25. Negative is atita (before
  sam), positive is anagata (after sam). `LayerCompiler.rotateForEduppu` rotates
  the compiled steps and `leadInBeats` holds the gap before the first click. The
  count-in is rotated by the same phase, so its final click lands immediately
  before the first musical click.

### Anga gestures

`Tala.gestures` derives one gesture per akshara:

- laghu: clap then finger counts;
- dhrutam: clap then wave;
- anudhrutam: clap;
- chapu: clap at the group start then finger counts;
- Hindustani vibhag: tali at `X`/tali markers, khali at `0`, no gesture inside
  the vibhag (the theka fills those aksharas).

`VoiceStep.gesture` carries the value into the compiled grid. The engines pass it
to `ClickSynth`, and the UI shows the same symbol/label on the pulse map and tala
cycle. Gestures are resolved from the tala when the primary grid is compiled, so
they do not affect accent edits or the grid key.

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

- **Shared unit tests** cover meter normalization, primary and layer pattern
  offsets and swing, polyrhythm/polymeter compilation, kalam/kalai/eduppu
  compilation and long-run drift, gesture mapping, a deterministic frame-level
  timing simulator, gap/stochastic/ramp logic, tap tempo, preset round-trips
  including layers and tala playback, all 35 Suladi talas, khali markers, theka
  lengths, and the Swift interop mutations.
- **Android lint** runs as a build gate; the app currently has warnings only.
- **Manual device/simulator checks** verify layout, transport, tala playback,
  adding/removing layers while playing, background service, and light/dark
  rendering.
- A hardware latency loopback test is still recommended before release, since
  emulator and simulator audio paths are not representative of real output
  latency.

## 11. Known limitations

- Polyrhythm, polymeter and the subdivision mixer are implemented. Chained
  mixed-meter songs and setlists are not yet implemented; the engine and tala
  compiler are ready to feed a song scheduler.
- Kalam, kalai, eduppu and anga gestures are implemented. The 108 Ashtottara
  talas are deferred until a verified published list and a practitioner review
  are available; the tala catalogue is data-only and can accept them later.
- Layer solo and per-pulse timing are implemented; the composite view shows each
  voice's own cycle rather than a single shared LCM ring.
- No MIDI, Ableton Link or network sync.
- No practice log or microphone-based rhythm grading.
- No custom click sample import; the five synthesized timbres are built in.
- Background behaviour on iOS depends on the audio background mode and the
  session being active; a full device test is required before App Store release.
- The iOS UI uses fixed-size type for the stage-oriented layout; a full Dynamic
  Type audit is a good next accessibility task.
