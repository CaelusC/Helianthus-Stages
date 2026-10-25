package com.helianthus.stages.client;

import com.helianthus.stages.config.HouseConfig;
import com.helianthus.stages.config.StageConfig;
import com.helianthus.stages.network.SyncCeremonyPacket;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Display-ready view of a synced ceremony, kept free of rendering code so it can be tested without a client.
 */
public record CeremonyHudData(String title, int progressPercent, List<HouseStatus> houses)
{
    public record HouseStatus(String name, boolean ready)
    {
        public String label()
        {
            return "[" + name + ": " + (ready ? "READY" : "WAITING") + "]";
        }
    }

    // Closest altar within radius that has a running session
    public static Optional<SyncCeremonyPacket> nearestActive(Collection<SyncCeremonyPacket> ceremonies, double x, double y, double z, double radius)
    {
        SyncCeremonyPacket nearest = null;
        double best = radius * radius;
        for (SyncCeremonyPacket ceremony : ceremonies)
        {
            if (ceremony.stageId().isEmpty())
                continue;
            BlockPos pos = ceremony.altarPos();
            double distSq = pos.distToCenterSqr(x, y, z);
            if (distSq <= best)
            {
                best = distSq;
                nearest = ceremony;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public static CeremonyHudData from(SyncCeremonyPacket ceremony, List<StageConfig> stages, List<HouseConfig> houses)
    {
        String stageName = stages.stream()
                .filter(stage -> stage.id().equals(ceremony.stageId()))
                .map(StageConfig::displayName)
                .findFirst()
                .orElse(ceremony.stageId());

        // Configured houses first in config order, then any synced team the config doesn't know about
        List<HouseStatus> statuses = new ArrayList<>();
        Map<String, Boolean> readiness = ceremony.readiness();
        for (HouseConfig house : houses)
            if (readiness.containsKey(house.team()))
                statuses.add(new HouseStatus(house.name(), readiness.get(house.team())));
        readiness.keySet().stream()
                .filter(team -> houses.stream().noneMatch(house -> house.team().equals(team)))
                .sorted()
                .forEach(team -> statuses.add(new HouseStatus(team, readiness.get(team))));

        int progress = Math.max(0, Math.min(100, ceremony.progressPercent()));
        return new CeremonyHudData("Ceremony in Progress: " + stageName, progress, List.copyOf(statuses));
    }

    public String readinessLine()
    {
        return String.join(" ", houses.stream().map(HouseStatus::label).toList());
    }
}
