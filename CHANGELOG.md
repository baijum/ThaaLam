# Changelog

## 0.3.0 — Tala Mastery

### Added

- Kalam 1× / 2× / 4×: scales the akshara rate without overwriting the base
  tempo. Live changes keep the running bar and step cursor.
- Kalai 1 / 2: adds a second click inside every akshara and multiplies the
  selected nadai.
- Eduppu: start the cycle at sam, before sam (atita) or after sam (anagata),
  with common one-tap choices and a quarter-akshara custom slider. The count-in
  is phase-aligned so its last click lands immediately before the requested
  start point.
- Anga gesture model: clap, finger count, wave, tali and khali, derived from
  each tala's angas.
- Gesture timbres in the procedural click synth and gesture symbols/labels in
  the pulse map and tala cycle.
- Live theka bol highlighting, with Jhoomra and Tilwada bols added.
- Tala practice presets: save any kalam/kalai/eduppu configuration as an
  ordinary preset from the tala sheet.
- Shared `TalaPlayback` model, eduppu rotation and lead-in scheduling in both
  audio engines, and long-run drift tests.
- `TalaFactory` / `SettingsMutations` tala playback helpers for SwiftUI.

### Changed

- `MetronomeSettings` gains `talaPlayback`, `effectiveBpm` and
  `effectiveTicksPerBeat`. All are additive JSON with defaults, so older
  settings and presets still load.
- The engine's bar-line scheduling now applies the next pattern's lead-in, which
  keeps count-in → musical-bar transitions exact when eduppu and kalai give the
  two grids different first-click offsets.
- The effective tempo may exceed the 400 BPM base limit when kalam is active;
  frame math clamps to 1600 BPM instead of 400.
- Tempo ramps ramp the base tempo and then apply kalam, so the ramp target keeps
  its base-tempo meaning.

### Deferred

- The 108 Ashtottara talas. Published lists found during development disagree,
  and the roadmap requires a verified source plus a practitioner review. The
  catalogue is data-only and can be added later without engine changes.

### Verification

- 127 shared tests, including kalam tempo scaling, kalai grid expansion, eduppu
  rotation and count-in alignment, gesture mapping, backward-compatible JSON and
  a long-run kalam/kalai/eduppu drift test.
- Android debug/release/lint and iOS Debug/Release simulator builds pass.
- Manual smoke tests on a Pixel 9 emulator and iPhone 17 Pro simulator at 2×
  kalam / 2-kalai / ½-after-sam eduppu, including gesture labels and theka
  highlighting.

## 0.2.0 — Layers

### Added

- Up to three additional rhythmic voices over the primary pulse, mixed on the
  same sample-accurate clock.
- Subdivision mixer layers for eighth notes, sixteenths, triplets, quintuplets,
  septuplets, 1/32 and nonuplets.
- Polyrhythm templates from 3-over-2 through 7-over-6, plus custom pulse counts
  and cycle lengths.
- Polymeter layers with independent cycle lengths (3/4, 5/4, 5/8, 7/8).
- Per-layer enable, solo, gain, sound set, ±50 ms offset, swing and four-state
  accent editing.
- Per-pulse micro-timing offsets cycled through −20, −10, 0, +10 and +20 ms.
- Composite cycle rings showing the primary pulse and each active layer as dots
  on their own cycle, with an accessibility text summary.
- Layer data in presets and settings; old JSON without layers still decodes.

### Changed

- The Android `AudioTrack` and iOS `AVAudioSourceNode` engines now schedule a
  primary voice plus up to three layers with one master frame clock and one
  tempo.
- Bar boundaries advance when the next primary tick is due, so a layer tick
  before a count-in or gap boundary still belongs to the old bar.
- Click synthesis pools increased from 12 to 32 voices on both platforms.

### Verification

- 101 shared tests, including layer compilation, solo filtering, per-step
  offsets, polyrhythm/polymeter alignment, backward-compatible JSON and a
  30-minute drift test.
- Android debug/release/lint and iOS Debug/Release simulator builds pass.
- Manual smoke tests on a Pixel 9 emulator and iPhone 17 Pro simulator.

### Known follow-ups

- Real-device CPU, jitter and Bluetooth latency profiling.
- A full Dynamic Type pass over the older metronome, tala and preset screens.
