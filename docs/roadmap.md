# ThaaLam roadmap — next feature set

> Status: v0.5 shipped (0.5.0); v0.4 is deferred at the user's request. See **Current status** below.
> Baseline: v0.1.0, commit `f45f89d`.
> Product constraints: offline only, no accounts, no analytics, no ads, no
> network permissions, no third-party audio SDKs without discussion.

This plan turns the two research reports into a sequenced, release-sized
roadmap. It is deliberately opinionated: the next release is chosen for
architectural leverage, not just for the longest feature list.

## Current status

> As of 18 October 2026 · branch `main` · v0.5.0 release

| Release | Theme | Status | Version |
|---|---|---|---|
| v0.1 | Foundation | **Shipped** — sample-accurate metronome, meters, talas, practice tools | 0.1.0 |
| v0.2 | Layers | **Shipped** — multi-voice scheduling, subdivision mixer, polyrhythm, polymeter, solo, micro-timing | 0.2.0 |
| v0.3 | Tala Mastery | **Shipped** — kalam, kalai, eduppu, anga gestures, theka highlighting, tala practice presets | 0.3.0 |
| v0.4 | Songs & Stage | **Deferred** — skipped at the user's request; scoped but not started | — |
| v0.5 | Practice Intelligence | **Shipped** — local practice log, goals/streaks, Gap 2.0, tap-accuracy grader | 0.5.0 |
| v0.6 | Pitch & Sound | **Next candidate** — not started | — |

**Current verification:** 156 shared tests pass; Android debug, release and lint
pass; iOS Debug and Release simulator builds pass; manual smoke tests on a
Pixel 9 emulator and iPhone 17 Pro simulator.

**Open follow-ups from shipped releases:**

- Real-device CPU, jitter and Bluetooth latency profiling.
- A full Dynamic Type / large-font pass over the older screens.
- Carnatic and Hindustani practitioner review of the kalam, kalai, eduppu and
  gesture labels and count-in behaviour.
- The 108 Ashtottara talas remain **deferred** pending a verified published
  source and that practitioner review.
- Count-in voice remains deferred (no bundled/licensed voice assets).
- The microphone rhythm coach remains a **no-go spike**; see
  [microphone-spike.md](microphone-spike.md).

**Next milestone:** decision pending — v0.4 Songs & Stage (deferred) or v0.6
Pitch & Sound.

## 1. Where we are

ThaaLam has shipped three releases. The app now covers the research reports'
"must-have" core and most of the "should-have" items:

- **v0.1 — Foundation:** sample-accurate frame scheduling on both platforms;
  meters and additive grouping; 1–9 subdivisions; swing; four accent states;
  35 Suladi Sapta Talas, Chapu and Hindustani cycles; nadai; count-in, gap
  trainer, stochastic muting and stepped tempo ramp; presets, background
  playback, haptics, screen flash and visual latency offset.
- **v0.2 — Layers:** up to three additional voices on the same clock;
  subdivision mixer, polyrhythm and polymeter; per-layer sound, gain, swing,
  offset and solo; per-pulse micro-timing; composite cycle rings.
- **v0.3 — Tala Mastery:** kalam 1×/2×/4×, kalai 1/2, eduppu with a
  phase-aligned count-in, anga gesture timbres and visual gestures, live theka
  bol highlighting, and tala practice presets.
- **v0.5 — Practice Intelligence:** local practice log with goals, streaks and
  local-day splitting; Gap 2.0 patterns, fade and sam protection; touch-based
  tap-accuracy grader with histogram, bias and calibration; CSV/JSON export and
  clear-history. Count-in voice and the microphone coach remain deferred.

The remaining tala-depth item is the 108 Ashtottara talas. The catalogue is
data-only, so it can be added without engine changes once a reliable source and
practitioner review exist.

Practice feedback is now shipped. The gaps that matter next are
**song/setlist workflows** (v0.4, currently deferred) and **pitch/sound**
(v0.6).

## 2. How the next features were chosen

Each candidate was scored on four questions:

