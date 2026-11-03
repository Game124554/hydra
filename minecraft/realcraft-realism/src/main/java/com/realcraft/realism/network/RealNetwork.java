package com.realcraft.realism.network;

import com.realcraft.realism.RealCraft;
import com.realcraft.realism.zombie.ZombifiedState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class RealNetwork {
    private static final String PROTOCOL = "1";
    // Either side may lack the mod: the client-only features keep working on vanilla servers.
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(RealCraft.id("main"),
            () -> PROTOCOL, NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));

    private RealNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(ZombifiedPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ZombifiedPacket::encode)
                .decoder(ZombifiedPacket::decode)
                .consumerMainThread(ZombifiedPacket::handle)
                .add();
    }

    /** Tells the player and everyone who can see them whether they are zombified. */
    public static void syncZombified(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new ZombifiedPacket(player.getId(), ZombifiedState.isZombified(player)));
    }

    /** Tells one player about another player's state (when they start seeing them). */
    public static void sendZombified(ServerPlayer receiver, Player subject) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> receiver),
                new ZombifiedPacket(subject.getId(), ZombifiedState.isZombified(subject)));
    }
}
