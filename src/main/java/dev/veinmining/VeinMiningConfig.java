package dev.veinmining;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Settings read from {@code <plugin data dir>/config.json}. The file is created with the defaults
 * on first start; missing fields keep their default values.
 */
public final class VeinMiningConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Most extra blocks one break may take with it (the block you mined is not counted). */
    public int maxBlocks = 64;
    /** Only vein mine while crouching. */
    public boolean requireCrouch = false;
    /**
     * Also follow ore that only touches along an edge or corner. Off by default: with faces only,
     * every mined block opens onto the tunnel, so its drops are always reachable.
     */
    public boolean includeDiagonals = false;
    /** Block ids that count as ore ({@code *} matches anything, case is ignored). */
    public List<String> blocks = List.of("Ore_*");

    public static VeinMiningConfig load(Path dataDirectory, HytaleLogger logger) {
        Path file = dataDirectory.resolve("config.json");
        VeinMiningConfig config = null;
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, VeinMiningConfig.class);
            } catch (IOException | RuntimeException e) {
                logger.atWarning().withCause(e).log("Could not read %s; using defaults", file);
            }
        }
        if (config == null) {
            config = new VeinMiningConfig();
        }
        if (config.blocks == null) {
            config.blocks = List.of();
        }
        config.maxBlocks = Math.max(0, config.maxBlocks);
        // Rewrite so new settings from plugin updates show up in the file.
        try {
            Files.createDirectories(dataDirectory);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            logger.atWarning().withCause(e).log("Could not write %s", file);
        }
        return config;
    }
}
