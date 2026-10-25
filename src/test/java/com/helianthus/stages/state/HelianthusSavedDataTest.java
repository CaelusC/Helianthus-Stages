package com.helianthus.stages.state;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelianthusSavedDataTest
{
    @Test
    void roundTripsStagesParticipantsAndReadiness()
    {
        HelianthusSavedData data = new HelianthusSavedData();
        ParticipantRecord alice = new ParticipantRecord(UUID.randomUUID(), "Alice", "sun");
        ParticipantRecord bob = new ParticipantRecord(UUID.randomUUID(), "Bob", "moon");
        data.unlockStage("sun_novice", 1234L, List.of(alice, bob));
        data.setReadiness("sun_novice", "sun", new HouseReadinessState(true, 1000L, "quest"));
        data.setReadiness("moon_novice", "moon", new HouseReadinessState(false, 2000L, "admin"));

        HelianthusSavedData loaded = HelianthusSavedData.load(data.save(new CompoundTag()));

        assertEquals(data.getStages(), loaded.getStages());
        StageData sun = loaded.getStage("sun_novice");
        assertTrue(sun.unlocked());
        assertEquals(1234L, sun.unlockedAtEpochMs());
        assertEquals(List.of(alice, bob), sun.participants());
        assertEquals(Map.of("sun", new HouseReadinessState(true, 1000L, "quest")), sun.readiness());
        StageData moon = loaded.getStage("moon_novice");
        assertFalse(moon.unlocked());
        assertEquals(new HouseReadinessState(false, 2000L, "admin"), moon.readiness().get("moon"));
    }

    @Test
    void freshDataHasStagesLocked()
    {
        HelianthusSavedData data = new HelianthusSavedData();

        StageData stage = data.getStage("sun_novice");

        assertFalse(stage.unlocked());
        assertEquals(0L, stage.unlockedAtEpochMs());
        assertTrue(stage.participants().isEmpty());
        assertTrue(stage.readiness().isEmpty());
        assertTrue(data.getStages().isEmpty());
        assertFalse(data.isDirty());
        assertFalse(HelianthusSavedData.load(new CompoundTag()).getStage("sun_novice").unlocked());
    }

    @Test
    void marksDirtyOnEveryChange()
    {
        HelianthusSavedData data = new HelianthusSavedData();
        data.unlockStage("sun_novice", 1L, List.of());
        assertTrue(data.isDirty());

        data.setDirty(false);
        data.setReadiness("sun_novice", "sun", new HouseReadinessState(true, 2L, "command"));
        assertTrue(data.isDirty());
    }
}
