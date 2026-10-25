package com.helianthus.stages.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Loads houses.json, gate.json and stages.json. A file that fails to parse or validate is reported
// with its file name and field path, and the previously active config for that file stays in place.
public final class ConfigManager
{
    public static final String DIRECTORY = "helianthus_stages";
    public static final String HOUSES_FILE = "houses.json";
    public static final String GATE_FILE = "gate.json";
    public static final String STAGES_FILE = "stages.json";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String[] STAGE_NAMES = {"Novice", "Apprentice", "Master", "Grandmaster"};

    private static volatile List<HouseConfig> houses = defaultHouses();
    private static volatile GateConfig gate = GateConfig.defaultConfig();
    private static volatile List<StageConfig> stages = defaultStages();
    private static volatile List<String> lastErrors = List.of();

    private ConfigManager()
    {
    }

    public static List<HouseConfig> getHouses()
    {
        return houses;
    }

    public static GateConfig getGate()
    {
        return gate;
    }

    public static List<StageConfig> getStages()
    {
        return stages;
    }

    // Errors from the most recent load, each formatted as "<file>: <path>: <message>".
    public static List<String> getLastErrors()
    {
        return lastErrors;
    }

    public static synchronized void load(Path directory)
    {
        List<String> errors = new ArrayList<>();
        writeDefaultsIfMissing(directory, errors);

        List<HouseConfig> loadedHouses = read(directory, HOUSES_FILE, new TypeToken<List<HouseConfig>>() {}, ConfigManager::validateHouses, errors);
        if (loadedHouses != null)
            houses = List.copyOf(loadedHouses);

        GateConfig loadedGate = read(directory, GATE_FILE, TypeToken.get(GateConfig.class), ConfigManager::validateGate, errors);
        if (loadedGate != null)
            gate = loadedGate;

        List<StageConfig> loadedStages = read(directory, STAGES_FILE, new TypeToken<List<StageConfig>>() {}, ConfigManager::validateStages, errors);
        if (loadedStages != null)
            stages = List.copyOf(loadedStages);

        lastErrors = List.copyOf(errors);
    }

    static List<HouseConfig> defaultHouses()
    {
        return List.of(
                new HouseConfig("Red House", "red", "red"),
                new HouseConfig("Blue House", "blue", "blue"),
                new HouseConfig("Green House", "green", "green"));
    }

    // Each house gets its own Novice -> Apprentice -> Master -> Grandmaster chain.
    static List<StageConfig> defaultStages()
    {
        List<StageConfig> result = new ArrayList<>();
        for (HouseConfig house : defaultHouses())
        {
            String previous = null;
            for (int order = 0; order < STAGE_NAMES.length; order++)
            {
                String name = STAGE_NAMES[order];
                String id = house.team() + "_" + name.toLowerCase();
                result.add(new StageConfig(id, order, name, id, previous, Map.of(),
                        new StageConfig.StageMessages(name, house.name(), house.name() + " has reached " + name),
                        List.of()));
                previous = id;
            }
        }
        return List.copyOf(result);
    }

    private static void writeDefaultsIfMissing(Path directory, List<String> errors)
    {
        writeIfMissing(directory, HOUSES_FILE, defaultHouses(), errors);
        writeIfMissing(directory, GATE_FILE, GateConfig.defaultConfig(), errors);
        writeIfMissing(directory, STAGES_FILE, defaultStages(), errors);
    }

    private static void writeIfMissing(Path directory, String file, Object defaults, List<String> errors)
    {
        Path path = directory.resolve(file);
        if (Files.exists(path))
            return;
        try
        {
            Files.createDirectories(directory);
            Files.writeString(path, GSON.toJson(defaults), StandardCharsets.UTF_8);
            LOGGER.info("Created default config {}", path);
        }
        catch (IOException e)
        {
            report(errors, file, "$", "could not write default file: " + e.getMessage());
        }
    }

