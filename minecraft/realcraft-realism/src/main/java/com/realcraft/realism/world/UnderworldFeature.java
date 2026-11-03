package com.realcraft.realism.world;

import com.mojang.serialization.Codec;
import com.realcraft.realism.config.RealConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Runs once per chunk at the last decoration step (see the biome modifiers in
 * data/realcraft/forge/biome_modifier). It
 * <ul>
 *     <li>replaces bedrock with a configurable block in the overworld and the nether, and</li>
 *     <li>turns the deepest overworld layers into hell: netherrack, magma, soul soil and basalt
 *     patches, burning floors, glowstone ceilings and lava lakes. Water boils away.</li>
 * </ul>
 */
public class UnderworldFeature extends Feature<NoneFeatureConfiguration> {
    private static final int TRANSITION = 4;
    private static volatile Noise noise;

    public UnderworldFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        boolean overworld = level.getLevel().dimension() == Level.OVERWORLD;
        boolean nether = level.getLevel().dimension() == Level.NETHER;
        if (!overworld && !nether) {
            return false;
        }

        int chunkX = ctx.origin().getX() & ~15;
        int chunkZ = ctx.origin().getZ() & ~15;
        ChunkAccess chunk = level.getChunk(chunkX >> 4, chunkZ >> 4);
        RandomSource random = ctx.random();
        boolean changed = false;

