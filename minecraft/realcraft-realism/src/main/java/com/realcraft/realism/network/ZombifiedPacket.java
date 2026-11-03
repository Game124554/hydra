package com.realcraft.realism.network;

import com.realcraft.realism.zombie.ZombifiedState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: whether the player with this entity id is zombified (drives the zombie skin). */
public record ZombifiedPacket(int entityId, boolean zombified) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(zombified);
    }

    public static ZombifiedPacket decode(FriendlyByteBuf buf) {
        return new ZombifiedPacket(buf.readVarInt(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ZombifiedState.setOnClient(entityId, zombified);
    }
}
