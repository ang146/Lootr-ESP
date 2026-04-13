package com.blockesp.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Listens for block break/place events and marks the affected
 * chunk section as dirty so the renderer rescans it immediately.
 */
public class BlockChangeListener {

    private final BlockESPRenderer renderer;

    public BlockChangeListener(BlockESPRenderer renderer) {
        this.renderer = renderer;
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        renderer.markDirty(event.getPos());
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        renderer.markDirty(event.getPos());
    }
}
