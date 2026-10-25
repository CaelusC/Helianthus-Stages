package com.helianthus.stages.block.entity;

import com.helianthus.stages.config.GateConfig;
import com.helianthus.stages.state.ParticipantRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.helianthus.stages.block.entity.AltarCeremony.Event;
import static com.helianthus.stages.block.entity.AltarCeremony.Phase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AltarCeremonyTest
{
    // quorum 2, commit 30s, ceremony 10s, cooldown 60s
    private static final GateConfig GATE = new GateConfig(8.0, 2, 30, 10, 60, true, List.of("survival"), 20);
    private static final List<String> HOUSES = List.of("sun", "moon");
    private static final String STAGE = "novice";

    private static List<ParticipantRecord> players(String house, int count)
    {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new ParticipantRecord(UUID.randomUUID(), house + i, house)).toList();
    }

    private static final Map<String, List<ParticipantRecord>> FULL = Map.of("sun", players("sun", 2), "moon", players("moon", 2));
    private static final Map<String, List<ParticipantRecord>> PARTIAL = Map.of("sun", players("sun", 2), "moon", players("moon", 1));

    @Test
    void staysIdleWithNobodyPresentOrNoStage()
    {
        AltarCeremony ceremony = new AltarCeremony();
        assertEquals(Event.NONE, ceremony.evaluate(0, STAGE, HOUSES, Map.of(), GATE));
        assertEquals(Event.NONE, ceremony.evaluate(0, null, HOUSES, FULL, GATE));
        assertEquals(Phase.IDLE, ceremony.getPhase());
    }

    @Test
    void progressesFromIdleThroughCeremonyToCompletion()
    {
        AltarCeremony ceremony = new AltarCeremony();

        assertEquals(Event.COMMIT_STARTED, ceremony.evaluate(0, STAGE, HOUSES, PARTIAL, GATE));
        assertEquals(Phase.COMMITTING, ceremony.getPhase());
        assertEquals(Set.of("sun"), ceremony.getReadyHouses());

        assertEquals(Event.CEREMONY_STARTED, ceremony.evaluate(20, STAGE, HOUSES, FULL, GATE));
        assertEquals(Phase.CEREMONY, ceremony.getPhase());
        assertEquals(Set.of("sun", "moon"), ceremony.getReadyHouses());
        assertEquals(20 + 10 * 20, ceremony.getDeadline());

        assertEquals(Event.NONE, ceremony.evaluate(200, STAGE, HOUSES, FULL, GATE));
        assertEquals(Event.COMPLETED, ceremony.evaluate(220, STAGE, HOUSES, FULL, GATE));
        assertEquals(Phase.IDLE, ceremony.getPhase());
        assertEquals(4, ceremony.getParticipants().size());
        assertTrue(ceremony.getReadyHouses().isEmpty());
    }

    @Test
    void commitTimeoutAbortsIntoCooldownThenResets()
    {
        AltarCeremony ceremony = new AltarCeremony();
        ceremony.evaluate(0, STAGE, HOUSES, PARTIAL, GATE);

        assertEquals(Event.NONE, ceremony.evaluate(599, STAGE, HOUSES, PARTIAL, GATE));
        assertEquals(Event.ABORTED, ceremony.evaluate(600, STAGE, HOUSES, PARTIAL, GATE));
        assertEquals(Phase.COOLDOWN, ceremony.getPhase());
        assertTrue(ceremony.getReadyHouses().isEmpty());
        assertEquals("", ceremony.getStageId());

        // Cooldown ignores a full quorum until it expires
        assertEquals(Event.NONE, ceremony.evaluate(1799, STAGE, HOUSES, FULL, GATE));
        assertEquals(Event.COOLDOWN_ENDED, ceremony.evaluate(1800, STAGE, HOUSES, FULL, GATE));
        assertEquals(Phase.IDLE, ceremony.getPhase());
        assertEquals(Event.COMMIT_STARTED, ceremony.evaluate(1820, STAGE, HOUSES, FULL, GATE));
    }

    @Test
    void losingQuorumDuringCeremonyAborts()
    {
        AltarCeremony ceremony = new AltarCeremony();
        ceremony.evaluate(0, STAGE, HOUSES, FULL, GATE);
        assertEquals(Event.CEREMONY_STARTED, ceremony.evaluate(20, STAGE, HOUSES, FULL, GATE));

        assertEquals(Event.ABORTED, ceremony.evaluate(40, STAGE, HOUSES, PARTIAL, GATE));
        assertEquals(Phase.COOLDOWN, ceremony.getPhase());
        assertEquals(40 + 60 * 20, ceremony.getDeadline());
    }

    @Test
    void stageChangingMidSessionAborts()
    {
        AltarCeremony ceremony = new AltarCeremony();
        ceremony.evaluate(0, STAGE, HOUSES, PARTIAL, GATE);
        assertEquals(Event.ABORTED, ceremony.evaluate(20, "apprentice", HOUSES, PARTIAL, GATE));
    }

    @Test
    void roundTripsThroughNbt()
    {
        AltarCeremony ceremony = new AltarCeremony();
        ceremony.evaluate(0, STAGE, HOUSES, FULL, GATE);
        ceremony.evaluate(20, STAGE, HOUSES, FULL, GATE);

        AltarCeremony loaded = new AltarCeremony();
        loaded.load(ceremony.save());

        assertEquals(Phase.CEREMONY, loaded.getPhase());
        assertEquals(STAGE, loaded.getStageId());
        assertEquals(ceremony.getDeadline(), loaded.getDeadline());
        assertEquals(Set.of("sun", "moon"), loaded.getReadyHouses());
        assertEquals(Event.COMPLETED, loaded.evaluate(220, STAGE, HOUSES, FULL, GATE));
    }

    @Test
    void loadingUnknownPhaseFallsBackToIdle()
    {
        AltarCeremony loaded = new AltarCeremony();
        loaded.load(new net.minecraft.nbt.CompoundTag());
        assertEquals(Phase.IDLE, loaded.getPhase());
    }
}
