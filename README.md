# OPMIZER

A measure-first performance configuration mod for Minecraft (Fabric, client only).
It never promises FPS. Every number it shows was sampled from the running game.

MEASURE > CONFIGURE > TEST > COMPARE > APPLY

## Requirements
- Minecraft 26.1.2
- Fabric Loader 0.19.3 or newer
- Fabric API 0.155.2+26.1.2
- Java 25

Download the jar from the Releases page and put it in your mods folder.

## Status
Builds with GitHub Actions and runs in game on 26.1.2. It is an early version.
Report bugs with your logs/latest.log.

## Controls
- Command: /opm test (measures the game as it is right now)
- Q: open the config UI. T: start the test.
- 5 slots, each a complete configuration: select S1 to S5, edit, Save, Load, Revert, Test, Benchmark.

Vanilla uses Q for Drop Item and T for Chat. Rebind one side in Options > Controls.

## How measurement works
- Frame time is the interval between Minecraft.runTick calls (one mixin, one nanoTime per frame, only while testing).
- Average FPS = frames / summed frame time. Min FPS = 1 / slowest frame. 1% low = average FPS of the slowest 1% of frames.
- Tick time = client tick duration. Integrated server tick time is not measured yet.
- Detailed profiling (tests only): process CPU, client thread CPU, GC time.

Verdicts compare average FPS and 1% low with a +/-3% noise threshold: IMPROVED, REGRESSION, NO SIGNIFICANT CHANGE, UNDETERMINED.
One run per slot has run-to-run variance. Repeat before trusting a small difference.

Bottleneck labels are heuristics from measured CPU and GC data. With VSync, a frame limiter or missing data the answer is UNDETERMINED. CHUNK-BOUND is never reported yet.

Benchmark order: the newest run is shown first, and inside a run slots are ranked greatest first by the chosen metric. A best slot is only named if it beats the reference slot beyond the noise threshold. Your previous settings are restored afterwards.

## What is real and what is not
Real (applied through vanilla options): render distance, max FPS, VSync, clouds, smooth lighting, particles, mipmaps, biome blend, entity distance, entity shadows, simulation distance, client thread priority.

Shown as UNAVAILABLE instead of faked: graphics mode (option changed in 26.1, not wired yet), render backend swap (OPMIZER is not Vulkan), core affinity, worker and chunk threads, JVM heap.

## Known limitations
- No portable GPU counters, so GPU-bound is inferred only.
- Benchmark history is kept in memory (last 10 runs). Only slots persist, in config/opmizer/slots.json.
- Setting changes that reload chunks or textures need the Settle Time. Slot order can bias results, so repeat runs.
- Plain static UI, no animations.
- No automated tests yet.

## Building
GitHub Actions builds every push. Push a tag like v0.1.0 to publish a release with the jar.
