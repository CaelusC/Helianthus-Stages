package com.helianthus.stages.ceremony;

import com.helianthus.stages.config.ConfigManager;
import com.helianthus.stages.config.StageConfig;
import com.helianthus.stages.integration.AStagesBridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CeremonyRewardHandlerTest
{
    private final List<String> calls = new ArrayList<>();

    private final AStagesBridge astages = new AStagesBridge(new AStagesBridge.Backend()
    {
        @Override
        public void add(String stage)
        {
            calls.add("grant:" + stage);
        }

        @Override
        public void remove(String stage)
        {
            calls.add("revoke:" + stage);
        }

        @Override
        public boolean has(String stage)
        {
            return false;
        }
    });

    private final CeremonyRewardHandler.Target target = new CeremonyRewardHandler.Target()
    {
        @Override
        public void sendTitle(String title, String subtitle)
        {
            calls.add("title:" + title + "|" + subtitle);
        }

        @Override
        public void broadcast(String message)
        {
            calls.add("broadcast:" + message);
        }

        @Override
        public void runCommand(String command)
        {
            calls.add("command:" + command);
        }
    };

    @TempDir
    Path dir;

    @BeforeEach
    void loadDefaults()
    {
        ConfigManager.load(dir);
    }

    @Test
    void completionGrantsStageThenAnnounces()
    {
        // Defaults: red_novice -> AStages "red_novice", messages "Novice" / "Red House" / "Red House has reached Novice"
        assertTrue(new CeremonyRewardHandler(astages).reward(target, "red_novice"));
        assertEquals(List.of(
                "grant:red_novice",
                "title:Novice|Red House",
                "broadcast:Red House has reached Novice"), calls);
    }

    @Test
    void unknownStageDoesNothing()
    {
        assertFalse(new CeremonyRewardHandler(astages).reward(target, "missing"));
        assertTrue(calls.isEmpty());
    }

    @Test
    void placeholdersAreReplaced()
    {
        StageConfig stage = new StageConfig("red_master", 2, "Master", "red_master", null, Map.of(), null, List.of());
        assertEquals("say red_master reached Master", CeremonyRewardHandler.replace("say {stage} reached {displayName}", stage));
        assertNull(CeremonyRewardHandler.replace(null, stage));
    }

    @Test
    void unlockCommandsRunAfterMessages() throws Exception
    {
        Files.writeString(dir.resolve(ConfigManager.STAGES_FILE), """
                [{"id":"red_novice","order":0,"displayName":"Novice","astagesStage":"red_novice",
                  "messages":{"unlockTitle":"Novice","broadcast":""},
                  "onUnlockCommands":["give @a minecraft:bread","say {stage} done"]}]
                """);
        ConfigManager.load(dir);

        assertTrue(new CeremonyRewardHandler(astages).reward(target, "red_novice"));
        assertEquals(List.of(
                "grant:red_novice",
                "title:Novice|null",
                "command:give @a minecraft:bread",
                "command:say red_novice done"), calls);
    }
}
