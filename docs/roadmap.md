# ThaaLam roadmap — next feature set

> Status: v0.2 in progress
> Baseline: v0.1.0, commit `f45f89d`
> Product constraints: offline only, no accounts, no analytics, no ads, no
> network permissions, no third-party audio SDKs without discussion.
>
> v0.2 progress: shared layer model, compiler, timing simulator and tests;
> multi-voice scheduling on Android and iOS; layer picker/editor on both
> platforms; preset persistence. Remaining: composite LCM view, solo controls,
> per-step micro-timing, performance profiling and release QA.

This plan turns the two research reports into a sequenced, release-sized
roadmap. It is deliberately opinionated: the next release is chosen for
architectural leverage, not just for the longest feature list.

## 1. Where we are

The first release covers the research reports' "must-have" core and several
"should-have" items:

- Sample-accurate frame scheduling on both platforms
- Meters, additive grouping, 1–9 subdivisions, swing, four accent states
- 35 Suladi Sapta Talas, Chapu talas, 8 Hindustani cycles, nadai
- Count-in, gap trainer, stochastic muting, stepped tempo ramp
- Presets, background playback, haptics, screen flash, visual latency offset
- Accessibility labels, reduce-motion handling, light/dark themes

The gaps that matter next are not more click sounds. They are **simultaneous
rhythmic layers**, **song/setlist workflows**, **practice feedback**, and
**depth in the tala model**.

## 2. How the next features were chosen

Each candidate was scored on four questions:

1. **Research evidence** — is it called out as a differentiator in the reports?
2. **User value** — does it change what a musician can practise or perform?
3. **Architectural leverage** — does it unlock several features at once?
4. **Risk** — does it need new permissions, hardware, or unproven DSP?

### Research-to-roadmap map

| Research feature | Report evidence | Current state | Decision |
|---|---|---|---|
| Simultaneous subdivision mixer | Report 2, "Tactile Subdivision Mixing"; report 1, sound/timing features | One subdivision grid at a time | **v0.2** |
| Polyrhythm / polymeter | Report 2, "Mathematical Resolution of Polyrhythms vs. Polymeters"; report 1, "Nice-to-have" | Not implemented | **v0.2** |
| Per-voice sound, gain, offset | Report 2, hybrid sound and micro-timing | Global sound/volume only | **v0.2** |
| Kalam, kalai, eduppu | Report 2, tala engine table | Basic akshara cycle only | **v0.3** |
| Anga-specific timbres / gestures | Report 2, anga gestures | Accent/ghost only | **v0.3** |
| 108 Ashtottara talas | Report 2, tala engine table | 46 cycles | **v0.3** |
| Hindustani bols | Report 2, tala engine table | Theka text only | **v0.3** |
| Setlists / song profiles | Report 2, stage UX; report 1, setlists | Flat presets only | **v0.4** |
| Chained mixed-meter sequences | Report 2, additive/mixed-meter sequencer | One meter at a time | **v0.4** |
| Count-off / auto-stop stage mode | Report 2, Tama-style live mode | Count-in only | **v0.4** |
| Continuous tempo ramps / tempo maps | Report 2, pedagogical automation | Stepped ramp only | **v0.4** |
| Bluetooth latency auto-detection | Report 2, dynamic A2DP compensation | Manual visual offset only | **v0.4** |
| Practice log, goals, streaks | Report 1, should-have; report 2, practice recording | Not implemented | **v0.5** |
| Deterministic gap + fade | Report 2, gap click | Gap bars, no fade/constraints | **v0.5** |
| Tap-accuracy grader | Report 2, Rhythm Coach | Tap tempo only | **v0.5** |
| Voice count-in | Report 1, should-have | Click count-in only | **v0.5** |
| Microphone rhythm coach / LiveBPM | Report 2, MIR | Not implemented | **v0.5 spike, then gate** |
| Drone / tanpura / temperament | Report 2, harmonic reference | Not implemented | **v0.6** |
| Custom click samples / EQ / pan | Report 2, hybrid sound | Five synth timbres | **v0.6** |
| Audio recording / export | Report 2, practice recorder | Not implemented | **v0.6** |
| Localization | Report 1, accessibility/localization | English only | **cross-cutting, language packs in v0.6** |
| MIDI, BLE pedals, Ableton Link | Report 2, stage connectivity | Not implemented | **deferred; needs a constraints decision** |
| Cloud, accounts, analytics, ads | Report 1, monetization | Deliberately absent | **out of scope** |

