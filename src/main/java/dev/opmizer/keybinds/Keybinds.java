package dev.opmizer.keybinds;

import org.lwjgl.glfw.GLFW;

import dev.opmizer.compat.Compat;
import dev.opmizer.core.OpmizerClient;
import dev.opmizer.ui.OpmizerScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * The two OPMIZER keybinds: Q opens the config UI, T starts the test.
 * NOTE: these are the requested defaults. Vanilla also uses Q (Drop Item) and T (Chat);
 * rebind whichever you prefer in Options > Controls.
 */
public final class Keybinds {
	private static KeyMapping open, test;

	public static void register() {
		open = Compat.registerKey(Compat.newKey("key.opmizer.open", GLFW.GLFW_KEY_Q));
		test = Compat.registerKey(Compat.newKey("key.opmizer.test", GLFW.GLFW_KEY_T));
	}

	/** Called every client tick. */
	public static void poll(Minecraft mc) {
		while (open.consumeClick()) {
			if (mc.screen == null) mc.setScreen(new OpmizerScreen());
		}
		while (test.consumeClick()) {
			if (mc.screen == null) OpmizerClient.BENCH.startSingle(OpmizerClient.CONFIG.activeSlot());
		}
	}

	private Keybinds() {}
}
