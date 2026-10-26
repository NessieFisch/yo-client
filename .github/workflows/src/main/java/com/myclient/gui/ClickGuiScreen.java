package com.myclient.gui;

import com.myclient.ClientConfig;
import com.myclient.ScreenUtil;
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
    private static final int GRAY = 0xFF77777F;

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
    private boolean in(int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private String fit(String s, int max) {
        while (s.length() > 1 && font.width(s) > max) s = s.substring(0, s.length() - 1);
        return s;
    }

    private int ui() { return c().uiAccent; }

    private void toggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        HudRenderer.roundRect(g, x, y, 26, 12, 6, on ? ui() : 0xFF35353F);
        HudRenderer.roundRect(g, on ? x + 16 : x + 2, y + 2, 8, 8, 4, 0xFFFFFFFF);
    }

    private void button(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, boolean active, Runnable r) {
        boolean hover = in(x, y, w, h);
        int bg = active ? ((ui() & 0x00FFFFFF) | 0xDD000000) : hover ? 0xFF24242D : 0xFF1A1A22;
        HudRenderer.roundRect(g, x, y, w, h, 4, bg);
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
        hits.add(new Hit(x, y, w, h, d -> r.run(), false));
    }

    private void slider(GuiGraphicsExtractor g, String label, int x, int y, int w, float min, float max, float val, Consumer<Float> set) {
        g.text(font, label + ": " + String.format("%.2f", val), x, y, 0xFFFFFFFF, true);
        int ty = y + 13;
        HudRenderer.roundRect(g, x, ty, w, 4, 2, 0xFF2A2A33);
        int fw = (int) (w * (val - min) / (max - min));
        HudRenderer.roundRect(g, x, ty, Math.max(4, fw), 4, 2, ui());
        HudRenderer.roundRect(g, Math.max(x, x + fw - 4), ty - 2, 8, 8, 4, 0xFFFFFFFF);
        hits.add(new Hit(x, ty - 5, w, 14, d -> {
            float t = (float) Math.max(0, Math.min(1, (d - x) / w));
            set.accept(min + t * (max - min));
        }, true));
    }

    private void swatches(GuiGraphicsExtractor g, int x, int y, int current, Consumer<Integer> set) {
        for (int i = 0; i < COLORS.length; i++) {
            int sx = x + i * 24, col = COLORS[i];
            if (col == current) HudRenderer.roundRect(g, sx - 2, y - 2, 20, 20, 5, 0xFFFFFFFF);
            HudRenderer.roundRect(g, sx, y, 16, 16, 4, col);
            hits.add(new Hit(sx, y, 16, 16, d -> set.accept(col), false));
        }
    }

    private void drawRow(GuiGraphicsExtractor g, Feature f, int x, int y, int w) {
        boolean hover = in(x, y, w, 26);
        HudRenderer.roundRect(g, x, y, w, 26, 4, hover ? 0xFF1C1C25 : 0xFF15151B);
        g.text(font, f.name(), x + 10, y + 5, 0xFFFFFFFF, true);
        g.text(font, fit(f.desc(), w - 90), x + 10, y + 15, GRAY, false);
        if (f.action() != null) {
            HudRenderer.roundRect(g, x + w - 52, y + 5, 44, 16, 4, ui());
            g.text(font, "Open", x + w - 52 + (44 - font.width("Open")) / 2, y + 9, 0xFFFFFFFF, false);
            hits.add(new Hit(x, y, w, 26, d -> f.action().run(), false));
        } else {
            boolean on = f.get().get();
            toggle(g, x + w - 36, y + 7, on);
            hits.add(new Hit(x, y, w, 26, d -> f.set().accept(!on), false));
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
        for (int i = 0; i < rows; i++) drawRow(g, list.get(start + i), x, y + i * 28, rw);
        if (needArrows) {
            button(g, "^", x + w - 16, y, 16, 16, false, () -> scroll--);
            button(g, "v", x + w - 16, y + visible * 28 - 18, 16, 16, false, () -> scroll++);
        }
        if (list.isEmpty()) g.text(font, "Nothing found.", x, y + 4, GRAY, false);
        return y + Math.max(rows, 0) * 28;
    }

    // ---------- main render ----------
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        hits.clear();
        mx = mouseX; my = mouseY;
        ClientConfig c = c();
        int ui = c.uiAccent;
        pw = Math.min(440, width - 12);
        ph = Math.min(262, height - 8);
        x0 = (width - pw) / 2;
        y0 = (height - ph) / 2;

        g.fillGradient(0, 0, width, height, 0x55000000, 0x99000000);
        HudRenderer.roundRect(g, x0 - 1, y0 - 1, pw + 2, ph + 2, 7, (ui & 0x00FFFFFF) | 0x44000000);
        HudRenderer.roundRect(g, x0, y0, pw, ph, 6, 0xF2101015);
        HudRenderer.roundRect(g, x0, y0, SB, ph, 6, 0xF20B0B0F);
        g.fill(x0 + SB - 8, y0, x0 + SB, y0 + ph, 0xF20B0B0F);
        g.fill(x0 + SB, y0 + 8, x0 + SB + 1, y0 + ph - 8, 0x22FFFFFF);

        // logo
        HudRenderer.roundRect(g, x0 + 10, y0 + 10, 20, 20, 5, ui);
        Icons.draw(g, Icons.CROWN, x0 + 15, y0 + 17, 0xFFFFFFFF);
        g.text(font, "SPARED", x0 + 36, y0 + 12, 0xFFFFFFFF, true);
        g.text(font, "Client 26.2", x0 + 36, y0 + 22, GRAY, false);

        // search box
        HudRenderer.roundRect(g, x0 + 10, y0 + 38, SB - 20, 16, 4, searchFocus ? 0xFF22222B : 0xFF16161C);
        String shown = search.isEmpty() ? (searchFocus ? "" : "Search features...") : search;
        if (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0) shown += "_";
        g.text(font, fit(shown, SB - 32), x0 + 16, y0 + 42, search.isEmpty() && !searchFocus ? GRAY : 0xFFFFFFFF, false);
        hits.add(new Hit(x0 + 10, y0 + 38, SB - 20, 16, d -> searchFocus = true, false));

        // pages
        for (int i = 0; i < PAGES.length; i++) {
            final int idx = i;
            int ey = y0 + 62 + i * 24;
            boolean sel = search.isEmpty() && page == i;
            if (sel) {
                HudRenderer.roundRect(g, x0 + 6, ey, SB - 12, 22, 4, (ui & 0x00FFFFFF) | 0x30000000);
                g.fill(x0 + 6, ey + 4, x0 + 8, ey + 18, ui);
            } else if (in(x0 + 6, ey, SB - 12, 22)) {
                HudRenderer.roundRect(g, x0 + 6, ey, SB - 12, 22, 4, 0x18FFFFFF);
            }
            g.text(font, PAGES[i][0], x0 + 16, ey + 3, sel ? 0xFFFFFFFF : 0xFFBBBBC4, false);
            g.text(font, PAGES[i][1], x0 + 16, ey + 12, 0xFF5E5E66, false);
            hits.add(new Hit(x0 + 6, ey, SB - 12, 22, d -> { page = idx; search = ""; searchFocus = false; scroll = 0; }, false));
        }

        // header
        int cx = x0 + SB + 14, cw = pw - SB - 28, cy = y0 + 44;
        boolean searching = !search.isEmpty();
        g.text(font, searching ? "Search" : PAGES[page][0], cx, y0 + 12, 0xFFFFFFFF, true);
        g.text(font, searching ? "Results for \"" + search + "\"" : PAGES[page][1], cx, y0 + 23, GRAY, false);
        g.fill(cx, y0 + 36, cx + cw, y0 + 37, 0x22FFFFFF);

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

        g.text(font, "Right Shift to close", cx, y0 + ph - 12, 0xFF55555C, false);
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
        g.text(font, "Rebind zoom in Options > Controls", x, ny + 40, GRAY, false);
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
        g.text(font, "Preview:", x, ny + 10, GRAY, false);
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
            g.text(font, "and play a song. |< Play >| are your media keys.", x, y + 58, GRAY, false);
            g.text(font, "Volume: use the browser. Windows only.", x, y + 70, GRAY, false);
            return;
        }
        g.text(font, "Put .mp3 / .wav in .minecraft/myclient_music, then Rescan", x, y + 26, GRAY, false);
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
        g.text(font, "Config: .minecraft/config/myclient.json", x, y + 30, GRAY, false);
        g.text(font, "Music: .minecraft/myclient_music", x, y + 42, GRAY, false);
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
