package dev.opmizer.profiler;

/**
 * Heuristic classification from what was actually measured during a test.
 * It is a hint, not a proof. When evidence is thin the answer is UNDETERMINED.
 * CHUNK-BOUND is part of the vocabulary but is never reported: OPMIZER has no
 * chunk-pipeline instrumentation yet (see README).
 */
public enum Bottleneck {
	CPU_BOUND("CPU-BOUND"),
	GPU_BOUND("GPU-BOUND"),
	MEMORY_BOUND("MEMORY-BOUND"),
	CHUNK_BOUND("CHUNK-BOUND"),
	MAIN_THREAD_BOUND("MAIN-THREAD-BOUND"),
	MIXED("MIXED"),
	UNDETERMINED("UNDETERMINED");

	public final String label;

	Bottleneck(String label) {
		this.label = label;
	}

	public static Bottleneck classify(TestResult r) {
		if (!r.valid || !r.detailed || r.procCpu < 0 || r.mainCpu < 0) return UNDETERMINED;
		// A limiter makes every other signal meaningless.
		boolean capped = r.fpsCap < 260 && r.avgFps >= 0.95 * r.fpsCap;
		if (capped || r.vsync) return r.gcFraction >= 0.05 ? MEMORY_BOUND : UNDETERMINED;

		boolean memory = r.gcFraction >= 0.05;       // >=5% of wall time inside GC
		boolean mainSat = r.mainCpu >= 0.90;         // client thread almost never waits
		boolean cpuSat = r.procCpu >= 0.90;          // all cores busy
		boolean gpuLikely = r.mainCpu < 0.60 && r.procCpu < 0.60; // CPU idle while frames are slow

		if (memory) return (mainSat || cpuSat) ? MIXED : MEMORY_BOUND;
		if (mainSat) return MAIN_THREAD_BOUND;
		if (cpuSat) return CPU_BOUND;
		if (gpuLikely) return GPU_BOUND;
		return UNDETERMINED;
	}
}
