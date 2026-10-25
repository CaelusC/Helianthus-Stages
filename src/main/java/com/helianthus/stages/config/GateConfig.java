package com.helianthus.stages.config;

import java.util.List;

public record GateConfig(
        double radius,
        int quorumPerHouse,
        int commitTimeoutSeconds,
        int ceremonySeconds,
        int abortCooldownSeconds,
        boolean requireSameDimension,
        List<String> allowedGameModes,
        int tickIntervalTicks)
{
    public GateConfig
    {
        allowedGameModes = allowedGameModes == null ? null : List.copyOf(allowedGameModes);
    }

    public static GateConfig defaultConfig()
    {
        return new GateConfig(8.0, 1, 30, 10, 60, true, List.of("survival", "adventure"), 20);
    }
}
