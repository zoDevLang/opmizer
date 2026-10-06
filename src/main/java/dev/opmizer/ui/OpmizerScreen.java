package dev.opmizer.ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.opmizer.benchmark.BenchmarkRunner;
import dev.opmizer.benchmark.Report;
import dev.opmizer.benchmark.Verdict;
import dev.opmizer.config.Category;
import dev.opmizer.config.ConfigManager;
import dev.opmizer.config.Risk;
import dev.opmizer.config.Setting;
import dev.opmizer.config.SettingRegistry;
import dev.opmizer.core.OpmizerClient;
import dev.opmizer.profiler.TestResult;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * OPMIZER control panel (Q). Dark, card based, text-first.
 * Everything custom is drawn with fill() and text() only; widgets are vanilla buttons.
 * Animations (fade-in, sliding tab marker, value bars, toggle knobs, result bars) are
 * time based and only run while this screen is open.
 */
public final class OpmizerScreen extends Screen {
	private static final int ACCENT = 0xFF3FB6E0, TEXT = 0xFFE3E8EE, DIM = 0xFF7D8896, FAINT = 0xFF4B5662;
	private static final int GOOD = 0xFF5BD68A, BAD = 0xFFE5534B, WARN = 0xFFE0B341, INK = 0xFF0B0F13;
	private static final int PANEL = 0xFF0E1217, SIDE = 0xFF0B0F13, CARD = 0xFF151A21, CARD_HOT = 0xFF1C242E;
	private static final int BORDER = 0xFF1F2833, TRACK = 0xFF262F3A, CHIP = 0xFF1A2029;

	private static final int LEFT = 10, TABW = 88, CX = 108, ROW_Y = 50, ROW_H = 20, TAB_H = 14, TAB_STEP = 15;
	private static final int SETTING_TABS = 9; // RENDERING..ADVANCED

	private record Line(String text, int color) {}

	private final ConfigManager cfg = OpmizerClient.CONFIG;
	private final BenchmarkRunner bench = OpmizerClient.BENCH;

	private Category tab = Category.RENDERING;
	private int selected;
	private int scroll;
	private String status = "";
	private long statusNs;

	private final List<Setting> rows = new ArrayList<>();
	private Setting pinned;
	private Setting wrappedFor;
	private final List<Line> wrapped = new ArrayList<>();

	private BenchmarkRunner.Run sortedFor;
	private List<TestResult> sorted = List.of();

	// animation state
	private final long openNs = System.nanoTime();
	private long lastNs = openNs;
	private long tabEnterNs = openNs;
	private float dt;
	private float indicatorY;
	private final Map<String, float[]> anim = new HashMap<>();

	public OpmizerScreen() {
		super(Component.literal("OPMIZER"));
		cfg.ensureInit();
		selected = cfg.activeSlot();
		indicatorY = tabY(tab);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		rebuild();
	}

	// ---------------------------------------------------------------- layout helpers
	private static int tabY(Category c) {
		int i = c.ordinal();
		if (i < SETTING_TABS) return 45 + i * TAB_STEP;
		return 45 + SETTING_TABS * TAB_STEP + 15 + (i - SETTING_TABS) * TAB_STEP;
	}

	private int visibleRows() {
		return Math.max(3, (height - 96 - ROW_Y) / ROW_H);
	}

	private void setStatus(String s) {
		status = s;
		statusNs = System.nanoTime();
	}

	private static int riskColor(Risk r) {
		return r == Risk.LOW ? GOOD : r == Risk.MEDIUM ? WARN : BAD;
	}

	private static int argb(int alpha, int rgb) {
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}

	private float animate(String key, float target) {
		float[] a = anim.computeIfAbsent(key, k -> new float[] { target });
		a[0] += (target - a[0]) * Math.min(1f, dt * 12f);
		return a[0];
	}

	/** Draws a small label chip, returns its width. */
	private int pill(GuiGraphicsExtractor g, int x, int y, String s, int fg, int bg) {
		int w = font.width(s) + 8;
		Gfx.fill(g, x, y, x + w, y + 11, bg);
		Gfx.text(g, font, s, x + 4, y + 2, fg);
		return w;
	}

