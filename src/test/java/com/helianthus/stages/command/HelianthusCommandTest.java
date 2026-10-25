package com.helianthus.stages.command;

import com.helianthus.stages.config.ConfigManager;
import com.helianthus.stages.state.HelianthusSavedData;
import com.helianthus.stages.state.HouseReadinessState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelianthusCommandTest
{
    private record Source(boolean operator)
    {
    }

    private final List<String> calls = new ArrayList<>();
    private final CommandDispatcher<Source> dispatcher = new CommandDispatcher<>();

    @TempDir
    Path dir;

    @BeforeEach
    void setUp()
    {
        ConfigManager.load(dir);
        dispatcher.register(HelianthusCommand.build(Source::operator, new HelianthusCommand.Actions<>()
        {
            @Override
            public int reload(Source source)
            {
                calls.add("reload");
                return 1;
            }

            @Override
            public int status(Source source)
            {
                calls.add("status");
                return 1;
            }

            @Override
            public int forceUnlock(Source source, String stageId)
            {
                calls.add("force_unlock:" + stageId);
                return 1;
            }
        }));
    }

    @Test
    void operatorCanRunEverySubcommand() throws CommandSyntaxException
    {
        Source op = new Source(true);
        dispatcher.execute("helianthus reload", op);
        dispatcher.execute("helianthus status", op);
        dispatcher.execute("helianthus force_unlock red_novice", op);
        assertEquals(List.of("reload", "status", "force_unlock:red_novice"), calls);
    }

    @Test
    void nonOperatorOnlyGetsStatus() throws CommandSyntaxException
    {
        Source player = new Source(false);
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("helianthus reload", player));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("helianthus force_unlock red_novice", player));
        dispatcher.execute("helianthus status", player);
        assertEquals(List.of("status"), calls);
    }

    @Test
    void invalidArgumentsAreRejected()
    {
        Source op = new Source(true);
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("helianthus force_unlock", op));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("helianthus force_unlock a b", op));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("helianthus nope", op));
        assertTrue(calls.isEmpty());
    }

    @Test
    void forceUnlockUnlocksAndRewardsOnce()
    {
        HelianthusSavedData data = new HelianthusSavedData();
        List<String> rewarded = new ArrayList<>();

        assertNull(HelianthusCommand.forceUnlock(data, ConfigManager.getStages(), "red_novice", 42L, rewarded::add));
        assertTrue(data.getStage("red_novice").unlocked());
        assertEquals(42L, data.getStage("red_novice").unlockedAtEpochMs());
        assertEquals(List.of("red_novice"), rewarded);

        assertEquals("Stage 'red_novice' is already unlocked",
                HelianthusCommand.forceUnlock(data, ConfigManager.getStages(), "red_novice", 43L, rewarded::add));
        assertEquals(List.of("red_novice"), rewarded);
    }

    @Test
    void forceUnlockRejectsUnknownStage()
    {
        HelianthusSavedData data = new HelianthusSavedData();
        List<String> rewarded = new ArrayList<>();
        assertEquals("Unknown stage 'missing'", HelianthusCommand.forceUnlock(data, ConfigManager.getStages(), "missing", 1L, rewarded::add));
        assertTrue(data.getStages().isEmpty());
        assertTrue(rewarded.isEmpty());
    }

    @Test
    void statusShowsLocksAndReadiness()
    {
        HelianthusSavedData data = new HelianthusSavedData();
        data.unlockStage("red_novice", 1L, List.of());
        data.setReadiness("red_apprentice", "red", new HouseReadinessState(true, 2L, "altar"));

        List<String> lines = HelianthusCommand.statusLines(data, ConfigManager.getStages(), ConfigManager.getHouses());
        assertTrue(lines.contains("red_novice (Novice): unlocked"));
        int apprentice = lines.indexOf("red_apprentice (Apprentice): locked");
        assertTrue(apprentice >= 0);
        assertEquals("  Red House: ready", lines.get(apprentice + 1));
        assertEquals("  Blue House: not ready", lines.get(apprentice + 2));
    }

    @Test
    void reloadReportsValidationErrors() throws Exception
    {
        assertEquals(1, HelianthusCommand.reloadLines(ConfigManager.getLastErrors()).size());

        Files.writeString(dir.resolve(ConfigManager.GATE_FILE), "{\"radius\": 0}");
        ConfigManager.load(dir);
        List<String> lines = HelianthusCommand.reloadLines(ConfigManager.getLastErrors());
        assertTrue(lines.get(0).startsWith("Reload finished with 1 error(s)"));
        assertTrue(lines.get(1).contains("gate.json: $.radius"));
    }
}