## 3. Recommended sequence

| Release | Theme | Main outcome | Indicative size |
|---|---|---|---|
| **v0.2** | **Layers** | Two to four simultaneous rhythmic voices, a subdivision mixer, polyrhythm and polymeter | 8–10 weeks |
| **v0.3** | **Tala Mastery** | Kalam, kalai, eduppu, anga timbres, 108 Ashtottara talas, richer Hindustani bols | 4–6 weeks |
| **v0.4** | **Songs & Stage** | Mixed-meter songs, setlists, count-off/auto-stop, stage view, continuous ramps, latency help | 6–8 weeks |
| **v0.5** | **Practice Intelligence** | Local practice log, goals, gap 2.0, tap-accuracy grader, count-in voice | 6–8 weeks |
| **v0.6** | **Pitch & Sound** | Drone/tanpura, temperaments, custom samples, EQ/pan, recording/export, localization | 8–10 weeks |

Estimates assume one experienced developer, both platforms, tests and manual
device checks. They are not commitments.

**Why Layers first.** One multi-voice scheduler unlocks the subdivision mixer,
polyrhythm, polymeter and per-voice sound design. Doing it while the codebase is
still small is cheaper than adding those features one by one on a single-voice
engine. It is also the largest unmet item in the research reports' "holy grail"
description.

**Why Tala Mastery before Songs.** The research roadmap is written from a
Western-metronome perspective. ThaaLam's identity is the tala engine, and kalam,
kalai and eduppu are core vocabulary for its audience, not optional polish. The
release is also mostly shared data and compiler work, so it carries less timing
risk than a song scheduler. If stage workflows become the commercial priority,
v0.3 and v0.4 can be swapped.

**Why practice feedback later.** A practice log is valuable but independent; it
can ship in any release. Microphone-based grading is the highest-risk feature in
either report (permissions, false onsets, latency, privacy) and should follow a
spike, not lead the roadmap.

### Small bets that can ship independently

These are useful if a smaller release is needed before or alongside v0.2:

- **A2DP latency suggestion** (1–2 weeks): detect the route, suggest a visual
  offset, keep the manual control.
- **Practice log MVP** (2–3 weeks): record sessions and show a weekly total,
  before goals and streaks.
- **Count-in voice** (1–2 weeks): bundled samples, no microphone.
- **String extraction** (about 1 week): move copy into resources before the new
  screens land, so localization is not a rewrite.

## 4. v0.2 — Layers

### Goal

Turn the single click grid into a small, deterministic rhythm workstation:
the main pulse plus up to three simultaneous layers, each with its own grid,
sound, gain and timing offset. This is the foundation for the subdivision mixer
and for polyrhythm/polymeter.

### User-facing scope

- **Layers card** on the metronome screen, below the pulse card and above
  practice controls. Each row: colour, name, on/off, gain, sound, grid label,
  mute/solo.
- **Add layer** sheet with templates:
  - Subdivision: 1/8, 1/16, 1/32, triplets, quintuplets, septuplets, nonuplets
  - Polyrhythm: 3:2, 4:3, 5:4, 7:5, 7:6, custom M:N
  - Polymeter: 3/4, 5/8, 7/8, custom cycle
- **Per-layer editing**: accent/normal/ghost/mute per pulse, gain, sound set,
  ±50 ms offset, and (where meaningful) swing.
- **Composite view**: the primary beat ring plus layer dots, with LCM markers
  showing where voices interlock. A text fallback lists each voice's pulse
  count and next pulse, so the feature is usable without colour.
- **Presets** save and restore layers. Old presets load with no layers.

### Semantics to settle before coding

These rules must be written into the shared tests, not discovered in the audio
thread:

- **Voice definition.** A layer is a sequence of evenly spaced pulses over a
  cycle: `pulses` per `cycleBeats`. The primary voice remains the existing
  meter, grouping, accent pattern and subdivision.
- **Polyrhythm.** The layer shares the primary cycle length; M pulses are
  spread across the same span as the primary N pulses. The visual LCM is
  `lcm(M, N)`.
