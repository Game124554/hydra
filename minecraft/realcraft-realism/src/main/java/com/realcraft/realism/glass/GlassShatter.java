package com.realcraft.realism.glass;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.config.RealConfig;
import com.realcraft.realism.registry.ModDamageTypes;
import com.realcraft.realism.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Glass that behaves like glass: arrows punch through it, sprinting into it or landing on it from
 * a height smashes it (and cuts you), and blast shockwaves blow out windows around explosions.
 */
@Mod.EventBusSubscriber(modid = RealCraft.MOD_ID)
public final class GlassShatter {
    /** Per entity: last x, last z, last horizontal speed, last direction x, last direction z. */
    private static final Map<LivingEntity, double[]> MOTION = new WeakHashMap<>();

    private GlassShatter() {
    }

    @SubscribeEvent
    @SuppressWarnings("removal")
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!RealConfig.GLASS.on() || !RealConfig.GLASS_PROJECTILES.on()
                || !(projectile.level() instanceof ServerLevel level)
                || !(projectile instanceof AbstractArrow)
                || !(event.getRayTraceResult() instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Vec3 velocity = projectile.getDeltaMovement();
        if (velocity.length() < RealConfig.GLASS_PROJECTILE_SPEED.val()) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModTags.SHATTERABLE)) {
            return;
        }
        shatter(level, pos, state, velocity);
        projectile.setDeltaMovement(velocity.scale(0.75));
        // Cancelling a block impact lets the arrow keep flying through the hole it just made.
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !RealConfig.GLASS.on() || !RealConfig.GLASS_IMPACTS.on()
                || entity.isSpectator() || entity.isPassenger()) {
            return;
        }
        // Positions are tracked here rather than read from xo/zo because the server resets those for
        // players right before ticking them.
        double[] m = MOTION.computeIfAbsent(entity, e -> new double[]{e.getX(), e.getZ(), 0, 0, 0});
        double vx = entity.getX() - m[0];
        double vz = entity.getZ() - m[1];
        double speed = Math.sqrt(vx * vx + vz * vz);
        double threshold = RealConfig.GLASS_IMPACT_SPEED.val();
        if (m[2] >= threshold && m[2] < 4.0 && speed < m[2] * 0.5) {
            crashThrough(level, entity, m[3], m[4]);
        }
        m[0] = entity.getX();
        m[1] = entity.getZ();
        m[2] = speed;
        if (speed > 1.0E-4) {
            m[3] = vx / speed;
            m[4] = vz / speed;
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !RealConfig.GLASS.on() || !RealConfig.GLASS_IMPACTS.on()
                || event.getDistance() < RealConfig.GLASS_FALL_DISTANCE.val()) {
            return;
        }
        AABB box = entity.getBoundingBox();
        int y = Mth.floor(box.minY - 0.2);
        boolean broke = false;
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (state.is(ModTags.SHATTERABLE)) {
                    shatter(level, pos, state, new Vec3(0, -1, 0));
                    broke = true;
                }
            }
        }
        if (broke) {
            // The glass absorbs part of the impact, but you go through it.
            event.setDamageMultiplier(event.getDamageMultiplier() * 0.5F);
            cut(level, entity);
        }
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !RealConfig.GLASS.on()) {
            return;
        }
        int radius = RealConfig.GLASS_SHOCKWAVE_RADIUS.val();
        if (radius <= 0) {
            return;
        }
        Vec3 center = event.getExplosion().getPosition();
        BlockPos origin = BlockPos.containing(center);
        double radiusSqr = (double) radius * radius;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            double distanceSqr = pos.distToCenterSqr(center);
            if (distanceSqr > radiusSqr || !level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.is(ModTags.SHATTERABLE)) {
                continue;
            }
            double chance = 1.0 - Math.sqrt(distanceSqr) / radius * 0.7;
            if (level.random.nextDouble() < chance) {
                shatter(level, pos.immutable(), state, Vec3.atCenterOf(pos).subtract(center));
            }
        }
    }

    private static void crashThrough(ServerLevel level, LivingEntity entity, double dirX, double dirZ) {
        AABB box = entity.getBoundingBox().expandTowards(dirX * 0.6, 0, dirZ * 0.6);
        boolean broke = false;
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY - 1.0E-3), Mth.floor(box.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModTags.SHATTERABLE)) {
                shatter(level, pos.immutable(), state, new Vec3(dirX, 0, dirZ));
                broke = true;
            }
        }
        if (broke) {
            cut(level, entity);
        }
    }

    private static void cut(ServerLevel level, LivingEntity entity) {
        float damage = RealConfig.GLASS_CUT_DAMAGE.valF();
        if (damage > 0) {
            entity.hurt(ModDamageTypes.glassShards(level), damage);
        }
    }

    public static void shatter(ServerLevel level, BlockPos pos, BlockState state, Vec3 direction) {
        level.destroyBlock(pos, false);
        Vec3 dir = direction.lengthSqr() > 1.0E-6 ? direction.normalize() : Vec3.ZERO;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                pos.getX() + 0.5 + dir.x * 0.5, pos.getY() + 0.5 + dir.y * 0.5, pos.getZ() + 0.5 + dir.z * 0.5,
                24, 0.3, 0.3, 0.3, 0.2);
    }
}
