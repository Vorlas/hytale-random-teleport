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
import com.hypixel.hytale.protocol.packets.player.ClientTeleport;
import com.hypixel.hytale.protocol.ModelTransform;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Direction;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import com.mars.randomteleport.utils.WarmupManager;
import com.mars.randomteleport.config.RandomTeleportConfig;

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

    private static final Random random = new Random();

    // Track last teleport time per player
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    // Warmup Manager
    private final WarmupManager warmupManager;

    // Config
    private final RandomTeleportConfig config;

    /**
     * Constructor - registers the command with name "rtp"
     */
    public RandomTeleportCommand(RandomTeleportConfig config) {
        super("rtp", "Randomly teleports you away from spawn");
        this.addAliases("randomtp", "randomteleport");
        this.setPermissionGroup(GameMode.Adventure);
        this.warmupManager = new WarmupManager();
        this.config = config;
    }

    // Ensure we clean up the scheduler when the plugin/command is destroyed (if
    // applicable lifecycle exists)
    public void cleanup() {
        this.warmupManager.shutdown();
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
                    long cooldownMs = config.getCooldownSeconds() * 1000L;

                    if (cooldowns.containsKey(playerUuid)) {
                        long lastUsed = cooldowns.get(playerUuid);
                        long timePassed = currentTime - lastUsed;

                        if (timePassed < cooldownMs) {
                            long remainingMs = cooldownMs - timePassed;
                            String remainingTime = formatTime(remainingMs);
                            player.sendMessage(
                                    Message.raw("You must wait " + remainingTime + " before using /rtp again!"));
                            return;
                        }
                    }

                    // Start Warmup
                    warmupManager.startWarmup(playerRef, ref, store, world, config.getWarmupSeconds(), () -> {
                        // This Runnable will be executed on the scheduler thread after warmup
                        // We need to be careful about thread safety, but the main teleport logic
                        // mostly calculates numbers or schedules back to the main thread.

                        // Execute Teleport Logic
                        executeRandomTeleport(player, ref, store, world, playerUuid);
                    });

                }, world);
            } else {
                player.sendMessage(Message.raw("You must be in a world to use this command!"));
                return CompletableFuture.completedFuture(null);
            }
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    private void executeRandomTeleport(Player player, Ref<EntityStore> ref, Store<EntityStore> store, World world,
            UUID playerUuid) {
        // Generate random distance
        int min = config.getMinDistance();
        int max = config.getMaxDistance();
        double distance = min + random.nextDouble() * (max - min);
        double angle = random.nextDouble() * 2 * Math.PI;

        double randomX = Math.cos(angle) * distance;
        double randomZ = Math.sin(angle) * distance;

        // Run the chunk loading and teleporting on the main thread/world thread to be
        // safe
        world.execute(() -> {
            double teleportY = 85.0; // Fallback
            boolean safeLocationFound = false;

            // Calculate chunk indices
            int chunkX = (int) Math.floor(randomX / 16.0);
            int chunkZ = (int) Math.floor(randomZ / 16.0);
            long chunkIndex = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);

            // Force load chunk to get height (Allowing unloaded chunks to be safely
            // targeted)
            try {
                com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk chunk = world.getChunk(chunkIndex);
                if (chunk != null) {
                    // Get safe height (Top block)
                    int localX = ((int) randomX) & 15;
                    int localZ = ((int) randomZ) & 15;
                    // Ensure positive modulo for negative coordinates
                    if (localX < 0)
                        localX += 16;
                    if (localZ < 0)
                        localZ += 16;

                    // Finds the highest block (terrain surface)
                    int terrainHeight = chunk.getHeight(localX, localZ);
                    teleportY = terrainHeight + 1.2; // Add invalid buffer
                    safeLocationFound = true;
                }
            } catch (Exception e) {
                player.sendMessage(Message.raw("Error loading target chunk. Using fallback height."));
                e.printStackTrace();
                teleportY = 120.0;
            }

            if (!safeLocationFound) {
                teleportY = 120.0;
            }

            // Get current position component for teleportation
            var transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) {
                // Teleport the player using Vector3d
                Vector3d targetPosition = new Vector3d(randomX, teleportY, randomZ);
                transform.teleportPosition(targetPosition);

                // Fix for vertical-only teleportation: Force client sync
                // Construct the packet using the correct ModelTransform API
                Position pos = new Position(targetPosition.x, targetPosition.y, targetPosition.z);
                Direction body = new Direction(0f, 0f, 0f);
                Direction look = new Direction(0f, 0f, 0f);
                ModelTransform modelTransform = new ModelTransform(pos, body, look);

                player.getPlayerConnection().write(new ClientTeleport((byte) 0, modelTransform, true));
            }

            // Set cooldown
            cooldowns.put(playerUuid, System.currentTimeMillis());

            // Notify player
            player.sendMessage(Message.raw(String.format(
                    "Teleported to X: %.0f, Y: %.0f, Z: %.0f (%.0f blocks from spawn)",
                    randomX, teleportY, randomZ, distance)));
        });
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
