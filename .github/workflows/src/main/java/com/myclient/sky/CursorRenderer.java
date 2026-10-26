package com.myclient.sky;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CursorRenderer {
    public static void draw(GuiGraphicsExtractor g, int cx, int cy, CursorStyle style, int color, float size) {
        switch (style) {
            case DOT -> {
                int r = Math.max(1, Math.round(2 * size));
                g.fill(cx - r, cy - r, cx + r, cy + r, color);
            }
            case RING -> {
                double r = 5 * size;
                for (int i = 0; i < 48; i++) {
                    double a = i * Math.PI * 2 / 48;
                    int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
                    g.fill(x, y, x + 1, y + 1, color);
                }
            }
            case CROSS -> {
                int gap = Math.round(2 * size), len = Math.round(5 * size);
                g.fill(cx - gap - len, cy, cx - gap, cy + 1, color);
                g.fill(cx + gap + 1, cy, cx + gap + 1 + len, cy + 1, color);
                g.fill(cx, cy - gap - len, cx + 1, cy - gap, color);
                g.fill(cx, cy + gap + 1, cx + 1, cy + gap + 1 + len, color);
            }
            case ARROW -> {
                int h = Math.round(10 * size);
                for (int i = 0; i < h; i++) {
                    int w = Math.max(1, (int) (i * 0.6) + 1);
                    g.fill(cx, cy + i, cx + w, cy + i + 1, color);
                }
            }
        }
    }
}
