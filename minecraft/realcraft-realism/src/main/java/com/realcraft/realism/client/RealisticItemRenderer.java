package com.realcraft.realism.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.realcraft.realism.config.RealConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Item physics: dropped items lie flat on the ground at a random angle (blocks sit on their base)
 * instead of hovering and spinning, tumble while flying through the air and bob in water.
 */
public class RealisticItemRenderer extends ItemEntityRenderer {
    private final ItemRenderer itemRenderer;
    private final RandomSource random = RandomSource.create();

    public RealisticItemRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ItemEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        ItemStack stack = entity.getItem();
        if (!RealConfig.ITEM_PHYSICS.on() || stack.isEmpty()) {
            super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);
            return;
        }
        BakedModel model = itemRenderer.getModel(stack, entity.level(), null, entity.getId());
        boolean block = model.isGui3d();
        ItemTransform ground = model.getTransforms().getTransform(ItemDisplayContext.GROUND);
        float time = entity.getAge() + partialTicks;
        int copies = Math.min(renderAmount(stack), RealConfig.ITEM_RENDER_COUNT.val());
        random.setSeed(Item.getId(stack.getItem()) + stack.getDamageValue());

        poseStack.pushPose();
        if (entity.isInWater()) {
            poseStack.translate(0.0F, 0.06F + Mth.sin(time * 0.1F + entity.bobOffs) * 0.03F, 0.0F);
            poseStack.mulPose(Axis.YP.rotation(entity.bobOffs + time * 0.01F));
            layFlat(poseStack, block, ground, Mth.sin(time * 0.15F + entity.bobOffs) * 8.0F);
        } else if (entity.onGround()) {
            poseStack.mulPose(Axis.YP.rotation(entity.bobOffs));
            layFlat(poseStack, block, ground, 0.0F);
        } else {
            // Tumbling end over end around the item's centre.
            float spin = time * 0.35F + entity.bobOffs;
            poseStack.translate(0.0F, 0.125F, 0.0F);
            poseStack.mulPose(Axis.YP.rotation(entity.bobOffs));
            poseStack.mulPose(Axis.XP.rotation(spin));
            poseStack.mulPose(Axis.ZP.rotation(spin * 0.6F));
            poseStack.translate(0.0F, -ground.translation.y(), 0.0F);
        }

        for (int i = 0; i < copies; i++) {
            poseStack.pushPose();
            if (i > 0) {
                float dx = (random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                float dy = (random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                if (block) {
                    poseStack.translate(dx, 0.0F, dy);
                    poseStack.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 90.0F));
                } else {
                    // Flat items are rotated onto their back, so local -Z is "up".
                    poseStack.translate(dx, dy, -0.032F * i);
                }
            }
            itemRenderer.render(stack, ItemDisplayContext.GROUND, false, poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY, model);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /** Rests the model on y = 0: flat items lie on their back, block items sit on their base. */
    private static void layFlat(PoseStack poseStack, boolean block, ItemTransform ground, float tiltDegrees) {
        if (block) {
            poseStack.translate(0.0F, -(ground.translation.y() - 0.5F * ground.scale.y()) + 0.001F, 0.0F);
        } else {
            poseStack.translate(0.0F, 0.5F * ground.scale.z() / 16.0F + 0.003F, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F + tiltDegrees));
            poseStack.translate(0.0F, -ground.translation.y(), 0.0F);
        }
    }

    private static int renderAmount(ItemStack stack) {
        int count = stack.getCount();
        if (count > 48) {
            return 5;
        }
        if (count > 32) {
            return 4;
        }
        if (count > 16) {
            return 3;
        }
        return count > 1 ? 2 : 1;
    }
}
