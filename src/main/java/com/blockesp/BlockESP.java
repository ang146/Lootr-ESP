package com.blockesp;

import com.blockesp.config.BlockESPConfig;
import com.blockesp.command.BlockESPCommand;
import com.blockesp.render.BlockChangeListener;
import com.blockesp.render.BlockESPRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BlockESP.MOD_ID)
public class BlockESP {
    public static final String MOD_ID = "blockesp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public BlockESP() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onClientSetup);

        MinecraftForge.EVENT_BUS.register(this);

        BlockESPRenderer renderer = new BlockESPRenderer();
        MinecraftForge.EVENT_BUS.register(renderer);
        MinecraftForge.EVENT_BUS.register(new BlockChangeListener(renderer));

        BlockESPConfig.load();
        LOGGER.info("BlockESP Mod Loaded!");
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("BlockESP Client Setup Complete");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterClientCommandsEvent event) {
        BlockESPCommand.register(event.getDispatcher());
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(BlockESPRenderer.TOGGLE_KEY);
            event.register(BlockESPRenderer.OPEN_GUI_KEY);
        }
    }
}
