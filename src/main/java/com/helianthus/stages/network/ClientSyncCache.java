package com.helianthus.stages.network;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Latest state received from the server, for client HUD and UI code to read.
 */
public final class ClientSyncCache
{
    private static final Set<String> unlockedStages = new TreeSet<>();
    private static final Map<BlockPos, SyncCeremonyPacket> ceremonies = new HashMap<>();

    private ClientSyncCache() {}

    public static void setUnlockedStages(List<String> stages)
    {
        unlockedStages.clear();
        unlockedStages.addAll(stages);
    }

    public static Set<String> getUnlockedStages()
    {
        return Set.copyOf(unlockedStages);
    }

    public static void putCeremony(SyncCeremonyPacket ceremony)
    {
        ceremonies.put(ceremony.altarPos(), ceremony);
    }

    public static SyncCeremonyPacket getCeremony(BlockPos altarPos)
    {
        return ceremonies.get(altarPos);
    }
}
