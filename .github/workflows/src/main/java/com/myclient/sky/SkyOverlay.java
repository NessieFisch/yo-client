package com.myclient.sky;

import com.myclient.ClientConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Random;

public final class SkyOverlay {
    private static final int MAX = 800;
    private static final float[] px = new float[MAX], py = new float[MAX], spd = new float[MAX], sz = new float[MAX];
    private static long last = System.nanoTime();

    static {
        Random r = new Random(1337);
        for (int i = 0; i < MAX; i++) {
            px[i] = r.nextFloat(); py[i] = r.nextFloat();
            spd[i] = 0.5f + r.nextFloat(); sz[i] = 0.6f + r.nextFloat() * 0.9f;
        }
    }

    private static int scaleAlpha(int argb, float f) {
        int a = Math.min(255, Math.max(0, (int) (((argb >>> 24) & 0xFF) * f)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static void render(GuiGraphicsExtractor ctx) {
        ClientConfig c = ClientConfig.get();
        SkyEffect e = c.sky;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;
        if (e == SkyEffect.CLEAR) return;

        int w = ctx.guiWidth(), h = ctx.guiHeight();
        float t = now / 1_000_000_000f;

        // tint
        ctx.fillGradient(0, 0, w, h, scaleAlpha(e.tintTop, c.skyTint), scaleAlpha(e.tintBottom, c.skyTint));

        // stars (twinkling, upper half)
        if (e.stars) {
            for (int i = 0; i < 110; i++) {
                float fx = (i * 0.6180339f) % 1f, fy = (i * 0.7548776f) % 1f;
                int x = (int) (fx * w), y = (int) (fy * h * 0.55f);
                float tw = 0.45f + 0.55f * (float) Math.sin(t * 2f + i);
                ctx.fill(x, y, x + 1, y + 1, ((int) (255 * tw) << 24) | 0xFFFFFF);
            }
        }

        // particles
        if (e.count > 0) {
            int n = Math.min(MAX, (int) (e.count * c.skyIntensity));
            for (int i = 0; i < n; i++) {
                float sway = e.streak ? 0f : (float) Math.sin(t + i) * 0.02f;
                px[i] += (e.vx * spd[i] + sway) * dt;
                py[i] += e.vy * spd[i] * dt;
                if (px[i] > 1f) px[i] -= 1f; else if (px[i] < 0f) px[i] += 1f;
                if (py[i] > 1f) py[i] -= 1f; else if (py[i] < 0f) py[i] += 1f;

                int x = (int) (px[i] * w), y = (int) (py[i] * h);
                int s = Math.max(1, Math.round(e.size * sz[i]));
                int col = scaleAlpha(e.particle, 0.5f + 0.5f * spd[i] / 1.5f);
                if (e.streak) ctx.fill(x, y, x + s * 6, y + Math.max(1, s / 2), col);
                else ctx.fill(x, y, x + s, y + s, col);
            }
        }
    }
}
