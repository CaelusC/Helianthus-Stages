package com.helianthus.stages.network;

import com.helianthus.stages.state.HelianthusSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Ids of every unlocked stage.
 */
public record SyncStagesPacket(List<String> unlockedStages)
{
    public static SyncStagesPacket of(HelianthusSavedData data)
    {
        return new SyncStagesPacket(data.getStages().entrySet().stream()
                .filter(entry -> entry.getValue().unlocked())
                .map(Map.Entry::getKey)
                .sorted()
                .toList());
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeCollection(unlockedStages, FriendlyByteBuf::writeUtf);
    }

    public static SyncStagesPacket decode(FriendlyByteBuf buf)
    {
        return new SyncStagesPacket(List.copyOf(buf.readList(FriendlyByteBuf::readUtf)));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> ClientSyncCache.setUnlockedStages(unlockedStages));
        ctx.get().setPacketHandled(true);
    }
}
