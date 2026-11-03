package com.realcraft.realism.physics;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.config.RealConfig;
import com.realcraft.realism.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Explosions throw part of what they destroy: chosen blocks become falling-block entities launched
 * away from the blast centre, and land as blocks wherever they come down.
 */
@Mod.EventBusSubscriber(modid = RealCraft.MOD_ID)
public final class ExplosionDebris {
    private ExplosionDebris() {
    }

    // Lowest priority so other mods get to edit the affected block list first.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !RealConfig.EXPLOSION_DEBRIS.on()) {
            return;
        }
        int max = RealConfig.MAX_DEBRIS.val();
        List<BlockPos> affected = event.getAffectedBlocks();
        if (max <= 0 || affected.isEmpty()) {
            return;
        }
        double chance = RealConfig.DEBRIS_CHANCE.val();
        double speed = RealConfig.DEBRIS_SPEED.val();
        Vec3 center = event.getExplosion().getPosition();
        RandomSource random = level.random;

        int start = random.nextInt(affected.size());
        int spawned = 0;
        for (int i = 0; i < affected.size() && spawned < max; i++) {
            if (random.nextDouble() >= chance) {
                continue;
            }
            BlockPos pos = affected.get((start + i) % affected.size());
            BlockState state = level.getBlockState(pos);
            if (!canFly(level, pos, state)) {
                continue;
            }
            FallingBlockEntity debris = FallingBlockEntity.fall(level, pos, StructuralPhysics.crumble(state));
            Vec3 away = Vec3.atCenterOf(pos).subtract(center);
            double distance = away.length();
            away = distance < 1.0E-3 ? new Vec3(0, 1, 0) : away.scale(1.0 / distance);
            double power = speed * (0.6 + 0.4 * random.nextDouble()) / Math.max(1.0, distance * 0.35);
            debris.setDeltaMovement(
                    away.x * power + (random.nextDouble() - 0.5) * 0.1,
                    Math.abs(away.y) * power * 0.5 + speed * 0.35 + random.nextDouble() * 0.2,
                    away.z * power + (random.nextDouble() - 0.5) * 0.1);
            debris.hurtMarked = true;
            debris.setHurtsEntities(1.0F, 10);
            spawned++;
        }
    }

    private static boolean canFly(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir()
                && !state.liquid()
                && !state.hasBlockEntity()
                && !state.is(Blocks.TNT)
                && !state.is(ModTags.NO_DEBRIS)
                && state.getDestroySpeed(level, pos) >= 0
                && state.isCollisionShapeFullBlock(level, pos);
    }
}
