package com.realcraft.realism;

import com.mojang.logging.LogUtils;
import com.realcraft.realism.client.RealCraftClient;
import com.realcraft.realism.config.RealConfig;
import com.realcraft.realism.network.RealNetwork;
import com.realcraft.realism.registry.ModEffects;
import com.realcraft.realism.registry.ModFeatures;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(RealCraft.MOD_ID)
public final class RealCraft {
    public static final String MOD_ID = "realcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RealCraft() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RealConfig.COMMON_SPEC, MOD_ID + "-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, RealConfig.CLIENT_SPEC, MOD_ID + "-client.toml");
        modBus.addListener(RealConfig::onLoad);
        modBus.addListener(RealConfig::onReload);

        ModFeatures.FEATURES.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        RealNetwork.register();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            RealCraftClient.init(modBus);
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
