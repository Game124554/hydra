package com.realcraft.realism.zombie;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Whether a player has turned into a zombie. The server keeps it in the player's persistent data
 * (saved with the player and copied on respawn); clients learn it through {@code ZombifiedPacket}.
 */
public final class ZombifiedState {
    public static final ResourceLocation ZOMBIE_SKIN = new ResourceLocation("minecraft", "textures/entity/zombie/zombie.png");
    private static final String ZOMBIFIED = "realcraft.zombified";
    private static final String CURING = "realcraft.curing";
    /** Entity ids of zombified players, as known by this client. Client thread only. */
    private static final IntSet CLIENT_ZOMBIFIED = new IntOpenHashSet();

    private ZombifiedState() {
    }

    public static boolean isZombified(Player player) {
        return player.getPersistentData().getBoolean(ZOMBIFIED);
    }

    public static void setZombified(Player player, boolean zombified) {
        player.getPersistentData().putBoolean(ZOMBIFIED, zombified);
    }

    /** Ticks left until a zombified player is cured, or 0 when no cure is in progress. */
    public static int curingTicks(Player player) {
        return player.getPersistentData().getInt(CURING);
    }

    public static void setCuringTicks(Player player, int ticks) {
        player.getPersistentData().putInt(CURING, Math.max(0, ticks));
    }

    public static void copy(Player from, Player to) {
        setZombified(to, isZombified(from));
        setCuringTicks(to, curingTicks(from));
    }

    public static boolean isZombifiedOnClient(int entityId) {
        return !CLIENT_ZOMBIFIED.isEmpty() && CLIENT_ZOMBIFIED.contains(entityId);
    }

    public static void setOnClient(int entityId, boolean zombified) {
        if (zombified) {
            CLIENT_ZOMBIFIED.add(entityId);
        } else {
            CLIENT_ZOMBIFIED.remove(entityId);
        }
    }

    public static void clearClient() {
        CLIENT_ZOMBIFIED.clear();
    }
}
