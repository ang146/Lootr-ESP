package com.lootresp.render;

import com.lootresp.LootrChestESP;
import com.lootresp.config.LootrChestESPConfig;
import com.lootresp.gui.LootrChestESPScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import noobanidus.mods.lootr.block.entities.LootrChestBlockEntity;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LootrChestESPRenderer {
    private static final ResourceLocation TARGET_ID = new ResourceLocation("lootr", "lootr_chest");
    public static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.lootrchestesp.toggle", KeyConflictContext.IN_GAME,
            com.mojang.blaze3d.platform.InputConstants.getKey(GLFW.GLFW_KEY_H, 0),
            "key.categories.lootrchestesp");
    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "key.lootrchestesp.gui", KeyConflictContext.IN_GAME,
            com.mojang.blaze3d.platform.InputConstants.getKey(GLFW.GLFW_KEY_J, 0),
            "key.categories.lootrchestesp");

    private final ConcurrentHashMap<Long, List<BlockPos>> sectionCache = new ConcurrentHashMap<>();
    private final Set<Long> dirtySections = ConcurrentHashMap.newKeySet();
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LootrChestESP-Scanner");
        thread.setDaemon(true);
        return thread;
    });

    private volatile List<BlockPos> candidates = Collections.emptyList();
    private volatile boolean scanRunning;
    private ClientLevel cachedLevel;
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;
    private int tickCounter;
    private int activeRadius = LootrChestESPConfig.getScanRadius();

    public void markDirty(BlockPos pos) {
        dirtySections.add(packSectionKey(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && TOGGLE_KEY.consumeClick()) {
            LootrChestESPConfig.toggle();
            mc.gui.setOverlayMessage(Component.literal("Lootr Chest ESP: "
                    + (LootrChestESPConfig.isEnabled() ? "ON" : "OFF")), false);
            if (!LootrChestESPConfig.isEnabled()) clearCache();
        }
        if (mc.screen == null && OPEN_GUI_KEY.consumeClick()) {
            mc.setScreen(new LootrChestESPScreen());
        }
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        LootrChestESPConfig.flushIfDue();
        if (!LootrChestESPConfig.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (activeRadius != LootrChestESPConfig.getScanRadius()) {
            activeRadius = LootrChestESPConfig.getScanRadius();
            clearCache();
            tickCounter = LootrChestESPConfig.getScanInterval();
        }
        if (cachedLevel != mc.level) {
            clearCache();
            cachedLevel = mc.level;
        }

        tickCounter++;
        if ((!dirtySections.isEmpty() || tickCounter >= LootrChestESPConfig.getScanInterval())
                && !scanRunning) {
            tickCounter = 0;
            triggerAsyncScan(mc.level, mc.player.blockPosition());
        }

        List<BlockPos> snapshot = candidates;
        if (!snapshot.isEmpty()) renderHighlights(event.getPoseStack(), mc, snapshot);
    }

    private void clearCache() {
        sectionCache.clear();
        dirtySections.clear();
        candidates = Collections.emptyList();
        lastPlayerChunkX = Integer.MIN_VALUE;
        lastPlayerChunkZ = Integer.MIN_VALUE;
    }

    private void triggerAsyncScan(ClientLevel level, BlockPos playerPos) {
        Block targetBlock = ForgeRegistries.BLOCKS.getValue(TARGET_ID);
        if (targetBlock == null) return;

        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;
        int radius = LootrChestESPConfig.getScanRadius();
        boolean playerMovedChunk = playerChunkX != lastPlayerChunkX || playerChunkZ != lastPlayerChunkZ;
        Set<Long> dirtySnapshot = new HashSet<>(dirtySections);
        dirtySections.removeAll(dirtySnapshot);
        lastPlayerChunkX = playerChunkX;
        lastPlayerChunkZ = playerChunkZ;
        scanRunning = true;

        scanExecutor.submit(() -> {
            try {
                int chunkRadius = (radius + 15) >> 4;
                Set<Long> validKeys = new HashSet<>();
                for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                    for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                        int chunkX = playerChunkX + dx;
                        int chunkZ = playerChunkZ + dz;
                        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                        if (chunk == null) continue;

                        int minSection = chunk.getMinSection();
                        int maxSection = chunk.getMaxSection();
                        for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
                            int sectionWorldY = sectionY << 4;
                            if (Math.abs(sectionWorldY - playerPos.getY()) > radius + 16) continue;
                            long key = packSectionKey(chunkX, sectionY, chunkZ);
                            validKeys.add(key);
                            if (sectionCache.containsKey(key) && !playerMovedChunk
                                    && !dirtySnapshot.contains(key)) continue;

                            int sectionIndex = sectionY - minSection;
                            LevelChunkSection[] sections = chunk.getSections();
                            if (sectionIndex < 0 || sectionIndex >= sections.length) continue;
                            LevelChunkSection section = sections[sectionIndex];
                            if (section == null || section.hasOnlyAir()) {
                                sectionCache.remove(key);
                                continue;
                            }

                            List<BlockPos> found = scanSection(section, targetBlock,
                                    chunkX << 4, sectionY << 4, chunkZ << 4);
                            if (found.isEmpty()) sectionCache.remove(key);
                            else sectionCache.put(key, found);
                        }
                    }
                }
                sectionCache.keySet().removeIf(key -> !validKeys.contains(key));
                List<BlockPos> rebuilt = new ArrayList<>();
                sectionCache.values().forEach(rebuilt::addAll);
                if (cachedLevel == level) candidates = List.copyOf(rebuilt);
            } catch (Exception e) {
                LootrChestESP.LOGGER.debug("Lootr chest scan failed", e);
            } finally {
                scanRunning = false;
            }
        });
    }

    private static List<BlockPos> scanSection(LevelChunkSection section, Block targetBlock,
                                               int baseX, int baseY, int baseZ) {
        List<BlockPos> found = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    if (section.getBlockState(x, y, z).getBlock() == targetBlock) {
                        found.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                    }
                }
            }
        }
        return found;
    }

    private void renderHighlights(PoseStack poseStack, Minecraft mc, List<BlockPos> positions) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        Block targetBlock = ForgeRegistries.BLOCKS.getValue(TARGET_ID);
        if (targetBlock == null) return;
        UUID playerId = mc.player.getUUID();
        double radiusSquared = (double) LootrChestESPConfig.getScanRadius()
                * LootrChestESPConfig.getScanRadius();
        List<BlockPos> visible = new ArrayList<>(positions.size());

        for (BlockPos pos : positions) {
            if (mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    > radiusSquared) continue;
            BlockState liveState = level.getBlockState(pos);
            if (liveState.getBlock() != targetBlock) continue;
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof LootrChestBlockEntity chest)) continue;
            if (chest.getOpeners().contains(playerId)) continue;
            visible.add(pos);
        }
        if (visible.isEmpty()) return;

        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(LootrChestESPConfig.getLineWidth());

        Matrix4f matrix = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        int color = LootrChestESPConfig.getEspColor();
        float red = ((color >> 16) & 0xff) / 255.0f;
        float green = ((color >> 8) & 0xff) / 255.0f;
        float blue = (color & 0xff) / 255.0f;
        float alpha = ((color >>> 24) & 0xff) / 255.0f;
        if (LootrChestESPConfig.isFilledFaces()) {
            RenderSystem.disableCull();
            BufferBuilder faces = tesselator.getBuilder();
            faces.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (BlockPos pos : visible) drawFilledBox(faces, matrix, pos,
                    red, green, blue, LootrChestESPConfig.getFaceOpacity());
            BufferUploader.drawWithShader(faces.end());
            RenderSystem.enableCull();
        }

        if (LootrChestESPConfig.isOutlineEnabled()) {
            BufferBuilder outlines = tesselator.getBuilder();
            outlines.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            for (BlockPos pos : visible) drawOutlineBox(outlines, matrix, pos, red, green, blue, alpha);
            BufferUploader.drawWithShader(outlines.end());
        }

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static long packSectionKey(int chunkX, int sectionY, int chunkZ) {
        return ((long) (chunkX & 0x3fffff) << 42)
                | ((long) (sectionY & 0xfffff) << 22)
                | (long) (chunkZ & 0x3fffff);
    }

    private static void drawOutlineBox(BufferBuilder buffer, Matrix4f matrix, BlockPos pos,
                                       float red, float green, float blue, float alpha) {
        float x1 = pos.getX(), y1 = pos.getY(), z1 = pos.getZ();
        float x2 = x1 + 1, y2 = y1 + 1, z2 = z1 + 1;
        line(buffer,matrix,x1,y1,z1,x2,y1,z1,red,green,blue,alpha); line(buffer,matrix,x2,y1,z1,x2,y1,z2,red,green,blue,alpha);
        line(buffer,matrix,x2,y1,z2,x1,y1,z2,red,green,blue,alpha); line(buffer,matrix,x1,y1,z2,x1,y1,z1,red,green,blue,alpha);
        line(buffer,matrix,x1,y2,z1,x2,y2,z1,red,green,blue,alpha); line(buffer,matrix,x2,y2,z1,x2,y2,z2,red,green,blue,alpha);
        line(buffer,matrix,x2,y2,z2,x1,y2,z2,red,green,blue,alpha); line(buffer,matrix,x1,y2,z2,x1,y2,z1,red,green,blue,alpha);
        line(buffer,matrix,x1,y1,z1,x1,y2,z1,red,green,blue,alpha); line(buffer,matrix,x2,y1,z1,x2,y2,z1,red,green,blue,alpha);
        line(buffer,matrix,x2,y1,z2,x2,y2,z2,red,green,blue,alpha); line(buffer,matrix,x1,y1,z2,x1,y2,z2,red,green,blue,alpha);
    }

    private static void line(BufferBuilder b, Matrix4f m, float x1,float y1,float z1,float x2,float y2,float z2,
                             float red,float green,float blue,float alpha) {
        b.vertex(m,x1,y1,z1).color(red,green,blue,alpha).endVertex();
        b.vertex(m,x2,y2,z2).color(red,green,blue,alpha).endVertex();
    }

    private static void drawFilledBox(BufferBuilder b, Matrix4f m, BlockPos pos,
                                      float red, float green, float blue, float alpha) {
        float x1=pos.getX(), y1=pos.getY(), z1=pos.getZ(), x2=x1+1, y2=y1+1, z2=z1+1;
        quad(b,m,x1,y1,z1,x2,y1,z1,x2,y1,z2,x1,y1,z2,red,green,blue,alpha);
        quad(b,m,x1,y2,z1,x1,y2,z2,x2,y2,z2,x2,y2,z1,red,green,blue,alpha);
        quad(b,m,x1,y1,z1,x1,y2,z1,x2,y2,z1,x2,y1,z1,red,green,blue,alpha);
        quad(b,m,x1,y1,z2,x2,y1,z2,x2,y2,z2,x1,y2,z2,red,green,blue,alpha);
        quad(b,m,x1,y1,z1,x1,y1,z2,x1,y2,z2,x1,y2,z1,red,green,blue,alpha);
        quad(b,m,x2,y1,z1,x2,y2,z1,x2,y2,z2,x2,y1,z2,red,green,blue,alpha);
    }

    private static void quad(BufferBuilder b, Matrix4f m,
                             float x1,float y1,float z1,float x2,float y2,float z2,
                             float x3,float y3,float z3,float x4,float y4,float z4,
                             float red,float green,float blue,float alpha) {
        b.vertex(m,x1,y1,z1).color(red,green,blue,alpha).endVertex();
        b.vertex(m,x2,y2,z2).color(red,green,blue,alpha).endVertex();
        b.vertex(m,x3,y3,z3).color(red,green,blue,alpha).endVertex();
        b.vertex(m,x4,y4,z4).color(red,green,blue,alpha).endVertex();
    }
}