- **Polymeter.** The layer has its own cycle length and pulse count; the pulse
  duration is shared with the primary. The cycles realign after
  `lcm(primaryBeats, layerBeats)` beats.
- **Subdivision mixer.** A layer uses `pulses = beatsPerBar × ticksPerBeat` over
  one primary bar. The main pulse remains the reference; the layer supplies the
  additional 8th/16th/triplet/etc. voice.
- **Count-in.** Count-in uses the primary voice only. Layers enter on the first
  musical bar so the count is unambiguous.
- **Gap and stochastic mute.** Gap bars mute all voices. Stochastic muting
  applies to the primary voice by default; a per-layer "follow practice" switch
  can be added later. The clock always advances.
- **Tempo ramp.** One master BPM; every voice scales with it. Polymeter layers
  keep their own cycle length but share the pulse duration.
- **Accent edits.** Live edits resolve from the current settings snapshot at
  tick time and never restart the bar.
- **Pattern changes.** Adding/removing a layer or changing its grid restarts
  that voice from the next buffer; it does not restart the primary bar unless
  the primary meter/subdivision changed.

### Data model

Additive, backward-compatible JSON:

```kotlin
@Serializable
data class LayerSettings(
    val id: String,
    val name: String = "",
    val enabled: Boolean = true,
    val pulses: Int = 2,             // pulses per cycle
    val cycleBeats: Double = 4.0,    // cycle length in primary beats
    val accentPattern: List<BeatKind> = listOf(ACCENT, NORMAL),
    val soundSet: SoundSet = SoundSet.CLICK,
    val gain: Float = 0.7f,
    val offsetMs: Double = 0.0,
    val swingPercent: Double = 50.0,
)

// MetronomeSettings
val layers: List<LayerSettings> = emptyList()
```

Rules in `normalized()`:

- maximum 3 layers (primary + 3 = 4 voices);
- `pulses` 1..64, `cycleBeats` 0.25..32.0, `offsetMs` −50..50;
- `accentPattern` resized to `pulses`;
- duplicate IDs rejected or regenerated;
- `gain` 0..1; `swingPercent` 50..75.

Do **not** rename existing fields. Add new fields with defaults so a v0.1
install can read a v0.2 settings blob, and document that a v0.1 app reading a
v0.2 blob will ignore the layers (acceptable one-way compatibility).

### Shared domain

New files:

- `data/LayerSettings.kt` — serializable layer value.
- `domain/VoicePattern.kt` — compiled voice: steps, gaps, cycle length,
  accent pattern, sound, gain, offset.
- `domain/LayerCompiler.kt` — primary + layers → `List<VoicePattern>`; owns
  polyrhythm/polymeter/subdivision math, LCM helpers and validation.
- `domain/TimingSimulator.kt` — deterministic frame-level simulation used by
  tests and golden fixtures.

Extend:

- `RhythmPattern.build()` stays the primary voice implementation.
- `MetronomeSettings.normalized()`, `withGroupAccents()`, `SettingsMutations`
  and `PresetFactory` understand layers.
- `PatternFactory.patternKey()` includes layer identity so engines rebuild
  only when a grid actually changes.

The compiler returns plain data. The audio engines still do no musical
reasoning in the render path.

### Engine changes

Android and iOS get the same structure:

```text
framePosition
voices: fixed array of VoiceClock
  stepIndex, nextTickFrame, cycleBar, pattern, sound, gain, offsetFrames
```

Per buffer:

1. Snapshot the immutable config (already the pattern today).
2. For each voice, schedule every tick inside the buffer window.
3. Mix all audible ticks into the same output buffer at their exact offsets.
4. Advance each voice's cursor and frame accumulator independently.
5. Publish a UI tick for the primary voice; include the layer id for optional
   layer animation.

Implementation notes:

- Fixed-size arrays, no allocation in the render path.
- A voice offset is converted to frames on the main thread and stored as
  `Double`, not milliseconds, so the render path does no floating-point
  conversion per tick.
- Limit the total clicks per buffer (e.g. 64) as the current guard does.
- Keep the single-voice path as the default and add a regression suite that
  compares one-voice output with the v0.1 scheduler.
- `EngineTick` gains `voiceId`; UI can ignore it when only the primary exists.

### iOS specifics

