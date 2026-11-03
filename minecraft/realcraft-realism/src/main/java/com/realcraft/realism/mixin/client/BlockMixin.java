package com.realcraft.realism.mixin.client;

import com.realcraft.realism.client.smooth.SmoothTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smooth terrain can pull away from a neighbouring cube block (planks, bricks, ...), so that
 * block's face must not be culled against it or there would be holes in the world.
 */
@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void realcraft$keepFacesNextToSmoothTerrain(BlockState state, BlockGetter level, BlockPos pos, Direction face,
                                                               BlockPos neighborPos, CallbackInfoReturnable<Boolean> cir) {
        if (SmoothTerrain.isActive() && !SmoothTerrain.isSmoothable(state)
                && SmoothTerrain.isSmoothable(level.getBlockState(neighborPos))) {
            cir.setReturnValue(true);
        }
    }
}
