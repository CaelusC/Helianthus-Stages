package com.helianthus.stages.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest
{
    private static final String HOUSES = """
            [
              {"name": "Sun", "team": "sun", "color": "gold"},
              {"name": "Moon", "team": "moon", "color": "aqua"}
            ]
            """;
    private static final String GATE = """
            {
              "radius": 5.5,
              "quorumPerHouse": 2,
              "commitTimeoutSeconds": 15,
              "ceremonySeconds": 8,
              "abortCooldownSeconds": 30,
              "requireSameDimension": false,
              "allowedGameModes": ["survival"],
              "tickIntervalTicks": 10
            }
            """;
    private static final String STAGES = """
            [
              {
                "id": "sun_novice",
                "order": 0,
                "displayName": "Novice",
                "astagesStage": "sun_novice",
                "ceremony": {"particles": "flame", "duration": 5},
                "messages": {"unlockTitle": "Novice", "unlockSubtitle": "Sun", "broadcast": "Sun has reached Novice"},
                "onUnlockCommands": ["say hello"]
              },
              {
                "id": "sun_apprentice",
                "order": 1,
                "displayName": "Apprentice",
                "astagesStage": "sun_apprentice",
                "requiresStage": "sun_novice",
                "messages": {"unlockTitle": "Apprentice", "unlockSubtitle": "Sun", "broadcast": "Sun has reached Apprentice"}
              }
            ]
            """;

    @TempDir
    Path tempDir;

    @Test
    void generatesDefaultFilesWhenMissing() throws IOException
    {
        Path dir = tempDir.resolve(ConfigManager.DIRECTORY);

        ConfigManager.load(dir);

        assertTrue(Files.isRegularFile(dir.resolve(ConfigManager.HOUSES_FILE)));
        assertTrue(Files.isRegularFile(dir.resolve(ConfigManager.GATE_FILE)));
        assertTrue(Files.isRegularFile(dir.resolve(ConfigManager.STAGES_FILE)));
        assertEquals(List.of(), ConfigManager.getLastErrors());
        assertEquals(ConfigManager.defaultHouses(), ConfigManager.getHouses());
        assertEquals(GateConfig.defaultConfig(), ConfigManager.getGate());
        assertEquals(ConfigManager.defaultStages(), ConfigManager.getStages());

        assertEquals(3, ConfigManager.getHouses().size());
        assertEquals(12, ConfigManager.getStages().size());
        StageConfig grandmaster = ConfigManager.getStages().get(3);
        assertEquals("red_grandmaster", grandmaster.id());
        assertEquals(3, grandmaster.order());
        assertEquals("red_master", grandmaster.requiresStage());
        assertNull(ConfigManager.getStages().get(0).requiresStage());
    }

    @Test
    void doesNotOverwriteExistingFiles() throws IOException
    {
        writeAll(tempDir);

        ConfigManager.load(tempDir);

        assertEquals(HOUSES, Files.readString(tempDir.resolve(ConfigManager.HOUSES_FILE)));
    }

    @Test
    void loadsValidConfigIntoRecords() throws IOException
    {
        writeAll(tempDir);

        ConfigManager.load(tempDir);

        assertEquals(List.of(), ConfigManager.getLastErrors());
        assertEquals(List.of(new HouseConfig("Sun", "sun", "gold"), new HouseConfig("Moon", "moon", "aqua")),
                ConfigManager.getHouses());
        assertEquals(new GateConfig(5.5, 2, 15, 8, 30, false, List.of("survival"), 10), ConfigManager.getGate());

        List<StageConfig> stages = ConfigManager.getStages();
        assertEquals(2, stages.size());
        StageConfig novice = stages.get(0);
        assertEquals("sun_novice", novice.id());
        assertNull(novice.requiresStage());
        assertEquals(Map.of("particles", "flame", "duration", 5.0), novice.ceremony());
        assertEquals(new StageConfig.StageMessages("Novice", "Sun", "Sun has reached Novice"), novice.messages());
        assertEquals(List.of("say hello"), novice.onUnlockCommands());
        assertEquals("sun_novice", stages.get(1).requiresStage());
        assertEquals(Map.of(), stages.get(1).ceremony());
        assertEquals(List.of(), stages.get(1).onUnlockCommands());
    }

    @Test
    void malformedJsonKeepsPreviousConfig() throws IOException
    {
        writeAll(tempDir);
        ConfigManager.load(tempDir);
        GateConfig previous = ConfigManager.getGate();

        Files.writeString(tempDir.resolve(ConfigManager.GATE_FILE), "{\n  \"radius\": \"wide\"\n}");
        ConfigManager.load(tempDir);

        assertEquals(previous, ConfigManager.getGate());
        assertEquals(1, ConfigManager.getLastErrors().size());
        String error = ConfigManager.getLastErrors().get(0);
        assertTrue(error.startsWith("gate.json: $.radius: "), error);
    }

    @Test
    void truncatedJsonKeepsPreviousConfig() throws IOException
    {
        writeAll(tempDir);
        ConfigManager.load(tempDir);
        List<StageConfig> previous = ConfigManager.getStages();

        Files.writeString(tempDir.resolve(ConfigManager.STAGES_FILE), "[{\"id\": \"broken\"");
        ConfigManager.load(tempDir);

        assertEquals(previous, ConfigManager.getStages());
        assertEquals(1, ConfigManager.getLastErrors().size());
        assertTrue(ConfigManager.getLastErrors().get(0).startsWith("stages.json: "));
    }

    @Test
    void duplicateTeamReportsFieldPathAndKeepsPreviousConfig() throws IOException
    {
        writeAll(tempDir);
        ConfigManager.load(tempDir);
        List<HouseConfig> previous = ConfigManager.getHouses();

        Files.writeString(tempDir.resolve(ConfigManager.HOUSES_FILE), """
                [
                  {"name": "Sun", "team": "sun", "color": "gold"},
                  {"name": "Other Sun", "team": "sun", "color": "yellow"}
                ]
                """);
        ConfigManager.load(tempDir);

        assertEquals(previous, ConfigManager.getHouses());
        assertEquals(List.of("houses.json: $[1].team: duplicate team 'sun'"), ConfigManager.getLastErrors());
    }

    @Test
    void missingRequiredFieldReportsFieldPath() throws IOException
    {
        writeAll(tempDir);
        Files.writeString(tempDir.resolve(ConfigManager.HOUSES_FILE), "[{\"name\": \"Sun\", \"color\": \"gold\"}]");

        ConfigManager.load(tempDir);

        assertEquals(List.of("houses.json: $[0].team: is required"), ConfigManager.getLastErrors());
    }

    @Test
    void parsesParticleAndSoundIdentifiers() throws IOException
    {
        writeAll(tempDir);
        Files.writeString(tempDir.resolve(ConfigManager.GATE_FILE), GATE.replace("\"tickIntervalTicks\": 10", """
                "tickIntervalTicks": 10,
                  "ceremonyParticle": "minecraft:flame",
                  "activeParticle": "end_rod",
                  "completionSound": "minecraft:entity.player.levelup",
                  "ceremonyTickSound": " minecraft:block.beacon.ambient "
                """));

        ConfigManager.load(tempDir);

        assertEquals(List.of(), ConfigManager.getLastErrors());
        GateConfig gate = ConfigManager.getGate();
        assertEquals("minecraft:flame", gate.ceremonyParticle());
        assertEquals("minecraft:end_rod", gate.activeParticle());
        assertEquals("minecraft:entity.player.levelup", gate.completionSound());
        assertEquals("minecraft:block.beacon.ambient", gate.ceremonyTickSound());
    }

    @Test
    void malformedOrMissingParticleAndSoundFallBackToDefaults() throws IOException
    {
        writeAll(tempDir);
        ConfigManager.load(tempDir);
        GateConfig missing = ConfigManager.getGate();

        Files.writeString(tempDir.resolve(ConfigManager.GATE_FILE), GATE.replace("\"tickIntervalTicks\": 10", """
                "tickIntervalTicks": 10,
                  "ceremonyParticle": "Not A Particle!",
                  "activeParticle": "",
                  "completionSound": "bad::sound",
                  "ceremonyTickSound": null
                """));
        ConfigManager.load(tempDir);
        GateConfig malformed = ConfigManager.getGate();

        assertEquals(List.of(), ConfigManager.getLastErrors());
        for (GateConfig gate : List.of(missing, malformed))
        {
            assertEquals(GateConfig.DEFAULT_CEREMONY_PARTICLE, gate.ceremonyParticle());
            assertEquals(GateConfig.DEFAULT_ACTIVE_PARTICLE, gate.activeParticle());
            assertEquals(GateConfig.DEFAULT_COMPLETION_SOUND, gate.completionSound());
            assertEquals(GateConfig.DEFAULT_CEREMONY_TICK_SOUND, gate.ceremonyTickSound());
        }
    }

    @Test
    void parsesOptionalRequiresQuest() throws IOException
    {
        writeAll(tempDir);
        Files.writeString(tempDir.resolve(ConfigManager.STAGES_FILE), STAGES.replace(
                "\"requiresStage\": \"sun_novice\",", "\"requiresStage\": \"sun_novice\", \"requiresQuest\": \"shared_monopoly\","));

        ConfigManager.load(tempDir);

        assertEquals(List.of(), ConfigManager.getLastErrors());
        assertNull(ConfigManager.getStages().get(0).requiresQuest());
        assertEquals("shared_monopoly", ConfigManager.getStages().get(1).requiresQuest());
    }

    private static void writeAll(Path dir) throws IOException
    {
        Files.writeString(dir.resolve(ConfigManager.HOUSES_FILE), HOUSES);
        Files.writeString(dir.resolve(ConfigManager.GATE_FILE), GATE);
        Files.writeString(dir.resolve(ConfigManager.STAGES_FILE), STAGES);
    }

    @Test
    void modpackTemplatesPassValidation() throws IOException
    {
        Path templates = Path.of("modpack_configs", ConfigManager.DIRECTORY);
        Path dir = tempDir.resolve(ConfigManager.DIRECTORY);
        Files.createDirectories(dir);
        for (String file : List.of(ConfigManager.HOUSES_FILE, ConfigManager.GATE_FILE, ConfigManager.STAGES_FILE))
            Files.copy(templates.resolve(file), dir.resolve(file));

        ConfigManager.load(dir);

        assertEquals(List.of(), ConfigManager.getLastErrors());
        assertEquals(3, ConfigManager.getHouses().size());
        assertEquals("minecraft:ui.toast.challenge_complete", ConfigManager.getGate().completionSound());
        assertEquals(List.of("novice_tier", "apprentice_tier", "master_tier"),
                ConfigManager.getStages().stream().map(StageConfig::id).toList());
        assertNull(ConfigManager.getStages().get(0).requiresQuest());
        assertEquals("quest_apprentice_trial", ConfigManager.getStages().get(1).requiresQuest());
        assertEquals("quest_master_trial", ConfigManager.getStages().get(2).requiresQuest());
    }
}