1. **Research evidence** — is it called out as a differentiator in the reports?
2. **User value** — does it change what a musician can practise or perform?
3. **Architectural leverage** — does it unlock several features at once?
4. **Risk** — does it need new permissions, hardware, or unproven DSP?

### Research-to-roadmap map

| Research feature | Report evidence | Baseline (v0.1) | Planned release | Status |
|---|---|---|---|---|
| Simultaneous subdivision mixer | Report 2, "Tactile Subdivision Mixing"; report 1, sound/timing features | One subdivision grid at a time | v0.2 | **Delivered (0.2.0)** |
| Polyrhythm / polymeter | Report 2, "Mathematical Resolution of Polyrhythms vs. Polymeters"; report 1, "Nice-to-have" | Not implemented | v0.2 | **Delivered (0.2.0)** |
| Per-voice sound, gain, offset | Report 2, hybrid sound and micro-timing | Global sound/volume only | v0.2 | **Delivered (0.2.0)** |
| Kalam, kalai, eduppu | Report 2, tala engine table | Basic akshara cycle only | v0.3 | **Delivered (0.3.0)** |
| Anga-specific timbres / gestures | Report 2, anga gestures | Accent/ghost only | v0.3 | **Delivered (0.3.0)** |
| 108 Ashtottara talas | Report 2, tala engine table | 46-cycle catalogue | v0.3 | **Deferred** (current catalogue: 47 cycles) |
| Hindustani bols | Report 2, tala engine table | Theka text only | v0.3 | **Delivered (0.3.0)** |
| Setlists / song profiles | Report 2, stage UX; report 1, setlists | Flat presets only | v0.4 | **Deferred** |
| Chained mixed-meter sequences | Report 2, additive/mixed-meter sequencer | One meter at a time | v0.4 | **Deferred** |
| Count-off / auto-stop stage mode | Report 2, Tama-style live mode | Count-in only | v0.4 | **Deferred** |
| Continuous tempo ramps / tempo maps | Report 2, pedagogical automation | Stepped ramp only | v0.4 | **Deferred** |
| Bluetooth latency auto-detection | Report 2, dynamic A2DP compensation | Manual visual offset only | v0.4 | **Deferred** |
| Practice log, goals, streaks | Report 1, should-have; report 2, practice recording | Not implemented | v0.5 | **Delivered (0.5.0)** |
| Deterministic gap + fade | Report 2, gap click | Gap bars, no fade/constraints | v0.5 | **Delivered (0.5.0)** |
| Tap-accuracy grader | Report 2, Rhythm Coach | Tap tempo only | v0.5 | **Delivered (0.5.0)** |
| Voice count-in | Report 1, should-have | Click count-in only | v0.5 | **Deferred** |
| Microphone rhythm coach / LiveBPM | Report 2, MIR | Not implemented | v0.5 spike, then gate | **No-go for 0.5.0** |
| Drone / tanpura / temperament | Report 2, harmonic reference | Not implemented | v0.6 | Planned |
| Custom click samples / EQ / pan | Report 2, hybrid sound | Five synth timbres | v0.6 | Planned |
| Audio recording / export | Report 2, practice recorder | Not implemented | v0.6 | Planned |
| Localization | Report 1, accessibility/localization | English only | Cross-cutting, language packs in v0.6 | Planned |
| MIDI, BLE pedals, Ableton Link | Report 2, stage connectivity | Not implemented | Deferred | **Deferred — needs a constraints decision** |
| Cloud, accounts, analytics, ads | Report 1, monetization | Deliberately absent | Out of scope | **Out of scope** |

## 3. Recommended sequence

