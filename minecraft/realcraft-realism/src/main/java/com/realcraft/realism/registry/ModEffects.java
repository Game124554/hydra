package com.realcraft.realism.registry;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.zombie.InfectionEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, RealCraft.MOD_ID);

    /** Zombie infection from a bite; turns the player into a zombie when it runs out. */
    public static final RegistryObject<MobEffect> INFECTION = EFFECTS.register("infection", InfectionEffect::new);

    private ModEffects() {
    }
}
