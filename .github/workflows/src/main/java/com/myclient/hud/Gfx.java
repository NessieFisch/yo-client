package com.myclient.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.reflect.Method;

/**
 * Smooth drawing helpers. Everything is drawn on a grid twice as fine as Minecraft's GUI pixels,
 * with anti-aliased edges. If the pose stack can't be scaled it falls back to plain blocky fills.
 */
public final class Gfx {
    private static Method poseM, pushM, popM, scaleM;
    private static boolean init, ok, threeD;

    public static boolean begin(GuiGraphicsExtractor g) {
        if (!init) {
            init = true;
            try {
                poseM = g.getClass().getMethod("pose");
                Object p = poseM.invoke(g);
                try {
                    pushM = p.getClass().getMethod("pushMatrix");
                    popM = p.getClass().getMethod("popMatrix");
                    scaleM = p.getClass().getMethod("scale", float.class, float.class);
                } catch (Throwable t) {
                    pushM = p.getClass().getMethod("pushPose");
                    popM = p.getClass().getMethod("popPose");
                    scaleM = p.getClass().getMethod("scale", float.class, float.class, float.class);
                    threeD = true;
                }
                ok = true;
            } catch (Throwable t) { ok = false; }
        }
        if (!ok) return false;
        try {
            Object p = poseM.invoke(g);
            pushM.invoke(p);
            if (threeD) scaleM.invoke(p, 0.5f, 0.5f, 1f); else scaleM.invoke(p, 0.5f, 0.5f);
            return true;
        } catch (Throwable t) { ok = false; return false; }
    }

    public static void end(GuiGraphicsExtractor g) {
        try { popM.invoke(poseM.invoke(g)); } catch (Throwable ignored) {}
    }

    // ---------- colour helpers ----------
    public static int a(int c, float f) {
        int al = Math.min(255, Math.max(0, Math.round(((c >>> 24) & 0xFF) * f)));
        return (al << 24) | (c & 0x00FFFFFF);
    }

