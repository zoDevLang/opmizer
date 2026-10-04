package dev.opmizer.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.opmizer.config.Setting.Type;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

/**
 * Every OPMIZER setting. GAME settings drive real vanilla options through the
 * OptionInstance API. Anything that cannot be done safely is UNAVAILABLE and says why.
 *
 * UNVERIFIED against 26.1: Options accessor names (graphicsMode, ambientOcclusion,
 * cloudStatus ...) and the enum packages imported above. Fix here if the compiler complains.
 */
public final class SettingRegistry {
	public static final List<Setting> ALL;
	private static final Map<String, Setting> BY_ID = new HashMap<>();

	private static Options o() {
		return Minecraft.getInstance().options;
	}

	private static String[] names(Enum<?>[] values) {
		String[] s = new String[values.length];
		for (int i = 0; i < values.length; i++) {
			String n = values[i].name();
			s[i] = n.charAt(0) + n.substring(1).toLowerCase(java.util.Locale.ROOT);
		}
		return s;
	}

	public static Setting byId(String id) {
		return BY_ID.get(id);
	}

	public static List<Setting> in(Category c) {
		List<Setting> out = new ArrayList<>();
		for (Setting s : ALL) if (s.category == c) out.add(s);
		return out;
	}

	static {
		List<Setting> l = new ArrayList<>();
		Setting.Type I = Type.INT, B = Type.BOOL, E = Type.ENUM;

		// ---------------- RENDERING ----------------
		l.add(Setting.game(Category.RENDERING, "render_distance", "Render Distance", I, 2, 32, 1, null,
				v -> v + " chunks", Risk.MEDIUM,
				"How many chunks are drawn in each direction around the camera.",
				"+ Fewer chunks to mesh and draw; usually a large lever for both CPU and GPU",
				"- Less visible terrain. Changing it reloads chunks, so allow settle time",
				() -> o().renderDistance().get(), v -> o().renderDistance().set(v)));
		l.add(Setting.game(Category.RENDERING, "max_fps", "Max Framerate", I, 10, 260, 10, null,
				v -> v >= 260 ? "Unlimited" : v + " fps", Risk.LOW,
				"Frame limiter. Benchmarks are capped by this value, so use Unlimited when testing.",
				"+ A lower cap reduces heat and power draw",
				"- A cap below your real capability hides performance differences in benchmarks",
				() -> o().framerateLimit().get(), v -> o().framerateLimit().set(v)));
		l.add(Setting.game(Category.RENDERING, "vsync", "VSync", B, 0, 1, 1, null, null, Risk.LOW,
				"Locks presentation to the monitor refresh rate.",
				"+ Removes tearing", "- Caps FPS at refresh rate; can add input latency; distorts benchmarks",
				() -> o().enableVsync().get() ? 1 : 0, v -> o().enableVsync().set(v != 0)));
		l.add(Setting.unavailable(Category.RENDERING, "graphics", "Graphics Mode", Risk.MEDIUM,
				"Vanilla graphics quality tier.", "", "",
				"GraphicsStatus no longer exists in 26.1; the replacement option is not wired up yet."));
		l.add(Setting.game(Category.RENDERING, "clouds", "Clouds", E, 0, 0, 1, names(CloudStatus.values()), null,
				Risk.LOW, "Cloud rendering mode.",
				"+ Off/Fast is cheaper on the GPU", "- Visual change only",
				() -> o().cloudStatus().get().ordinal(), v -> o().cloudStatus().set(CloudStatus.values()[v])));
		l.add(Setting.game(Category.RENDERING, "smooth_lighting", "Smooth Lighting", B, 0, 1, 1, null, null,
				Risk.LOW, "Ambient occlusion / smooth lighting in chunk meshes.",
				"+ OFF makes chunk meshing slightly cheaper", "- Flatter looking blocks",
				() -> o().ambientOcclusion().get() ? 1 : 0, v -> o().ambientOcclusion().set(v != 0)));
		l.add(Setting.game(Category.RENDERING, "particles", "Particles", E, 0, 0, 1, names(ParticleStatus.values()),
				null, Risk.LOW, "How many particles are spawned and drawn.",
				"+ Fewer particles help in heavy scenes (explosions, farms)", "- Less visual feedback",
				() -> o().particles().get().ordinal(), v -> o().particles().set(ParticleStatus.values()[v])));
		l.add(Setting.game(Category.RENDERING, "mipmaps", "Mipmap Levels", I, 0, 4, 1, null, null, Risk.MEDIUM,
				"Texture mipmap chain length.",
				"+ Lower values reduce texture memory", "- More shimmering at distance; reloads textures",
				() -> o().mipmapLevels().get(), v -> o().mipmapLevels().set(v)));
		l.add(Setting.unavailable(Category.RENDERING, "render_backend", "Render Backend", Risk.HIGH,
				"Swapping the renderer or graphics API.", "", "",
				"OPMIZER does not replace Minecraft's renderer and does not claim to be Vulkan."));

		// ---------------- CPU ----------------
		l.add(Setting.game(Category.CPU, "main_thread_priority", "Client Thread Priority", I, 1, 10, 1, null, null,
				Risk.MEDIUM, "JVM scheduling priority hint for the client (render + tick) thread.",
				"+ May help the client thread win CPU time on a busy machine",
				"- Often ignored by the OS without privileges; can starve other apps. Measure it",
				() -> dev.opmizer.core.OpmizerClient.CLIENT_THREAD.getPriority(),
				v -> dev.opmizer.core.OpmizerClient.CLIENT_THREAD.setPriority(v)));
		l.add(Setting.unavailable(Category.CPU, "core_affinity", "Core Affinity", Risk.HIGH,
				"Pinning the game to specific CPU cores.", "", "",
				"Requires OS-level changes. OPMIZER never modifies OS settings."));

		// ---------------- THREADS ----------------
		l.add(Setting.unavailable(Category.THREADS, "worker_threads", "Worker Threads", Risk.HIGH,
				"Size of the shared background executor.", "+ More parallel background work",
				"- More contention with the render thread", "Vanilla does not expose a safe runtime setter."));
		l.add(Setting.unavailable(Category.THREADS, "mesh_threads", "Chunk Meshing Threads", Risk.MEDIUM,
				"Threads used to build chunk meshes.", "+ Faster chunk mesh builds",
				"- Contention with the main thread", "No stable public hook in vanilla for changing this at runtime."));

		// ---------------- MEMORY ----------------
		l.add(Setting.internal(Category.MEMORY, "gc_before_test", "GC Before Each Test", B, 0, 1, 1, 1, Risk.LOW,
				"Requests a garbage collection at the start of each test's settle phase.",
				"+ Reduces leftover garbage skewing the first measurement",
				"- One short stall before measuring (outside the measured window)"));
		l.add(Setting.unavailable(Category.MEMORY, "heap_size", "JVM Heap Size", Risk.HIGH,
				"Maximum heap (-Xmx).", "+ Less GC pressure when the heap is too small",
				"- Too large can hurt GC pause times", "Set at launch by your launcher's JVM arguments."));

		// ---------------- CHUNKS ----------------
		l.add(Setting.game(Category.CHUNKS, "biome_blend", "Biome Blend Radius", I, 0, 7, 1, null, v -> v + " blocks",
				Risk.LOW, "Smoothing radius for biome colours, applied while meshing chunks.",
				"+ Lower values make each chunk mesh cheaper to build",
				"- Harder colour edges between biomes; triggers chunk rebuild",
				() -> o().biomeBlendRadius().get(), v -> o().biomeBlendRadius().set(v)));
		l.add(Setting.unavailable(Category.CHUNKS, "chunk_workers", "Chunk Workers", Risk.MEDIUM,
				"Worker processing allocated to chunk tasks.",
				"+ Could improve chunk processing", "- Would add CPU contention and could reduce FPS",
				"Vanilla has no safe runtime control for this. Marked unavailable rather than faked."));

		// ---------------- ENTITIES ----------------
		l.add(Setting.game(Category.ENTITIES, "entity_distance", "Entity Distance", I, 50, 500, 25, null,
				v -> v + "%", Risk.MEDIUM, "Scales the distance at which entities are rendered (100% = vanilla).",
				"+ Lower values skip rendering distant entities", "- Mobs pop in/out sooner",
				() -> (int) Math.round(o().entityDistanceScaling().get() * 100.0),
				v -> o().entityDistanceScaling().set(v / 100.0)));
		l.add(Setting.game(Category.ENTITIES, "entity_shadows", "Entity Shadows", B, 0, 1, 1, null, null, Risk.LOW,
				"Blob shadows under entities.", "+ OFF removes some per-entity draw work",
				"- Entities look less grounded",
				() -> o().entityShadows().get() ? 1 : 0, v -> o().entityShadows().set(v != 0)));

		// ---------------- WORLD ----------------
		l.add(Setting.game(Category.WORLD, "simulation_distance", "Simulation Distance", I, 5, 32, 1, null,
				v -> v + " chunks", Risk.MEDIUM, "Radius in which the world is ticked (entities, crops, redstone).",
				"+ Lower values cut server tick work (integrated server)",
				"- Distant farms and mobs stop updating",
				() -> o().simulationDistance().get(), v -> o().simulationDistance().set(v)));

		// ---------------- SCHEDULING ----------------
		l.add(Setting.internal(Category.SCHEDULING, "settle_seconds", "Settle Time", I, 1, 30, 1, 5, Risk.LOW,
				"Seconds to wait after applying a slot before measuring. Chunk reloads and JIT warm-up need time.",
				"+ Longer settle = steadier measurements", "- Longer benchmarks"));
		l.add(Setting.internal(Category.SCHEDULING, "measure_seconds", "Measure Time", I, 5, 60, 5, 15, Risk.LOW,
				"Seconds of frames sampled per slot.",
				"+ More samples = more trustworthy 1% lows", "- Longer benchmarks"));

		// ---------------- ADVANCED ----------------
		l.add(Setting.internal(Category.ADVANCED, "detailed_profiling", "Detailed Profiling (tests only)", B, 0, 1,
				1, 1, Risk.LOW, "Collect process CPU, main-thread CPU and GC time during tests. Never runs outside a test.",
				"+ Enables bottleneck classification", "- Tiny overhead during the test window"));
		l.add(Setting.internal(Category.ADVANCED, "log_results", "Log Results To latest.log", B, 0, 1, 1, 0,
				Risk.LOW, "Writes a one-line summary of each finished test to the game log.",
				"+ Keeps a record", "- Adds log lines"));

		ALL = List.copyOf(l);
		for (Setting s : ALL) BY_ID.put(s.id, s);
	}

	private SettingRegistry() {}
}
