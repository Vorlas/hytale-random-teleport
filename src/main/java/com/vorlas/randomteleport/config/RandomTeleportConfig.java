package com.vorlas.randomteleport.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

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

    // Permission getters
    public String getUsePermission() {
        return data.permissions.use;
    }

    public String getBypassCooldownPermission() {
        return data.permissions.bypassCooldown;
    }

    public String getBypassWarmupPermission() {
        return data.permissions.bypassWarmup;
    }

    // Tier getters
    public Map<String, TierData> getTiers() {
        return data.tiers;
    }

    // Default getters
    public int getDefaultCooldownSeconds() {
        return data.defaults.cooldownSeconds;
    }

    public int getDefaultWarmupSeconds() {
        return data.defaults.warmupSeconds;
    }

    public int getMinDistance() {
        return data.defaults.minDistance;
    }

    public int getMaxDistance() {
        return data.defaults.maxDistance;
    }

    public double getMovementThreshold() {
        return data.defaults.movementThreshold;
    }

    public int getMinHeight() {
        return data.defaults.minHeight;
    }

    public int getMaxHeight() {
        return data.defaults.maxHeight;
    }

    public int getMaxAttempts() {
        return data.defaults.maxAttempts;
    }

    // Message getters
    public String getMessageCooldown() {
        return data.messages.cooldown;
    }

    public String getMessageNoPermission() {
        return data.messages.noPermission;
    }

    public String getMessageNoWorld() {
        return data.messages.noWorld;
    }

    public String getMessageWarmupStart() {
        return data.messages.warmupStart;
    }

    public String getMessageMovedCancelled() {
        return data.messages.movedCancelled;
    }

    public String getMessageNoSafeSpot() {
        return data.messages.noSafeSpot;
    }

    public String getMessageError() {
        return data.messages.error;
    }

    public String getMessageTeleported() {
        return data.messages.teleported;
    }

    public String getMessageSearching() {
        return data.messages.searching;
    }

    public String getMessageWarning1() {
        return data.messages.warning1;
    }

    public String getMessageWarning2() {
        return data.messages.warning2;
    }

    // Inner data classes
    public static class TierData {
        public String permission = "";
        public int cooldownSeconds = 600;
        public int warmupSeconds = 5;
    }

    private static class PermissionsData {
        String use = "randomteleport.use";
        String bypassCooldown = "randomteleport.bypass.cooldown";
        String bypassWarmup = "randomteleport.bypass.warmup";
    }

    private static class DefaultsData {
        int cooldownSeconds = 3600;
        int warmupSeconds = 5;
        int minDistance = 5000;
        int maxDistance = 9000;
        double movementThreshold = 0.5;
        int minHeight = 120;
        int maxHeight = 200;
        int maxAttempts = 10;
    }

    private static class MessagesData {
        String cooldown = "You must wait {time} before using /rtp again!";
        String noPermission = "You don't have permission to use /rtp!";
        String noWorld = "You must be in a world to use this command!";
        String warmupStart = "Teleporting in {seconds} seconds... Don't move!";
        String movedCancelled = "Teleportation cancelled! You moved too much.";
        String noSafeSpot = "Could not find a safe landing spot. Try again!";
        String error = "Error scanning for safe location.";
        String teleported = "Teleported to X: {x}, Y: {y}, Z: {z} ({distance} blocks from spawn)";
        String warning1 = "WARNING: RTP is in early development!";
        String warning2 = "May teleport to dangerous locations. Move to cancel.";
        String searching = "Searching for safe location... (attempt {attempt}/{max})";
    }

    private static class ConfigData {
        String pluginName = "RandomTeleport";
        String version = "1.0.3";
        boolean debugMode = false;
        PermissionsData permissions = new PermissionsData();
        Map<String, TierData> tiers = createDefaultTiers();
        DefaultsData defaults = new DefaultsData();
        MessagesData messages = new MessagesData();

        private static Map<String, TierData> createDefaultTiers() {
            Map<String, TierData> tiers = new LinkedHashMap<>();

            TierData diamond = new TierData();
            diamond.permission = "randomteleport.tier.diamond";
            diamond.cooldownSeconds = 300;
            diamond.warmupSeconds = 1;
            tiers.put("diamond", diamond);

            TierData gold = new TierData();
            gold.permission = "randomteleport.tier.gold";
            gold.cooldownSeconds = 900;
            gold.warmupSeconds = 2;
            tiers.put("gold", gold);

            TierData silver = new TierData();
            silver.permission = "randomteleport.tier.silver";
            silver.cooldownSeconds = 1800;
            silver.warmupSeconds = 3;
            tiers.put("silver", silver);

            TierData bronze = new TierData();
            bronze.permission = "randomteleport.tier.bronze";
            bronze.cooldownSeconds = 2700;
            bronze.warmupSeconds = 5;
            tiers.put("bronze", bronze);

            return tiers;
        }
    }
}
