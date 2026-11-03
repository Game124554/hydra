package com.realcraft.realism.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.realcraft.realism.client.smooth.SmoothMesher;
import com.realcraft.realism.client.smooth.SmoothTerrain;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Swaps the cube model of smoothable blocks for the smooth mesh while a chunk section is built. */
@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask")
public abstract class RebuildTaskMixin {
    @Redirect(method = "compile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V"))
    private void realcraft$renderSmooth(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level,
                                        PoseStack poseStack, VertexConsumer consumer, boolean checkSides, RandomSource random,
                                        ModelData modelData, RenderType renderType) {
        if (SmoothTerrain.isActive() && SmoothTerrain.isSmoothable(state)) {
            SmoothMesher.renderBlock(state, pos, level, poseStack, consumer, random, modelData, renderType);
        } else {
            dispatcher.renderBatched(state, pos, level, poseStack, consumer, checkSides, random, modelData, renderType);
        }
    }
}
