package com.helianthus.stages.state;

import java.util.List;
import java.util.Map;

public record StageData(boolean unlocked, long unlockedAtEpochMs, List<ParticipantRecord> participants,
                        Map<String, HouseReadinessState> readiness)
{
    public StageData
    {
        participants = List.copyOf(participants);
        readiness = Map.copyOf(readiness);
    }

    public static StageData locked()
    {
        return new StageData(false, 0L, List.of(), Map.of());
    }
}
