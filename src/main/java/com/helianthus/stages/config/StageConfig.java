package com.helianthus.stages.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record StageConfig(
        String id,
        int order,
        String displayName,
        String astagesStage,
        String requiresStage,
        String requiresQuest,
        Map<String, Object> ceremony,
        StageMessages messages,
        List<String> onUnlockCommands)
{
    public StageConfig
    {
        ceremony = ceremony == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(ceremony));
        onUnlockCommands = onUnlockCommands == null ? List.of() : List.copyOf(onUnlockCommands);
    }

    // requiresQuest is optional; callers that predate it get null
    public StageConfig(String id, int order, String displayName, String astagesStage, String requiresStage,
                       Map<String, Object> ceremony, StageMessages messages, List<String> onUnlockCommands)
    {
        this(id, order, displayName, astagesStage, requiresStage, null, ceremony, messages, onUnlockCommands);
    }

    public record StageMessages(String unlockTitle, String unlockSubtitle, String broadcast)
    {
    }
}
