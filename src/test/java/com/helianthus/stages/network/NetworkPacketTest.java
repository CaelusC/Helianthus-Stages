package com.helianthus.stages.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkPacketTest
{
    @Test
    void stagesRoundTrip()
    {
        SyncStagesPacket packet = new SyncStagesPacket(List.of("novice", "adept", "master"));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buf);
        assertEquals(packet, SyncStagesPacket.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void emptyStagesRoundTrip()
    {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new SyncStagesPacket(List.of()).encode(buf);
        assertEquals(new SyncStagesPacket(List.of()), SyncStagesPacket.decode(buf));
    }

    @Test
    void ceremonyRoundTrip()
    {
        SyncCeremonyPacket packet = new SyncCeremonyPacket(new BlockPos(-12, 64, 300000), "novice", 42,
                Map.of("sun", true, "moon", false));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buf);
        assertEquals(packet, SyncCeremonyPacket.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void sealedCeremonyRoundTrip()
    {
        SyncCeremonyPacket packet = new SyncCeremonyPacket(BlockPos.ZERO, "novice", 0, Map.of(), "shared_monopoly");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buf);
        assertEquals(packet, SyncCeremonyPacket.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void idleCeremonyRoundTrip()
    {
        SyncCeremonyPacket packet = new SyncCeremonyPacket(BlockPos.ZERO, "", 0, Map.of());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buf);
        assertEquals(packet, SyncCeremonyPacket.decode(buf));
    }
}
