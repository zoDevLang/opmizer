package dev.opmizer.ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * OPMIZER control panel (opened with Q). Plain dark panel, text-first.
 * All state lives in ConfigManager / BenchmarkRunner; this class only draws and forwards clicks.
 * UNVERIFIED on 26.1: Screen#render / #renderBackground / #mouseScrolled signatures and Button.builder.
 */
public final class OpmizerScreen extends Screen {
	private static final int ACCENT = 0xFF3FB6E0, TEXT = 0xFFD8DEE4, DIM = 0xFF7F8A96;
	private static final int GOOD = 0xFF5BD68A, BAD = 0xFFE5534B, WARN = 0xFFE0B341;
	private static final int LEFT = 10, TABW = 88, CX = 108, ROW_Y = 46, ROW_H = 18;

	private final ConfigManager cfg = OpmizerClient.CONFIG;
	private final BenchmarkRunner bench = OpmizerClient.BENCH;

	private Category tab = Category.RENDERING;
	private int selected;
	private int scroll;
	private String status = "";

	private final List<Setting> rows = new ArrayList<>();
	private Setting pinned;
	private Setting wrappedFor;
	private final List<String[]> wrapped = new ArrayList<>(); // {text, colorHex}

	private BenchmarkRunner.Run sortedFor;
	private List<TestResult> sorted = List.of();

	public OpmizerScreen() {
		super(Component.literal("OPMIZER"));
		cfg.ensureInit();
		selected = cfg.activeSlot();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		rebuild();
	}

