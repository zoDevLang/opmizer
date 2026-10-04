package dev.opmizer.benchmark;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import dev.opmizer.profiler.TestResult;

/** Builds the text shown in chat. Every number is read from a TestResult. */
public final class Report {
	private static String f(String fmt, Object... a) {
		return String.format(Locale.ROOT, fmt, a);
	}

	public static String oneLine(TestResult r) {
		return f("OPMIZER SLOT %d: avg %.1f fps, 1%% low %.1f, min %.1f, frame %.2f ms, frames %d, %s", r.slot + 1,
				r.avgFps, r.low1Fps, r.minFps, r.avgFrameMs, r.frames, r.bottleneck.label);
	}

	public static List<String> single(TestResult r, TestResult prev) {
		List<String> o = new ArrayList<>();
		o.add("OPMIZER TEST");
		if (!r.valid) {
			o.add("STATUS: TEST INVALID - " + r.note);
			return o;
		}
		o.add(f("FPS: %.0f", r.avgFps));
		o.add(f("1%% LOW: %.0f", r.low1Fps));
		o.add(f("MIN FPS: %.0f", r.minFps));
		o.add(f("FRAME TIME: %.2f ms (std dev %.2f)", r.avgFrameMs, r.stdFrameMs));
		o.add(r.avgTickMs >= 0 ? f("TICK TIME: %.2f ms (max %.2f)", r.avgTickMs, r.maxTickMs) : "TICK TIME: n/a");
		if (r.detailed && r.procCpu >= 0)
			o.add(f("CPU: process %.0f%% of all cores, client thread %.0f%% | GC %.1f%%", r.procCpu * 100, r.mainCpu * 100,
					r.gcFraction * 100));
		o.add("GPU: no portable GPU counters; inferred only via BOTTLENECK");
		o.add("Configuration: SLOT " + (r.slot + 1));
		if (r.configNote != null) o.add("NOTE: " + r.configNote);
		if (r.vsync) o.add("NOTE: VSync is on, FPS may be refresh-limited");
		if (r.fpsCap < 260) o.add("NOTE: frame limiter at " + r.fpsCap + " fps");
		o.add("BOTTLENECK: " + r.bottleneck.label);

		Verdict v = Verdict.compare(prev, r);
		if (prev == null) {
			o.add("RESULT: UNDETERMINED (no earlier test to compare with)");
		} else {
			o.add("RESULT: " + v.label + " (vs previous test, SLOT " + (prev.slot + 1) + ")");
			o.add(f("Average FPS: %+.1f%%", Verdict.pct(prev.avgFps, r.avgFps)));
			o.add(f("1%% Low: %+.1f%%", Verdict.pct(prev.low1Fps, r.low1Fps)));
			o.add("Recommendation: " + v.recommendation());
		}
		if (!r.note.isEmpty()) o.add("NOTE: " + r.note);
		o.add("STATUS: TEST COMPLETE");
		return o;
	}

	public static List<TestResult> ranked(BenchmarkRunner.Run run) {
		List<TestResult> s = new ArrayList<>(run.results());
		s.sort(Comparator.comparingDouble((TestResult r) -> run.metric().of(r)).reversed());
		return s;
	}

	public static List<String> suite(BenchmarkRunner.Run run) {
		List<String> o = new ArrayList<>();
		o.add("OPMIZER BENCHMARK - ranked by " + run.metric().label + " (greatest first)");
		TestResult base = run.results().get(run.reference());
		int rank = 1;
		for (TestResult r : ranked(run)) {
			String tag = !r.valid ? "INVALID: " + r.note
					: r.slot == run.reference() ? "reference"
					: Verdict.compare(base, r).label;
			o.add(f("#%d SLOT %d  avg %.1f  1%%low %.1f  min %.1f  %.2f ms  %s  [%s]", rank++, r.slot + 1, r.avgFps,
					r.low1Fps, r.minFps, r.avgFrameMs, r.bottleneck.label, tag));
		}

		TestResult top = ranked(run).get(0);
		if (!top.valid || !base.valid) {
			o.add("BEST: UNDETERMINED (not enough valid data)");
		} else if (top.slot == run.reference()) {
			o.add("BEST MEASURED: SLOT " + (top.slot + 1) + " (the reference). No slot beat it on " + run.metric().label + ".");
		} else {
			Verdict v = Verdict.compare(base, top);
			if (v == Verdict.IMPROVED)
				o.add(f("BEST MEASURED: SLOT %d on %s (%+.1f%% vs SLOT %d). Single run - repeat to confirm.", top.slot + 1,
						run.metric().label, Verdict.pct(run.metric().of(base), run.metric().of(top)), base.slot + 1));
			else
				o.add("BEST: UNDETERMINED - the top slot is not significantly different from the reference (" + v.label + ").");
		}
		o.add("STATUS: BENCHMARK COMPLETE. Your previous settings were restored.");
		return o;
	}

	private Report() {}
}
