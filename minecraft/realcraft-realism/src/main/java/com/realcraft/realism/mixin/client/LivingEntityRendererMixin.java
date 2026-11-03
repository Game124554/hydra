package com.realcraft.realism.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.realcraft.realism.client.MobAnimations;
import com.realcraft.realism.client.ragdoll.Ragdoll;
import com.realcraft.realism.client.ragdoll.RagdollManager;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ragdoll poses for dying creatures and procedural animation for living ones. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Shadow
    protected M model;

    @Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
    private void realcraft$ragdollPose(LivingEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                       float partialTicks, CallbackInfo ci) {
        Ragdoll ragdoll = RagdollManager.get(entity);
        if (ragdoll != null) {
            ragdoll.applyBodyPose(entity, poseStack, partialTicks);
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void realcraft$ragdollLimbs(LivingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                                        MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        Ragdoll ragdoll = RagdollManager.get(entity);
        if (ragdoll != null) {
            ragdoll.applyLimbs(this.model, partialTicks);
        } else {
            MobAnimations.apply(this.model, entity, partialTicks);
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"))
    private void realcraft$restoreLimbs(LivingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                                        MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (RagdollManager.get(entity) != null) {
            Ragdoll.restoreLimbs(this.model);
        }
    }

    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void realcraft$noDeathTint(LivingEntity entity, float whiteOverlay, CallbackInfoReturnable<Integer> cir) {
        if (RagdollManager.get(entity) != null) {
            cir.setReturnValue(OverlayTexture.pack(OverlayTexture.u(whiteOverlay), OverlayTexture.v(false)));
        }
    }
}
