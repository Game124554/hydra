package com.realcraft.realism.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.realcraft.realism.client.clouds.RealisticClouds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/** Draws the realistic clouds instead of the vanilla ones. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    @Nullable
    private ClientLevel level;

    @Shadow
    private int ticks;

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void realcraft$renderClouds(PoseStack poseStack, Matrix4f projection, float partialTick,
                                        double camX, double camY, double camZ, CallbackInfo ci) {
        if (level != null && RealisticClouds.render(level, ticks, partialTick, poseStack, projection, camX, camY, camZ)) {
            ci.cancel();
        }
    }
}
