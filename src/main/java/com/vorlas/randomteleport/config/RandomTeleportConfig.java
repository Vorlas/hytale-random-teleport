package com.vorlas.randomteleport.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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

    // Command getters
    public String getCommandName() {
        return data.command.name;
    }

    public String[] getCommandAliases() {
        return data.command.aliases.toArray(new String[0]);
    }

    public String getCommandDescription() {
        return data.command.description;
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

    public String getTeleportOtherPermission() {
        return data.permissions.teleportOther;
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

    public String getMessageTeleportedOther() {
        return data.messages.teleportedOther;
    }

    public String getMessageNoPermissionOther() {
        return data.messages.noPermissionOther;
    }

    public String getMessageTargetNotFound() {
        return data.messages.targetNotFound;
    }

    // Inner data classes
    public static class TierData {
        public String permission = "";
        public int cooldownSeconds = 600;
        public int warmupSeconds = 5;
        public int minDistance = -1; // -1 = use defaults
        public int maxDistance = -1;
        public int minHeight = -1;
        public int maxHeight = -1;
    }

    private static class CommandData {
        String name = "rtp";
        List<String> aliases = new ArrayList<>(Arrays.asList("randomtp", "randomteleport"));
        String description = "Randomly teleports you away from spawn";
    }

    private static class PermissionsData {
        String use = "randomteleport.use";
        String bypassCooldown = "randomteleport.bypass.cooldown";
        String bypassWarmup = "randomteleport.bypass.warmup";
        String teleportOther = "randomteleport.admin.teleportother";
    }

    private static class DefaultsData {
        int cooldownSeconds = 3600;
        int warmupSeconds = 5;
        int minDistance = 5000;
        int maxDistance = 9000;
        double movementThreshold = 0.5;
        int minHeight = 120;
        int maxHeight = 260;
        int maxAttempts = 10;
    }

    private static class MessagesData {
        String cooldown = "&5[RTP] &bYou must wait &e{time} &bbefore using /rtp again!";
        String noPermission = "&5[RTP] &cYou don't have permission to use /rtp!";
        String noWorld = "&5[RTP] &cYou must be in a world to use this command!";
        String warmupStart = "&5[RTP] &bTeleporting in &e{seconds} &bseconds... Don't move!";
        String movedCancelled = "&5[RTP] &cTeleportation cancelled! You moved too much.";
        String noSafeSpot = "&5[RTP] &cCould not find a safe landing spot. Try again!";
        String error = "&5[RTP] &cError scanning for safe location.";
        String teleported = "&5[RTP] &bTeleported to &fX: {x}, Y: {y}, Z: {z} &f({distance} blocks from spawn)";
        String searching = "&5[RTP] &fSearching for safe location... (attempt {attempt}/{max})";
        String teleportedOther = "&5[RTP] &bTeleported &e{player} &bto &fX: {x}, Y: {y}, Z: {z} &f({distance} blocks from spawn)";
        String noPermissionOther = "&5[RTP] &cYou don't have permission to teleport other players!";
        String targetNotFound = "&5[RTP] &cTarget player is not in a world!";
    }

    private static class ConfigData {
        String pluginName = "RandomTeleport";
        String version = "1.2.0";
        boolean debugMode = false;
        CommandData command = new CommandData();
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
            diamond.minDistance = 8000;
            diamond.maxDistance = 15000;
            diamond.minHeight = 120;
            diamond.maxHeight = 260;
            tiers.put("diamond", diamond);

            TierData gold = new TierData();
            gold.permission = "randomteleport.tier.gold";
            gold.cooldownSeconds = 900;
            gold.warmupSeconds = 2;
            gold.minDistance = 6000;
            gold.maxDistance = 12000;
            gold.minHeight = 120;
            gold.maxHeight = 260;
            tiers.put("gold", gold);

            TierData silver = new TierData();
            silver.permission = "randomteleport.tier.silver";
            silver.cooldownSeconds = 1800;
            silver.warmupSeconds = 3;
            silver.minDistance = 4000;
            silver.maxDistance = 9000;
            silver.minHeight = 120;
            silver.maxHeight = 260;
            tiers.put("silver", silver);

            TierData bronze = new TierData();
            bronze.permission = "randomteleport.tier.bronze";
            bronze.cooldownSeconds = 2700;
            bronze.warmupSeconds = 5;
            bronze.minDistance = 3000;
            bronze.maxDistance = 6000;
            bronze.minHeight = 120;
            bronze.maxHeight = 260;
            tiers.put("bronze", bronze);

            return tiers;
        }
    }
}
