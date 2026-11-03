package com.realcraft.realism.physics;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.config.RealConfig;
import com.realcraft.realism.registry.ModTags;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Cave-ins. When a block is mined, placed or blown up, the natural blocks around it are queued
 * for a support check. A block is supported when something solid is directly below it, or, for
 * rock, when a solid path along its own layer reaches a supported block within a few blocks
 * (arches and tunnel ceilings hold, wide unsupported ceilings collapse). Unsupported blocks fall
 * as falling-block entities and their neighbours are re-checked, so collapses spread naturally.
 * Work per tick and the queue size are capped by config so a cave-in can never stall the server.
 */
@Mod.EventBusSubscriber(modid = RealCraft.MOD_ID)
public final class StructuralPhysics {
    private static final Map<ResourceKey<Level>, LinkedHashSet<BlockPos>> QUEUES = new HashMap<>();
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private StructuralPhysics() {
    }

    private enum Kind {NONE, RIGID, LOOSE}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && RealConfig.PHYSICS.on()) {
            scheduleNeighbours(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && RealConfig.PHYSICS.on()) {
            schedule(level, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && RealConfig.PHYSICS.on()) {
            for (BlockPos pos : event.getAffectedBlocks()) {
                scheduleNeighbours(level, pos);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            process(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        QUEUES.clear();
    }

    public static void scheduleNeighbours(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            schedule(level, pos.relative(direction));
        }
    }

    public static void schedule(ServerLevel level, BlockPos pos) {
        LinkedHashSet<BlockPos> queue = QUEUES.computeIfAbsent(level.dimension(), k -> new LinkedHashSet<>());
        if (queue.size() < RealConfig.PHYSICS_QUEUE_LIMIT.val()) {
            queue.add(pos.immutable());
        }
    }

    private static void process(ServerLevel level) {
        LinkedHashSet<BlockPos> queue = QUEUES.get(level.dimension());
        if (queue == null || queue.isEmpty()) {
            return;
        }
        if (!RealConfig.PHYSICS.on()) {
            queue.clear();
            return;
        }
        int maxChecks = RealConfig.PHYSICS_CHECKS_PER_TICK.val();
        int maxFalls = RealConfig.PHYSICS_FALLS_PER_TICK.val();
        int checks = 0;
        int falls = 0;
        List<BlockPos> followUps = new ArrayList<>();

        Iterator<BlockPos> it = queue.iterator();
        while (it.hasNext() && checks < maxChecks && falls < maxFalls) {
            BlockPos pos = it.next();
            it.remove();
            checks++;
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            Kind kind = kind(state);
            if (kind == Kind.NONE || isSupported(level, pos, kind)) {
                continue;
            }
            collapse(level, pos, state);
            falls++;
            for (Direction direction : Direction.values()) {
                if (direction != Direction.DOWN) {
                    followUps.add(pos.relative(direction));
                }
            }
        }
        for (BlockPos pos : followUps) {
            schedule(level, pos);
        }
    }

    private static Kind kind(BlockState state) {
        if (state.isAir()) {
            return Kind.NONE;
        }
        if (state.is(ModTags.PHYSICS_RIGID)) {
            return Kind.RIGID;
        }
        if (state.is(ModTags.PHYSICS_LOOSE)) {
            return Kind.LOOSE;
        }
        return Kind.NONE;
    }

    private static boolean isSupported(ServerLevel level, BlockPos pos, Kind kind) {
        if (hasSupportBelow(level, pos)) {
            return true;
        }
        int span = kind == Kind.RIGID ? RealConfig.ROCK_SPAN.val() : RealConfig.SOIL_SPAN.val();
        if (span <= 0) {
            return false;
        }
        // Breadth-first walk along the block's own layer looking for a supported neighbour.
        LongSet visited = new LongOpenHashSet();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        ArrayDeque<Integer> distances = new ArrayDeque<>();
        visited.add(pos.asLong());
        frontier.add(pos);
        distances.add(0);
        while (!frontier.isEmpty()) {
            BlockPos current = frontier.poll();
            int distance = distances.poll();
            if (distance >= span) {
                continue;
            }
            for (Direction direction : HORIZONTAL) {
                BlockPos next = current.relative(direction);
                if (!visited.add(next.asLong())) {
                    continue;
                }
                if (!level.isLoaded(next)) {
                    return true; // never collapse across unloaded chunks
                }
                BlockState state = level.getBlockState(next);
                if (!isSolid(level, next, state)) {
                    continue;
                }
                if (hasSupportBelow(level, next)) {
                    return true;
                }
                frontier.add(next);
                distances.add(distance + 1);
            }
        }
        return false;
    }

    private static boolean hasSupportBelow(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        if (below.getY() < level.getMinBuildHeight() || !level.isLoaded(below)) {
            return true;
        }
        return isSolid(level, below, level.getBlockState(below));
    }

    private static boolean isSolid(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && !state.liquid() && !state.getCollisionShape(level, pos).isEmpty();
    }

    private static void collapse(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState falling = RealConfig.ROCK_CRUMBLES.on() ? crumble(state) : state;
        FallingBlockEntity entity = FallingBlockEntity.fall(level, pos, falling);
        float damage = RealConfig.FALLING_BLOCK_DAMAGE.valF();
        if (damage > 0) {
            entity.setHurtsEntities(damage, 40);
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, state),
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 6, 0.4, 0.1, 0.4, 0.0);
        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.5F, 0.55F + level.random.nextFloat() * 0.2F);
    }

    /** The block a natural block turns into when it breaks loose. */
    public static BlockState crumble(BlockState state) {
        if (state.is(Blocks.STONE)) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        if (state.is(Blocks.DEEPSLATE)) {
            return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        }
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.DIRT_PATH)) {
            return Blocks.DIRT.defaultBlockState();
        }
        return state;
    }
}