        if (overworld && RealConfig.REMOVE_BEDROCK.on()) {
            changed |= replaceBedrock(level, chunk, chunkX, chunkZ, level.getMinBuildHeight(), level.getMinBuildHeight() + 6,
                    RealConfig.overworldBedrockReplacement());
        }
        if (nether && RealConfig.REMOVE_NETHER_BEDROCK.on()) {
            int floor = level.getMinBuildHeight();
            int roof = floor + level.dimensionType().logicalHeight();
            BlockState replacement = RealConfig.netherBedrockReplacement();
            changed |= replaceBedrock(level, chunk, chunkX, chunkZ, floor, floor + 6, replacement);
            changed |= replaceBedrock(level, chunk, chunkX, chunkZ, roof - 6, roof, replacement);
        }
        if (overworld && RealConfig.HELL_LAYER.on()) {
            changed |= carveHell(level, chunk, chunkX, chunkZ, random);
        }
        return changed;
    }

    private static boolean replaceBedrock(WorldGenLevel level, ChunkAccess chunk, int chunkX, int chunkZ, int fromY, int toY, BlockState replacement) {
        boolean changed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunkX; x < chunkX + 16; x++) {
            for (int z = chunkZ; z < chunkZ + 16; z++) {
                for (int y = fromY; y < toY; y++) {
                    pos.set(x, y, z);
                    if (chunk.getBlockState(pos).is(Blocks.BEDROCK)) {
                        level.setBlock(pos, replacement, Block.UPDATE_CLIENTS);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static boolean carveHell(WorldGenLevel level, ChunkAccess chunk, int chunkX, int chunkZ, RandomSource random) {
        Noise n = noise(level.getSeed());
        int minY = level.getMinBuildHeight();
        int baseTop = RealConfig.HELL_TOP_Y.val();
        int lavaLevel = RealConfig.HELL_LAVA_LEVEL.val();
        double fireChance = RealConfig.HELL_FIRE_CHANCE.val();
        double glowChance = RealConfig.HELL_GLOWSTONE_CHANCE.val();
        boolean convertOres = RealConfig.HELL_CONVERT_ORES.on();
        if (baseTop + 6 < minY) {
            return false;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos other = new BlockPos.MutableBlockPos();
        for (int x = chunkX; x < chunkX + 16; x++) {
            for (int z = chunkZ; z < chunkZ + 16; z++) {
                int top = baseTop + (int) Math.round(n.top.getValue(x / 64.0, z / 64.0) * 6.0);
                for (int y = minY; y <= top; y++) {
                    int depth = top - y;
                    if (depth < TRANSITION && random.nextInt(TRANSITION) > depth) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);

                    // Water boils away; waterlogged blocks dry out.
                    if (!state.getFluidState().isEmpty() && state.getFluidState().is(FluidTags.WATER)) {
                        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                            level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_CLIENTS);
                            continue;
                        }
                        state = Blocks.AIR.defaultBlockState();
                        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    }

                    if (state.isAir()) {
                        BlockState below = chunk.getBlockState(other.set(x, y - 1, z));
                        BlockState fill = null;
                        if (y <= lavaLevel && (below.is(Blocks.LAVA) || isHellGround(below))) {
                            fill = Blocks.LAVA.defaultBlockState();
                        } else if (isHellGround(below) && random.nextDouble() < fireChance) {
                            fill = below.is(Blocks.SOUL_SOIL) || below.is(Blocks.SOUL_SAND)
                                    ? Blocks.SOUL_FIRE.defaultBlockState()
                                    : Blocks.FIRE.defaultBlockState();
                        } else if (random.nextDouble() < glowChance && isNaturalStone(chunk.getBlockState(other.set(x, y + 1, z)))) {
                            fill = Blocks.GLOWSTONE.defaultBlockState();
                        }
                        if (fill != null) {
                            level.setBlock(pos, fill, Block.UPDATE_CLIENTS);
                        }
                        continue;
                    }

                    BlockState replacement = hellify(state, x, y, z, minY, n, random, convertOres);
                    if (replacement == null && state.ignitedByLava() && state.getCollisionShape(chunk, pos).isEmpty()) {
                        replacement = Blocks.AIR.defaultBlockState(); // plants and vines burn away
                    }
                    if (replacement != null && replacement != state) {
                        level.setBlock(pos, replacement, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return true;
    }

    private static BlockState hellify(BlockState state, int x, int y, int z, int minY, Noise n, RandomSource random, boolean convertOres) {
        double patch = n.patch.getValue(x / 24.0, y / 16.0, z / 24.0);
        if (isNaturalStone(state)) {
            if (y < minY + 6 && random.nextBoolean()) {
                return Blocks.BLACKSTONE.defaultBlockState();
            }
            if (patch > 0.55) {
                return random.nextInt(3) == 0 ? Blocks.SOUL_SAND.defaultBlockState() : Blocks.SOUL_SOIL.defaultBlockState();
            }
            if (patch < -0.55) {
                return random.nextInt(4) == 0 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.BASALT.defaultBlockState();
            }
            int roll = random.nextInt(1000);
            if (roll < 60) {
                return Blocks.MAGMA_BLOCK.defaultBlockState();
            }
            if (convertOres) {
                if (roll < 75) {
                    return Blocks.NETHER_QUARTZ_ORE.defaultBlockState();
                }
                if (roll < 83) {
                    return Blocks.NETHER_GOLD_ORE.defaultBlockState();
                }
                if (roll == 999 && random.nextInt(4) == 0) {
                    return Blocks.ANCIENT_DEBRIS.defaultBlockState();
                }
            }
            return Blocks.NETHERRACK.defaultBlockState();
        }
        if (state.is(BlockTags.DIRT) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(BlockTags.SAND)) {
            return random.nextInt(3) == 0 ? Blocks.SOUL_SAND.defaultBlockState() : Blocks.SOUL_SOIL.defaultBlockState();
        }
        if (convertOres) {
            if (state.is(BlockTags.GOLD_ORES) && !state.is(Blocks.NETHER_GOLD_ORE)) {
                return Blocks.NETHER_GOLD_ORE.defaultBlockState();
            }
            if (state.is(BlockTags.COAL_ORES)) {
                return Blocks.NETHER_QUARTZ_ORE.defaultBlockState();
            }
        }
        return null;
    }

    private static boolean isNaturalStone(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.CALCITE)
                || state.is(Blocks.DRIPSTONE_BLOCK)
                || state.is(Blocks.SMOOTH_BASALT)
                || state.is(Blocks.COBBLED_DEEPSLATE);
    }

    /** Ground that belongs to the hell layer (so lava never floods structures such as ancient cities). */
    private static boolean isHellGround(BlockState state) {
        return state.is(Blocks.NETHERRACK)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.BLACKSTONE)
                || state.is(Blocks.BASALT)
                || state.is(Blocks.SOUL_SOIL)
                || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.NETHER_QUARTZ_ORE)
                || state.is(Blocks.NETHER_GOLD_ORE)
                || isNaturalStone(state);
    }

    private static Noise noise(long seed) {
        Noise current = noise;
        if (current == null || current.seed != seed) {
            current = new Noise(seed);
            noise = current;
        }
        return current;
    }

    private static final class Noise {
        final long seed;
        final SimplexNoise top;
        final SimplexNoise patch;

        Noise(long seed) {
            this.seed = seed;
            this.top = new SimplexNoise(RandomSource.create(seed ^ 0x5DEECE66DL));
            this.patch = new SimplexNoise(RandomSource.create(seed * 31L + 0x2545F4914F6CDD1DL));
        }
    }
}
