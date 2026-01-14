package com.mars.randomteleport.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Random Teleport Command - Teleports player to a random location 2000-5000
 * blocks from spawn.
 * 
 * Usage: /rtp
 * 
 * @author Mars
 */
public class RandomTeleportCommand extends AbstractAsyncCommand {

    // Teleport distance range from spawn
    private static final int MIN_DISTANCE = 5000;
    private static final int MAX_DISTANCE = 9000;

    // Cooldown: 1 hour in milliseconds
    private static final long COOLDOWN_MS = 60 * 60 * 1000;

    private static final Random random = new Random();

    // Track last teleport time per player
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    /**
     * Constructor - registers the command with name "rtp"
     */
    public RandomTeleportCommand() {
        super("rtp", "Randomly teleports you 5000-9000 blocks from spawn");
        this.addAliases("randomtp", "randomteleport");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @NonNullDecl
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        if (sender instanceof Player player) {
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                return CompletableFuture.runAsync(() -> {
                    PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                    if (playerRef == null)
                        return;

                    UUID playerUuid = playerRef.getUuid();

                    // Check cooldown
                    long currentTime = System.currentTimeMillis();
                    if (cooldowns.containsKey(playerUuid)) {
                        long lastUsed = cooldowns.get(playerUuid);
                        long timePassed = currentTime - lastUsed;

                        if (timePassed < COOLDOWN_MS) {
                            long remainingMs = COOLDOWN_MS - timePassed;
                            String remainingTime = formatTime(remainingMs);
                            player.sendMessage(
                                    Message.raw("You must wait " + remainingTime + " before using /rtp again!"));
                            return;
                        }
                    }

                    // Generate random distance between 2000-5000 blocks
                    double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);

                    // Generate random angle (0 to 360 degrees in radians)
                    double angle = random.nextDouble() * 2 * Math.PI;

                    // Calculate X and Z coordinates from spawn (0, 0)
                    double randomX = Math.cos(angle) * distance;
                    double randomZ = Math.sin(angle) * distance;

                    // Get ground level at target location (using default for now)
                    double teleportY = 100.0;

                    // Get current position component for teleportation
                    var transform = store.getComponent(ref, TransformComponent.getComponentType());
                    if (transform != null) {
                        // Teleport the player using Vector3d
                        Vector3d targetPosition = new Vector3d(randomX, teleportY, randomZ);
                        transform.teleportPosition(targetPosition);
                    }

                    // Set cooldown
                    cooldowns.put(playerUuid, currentTime);

                    // Notify player
                    player.sendMessage(Message.raw(String.format(
                            "Teleported to X: %.0f, Y: %.0f, Z: %.0f (%.0f blocks from spawn)",
                            randomX, teleportY, randomZ, distance)));
                }, world);
            } else {
                player.sendMessage(Message.raw("You must be in a world to use this command!"));
                return CompletableFuture.completedFuture(null);
            }
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Format milliseconds into a readable time string
     */
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        minutes = minutes % 60;
        seconds = seconds % 60;

        if (hours > 0) {
            return String.format("%d hour%s %d minute%s",
                    hours, hours == 1 ? "" : "s",
                    minutes, minutes == 1 ? "" : "s");
        } else if (minutes > 0) {
            return String.format("%d minute%s %d second%s",
                    minutes, minutes == 1 ? "" : "s",
                    seconds, seconds == 1 ? "" : "s");
        } else {
            return String.format("%d second%s", seconds, seconds == 1 ? "" : "s");
        }
    }
}
