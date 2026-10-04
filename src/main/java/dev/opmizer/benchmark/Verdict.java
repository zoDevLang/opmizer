package dev.opmizer.benchmark;

import dev.opmizer.profiler.TestResult;

/** Compares two measured results. Nothing is assumed: no evidence means UNDETERMINED. */
public enum Verdict {
	IMPROVED("IMPROVED"),
	REGRESSION("REGRESSION"),
	NO_CHANGE("NO SIGNIFICANT CHANGE"),
	UNDETERMINED("UNDETERMINED");

	/** Percent change below which a difference is treated as noise. Single runs have run-to-run variance. */
	public static final double THRESHOLD_PCT = 3.0;

	public final String label;

	Verdict(String label) {
		this.label = label;
	}

	public static double pct(double base, double v) {
		return base <= 0 ? 0 : (v - base) / base * 100.0;
	}

	public static Verdict compare(TestResult base, TestResult t) {
		if (base == null || t == null || !base.valid || !t.valid) return UNDETERMINED;
		double a = pct(base.avgFps, t.avgFps);
		double l = pct(base.low1Fps, t.low1Fps);
		boolean up = a >= THRESHOLD_PCT || l >= THRESHOLD_PCT;
		boolean down = a <= -THRESHOLD_PCT || l <= -THRESHOLD_PCT;
		if (up && down) return UNDETERMINED; // mixed signals: average and 1% low disagree
		if (down) return REGRESSION;
		if (up) return IMPROVED;
		return NO_CHANGE;
	}

	public String recommendation() {
		switch (this) {
			case REGRESSION:
				return "Revert configuration.";
			case IMPROVED:
				return "Improvement measured. Repeat the test to confirm before relying on it.";
			case NO_CHANGE:
				return "No meaningful difference measured. Prefer the lower-risk configuration.";
			default:
				return "Not enough evidence. Re-run under identical conditions.";
		}
	}
}
