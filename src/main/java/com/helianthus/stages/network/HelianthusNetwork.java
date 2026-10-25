package com.helianthus.stages.network;

import com.example.examplemod.ExampleMod;
import com.helianthus.stages.state.HelianthusSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Server -> client sync of unlocked stages and altar ceremony progress.
 */
public final class HelianthusNetwork
{
    public static final String PROTOCOL_VERSION = "1";

    public static final int SYNC_STAGES_ID = 0;
    public static final int SYNC_CEREMONY_ID = 1;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ExampleMod.MODID, "main"),
            () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

    private HelianthusNetwork() {}

    public static void register()
    {
        CHANNEL.registerMessage(SYNC_STAGES_ID, SyncStagesPacket.class, SyncStagesPacket::encode, SyncStagesPacket::decode,
                SyncStagesPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(SYNC_CEREMONY_ID, SyncCeremonyPacket.class, SyncCeremonyPacket::encode, SyncCeremonyPacket::decode,
                SyncCeremonyPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        MinecraftForge.EVENT_BUS.addListener(HelianthusNetwork::onPlayerLoggedIn);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), SyncStagesPacket.of(HelianthusSavedData.get(player.serverLevel())));
    }

    // Stages are server-scoped, so every online player gets the update
    public static void syncStagesToAll(ServerLevel level)
    {
        CHANNEL.send(PacketDistributor.ALL.noArg(), SyncStagesPacket.of(HelianthusSavedData.get(level)));
    }

    public static void syncCeremony(LevelChunk chunk, SyncCeremonyPacket packet)
    {
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), packet);
    }
}
