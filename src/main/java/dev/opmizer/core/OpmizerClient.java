package dev.opmizer.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.opmizer.benchmark.BenchmarkRunner;
import dev.opmizer.commands.OpmCommand;
import dev.opmizer.config.ConfigManager;
import dev.opmizer.keybinds.Keybinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class OpmizerClient implements ClientModInitializer {
	public static final String ID = "opmizer";
	public static final Logger LOG = LoggerFactory.getLogger("OPMIZER");

	public static final ConfigManager CONFIG = new ConfigManager();
	public static final BenchmarkRunner BENCH = new BenchmarkRunner(CONFIG);

	@Override
	public void onInitializeClient() {
		Keybinds.register();
		OpmCommand.register();

		ClientTickEvents.START_CLIENT_TICK.register(mc -> BENCH.onTickStart(System.nanoTime()));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			BENCH.onTickEnd(System.nanoTime());
			CONFIG.ensureInit();
			Keybinds.poll(mc);
		});
		LOG.info("OPMIZER loaded. Press Q for config, T or /opm test to measure. No FPS is promised.");
	}

	/** Called by the Minecraft.runTick mixin once per frame. */
	public static void onFrame() {
		BENCH.onFrame(System.nanoTime());
	}
}
