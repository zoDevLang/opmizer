package dev.opmizer.config;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * Describes one configurable value. Every value is stored as an int
 * (bool = 0/1, enum = ordinal) so a slot is just Map&lt;id, int&gt;.
 *
 * GAME        - bound to a real game option (reader + applier).
 * INTERNAL    - OPMIZER's own behaviour (test durations etc).
 * UNAVAILABLE - cannot be implemented safely; shown with the reason, never faked.
 */
public final class Setting {
	public enum Type { INT, BOOL, ENUM }
	public enum Source { GAME, INTERNAL, UNAVAILABLE }

	public final String id, label, description, benefits, costs, unavailableReason;
	public final Category category;
	public final Risk risk;
	public final Type type;
	public final Source source;
	public final int min, max, step, def;
	public final String[] choices;
	public final IntFunction<String> fmt;
	public final IntSupplier reader;
	public final IntConsumer applier;

	private Setting(Category category, String id, String label, Type type, Source source, int min, int max, int step,
			int def, String[] choices, IntFunction<String> fmt, Risk risk, String description, String benefits,
			String costs, IntSupplier reader, IntConsumer applier, String unavailableReason) {
		this.category = category;
		this.id = id;
		this.label = label;
		this.type = type;
		this.source = source;
		this.min = min;
		this.max = max;
		this.step = step;
		this.def = def;
		this.choices = choices;
		this.fmt = fmt;
		this.risk = risk;
		this.description = description;
		this.benefits = benefits;
		this.costs = costs;
		this.reader = reader;
		this.applier = applier;
		this.unavailableReason = unavailableReason;
	}

	public static Setting game(Category c, String id, String label, Type t, int min, int max, int step,
			String[] choices, IntFunction<String> fmt, Risk risk, String desc, String benefits, String costs,
			IntSupplier reader, IntConsumer applier) {
		return new Setting(c, id, label, t, Source.GAME, min, max, step, min, choices, fmt, risk, desc, benefits,
				costs, reader, applier, null);
	}

	public static Setting internal(Category c, String id, String label, Type t, int min, int max, int step, int def,
			Risk risk, String desc, String benefits, String costs) {
		return new Setting(c, id, label, t, Source.INTERNAL, min, max, step, def, null, null, risk, desc, benefits,
				costs, null, null, null);
	}

	public static Setting unavailable(Category c, String id, String label, Risk risk, String desc, String benefits,
			String costs, String reason) {
		return new Setting(c, id, label, Type.INT, Source.UNAVAILABLE, 0, 0, 1, 0, null, null, risk, desc, benefits,
				costs, null, null, reason);
	}

	public boolean available() {
		return source != Source.UNAVAILABLE;
	}

	public int clamp(int v) {
		if (type == Type.BOOL) return v != 0 ? 1 : 0;
		if (type == Type.ENUM) return Math.max(0, Math.min(choices.length - 1, v));
		return Math.max(min, Math.min(max, v));
	}

	/** Next value when the user presses - (dir = -1) or + (dir = +1). */
	public int step(int cur, int dir) {
		switch (type) {
			case BOOL:
				return cur != 0 ? 0 : 1;
			case ENUM:
				return (cur + dir + choices.length) % choices.length;
			default:
				return clamp(cur + dir * step);
		}
	}

	public String display(int v) {
		switch (type) {
			case BOOL:
				return v != 0 ? "ON" : "OFF";
			case ENUM:
				return choices[clamp(v)];
			default:
				return fmt != null ? fmt.apply(v) : Integer.toString(v);
		}
	}
}
