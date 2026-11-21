package com.roadforge;

import com.mojang.logging.LogUtils;
import com.roadforge.build.BuildManager;
import com.roadforge.command.RoadCommands;
import com.roadforge.item.ModItems;
import com.roadforge.network.Net;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(RoadForge.MOD_ID)
public class RoadForge {
    public static final String MOD_ID = "roadforge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RoadForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, RoadConfig.SPEC);

        ModItems.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addCreative);

        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(BuildManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(BuildManager::onServerStopping);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
        LOGGER.info("RoadForge: common setup done");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.ROAD_WAND.get());
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        RoadCommands.register(event.getDispatcher());
    }
}
