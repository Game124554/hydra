package com.realcraft.realism.world;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.config.RealConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * With the bedrock gone the world has a bottom you can fall through. Dropping out of the bottom
 * of the overworld lands you under the Nether's ceiling (at the matching Nether coordinates);
 * dropping out of the bottom of the Nether drops you out of the overworld sky. Slow falling
 * keeps the arrival survivable.
 */
@Mod.EventBusSubscriber(modid = RealCraft.MOD_ID)
public final class AbyssTravel {
    private AbyssTravel() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (player.getY() >= level.getMinBuildHeight() - 6) {
            return;
        }
        if (level.dimension() == Level.OVERWORLD && RealConfig.DIG_TO_NETHER.on()) {
            ServerLevel nether = player.server.getLevel(Level.NETHER);
            if (nether != null) {
                dropIntoNether(player, level, nether);
            }
        } else if (level.dimension() == Level.NETHER && RealConfig.NETHER_VOID_TO_OVERWORLD.on()) {
            dropIntoOverworld(player, level, player.server.overworld());
        }
    }

    private static void dropIntoNether(ServerPlayer player, ServerLevel from, ServerLevel nether) {
        double scale = DimensionType.getTeleportationScale(from.dimensionType(), nether.dimensionType());
        int x = clampToBorder(nether.getWorldBorder(), Mth.floor(player.getX() * scale), true);
        int z = clampToBorder(nether.getWorldBorder(), Mth.floor(player.getZ() * scale), false);
        BlockPos spot = findSpaceUnderCeiling(nether, x, z);
        player.teleportTo(nether, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0));
        player.displayClientMessage(Component.translatableWithFallback("realcraft.abyss.nether",
                "You fell through the bottom of the world... into the Nether.").withStyle(ChatFormatting.RED), true);
    }

    private static void dropIntoOverworld(ServerPlayer player, ServerLevel from, ServerLevel overworld) {
        double scale = DimensionType.getTeleportationScale(from.dimensionType(), overworld.dimensionType());
        int x = clampToBorder(overworld.getWorldBorder(), Mth.floor(player.getX() * scale), true);
        int z = clampToBorder(overworld.getWorldBorder(), Mth.floor(player.getZ() * scale), false);
        overworld.getChunk(x >> 4, z >> 4);
        int surface = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        double y = Math.min(surface + 48, overworld.getMaxBuildHeight() - 2);
        player.teleportTo(overworld, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0));
        player.displayClientMessage(Component.translatableWithFallback("realcraft.abyss.overworld",
                "You fell out of the Nether... and out of the sky.").withStyle(ChatFormatting.AQUA), true);
    }

    private static int clampToBorder(WorldBorder border, int value, boolean xAxis) {
        double min = (xAxis ? border.getMinX() : border.getMinZ()) + 16;
        double max = (xAxis ? border.getMaxX() : border.getMaxZ()) - 16;
        return Mth.floor(Mth.clamp(value, min, max));
    }

    /**
     * Searches down from the Nether roof for the first two-block air gap below solid ground, i.e.
     * the top of an open cavern, near (x, z). Carves a pocket if nothing is found.
     */
    private static BlockPos findSpaceUnderCeiling(ServerLevel nether, int x, int z) {
        int floor = nether.getMinBuildHeight();
        int roof = floor + nether.dimensionType().logicalHeight() - 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int radius = 0; radius <= 16; radius += 8) {
            for (int dx = -radius; dx <= radius; dx += Math.max(1, radius)) {
                for (int dz = -radius; dz <= radius; dz += Math.max(1, radius)) {
                    int cx = x + dx;
                    int cz = z + dz;
                    nether.getChunk(cx >> 4, cz >> 4);
                    boolean underSolid = false;
                    for (int y = roof; y > floor + 4; y--) {
                        if (!nether.getBlockState(pos.set(cx, y, cz)).isAir()) {
                            underSolid = true;
                        } else if (underSolid && nether.getBlockState(pos.set(cx, y - 1, cz)).isAir()) {
                            return new BlockPos(cx, y - 1, cz);
                        }
                    }
                }
            }
        }
        BlockPos pocket = new BlockPos(x, floor + 100, z);
        nether.setBlock(pocket, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        nether.setBlock(pocket.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return pocket;
    }
}
