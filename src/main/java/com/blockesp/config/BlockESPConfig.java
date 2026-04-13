package com.blockesp.config;

import com.blockesp.BlockESP;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the list of blocks to highlight and their colors.
 * Saves/loads from config/blockesp.json
 */
public class BlockESPConfig {

    private static final Path CONFIG_PATH = Paths.get("config", "blockesp.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Map of block registry name -> ARGB color (e.g., "minecraft:diamond_ore" -> 0xFF00FFFF)
    private static final Map<String, Integer> trackedBlocks = new ConcurrentHashMap<>();

    // Global toggle
    private static boolean enabled = true;

    // Render distance in blocks
    private static int scanRadius = 32;

    // Scan interval in ticks (lower = more frequent, more CPU)
    private static int scanInterval = 10;

    // Line thickness
    private static float lineWidth = 2.0f;

    // Whether to render filled faces or just outlines
    private static boolean filledFaces = true;

    // Face opacity (0.0 - 1.0)
    private static float faceOpacity = 0.15f;

    // ========== Getters / Setters ==========

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean val) { enabled = val; }
    public static void toggle() { enabled = !enabled; }

    public static int getScanRadius() { return scanRadius; }
    public static void setScanRadius(int r) { scanRadius = Math.max(4, Math.min(128, r)); save(); }

    public static int getScanInterval() { return scanInterval; }
    public static void setScanInterval(int t) { scanInterval = Math.max(1, Math.min(100, t)); save(); }

    public static float getLineWidth() { return lineWidth; }
    public static void setLineWidth(float w) { lineWidth = Math.max(0.5f, Math.min(10f, w)); save(); }

    public static boolean isFilledFaces() { return filledFaces; }
    public static void setFilledFaces(boolean val) { filledFaces = val; save(); }

    public static float getFaceOpacity() { return faceOpacity; }
    public static void setFaceOpacity(float o) { faceOpacity = Math.max(0f, Math.min(1f, o)); save(); }

    public static Map<String, Integer> getTrackedBlocks() {
        return trackedBlocks;
    }

    // ========== Block Management ==========

    public static void addBlock(String blockId, int color) {
        trackedBlocks.put(blockId, color);
        save();
    }

    public static void removeBlock(String blockId) {
        trackedBlocks.remove(blockId);
        save();
    }

    public static boolean isTracked(ResourceLocation blockId) {
        return trackedBlocks.containsKey(blockId.toString());
    }

    public static int getColor(ResourceLocation blockId) {
        return trackedBlocks.getOrDefault(blockId.toString(), 0xFFFFFFFF);
    }

    public static void clearAll() {
        trackedBlocks.clear();
        save();
    }

    // ========== Preset Colors ==========

    /** Default colors for common block types */
    public static int getDefaultColor(String blockId) {
        if (blockId.contains("diamond"))    return 0xFF00FFFF; // Cyan
        if (blockId.contains("emerald"))    return 0xFF00FF00; // Green
        if (blockId.contains("gold"))       return 0xFFFFD700; // Gold
        if (blockId.contains("iron"))       return 0xFFC0C0C0; // Silver
        if (blockId.contains("redstone"))   return 0xFFFF0000; // Red
        if (blockId.contains("lapis"))      return 0xFF0000FF; // Blue
        if (blockId.contains("coal"))       return 0xFF404040; // Dark gray
        if (blockId.contains("copper"))     return 0xFFB87333; // Copper
        if (blockId.contains("ancient_debris") || blockId.contains("netherite"))
                                            return 0xFF6B3A2A; // Brown
        if (blockId.contains("amethyst"))   return 0xFF9966CC; // Purple
        if (blockId.contains("spawner"))    return 0xFFFF00FF; // Magenta
        if (blockId.contains("chest"))      return 0xFFFFAA00; // Orange
        return 0xFFFFFFFF; // White default
    }

    // ========== Persistence ==========

    private static class ConfigData {
        Map<String, Integer> blocks;
        boolean enabled;
        int scanRadius;
        int scanInterval;
        float lineWidth;
        boolean filledFaces;
        float faceOpacity;
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            ConfigData data = new ConfigData();
            data.blocks = trackedBlocks;
            data.enabled = enabled;
            data.scanRadius = scanRadius;
            data.scanInterval = scanInterval;
            data.lineWidth = lineWidth;
            data.filledFaces = filledFaces;
            data.faceOpacity = faceOpacity;

            try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            BlockESP.LOGGER.error("Failed to save BlockESP config", e);
        }
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                    ConfigData data = GSON.fromJson(reader, ConfigData.class);
                    if (data != null) {
                        if (data.blocks != null) {
                            trackedBlocks.clear();
                            trackedBlocks.putAll(data.blocks);
                        }
                        enabled = data.enabled;
                        scanRadius = data.scanRadius > 0 ? data.scanRadius : 32;
                        scanInterval = data.scanInterval > 0 ? data.scanInterval : 10;
                        lineWidth = data.lineWidth > 0 ? data.lineWidth : 2.0f;
                        filledFaces = data.filledFaces;
                        faceOpacity = data.faceOpacity > 0 ? data.faceOpacity : 0.15f;
                    }
                }
                BlockESP.LOGGER.info("Loaded {} tracked blocks from config", trackedBlocks.size());
            } else {
                // Add some defaults
                addBlock("minecraft:diamond_ore", 0xFF00FFFF);
                addBlock("minecraft:deepslate_diamond_ore", 0xFF00FFFF);
                addBlock("minecraft:ancient_debris", 0xFF6B3A2A);
                addBlock("minecraft:spawner", 0xFFFF00FF);
                BlockESP.LOGGER.info("Created default BlockESP config");
            }
        } catch (Exception e) {
            BlockESP.LOGGER.error("Failed to load BlockESP config", e);
        }
    }
}