    public static int lerp(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int al = Math.round(((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
        int r = Math.round(((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int gr = Math.round(((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = Math.round((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (al << 24) | (r << 16) | (gr << 8) | b;
    }

    // ---------- core: rounded rect in half-units (X,Y,W,H,R are all in 1/2 GUI pixels) ----------
    private static void rr2(GuiGraphicsExtractor g, int X, int Y, int W, int H, int R, int top, int bot) {
        if (W <= 0 || H <= 0) return;
        R = Math.max(0, Math.min(R, Math.min(W, H) / 2));
        int bt = R, bb = H - R;
        if (bb > bt) {
            int c1 = lerp(top, bot, (float) bt / H), c2 = lerp(top, bot, (float) bb / H);
            if (c1 == c2) g.fill(X, Y + bt, X + W, Y + bb, c1);
            else g.fillGradient(X, Y + bt, X + W, Y + bb, c1, c2);
        }
        for (int i = 0; i < R; i++) {
            double dy = R - (i + 0.5);
            double inset = R - Math.sqrt(Math.max(0, (double) R * R - dy * dy));
            int fl = (int) Math.floor(inset);
            float cov = (float) (fl + 1 - inset);
            for (int side = 0; side < 2; side++) {
                int row = side == 0 ? i : H - 1 - i;
                int col = lerp(top, bot, (row + 0.5f) / H);
                int xs = X + fl + 1, xe = X + W - fl - 1;
                if (xe > xs) g.fill(xs, Y + row, xe, Y + row + 1, col);
                int pc = a(col, cov);
                g.fill(X + fl, Y + row, X + fl + 1, Y + row + 1, pc);
                if (X + W - fl - 1 > X + fl) g.fill(X + W - fl - 1, Y + row, X + W - fl, Y + row + 1, pc);
            }
        }
    }

    private static void blocky(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < h; i++) {
            int inset = 0;
            if (i < r) inset = r - (int) Math.sqrt(r * r - (r - i) * (r - i));
            else if (i >= h - r) { int j = h - 1 - i; inset = r - (int) Math.sqrt(r * r - (r - j) * (r - j)); }
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
    }

    // ---------- public API (GUI units) ----------
    public static void roundV2(GuiGraphicsExtractor g, int X, int Y, int W, int H, int R, int top, int bot) {
        if (begin(g)) { try { rr2(g, X, Y, W, H, R, top, bot); } finally { end(g); } }
        else blocky(g, X / 2, Y / 2, W / 2, H / 2, R / 2, top);
    }

    public static void roundV(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bot) {
        roundV2(g, x * 2, y * 2, w * 2, h * 2, r * 2, top, bot);
    }

    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        roundV2(g, x * 2, y * 2, w * 2, h * 2, r * 2, color, color);
    }

    public static void roundF(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int color) {
        roundV2(g, Math.round(x * 2), Math.round(y * 2), Math.max(1, Math.round(w * 2)), Math.max(1, Math.round(h * 2)),
                Math.round(r * 2), color, color);
    }

    public static void circle(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
        roundF(g, cx - r, cy - r, 2 * r, 2 * r, r, color);
    }

    public static void rectF(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
        if (begin(g)) {
            try {
                int X = Math.round(x * 2), Y = Math.round(y * 2);
                g.fill(X, Y, X + Math.max(1, Math.round(w * 2)), Y + Math.max(1, Math.round(h * 2)), color);
            } finally { end(g); }
        } else g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
    }

    /** Filled fine-grid rectangle in half-units. */
    public static void fill2(GuiGraphicsExtractor g, int X1, int Y1, int X2, int Y2, int color) {
        if (begin(g)) { try { g.fill(X1, Y1, X2, Y2, color); } finally { end(g); } }
        else g.fill(X1 / 2, Y1 / 2, X2 / 2, Y2 / 2, color);
    }

    public static void gradient2(GuiGraphicsExtractor g, int X1, int Y1, int X2, int Y2, int c1, int c2) {
        if (begin(g)) { try { g.fillGradient(X1, Y1, X2, Y2, c1, c2); } finally { end(g); } }
        else g.fillGradient(X1 / 2, Y1 / 2, X2 / 2, Y2 / 2, c1, c2);
    }

    /** Rounded rect with a thin (half pixel) border. */
    public static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int borderCol, int top, int bot) {
        if (begin(g)) {
            try {
                int X = x * 2, Y = y * 2, W = w * 2, H = h * 2, R = r * 2;
                rr2(g, X, Y, W, H, R, borderCol, borderCol);
                rr2(g, X + 1, Y + 1, W - 2, H - 2, Math.max(0, R - 1), top, bot);
            } finally { end(g); }
        } else {
            blocky(g, x, y, w, h, r, borderCol);
            blocky(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), top);
        }
    }

    /** Soft drop shadow / glow behind a rounded rect. */
    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int rgb, int maxAlpha, int yOff2) {
        if (!begin(g)) return;
        try {
            for (int k = 5; k >= 1; k--) {
                float t = (6 - k) / 6f;
                int col = ((int) (maxAlpha * t * t) << 24) | (rgb & 0x00FFFFFF);
                rr2(g, x * 2 - k * 2, y * 2 - k * 2 + yOff2, w * 2 + k * 4, h * 2 + k * 4, r * 2 + k * 2, col, col);
            }
        } finally { end(g); }
    }

    /** Subtle light streak along the top of a panel. */
    public static void shine(GuiGraphicsExtractor g, int x, int y, int w, int r) {
        gradient2(g, x * 2 + r * 2, y * 2 + 2, (x + w) * 2 - r * 2, y * 2 + 12, 0x2CFFFFFF, 0x00FFFFFF);
    }

    /** dir: 0 right, 1 left, 2 up, 3 down */
    public static void tri(GuiGraphicsExtractor g, float x, float y, float w, float h, int dir, int color) {
        if (!begin(g)) return;
        try {
            int X = Math.round(x * 2), Y = Math.round(y * 2), W = Math.max(1, Math.round(w * 2)), H = Math.max(1, Math.round(h * 2));
            if (dir < 2) {
                for (int j = 0; j < W; j++) {
                    float f = dir == 0 ? (j + 0.5f) / W : 1 - (j + 0.5f) / W;
                    int hh = Math.round((1 - f) * H / 2f);
                    if (hh <= 0) continue;
                    g.fill(X + j, Y + H / 2 - hh, X + j + 1, Y + H / 2 + hh, color);
                }
            } else {
                for (int i = 0; i < H; i++) {
                    float f = dir == 2 ? (i + 0.5f) / H : 1 - (i + 0.5f) / H;
                    int hw = Math.round(f * W / 2f);
                    if (hw <= 0) continue;
                    g.fill(X + W / 2 - hw, Y + i, X + W / 2 + hw, Y + i + 1, color);
                }
            }
        } finally { end(g); }
    }

    /** Thin ring; thick is in half-units. */
    public static void ring(GuiGraphicsExtractor g, float cx, float cy, float r, int thick, int color) {
        if (!begin(g)) return;
        try {
            int n = Math.max(48, (int) (2 * Math.PI * r * 2 * 1.3));
            for (int k = 0; k < n; k++) {
                double ang = k * Math.PI * 2 / n;
                int px = (int) Math.round(cx * 2 + Math.cos(ang) * r * 2), py = (int) Math.round(cy * 2 + Math.sin(ang) * r * 2);
                g.fill(px - thick / 2, py - thick / 2, px - thick / 2 + thick, py - thick / 2 + thick, color);
            }
        } finally { end(g); }
    }

    /** Mouse-pointer arrow with its tip at (x, y). */
    public static void pointer(GuiGraphicsExtractor g, float x, float y, float size, int color) {
        if (!begin(g)) return;
        try {
            int X = Math.round(x * 2), Y = Math.round(y * 2), H = Math.round(13 * size * 2);
            for (int i = 0; i < H; i++) {
                int w = (int) (i * 0.62) + 1;
                g.fill(X, Y + i, X + w, Y + i + 1, color);
            }
        } finally { end(g); }
    }
}
