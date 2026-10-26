package com.myclient;

import com.mojang.blaze3d.platform.InputConstants;
import com.myclient.gui.ClickGuiScreen;
import com.myclient.hud.HudRenderer;
import com.myclient.music.MusicPlayer;
import com.myclient.mods.ModTicker;
import com.myclient.sky.CursorRenderer;
import com.myclient.sky.CursorStyle;
import com.myclient.sky.SkyOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class MyClient implements ClientModInitializer {
    public static KeyMapping menuKey, zoomKey;

    @Override
    public void onInitializeClient() {
        ClientConfig.get();
        MusicPlayer.init(ClientConfig.get().musicVolume, ClientConfig.get().systemMusic);

        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.myclient.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC));

        zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.myclient.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            HudRenderer.tick(mc);
            ModTicker.tick(mc, zoomKey);
            while (menuKey.consumeClick()) {
                if (!ScreenUtil.screenOpen()) ScreenUtil.open(new ClickGuiScreen());
            }
        });

        // sky overlay first (under everything), HUD last (on top)
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("myclient", "sky"), (g, dt) -> {
            if (Minecraft.getInstance().level != null) SkyOverlay.render(g);
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("myclient", "hud"), (g, dt) -> {
            if (Minecraft.getInstance().level != null) HudRenderer.render(g);
        });

        // custom crosshair: replace the vanilla element, no mixin needed
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (g, dt) -> {
            ClientConfig c = ClientConfig.get();
            if (!c.customCrosshair) { original.extractRenderState(g, dt); return; }
            CursorStyle s = c.cursor == CursorStyle.ARROW ? CursorStyle.CROSS : c.cursor;
            CursorRenderer.draw(g, g.guiWidth() / 2, g.guiHeight() / 2, s, c.cursorColor, c.cursorSize);
        });
    }
}