    private static <T> T read(Path directory, String file, TypeToken<T> type, Validator<T> validator, List<String> errors)
    {
        JsonReader json = null;
        try (Reader reader = Files.newBufferedReader(directory.resolve(file), StandardCharsets.UTF_8))
        {
            json = new JsonReader(reader);
            T value = GSON.getAdapter(type).read(json);
            if (value == null)
                throw new InvalidConfigException("$", "file is empty");
            validator.validate(value);
            return value;
        }
        catch (InvalidConfigException e)
        {
            report(errors, file, e.path, e.getMessage());
        }
        catch (IOException | RuntimeException e)
        {
            // The reader's path points at the element that failed to parse
            report(errors, file, json == null ? "$" : json.getPath(), e.getMessage());
        }
        return null;
    }

    private static void report(List<String> errors, String file, String path, String message)
    {
        String error = file + ": " + path + ": " + message;
        errors.add(error);
        LOGGER.error("Invalid config {}, keeping previous values", error);
    }

    private static void validateHouses(List<HouseConfig> list) throws InvalidConfigException
    {
        require(!list.isEmpty(), "$", "at least one house is required");
        Set<String> teams = new HashSet<>();
        for (int i = 0; i < list.size(); i++)
        {
            HouseConfig house = list.get(i);
            String path = "$[" + i + "]";
            require(house != null, path, "house must be an object");
            requireText(house.name(), path + ".name");
            requireText(house.team(), path + ".team");
            requireText(house.color(), path + ".color");
            require(teams.add(house.team()), path + ".team", "duplicate team '" + house.team() + "'");
        }
    }

    private static void validateGate(GateConfig config) throws InvalidConfigException
    {
        require(config.radius() > 0, "$.radius", "must be greater than 0");
        require(config.quorumPerHouse() >= 1, "$.quorumPerHouse", "must be at least 1");
        require(config.commitTimeoutSeconds() > 0, "$.commitTimeoutSeconds", "must be greater than 0");
        require(config.ceremonySeconds() > 0, "$.ceremonySeconds", "must be greater than 0");
        require(config.abortCooldownSeconds() >= 0, "$.abortCooldownSeconds", "must not be negative");
        require(config.tickIntervalTicks() >= 1, "$.tickIntervalTicks", "must be at least 1");
        require(config.allowedGameModes() != null && !config.allowedGameModes().isEmpty(), "$.allowedGameModes", "at least one game mode is required");
        for (int i = 0; i < config.allowedGameModes().size(); i++)
            requireText(config.allowedGameModes().get(i), "$.allowedGameModes[" + i + "]");
    }

    private static void validateStages(List<StageConfig> list) throws InvalidConfigException
    {
        require(!list.isEmpty(), "$", "at least one stage is required");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < list.size(); i++)
        {
            StageConfig stage = list.get(i);
            String path = "$[" + i + "]";
            require(stage != null, path, "stage must be an object");
            requireText(stage.id(), path + ".id");
            require(ids.add(stage.id()), path + ".id", "duplicate stage id '" + stage.id() + "'");
            require(stage.order() >= 0, path + ".order", "must not be negative");
            requireText(stage.displayName(), path + ".displayName");
            requireText(stage.astagesStage(), path + ".astagesStage");
            require(stage.messages() != null, path + ".messages", "is required");
        }
    }

    private static void requireText(String value, String path) throws InvalidConfigException
    {
        require(value != null && !value.isBlank(), path, "is required");
    }

    private static void require(boolean condition, String path, String message) throws InvalidConfigException
    {
        if (!condition)
            throw new InvalidConfigException(path, message);
    }

    @FunctionalInterface
    private interface Validator<T>
    {
        void validate(T value) throws InvalidConfigException;
    }

    private static final class InvalidConfigException extends Exception
    {
        private final String path;

        private InvalidConfigException(String path, String message)
        {
            super(message);
            this.path = path;
        }
    }
}
