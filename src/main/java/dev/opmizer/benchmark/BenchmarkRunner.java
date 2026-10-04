package dev.opmizer.benchmark;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.opmizer.compat.Compat;
import dev.opmizer.config.ConfigManager;
import dev.opmizer.config.Slot;
import dev.opmizer.core.OpmizerClient;
import dev.opmizer.profiler.Bottleneck;
import dev.opmizer.profiler.Sampler;
import dev.opmizer.profiler.TestResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Drives real measurements. States: IDLE -> SETTLE (wait, let chunks/JIT settle)
 * -> MEASURE (sample real frames) -> next slot or finish. Nothing is simulated.
 * The per-frame entry point returns immediately when IDLE.
 */
public final class BenchmarkRunner {
	public enum State { IDLE, SETTLE, MEASURE }

	public enum Metric {
		AVG_FPS("Average FPS"), LOW_1("1% Low FPS"), MIN_FPS("Minimum FPS");

		public final String label;

		Metric(String label) {
			this.label = label;
		}

		public double of(TestResult r) {
			switch (this) {
				case LOW_1:
					return r.low1Fps;
				case MIN_FPS:
					return r.minFps;
				default:
					return r.avgFps;
			}
		}
	}

	/** One finished benchmark: results are in slot order. */
	public record Run(long time, Metric metric, int reference, List<TestResult> results) {}

	private static final int MAX_HISTORY = 10;

	private final ConfigManager cfg;
	private final Sampler sampler = new Sampler();
	private final ArrayDeque<Run> history = new ArrayDeque<>(); // newest first
	private final List<TestResult> current = new ArrayList<>();

	public Metric metric = Metric.AVG_FPS;
	public int reference = 0;

	private State state = State.IDLE;
	private boolean suite;
	private int[] queue = new int[0];
	private int qi;
	private long phaseStart, settleNs, measureNs, lastHud;
	private boolean detailed, logResults;
	private Slot snapshot;
	private String configNote;
	private TestResult previous;
	private TestResult lastSingle;

	public BenchmarkRunner(ConfigManager cfg) {
		this.cfg = cfg;
	}

	public State state() { return state; }
	public boolean busy() { return state != State.IDLE; }
	public ArrayDeque<Run> history() { return history; }
	public TestResult lastSingle() { return lastSingle; }

	// ------------------------------------------------------------------ start
	/** Tests the game AS IT IS NOW and labels it with the slot. Never changes any setting. */
	public boolean startSingle(int slot) {
		if (!canStart()) return false;
		suite = false;
		snapshot = null;
		queue = new int[] { slot };
		qi = 0;
		current.clear();
		loadDurations(slot);
		configNote = cfg.matchesGame(cfg.working(slot))
				? null
				: "game settings differ from SLOT " + (slot + 1) + " (changed outside OPMIZER or unapplied edits)";
		beginSettle();
		Compat.chat("OPMIZER: test started on SLOT " + (slot + 1) + ". Keep the scene steady.");
		return true;
	}

	/** Runs all five slots in order, then restores the settings you had before. */
	public boolean startSuite() {
		if (!canStart()) return false;
		suite = true;
		snapshot = cfg.snapshotGame();
		queue = new int[ConfigManager.SLOT_COUNT];
		for (int i = 0; i < queue.length; i++) queue[i] = i;
		qi = 0;
		current.clear();
		configNote = null;
		loadDurations(reference); // same durations for every slot keeps the comparison fair
		cfg.apply(cfg.working(queue[0]));
		beginSettle();
		Compat.chat("OPMIZER: benchmark started (5 slots, metric: " + metric.label + "). Your settings are restored afterwards.");
		return true;
	}

	private boolean canStart() {
		cfg.ensureInit();
		Minecraft mc = Minecraft.getInstance();
		if (state != State.IDLE) {
			Compat.chat("OPMIZER: a test is already running.");
			return false;
		}
		if (mc.level == null || mc.player == null) {
			Compat.chat("OPMIZER: join a world first. Tests measure the running game.");
			return false;
		}
		return true;
	}

