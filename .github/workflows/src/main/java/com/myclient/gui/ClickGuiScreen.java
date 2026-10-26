package com.myclient.gui;

import com.myclient.ClientConfig;
import com.myclient.ScreenUtil;
import com.myclient.hud.Gfx;
import com.myclient.hud.HudRenderer;
import com.myclient.hud.Icons;
import com.myclient.music.MusicPlayer;
import com.myclient.sky.CursorRenderer;
import com.myclient.sky.CursorStyle;
import com.myclient.sky.SkyEffect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/** Sidebar menu: pages on the left, content on the right, search box on top. */
public class ClickGuiScreen extends Screen {
    private static final int SB = 108;
    private static final String[][] PAGES = {
            { "HUD", "Overlays" }, { "Visuals", "Sky effects" }, { "Utility", "Quality of life" },
            { "Cursor", "Pointer & aim" }, { "Music", "Local & YT" }, { "Themes", "Colors" }, { "Settings", "Client options" } };
    private static final int[] COLORS = {
            0xFF8B6CFF, 0xFFE8B931, 0xFFFFFFFF, 0xFFFF5555, 0xFF55FF88, 0xFF55AAFF, 0xFFFF77CC, 0xFF00E5E5 };

    private record Hit(int x, int y, int w, int h, DoubleConsumer fn, boolean slider) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private record Feature(String cat, String name, String desc, Supplier<Boolean> get, Consumer<Boolean> set, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<Feature> features = new ArrayList<>();
    private Hit dragging;
    private static int page = 0;
    private String search = "";
    private boolean searchFocus;
    private int scroll, mx, my, x0, y0, pw, ph;
    private final HashMap<String, Float> anims = new HashMap<>();
    private long lastNs = System.nanoTime(), openNs = System.nanoTime();
    private float dt;

    private float anim(String key, float target) {
        float v = anims.getOrDefault(key, target);
        v += (target - v) * Math.min(1f, dt * 16f);
        if (Math.abs(target - v) < 0.003f) v = target;
        anims.put(key, v);
        return v;
    }

    public ClickGuiScreen() {
        super(Component.literal("Client"));
        buildFeatures();
    }

    private static ClientConfig c() { return ClientConfig.get(); }

    private void add(String cat, String name, String desc, Supplier<Boolean> g, Consumer<Boolean> s) {
        features.add(new Feature(cat, name, desc, g, s, null));
    }

    private void action(String cat, String name, String desc, Runnable r) {
        features.add(new Feature(cat, name, desc, null, null, r));
    }

    private void buildFeatures() {
        action("HUD", "Edit layout", "Drag every HUD element where you want it", () -> ScreenUtil.open(new HudEditorScreen()));
        action("HUD", "Reset layout", "Put all elements back to their default spot", () -> c().hudPos.clear());
        add("HUD", "Top bar", "Name, FPS, mode and clock", () -> c().showTopBar, v -> c().showTopBar = v);
        add("HUD", "Speed", "Blocks per second with a graph", () -> c().showSpeed, v -> c().showSpeed = v);
        add("HUD", "Music card", "Now playing with controls", () -> c().showMusic, v -> c().showMusic = v);
        add("HUD", "Coordinates", "Your position", () -> c().showCoords, v -> c().showCoords = v);
        add("HUD", "RAM usage", "Memory with a graph", () -> c().showMemory, v -> c().showMemory = v);
        add("HUD", "Keystrokes", "WASD, mouse buttons and space", () -> c().showKeys, v -> c().showKeys = v);
        add("HUD", "CPS counter", "Clicks per second", () -> c().showCps, v -> c().showCps = v);
        add("HUD", "Ping", "Latency on servers", () -> c().showPing, v -> c().showPing = v);
        add("HUD", "Reach", "Distance of your last entity hit", () -> c().showReach, v -> c().showReach = v);
        add("HUD", "Armor status", "Durability as text", () -> c().showArmor, v -> c().showArmor = v);
        add("HUD", "Armor icons", "Armor with icons by the hotbar", () -> c().showArmorIcons, v -> c().showArmorIcons = v);
        add("HUD", "Potion timer", "Effects with countdown", () -> c().showPotions, v -> c().showPotions = v);
        add("HUD", "Direction", "Facing and angle", () -> c().showDirection, v -> c().showDirection = v);
        add("HUD", "Health / Food", "Health and hunger numbers", () -> c().showStats, v -> c().showStats = v);
        add("HUD", "Saturation", "Hidden saturation value (AppleSkin style)", () -> c().showSaturation, v -> c().showSaturation = v);
        add("HUD", "Held item", "Item in hand and its durability", () -> c().showHeld, v -> c().showHeld = v);
        add("HUD", "Item counter", "Gapples, pearls, totems, arrows", () -> c().showItems, v -> c().showItems = v);
        add("HUD", "Session timer", "How long you have played", () -> c().showSession, v -> c().showSession = v);
        add("HUD", "Sprint status", "Sprinting / sneaking / walking", () -> c().showStatus, v -> c().showStatus = v);
        add("Utility", "Toggle sprint", "Sprint automatically while moving forward", () -> c().toggleSprint, v -> c().toggleSprint = v);
        add("Utility", "Fullbright", "See in the dark (client side)", () -> c().fullbright, v -> c().fullbright = v);
        add("Utility", "No hurt cam", "No camera shake when you take damage", () -> c().noHurtCam, v -> c().noHurtCam = v);
        add("Cursor", "Custom crosshair", "Replace the in-game crosshair", () -> c().customCrosshair, v -> c().customCrosshair = v);
    }

    // ---------- lifecycle ----------
    @Override
    protected void init() {
        openNs = System.nanoTime();
        GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    @Override
    public void removed() {
        GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        ClientConfig.get().save();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---------- small helpers ----------
    private static final int TXT = 0xFFEDEDF4, SUB = 0xFF84848F;

    private boolean in(int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private String fit(String s, int max) {
        while (s.length() > 1 && font.width(s) > max) s = s.substring(0, s.length() - 1);
        return s;
    }

    private int ui() { return c().uiAccent; }

    private void toggle(GuiGraphicsExtractor g, int x, int y, boolean on, String key) {
        float t = anim("tg" + key, on ? 1f : 0f);
        int off = 0xFF2C2C36;
        Gfx.roundV(g, x, y, 28, 14, 7, Gfx.lerp(off, Gfx.lerp(ui(), 0xFFFFFFFF, 0.2f), t), Gfx.lerp(off, ui(), t));
        Gfx.circle(g, x + 7 + 14 * t, y + 7.4f, 5.2f, 0x33000000);
        Gfx.circle(g, x + 7 + 14 * t, y + 7, 5f, 0xFFFFFFFF);
    }

    private void button(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, boolean active, Runnable r) {
        float hv = anim("bt" + x + "," + y + "," + page, in(x, y, w, h) ? 1f : 0f);
        int top = active ? Gfx.lerp(ui(), 0xFFFFFFFF, 0.18f) : Gfx.lerp(0xFF1B1B23, 0xFF2A2A35, hv);
        int bot = active ? ui() : Gfx.lerp(0xFF16161C, 0xFF22222B, hv);
        Gfx.border(g, x, y, w, h, 7, active ? 0x00000000 : 0x16FFFFFF, top, bot);
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
        hits.add(new Hit(x, y, w, h, d -> r.run(), false));
    }

    private void slider(GuiGraphicsExtractor g, String label, int x, int y, int w, float min, float max, float val, Consumer<Float> set) {
        g.text(font, label, x, y, TXT, false);
        String v = String.format("%.2f", val);
        g.text(font, v, x + w - font.width(v), y, SUB, false);
        int ty = y + 14;
        Gfx.roundF(g, x, ty, w, 4, 2, 0xFF262630);
        float fw = w * (val - min) / (max - min);
        Gfx.roundV(g, x, ty, Math.max(4, (int) fw), 4, 2, Gfx.lerp(ui(), 0xFFFFFFFF, 0.25f), ui());
        Gfx.circle(g, x + Math.max(2f, fw), ty + 2.6f, 6f, 0x33000000);
        Gfx.circle(g, x + Math.max(2f, fw), ty + 2f, 5.2f, 0xFFFFFFFF);
        hits.add(new Hit(x, ty - 6, w, 16, d -> {
            float t = (float) Math.max(0, Math.min(1, (d - x) / w));
            set.accept(min + t * (max - min));
        }, true));
    }

    private void swatches(GuiGraphicsExtractor g, int x, int y, int current, Consumer<Integer> set) {
        for (int i = 0; i < COLORS.length; i++) {
            int sx = x + i * 26, col = COLORS[i];
            if (col == current) Gfx.circle(g, sx + 9, y + 9, 10.5f, 0xFFFFFFFF);
            Gfx.circle(g, sx + 9, y + 9, col == current ? 8.8f : 9f, col == current ? 0xFF101015 : col);
            if (col == current) Gfx.circle(g, sx + 9, y + 9, 6.8f, col);
            hits.add(new Hit(sx, y, 18, 18, d -> set.accept(col), false));
        }
    }

    private void drawRow(GuiGraphicsExtractor g, Feature f, int x, int y, int w) {
        float hv = anim("row" + f.name() + page, in(x, y, w, 28) ? 1f : 0f);
        Gfx.border(g, x, y, w, 28, 8, 0x14FFFFFF, Gfx.lerp(0xFF17171E, 0xFF20202A, hv), Gfx.lerp(0xFF131319, 0xFF1A1A23, hv));
        g.text(font, f.name(), x + 12, y + 6, TXT, false);
        g.text(font, fit(f.desc(), w - 100), x + 12, y + 16, SUB, false);
        if (f.action() != null) {
            Gfx.roundV(g, x + w - 56, y + 6, 46, 16, 8, Gfx.lerp(ui(), 0xFFFFFFFF, 0.2f), ui());
            g.text(font, "Open", x + w - 56 + (46 - font.width("Open")) / 2, y + 10, 0xFFFFFFFF, false);
            hits.add(new Hit(x, y, w, 28, d -> f.action().run(), false));
        } else {
            boolean on = f.get().get();
            toggle(g, x + w - 42, y + 7, on, f.name());
            hits.add(new Hit(x, y, w, 28, d -> f.set().accept(!on), false));
        }
    }

    /** Draws the feature rows of a category (or a search). Returns the y below the last row. */
    private int featureRows(GuiGraphicsExtractor g, int x, int y, int w, String cat, String q, int visible, boolean scrollable) {
        List<Feature> list = new ArrayList<>();
        for (Feature f : features) {
            if (cat != null && !f.cat().equals(cat)) continue;
            if (q != null && !q.isEmpty() && !(f.name().toLowerCase().contains(q) || f.desc().toLowerCase().contains(q))) continue;
            list.add(f);
        }
        int start = 0;
        if (scrollable) {
            start = Math.max(0, Math.min(scroll, Math.max(0, list.size() - visible)));
            scroll = start;
        }
        boolean needArrows = scrollable && list.size() > visible;
        int rw = w - (needArrows ? 20 : 0);
        int rows = Math.min(visible, list.size() - start);
        for (int i = 0; i < rows; i++) drawRow(g, list.get(start + i), x, y + i * 31, rw);
        if (needArrows) {
            button(g, "^", x + w - 16, y, 16, 16, false, () -> scroll--);
            button(g, "v", x + w - 16, y + visible * 31 - 19, 16, 16, false, () -> scroll++);
        }
        if (list.isEmpty()) g.text(font, "Nothing found.", x, y + 4, SUB, false);
        return y + Math.max(rows, 0) * 31;
    }

    // ---------- main render ----------
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        hits.clear();
        mx = mouseX; my = mouseY;
        long now = System.nanoTime();
        dt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;
        float open = Math.min(1f, (now - openNs) / 200_000_000f);
        float ease = 1f - (1f - open) * (1f - open) * (1f - open);

        ClientConfig c = c();
        int ui = c.uiAccent;
        pw = Math.min(440, width - 12);
        ph = Math.min(262, height - 8);
        x0 = (width - pw) / 2;
        y0 = (height - ph) / 2 + (int) ((1f - ease) * 14f);

        g.fillGradient(0, 0, width, height, Gfx.a(0x55000000, ease), Gfx.a(0xAA000000, ease));
        Gfx.shadow(g, x0, y0, pw, ph, 12, 0x000000, 0x70, 6);
        // 1px hairline outline, sidebar, content (content is squared on its left side)
        Gfx.roundV2(g, x0 * 2, y0 * 2, pw * 2, ph * 2, 24, 0xFF2B2B36, 0xFF1B1B23);
        Gfx.roundV2(g, x0 * 2 + 1, y0 * 2 + 1, pw * 2 - 2, ph * 2 - 2, 23, 0xFF0D0D12, 0xFF0A0A0E);
        Gfx.roundV2(g, (x0 + SB) * 2, y0 * 2 + 1, (pw - SB) * 2 - 1, ph * 2 - 2, 23, 0xFF14141B, 0xFF0F0F15);
        Gfx.fill2(g, (x0 + SB) * 2, y0 * 2 + 1, (x0 + SB + 14) * 2, (y0 + 14) * 2, 0xFF14141B);
        Gfx.fill2(g, (x0 + SB) * 2, (y0 + ph - 14) * 2, (x0 + SB + 14) * 2, y0 * 2 + ph * 2 - 1, 0xFF0F0F15);
        Gfx.fill2(g, (x0 + SB) * 2, (y0 + 12) * 2, (x0 + SB) * 2 + 1, (y0 + ph - 12) * 2, 0x14FFFFFF);

        // logo
        Gfx.roundV(g, x0 + 12, y0 + 11, 22, 22, 7, Gfx.lerp(ui, 0xFFFFFFFF, 0.3f), ui);
        g.text(font, "S", x0 + 12 + (22 - font.width("S")) / 2, y0 + 18, 0xFFFFFFFF, true);
        g.text(font, "Spared", x0 + 41, y0 + 13, TXT, true);
        g.text(font, "Client  26.2", x0 + 41, y0 + 23, SUB, false);

        // search box
        Gfx.border(g, x0 + 10, y0 + 40, SB - 20, 18, 9, searchFocus ? Gfx.a(ui, 0.75f) : 0x14FFFFFF,
                searchFocus ? 0xFF1B1B24 : 0xFF14141B, searchFocus ? 0xFF16161D : 0xFF111116);
        String shown = search.isEmpty() ? (searchFocus ? "" : "Search...") : search;
        if (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0) shown += "_";
        g.text(font, fit(shown, SB - 38), x0 + 20, y0 + 45, search.isEmpty() && !searchFocus ? SUB : TXT, false);
        hits.add(new Hit(x0 + 10, y0 + 40, SB - 20, 18, d -> searchFocus = true, false));

        // pages
        for (int i = 0; i < PAGES.length; i++) {
            final int idx = i;
            int ey = y0 + 66 + i * 25;
            boolean sel = search.isEmpty() && page == i;
            float sv = anim("pg" + i, sel ? 1f : 0f);
            float hv = anim("ph" + i, in(x0 + 6, ey, SB - 12, 23) ? 1f : 0f);
            if (sv > 0.01f) {
                Gfx.roundV(g, x0 + 7, ey, SB - 14, 23, 8, Gfx.a(ui, 0.30f * sv), Gfx.a(ui, 0.12f * sv));
                Gfx.roundF(g, x0 + 7, ey + 5, 2.2f, 13, 1.1f, Gfx.a(ui, sv));
            } else if (hv > 0.01f) {
                Gfx.round(g, x0 + 7, ey, SB - 14, 23, 8, Gfx.a(0xFFFFFFFF, 0.06f * hv));
            }
            g.text(font, PAGES[i][0], x0 + 17, ey + 3, sel ? 0xFFFFFFFF : 0xFFC0C0CA, false);
            g.text(font, PAGES[i][1], x0 + 17, ey + 13, 0xFF5C5C66, false);
            hits.add(new Hit(x0 + 6, ey, SB - 12, 23, d -> { page = idx; search = ""; searchFocus = false; scroll = 0; }, false));
        }

        // header
        int cx = x0 + SB + 14, cw = pw - SB - 28, cy = y0 + 44;
        boolean searching = !search.isEmpty();
        g.text(font, searching ? "Search" : PAGES[page][0], cx, y0 + 12, 0xFFFFFFFF, true);
        g.text(font, searching ? "Results for \"" + search + "\"" : PAGES[page][1], cx, y0 + 23, SUB, false);
        Gfx.fill2(g, cx * 2, (y0 + 36) * 2, (cx + cw) * 2, (y0 + 36) * 2 + 1, 0x1AFFFFFF);

        if (searching) featureRows(g, cx, cy, cw, null, search.toLowerCase(), 6, true);
        else switch (page) {
            case 0 -> featureRows(g, cx, cy, cw, "HUD", null, 6, true);
            case 1 -> visualsPage(g, cx, cy, cw);
            case 2 -> utilityPage(g, cx, cy, cw);
            case 3 -> cursorPage(g, cx, cy, cw);
            case 4 -> musicPage(g, cx, cy, cw);
            case 5 -> themesPage(g, cx, cy, cw);
            case 6 -> settingsPage(g, cx, cy, cw);
        }

        g.text(font, "Right Shift to close", cx, y0 + ph - 13, 0xFF4E4E58, false);
        CursorRenderer.draw(g, mouseX, mouseY, c.cursor, c.cursorColor, c.cursorSize);
    }

    // ---------- pages ----------
    private void visualsPage(GuiGraphicsExtractor g, int x, int y, int w) {
        ClientConfig c = c();
        SkyEffect[] all = SkyEffect.values();
        int bw = (w - 6) / 2;
        for (int i = 0; i < all.length; i++) {
            SkyEffect e = all[i];
            button(g, e.label, x + (i % 2) * (bw + 6), y + (i / 2) * 24, bw, 20, c.sky == e, () -> c().sky = e);
        }
        int sy = y + ((all.length + 1) / 2) * 24 + 8;
        slider(g, "Intensity", x, sy, w, 0.2f, 2f, c.skyIntensity, v -> c().skyIntensity = v);
        slider(g, "Tint strength", x, sy + 30, w, 0f, 1.5f, c.skyTint, v -> c().skyTint = v);
    }

    private void utilityPage(GuiGraphicsExtractor g, int x, int y, int w) {
        int ny = featureRows(g, x, y, w, "Utility", null, 6, false);
        slider(g, "Zoom FOV (hold C)", x, ny + 10, w, 30f, 90f, c().zoomFov, v -> c().zoomFov = v);
        g.text(font, "Rebind zoom in Options > Controls", x, ny + 40, SUB, false);
    }

    private void cursorPage(GuiGraphicsExtractor g, int x, int y, int w) {
        ClientConfig c = c();
        g.text(font, "Style", x, y, 0xFFFFFFFF, true);
        CursorStyle[] all = CursorStyle.values();
        int bw = (w - 3 * 6) / 4;
        for (int i = 0; i < all.length; i++) {
            CursorStyle s = all[i];
            button(g, s.label, x + i * (bw + 6), y + 12, bw, 20, c.cursor == s, () -> c().cursor = s);
        }
        g.text(font, "Color", x, y + 40, 0xFFFFFFFF, true);
        swatches(g, x, y + 52, c.cursorColor, col -> c().cursorColor = col);
        slider(g, "Size", x, y + 80, w, 0.5f, 3f, c.cursorSize, v -> c().cursorSize = v);
        int ny = featureRows(g, x, y + 112, w, "Cursor", null, 2, false);
        g.text(font, "Preview:", x, ny + 10, SUB, false);
        CursorRenderer.draw(g, x + 60, ny + 14, c.cursor, c.cursorColor, c.cursorSize);
    }

    private void musicPage(GuiGraphicsExtractor g, int x, int y, int w) {
        ClientConfig c = c();
        button(g, "|<", x, y, 40, 20, false, MusicPlayer::prev);
        button(g, MusicPlayer.isPlaying() ? "Pause" : "Play", x + 46, y, 64, 20, MusicPlayer.isPlaying(), MusicPlayer::toggle);
        button(g, ">|", x + 116, y, 40, 20, false, MusicPlayer::next);
        button(g, "Rescan", x + 162, y, 56, 20, false, () -> { MusicPlayer.rescan(); scroll = 0; });
        button(g, c.systemMusic ? "YT Music" : "Local", x + 224, y, Math.min(76, w - 224), 20, c.systemMusic, () -> {
            c().systemMusic = !c().systemMusic;
            MusicPlayer.setSystem(c().systemMusic);
        });
        if (c.systemMusic) {
            g.text(font, "Shows what plays on your PC: YouTube Music in", x, y + 34, 0xFFFFFFFF, true);
            g.text(font, "Chrome/Edge, Spotify, etc. Open music.youtube.com", x, y + 46, 0xFFFFFFFF, true);
            g.text(font, "and play a song. |< Play >| are your media keys.", x, y + 58, SUB, false);
            g.text(font, "Volume: use the browser. Windows only.", x, y + 70, SUB, false);
            return;
        }
        g.text(font, "Put .mp3 / .wav in .minecraft/myclient_music, then Rescan", x, y + 26, SUB, false);
        slider(g, "Volume", x, y + 40, w, 0f, 1f, c.musicVolume, v -> { c().musicVolume = v; MusicPlayer.setVolume(v); });
        List<String> names = MusicPlayer.names();
        if (names.isEmpty()) {
            g.text(font, "No songs found yet.", x, y + 78, 0xFFFFFFFF, true);
            return;
        }
        int start = Math.max(0, Math.min(scroll, Math.max(0, names.size() - 5)));
        scroll = start;
        for (int i = 0; i < 5; i++) {
            final int k = start + i;
            if (k >= names.size()) break;
            button(g, fit(names.get(k), w - 50), x, y + 70 + i * 22, w - 24, 20, k == MusicPlayer.currentIndex(), () -> MusicPlayer.play(k));
        }
        button(g, "^", x + w - 20, y + 70, 20, 20, false, () -> scroll--);
        button(g, "v", x + w - 20, y + 94, 20, 20, false, () -> scroll++);
    }

    private void themesPage(GuiGraphicsExtractor g, int x, int y, int w) {
        ClientConfig c = c();
        g.text(font, "HUD color (gold matches the screenshot)", x, y, 0xFFFFFFFF, true);
        swatches(g, x, y + 14, c.accent, col -> c().accent = col);
        g.text(font, "Menu color", x, y + 46, 0xFFFFFFFF, true);
        swatches(g, x, y + 60, c.uiAccent, col -> c().uiAccent = col);
    }

    private void settingsPage(GuiGraphicsExtractor g, int x, int y, int w) {
        g.text(font, "Right Shift  -  open this menu", x, y, 0xFFFFFFFF, true);
        g.text(font, "C (hold)  -  zoom", x, y + 12, 0xFFFFFFFF, true);
        g.text(font, "Config: .minecraft/config/myclient.json", x, y + 30, SUB, false);
        g.text(font, "Music: .minecraft/myclient_music", x, y + 42, SUB, false);
        button(g, "Reset all settings", x, y + 64, 140, 20, false, ClientConfig::reset);
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
        double mx = e.x(), my = e.y();
        searchFocus = false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.in(mx, my)) {
                h.fn().accept(mx);
                if (h.slider()) dragging = h;
                return true;
            }
        }
        return super.mouseClicked(e, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging != null) { dragging.fn().accept(e.x()); return true; }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragging = null;
        return super.mouseReleased(e);
    }

    // no @Override on purpose: if the signature differs in a version this is simply unused
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        scroll -= (int) Math.signum(sy);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (searchFocus) {
            if (k == 259 && !search.isEmpty()) search = search.substring(0, search.length() - 1);
            else if (k == 32 && search.length() < 24) search += " ";
            else if (k >= 65 && k <= 90 && search.length() < 24) search += (char) (k + 32);
            else if (k >= 48 && k <= 57 && search.length() < 24) search += (char) k;
            else if (k == 256 || k == 257) searchFocus = false;
            scroll = 0;
            return true;
        }
        if (k == GLFW.GLFW_KEY_RIGHT_SHIFT) { onClose(); return true; }
        return super.keyPressed(e);
    }
}
