package com.realcraft.realism.mixin;

import com.realcraft.realism.config.RealConfig;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Keeps corpses around longer than vanilla's 20 ticks so ragdolls can finish falling. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyConstant(method = "tickDeath", constant = @Constant(intValue = 20))
    private int realcraft$corpseTicks(int vanilla) {
        return RealConfig.CORPSE_TICKS.val();
    }
}
