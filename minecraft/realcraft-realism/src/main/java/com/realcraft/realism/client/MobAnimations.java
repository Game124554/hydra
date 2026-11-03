package com.realcraft.realism.client;

import com.realcraft.realism.config.RealConfig;
import com.realcraft.realism.mixin.client.QuadrupedModelAccessor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;

/**
 * Procedural animation layered on top of the vanilla poses: breathing and head bob for
 * humanoids, a shambling gait with a lolling head and dragging leg for zombies, rattling
 * skulls for skeletons and a nodding head for four-legged animals. Values vanilla resets every
 * frame are adjusted; head roll, which vanilla never sets, is assigned so nothing accumulates.
 */
public final class MobAnimations {
    private MobAnimations() {
    }

    public static void apply(EntityModel<?> model, LivingEntity entity, float partialTicks) {
        if (!RealConfig.REALISTIC_ANIMATIONS.on() || entity instanceof ArmorStand || entity.isDeadOrDying()) {
            return;
        }
        float age = entity.tickCount + partialTicks;
        float limb = entity.walkAnimation.position(partialTicks);
        float speed = Math.min(1.0F, entity.walkAnimation.speed(partialTicks));
        float seed = (entity.getId() % 97) * 0.37F;

        if (model instanceof HumanoidModel<?> humanoid) {
            animateHumanoid(humanoid, entity, age, limb, speed, seed);
        } else if (model instanceof QuadrupedModel<?> quadruped) {
            ModelPart head = ((QuadrupedModelAccessor) quadruped).realcraft$head();
            head.xRot += Mth.sin(limb * 1.3324F) * 0.08F * speed + Mth.sin(age * 0.06F + seed) * 0.03F * (1.0F - speed);
            head.zRot = Mth.sin(limb * 0.6662F) * 0.04F * speed; // vanilla never sets head roll, so assign rather than add
        }
    }

    private static void animateHumanoid(HumanoidModel<?> model, LivingEntity entity, float age, float limb, float speed, float seed) {
        float idle = 1.0F - speed;
        // Breathing while standing, a footstep bob while walking (head offsets are in pixels).
        model.head.y += Mth.sin(age * 0.08F + seed) * 0.25F * idle - Mth.abs(Mth.sin(limb * 0.6662F)) * 0.6F * speed;

        if (entity instanceof Zombie) {
            model.head.zRot = Mth.sin(age * 0.05F + seed) * 0.14F;
            model.head.xRot += 0.18F + Mth.sin(age * 0.07F + seed) * 0.06F;
            model.rightArm.zRot += Mth.sin(age * 0.09F + seed) * 0.09F;
            model.leftArm.zRot -= Mth.sin(age * 0.11F + seed + 1.0F) * 0.09F;
            model.rightArm.xRot += Mth.sin(limb * 0.33F + seed) * 0.18F * speed;
            model.leftArm.xRot += Mth.sin(limb * 0.41F) * 0.12F * speed;
            model.leftLeg.xRot *= 0.55F; // dragging leg
        } else if (entity instanceof AbstractSkeleton) {
            model.head.zRot = Mth.sin(age * 1.7F + seed) * 0.025F;
            model.head.xRot += Mth.sin(age * 2.3F + seed) * 0.015F;
        } else {
            model.rightArm.zRot += 0.05F * speed;
            model.leftArm.zRot -= 0.05F * speed;
            model.head.xRot += Mth.sin(limb * 1.3324F) * 0.04F * speed;
        }

        model.hat.copyFrom(model.head);
        if (model instanceof PlayerModel<?> player) {
            player.rightSleeve.copyFrom(player.rightArm);
            player.leftSleeve.copyFrom(player.leftArm);
            player.rightPants.copyFrom(player.rightLeg);
            player.leftPants.copyFrom(player.leftLeg);
        }
    }
}