| Release | Theme | Main outcome | Status | Original estimate |
|---|---|---|---|---|
| **v0.1** | **Foundation** | Sample-accurate engine, meters, accents, practice tools, first tala catalogue | **Shipped** | — |
| **v0.2** | **Layers** | Two to four simultaneous rhythmic voices, a subdivision mixer, polyrhythm and polymeter | **Shipped** (0.2.0) | 8–10 weeks |
| **v0.3** | **Tala Mastery** | Kalam, kalai, eduppu, anga timbres, richer Hindustani bols; Ashtottara deferred | **Shipped** (0.3.0) | 4–6 weeks |
| **v0.4** | **Songs & Stage** | Mixed-meter songs, setlists, count-off/auto-stop, stage view, continuous ramps, latency help | **Deferred — not started** | 6–8 weeks |
| **v0.5** | **Practice Intelligence** | Local practice log, goals, gap 2.0, tap-accuracy grader, count-in voice | **Shipped (0.5.0)**; count-in voice and mic coach deferred | 6–8 weeks |
| **v0.6** | **Pitch & Sound** | Drone/tanpura, temperaments, custom samples, EQ/pan, recording/export, localization | **Next candidate** | 8–10 weeks |

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
release was mostly shared data and compiler work, so it carried less timing risk
than a song scheduler. It shipped as 0.3.0.

**Why v0.5 before v0.4.** The user asked to skip Songs & Stage for now. Practice
Intelligence was largely independent of a song scheduler and could ship as
0.5.0. v0.4 remains scoped and deferred; it is the natural next stage release
when prioritised.

**Why practice feedback later.** A practice log is valuable but independent; it
shipped as v0.5 after the tala work. Microphone-based grading is the
highest-risk feature in either report (permissions, false onsets, latency,
privacy); the 0.5.0 spike is a documented no-go, not a shipped guesser.

### Small bets that can ship independently

These are useful if a smaller release is needed before or alongside v0.4:

- **A2DP latency suggestion** (1–2 weeks): detect the route, suggest a visual
  offset, keep the manual control. **Folded into v0.4 latency assistance.**
- **Practice log MVP** (2–3 weeks): record sessions and show a weekly total,
  before goals and streaks. **Folded into v0.5.**
- **Count-in voice** (1–2 weeks): bundled samples, no microphone. **Folded into
  v0.5.**
- **String extraction** (about 1 week): move copy into resources before the new
  screens land, so localization is not a rewrite. **Not started; still a
  cross-cutting prerequisite for v0.6 localization.**

## 4. v0.2 — Layers

> **Status: shipped as 0.2.0.** Kept here as the design record for the layer
> semantics.

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

> **Status: shipped as 0.3.0** (build 3). Kalam, kalai, eduppu, anga gestures
> and theka highlighting are in the app. Two scope items are not fully closed:
> the 108 Ashtottara talas are **deferred**, and automated kalam-ladder /
> nadai-cycle drills are **partial** (static drill presets work; automatic
> sequences need the v0.4 tempo-map/scheduler). The kalam, kalai, eduppu and
> gesture labels still need practitioner review.

### Scope status

| Scope item | Status | Notes |
|---|---|---|
| Kalam 1×/2×/4× | **Shipped** | Base tempo preserved; live changes keep the running bar |
| Kalai 1/2 | **Shipped, review pending** | Multiplies the primary tick grid; count-in stays one click per akshara; practitioner review of the 1/2-kalai definition still needed |
| Eduppu | **Shipped, review pending** | Sam, atita, anagata and custom quarter-akshara offset; count-in is phase-aligned; review of the count-in cue still needed |
| Anga timbres and gestures | **Shipped, review pending** | Clap, finger, wave, tali and khali in both sound and UI; chapu gesture interpretation is documented and needs review |
| Hindustani bols | **Shipped** | Live current-bol highlight; Jhoomra and Tilwada bols added |
| Tala practice presets | **Partial** | Any kalam/kalai/eduppu configuration can be saved as an ordinary preset; automatic kalam-ladder / nadai-cycle drills need a sequencer |
| 108 Ashtottara talas | **Deferred** | Needs a verified published list and practitioner review |

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
   **Deferred:** the published lists located during v0.3 disagree on names,
   anga structure and ordering, and no practitioner review was available. The
   catalogue is data-only, so it can be added in a later release without an
   engine change. Shipping a guessed list would be worse than shipping none.
6. **Hindustani bols** — display the theka with the current bol highlighted;
   optional spoken/sample playback can follow in v0.6.
