package dev.opmizer.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Thin wrapper over the 2D draw calls so a rename in 26.1 only breaks this file.
 * UNVERIFIED: GuiGraphics#fill / #drawString names on 26.1 (a newer render-state API may replace them).
 */
final class Gfx {
	static void fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int argb) {
		g.fill(x1, y1, x2, y2, argb);
	}

	static void text(GuiGraphicsExtractor g, Font f, String s, int x, int y, int argb) {
		g.text(f, s, x, y, argb);
	}

	static void textRight(GuiGraphicsExtractor g, Font f, String s, int xRight, int y, int argb) {
		g.text(f, s, xRight - f.width(s), y, argb);
	}

	static void textCentered(GuiGraphicsExtractor g, Font f, String s, int xCenter, int y, int argb) {
		g.text(f, s, xCenter - f.width(s) / 2, y, argb);
	}

	private Gfx() {}
}
