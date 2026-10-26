package com.myclient.hud;

import com.myclient.ClientConfig;
import com.myclient.music.MusicPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class HudRenderer {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final float[] speedHist = new float[40];
    private static final float[] memHist = new float[40];
    private static double lx, lz;
    private static boolean has;
    private static float speed;
    private static int fps, frames;
    private static long fpsStamp = System.nanoTime();

    private static void push(float[] a, float v) {
        System.arraycopy(a, 1, a, 0, a.length - 1);
        a[a.length - 1] = v;
    }

    /** Called every client tick (20/s). */
    public static void tick(Minecraft mc) {
        if (mc.player == null) { has = false; return; }
        double x = mc.player.getX(), z = mc.player.getZ();
        if (has) speed = (float) (Math.hypot(x - lx, z - lz) * 20.0);
        lx = x; lz = z; has = true;
        push(speedHist, speed);
        Runtime rt = Runtime.getRuntime();
        push(memHist, (rt.totalMemory() - rt.freeMemory()) / (float) rt.maxMemory());
    }

    // ---------- drawing helpers ----------
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < h; i++) {
            int inset = 0;
            if (i < r) inset = r - (int) Math.sqrt(r * r - (r - i) * (r - i));
            else if (i >= h - r) { int j = h - 1 - i; inset = r - (int) Math.sqrt(r * r - (r - j) * (r - j)); }
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
    }

    /** dark version of the accent colour, used for glass backgrounds */
    public static int tint(int accent, float k, int alpha) {
        int r = (int) (((accent >> 16) & 255) * k), gr = (int) (((accent >> 8) & 255) * k), b = (int) ((accent & 255) * k);
        return (alpha << 24) | (r << 16) | (gr << 8) | b;
    }

    /** Glass panel: soft glow, accent border, tinted translucent inside, top shine. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        int rad = Math.min(6, h / 2);
        roundRect(g, x - 2, y - 2, w + 4, h + 4, rad + 2, (accent & 0x00FFFFFF) | 0x16000000);
        roundRect(g, x, y, w, h, rad, (accent & 0x00FFFFFF) | 0xB0000000);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(1, rad - 1), tint(accent, 0.20f, 0xC4));
        g.fill(x + rad, y + 1, x + w - rad, y + 2, 0x22FFFFFF);
    }

    private static void spark(GuiGraphicsExtractor g, int x, int y, int w, int h, float[] hist, float max, int color) {
        int cw = Math.max(1, w / hist.length);
        for (int i = 0; i < hist.length; i++) {
            int bh = (int) Math.min(h - 1, Math.max(1, hist[i] / max * h));
            int yy = y + h - bh;
            g.fill(x + i * cw, yy, x + (i + 1) * cw, yy + 1, color);
            g.fill(x + i * cw, yy + 1, x + (i + 1) * cw, y + h, (color & 0x00FFFFFF) | 0x28000000);
        }
    }

    private static String fit(Font tr, String s, int max) {
        while (s.length() > 1 && tr.width(s) > max) s = s.substring(0, s.length() - 1);
        return s;
    }

    // ---------- main render ----------
    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        ClientConfig c = ClientConfig.get();
        Font tr = mc.font;
        frames++;
        long nowNs = System.nanoTime();
        if (nowNs - fpsStamp >= 1_000_000_000L) { fps = frames; frames = 0; fpsStamp = nowNs; }
        int W = g.guiWidth(), H = g.guiHeight();
        int accent = c.accent;
        int dim = (accent & 0x00FFFFFF) | 0x66000000;

        HudLayout.begin();
        ModHud.render(g, mc, c);

        // ---- top bar: [head] name | [monitor] fps | [cloud] mode | [clock] time ----
        if (c.showTopBar) {
            String mode = mc.getSingleplayerServer() != null ? "Singleplayer"
                    : (mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Multiplayer");
            String[] texts = { mc.getUser().getName(), fps + " FPS", mode, LocalTime.now().format(FMT) };
            String[][] icons = { Icons.HEAD, Icons.MONITOR, Icons.CLOUD, Icons.CLOCK };
            int pad = 11, gap = 6, sep = 16, h = 22;
            int total = pad * 2 + sep * 3;
            for (int i = 0; i < 4; i++) total += Icons.w(icons[i]) + gap + tr.width(texts[i]);
            int[] pos = HudLayout.place("topbar", total, h, (W - total) / 2, 5, W, H);
            int x = pos[0], y = pos[1], cx = x + pad, mid = y + h / 2;
            panel(g, x, y, total, h, accent);
            for (int i = 0; i < 4; i++) {
                int iy = mid - Icons.h(icons[i]) / 2;
                if (i == 0) Icons.head(g, cx, iy); else Icons.draw(g, icons[i], cx, iy, accent);
                cx += Icons.w(icons[i]) + gap;
                g.text(tr, texts[i], cx, mid - 4, 0xFFFFFFFF, true);
                cx += tr.width(texts[i]);
                if (i < 3) { g.fill(cx + sep / 2, y + 5, cx + sep / 2 + 1, y + h - 5, dim); cx += sep; }
            }
        }

        // ---- speed ----
        if (c.showSpeed) {
            int[] pos = HudLayout.place("speed", 96, 30, 8, 34, W, H);
            panel(g, pos[0], pos[1], 96, 30, accent);
            g.text(tr, String.format("Speed - %.1f b/s", speed), pos[0] + 7, pos[1] + 5, 0xFFFFFFFF, true);
            spark(g, pos[0] + 7, pos[1] + 16, 82, 9, speedHist, 12f, accent);
        }

        // ---- music card ----
        if (c.showMusic) {
            int mw = 176, mh = 46;
            int[] pos = HudLayout.place("music", mw, mh, (W - mw) / 2, 32, W, H);
            int mx = pos[0], my = pos[1];
            panel(g, mx, my, mw, mh, accent);
            roundRect(g, mx + 6, my + 7, 32, 32, 4, tint(accent, 0.55f, 0xE0));
            Icons.draw(g, Icons.NOTE, mx + 6 + 12, my + 7 + 12, 0xFFFFFFFF);
            g.text(tr, fit(tr, MusicPlayer.title(), mw - 56), mx + 44, my + 6, 0xFFFFFFFF, true);
            g.text(tr, fit(tr, MusicPlayer.artist(), mw - 56), mx + 44, my + 16, 0xFFB8B8B8, false);
            int mid = mx + 44 + (mw - 52) / 2;
            Icons.draw(g, Icons.PREV, mid - 18, my + 26, 0xFFFFFFFF);
            Icons.draw(g, MusicPlayer.isPlaying() ? Icons.PAUSE : Icons.PLAY, mid - 2, my + 26, 0xFFFFFFFF);
            Icons.draw(g, Icons.NEXT, mid + 12, my + 26, 0xFFFFFFFF);
            int bx = mx + 44, bw = mw - 52, by = my + 38;
            roundRect(g, bx, by, bw, 3, 1, (accent & 0x00FFFFFF) | 0x44000000);
            roundRect(g, bx, by, Math.max(2, (int) (bw * MusicPlayer.progress())), 3, 1, accent);
        }

        // ---- coordinates ----
        if (c.showCoords && mc.player != null) {
            String s = String.format("x %d   y %d   z %d", (int) Math.floor(mc.player.getX()),
                    (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ()));
            int w = tr.width(s) + 14 + 12, h = 16;
            int[] pos = HudLayout.place("coords", w, h, (W - w) / 2, 82, W, H);
            panel(g, pos[0], pos[1], w, h, accent);
            Icons.draw(g, Icons.ARROW, pos[0] + 7, pos[1] + 5, accent);
            g.text(tr, s, pos[0] + 19, pos[1] + 4, 0xFFFFFFFF, true);
        }

        // ---- RAM ----
        if (c.showMemory) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L, max = rt.maxMemory() / 1048576L;
            int[] pos = HudLayout.place("ram", 96, 30, W - 104, 34, W, H);
            panel(g, pos[0], pos[1], 96, 30, accent);
            g.text(tr, "RAM Usage - " + (used * 100 / max) + "%", pos[0] + 7, pos[1] + 5, 0xFFFFFFFF, true);
            spark(g, pos[0] + 7, pos[1] + 16, 82, 9, memHist, 1f, accent);
        }
    }
}