- `EngineConfig` becomes an array of plain `EngineVoice` values plus the
  existing practice/sound fields.
- `RenderState` owns a fixed array of `VoiceClock` structs.
- No KMP call in the render callback; the voice array is built on the main
  thread from the shared compiler.
- Keep the existing lock-protected config/tick snapshots.

### Android specifics

- `EngineConfig` holds `List<VoiceRuntime>` built on the controller thread.
- The audio loop iterates a fixed `Array<VoiceRuntime?>`.
- `ClickSynth` voice budget must cover four layers; raise the voice count or
  steal the oldest voice deterministically. Test bell tails with all layers on.

### UI plan

- `LayersCard` (Compose and SwiftUI): compact rows, add button, empty state.
- `LayerEditorSheet`: name, template, pulses/cycle, gain, sound, offset,
  accent grid, preview button.
- `PolyCycleView`: two or three concentric rings; primary on the outside.
  Reduce-motion mode shows static dots plus a text "beat x of y".
- Accessibility: every layer row reads "Layer 2, 5 against 4, woodblock,
  enabled, gain 70 percent"; the composite view exposes a text summary.
- Presets screen shows a "3 layers" badge.

### Tests and acceptance

Shared tests:

- Layer normalization (counts, ranges, accent resize, duplicate ids).
- Subdivision layer offsets for 1/8, 1/16, triplets and quintuplets.
- Polyrhythm 3:2, 4:3, 5:4 and 7:5: exact pulse offsets and LCM.
- Polymeter 4/4 vs 3/4 and 5/8: realignment after the correct number of beats.
- Old JSON without `layers` decodes; new JSON round-trips; a v0.2 preset with
  layers survives `normalized()` unchanged.
- Golden timing simulation: 60 seconds at 123.4 BPM, 5:4, no drift beyond one
  sample.
- One-voice regression against the v0.1 frame table.

Platform checks:

- Android debug/release/lint; iOS Debug/Release simulator builds.
- Instruments/Profiler: no allocations in the render callback, CPU within the
  agreed budget on the reference devices.
- Manual: add a 5:4 layer while playing; verify the primary bar does not
  restart; edit a layer accent live; save and reload a preset.
- Manual: 30-minute run with three layers; verify no audible drift or click
  dropout.
- Accessibility pass with TalkBack and VoiceOver on the layers sheet.

### Risks

| Risk | Mitigation |
|---|---|
| Multi-voice refactor regresses core timing | Keep one-voice path, golden fixtures, run both engines against the same simulation |
| UI becomes cluttered | Progressive disclosure: layers collapsed by default, templates first, advanced fields in the editor |
| Layer count exceeds synth polyphony | Fixed voice budget, oldest-voice stealing, test bell tails |
| Polyrhythm semantics confuse users | Template labels ("3 over 2"), composite LCM view, explanatory copy |
| Schema drift between platforms | Shared serializer + JSON fixture tests, no platform-specific fields |

### Out of scope for v0.2

- Custom samples
- MIDI, Link or network sync
- Practice logging
- Song/setlist playback
- Pitch/drone

## 5. v0.3 — Tala Mastery

### Goal

Make the tala engine musically complete for Carnatic and Hindustani practice,
using the same audio clock and no special playback mode.

### Scope

1. **Kalam** — 1x, 2x and 4x speed controls. Kalam changes the akshara rate
   while preserving the cycle structure. Store it separately from BPM so the
   user can switch kalam without losing the base tempo.
2. **Kalai** — 1-kalai and 2-kalai. Model the sub-unit expansion explicitly in
   `TalaPlayback`; validate the musical definition with a Carnatic practitioner
   before shipping.
3. **Eduppu** — start the cycle at a fractional offset before or after sam.
   Provide common choices (sam, half-akshara before, one akshara before,
   anagata after) plus a custom offset. The count-in must cue the eduppu, not
   just beat 1.
4. **Anga timbres** — distinguish clap, finger count, wave, tali and khali in
   the click sound and in the visual gesture. This requires a timbre reference
   on `RhythmStep` or a parallel anga map, resolved from the live snapshot.
5. **Ashtottara talas** — add the 108-cycle catalogue if a reliable source and
   a practitioner review are available. Keep it as data, not code.
