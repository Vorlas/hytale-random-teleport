package com.mars.randomteleport.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class RandomTeleportConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configFile;
    private ConfigData data;

    public RandomTeleportConfig(Path dataDirectory) {
        this.configFile = dataDirectory.resolve("config.json");
        load();
    }

    private void load() {
        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile)) {
                data = GSON.fromJson(reader, ConfigData.class);
                if (data == null) {
                    data = new ConfigData();
                }
            } catch (Exception e) {
                // Return default on error, backup might be good but keep it simple for now
                data = new ConfigData();
            }
        } else {
            data = new ConfigData();
            save();
        }
    }

    public void save() {
        try {
            if (configFile.getParent() != null) {
                Files.createDirectories(configFile.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getCooldownSeconds() {
        return data.cooldownSeconds;
    }

    public int getWarmupSeconds() {
        return data.warmupSeconds;
    }

    public int getMinDistance() {
        return data.minDistance;
    }

    public int getMaxDistance() {
        return data.maxDistance;
    }

    public double getMovementThreshold() {
        return data.movementThreshold;
    }

    private static class ConfigData {
        int cooldownSeconds = 3600; // 1 hour
        int warmupSeconds = 5;
        int minDistance = 5000;
        int maxDistance = 9000;
        double movementThreshold = 0.5;
    }
}
