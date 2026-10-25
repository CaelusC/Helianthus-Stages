package com.helianthus.stages.ceremony;

import com.helianthus.stages.config.ConfigManager;
import com.helianthus.stages.config.StageConfig;
import com.helianthus.stages.integration.AStagesBridge;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Pays out a completed ceremony: grants the AStages stage, announces it and runs the stage's unlock commands.
 * Stages are server-scoped (see {@link AStagesBridge}), so the grant and the announcement reach every online player.
 */
public final class CeremonyRewardHandler
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private final AStagesBridge astages;

    public CeremonyRewardHandler(AStagesBridge astages)
    {
        this.astages = astages;
    }

    public void reward(MinecraftServer server, String stageId)
    {
        reward(new ServerTarget(server), stageId);
    }

    public boolean reward(Target target, String stageId)
    {
        Optional<StageConfig> found = ConfigManager.getStages().stream().filter(s -> s.id().equals(stageId)).findFirst();
        if (found.isEmpty())
        {
            LOGGER.warn("No stage config for completed ceremony stage '{}'", stageId);
            return false;
        }
        StageConfig stage = found.get();

        astages.addServerStage(stage.astagesStage());

        StageConfig.StageMessages messages = stage.messages();
        if (messages != null)
        {
            if (!isBlank(messages.unlockTitle()) || !isBlank(messages.unlockSubtitle()))
                target.sendTitle(replace(messages.unlockTitle(), stage), replace(messages.unlockSubtitle(), stage));
            if (!isBlank(messages.broadcast()))
                target.broadcast(replace(messages.broadcast(), stage));
        }

        for (String command : stage.onUnlockCommands())
            target.runCommand(replace(command, stage));
        return true;
    }

    // {stage} -> stage id, {displayName} -> display name
    static String replace(String text, StageConfig stage)
    {
        if (text == null)
            return null;
        return text.replace("{stage}", stage.id())
                .replace("{displayName}", stage.displayName() == null ? "" : stage.displayName());
    }

    private static boolean isBlank(String text)
    {
        return text == null || text.isBlank();
    }

    public interface Target
    {
        /** Title and/or subtitle; either may be null. */
        void sendTitle(String title, String subtitle);

        void broadcast(String message);

        void runCommand(String command);
    }

    private record ServerTarget(MinecraftServer server) implements Target
    {
        @Override
        public void sendTitle(String title, String subtitle)
        {
            for (ServerPlayer player : server.getPlayerList().getPlayers())
            {
                // The client shows a subtitle only alongside the next title, so it goes first
                if (subtitle != null && !subtitle.isBlank())
                    player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title == null ? "" : title)));
            }
        }

        @Override
        public void broadcast(String message)
        {
            server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
        }

        @Override
        public void runCommand(String command)
        {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
        }
    }
}