	private void loadDurations(int slot) {
		settleNs = cfg.value(slot, "settle_seconds") * 1_000_000_000L;
		measureNs = cfg.value(slot, "measure_seconds") * 1_000_000_000L;
		detailed = cfg.value(slot, "detailed_profiling") != 0;
		logResults = cfg.value(slot, "log_results") != 0;
	}

	private void beginSettle() {
		if (cfg.value(queue[qi], "gc_before_test") != 0) System.gc();
		state = State.SETTLE;
		phaseStart = System.nanoTime();
		lastHud = 0;
	}

	// ------------------------------------------------------------- per frame
	public void onFrame(long now) {
		if (state == State.IDLE) return;
		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null || mc.player == null) {
			abort("left the world");
			return;
		}

		if (state == State.SETTLE) {
			if (mc.screen != null) { // wait for menus/chat to close
				phaseStart = now;
				return;
			}
			hud(now, "settling", settleNs - (now - phaseStart));
			if (now - phaseStart >= settleNs) {
				sampler.start(detailed, now);
				state = State.MEASURE;
				phaseStart = now;
			}
			return;
		}

		// MEASURE
		if (mc.screen != null || mc.isPaused()) {
			abort("a menu opened or the game paused (results would be invalid)");
			return;
		}
		sampler.frame(now);
		hud(now, "measuring", measureNs - (now - phaseStart));
		if (now - phaseStart >= measureNs) finishSlot(now);
	}

	public void onTickStart(long now) {
		if (state == State.MEASURE) sampler.tickStart(now);
	}

	public void onTickEnd(long now) {
		if (state == State.MEASURE) sampler.tickEnd(now);
	}

	private void hud(long now, String phase, long remainingNs) {
		if (now - lastHud < 500_000_000L) return;
		lastHud = now;
		Compat.actionBar(String.format(Locale.ROOT, "OPMIZER %s SLOT %d  %.0fs left%s", phase, queue[qi] + 1,
				Math.max(0, remainingNs) / 1e9, suite ? "  (" + (qi + 1) + "/" + queue.length + ")" : ""));
	}

	// ----------------------------------------------------------------- finish
	private void finishSlot(long now) {
		Options o = Minecraft.getInstance().options;
		TestResult r = sampler.stop(now, queue[qi]);
		r.timestamp = System.currentTimeMillis();
		r.configNote = configNote;
		try {
			r.fpsCap = o.framerateLimit().get();
			r.vsync = o.enableVsync().get();
		} catch (Throwable ignored) {
		}
		r.bottleneck = Bottleneck.classify(r);
		current.add(r);
		if (logResults) OpmizerClient.LOG.info(Report.oneLine(r));

		qi++;
		if (qi < queue.length) {
			cfg.apply(cfg.working(queue[qi]));
			beginSettle();
		} else {
			complete();
		}
	}

	private void complete() {
		state = State.IDLE;
		if (suite) {
			cfg.apply(snapshot);
			Run run = new Run(System.currentTimeMillis(), metric, reference, new ArrayList<>(current));
			history.addFirst(run);
			while (history.size() > MAX_HISTORY) history.removeLast();
			for (String line : Report.suite(run)) Compat.chat(line);
			previous = current.get(current.size() - 1);
		} else {
			TestResult r = current.get(0);
			for (String line : Report.single(r, previous)) Compat.chat(line);
			lastSingle = r;
			previous = r;
		}
		current.clear();
		Compat.actionBar("OPMIZER: test complete");
	}

	private void abort(String why) {
		state = State.IDLE;
		if (sampler.running()) sampler.stop(System.nanoTime(), 0);
		if (suite && snapshot != null) cfg.apply(snapshot);
		current.clear();
		Compat.chat("OPMIZER: test aborted - " + why + ".");
	}
}
