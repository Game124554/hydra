package com.realcraft.realism.client.smooth;

import com.realcraft.realism.config.RealConfig;
import com.realcraft.realism.registry.ModTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Decides which blocks are drawn as smooth terrain. Safe to call from chunk-builder threads. */
public final class SmoothTerrain {
    private SmoothTerrain() {
    }

    public static boolean isActive() {
        return RealConfig.SMOOTH_TERRAIN.on() && RealConfig.SMOOTHNESS.val() > 0.0;
    }

    public static boolean isSmoothable(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        if (RealConfig.smoothExcludedBlocks().contains(block)) {
            return false;
        }
        return state.is(ModTags.SMOOTHABLE) || RealConfig.smoothExtraBlocks().contains(block);
    }
}
