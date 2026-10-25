package com.helianthus.stages.config;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record GateConfig(
        double radius,
        int quorumPerHouse,
        int commitTimeoutSeconds,
        int ceremonySeconds,
        int abortCooldownSeconds,
        boolean requireSameDimension,
        List<String> allowedGameModes,
        int tickIntervalTicks,
        String ceremonyParticle,
        String activeParticle,
        String completionSound,
        String ceremonyTickSound)
{
    public static final String DEFAULT_CEREMONY_PARTICLE = "minecraft:enchant";
    public static final String DEFAULT_ACTIVE_PARTICLE = "minecraft:portal";
    public static final String DEFAULT_COMPLETION_SOUND = "minecraft:ui.toast.challenge_complete";
    public static final String DEFAULT_CEREMONY_TICK_SOUND = "minecraft:block.enchantment_table.use";

    // Missing or malformed identifiers fall back to the defaults so an old or broken gate.json still loads
    public GateConfig
    {
        allowedGameModes = allowedGameModes == null ? null : List.copyOf(allowedGameModes);
        ceremonyParticle = identifierOr(ceremonyParticle, DEFAULT_CEREMONY_PARTICLE);
        activeParticle = identifierOr(activeParticle, DEFAULT_ACTIVE_PARTICLE);
        completionSound = identifierOr(completionSound, DEFAULT_COMPLETION_SOUND);
        ceremonyTickSound = identifierOr(ceremonyTickSound, DEFAULT_CEREMONY_TICK_SOUND);
    }

    public GateConfig(double radius, int quorumPerHouse, int commitTimeoutSeconds, int ceremonySeconds,
                      int abortCooldownSeconds, boolean requireSameDimension, List<String> allowedGameModes,
                      int tickIntervalTicks)
    {
        this(radius, quorumPerHouse, commitTimeoutSeconds, ceremonySeconds, abortCooldownSeconds,
                requireSameDimension, allowedGameModes, tickIntervalTicks, null, null, null, null);
    }

    public static GateConfig defaultConfig()
    {
        return new GateConfig(8.0, 1, 30, 10, 60, true, List.of("survival", "adventure"), 20,
                DEFAULT_CEREMONY_PARTICLE, DEFAULT_ACTIVE_PARTICLE, DEFAULT_COMPLETION_SOUND, DEFAULT_CEREMONY_TICK_SOUND);
    }

    private static String identifierOr(String value, String fallback)
    {
        ResourceLocation id = value == null || value.isBlank() ? null : ResourceLocation.tryParse(value.trim());
        return id == null ? fallback : id.toString();
    }
}
