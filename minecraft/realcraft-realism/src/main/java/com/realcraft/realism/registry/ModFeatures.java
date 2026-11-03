package com.realcraft.realism.registry;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.world.UnderworldFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, RealCraft.MOD_ID);

    /** Runs once per chunk: removes bedrock and carves the hell layer. Placed through biome modifiers. */
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> UNDERWORLD =
            FEATURES.register("underworld", () -> new UnderworldFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {
    }
}
