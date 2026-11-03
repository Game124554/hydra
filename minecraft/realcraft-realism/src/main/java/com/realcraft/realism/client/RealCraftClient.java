package com.realcraft.realism.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.realcraft.realism.RealCraft;
import com.realcraft.realism.client.gui.RealismSettingsScreen;
import com.realcraft.realism.zombie.ZombifiedState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

/** Client-only setup: settings key, settings screen in the Mods list and the item renderer. */
@Mod.EventBusSubscriber(modid = RealCraft.MOD_ID, value = Dist.CLIENT)
public final class RealCraftClient {
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping("key.realcraft.settings", InputConstants.KEY_K, "key.categories.realcraft");

    private RealCraftClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(RealCraftClient::onRegisterKeys);
        modBus.addListener(RealCraftClient::onRegisterRenderers);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new RealismSettingsScreen(parent)));
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityType.ITEM, RealisticItemRenderer::new);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ZombifiedState.clearClient();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_SETTINGS.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new RealismSettingsScreen(null));
            }
        }
    }
}
