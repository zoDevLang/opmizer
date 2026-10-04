package dev.opmizer.profiler;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;

/**
 * Lightweight sampler. Does nothing unless a measurement window is open.
 * Buffers are allocated once on first use and reused, so frames never allocate.
 */
public final class Sampler {
	private static final int FRAME_CAP = 200_000;
	private static final int TICK_CAP = 20 * 130;
	public static final int MIN_FRAMES = 100;

	private float[] frames, ticks, scratch;
	private int nFrames, nTicks;
	private long lastFrameNs, tickStartNs, startNs;
	private volatile boolean running;
	private boolean detailed, bufferFull;
	private long cpuStartNs, mainCpuStartNs, gcStartMs, mainTid;
	private final long heapStartBytes[] = new long[1];

	public boolean running() { return running; }

	public void start(boolean detailedProfiling, long nowNs) {
		if (frames == null) {
			frames = new float[FRAME_CAP];
			ticks = new float[TICK_CAP];
			scratch = new float[FRAME_CAP];
		}
		nFrames = 0;
		nTicks = 0;
		bufferFull = false;
		lastFrameNs = 0;
		tickStartNs = 0;
		startNs = nowNs;
		detailed = detailedProfiling;
		Thread t = Thread.currentThread();
		mainTid = t.threadId();
		Runtime rt = Runtime.getRuntime();
		heapStartBytes[0] = rt.totalMemory() - rt.freeMemory();
		if (detailed) {
			cpuStartNs = Probe.processCpuNs();
			mainCpuStartNs = Probe.threadCpuNs(mainTid);
			gcStartMs = Probe.gcMs();
		}
		running = true;
	}

	/** Called once per frame from the Minecraft.runTick mixin (only while running). */
	public void frame(long nowNs) {
		if (!running) return;
		if (lastFrameNs != 0) {
			if (nFrames < FRAME_CAP) frames[nFrames++] = (nowNs - lastFrameNs) / 1_000_000f;
			else bufferFull = true;
		}
		lastFrameNs = nowNs;
	}

	public void tickStart(long nowNs) {
		if (running) tickStartNs = nowNs;
	}

	public void tickEnd(long nowNs) {
		if (running && tickStartNs != 0 && nTicks < TICK_CAP) {
			ticks[nTicks++] = (nowNs - tickStartNs) / 1_000_000f;
			tickStartNs = 0;
		}
	}

	public TestResult stop(long nowNs, int slot) {
		running = false;
		TestResult r = new TestResult();
		r.slot = slot;
		r.detailed = detailed;
		r.frames = nFrames;

		double sum = 0, max = 0;
		for (int i = 0; i < nFrames; i++) {
			sum += frames[i];
			if (frames[i] > max) max = frames[i];
		}
		if (nFrames > 0) {
			r.seconds = sum / 1000.0;
			r.avgFrameMs = sum / nFrames;
			r.maxFrameMs = max;
			r.avgFps = nFrames / r.seconds;
			r.minFps = 1000.0 / max;

			double var = 0;
			for (int i = 0; i < nFrames; i++) {
				double d = frames[i] - r.avgFrameMs;
				var += d * d;
			}
			r.stdFrameMs = Math.sqrt(var / nFrames);

			// 1% low = average FPS of the slowest 1% of frames
			System.arraycopy(frames, 0, scratch, 0, nFrames);
			Arrays.sort(scratch, 0, nFrames);
			int k = Math.max(1, nFrames / 100);
			double worst = 0;
			for (int i = nFrames - k; i < nFrames; i++) worst += scratch[i];
			r.low1Fps = 1000.0 / (worst / k);
		}

		if (nTicks > 0) {
			double ts = 0, tm = 0;
			for (int i = 0; i < nTicks; i++) {
				ts += ticks[i];
				if (ticks[i] > tm) tm = ticks[i];
			}
			r.avgTickMs = ts / nTicks;
			r.maxTickMs = tm;
		}

		Runtime rt = Runtime.getRuntime();
		r.heapStartMb = heapStartBytes[0] >> 20;
		r.heapEndMb = (rt.totalMemory() - rt.freeMemory()) >> 20;

		if (detailed) {
			long wallNs = Math.max(1, nowNs - startNs);
			long cpuEnd = Probe.processCpuNs();
			long mainEnd = Probe.threadCpuNs(mainTid);
			if (cpuEnd >= 0 && cpuStartNs >= 0)
				r.procCpu = Math.min(1.0, (cpuEnd - cpuStartNs) / (double) (wallNs * rt.availableProcessors()));
			if (mainEnd >= 0 && mainCpuStartNs >= 0)
				r.mainCpu = Math.min(1.0, (mainEnd - mainCpuStartNs) / (double) wallNs);
			long gcEnd = Probe.gcMs();
			if (gcEnd >= 0 && gcStartMs >= 0) {
				r.gcMs = gcEnd - gcStartMs;
				r.gcFraction = Math.min(1.0, r.gcMs / (wallNs / 1_000_000.0));
			}
		}

		r.valid = nFrames >= MIN_FRAMES;
		if (!r.valid) r.note = "too few frames sampled (" + nFrames + "); need " + MIN_FRAMES;
		else if (bufferFull) r.note = "sample buffer filled; later frames were not recorded";
		return r;
	}

	/** JMX probes. Isolated because some trimmed runtimes lack jdk.management. Every call is guarded. */
	private static final class Probe {
		static long processCpuNs() {
			try {
				var os = ManagementFactory.getOperatingSystemMXBean();
				if (os instanceof com.sun.management.OperatingSystemMXBean x) return x.getProcessCpuTime();
			} catch (Throwable ignored) {
			}
			return -1;
		}

		static long threadCpuNs(long tid) {
			try {
				ThreadMXBean t = ManagementFactory.getThreadMXBean();
				if (t.isThreadCpuTimeSupported()) return t.getThreadCpuTime(tid);
			} catch (Throwable ignored) {
			}
			return -1;
		}

		static long gcMs() {
			try {
				long total = 0;
				for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
					long t = b.getCollectionTime();
					if (t > 0) total += t;
				}
				return total;
			} catch (Throwable ignored) {
				return -1;
			}
		}
	}
}
