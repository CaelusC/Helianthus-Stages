package com.helianthus.stages.integration;

import com.alessandro.astages.api.holder.AHolder;
import com.alessandro.astages.api.util.AStagesUtils;
import net.minecraftforge.fml.ModList;

/**
 * The only place that talks to AStages. Stages are server-scoped; when AStages is absent every call is a no-op.
 */
public final class AStagesBridge
{
    public static final String MOD_ID = "astages";

    private final Backend backend;

    public AStagesBridge(Backend backend)
    {
        this.backend = backend;
    }

    public static AStagesBridge create()
    {
        return new AStagesBridge(ModList.get().isLoaded(MOD_ID) ? new AStagesBackend() : null);
    }

    public boolean isAvailable()
    {
        return backend != null;
    }

    public void addServerStage(String stage)
    {
        if (backend != null)
        {
            backend.add(stage);
        }
    }

    public void removeServerStage(String stage)
    {
        if (backend != null)
        {
            backend.remove(stage);
        }
    }

    public boolean serverHasStage(String stage)
    {
        return backend != null && backend.has(stage);
    }

    public interface Backend
    {
        void add(String stage);

        void remove(String stage);

        boolean has(String stage);
    }

    // Loaded only when AStages is present, so its classes are never resolved otherwise
    private static final class AStagesBackend implements Backend
    {
        @Override
        public void add(String stage)
        {
            AStagesUtils.addStage(AHolder.server(), stage, false, false, false);
        }

        @Override
        public void remove(String stage)
        {
            AStagesUtils.removeStage(AHolder.server(), stage, false, false, false);
        }

        @Override
        public boolean has(String stage)
        {
            return AStagesUtils.hasStage(AHolder.server(), stage);
        }
    }
}