	// ------------------------------------------------------------------ widgets
	private void rebuild() {
		clearWidgets();
		rows.clear();

		int y = 34;
		for (Category c : Category.values()) {
			final Category cc = c;
			Button b = Button.builder(Component.literal(c.label), x -> {
				tab = cc;
				scroll = 0;
				rebuild();
			}).bounds(LEFT, y, TABW, 16).build();
			b.active = c != tab;
			addRenderableWidget(b);
			y += 17;
		}

		for (int i = 0; i < ConfigManager.SLOT_COUNT; i++) {
			final int idx = i;
			Button b = Button.builder(Component.literal("S" + (i + 1)), x -> {
				selected = idx;
				rebuild();
			}).bounds(CX + i * 34, 8, 32, 18).build();
			b.active = i != selected;
			addRenderableWidget(b);
		}

		int ay = height - 26;
		boolean inWorld = minecraft != null && minecraft.level != null && !bench.busy();
		addRenderableWidget(Button.builder(Component.literal("Save"), x -> {
			cfg.save(selected);
			status = "Saved SLOT " + (selected + 1);
		}).bounds(CX, ay, 54, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Load"), x -> {
			int f = cfg.load(selected);
			status = "Loaded + applied SLOT " + (selected + 1) + (f > 0 ? " (" + f + " setting(s) failed, see log)" : "");
		}).bounds(CX + 58, ay, 54, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Revert"), x -> {
			cfg.revert(selected);
			status = "Reverted unsaved edits on SLOT " + (selected + 1);
		}).bounds(CX + 116, ay, 54, 18).build());
		Button test = Button.builder(Component.literal("Test (apply)"), x -> {
			cfg.applyWorking(selected);
			onClose();
			bench.startSingle(selected);
		}).bounds(CX + 174, ay, 78, 18).build();
		test.active = inWorld;
		addRenderableWidget(test);
		Button run = Button.builder(Component.literal("Benchmark"), x -> {
			onClose();
			bench.startSuite();
		}).bounds(CX + 256, ay, 70, 18).build();
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
				int ry = ROW_Y + i * ROW_H;
				addRenderableWidget(Button.builder(Component.literal("-"), x -> change(s, -1))
						.bounds(width - 138, ry, 18, 16).build());
				addRenderableWidget(Button.builder(Component.literal("+"), x -> change(s, 1))
						.bounds(width - 30, ry, 18, 16).build());
			}
		} else if (tab == Category.BENCHMARK) {
			addRenderableWidget(Button.builder(Component.literal("Metric: " + bench.metric.label), x -> {
				BenchmarkRunner.Metric[] m = BenchmarkRunner.Metric.values();
				bench.metric = m[(bench.metric.ordinal() + 1) % m.length];
				rebuild();
			}).bounds(CX, ROW_Y, 180, 16).build());
			addRenderableWidget(Button.builder(Component.literal("Reference slot: SLOT " + (bench.reference + 1)), x -> {
				bench.reference = (bench.reference + 1) % ConfigManager.SLOT_COUNT;
				rebuild();
			}).bounds(CX, ROW_Y + 22, 180, 16).build());
		}
	}

	private int visibleRows() {
		return Math.max(3, (height - 26 - 70 - ROW_Y) / ROW_H);
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

	// ------------------------------------------------------------------- drawing
	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
		Gfx.fill(g, 0, 0, width, height, 0xE60B0E12);
		Gfx.fill(g, 5, 3, width - 5, height - 3, 0xFF12161B);
		Gfx.fill(g, 5, 3, width - 5, 4, ACCENT);
		Gfx.fill(g, CX - 4, 30, CX - 3, height - 30, 0xFF222A33);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		super.render(g, mx, my, pt); // background + widgets

		Gfx.text(g, font, "OPMIZER", LEFT, 10, ACCENT);
		Gfx.text(g, font, "measure > configure > test", LEFT, 21, DIM);

		boolean dirty = cfg.dirty(selected);
		String head = "SLOT " + (selected + 1) + (selected == cfg.activeSlot() ? "  [ACTIVE]" : "")
				+ (dirty ? "  *unsaved edits" : "") + "  |  " + tab.label;
		Gfx.text(g, font, head, CX, 31, TEXT);
		if (!status.isEmpty()) Gfx.textRight(g, font, status, width - 12, 12, WARN);
		if (bench.busy()) Gfx.textRight(g, font, "TEST RUNNING", width - 12, 31, WARN);

		switch (tab) {
			case BENCHMARK -> drawBenchmark(g);
			case RESULTS -> drawResults(g);
			case PROFILES -> drawProfiles(g);
			default -> drawSettings(g, mx, my);
		}
	}

	private void drawSettings(GuiGraphics g, int mx, int my) {
		Setting hovered = null;
		var slot = cfg.working(selected);
		var saved = cfg.saved(selected);
		for (int i = 0; i < rows.size(); i++) {
			Setting s = rows.get(i);
			int y = ROW_Y + i * ROW_H;
			if (my >= y && my < y + ROW_H && mx >= CX && mx < width - 8) hovered = s;
			if (!s.available()) {
				Gfx.text(g, font, s.label, CX, y + 4, DIM);
				Gfx.textRight(g, font, "UNAVAILABLE", width - 12, y + 4, DIM);
				continue;
			}
			int v = slot.get(s);
			boolean edited = v != saved.get(s);
			Gfx.text(g, font, s.label + (edited ? " *" : ""), CX, y + 4, edited ? ACCENT : TEXT);
			Gfx.textCentered(g, font, s.display(v), width - 75, y + 4, TEXT);
		}
		if (rows.isEmpty()) Gfx.text(g, font, "No settings in this category.", CX, ROW_Y, DIM);
		int total = SettingRegistry.in(tab).size();
		if (total > rows.size()) Gfx.text(g, font, "scroll for more (" + total + " total)", CX, ROW_Y + rows.size() * ROW_H, DIM);

		Setting focus = hovered != null ? hovered : pinned;
		if (focus != null && focus.category == tab) drawInfo(g, focus);
		else Gfx.text(g, font, "Hover a setting to see what it does and what it costs.", CX, height - 78, DIM);
	}

	private void drawInfo(GuiGraphics g, Setting s) {
		if (wrappedFor != s) {
			wrappedFor = s;
			wrapped.clear();
			int w = width - CX - 14;
			wrap(s.description, w, TEXT);
			if (!s.available()) {
				wrap("Unavailable: " + s.unavailableReason, w, WARN);
			} else {
				wrap(s.benefits, w, GOOD);
				wrap(s.costs, w, BAD);
			}
		}
		int y = height - 82;
		Gfx.fill(g, CX, y - 3, width - 8, height - 30, 0xFF171C22);
		int rc = s.risk == Risk.LOW ? GOOD : s.risk == Risk.MEDIUM ? WARN : BAD;
		Gfx.text(g, font, s.label + "   Risk: " + s.risk, CX + 4, y, rc);
		y += 11;
		for (String[] ln : wrapped) {
			if (y > height - 40) break;
			Gfx.text(g, font, ln[0], CX + 4, y, (int) Long.parseLong(ln[1], 16));
			y += 10;
		}
	}

	private void wrap(String text, int maxW, int color) {
		if (text == null || text.isEmpty()) return;
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			if (line.length() > 0 && font.width(line + " " + word) > maxW) {
				wrapped.add(new String[] { line.toString(), Integer.toHexString(color) });
				line.setLength(0);
			}
			if (line.length() > 0) line.append(' ');
			line.append(word);
		}
		if (line.length() > 0) wrapped.add(new String[] { line.toString(), Integer.toHexString(color) });
	}

	private void drawBenchmark(GuiGraphics g) {
		int y = ROW_Y + 48;
		String[] lines = {
				"Runs all 5 slots one after another in the CURRENT world, then restores your settings.",
				"Each slot: settle, then sample real frames. Durations come from the reference slot.",
				String.format(Locale.ROOT, "Estimated time: %d s", ConfigManager.SLOT_COUNT
						* (cfg.value(bench.reference, "settle_seconds") + cfg.value(bench.reference, "measure_seconds"))),
				"Ranking is greatest-first by the chosen metric. 'Best' is only named when it beats the",
				"reference by more than " + Verdict.THRESHOLD_PCT + "% - otherwise the answer is UNDETERMINED.",
				"Stand still in a representative scene. Disable VSync / raise the frame cap for honest numbers.",
				"Needs a loaded world. Use the Benchmark button below.",
		};
		for (String s : lines) {
			Gfx.text(g, font, s, CX, y, DIM);
			y += 11;
		}
	}

	private void drawResults(GuiGraphics g) {
		int y = ROW_Y;
		var hist = bench.history();
		if (hist.isEmpty() && bench.lastSingle() == null) {
			Gfx.text(g, font, "No measurements yet. Press T, run /opm test, or run a Benchmark.", CX, y, DIM);
			return;
		}
		if (bench.lastSingle() != null) {
			TestResult r = bench.lastSingle();
			Gfx.text(g, font, Report.oneLine(r), CX, y, TEXT);
			y += 14;
		}
		if (hist.isEmpty()) return;
		BenchmarkRunner.Run run = hist.peekFirst();
		if (sortedFor != run) {
			sortedFor = run;
			sorted = Report.ranked(run);
		}
		Gfx.text(g, font, "LATEST BENCHMARK  " + new SimpleDateFormat("HH:mm:ss").format(new Date(run.time()))
				+ "  by " + run.metric().label, CX, y, ACCENT);
		y += 12;
		Gfx.text(g, font, "#  SLOT   AVG     1%LOW   MIN     FRAME    BOTTLENECK        RESULT", CX, y, DIM);
		y += 11;
		TestResult base = run.results().get(run.reference());
		int rank = 1;
		for (TestResult r : sorted) {
			Verdict v = r.slot == run.reference() ? null : Verdict.compare(base, r);
			int c = v == null ? TEXT : v == Verdict.IMPROVED ? GOOD : v == Verdict.REGRESSION ? BAD : DIM;
			String row = String.format(Locale.ROOT, "%d  S%d    %-7.1f %-7.1f %-7.1f %-6.2fms %-17s %s", rank++, r.slot + 1,
					r.avgFps, r.low1Fps, r.minFps, r.avgFrameMs, r.bottleneck.label,
					!r.valid ? "INVALID" : v == null ? "reference" : v.label);
			Gfx.text(g, font, row, CX, y, c);
			y += 10;
		}
		y += 8;
		int shown = 0;
		for (BenchmarkRunner.Run old : hist) {
			if (old == run) continue;
			if (shown++ >= 3 || y > height - 40) break;
			TestResult top = Report.ranked(old).get(0);
			Gfx.text(g, font, String.format(Locale.ROOT, "earlier %s: highest %s = SLOT %d (%.1f)",
					new SimpleDateFormat("HH:mm:ss").format(new Date(old.time())), old.metric().label, top.slot + 1,
					old.metric().of(top)), CX, y, DIM);
			y += 10;
		}
	}

	private void drawProfiles(GuiGraphics g) {
		int y = ROW_Y;
		Gfx.text(g, font, "5 slots. Each stores EVERY setting. Use S1-S5 above, then Save / Load / Revert / Test.", CX, y, DIM);
		y += 16;
		var hist = bench.history();
		BenchmarkRunner.Run run = hist.isEmpty() ? null : hist.peekFirst();
		for (int i = 0; i < ConfigManager.SLOT_COUNT; i++) {
			String line = "SLOT " + (i + 1) + (i == cfg.activeSlot() ? "  [ACTIVE]" : "") + (cfg.dirty(i) ? "  *unsaved" : "");
			if (run != null && run.results().get(i).valid)
				line += String.format(Locale.ROOT, "   last benchmark: %.1f avg / %.1f low", run.results().get(i).avgFps,
						run.results().get(i).low1Fps);
			Gfx.text(g, font, line, CX, y, i == selected ? ACCENT : TEXT);
			y += 12;
		}
		y += 8;
		Gfx.text(g, font, "Save writes config/opmizer/slots.json. Load applies the slot to the running game.", CX, y, DIM);
	}
}
