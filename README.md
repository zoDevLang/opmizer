# OPMIZER

A measure-first performance configuration mod for Minecraft (Fabric, target 26.1.x, client only).
It never promises FPS. Every number it shows was sampled from the running game.

    MEASURE -> CONFIGURE -> TEST -> COMPARE -> APPLY

## STATUS - read this first

**This source has NOT been compiled or run.** It was written in an offline sandbox with no
Gradle, no javac and no Minecraft 26.1 jars. Treat it as a complete first draft.

Anything whose 26.1 name could not be checked is isolated:

| File | What may need renaming |
|------|------------------------|
| `compat/Compat.java` | Fabric key mapping + client command API, `Identifier`, `KeyMapping.Category`, chat/overlay calls |
| `ui/Gfx.java` | `GuiGraphics.fill` / `drawString` (26.1 may use a different render API) |
| `ui/OpmizerScreen.java` | `Screen.render`, `renderBackground`, `mouseScrolled`, `Button.builder`, `minecraft` field |
| `config/SettingRegistry.java` | `Options` accessors and the `GraphicsStatus` / `CloudStatus` / `ParticleStatus` packages |
| `mixin/MinecraftMixin.java` | `Minecraft.runTick(boolean)` |
| `gradle.properties` | **All four versions are guesses.** Copy the real ones from https://fabricmc.net/develop |

## Build

1. Add a Gradle wrapper (`gradle wrapper`, or copy it from the Fabric example mod).
2. Fix `gradle.properties` using fabricmc.net/develop (Java 25 is expected for 26.1).
3. `./gradlew build`, then `./gradlew runClient`.
4. Send compiler errors back; they should be confined to the files above.

## Controls (exactly these)

* **Command:** `/opm test` - measures the game as it is right now.
* **Q:** open the config UI. **T:** start the test.
* **5 slots**, each a complete configuration. Select (S1-S5), edit, Save, Load, Revert, Test, Benchmark.

Q and T are the requested defaults, but vanilla uses Q for Drop Item and T for Chat. Rebind
whichever you prefer in Options > Controls.

## How measurement works

* Frame time = interval between consecutive `Minecraft.runTick` calls (one mixin, one `nanoTime()` per frame, only while testing).
* Average FPS = frames / summed frame time. Min FPS = 1 / slowest frame. 1% low = average FPS of the slowest 1% of frames.
* Tick time = client tick duration (`START/END_CLIENT_TICK`). Integrated-server MSPT is not measured yet.
* Detailed profiling (tests only): process CPU, client-thread CPU, GC time via JMX.
* Buffers are preallocated once; nothing runs when no test is active.

### Verdicts
Compared on average FPS and 1% low with a +/-3% noise threshold: `IMPROVED`, `REGRESSION`,
`NO SIGNIFICANT CHANGE`, or `UNDETERMINED` (invalid data, or average and 1% low disagree).
One run per slot still has run-to-run variance. Repeat before trusting a small difference.

### Bottleneck labels
Heuristics from the measured CPU / GC data: `MAIN-THREAD-BOUND`, `CPU-BOUND`, `MEMORY-BOUND`,
`GPU-BOUND` (CPU idle while frames are slow), `MIXED`, `UNDETERMINED`. If VSync or a frame limiter is
active, or data is missing, the answer is `UNDETERMINED`. `CHUNK-BOUND` is never reported (no chunk instrumentation).

### Benchmark - "Newest to Greatest"
Interpreted as: the newest run is shown first (with older runs below it), and within a run slots
are ranked greatest-first by the chosen metric. A "best" slot is only named if it beats the
reference slot beyond the noise threshold. Your previous settings are restored afterwards.

## What is real and what is not

Real, applied through vanilla `OptionInstance`s: render distance, max FPS, VSync, graphics mode,
clouds, smooth lighting, particles, mipmaps, biome blend, entity distance, entity shadows,
simulation distance, client thread priority.

Shown as **UNAVAILABLE** (not faked): render backend swap (OPMIZER is not Vulkan), core affinity
(OS-level), worker/meshing/chunk worker threads (no safe runtime control), JVM heap (launch-time).

## Known limitations
* GPU utilisation has no portable API; GPU-bound is inferred only.
* Benchmark results are kept in memory (last 10 runs); only slots persist (`config/opmizer/slots.json`).
* Settings changes that reload chunks/textures need the Settle Time; slot order can bias results (JIT warm-up), so repeat runs.
* UI is plain and static; no animations beyond the action-bar progress text.
* No automated tests yet.
