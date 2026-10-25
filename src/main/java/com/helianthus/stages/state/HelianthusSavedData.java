package com.helianthus.stages.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All stage state that must survive a server restart. Stored once on the Overworld.
 */
public class HelianthusSavedData extends SavedData
{
    public static final String NAME = "helianthus_stages";

    private final Map<String, StageData> stages = new HashMap<>();

    public static HelianthusSavedData get(ServerLevel level)
    {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(HelianthusSavedData::load, HelianthusSavedData::new, NAME);
    }

    public StageData getStage(String stageId)
    {
        return stages.getOrDefault(stageId, StageData.locked());
    }

    public Map<String, StageData> getStages()
    {
        return Collections.unmodifiableMap(stages);
    }

    public void unlockStage(String stageId, long atEpochMs, List<ParticipantRecord> participants)
    {
        StageData current = getStage(stageId);
        stages.put(stageId, new StageData(true, atEpochMs, participants, current.readiness()));
        setDirty();
    }

    public void setReadiness(String stageId, String house, HouseReadinessState state)
    {
        StageData current = getStage(stageId);
        Map<String, HouseReadinessState> readiness = new HashMap<>(current.readiness());
        readiness.put(house, state);
        stages.put(stageId, new StageData(current.unlocked(), current.unlockedAtEpochMs(),
                current.participants(), readiness));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        CompoundTag stagesTag = new CompoundTag();
        stages.forEach((id, stage) -> {
            CompoundTag stageTag = new CompoundTag();
            stageTag.putBoolean("unlocked", stage.unlocked());
            stageTag.putLong("unlockedAtEpochMs", stage.unlockedAtEpochMs());

            ListTag participantsTag = new ListTag();
            for (ParticipantRecord participant : stage.participants())
            {
                CompoundTag p = new CompoundTag();
                p.putUUID("uuid", participant.uuid());
                p.putString("name", participant.name());
                p.putString("house", participant.house());
                participantsTag.add(p);
            }
            stageTag.put("participants", participantsTag);

            CompoundTag readinessTag = new CompoundTag();
            stage.readiness().forEach((house, state) -> {
                CompoundTag r = new CompoundTag();
                r.putBoolean("ready", state.ready());
                r.putLong("atEpochMs", state.atEpochMs());
                r.putString("via", state.via());
                readinessTag.put(house, r);
            });
            stageTag.put("readiness", readinessTag);

            stagesTag.put(id, stageTag);
        });
        tag.put("stages", stagesTag);
        return tag;
    }

    public static HelianthusSavedData load(CompoundTag tag)
    {
        HelianthusSavedData data = new HelianthusSavedData();
        CompoundTag stagesTag = tag.getCompound("stages");
        for (String id : stagesTag.getAllKeys())
        {
            CompoundTag stageTag = stagesTag.getCompound(id);

            List<ParticipantRecord> participants = new ArrayList<>();
            ListTag participantsTag = stageTag.getList("participants", Tag.TAG_COMPOUND);
            for (int i = 0; i < participantsTag.size(); i++)
            {
                CompoundTag p = participantsTag.getCompound(i);
                participants.add(new ParticipantRecord(p.getUUID("uuid"), p.getString("name"), p.getString("house")));
            }

            Map<String, HouseReadinessState> readiness = new HashMap<>();
            CompoundTag readinessTag = stageTag.getCompound("readiness");
            for (String house : readinessTag.getAllKeys())
            {
                CompoundTag r = readinessTag.getCompound(house);
                readiness.put(house, new HouseReadinessState(r.getBoolean("ready"), r.getLong("atEpochMs"), r.getString("via")));
            }

            data.stages.put(id, new StageData(stageTag.getBoolean("unlocked"), stageTag.getLong("unlockedAtEpochMs"),
                    participants, readiness));
        }
        return data;
    }
}
