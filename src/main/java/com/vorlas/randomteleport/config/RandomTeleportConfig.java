package com.vorlas.randomteleport.config;

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

    public int getMinHeight() {
        return data.minHeight;
    }

    public int getMaxHeight() {
        return data.maxHeight;
    }

    public int getInvulnerabilitySeconds() {
        return data.invulnerabilitySeconds;
    }

    public String getMessageCooldown() {
        return data.messageCooldown;
    }

    public String getMessageNoWorld() {
        return data.messageNoWorld;
    }

    public String getMessageWarmupStart() {
        return data.messageWarmupStart;
    }

    public String getMessageMovedCancelled() {
        return data.messageMovedCancelled;
    }

    public String getMessageNoSafeSpot() {
        return data.messageNoSafeSpot;
    }

    public String getMessageError() {
        return data.messageError;
    }

    public String getMessageTeleported() {
        return data.messageTeleported;
    }

    public String getMessageWarning1() {
        return data.messageWarning1;
    }

    public String getMessageWarning2() {
        return data.messageWarning2;
    }

    private static class ConfigData {
        int cooldownSeconds = 3600;
        int warmupSeconds = 5;
        int minDistance = 5000;
        int maxDistance = 9000;
        double movementThreshold = 0.5;
        int minHeight = 120;
        int maxHeight = 200;
        int invulnerabilitySeconds = 5;

        String messageCooldown = "You must wait {time} before using /rtp again!";
        String messageNoWorld = "You must be in a world to use this command!";
        String messageWarmupStart = "Teleporting in {seconds} seconds... Don't move!";
        String messageMovedCancelled = "Teleportation cancelled! You moved too much.";
        String messageNoSafeSpot = "Could not find a safe landing spot. Try again!";
        String messageError = "Error scanning for safe location.";
        String messageTeleported = "Teleported to X: {x}, Y: {y}, Z: {z} ({distance} blocks from spawn)";
        String messageWarning1 = "WARNING: RTP is in early development!";
        String messageWarning2 = "May teleport to dangerous locations. Move to cancel.";
    }
}
