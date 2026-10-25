package com.helianthus.stages.command;

import com.helianthus.stages.HelianthusStages;
import com.helianthus.stages.ceremony.CeremonyRewardHandler;
import com.helianthus.stages.config.ConfigManager;
import com.helianthus.stages.config.HouseConfig;
import com.helianthus.stages.config.StageConfig;
import com.helianthus.stages.integration.AStagesBridge;
import com.helianthus.stages.state.HelianthusSavedData;
import com.helianthus.stages.state.HouseReadinessState;
import com.helianthus.stages.state.StageData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * /helianthus reload | status | force_unlock &lt;stageId&gt;. reload and force_unlock need operator permission;
 * status is read-only and open to everyone.
 */
@Mod.EventBusSubscriber(modid = HelianthusStages.MODID)
public final class HelianthusCommand
{
    public static final String ROOT = "helianthus";
    public static final int OPERATOR_LEVEL = 2;

    private static CeremonyRewardHandler rewards;

    private HelianthusCommand()
    {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(build(source -> source.hasPermission(OPERATOR_LEVEL), new ServerActions()));
    }

    // Generic over the source so the tree can be dispatched in tests without a running server
    static <S> LiteralArgumentBuilder<S> build(Predicate<S> isOperator, Actions<S> actions)
    {
        return LiteralArgumentBuilder.<S>literal(ROOT)
                .then(LiteralArgumentBuilder.<S>literal("reload")
                        .requires(isOperator)
                        .executes(ctx -> actions.reload(ctx.getSource())))
                .then(LiteralArgumentBuilder.<S>literal("status")
                        .executes(ctx -> actions.status(ctx.getSource())))
                .then(LiteralArgumentBuilder.<S>literal("force_unlock")
                        .requires(isOperator)
                        .then(RequiredArgumentBuilder.<S, String>argument("stageId", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    ConfigManager.getStages().forEach(stage -> builder.suggest(stage.id()));
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> actions.forceUnlock(ctx.getSource(), StringArgumentType.getString(ctx, "stageId")))));
    }

    static List<String> reloadLines(List<String> errors)
    {
        if (errors.isEmpty())
            return List.of("Reloaded " + ConfigManager.HOUSES_FILE + ", " + ConfigManager.GATE_FILE + " and " + ConfigManager.STAGES_FILE);
        List<String> lines = new ArrayList<>();
        lines.add("Reload finished with " + errors.size() + " error(s), previous values kept for those files:");
        errors.forEach(error -> lines.add(" - " + error));
        return lines;
    }

    static List<String> statusLines(HelianthusSavedData data, List<StageConfig> stages, List<HouseConfig> houses)
    {
        List<String> lines = new ArrayList<>();
        stages.stream().sorted(Comparator.comparingInt(StageConfig::order).thenComparing(StageConfig::id)).forEach(stage -> {
            StageData state = data.getStage(stage.id());
            lines.add(stage.id() + " (" + stage.displayName() + "): " + (state.unlocked() ? "unlocked" : "locked"));
            if (state.unlocked())
                return;
            for (HouseConfig house : houses)
            {
                HouseReadinessState readiness = state.readiness().get(house.team());
                boolean ready = readiness != null && readiness.ready();
                lines.add("  " + house.name() + ": " + (ready ? "ready" : "not ready"));
            }
        });
        return lines;
    }

    // Returns null on success, otherwise the reason the stage was not unlocked
    static String forceUnlock(HelianthusSavedData data, List<StageConfig> stages, String stageId, long nowEpochMs, Consumer<String> reward)
    {
        if (stages.stream().noneMatch(stage -> stage.id().equals(stageId)))
            return "Unknown stage '" + stageId + "'";
        if (data.getStage(stageId).unlocked())
            return "Stage '" + stageId + "' is already unlocked";
        data.unlockStage(stageId, nowEpochMs, List.of());
        reward.accept(stageId);
        return null;
    }

    private static CeremonyRewardHandler rewards()
    {
        if (rewards == null)
            rewards = new CeremonyRewardHandler(AStagesBridge.create());
        return rewards;
    }

    interface Actions<S>
    {
        int reload(S source);

        int status(S source);

        int forceUnlock(S source, String stageId);
    }

    private static final class ServerActions implements Actions<CommandSourceStack>
    {
        @Override
        public int reload(CommandSourceStack source)
        {
            ConfigManager.load(FMLPaths.CONFIGDIR.get().resolve(ConfigManager.DIRECTORY));
            List<String> errors = ConfigManager.getLastErrors();
            for (String line : reloadLines(errors))
            {
                if (errors.isEmpty())
                    source.sendSuccess(() -> Component.literal(line), true);
                else
                    source.sendFailure(Component.literal(line));
            }
            return errors.isEmpty() ? 1 : 0;
        }

        @Override
        public int status(CommandSourceStack source)
        {
            HelianthusSavedData data = HelianthusSavedData.get(source.getServer().overworld());
            for (String line : statusLines(data, ConfigManager.getStages(), ConfigManager.getHouses()))
                source.sendSuccess(() -> Component.literal(line), false);
            return 1;
        }

        @Override
        public int forceUnlock(CommandSourceStack source, String stageId)
        {
            HelianthusSavedData data = HelianthusSavedData.get(source.getServer().overworld());
            String error = HelianthusCommand.forceUnlock(data, ConfigManager.getStages(), stageId, System.currentTimeMillis(),
                    id -> rewards().reward(source.getServer(), id));
            if (error != null)
            {
                source.sendFailure(Component.literal(error));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Force-unlocked stage '" + stageId + "'"), true);
            return 1;
        }
    }
}