6. **Hindustani bols** — display the theka with the current bol highlighted;
   optional spoken/sample playback can follow in v0.6.
7. **Tala practice presets** — kalam ladder (1x → 2x → 4x), nadai cycle, and
   eduppu drills, saved as ordinary presets.

### Data and compiler

- `TalaPlayback(kalam, kalai, eduppuOffsetAksharas)`.
- `TalaApplication.apply()` gains kalam/kalai/eduppu and writes the effective
  meter, subdivision and count-in offset.
- `Tala` gains optional `angaTimbres` or a mapping from `AngaType` to a
  `SoundSet`/click variant.
- `TalaLibrary` gains `ashtottara` and more Hindustani cycles; every entry
  tested for total aksharas, anga sums, marker placement and bol length.

### Acceptance

- Switching kalam while playing changes tempo without dropping or restarting
  the cycle.
- Eduppu count-in lands on the requested fractional offset.
- Every tala still compiles to a valid `MetronomeSettings` and plays with
  layers, swing, gap and ramp.
- All data tests pass; a Carnatic and a Hindustani practitioner review the
  labels and count-in behaviour.
- Both platform builds and accessibility pass.

## 6. v0.4 — Songs & Stage

### Goal

Move from a single looping setup to performed repertoire: songs made of
sections, setlists, hands-free stage behaviour and tempo that changes with the
music.

### Scope

1. **Song model** — a song is an ordered list of sections. Each section embeds
   a full `MetronomeSettings` snapshot (including layers), a bar count, repeats,
   count-in and an optional auto-stop.
2. **Mixed-meter sequencing** — sections can change meter, grouping, tempo,
   subdivision and accents at bar boundaries without stopping the transport.
3. **Setlists** — ordered songs, next/previous, reorder, duplicate, play
   through. Persist locally; export/import JSON through the platform share
   sheet, not the network.
4. **Count-off and auto-stop** — play N count-in bars, then either continue or
   mute/stop for the band; Tama-style "count off and stop".
5. **Stage view** — full-screen giant count, current/next section, progress,
   transport, tempo lock, keep-screen-on. No nested menus for start/stop.
6. **Tempo control deck** — rotary jog wheel with haptic detents, ±1 and ±0.1
   buttons, numeric keypad. Tap tempo stays.
7. **Continuous tempo ramps and tempo maps** — linear and exponential ramps
   between section tempos, plus stepped ramps; all computed from elapsed audio
   frames, not timers.
8. **Latency assistance** — read `AVAudioSession.outputLatency` and Android's
   output latency/route; detect A2DP and suggest a visual offset. Add an
   optional tap calibration assistant. Keep the existing manual visual offset.

### Engine model

- Build a flattened `List<BarPlan>` on the main thread from the song sections
  (bounded, e.g. 4096 bars; loop or stop at the end).
- Each `BarPlan` references an immutable voice pattern set and a BPM.
- The engine advances to the next bar at the bar boundary; pattern/tempo changes
  are applied before the next downbeat. Never cut a click tail.
- Manual next/previous jumps at the next bar boundary; a long-press can jump
  immediately after a confirmation.
- Count-in and auto-stop are engine states, not UI timers.
- The shared `SongCompiler` owns repeats, transition rules and validation; the
  platform engines only walk the compiled plan.

### Acceptance

- A 4/4 → 7/8 → 5/4 sequence plays without a gap, double click or phase jump.
- A five-song setlist advances, repeats and stops as configured.
- Count-off and auto-stop work from the stage view with the screen locked.
- Continuous ramp hits its target BPM at the requested bar and the effective
  BPM readout matches.
- A2DP detection suggests an offset; manual calibration overrides it.
- Background playback and notification transport still work.
- Both builds, tests, lint and accessibility pass.

## 7. v0.5 — Practice Intelligence

### Goal

Give players objective, local feedback and a reason to return, without
accounts, telemetry or cloud.

### Scope

1. **Practice log** — automatically record each transport run: start/end,
   duration, starting/ending BPM, meter/tala, preset/song, and which practice
   modes were active. Store locally, cap the history, allow clear.
2. **Goals and streaks** — daily/weekly minutes, streak, total practice time,
   best tempo per preset/tala. Compute in shared Kotlin.
