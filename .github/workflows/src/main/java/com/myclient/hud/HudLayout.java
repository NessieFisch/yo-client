package com.myclient.hud;

import com.myclient.ClientConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/** Remembers where every HUD element is, and applies the positions the player dragged them to. */
public final class HudLayout {
    public record Rect(int x, int y, int w, int h) {}

    /** true while the layout editor is open, so empty elements show sample content. */
    public static boolean editing;

    public static final Map<String, Rect> rects = new LinkedHashMap<>();

    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    static {
        NAMES.put("topbar", "Top bar");   NAMES.put("speed", "Speed");        NAMES.put("music", "Music");
        NAMES.put("coords", "Coordinates"); NAMES.put("ram", "RAM");          NAMES.put("keys", "Keystrokes");
        NAMES.put("cps", "CPS");          NAMES.put("ping", "Ping");          NAMES.put("armor", "Armor");
        NAMES.put("potions", "Potions");  NAMES.put("direction", "Direction"); NAMES.put("stats", "Health / Food");
        NAMES.put("held", "Held item");   NAMES.put("items", "Item counter"); NAMES.put("session", "Session");
        NAMES.put("status", "Sprint status");
        NAMES.put("armoricons", "Armor icons"); NAMES.put("saturation", "Saturation"); NAMES.put("reach", "Reach");
    }

    public static String name(String id) { return NAMES.getOrDefault(id, id); }

    public static void begin() { rects.clear(); }

    /** Returns the final {x, y} of an element: the dragged position if set, else the default. */
    public static int[] place(String id, int w, int h, int defX, int defY, int W, int H) {
        float[] p = ClientConfig.get().hudPos.get(id);
        int x = defX, y = defY;
        if (p != null && p.length >= 2) { x = Math.round(p[0] * W); y = Math.round(p[1] * H); }
        x = Math.max(0, Math.min(Math.max(0, W - w), x));
        y = Math.max(0, Math.min(Math.max(0, H - h), y));
        rects.put(id, new Rect(x, y, w, h));
        return new int[] { x, y };
    }
}