7. **Tala practice presets** — kalam ladder (1x → 2x → 4x), nadai cycle, and
   eduppu drills, saved as ordinary presets. **Partial:** static drill presets
   are supported today; automatic ladders/cycles are deferred to the v0.4
   tempo-map/sequencer work.

### Data and compiler

- `TalaPlayback(kalam, kalai, eduppuOffsetAksharas)` — **implemented**.
- `TalaApplication.apply()` gains kalam/kalai/eduppu and writes the effective
  meter, subdivision and count-in offset — **implemented**; kalam scales the
  effective BPM, kalai scales the tick grid, and eduppu rotates the step list.
- `Tala` gains optional `angaTimbres` or a mapping from `AngaType` to a
  `SoundSet`/click variant — **implemented as `Tala.gestures` plus a gesture
  colour in `ClickSynth`** (clap/finger/wave/tali/khali).
- `TalaLibrary` gains `ashtottara` and more Hindustani cycles; every entry
  tested for total aksharas, anga sums, marker placement and bol length —
  **partly implemented**: Jhoomra and Tilwada bols shipped, gestures and bol
  lengths tested; `ashtottara` deferred.

### Acceptance status

- **Verified:** switching kalam while playing changes tempo without dropping or
  restarting the cycle (shared scheduling tests plus a live 2× → 4× simulator
  check).
- **Verified:** eduppu count-in lands on the requested fractional offset
  (shared simulator tests, including the count-in → musical-bar transition with
  kalai).
- **Verified:** every tala still compiles to a valid `MetronomeSettings` and
  plays with layers, swing, gap and ramp (shared tests and manual checks).
- **Verified:** 127 shared tests, Android debug/release/lint and iOS
  Debug/Release builds pass.
- **Outstanding:** Carnatic and Hindustani practitioner review of the labels and
  count-in behaviour; a full accessibility audit of the new tala controls.
- **Deferred:** Ashtottara catalogue, as above.

## 6. v0.4 — Songs & Stage

> **Status: deferred at the user's request; not started.** Scoped below and kept
> as the design record. Practice Intelligence (v0.5) shipped first.

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

> **Status: shipped as 0.5.0** (build 5). The practice log, goals/streaks, Gap 2.0
> and tap-accuracy grader are implemented. Count-in voice and the microphone
> coach remain deferred; the latter is a documented no-go.

### Scope status

| Scope item | Status | Notes |
|---|---|---|
| Practice log | **Shipped** | Start-to-stop sessions, tala/meter/preset context, practice modes, tap summary, 500-session cap, clear and CSV/JSON export |
| Goals and streaks | **Shipped** | Daily/weekly goals, local-midnight splitting, time-zone-aware streak, best tempo per tala/preset |
| Gap 2.0 | **Shipped** | Custom patterns, fade, max-silent repair, optional protected sam, downbeat re-entry |
| Tap-accuracy grader | **Shipped** | Touch input, host-clock targets, output-latency estimate, calibration, histogram and rating |
| Count-in voice | **Deferred** | No bundled/licensed voice assets; click count-in remains |
| Microphone coach / LiveBPM | **No-go for 0.5.0** | Permission, privacy, false-onset and latency risks; see [microphone-spike.md](microphone-spike.md) |

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
   **Partly implemented:** patterns, fade, downbeat re-entry, sam protection and
   a maximum consecutive *silent-bar* repair shipped; the finer beat-cluster
   constraints are deferred.
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
   **No-go for 0.5.0:** no microphone permission was added; the decision,
   criteria and future plan are in
   [microphone-spike.md](microphone-spike.md).

### Storage

Shipped as the shared JSON codec under `thaalam.practice.v1`, with a bounded
500-session list. If history grows beyond that, migrate to SQLDelight or a
platform database behind the same repository interface. Do not add a network
service.

### Acceptance status

- **Verified:** a session is recorded from start to stop and survives app
  restart (manual Android and iOS smoke tests).
- **Verified:** goals/streaks compute across time zones and midnight (shared
  tests with a fixed Asia/Kolkata zone and a midnight-crossing session).
