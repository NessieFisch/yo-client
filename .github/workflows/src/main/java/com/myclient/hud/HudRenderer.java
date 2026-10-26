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

    // ---------- shared helpers (ModHud uses these too) ----------
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        Gfx.round(g, x, y, w, h, r, color);
    }

    /** dark version of the accent colour, used for glass backgrounds */
    public static int tint(int accent, float k, int alpha) {
        int r = (int) (((accent >> 16) & 255) * k), gr = (int) (((accent >> 8) & 255) * k), b = (int) ((accent & 255) * k);
        return (alpha << 24) | (r << 16) | (gr << 8) | b;
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        panelR(g, x, y, w, h, Math.min(7, h / 2), accent);
    }

    /** Modern glass panel: soft glow, hairline border, gradient body, top shine. */
    public static void panelR(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int accent) {
        Gfx.shadow(g, x, y, w, h, r, accent, 0x2C, 0);
        Gfx.border(g, x, y, w, h, r, (accent & 0x00FFFFFF) | 0x66000000,
                tint(accent, 0.30f, 0xCC), tint(accent, 0.10f, 0xE0));
        Gfx.shine(g, x, y, w, r);
    }

    /** Smooth area graph. */
    private static void spark(GuiGraphicsExtractor g, int x, int y, int w, int h, float[] hist, float max, int color) {
        int top2 = y * 2, bot2 = (y + h) * 2;
        int prev = -1;
        for (int i = 0; i < w; i++) {
            float pos = i * (hist.length - 1) / (float) (w - 1);
            int i0 = (int) pos, i1 = Math.min(hist.length - 1, i0 + 1);
            float v = hist[i0] + (hist[i1] - hist[i0]) * (pos - i0);
            int yy = bot2 - Math.max(2, Math.min((h * 2) - 1, (int) (v / max * h * 2)));
            Gfx.gradient2(g, (x + i) * 2, yy, (x + i) * 2 + 2, bot2, Gfx.a(color, 0.40f), Gfx.a(color, 0f));
            int y1 = prev < 0 ? yy : Math.min(prev, yy), y2 = prev < 0 ? yy + 2 : Math.max(prev, yy) + 2;
            Gfx.fill2(g, (x + i) * 2, y1, (x + i) * 2 + 2, y2, color);
            prev = yy;
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
        int dim = (accent & 0x00FFFFFF) | 0x55000000;

        HudLayout.begin();
        ModHud.render(g, mc, c);

        // ---- top bar: pill with avatar | monitor fps | cloud mode | clock time ----
        if (c.showTopBar) {
            String name = mc.getUser().getName();
            String mode = mc.getSingleplayerServer() != null ? "Singleplayer"
                    : (mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Multiplayer");
            String[] texts = { name, fps + " FPS", mode, LocalTime.now().format(FMT) };
            int[] iw = { 14, 11, 12, 10 };
            int pad = 13, gap = 6, sep = 18, h = 24;
            int total = pad * 2 + sep * 3;
            for (int i = 0; i < 4; i++) total += iw[i] + gap + tr.width(texts[i]);
            int[] pos = HudLayout.place("topbar", total, h, (W - total) / 2, 5, W, H);
            int x = pos[0], y = pos[1], cx = x + pad, mid = y + h / 2;
            panelR(g, x, y, total, h, h / 2, accent);
            for (int i = 0; i < 4; i++) {
                switch (i) {
                    case 0 -> Icons.avatar(g, tr, cx, mid - 7, name, accent);
                    case 1 -> Icons.monitor(g, cx, mid - 4, accent);
                    case 2 -> Icons.cloud(g, cx, mid - 4, accent);
                    default -> Icons.clock(g, cx, mid - 5, accent);
                }
                cx += iw[i] + gap;
                g.text(tr, texts[i], cx, mid - 4, 0xFFFFFFFF, true);
                cx += tr.width(texts[i]);
                if (i < 3) { Gfx.rectF(g, cx + sep / 2f, y + 7, 0.5f, h - 14, dim); cx += sep; }
            }
        }

        // ---- speed ----
        if (c.showSpeed) {
            int[] pos = HudLayout.place("speed", 100, 32, 8, 34, W, H);
            panel(g, pos[0], pos[1], 100, 32, accent);
            g.text(tr, String.format("Speed - %.1f b/s", speed), pos[0] + 8, pos[1] + 5, 0xFFFFFFFF, true);
            spark(g, pos[0] + 8, pos[1] + 17, 84, 10, speedHist, 12f, accent);
        }

        // ---- music card ----
        if (c.showMusic) {
            int mw = 180, mh = 50;
            int[] pos = HudLayout.place("music", mw, mh, (W - mw) / 2, 33, W, H);
            int mx = pos[0], my = pos[1];
            panelR(g, mx, my, mw, mh, 9, accent);
            Gfx.roundV(g, mx + 7, my + 8, 34, 34, 8, tint(accent, 0.95f, 0xFF), tint(accent, 0.45f, 0xFF));
            Icons.note(g, mx + 7 + 13, my + 8 + 11, 0xFFFFFFFF);
            int tx = mx + 49, tw = mw - 57;
            g.text(tr, fit(tr, MusicPlayer.title(), tw), tx, my + 7, 0xFFFFFFFF, true);
            g.text(tr, fit(tr, MusicPlayer.artist(), tw), tx, my + 17, 0xFFB4B4BC, false);
            int mid = tx + tw / 2;
            Icons.prev(g, mid - 22, my + 28, 0xFFFFFFFF);
            if (MusicPlayer.isPlaying()) Icons.pause(g, mid - 3, my + 27, 0xFFFFFFFF); else Icons.play(g, mid - 3, my + 27, 0xFFFFFFFF);
            Icons.next(g, mid + 14, my + 28, 0xFFFFFFFF);
            int by = my + 41;
            Gfx.roundF(g, tx, by, tw, 3, 1.5f, (accent & 0x00FFFFFF) | 0x44000000);
            float prog = MusicPlayer.progress();
            Gfx.roundF(g, tx, by, Math.max(3, tw * prog), 3, 1.5f, accent);
            Gfx.circle(g, tx + Math.max(1.5f, tw * prog), by + 1.5f, 2.6f, 0xFFFFFFFF);
        }

        // ---- coordinates ----
        if (c.showCoords && mc.player != null) {
            String s = String.format("x %d   y %d   z %d", (int) Math.floor(mc.player.getX()),
                    (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ()));
            int w = tr.width(s) + 14 + 14, h = 17;
            int[] pos = HudLayout.place("coords", w, h, (W - w) / 2, 87, W, H);
            panelR(g, pos[0], pos[1], w, h, h / 2, accent);
            Icons.arrow(g, pos[0] + 9, pos[1] + 4, accent);
            g.text(tr, s, pos[0] + 21, pos[1] + 5, 0xFFFFFFFF, true);
        }

        // ---- RAM ----
        if (c.showMemory) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L, max = rt.maxMemory() / 1048576L;
            int[] pos = HudLayout.place("ram", 100, 32, W - 108, 34, W, H);
            panel(g, pos[0], pos[1], 100, 32, accent);
            g.text(tr, "RAM Usage - " + (used * 100 / max) + "%", pos[0] + 8, pos[1] + 5, 0xFFFFFFFF, true);
            spark(g, pos[0] + 8, pos[1] + 17, 84, 10, memHist, 1f, accent);
        }
    }
}
