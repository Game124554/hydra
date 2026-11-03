package com.realcraft.realism.client;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.config.RealConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/** Creatures that hit the water hard throw up a crown of spray proportional to their speed and size. */
@Mod.EventBusSubscriber(modid = RealCraft.MOD_ID, value = Dist.CLIENT)
public final class SplashEffects {
    private static final Map<Entity, Boolean> IN_WATER = new WeakHashMap<>();

    private SplashEffects() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || !RealConfig.BIG_SPLASHES.on()) {
            IN_WATER.clear();
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || entity.distanceToSqr(camera) > 48.0 * 48.0) {
                continue;
            }
            boolean inWater = entity.isInWater();
            Boolean before = IN_WATER.put(entity, inWater);
            if (before != null && !before && inWater) {
                double fallSpeed = entity.yo - entity.getY();
                if (fallSpeed > 0.25) {
                    splash(level, entity, fallSpeed);
                }
            }
        }
    }

    private static void splash(ClientLevel level, Entity entity, double speed) {
        RandomSource random = level.random;
        double surface = surfaceY(level, entity);
        float width = Math.max(0.5F, entity.getBbWidth());
        int count = Mth.clamp((int) (Math.min(speed, 1.5) * 40.0 * width * RealConfig.PARTICLE_DENSITY.val()), 4, 120);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = width * (0.4 + random.nextDouble() * 0.5);
            double x = entity.getX() + Math.cos(angle) * radius;
            double z = entity.getZ() + Math.sin(angle) * radius;
            double outward = 0.05 + random.nextDouble() * 0.1 * speed;
            level.addParticle(ParticleTypes.SPLASH, x, surface + 0.1, z,
                    Math.cos(angle) * outward, 0.2 + random.nextDouble() * 0.3 * speed, Math.sin(angle) * outward);
            if (i % 3 == 0) {
                level.addParticle(ParticleTypes.BUBBLE, x, surface - 0.3 - random.nextDouble() * 0.6, z, 0.0, 0.05, 0.0);
            }
        }
    }

    private static double surfaceY(ClientLevel level, Entity entity) {
        BlockPos.MutableBlockPos pos = entity.blockPosition().mutable();
        for (int i = 0; i < 4; i++) {
            FluidState above = level.getFluidState(pos.above());
            if (!above.is(FluidTags.WATER)) {
                FluidState here = level.getFluidState(pos);
                return pos.getY() + (here.is(FluidTags.WATER) ? here.getHeight(level, pos) : 0.0F);
            }
            pos.move(0, 1, 0);
        }
        return entity.getY();
    }
}
