package com.myclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.myclient.sky.CursorStyle;
import com.myclient.sky.SkyEffect;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("myclient.json");
    private static ClientConfig instance;

    // Sky
    public SkyEffect sky = SkyEffect.CLEAR;
    public float skyIntensity = 1.0f;   // particle amount multiplier
    public float skyTint = 1.0f;        // tint strength multiplier

    // Cursor / crosshair
    public CursorStyle cursor = CursorStyle.RING;
    public int cursorColor = 0xFFFFD84A;
    public float cursorSize = 1.0f;
    public boolean customCrosshair = true;

    // HUD
    public int accent = 0xFFE8B931;
    public boolean showTopBar = true, showSpeed = true, showMusic = true, showCoords = true, showMemory = true;
    public boolean showKeys = true, showCps = true, showArmor = true, showPotions = true, showPing = true;
    public boolean showDirection = false, showStats = false, showHeld = false, showItems = false, showSession = false, showStatus = false;
    public java.util.Map<String, float[]> hudPos = new java.util.HashMap<>();
    public boolean showArmorIcons = false, showSaturation = false, showReach = false, noHurtCam = false;
    public int uiAccent = 0xFF8B6CFF;
    public boolean toggleSprint = false, fullbright = false;
    public float zoomFov = 30f;
    public boolean systemMusic = false;
    public float musicVolume = 0.5f;
    public String trackTitle = "MCDOLLAR";
    public String trackArtist = "Lil Saper";

    public static void reset() { instance = new ClientConfig(); instance.save(); }

    public static ClientConfig get() {
        if (instance == null) {
            try {
                if (Files.exists(FILE)) instance = GSON.fromJson(Files.readString(FILE), ClientConfig.class);
            } catch (Exception ignored) {}
            if (instance == null) instance = new ClientConfig();
        }
        return instance;
    }

    public void save() {
        try { Files.writeString(FILE, GSON.toJson(this)); } catch (Exception e) { e.printStackTrace(); }
    }
}
