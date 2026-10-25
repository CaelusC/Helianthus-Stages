package com.helianthus.stages.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Ceremony state of one altar. {@code stageId} is empty when no session is running; {@code readiness} is keyed by house team.
 */
public record SyncCeremonyPacket(BlockPos altarPos, String stageId, int progressPercent, Map<String, Boolean> readiness)
{
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeBlockPos(altarPos);
        buf.writeUtf(stageId);
        buf.writeVarInt(progressPercent);
        buf.writeMap(readiness, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeBoolean);
    }

    public static SyncCeremonyPacket decode(FriendlyByteBuf buf)
    {
        return new SyncCeremonyPacket(buf.readBlockPos(), buf.readUtf(), buf.readVarInt(),
                Map.copyOf(buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readBoolean)));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> ClientSyncCache.putCeremony(this));
        ctx.get().setPacketHandled(true);
    }
}
