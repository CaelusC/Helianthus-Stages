package com.helianthus.stages.integration;

import com.helianthus.stages.config.HouseConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeTest
{
    private static final List<HouseConfig> HOUSES = List.of(
            new HouseConfig("Sun", "sun", "gold"),
            new HouseConfig("Moon", "moon", "aqua"));

    @Test
    void astagesDelegatesToBackend()
    {
        Set<String> stages = new HashSet<>();
        List<String> calls = new ArrayList<>();
        AStagesBridge bridge = new AStagesBridge(new AStagesBridge.Backend()
        {
            @Override
            public void add(String stage)
            {
                calls.add("add:" + stage);
                stages.add(stage);
            }

            @Override
            public void remove(String stage)
            {
                calls.add("remove:" + stage);
                stages.remove(stage);
            }

            @Override
            public boolean has(String stage)
            {
                calls.add("has:" + stage);
                return stages.contains(stage);
            }
        });

        assertTrue(bridge.isAvailable());
        bridge.addServerStage("novice");
        assertTrue(bridge.serverHasStage("novice"));
        bridge.removeServerStage("novice");
        assertFalse(bridge.serverHasStage("novice"));
        assertEquals(List.of("add:novice", "has:novice", "remove:novice", "has:novice"), calls);
    }

    @Test
    void astagesMissingIsNoOp()
    {
        AStagesBridge bridge = new AStagesBridge(null);

        assertFalse(bridge.isAvailable());
        bridge.addServerStage("novice");
        bridge.removeServerStage("novice");
        assertFalse(bridge.serverHasStage("novice"));
    }

    @Test
    void teamsMapsShortNameToHouse()
    {
        TeamsBridge bridge = new TeamsBridge(player -> Optional.of("moon"), () -> HOUSES);

        assertTrue(bridge.isAvailable());
        assertEquals(Optional.of("moon"), bridge.getTeamShortName(null));
        assertEquals(Optional.of(HOUSES.get(1)), bridge.getHouse(null));
    }

    @Test
    void teamsUnknownTeamHasNoHouse()
    {
        TeamsBridge bridge = new TeamsBridge(player -> Optional.of("stars"), () -> HOUSES);

        assertEquals(Optional.of("stars"), bridge.getTeamShortName(null));
        assertEquals(Optional.empty(), bridge.getHouse(null));
    }

    @Test
    void teamsMissingReturnsEmpty()
    {
        TeamsBridge bridge = new TeamsBridge(null, () -> HOUSES);

        assertFalse(bridge.isAvailable());
        assertEquals(Optional.empty(), bridge.getTeamShortName(null));
        assertEquals(Optional.empty(), bridge.getHouse(null));
    }
}
