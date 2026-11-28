package com.magicmacchiato.vrextensions;

import com.magicmacchiato.vrextensions.Network.ModNetwork;
import com.magicmacchiato.vrextensions.Utils.DialogueManager;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;


@Mod(VillagersRebornExtensionsMod.MOD_ID)
public class VillagersRebornExtensionsMod {

    public static final String MOD_ID = "vrextensions";
    public static final Logger LOGGER = LogUtils.getLogger();


    public VillagersRebornExtensionsMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        ModNetwork.register(modEventBus);
        com.magicmacchiato.vrextensions.Items.ModItems.init();
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new com.magicmacchiato.vrextensions.Items.RelationshipDumpEvents());
        NeoForge.EVENT_BUS.register(new com.magicmacchiato.vrextensions.Features.MoodEvents());
        NeoForge.EVENT_BUS.register(new com.magicmacchiato.vrextensions.Features.InterestEvents());
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }


    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
        DialogueManager.loadAll();
    }


    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }
}
