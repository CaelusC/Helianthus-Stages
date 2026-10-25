package com.helianthus.stages.integration;

import com.helianthus.stages.config.HouseConfig;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The only place that talks to FTB Teams. Maps a player's team short name to a house from houses.json.
 */
public final class TeamsBridge
{
    public static final String MOD_ID = "ftbteams";

    private final Backend backend;
    private final Supplier<List<HouseConfig>> houses;

    public TeamsBridge(Backend backend, Supplier<List<HouseConfig>> houses)
    {
        this.backend = backend;
        this.houses = houses;
    }

    public static TeamsBridge create(Supplier<List<HouseConfig>> houses)
    {
        return new TeamsBridge(ModList.get().isLoaded(MOD_ID) ? new FtbTeamsBackend() : null, houses);
    }

    public boolean isAvailable()
    {
        return backend != null;
    }

    public Optional<String> getTeamShortName(ServerPlayer player)
    {
        return backend == null ? Optional.empty() : backend.teamShortName(player);
    }

    public Optional<HouseConfig> getHouse(ServerPlayer player)
    {
        return getTeamShortName(player).flatMap(this::getHouseForTeam);
    }

    public Optional<HouseConfig> getHouseForTeam(String teamShortName)
    {
        return houses.get().stream().filter(house -> house.team().equals(teamShortName)).findFirst();
    }

    public interface Backend
    {
        Optional<String> teamShortName(ServerPlayer player);
    }

    // Loaded only when FTB Teams is present, so its classes are never resolved otherwise
    private static final class FtbTeamsBackend implements Backend
    {
        @Override
        public Optional<String> teamShortName(ServerPlayer player)
        {
            return FTBTeamsAPI.api().getManager().getTeamForPlayer(player).map(Team::getShortName);
        }
    }
}