3. **Gap 2.0** — deterministic play/gap patterns, fade-out over N bars,
   re-enter on a downbeat, optional protection of sam, and mute constraints
   (maximum consecutive muted beats, cluster by beat vs. subdivision).
4. **Tap-accuracy grader** — tap along with the click; compare touch timestamps
   with the scheduled grid; show signed error, rushing/dragging, a histogram
   and a per-session summary. This needs an explicit audio-frame-to-host-clock
   anchor (not the UI tick timestamps) and a measured touch-to-audio offset.
   Start with touch input; microphone later.
5. **Count-in voice** — bundled, offline voice samples for 1–8 and "and", with
   a language-pack structure. If assets are not feasible, keep the click
   count-in and defer.
6. **Microphone rhythm coach / LiveBPM spike** — a time-boxed prototype:
   onset detection, noise gate, latency compensation, permission handling and
   privacy copy. Gate the feature on measured accuracy and usability. Do not
   ship a grader that guesses.

### Storage

Start with the existing shared JSON codec and a bounded list (e.g. 500
sessions). If history grows beyond that, migrate to SQLDelight or a platform
database behind the same repository interface. Do not add a network service.

### Acceptance

- A session is recorded from start to stop and survives app restart.
- Goals/streaks compute correctly across time zones and midnight.
- Gap fade and mute constraints are deterministic and unit-tested.
- Tap accuracy is within the agreed tolerance on a simulated clock and shows
  useful feedback on a real device.
- Export produces a readable CSV/JSON via the share sheet.
- A clear-data action removes all practice history.
- The microphone spike has a written go/no-go with measurements; if no-go, the
  rest of the release still ships.

## 8. v0.6 — Pitch & Sound

### Goal

Make ThaaLam a complete practice companion for pitch-based traditions and give
users control over the click's character.

### Scope

1. **Drone / tanpura** — synthesized Sa–Pa drone with harmonics and a slow
   envelope, independent volume, A4 calibration (e.g. 415–466 Hz), equal and
   just intonation. Mix inside the same render path as the click so there is no
   second clock. Optional "drone after delay" only if microphone pitch tracking
   proves reliable.
2. **Temperaments** — equal, just, Pythagorean and one or two historical
   temperaments; shared pitch math and frequency tests.
3. **Custom click samples** — import WAV/AIFF/CAF through the document picker,
   decode to mono 48 kHz, trim/normalize, store on device, assign to a layer.
   Missing samples fall back to a synth timbre. No bundled copyrighted samples.
4. **EQ / pan** — per-layer gain and pan, master tone controls, and a limiter
   to prevent clipping with four layers plus drone.
5. **Recording / export** — optional microphone recording with the click and
   drone mixed, exported as WAV/M4A through the share sheet. Request permission
   only when used; explain local-only storage.
6. **Localization** — move all user-facing copy into string resources and a
   String Catalog, add Hindi and Tamil, audit Dynamic Type and long labels.
   Keep English as the base language.

### Acceptance

- Drone and click stay phase-locked for a 30-minute run.
- Temperament frequencies match the reference table within 0.1 cent.
- A user sample imports, previews, plays in a preset and survives relaunch.
- A missing sample file does not crash or silence the app.
- Recording/export works on both platforms with permission denied and granted
  paths tested.
- Localized builds compile; no hard-coded user-facing strings remain in the new
  screens.

## 9. Cross-cutting work

These are not separate releases; they are part of every release's definition of
done.

### Timing conformance

- A shared `TimingSimulator` generates tick frame tables for scenarios:
  straight 4/4, swing, tuplets, layers, polyrhythm, polymeter, gap, ramp.
- Commit golden fixtures; Android and iOS scheduler tests consume the same
  expected frames.
- Real-device loopback measurement before each release: record the click,
  measure inter-onset intervals, report jitter and drift.
- No timer, coroutine, `Handler`, `Timer`, `DispatchSourceTimer` or display
  refresh may schedule a click.

### Schema and migration

- Add fields with defaults; never rename existing JSON fields.
- Keep `thaalam.settings.v1` and `thaalam.presets.v1`; add `thaalam.songs.v1`
  and `thaalam.practice.v1` when needed.
- Golden JSON tests for: old settings, old presets, new settings, malformed
  JSON, unknown fields.
