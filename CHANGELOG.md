# Changelog

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
