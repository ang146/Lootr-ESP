package com.lootresp;

import com.lootresp.config.LootrChestESPConfig;
import com.lootresp.render.BlockChangeListener;
import com.lootresp.render.LootrChestESPRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(LootrChestESP.MOD_ID)
public final class LootrChestESP {
    public static final String MOD_ID = "lootrchestesp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public LootrChestESP() {
        ModLoadingContext.get().registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> NetworkConstants.IGNORESERVERONLY,
                        (remoteVersion, isServer) -> true));

        LootrChestESPConfig.load();
        LootrChestESPRenderer renderer = new LootrChestESPRenderer();
        MinecraftForge.EVENT_BUS.register(renderer);
        MinecraftForge.EVENT_BUS.register(new BlockChangeListener(renderer));
        LOGGER.info("Lootr Chest ESP loaded");
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ClientModEvents {
        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(LootrChestESPRenderer.TOGGLE_KEY);
            event.register(LootrChestESPRenderer.OPEN_GUI_KEY);
        }
    }
}
