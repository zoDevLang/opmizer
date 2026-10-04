package dev.opmizer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.opmizer.core.OpmizerClient;
import net.minecraft.client.Minecraft;

/** One nanoTime() per frame, and only when a test is running. UNVERIFIED: runTick(boolean) signature on 26.1. */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void opmizer$frame(boolean renderLevel, CallbackInfo ci) {
		OpmizerClient.onFrame();
	}
}
