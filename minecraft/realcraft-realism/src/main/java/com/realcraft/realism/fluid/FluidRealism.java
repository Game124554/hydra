package com.realcraft.realism.fluid;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.config.RealConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Water and lava realism: strong currents, heavy armour that drags swimmers down, radiant heat
 * from lava and steam bursts where the two meet.
 */
@Mod.EventBusSubscriber(modid = RealCraft.MOD_ID)
public final class FluidRealism {
    private FluidRealism() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.isSpectator()) {
            return;
        }
        Level level = entity.level();
        // Movement is only changed on the side that simulates the entity: the server for mobs,
        // the client for the local player.
        if (entity.isControlledByLocalInstance() && entity.isInWater()) {
            applyCurrent(entity, level);
            applyArmorSinking(entity);
        }
        if (!level.isClientSide && RealConfig.LAVA_HEAT.on() && entity.tickCount % 10 == 0) {
            applyLavaHeat(entity, level);
        }
    }

    private static void applyCurrent(LivingEntity entity, Level level) {
        double strength = RealConfig.WATER_CURRENT.val();
        if (strength <= 0 || entity.isPassenger()) {
            return;
        }
        BlockPos pos = entity.blockPosition();
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.is(FluidTags.WATER) || fluid.isSource()) {
            return;
        }
        Vec3 flow = fluid.getFlow(level, pos);
        if (flow.lengthSqr() > 1.0E-6) {
            entity.setDeltaMovement(entity.getDeltaMovement().add(flow.normalize().scale(strength)));
        }
    }

    private static void applyArmorSinking(LivingEntity entity) {
        if (!RealConfig.ARMOR_SINKING.on() || entity.onGround() || entity.isPassenger()) {
            return;
        }
        if (entity instanceof Player player && player.getAbilities().flying) {
            return;
        }
        int armor = entity.getArmorValue();
        if (armor <= 4) {
            return; // leather and partial sets still let you swim freely
        }
        double pull = (armor - 4) * RealConfig.ARMOR_SINK_STRENGTH.val();
        entity.setDeltaMovement(entity.getDeltaMovement().add(0, -pull, 0));
    }

    private static void applyLavaHeat(LivingEntity entity, Level level) {
        if (entity.fireImmune() || entity.isInLava() || entity.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return;
        }
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        double radius = RealConfig.LAVA_HEAT_RADIUS.val();
        AABB box = entity.getBoundingBox().inflate(radius, 0.1, radius);
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            if (level.getFluidState(pos).is(FluidTags.LAVA)) {
                entity.setSecondsOnFire(3);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onFluidMakesBlock(BlockEvent.FluidPlaceBlockEvent event) {
        if (!RealConfig.STEAM.on() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.CLOUD, x, y, z, 14, 0.35, 0.25, 0.35, 0.04);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 6, 0.3, 0.2, 0.3, 0.02);
        level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 0.6F + level.random.nextFloat() * 0.3F);
    }
}
