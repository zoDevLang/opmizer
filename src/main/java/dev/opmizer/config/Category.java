package dev.opmizer.config;

/** UI categories. The first nine hold settings; the last three are views. */
public enum Category {
	RENDERING("Rendering", true),
	CPU("CPU", true),
	THREADS("Threads", true),
	MEMORY("Memory", true),
	CHUNKS("Chunks", true),
	ENTITIES("Entities", true),
	WORLD("World", true),
	SCHEDULING("Scheduling", true),
	ADVANCED("Advanced", true),
	BENCHMARK("Benchmark", false),
	RESULTS("Results", false),
	PROFILES("Profiles", false);

	public final String label;
	public final boolean hasSettings;

	Category(String label, boolean hasSettings) {
		this.label = label;
		this.hasSettings = hasSettings;
	}
}
