package dev.opmizer.compat;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * ALL version-fragile API calls are isolated here.
 *
 * UNVERIFIED (written without network access; 26.1 names guessed from the Mojang-name
 * migration notes). If the build fails, the fix should be in this file or in ui/Gfx:
 *  - KeyMappingHelper.registerKeyMapping  (older: KeyBindingHelper.registerKeyBinding)
 *  - ClientCommands.literal               (older: ClientCommandManager.literal)
 *  - Identifier                           (older: ResourceLocation)
 *  - KeyMapping.Category.register(...)    (added in 1.21.9)
 *  - gui.getChat().addMessage / gui.setOverlayMessage
 */
public final class Compat {
	private static KeyMapping.Category category;

	public static KeyMapping newKey(String translationKey, int glfwKey) {
		if (category == null) category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("opmizer", "main"));
		return new KeyMapping(translationKey, InputConstants.Type.KEYSYM, glfwKey, category);
	}

	public static KeyMapping registerKey(KeyMapping key) {
		return KeyMappingHelper.registerKeyMapping(key);
	}

	/** Registers the ONE OPMIZER command: /opm test. "/opm" alone does nothing. */
	public static void registerTestCommand(Runnable onTest) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher
				.register(ClientCommands.literal("opm").then(ClientCommands.literal("test").executes(ctx -> {
					onTest.run();
					return 1;
				}))));
	}

	public static void chat(String line) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui != null) mc.gui.getChat().addClientSystemMessage(Component.literal(line));
	}

	public static void actionBar(String line) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui != null) mc.gui.setOverlayMessage(Component.literal(line), false);
	}

	private Compat() {}
}