- **Verified:** gap patterns, fade and the silent-run repair are deterministic
  and unit-tested. Finer beat-cluster constraints are deferred.
- **Verified:** tap accuracy is deterministic on a simulated clock (shared
  grader tests); the Android emulator shows the live summary, and an iOS smoke
  test persisted a tap summary into the session log. Real-device latency
  calibration is still recommended.
- **Verified:** CSV/JSON export and clear-history work through the platform
  share sheet / local action.
- **No-go:** the microphone spike has a written decision and criteria; no
  permission was added. Count-in voice is deferred for asset reasons.

## 8. v0.6 — Pitch & Sound

> **Status: planned.** Not started.

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
- Keep `thaalam.settings.v1`, `thaalam.presets.v1` and `thaalam.practice.v1`;
  add `thaalam.songs.v1` when v0.4 lands.
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

1. **Practice log storage**: shipped as shared JSON with a 500-session cap.
   Migrate to a platform database only if history grows beyond that.
2. **Microphone coach**: **no-go for 0.5.0**; no permission added. Revisit with a
   real-device test budget and the criteria in
   [microphone-spike.md](microphone-spike.md).
3. **Tala sources**: choose the authoritative source for the 108 Ashtottara
   talas and arrange a practitioner review.
4. **Localization languages**: Hindi and Tamil are proposed; confirm.
5. **Monetization**: the research recommends a one-time "Pro" unlock rather
   than a subscription. That is a product/store decision, not part of this
   technical plan; no ads or telemetry should be added.
6. **Layer cap**: three extra layers shipped in v0.2 and remain the cap.
   Confirm whether four or more is worth the UI and synth cost in a later
   release.

## 11. What success looks like

Without telemetry, success is measured by task tests and timing measurements:

- **Achieved (v0.2):** a musician can build a 5:4 polyrhythm in under 30 seconds
  and explain what they hear.
- **Achieved (v0.2):** a three-layer preset survives save/reload on both
  platforms without drift.
- **Achieved (v0.3, pending practitioner review):** a Carnatic student can
  switch kalam and start on an eduppu without leaving the tala screen.
- **Pending (v0.4):** a performer can program a five-song setlist and use
  count-off/auto-stop on a dark stage.
- **Achieved (v0.5, with real-device calibration outstanding):** a practice
  session produces a log, a streak and an accuracy summary that the player can
  inspect and export.
- **Achieved, with profiling outstanding:** the click remains sample-accurate
  in shared 30-minute drift tests and manual runs; real-device jitter and
  Bluetooth latency measurements are still recommended.
- **Achieved, with audit outstanding:** the app remains offline,
  permission-light and accessible; a full accessibility audit is still open.

## 12. Immediate next actions

1. Keep **Current status** and the release tables at the top of this document
   up to date at every release; that is the single source of truth for shipped
   scope.
2. ~~Review and approve the v0.2 scope and the layer semantics.~~ Done.
3. ~~Write the shared layer tests before touching either engine.~~ Done.
4. ~~Add the timing simulator and golden fixtures.~~ Done.
5. ~~Implement the Android multi-voice engine, then port the same structure to
   iOS.~~ Done.
6. ~~Build the layers UI and preset migration; run the full release checklist.~~
   Done.
7. ~~Re-estimate v0.3 after v0.2 lands.~~ Done; v0.3 shipped as 0.3.0.
8. ~~Ship v0.5 Practice Intelligence.~~ Done; 0.5.0 shipped.
9. **Next:** choose between the deferred v0.4 Songs & Stage and v0.6 Pitch &
   Sound.
10. **Before v0.4 ships:** verify the Ashtottara source and arrange a Carnatic /
    Hindustani practitioner review so the tala depth item can close.
11. **Carry into the next release:** real-device CPU/jitter/Bluetooth profiling,
    the full Dynamic Type pass, and a real-device tap-latency calibration.
12. **Keep deferred:** count-in voice assets and the microphone coach; revisit
    only with an explicit product decision and test budget.
