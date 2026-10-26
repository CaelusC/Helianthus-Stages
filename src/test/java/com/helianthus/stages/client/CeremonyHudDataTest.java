package com.helianthus.stages.client;

import com.helianthus.stages.config.HouseConfig;
import com.helianthus.stages.config.StageConfig;
import com.helianthus.stages.network.ClientSyncCache;
import com.helianthus.stages.network.SyncCeremonyPacket;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CeremonyHudDataTest
{
    private static final List<StageConfig> STAGES = List.of(
            new StageConfig("master", 3, "Master Stage", "master", null, null, null, null));
    private static final List<HouseConfig> HOUSES = List.of(
            new HouseConfig("Red House", "red", "red"),
            new HouseConfig("Blue House", "blue", "blue"));

    @Test
    void formatsCachedCeremony()
    {
        BlockPos pos = new BlockPos(100, 64, -200);
        ClientSyncCache.putCeremony(new SyncCeremonyPacket(pos, "master", 65, Map.of("blue", false, "red", true)));

        CeremonyHudData data = CeremonyHudData.from(ClientSyncCache.getCeremony(pos), STAGES, HOUSES);

        assertEquals("Ceremony in Progress: Master Stage", data.title());
        assertEquals(65, data.progressPercent());
        assertEquals("[Red House: READY] [Blue House: WAITING]", data.readinessLine());
    }

    @Test
    void unknownStageAndTeamFallBackToIds()
    {
        SyncCeremonyPacket packet = new SyncCeremonyPacket(BlockPos.ZERO, "mystery", 150, Map.of("green", true));

        CeremonyHudData data = CeremonyHudData.from(packet, STAGES, HOUSES);

        assertEquals("Ceremony in Progress: mystery", data.title());
        assertEquals(100, data.progressPercent());
        assertEquals("[green: READY]", data.readinessLine());
    }

    @Test
    void sealedAltarShowsQuestMessageWithoutCountdown()
    {
        SyncCeremonyPacket packet = new SyncCeremonyPacket(BlockPos.ZERO, "master", 0, Map.of(), "shared_monopoly");

        CeremonyHudData data = CeremonyHudData.from(packet, STAGES, HOUSES);

        assertEquals("[Altar Sealed: Complete Shared Quest 'shared_monopoly' First]", data.title());
        assertTrue(data.sealed());
        assertEquals(0, data.progressPercent());
        assertFalse(new CeremonyHudData("t", 0, List.of(), false).sealed());
    }

    @Test
    void nearestActiveSkipsIdleAndDistantAltars()
    {
        SyncCeremonyPacket idle = new SyncCeremonyPacket(new BlockPos(0, 64, 0), "", 0, Map.of());
        SyncCeremonyPacket near = new SyncCeremonyPacket(new BlockPos(3, 64, 0), "master", 10, Map.of());
        SyncCeremonyPacket far = new SyncCeremonyPacket(new BlockPos(50, 64, 0), "master", 10, Map.of());

        assertEquals(near, CeremonyHudData.nearestActive(List.of(idle, far, near), 0.5, 64.5, 0.5, 8.0).orElseThrow());
        assertTrue(CeremonyHudData.nearestActive(List.of(idle, far), 0.5, 64.5, 0.5, 8.0).isEmpty());
    }
}
