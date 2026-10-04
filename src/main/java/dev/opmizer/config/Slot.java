package dev.opmizer.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** One COMPLETE OPMIZER configuration: a value for every available setting. */
public final class Slot {
	public final Map<String, Integer> values = new LinkedHashMap<>();

	public int get(Setting s) {
		Integer v = values.get(s.id);
		return v == null ? s.def : s.clamp(v);
	}

	public void set(Setting s, int v) {
		values.put(s.id, s.clamp(v));
	}

	public Slot copy() {
		Slot c = new Slot();
		c.values.putAll(values);
		return c;
	}

	public boolean sameAs(Slot o) {
		return values.equals(o.values);
	}
}
