package com.lootresp.config;

import com.lootresp.LootrChestESP;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class LootrChestESPConfig {
    private static final Path CONFIG_PATH = Paths.get("config", "lootrchestesp.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static boolean enabled = true;
    private static int scanRadius = 32;
    private static int scanInterval = 10;
    private static float lineWidth = 2.0f;
    private static boolean outlineEnabled = true;
    private static boolean filledFaces = true;
    private static float faceOpacity = 0.15f;
    private static int espColor = 0xFFFFAA00;
    private static boolean dirty;
    private static long saveAfterMillis;
    private static final long SAVE_DEBOUNCE_MILLIS = 350L;

    private LootrChestESPConfig() {}

    public static boolean isEnabled() { return enabled; }
    public static int getScanRadius() { return scanRadius; }
    public static int getScanInterval() { return scanInterval; }
    public static float getLineWidth() { return lineWidth; }
    public static boolean isOutlineEnabled() { return outlineEnabled; }
    public static boolean isFilledFaces() { return filledFaces; }
    public static float getFaceOpacity() { return faceOpacity; }
    public static int getEspColor() { return espColor; }

    public static void setEnabled(boolean value) { enabled = value; markDirty(); }
    public static void setScanRadius(int value) { scanRadius = Math.max(4, Math.min(128, value)); markDirty(); }
    public static void setScanInterval(int value) { scanInterval = Math.max(1, Math.min(100, value)); markDirty(); }
    public static void setLineWidth(float value) { lineWidth = Math.max(0.5f, Math.min(10.0f, value)); markDirty(); }
    public static void setOutlineEnabled(boolean value) { outlineEnabled = value; markDirty(); }
    public static void setFilledFaces(boolean value) { filledFaces = value; markDirty(); }
    public static void setFaceOpacity(float value) { faceOpacity = Math.max(0.0f, Math.min(1.0f, value)); markDirty(); }
    public static void setEspColor(int value) { espColor = 0xFF000000 | (value & 0xFFFFFF); markDirty(); }

    public static void toggle() {
        enabled = !enabled;
        markDirty();
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data != null) {
                if (data.enabled != null) enabled = data.enabled;
                scanRadius = clamp(data.scanRadius, 4, 128, 32);
                scanInterval = clamp(data.scanInterval, 1, 100, 10);
                lineWidth = clamp(data.lineWidth, 0.5f, 10.0f, 2.0f);
                if (data.outlineEnabled != null) outlineEnabled = data.outlineEnabled;
                if (data.filledFaces != null) filledFaces = data.filledFaces;
                faceOpacity = clamp(data.faceOpacity, 0.0f, 1.0f, 0.15f);
                if (data.espColor != null) espColor = 0xFF000000 | (data.espColor & 0xFFFFFF);
            }
        } catch (Exception e) {
            LootrChestESP.LOGGER.error("Failed to load Lootr Chest ESP config", e);
        }
    }

    private static synchronized void markDirty() {
        dirty = true;
        saveAfterMillis = System.currentTimeMillis() + SAVE_DEBOUNCE_MILLIS;
    }

    public static void flushIfDue() {
        if (dirty && System.currentTimeMillis() >= saveAfterMillis) save();
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(new ConfigData(enabled, scanRadius, scanInterval, lineWidth,
                        outlineEnabled, filledFaces, faceOpacity, espColor), writer);
            }
            dirty = false;
        } catch (Exception e) {
            LootrChestESP.LOGGER.error("Failed to save Lootr Chest ESP config", e);
        }
    }

    private static int clamp(int value, int min, int max, int fallback) {
        return value <= 0 ? fallback : Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max, float fallback) {
        return value < min || Float.isNaN(value) ? fallback : Math.min(max, value);
    }

    private static final class ConfigData {
        Boolean enabled;
        int scanRadius;
        int scanInterval;
        float lineWidth;
        Boolean outlineEnabled;
        Boolean filledFaces;
        float faceOpacity;
        Integer espColor;

        ConfigData(boolean enabled, int scanRadius, int scanInterval, float lineWidth,
                   boolean outlineEnabled, boolean filledFaces, float faceOpacity, int espColor) {
            this.enabled = enabled;
            this.scanRadius = scanRadius;
            this.scanInterval = scanInterval;
            this.lineWidth = lineWidth;
            this.outlineEnabled = outlineEnabled;
            this.filledFaces = filledFaces;
            this.faceOpacity = faceOpacity;
            this.espColor = espColor;
        }
    }
}
