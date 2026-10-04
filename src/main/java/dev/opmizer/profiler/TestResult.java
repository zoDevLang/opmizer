package dev.opmizer.profiler;

/** Plain data holder for one measured test window. All values come from the running game. */
public final class TestResult {
	public int slot;                 // 0-based
	public long timestamp;
	public String configNote;        // e.g. game differs from the slot

	public int frames;
	public double seconds;           // sum of sampled frame intervals
	public double avgFps, minFps, low1Fps;
	public double avgFrameMs, maxFrameMs, stdFrameMs;
	public double avgTickMs = -1, maxTickMs = -1;

	public boolean detailed;
	public double procCpu = -1;      // 0..1 of all cores
	public double mainCpu = -1;      // 0..1 of one core (client thread)
	public double gcFraction;        // 0..1
	public long gcMs;
	public long heapStartMb, heapEndMb;

	public int fpsCap = 260;
	public boolean vsync;

	public boolean valid;
	public String note = "";
	public Bottleneck bottleneck = Bottleneck.UNDETERMINED;
}