	// ---------------------------------------------------------------------- widgets
	private void rebuild() {
		clearWidgets();
		rows.clear();

		for (Category c : Category.values()) {
			final Category cc = c;
			Button b = Button.builder(Component.literal(c.label), x -> {
				tab = cc;
				scroll = 0;
				tabEnterNs = System.nanoTime();
				rebuild();
			}).bounds(LEFT, tabY(c), TABW, TAB_H).build();
			b.active = c != tab;
			addRenderableWidget(b);
		}

		for (int i = 0; i < ConfigManager.SLOT_COUNT; i++) {
			final int idx = i;
			Button b = Button.builder(Component.literal("S" + (i + 1)), x -> {
				selected = idx;
				rebuild();
			}).bounds(CX + i * 36, 6, 32, 16).build();
			b.active = i != selected;
			addRenderableWidget(b);
		}

		int ay = height - 26;
		boolean inWorld = minecraft != null && minecraft.level != null && !bench.busy();
		addRenderableWidget(Button.builder(Component.literal("Save"), x -> {
			cfg.save(selected);
			setStatus("Saved SLOT " + (selected + 1));
		}).bounds(CX, ay, 54, 16).build());
		addRenderableWidget(Button.builder(Component.literal("Load"), x -> {
			int f = cfg.load(selected);
			setStatus("Applied SLOT " + (selected + 1) + (f > 0 ? " (" + f + " failed, see log)" : ""));
		}).bounds(CX + 58, ay, 54, 16).build());
		addRenderableWidget(Button.builder(Component.literal("Revert"), x -> {
			cfg.revert(selected);
			setStatus("Reverted edits on SLOT " + (selected + 1));
		}).bounds(CX + 116, ay, 54, 16).build());
		Button test = Button.builder(Component.literal("Test (apply)"), x -> {
			cfg.applyWorking(selected);
			onClose();
			bench.startSingle(selected);
		}).bounds(CX + 174, ay, 78, 16).build();
		test.active = inWorld;
		addRenderableWidget(test);
		Button run = Button.builder(Component.literal("Benchmark"), x -> {
			onClose();
			bench.startSuite();
		}).bounds(CX + 256, ay, 70, 16).build();
		run.active = inWorld;
		addRenderableWidget(run);

		if (tab.hasSettings) {
			List<Setting> all = SettingRegistry.in(tab);
			int vis = visibleRows();
			scroll = Math.max(0, Math.min(scroll, Math.max(0, all.size() - vis)));
			for (int i = 0; i < vis && scroll + i < all.size(); i++) {
				final Setting s = all.get(scroll + i);
				rows.add(s);
				if (!s.available()) continue;
				int ry = ROW_Y + i * ROW_H + 1;
				addRenderableWidget(Button.builder(Component.literal("-"), x -> change(s, -1))
						.bounds(width - 142, ry, 16, 16).build());
				addRenderableWidget(Button.builder(Component.literal("+"), x -> change(s, 1))
						.bounds(width - 34, ry, 16, 16).build());
			}
		} else if (tab == Category.BENCHMARK) {
			addRenderableWidget(Button.builder(Component.literal("Metric: " + bench.metric.label), x -> {
				BenchmarkRunner.Metric[] m = BenchmarkRunner.Metric.values();
				bench.metric = m[(bench.metric.ordinal() + 1) % m.length];
				rebuild();
			}).bounds(CX, ROW_Y, 200, 16).build());
			addRenderableWidget(Button.builder(Component.literal("Reference slot: SLOT " + (bench.reference + 1)), x -> {
				bench.reference = (bench.reference + 1) % ConfigManager.SLOT_COUNT;
				rebuild();
			}).bounds(CX, ROW_Y + 22, 200, 16).build());
		} else if (tab == Category.PROFILES) {
			for (int i = 0; i < ConfigManager.SLOT_COUNT; i++) {
				final int idx = i;
				Button b = Button.builder(Component.literal("Select"), x -> {
					selected = idx;
					rebuild();
				}).bounds(width - 62, ROW_Y + 14 + i * 26 + 4, 48, 14).build();
				b.active = i != selected;
				addRenderableWidget(b);
			}
		}
	}

