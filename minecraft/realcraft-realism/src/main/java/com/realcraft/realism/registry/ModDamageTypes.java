package com.realcraft.realism.registry;

import com.realcraft.realism.RealCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

public final class ModDamageTypes {
    /** Defined in data/realcraft/damage_type/glass_shards.json. */
    public static final ResourceKey<DamageType> GLASS_SHARDS = ResourceKey.create(Registries.DAMAGE_TYPE, RealCraft.id("glass_shards"));

    private ModDamageTypes() {
    }

    public static DamageSource glassShards(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(GLASS_SHARDS));
    }
}
