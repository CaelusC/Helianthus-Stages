package com.helianthus.stages.block.entity;

import com.helianthus.stages.config.GateConfig;
import com.helianthus.stages.state.ParticipantRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Ceremony state machine for one altar. Kept free of world access so it can be driven directly in tests.
 * IDLE -> COMMITTING (someone arrived) -> CEREMONY (every required house met quorum) -> back to IDLE on completion.
 * A commit timeout or a house dropping below quorum aborts into COOLDOWN, which returns to IDLE once it expires.
 * While the stage is sealed by an unfulfilled shared quest no session starts and COMMITTING never advances to CEREMONY.
 */
public final class AltarCeremony
{
    public static final int TICKS_PER_SECOND = 20;

    public enum Phase
    {
        IDLE, COMMITTING, CEREMONY, COOLDOWN
    }

    public enum Event
    {
        NONE, COMMIT_STARTED, CEREMONY_STARTED, COMPLETED, ABORTED, COOLDOWN_ENDED
    }

    private Phase phase = Phase.IDLE;
    private String stageId = "";
    private long deadline;
    private final Set<String> readyHouses = new TreeSet<>();
    private final List<ParticipantRecord> participants = new ArrayList<>();

    public Event evaluate(long gameTime, String stageId, Collection<String> requiredHouses,
                          Map<String, List<ParticipantRecord>> presentByHouse, GateConfig gate)
    {
        return evaluate(gameTime, stageId, requiredHouses, presentByHouse, gate, false);
    }

    /**
     * Advances the state machine by one evaluation.
     *
     * @param gameTime       current level game time in ticks
     * @param stageId        stage the altar would attempt, or null when no stage is available
     * @param requiredHouses houses (team short names) that must all meet quorum
     * @param presentByHouse eligible players near the altar, grouped by house team short name
     * @param sealed         true while the stage's required shared quest is unfulfilled
     */
    public Event evaluate(long gameTime, String stageId, Collection<String> requiredHouses,
                          Map<String, List<ParticipantRecord>> presentByHouse, GateConfig gate, boolean sealed)
    {
        switch (phase)
        {
            case IDLE ->
            {
                if (sealed || stageId == null || requiredHouses.isEmpty() || presentByHouse.values().stream().allMatch(List::isEmpty))
                    return Event.NONE;
                phase = Phase.COMMITTING;
                this.stageId = stageId;
                deadline = gameTime + (long) gate.commitTimeoutSeconds() * TICKS_PER_SECOND;
                updateQuorum(requiredHouses, presentByHouse, gate);
                return Event.COMMIT_STARTED;
            }
            case COMMITTING ->
            {
                if (!this.stageId.equals(stageId))
                    return abort(gameTime, gate);
                updateQuorum(requiredHouses, presentByHouse, gate);
                if (!sealed && readyHouses.containsAll(requiredHouses))
                {
                    phase = Phase.CEREMONY;
                    deadline = gameTime + (long) gate.ceremonySeconds() * TICKS_PER_SECOND;
                    return Event.CEREMONY_STARTED;
                }
                return gameTime >= deadline ? abort(gameTime, gate) : Event.NONE;
            }
            case CEREMONY ->
            {
                updateQuorum(requiredHouses, presentByHouse, gate);
                if (!this.stageId.equals(stageId) || !readyHouses.containsAll(requiredHouses))
                    return abort(gameTime, gate);
                if (gameTime >= deadline)
                {
                    List<ParticipantRecord> finished = List.copyOf(participants);
                    reset();
                    participants.addAll(finished);
                    return Event.COMPLETED;
                }
                return Event.NONE;
            }
            case COOLDOWN ->
            {
                if (gameTime < deadline)
                    return Event.NONE;
                reset();
                return Event.COOLDOWN_ENDED;
            }
        }
        return Event.NONE;
    }

    private void updateQuorum(Collection<String> requiredHouses, Map<String, List<ParticipantRecord>> presentByHouse, GateConfig gate)
    {
        readyHouses.clear();
        participants.clear();
        for (String house : requiredHouses)
        {
            List<ParticipantRecord> present = presentByHouse.getOrDefault(house, List.of());
            if (present.size() >= gate.quorumPerHouse())
                readyHouses.add(house);
            participants.addAll(present);
        }
    }

    private Event abort(long gameTime, GateConfig gate)
    {
        reset();
        phase = Phase.COOLDOWN;
        deadline = gameTime + (long) gate.abortCooldownSeconds() * TICKS_PER_SECOND;
        return Event.ABORTED;
    }

    private void reset()
    {
        phase = Phase.IDLE;
        stageId = "";
        deadline = 0L;
        readyHouses.clear();
        participants.clear();
    }

    public Phase getPhase()
    {
        return phase;
    }

    public String getStageId()
    {
        return stageId;
    }

    public long getDeadline()
    {
        return deadline;
    }

    public Set<String> getReadyHouses()
    {
        return Set.copyOf(readyHouses);
    }

    // Participants as of the last evaluation; captured before COMPLETED resets the session
    public List<ParticipantRecord> getParticipants()
    {
        return List.copyOf(participants);
    }

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("phase", phase.name());
        tag.putString("stageId", stageId);
        tag.putLong("deadline", deadline);
        ListTag ready = new ListTag();
        readyHouses.forEach(house -> ready.add(StringTag.valueOf(house)));
        tag.put("readyHouses", ready);
        return tag;
    }

    public void load(CompoundTag tag)
    {
        reset();
        try
        {
            phase = Phase.valueOf(tag.getString("phase"));
        }
        catch (IllegalArgumentException e)
        {
            return;
        }
        stageId = tag.getString("stageId");
        deadline = tag.getLong("deadline");
        ListTag ready = tag.getList("readyHouses", Tag.TAG_STRING);
        for (int i = 0; i < ready.size(); i++)
            readyHouses.add(ready.getString(i));
    }
}
