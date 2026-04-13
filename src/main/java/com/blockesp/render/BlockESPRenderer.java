package com.blockesp.render;

import com.blockesp.config.BlockESPConfig;
import com.blockesp.gui.BlockESPScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.*;

/**
 * Optimized BlockESP renderer with:
 * - Chunk-based scanning (only scan 16x16x16 sections, skip empty/air-only ones)
 * - Async scanning on a background thread (no main thread lag)
 * - Chunk cache with dirty tracking (only rescan when player moves or config changes)
 * - Single batched draw calls for all blocks
 */
public class BlockESPRenderer {

    // Keybinds
    public static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.blockesp.toggle",
            KeyConflictContext.IN_GAME,
            com.mojang.blaze3d.platform.InputConstants.getKey(GLFW.GLFW_KEY_H, 0),
            "key.categories.blockesp"
    );

    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "key.blockesp.gui",
            KeyConflictContext.IN_GAME,
            com.mojang.blaze3d.platform.InputConstants.getKey(GLFW.GLFW_KEY_J, 0),
            "key.categories.blockesp"
    );

    // ==================== Scan Cache ====================

    // Cache: packed section key -> list of found blocks in that section
    private final ConcurrentHashMap<Long, List<BlockPosColor>> chunkCache = new ConcurrentHashMap<>();

    // Flattened render list (rebuilt from cache after each scan)
    private volatile List<BlockPosColor> renderList = Collections.emptyList();

    // Background scanner thread
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BlockESP-Scanner");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean scanRunning = false;

    // Track state for dirty detection
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;
    private int lastConfigHash = 0;
    private int tickCounter = 0;

    // Sections that need rescanning due to block changes
    private final Set<Long> dirtySections = ConcurrentHashMap.newKeySet();

    /** Called when a block changes — marks that section for immediate rescan */
    public void markDirty(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int sectionY = pos.getY() >> 4;
        int chunkZ = pos.getZ() >> 4;
        dirtySections.add(packSectionKey(chunkX, sectionY, chunkZ));
    }

    // Simple data holder - pre-compute color floats once
    public static class BlockPosColor {
        public final int x, y, z;
        public final float r, g, b, a;

        public BlockPosColor(int x, int y, int z, int color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = ((color >> 16) & 0xFF) / 255f;
            this.g = ((color >> 8) & 0xFF) / 255f;
            this.b = (color & 0xFF) / 255f;
            this.a = ((color >> 24) & 0xFF) / 255f;
        }
    }

    // ==================== Event Handlers ====================

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        if (TOGGLE_KEY.consumeClick()) {
            BlockESPConfig.toggle();
            String state = BlockESPConfig.isEnabled() ? "§aON" : "§cOFF";
            mc.gui.setOverlayMessage(
                    Component.literal("§7[BlockESP] " + state), false);
            if (!BlockESPConfig.isEnabled()) {
                chunkCache.clear();
                renderList = Collections.emptyList();
            }
        }

        if (OPEN_GUI_KEY.consumeClick()) {
            mc.setScreen(new BlockESPScreen());
        }
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!BlockESPConfig.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Trigger async scan periodically or when blocks changed
        boolean hasDirty = !dirtySections.isEmpty();
        tickCounter++;
        if ((hasDirty || tickCounter >= BlockESPConfig.getScanInterval()) && !scanRunning) {
            tickCounter = 0;
            triggerAsyncScan(mc);
        }

        // Render from cached results (never blocks the render thread)
        List<BlockPosColor> currentList = renderList;
        if (!currentList.isEmpty()) {
            renderHighlights(event.getPoseStack(), mc, currentList);
        }
    }

    // ==================== Async Chunk-Based Scanning ====================

    private void triggerAsyncScan(Minecraft mc) {
        final Level level = mc.level;
        final BlockPos playerPos = mc.player.blockPosition();
        final int playerChunkX = playerPos.getX() >> 4;
        final int playerChunkZ = playerPos.getZ() >> 4;
        final int radius = BlockESPConfig.getScanRadius();
        final Map<String, Integer> tracked = new HashMap<>(BlockESPConfig.getTrackedBlocks());
        final int configHash = tracked.hashCode();

        if (tracked.isEmpty()) {
            chunkCache.clear();
            renderList = Collections.emptyList();
            return;
        }

        final boolean fullRescan = (configHash != lastConfigHash);
        final boolean playerMovedChunk = (playerChunkX != lastPlayerChunkX || playerChunkZ != lastPlayerChunkZ);

        // Grab dirty sections and clear the set
        final Set<Long> dirtySnapshot = new HashSet<>(dirtySections);
        dirtySections.clear();

        lastPlayerChunkX = playerChunkX;
        lastPlayerChunkZ = playerChunkZ;
        lastConfigHash = configHash;

        scanRunning = true;
        scanExecutor.submit(() -> {
            try {
                int chunkRadius = (radius + 15) >> 4;

                if (fullRescan) {
                    chunkCache.clear();
                }

                Set<Long> validKeys = new HashSet<>();

                for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
                    for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                        int chunkX = playerChunkX + cx;
                        int chunkZ = playerChunkZ + cz;

                        LevelChunk chunk;
                        try {
                            chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                        } catch (Exception e) {
                            continue;
                        }
                        if (chunk == null) continue;

                        int minSection = chunk.getMinSection();
                        int maxSection = chunk.getMaxSection();

                        for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
                            int sectionWorldY = sectionY << 4;
                            if (Math.abs(sectionWorldY - playerPos.getY()) > radius + 16) continue;

                            long key = packSectionKey(chunkX, sectionY, chunkZ);
                            validKeys.add(key);

                            // Skip cached sections unless dirty, player moved, or config changed
                            if (!fullRescan && chunkCache.containsKey(key)
                                    && !playerMovedChunk && !dirtySnapshot.contains(key)) {
                                continue;
                            }

                            int sectionIndex = sectionY - minSection;
                            LevelChunkSection section;
                            try {
                                section = chunk.getSections()[sectionIndex];
                            } catch (Exception e) {
                                continue;
                            }

                            // OPTIMIZATION: Skip air-only sections entirely
                            if (section == null || section.hasOnlyAir()) {
                                chunkCache.remove(key);
                                continue;
                            }

                            List<BlockPosColor> found = new ArrayList<>();
                            int baseX = chunkX << 4;
                            int baseY = sectionY << 4;
                            int baseZ = chunkZ << 4;

                            for (int x = 0; x < 16; x++) {
                                for (int y = 0; y < 16; y++) {
                                    for (int z = 0; z < 16; z++) {
                                        BlockState state;
                                        try {
                                            state = section.getBlockState(x, y, z);
                                        } catch (Exception e) {
                                            continue;
                                        }
                                        if (state.isAir()) continue;

                                        ResourceLocation blockId = state.getBlock()
                                                .builtInRegistryHolder().key().location();
                                        Integer color = tracked.get(blockId.toString());
                                        if (color != null) {
                                            found.add(new BlockPosColor(
                                                    baseX + x, baseY + y, baseZ + z, color));
                                        }
                                    }
                                }
                            }

                            if (found.isEmpty()) {
                                chunkCache.remove(key);
                            } else {
                                chunkCache.put(key, found);
                            }
                        }
                    }
                }

                // Remove out-of-range entries
                chunkCache.keySet().removeIf(k -> !validKeys.contains(k));

                // Rebuild flat render list
                List<BlockPosColor> newList = new ArrayList<>();
                for (List<BlockPosColor> sectionList : chunkCache.values()) {
                    newList.addAll(sectionList);
                }
                renderList = newList;

            } catch (Exception e) {
                // Don't crash the game
            } finally {
                scanRunning = false;
            }
        });
    }

    private static long packSectionKey(int chunkX, int sectionY, int chunkZ) {
        return ((long)(chunkX & 0x3FFFFF) << 42) |
               ((long)(sectionY & 0xFFFFF) << 22) |
               ((long)(chunkZ & 0x3FFFFF));
    }

    // ==================== Rendering ====================

    private void renderHighlights(PoseStack poseStack, Minecraft mc, List<BlockPosColor> blocks) {
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(BlockESPConfig.getLineWidth());

        Matrix4f matrix = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder;

        // Single batched draw call for all filled faces
        if (BlockESPConfig.isFilledFaces()) {
            RenderSystem.disableCull();
            bufferBuilder = tesselator.getBuilder();
            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            float faceAlpha = BlockESPConfig.getFaceOpacity();
            for (BlockPosColor bpc : blocks) {
                drawFilledBox(bufferBuilder, matrix,
                        bpc.x, bpc.y, bpc.z,
                        bpc.x + 1, bpc.y + 1, bpc.z + 1,
                        bpc.r, bpc.g, bpc.b, faceAlpha);
            }

            BufferUploader.drawWithShader(bufferBuilder.end());
            RenderSystem.enableCull();
        }

        // Single batched draw call for all outlines
        bufferBuilder = tesselator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (BlockPosColor bpc : blocks) {
            drawOutlineBox(bufferBuilder, matrix,
                    bpc.x, bpc.y, bpc.z,
                    bpc.x + 1, bpc.y + 1, bpc.z + 1,
                    bpc.r, bpc.g, bpc.b, bpc.a);
        }

        BufferUploader.drawWithShader(bufferBuilder.end());

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        poseStack.popPose();
    }

    // ==================== Geometry Helpers ====================

    private static void drawOutlineBox(BufferBuilder buf, Matrix4f matrix,
                                        float x1, float y1, float z1,
                                        float x2, float y2, float z2,
                                        float r, float g, float b, float a) {
        line(buf, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(buf, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(buf, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(buf, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);
        line(buf, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(buf, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(buf, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(buf, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);
        line(buf, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(buf, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(buf, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(buf, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void line(BufferBuilder buf, Matrix4f matrix,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float r, float g, float b, float a) {
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x2, y2, z2).color(r, g, b, a).endVertex();
    }

    private static void drawFilledBox(BufferBuilder buf, Matrix4f matrix,
                                       float x1, float y1, float z1,
                                       float x2, float y2, float z2,
                                       float r, float g, float b, float a) {
        quad(buf, matrix, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        quad(buf, matrix, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        quad(buf, matrix, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
        quad(buf, matrix, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        quad(buf, matrix, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        quad(buf, matrix, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
    }

    private static void quad(BufferBuilder buf, Matrix4f matrix,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float x3, float y3, float z3,
                              float x4, float y4, float z4,
                              float r, float g, float b, float a) {
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x2, y2, z2).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x3, y3, z3).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x4, y4, z4).color(r, g, b, a).endVertex();
    }
}
