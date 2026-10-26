package com.helianthus.stages.block.entity;

import com.helianthus.stages.ceremony.CeremonyRewardHandler;
import com.helianthus.stages.config.ConfigManager;
import com.helianthus.stages.config.GateConfig;
import com.helianthus.stages.config.HouseConfig;
import com.helianthus.stages.config.StageConfig;
import com.helianthus.stages.integration.AStagesBridge;
import com.helianthus.stages.integration.TeamsBridge;
import com.helianthus.stages.network.HelianthusNetwork;
import com.helianthus.stages.network.SyncCeremonyPacket;
import com.helianthus.stages.state.HelianthusSavedData;
import com.helianthus.stages.state.HouseReadinessState;
import com.helianthus.stages.state.ParticipantRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private static CeremonyRewardHandler rewards;
    private static AStagesBridge stages;

    private final AltarCeremony ceremony = new AltarCeremony();
    // Whether the last evaluation synced a sealed altar, so the seal is cleared on clients once lifted
    private boolean syncedSealed;

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
        String sealedQuest = sealedQuest(data, stageId);

        AltarCeremony.Event event = ceremony.evaluate(level.getGameTime(), stageId, requiredHouses,
                presentByHouse(level, gate), gate, !sealedQuest.isEmpty());
        boolean sealedIdle = !sealedQuest.isEmpty() && ceremony.getPhase() == AltarCeremony.Phase.IDLE;
        if (sealedIdle)
        {
            syncedSealed = true;
            HelianthusNetwork.syncCeremony(level.getChunkAt(getBlockPos()),
                    new SyncCeremonyPacket(getBlockPos(), stageId, 0, Map.of(), sealedQuest));
            return;
        }
        if (event == AltarCeremony.Event.NONE && ceremony.getPhase() == AltarCeremony.Phase.IDLE && !syncedSealed)
            return;
        syncedSealed = false;
        playEffects(level, event, gate);

        if (event == AltarCeremony.Event.COMPLETED)
        {
            data.unlockStage(sessionStage, System.currentTimeMillis(), ceremony.getParticipants());
            rewards().reward(level.getServer(), sessionStage);
            HelianthusNetwork.syncStagesToAll(level);
        }
        else if (sessionStage != null && !sessionStage.isEmpty())
            syncReadiness(data, sessionStage, requiredHouses);
        HelianthusNetwork.syncCeremony(level.getChunkAt(getBlockPos()), ceremonyPacket(level.getGameTime(), event, sessionStage, requiredHouses, gate));
        setChanged();
    }

    // Committing shows the active particle; the countdown adds the ceremony particle and tick sound; completion plays its sound
    private void playEffects(ServerLevel level, AltarCeremony.Event event, GateConfig gate)
    {
        Vec3 center = Vec3.atCenterOf(getBlockPos());
        if (event == AltarCeremony.Event.COMPLETED)
        {
            playSound(level, center, sound(gate.completionSound(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE));
            return;
        }
        switch (ceremony.getPhase())
        {
            case COMMITTING -> spawnParticles(level, center, particle(gate.activeParticle(), ParticleTypes.PORTAL));
            case CEREMONY ->
            {
                spawnParticles(level, center, particle(gate.ceremonyParticle(), ParticleTypes.ENCHANT));
                playSound(level, center, sound(gate.ceremonyTickSound(), SoundEvents.ENCHANTMENT_TABLE_USE));
            }
            default -> {}
        }
    }

    private static void spawnParticles(ServerLevel level, Vec3 center, SimpleParticleType particle)
    {
        level.sendParticles(particle, center.x, center.y + 0.5, center.z, 20, 0.75, 0.5, 0.75, 0.05);
    }

    private static void playSound(ServerLevel level, Vec3 center, SoundEvent sound)
    {
        level.playSound(null, center.x, center.y, center.z, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    // Only parameterless particles can be spawned from a bare id; anything else falls back
    private static SimpleParticleType particle(String id, SimpleParticleType fallback)
    {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null && ForgeRegistries.PARTICLE_TYPES.getValue(location) instanceof SimpleParticleType type
                ? type
                : fallback;
    }

    private static SoundEvent sound(String id, SoundEvent fallback)
    {
        ResourceLocation location = ResourceLocation.tryParse(id);
        SoundEvent sound = location == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(location);
        return sound == null ? fallback : sound;
    }

    // Completion reports the finished stage at 100%; otherwise progress runs only while the ceremony phase counts down
    private SyncCeremonyPacket ceremonyPacket(long gameTime, AltarCeremony.Event event, String sessionStage,
                                              List<String> requiredHouses, GateConfig gate)
    {
        if (event == AltarCeremony.Event.COMPLETED)
        {
            Map<String, Boolean> allReady = new LinkedHashMap<>();
            requiredHouses.forEach(house -> allReady.put(house, true));
            return new SyncCeremonyPacket(getBlockPos(), sessionStage, 100, allReady);
        }
        int progress = 0;
        if (ceremony.getPhase() == AltarCeremony.Phase.CEREMONY)
        {
            long total = (long) gate.ceremonySeconds() * AltarCeremony.TICKS_PER_SECOND;
            long remaining = Math.max(0, ceremony.getDeadline() - gameTime);
            progress = (int) Math.min(100, (total - remaining) * 100 / total);
        }
        Map<String, Boolean> readiness = new LinkedHashMap<>();
        requiredHouses.forEach(house -> readiness.put(house, ceremony.getReadyHouses().contains(house)));
        return new SyncCeremonyPacket(getBlockPos(), ceremony.getStageId(), progress, readiness);
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

    // The stage's required shared quest when it is unlocked neither in saved data nor as an AStages stage, else empty
    private static String sealedQuest(HelianthusSavedData data, String stageId)
    {
        String quest = ConfigManager.getStages().stream()
                .filter(stage -> stage.id().equals(stageId))
                .map(StageConfig::requiresQuest)
                .findFirst()
                .orElse(null);
        if (quest == null || quest.isEmpty() || data.getStage(quest).unlocked() || stages().serverHasStage(quest))
            return "";
        return quest;
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

    private static CeremonyRewardHandler rewards()
    {
        if (rewards == null)
            rewards = new CeremonyRewardHandler(stages());
        return rewards;
    }

    private static AStagesBridge stages()
    {
        if (stages == null)
            stages = AStagesBridge.create();
        return stages;
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
