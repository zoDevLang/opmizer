package dev.opmizer.config;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.opmizer.core.OpmizerClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Exactly FIVE slots. Each is a complete configuration. "saved" is what is on disk,
 * "working" is the editable copy shown in the UI.
 */
public final class ConfigManager {
	public static final int SLOT_COUNT = 5;

	private final Slot[] saved = new Slot[SLOT_COUNT];
	private final Slot[] working = new Slot[SLOT_COUNT];
	private int active = 0;
	private boolean init = false;

	private Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("opmizer").resolve("slots.json");
	}

	/** Lazy because game options may not exist yet during mod init. Cheap once done. */
	public void ensureInit() {
		if (init) return;
		if (Minecraft.getInstance().options == null) return;
		init = true;
		readFile();
		for (int i = 0; i < SLOT_COUNT; i++) {
			if (saved[i] == null) saved[i] = new Slot();
			complete(saved[i]);
			working[i] = saved[i].copy();
		}
	}

	/** Missing keys are filled from the CURRENT game state, so a fresh slot changes nothing. */
	private void complete(Slot s) {
		for (Setting st : SettingRegistry.ALL) {
			if (!st.available()) continue;
			if (!s.values.containsKey(st.id)) {
				int v = st.def;
				if (st.source == Setting.Source.GAME) {
					try {
						v = st.reader.getAsInt();
					} catch (Throwable t) {
						OpmizerClient.LOG.warn("OPMIZER: could not read '{}': {}", st.id, t.toString());
					}
				}
				s.set(st, v);
			} else {
				s.set(st, s.values.get(st.id));
			}
		}
	}

	private void readFile() {
		Path p = file();
		if (!Files.exists(p)) return;
		try (Reader r = Files.newBufferedReader(p)) {
			JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
			if (root.has("activeSlot")) active = Math.max(0, Math.min(SLOT_COUNT - 1, root.get("activeSlot").getAsInt() - 1));
			JsonArray arr = root.getAsJsonArray("slots");
			for (JsonElement e : arr) {
				JsonObject so = e.getAsJsonObject();
				int idx = so.get("index").getAsInt() - 1;
				if (idx < 0 || idx >= SLOT_COUNT) continue;
				Slot s = new Slot();
				for (Map.Entry<String, JsonElement> en : so.getAsJsonObject("values").entrySet())
					s.values.put(en.getKey(), en.getValue().getAsInt());
				saved[idx] = s;
			}
		} catch (Exception ex) {
			OpmizerClient.LOG.warn("OPMIZER: could not read slots.json, starting from current game state: {}", ex.toString());
		}
	}

	private void writeFile() {
		try {
			Path p = file();
			Files.createDirectories(p.getParent());
			JsonObject root = new JsonObject();
			root.addProperty("version", 1);
			root.addProperty("activeSlot", active + 1);
			JsonArray arr = new JsonArray();
			for (int i = 0; i < SLOT_COUNT; i++) {
				JsonObject so = new JsonObject();
				so.addProperty("index", i + 1);
				JsonObject vals = new JsonObject();
				for (Map.Entry<String, Integer> en : saved[i].values.entrySet()) vals.addProperty(en.getKey(), en.getValue());
				so.add("values", vals);
				arr.add(so);
			}
			root.add("slots", arr);
			Path tmp = p.resolveSibling("slots.json.tmp");
			try (Writer w = Files.newBufferedWriter(tmp)) {
				new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
			}
			Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception ex) {
			OpmizerClient.LOG.error("OPMIZER: failed to write slots.json", ex);
		}
	}

	// ---------- slot operations ----------
	public int activeSlot() { return active; }
	public Slot working(int i) { return working[i]; }
	public Slot saved(int i) { return saved[i]; }
	public boolean dirty(int i) { return !working[i].sameAs(saved[i]); }

	public void save(int i) {
		saved[i] = working[i].copy();
		writeFile();
	}

	public void revert(int i) {
		working[i] = saved[i].copy();
	}

	/** Load = discard edits, apply to the game, make it the active slot. */
	public int load(int i) {
		working[i] = saved[i].copy();
		int failures = apply(working[i]);
		active = i;
		writeFile();
		return failures;
	}

	/** Apply the working copy (including unsaved edits) and make it active. */
	public int applyWorking(int i) {
		int failures = apply(working[i]);
		active = i;
		return failures;
	}

	/** Pushes GAME settings into the live options. Only touches values that differ. Returns failure count. */
	public int apply(Slot s) {
		int failures = 0;
		for (Setting st : SettingRegistry.ALL) {
			if (st.source != Setting.Source.GAME) continue;
			try {
				int want = s.get(st);
				if (st.reader.getAsInt() != want) st.applier.accept(want);
			} catch (Throwable t) {
				failures++;
				OpmizerClient.LOG.warn("OPMIZER: could not apply '{}': {}", st.id, t.toString());
			}
		}
		return failures;
	}

	/** Current live game values as a slot (used to restore after a benchmark). */
	public Slot snapshotGame() {
		Slot s = new Slot();
		for (Setting st : SettingRegistry.ALL) {
			if (st.source == Setting.Source.GAME) {
				try {
					s.set(st, st.reader.getAsInt());
				} catch (Throwable ignored) {
				}
			}
		}
		return s;
	}

	/** True if the live game currently matches this slot's GAME settings. */
	public boolean matchesGame(Slot s) {
		for (Setting st : SettingRegistry.ALL) {
			if (st.source != Setting.Source.GAME) continue;
			try {
				if (st.reader.getAsInt() != s.get(st)) return false;
			} catch (Throwable t) {
				return false;
			}
		}
		return true;
	}

	public int value(int slot, String id) {
		return working[slot].get(SettingRegistry.byId(id));
	}
}