- If a breaking change is unavoidable, add a versioned key and a migration
  function with tests.

### UI parity and accessibility

- Every new control gets a label, value and state description.
- Colour is never the only layer/voice signal; use shape, text and position.
- Reduce motion applies to rings, pendulum and flash.
- Dynamic Type / large font layouts are checked on both platforms.
- Manual checklist: metronome, layers, tala sheet, song editor, stage view,
  practice log, settings.

### Localization readiness

- Put new strings in resources from v0.2 onward.
- Avoid concatenated sentences; use plural/quantity resources where needed.
- Language packs in v0.6; keep the UI copy short enough for translation.

### Performance and battery

- No allocations in the render callback; verify with profilers.
- Keep the audio thread work bounded with four voices plus drone.
- Measure battery for a 60-minute background run before each release.
- Keep app size reasonable; custom samples are user data, not bundled assets.

### Release checklist

```bash
./gradlew :shared:jvmTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:assembleRelease

cd iosApp
xcodegen generate
xcodebuild -project ThaaLam.xcodeproj -scheme ThaaLam \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -configuration Debug CODE_SIGNING_ALLOWED=NO build
xcodebuild -project ThaaLam.xcodeproj -scheme ThaaLam \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -configuration Release CODE_SIGNING_ALLOWED=NO build
```

Plus: version bump, changelog, roadmap/README/architecture updates, screenshots,
manual device checks, and a final `git diff --check`.

## 10. Deferred and decisions needed

### Deferred by the offline constraint

- **Ableton Link / network ensemble sync** — requires local network access and
  a third-party SDK. This conflicts with the current "offline only, no network
  permissions" rule. Defer unless the product constraint is explicitly changed;
  if changed, add an opt-in local-network permission and a privacy note.
- **Cloud sync, accounts, analytics, ads, subscriptions** — out of scope.
- **OSC / WebRTC / DAW sync** — out of scope.
- **AUv3 / audio unit hosting** — out of scope for a standalone practice app.

### Deferred pending hardware and permissions

- **MIDI clock / BLE-MIDI / foot pedals** — offline in principle, but needs
  platform MIDI/BLE permissions, hardware test devices and a separate UX.
  Revisit after v0.4 as a "Stage I/O" release.
- **Watch / Wear haptics** — a second app target, not a feature toggle.
- **Camera LED flash** — screen flash already covers the use case without a
  camera permission.

### Product decisions

1. **Practice log storage**: JSON first, database later. Confirm the history cap.
2. **Microphone coach**: approve the spike budget and the go/no-go criteria
   before v0.5.
3. **Tala sources**: choose the authoritative source for the 108 Ashtottara
   talas and arrange a practitioner review.
4. **Localization languages**: Hindi and Tamil are proposed; confirm.
5. **Monetization**: the research recommends a one-time "Pro" unlock rather
   than a subscription. That is a product/store decision, not part of this
   technical plan; no ads or telemetry should be added.
6. **Layer cap**: three extra layers is the proposed cap for v0.2. Confirm
   whether four or more is worth the UI and synth cost.

## 11. What success looks like

Without telemetry, success is measured by task tests and timing measurements:

- A musician can build a 5:4 polyrhythm in under 30 seconds and explain what
  they hear.
- A three-layer preset survives save/reload on both platforms without drift.
- A Carnatic student can switch kalam and start on an eduppu without leaving
  the tala screen.
- A performer can program a five-song setlist and use count-off/auto-stop on a
  dark stage.
- A practice session produces a log, a streak and an accuracy summary that the
  player trusts.
- The click remains sample-accurate: no drift over 30 minutes, no audible
  jitter, no timer-based scheduling.
- The app remains offline, permission-light and accessible.

## 12. Immediate next actions

1. Review and approve the v0.2 scope and the layer semantics in section 4.
2. Write the shared `LayerSettings`/`LayerCompiler` tests before touching either
   engine (spec-first).
3. Add the timing simulator and golden fixtures; capture the v0.1 one-voice
   baseline.
4. Implement the Android multi-voice engine behind the existing single-voice
   path, then port the same structure to iOS.
5. Build the layers UI and preset migration; run the full release checklist.
6. Re-estimate v0.3 after v0.2 lands; the tala work is data-heavy and may be
   smaller than the estimate.
