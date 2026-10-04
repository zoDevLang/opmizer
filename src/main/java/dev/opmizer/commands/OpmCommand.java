package dev.opmizer.commands;

import dev.opmizer.compat.Compat;
import dev.opmizer.core.OpmizerClient;

/** The only OPMIZER command: /opm test */
public final class OpmCommand {
	public static void register() {
		Compat.registerTestCommand(() -> OpmizerClient.BENCH.startSingle(OpmizerClient.CONFIG.activeSlot()));
	}

	private OpmCommand() {}
}
