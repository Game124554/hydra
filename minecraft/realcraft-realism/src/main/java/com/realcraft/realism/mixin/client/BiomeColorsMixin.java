package com.realcraft.realism.mixin.client;

import com.realcraft.realism.client.RealisticWater;
import com.realcraft.realism.config.RealConfig;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shifts the biome water tint towards natural blue-green tones. */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {
    @Inject(method = "getAverageWaterColor", at = @At("RETURN"), cancellable = true)
    private static void realcraft$realisticWater(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (RealConfig.REALISTIC_WATER_COLOR.on()) {
            cir.setReturnValue(RealisticWater.naturalTint(cir.getReturnValue()));
        }
    }
}
