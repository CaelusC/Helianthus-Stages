package com.helianthus.stages.block.entity;

import com.helianthus.stages.config.ConfigManager;
import com.helianthus.stages.config.GateConfig;
import com.helianthus.stages.config.HouseConfig;
import com.helianthus.stages.config.StageConfig;
import com.helianthus.stages.integration.TeamsBridge;
import com.helianthus.stages.state.HelianthusSavedData;
import com.helianthus.stages.state.HouseReadinessState;
import com.helianthus.stages.state.ParticipantRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Altar that hosts the multi-house ceremony. Ticks on the server only; the owning block hands out
 * {@link #serverTick} as its ticker.
 */
public class HelianthusAltarBlockEntity extends BlockEntity
{
    public static final String READINESS_VIA = "altar";

    private static TeamsBridge teams;

    private final AltarCeremony ceremony = new AltarCeremony();

    public HelianthusAltarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state)
    {
        super(type, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HelianthusAltarBlockEntity altar)
    {
        GateConfig gate = ConfigManager.getGate();
        if (!(level instanceof ServerLevel serverLevel) || level.getGameTime() % gate.tickIntervalTicks() != 0)
            return;
        altar.evaluate(serverLevel, gate);
    }

    private void evaluate(ServerLevel level, GateConfig gate)
    {
        HelianthusSavedData data = HelianthusSavedData.get(level);
        List<String> requiredHouses = ConfigManager.getHouses().stream().map(HouseConfig::team).toList();
        String stageId = nextStage(data);
        // Readiness belongs to the stage of the running session, even if that session aborts this tick
        String sessionStage = ceremony.getPhase() == AltarCeremony.Phase.IDLE ? stageId : ceremony.getStageId();

        AltarCeremony.Event event = ceremony.evaluate(level.getGameTime(), stageId, requiredHouses,
                presentByHouse(level, gate), gate);
        if (event == AltarCeremony.Event.NONE && ceremony.getPhase() == AltarCeremony.Phase.IDLE)
            return;

        if (event == AltarCeremony.Event.COMPLETED)
            data.unlockStage(sessionStage, System.currentTimeMillis(), ceremony.getParticipants());
        else if (sessionStage != null && !sessionStage.isEmpty())
            syncReadiness(data, sessionStage, requiredHouses);
        setChanged();
    }

    // First locked stage, by order, whose prerequisite is unlocked
    private static String nextStage(HelianthusSavedData data)
    {
        return ConfigManager.getStages().stream()
                .sorted(Comparator.comparingInt(StageConfig::order))
                .filter(stage -> !data.getStage(stage.id()).unlocked())
                .filter(stage -> stage.requiresStage() == null || data.getStage(stage.requiresStage()).unlocked())
                .map(StageConfig::id)
                .findFirst()
                .orElse(null);
    }

    private Map<String, List<ParticipantRecord>> presentByHouse(ServerLevel level, GateConfig gate)
    {
        TeamsBridge bridge = teams();
        Vec3 center = Vec3.atCenterOf(getBlockPos());
        double radiusSqr = gate.radius() * gate.radius();
        List<ServerPlayer> candidates = gate.requireSameDimension()
                ? level.players()
                : level.getServer().getPlayerList().getPlayers();

        Map<String, List<ParticipantRecord>> result = new HashMap<>();
        for (ServerPlayer player : candidates)
        {
            if (player.position().distanceToSqr(center) > radiusSqr
                    || !gate.allowedGameModes().contains(player.gameMode.getGameModeForPlayer().getName()))
                continue;
            bridge.getHouse(player).ifPresent(house -> result.computeIfAbsent(house.team(), k -> new ArrayList<>())
                    .add(new ParticipantRecord(player.getUUID(), player.getGameProfile().getName(), house.team())));
        }
        return result;
    }

    private void syncReadiness(HelianthusSavedData data, String stageId, List<String> requiredHouses)
    {
        long now = System.currentTimeMillis();
        for (String house : requiredHouses)
        {
            boolean ready = ceremony.getReadyHouses().contains(house);
            HouseReadinessState current = data.getStage(stageId).readiness().get(house);
            if (current == null ? ready : current.ready() != ready)
                data.setReadiness(stageId, house, new HouseReadinessState(ready, now, READINESS_VIA));
        }
    }

    private static TeamsBridge teams()
    {
        if (teams == null)
            teams = TeamsBridge.create(ConfigManager::getHouses);
        return teams;
    }

    public AltarCeremony getCeremony()
    {
        return ceremony;
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.put("ceremony", ceremony.save());
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        ceremony.load(tag.getCompound("ceremony"));
    }
}
