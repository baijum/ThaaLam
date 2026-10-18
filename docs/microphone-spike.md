# Microphone rhythm-coach spike — decision record

> Status: **no-go for 0.5.0**. No microphone permission has been added.
> Revisit only with a real-device test budget and an explicit product decision.

## Why this is gated

The research reports list microphone-based rhythm coaching and LiveBPM tempo
induction as attractive features. They are also the highest-risk features in
either report:

- a new `RECORD_AUDIO` permission and a privacy explanation;
- false onsets from room noise, drums, voice and the metronome itself;
- audio input latency that differs from output latency and from device to device;
- echo cancellation and permission-denied paths;
- a grader that guesses is worse than no grader.

ThaaLam is offline and permission-light. Shipping a microphone feature without
measured accuracy would undermine both the product promise and the existing tap
grader, which has a known anchor and a calibration control.

## Proposed spike (time-boxed, not started)

1. Capture mono 48 kHz input through the platform audio API; no third-party SDK.
2. Onset detector: spectral flux or a small energy/derivative detector, tuned on
   a fixed corpus of claps, voice and tabla strokes recorded locally.
3. Reject the metronome's own output with a gate around scheduled click times
   (and optionally echo cancellation).
4. Measure input latency with a loopback click and report a per-device value.
5. Compare detected onsets with the same `TapGrader` error model used for touch.
6. Run a 20-minute session with headphones, speakers and a noisy room.
7. Write a go/no-go with false-positive/false-negative rates and latency error.

## Go/no-go criteria

Ship only if all are true:

- onset timing error is within ±25 ms on at least two reference devices;
- false positives stay below 2% in a quiet practice room and below 5% with a
  backing track at normal practice volume;
- the metronome's own click produces no self-triggered onsets;
- permission-denied and interrupted-audio paths are safe and explained;
- a privacy note states that audio is processed locally and never stored or sent.

If any criterion fails, keep the touch-based tap grader and do not add the
microphone permission.

## What shipped instead

- Touch-based tap accuracy with signed bias, consistency, histogram and rating.
- A manual calibration control for touch-to-sound latency.
- The existing click count-in, because no bundled/licensed count-in voice assets
  are available. The roadmap explicitly permits keeping the click count-in.