	private void change(Setting s, int dir) {
		var slot = cfg.working(selected);
		slot.set(s, s.step(slot.get(s), dir));
		pinned = s;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (tab.hasSettings) {
			scroll -= (int) Math.signum(sy);
			rebuild();
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------------- drawing
	/** Intentionally empty: stops vanilla from blurring/dimming. We paint our own backdrop. */
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - lastNs) / 1e9f);
		lastNs = now;
		indicatorY += (tabY(tab) - indicatorY) * Math.min(1f, dt * 14f);

		drawChrome(g, now);
		switch (tab) {
			case BENCHMARK -> drawBenchmark(g);
			case RESULTS -> drawResults(g, now);
			case PROFILES -> drawProfiles(g);
			default -> drawSettings(g, mx, my);
		}

		super.extractRenderState(g, mx, my, pt); // vanilla buttons on top

		float t = (now - openNs) / 200_000_000f; // fade in from black
		if (t < 1f) Gfx.fill(g, 0, 0, width, height, (int) (255 * (1f - t)) << 24);
	}

	private void drawChrome(GuiGraphicsExtractor g, long now) {
		Gfx.fill(g, 0, 0, width, height, 0xD0070A0D);
		Gfx.fill(g, 5, 3, width - 5, height - 3, BORDER);
		Gfx.fill(g, 6, 4, width - 6, height - 4, PANEL);
		Gfx.fill(g, 6, 4, width - 6, 6, ACCENT);

		// sidebar
		Gfx.fill(g, 6, 6, CX - 4, height - 4, SIDE);
		Gfx.fill(g, CX - 4, 6, CX - 3, height - 4, BORDER);
		Gfx.text(g, font, "OPMIZER", LEFT, 11, ACCENT);
		Gfx.text(g, font, "v0.1  measure first", LEFT, 21, DIM);
		Gfx.text(g, font, "SETTINGS", LEFT, 35, FAINT);
		Gfx.text(g, font, "TOOLS", LEFT, tabY(Category.BENCHMARK) - 12, FAINT);
		int iy = Math.round(indicatorY);
		Gfx.fill(g, 7, iy, 9, iy + TAB_H, ACCENT);
		Gfx.text(g, font, "Q panel   T test", LEFT, height - 14, FAINT);

		// slot chip markers: underline = selected, green = active slot, amber = unsaved edits
		for (int i = 0; i < ConfigManager.SLOT_COUNT; i++) {
			int x = CX + i * 36;
			Gfx.fill(g, x, 23, x + 32, i == selected ? 25 : 24, i == selected ? ACCENT : BORDER);
			if (i == cfg.activeSlot()) Gfx.fill(g, x, 26, x + 14, 28, GOOD);
			if (cfg.dirty(i)) Gfx.fill(g, x + 18, 26, x + 32, 28, WARN);
		}

		// sub header pills
		int px = CX;
		px += pill(g, px, 33, "SLOT " + (selected + 1), INK, ACCENT) + 4;
		if (selected == cfg.activeSlot()) px += pill(g, px, 33, "ACTIVE", GOOD, 0xFF16301F) + 4;
		if (cfg.dirty(selected)) px += pill(g, px, 33, "UNSAVED", WARN, 0xFF35290B) + 4;
		pill(g, px, 33, tab.label.toUpperCase(Locale.ROOT), DIM, CHIP);

		if (bench.busy()) {
			String s = "TEST RUNNING";
			int w = font.width(s) + 8;
			pill(g, width - 10 - w, 33, s, WARN, 0xFF35290B);
		}

		// status toast fades out
		if (!status.isEmpty()) {
			float age = (now - statusNs) / 1e9f;
			if (age > 3.2f) {
				status = "";
			} else {
				int a = age < 2.5f ? 255 : (int) (255 * (1f - (age - 2.5f) / 0.7f));
				if (a > 12) Gfx.textRight(g, font, status, width - 12, 11, argb(a, WARN));
			}
		}

		// footer
		Gfx.fill(g, CX - 3, height - 31, width - 6, height - 30, BORDER);
		Gfx.fill(g, CX - 3, height - 30, width - 6, height - 6, SIDE);
	}

	// ------------------------------------------------------------------- settings
	private void drawSettings(GuiGraphicsExtractor g, int mx, int my) {
		Setting hovered = null;
		var slot = cfg.working(selected);
		var saved = cfg.saved(selected);

		for (int i = 0; i < rows.size(); i++) {
			Setting s = rows.get(i);
			int y = ROW_Y + i * ROW_H;
			boolean hot = my >= y && my < y + ROW_H - 2 && mx >= CX && mx < width - 8;
			if (hot) hovered = s;

			Gfx.fill(g, CX, y, width - 8, y + ROW_H - 2, hot ? CARD_HOT : CARD);
			Gfx.fill(g, CX, y, CX + 2, y + ROW_H - 2, s.available() ? riskColor(s.risk) : FAINT);

			if (!s.available()) {
				Gfx.text(g, font, s.label, CX + 8, y + 5, DIM);
				String tag = "UNAVAILABLE";
				int w = font.width(tag) + 8;
				pill(g, width - 14 - w, y + 3, tag, DIM, CHIP);
				continue;
			}

			int v = slot.get(s);
			boolean edited = v != saved.get(s);
			Gfx.text(g, font, s.label + (edited ? " *" : ""), CX + 8, y + 5, edited ? ACCENT : TEXT);
			String tag = s.risk == Risk.LOW ? "low" : s.risk == Risk.MEDIUM ? "med" : "high";
			Gfx.textRight(g, font, tag, width - 150, y + 5, riskColor(s.risk));

			int cx = width - 80; // center of the value area between - and +
			if (s.type == Setting.Type.BOOL) {
				float k = animate(s.id + "#k", v != 0 ? 1f : 0f);
				int x0 = cx - 12, y0 = y + 4;
				Gfx.fill(g, x0, y0, x0 + 24, y0 + 10, v != 0 ? 0xFF2E7D55 : 0xFF2B323C);
				int kx = x0 + 2 + Math.round(k * 12f);
				Gfx.fill(g, kx, y0 + 2, kx + 8, y0 + 8, TEXT);
			} else if (s.type == Setting.Type.INT && s.max > s.min) {
				float f = animate(s.id + "#b", (v - s.min) / (float) (s.max - s.min));
				Gfx.textCentered(g, font, s.display(v), cx, y + 3, TEXT);
				Gfx.fill(g, cx - 44, y + 14, cx + 44, y + 16, TRACK);
				Gfx.fill(g, cx - 44, y + 14, cx - 44 + Math.round(88f * f), y + 16, ACCENT);
			} else {
				Gfx.textCentered(g, font, s.display(v), cx, y + 5, TEXT);
			}
		}

		if (rows.isEmpty()) Gfx.text(g, font, "No settings in this category.", CX, ROW_Y, DIM);
		int total = SettingRegistry.in(tab).size();
		if (total > rows.size())
			Gfx.textRight(g, font, "scroll " + (scroll + 1) + "-" + (scroll + rows.size()) + " of " + total, width - 12, 36, FAINT);

		Setting focus = hovered != null ? hovered : pinned;
		if (focus != null && focus.category == tab) drawInfo(g, focus);
		else Gfx.text(g, font, "Hover a setting to see what it does and what it costs.", CX + 2, height - 66, FAINT);
	}

	private void drawInfo(GuiGraphicsExtractor g, Setting s) {
		if (wrappedFor != s) {
			wrappedFor = s;
			wrapped.clear();
			int w = width - CX - 28;
			wrap(s.description, w, TEXT);
			if (!s.available()) {
				wrap("Unavailable: " + s.unavailableReason, w, WARN);
			} else {
				wrap(s.benefits, w, GOOD);
				wrap(s.costs, w, BAD);
			}
		}
		int top = height - 92, bottom = height - 34;
		Gfx.fill(g, CX, top, width - 8, bottom, 0xFF131920);
		Gfx.fill(g, CX, top, CX + 2, bottom, riskColor(s.risk));
		Gfx.text(g, font, s.label, CX + 8, top + 4, TEXT);
		String r = "RISK: " + s.risk;
		int rw = font.width(r) + 8;
		pill(g, width - 12 - rw, top + 3, r, riskColor(s.risk), CHIP);
		int y = top + 17;
		for (Line ln : wrapped) {
			if (y > bottom - 10) break;
			Gfx.text(g, font, ln.text(), CX + 8, y, ln.color());
			y += 10;
		}
	}

	private void wrap(String text, int maxW, int color) {
		if (text == null || text.isEmpty()) return;
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			if (line.length() > 0 && font.width(line + " " + word) > maxW) {
				wrapped.add(new Line(line.toString(), color));
				line.setLength(0);
			}
			if (line.length() > 0) line.append(' ');
			line.append(word);
		}
		if (line.length() > 0) wrapped.add(new Line(line.toString(), color));
	}

	// ------------------------------------------------------------------ benchmark
	private void drawBenchmark(GuiGraphicsExtractor g) {
		int est = ConfigManager.SLOT_COUNT
				* (cfg.value(bench.reference, "settle_seconds") + cfg.value(bench.reference, "measure_seconds"));
		String[] lines = {
				"Runs all 5 slots one after another in the current world, then restores your settings.",
				"Each slot: settle, then sample real frames. Durations come from the reference slot.",
				"Ranked greatest first by the chosen metric.",
				"A best slot is only named if it beats the reference by more than " + Verdict.THRESHOLD_PCT + "%.",
				"Otherwise the answer is UNDETERMINED. Stand still in a representative scene.",
		};
		int y = ROW_Y + 46;
		int h = lines.length * 11 + 22;
		Gfx.fill(g, CX, y, width - 8, y + h, CARD);
		Gfx.fill(g, CX, y, CX + 2, y + h, ACCENT);
		pill(g, CX + 8, y + 4, "PLAN", INK, ACCENT);
		Gfx.text(g, font, "about " + est + " s total", CX + 50, y + 6, DIM);
		int ty = y + 20;
		for (String s : lines) {
			Gfx.text(g, font, s, CX + 8, ty, DIM);
			ty += 11;
		}

		int wy = y + h + 6;
		boolean vsync = false;
		int cap = 260;
		try {
			vsync = minecraft.options.enableVsync().get();
			cap = minecraft.options.framerateLimit().get();
		} catch (Throwable ignored) {
		}
		if (vsync || cap < 260) {
			Gfx.fill(g, CX, wy, width - 8, wy + 24, 0xFF2A230E);
			Gfx.fill(g, CX, wy, CX + 2, wy + 24, WARN);
			Gfx.text(g, font, vsync ? "VSync is ON: FPS may be capped at your refresh rate." : "Frame limiter is " + cap + " fps.",
					CX + 8, wy + 4, WARN);
			Gfx.text(g, font, "Results can hide differences. Use Unlimited and VSync off for honest numbers.", CX + 8, wy + 14, DIM);
		}
	}

	// -------------------------------------------------------------------- results
	private void drawResults(GuiGraphicsExtractor g, long now) {
		var hist = bench.history();
		TestResult ls = bench.lastSingle();
		int y = ROW_Y;

		if (hist.isEmpty() && ls == null) {
			Gfx.fill(g, CX, y, width - 8, y + 34, CARD);
			Gfx.fill(g, CX, y, CX + 2, y + 34, FAINT);
			Gfx.text(g, font, "No measurements yet.", CX + 8, y + 6, TEXT);
			Gfx.text(g, font, "Press T, run /opm test, or run a Benchmark from the footer.", CX + 8, y + 18, DIM);
			return;
		}

		float t = Math.min(1f, (now - tabEnterNs) / 500_000_000f);
		float ease = 1f - (1f - t) * (1f - t) * (1f - t);

		if (ls != null) {
			Gfx.fill(g, CX, y, width - 8, y + 30, CARD);
			Gfx.fill(g, CX, y, CX + 2, y + 30, ACCENT);
			int px = CX + 8;
			px += pill(g, px, y + 4, "LAST TEST  SLOT " + (ls.slot + 1), INK, ACCENT) + 6;
			if (ls.valid) {
				Gfx.text(g, font, String.format(Locale.ROOT, "%.0f FPS   1%% low %.0f   min %.0f", ls.avgFps, ls.low1Fps, ls.minFps),
						px, y + 6, TEXT);
				String tick = ls.avgTickMs >= 0 ? String.format(Locale.ROOT, "   tick %.2f ms", ls.avgTickMs) : "";
				Gfx.text(g, font, String.format(Locale.ROOT, "frame %.2f ms%s   %s", ls.avgFrameMs, tick, ls.bottleneck.label),
						CX + 8, y + 18, DIM);
			} else {
				Gfx.text(g, font, "INVALID: " + ls.note, px, y + 6, WARN);
			}
			y += 36;
		}
		if (hist.isEmpty()) return;

		BenchmarkRunner.Run run = hist.peekFirst();
		if (sortedFor != run) {
			sortedFor = run;
			sorted = Report.ranked(run);
		}
		Gfx.text(g, font, "LATEST BENCHMARK  " + new SimpleDateFormat("HH:mm:ss").format(new Date(run.time())), CX, y, ACCENT);
		Gfx.textRight(g, font, "by " + run.metric().label + ", greatest first", width - 12, y, DIM);
		y += 14;

		TestResult base = run.results().get(run.reference());
		double top = 0;
		for (TestResult r : sorted) if (r.valid) top = Math.max(top, run.metric().of(r));

		int rank = 1;
		for (TestResult r : sorted) {
			if (y + 26 > height - 36) break;
			boolean ref = r.slot == run.reference();
			Verdict v = ref || !r.valid ? null : Verdict.compare(base, r);
			int col = !r.valid ? FAINT : ref ? ACCENT : v == Verdict.IMPROVED ? GOOD : v == Verdict.REGRESSION ? BAD : 0xFF5A6572;

			Gfx.fill(g, CX, y, width - 8, y + 26, CARD);
			Gfx.fill(g, CX, y, CX + 2, y + 26, col);
			Gfx.text(g, font, "#" + rank++ + "   SLOT " + (r.slot + 1), CX + 8, y + 3, TEXT);
			String verdict = !r.valid ? "INVALID" : ref ? "reference"
					: v.label + String.format(Locale.ROOT, "  %+.1f%%",
							Verdict.pct(run.metric().of(base), run.metric().of(r)));
			Gfx.textRight(g, font, verdict, width - 14, y + 3, col == FAINT ? DIM : col);
			Gfx.text(g, font, String.format(Locale.ROOT, "avg %.1f   1%% low %.1f   min %.1f   %.2f ms   %s", r.avgFps,
					r.low1Fps, r.minFps, r.avgFrameMs, r.bottleneck.label), CX + 8, y + 13, DIM);

			int x0 = CX + 8, x1 = width - 16;
			Gfx.fill(g, x0, y + 22, x1, y + 24, TRACK);
			double frac = r.valid && top > 0 ? run.metric().of(r) / top : 0;
			Gfx.fill(g, x0, y + 22, x0 + (int) ((x1 - x0) * frac * ease), y + 24, col);
			y += 28;
		}
		if (hist.size() > 1 && y + 10 < height - 36)
			Gfx.text(g, font, (hist.size() - 1) + " earlier run(s) kept in memory (newest first).", CX, y + 2, FAINT);
	}

	// -------------------------------------------------------------------- profiles
	private void drawProfiles(GuiGraphicsExtractor g) {
		Gfx.text(g, font, "5 slots. Each one stores EVERY setting. Pick one with S1-S5 or Select.", CX, ROW_Y, DIM);
		var hist = bench.history();
		BenchmarkRunner.Run run = hist.isEmpty() ? null : hist.peekFirst();
		for (int i = 0; i < ConfigManager.SLOT_COUNT; i++) {
			int y = ROW_Y + 14 + i * 26;
			boolean sel = i == selected;
			Gfx.fill(g, CX, y, width - 8, y + 22, sel ? CARD_HOT : CARD);
			Gfx.fill(g, CX, y, CX + 2, y + 22, sel ? ACCENT : FAINT);
			Gfx.text(g, font, "SLOT " + (i + 1), CX + 8, y + 7, sel ? ACCENT : TEXT);
			int px = CX + 58;
			if (i == cfg.activeSlot()) px += pill(g, px, y + 5, "ACTIVE", GOOD, 0xFF16301F) + 4;
			if (cfg.dirty(i)) px += pill(g, px, y + 5, "UNSAVED", WARN, 0xFF35290B) + 4;
			if (run != null && run.results().get(i).valid) {
				TestResult r = run.results().get(i);
				Gfx.textRight(g, font, String.format(Locale.ROOT, "last: %.1f avg / %.1f low", r.avgFps, r.low1Fps),
						width - 70, y + 7, DIM);
			}
		}
		Gfx.text(g, font, "Save writes config/opmizer/slots.json. Load applies a slot to the running game.", CX,
				ROW_Y + 14 + ConfigManager.SLOT_COUNT * 26 + 4, FAINT);
	}
}
