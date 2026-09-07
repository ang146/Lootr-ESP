package com.lootresp.render;

import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class BlockChangeListener {
    private final LootrChestESPRenderer renderer;

    public BlockChangeListener(LootrChestESPRenderer renderer) {
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
