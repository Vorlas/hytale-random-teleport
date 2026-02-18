package com.vorlas.randomteleport.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import com.vorlas.randomteleport.utils.WarmupManager;
import com.vorlas.randomteleport.utils.MessageUtil;
import com.vorlas.randomteleport.config.RandomTeleportConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class RandomTeleportCommand extends AbstractAsyncCommand {

    private static final Random random = new Random();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final WarmupManager warmupManager;
    private final RandomTeleportConfig config;
    private final OptionalArg<PlayerRef> targetPlayerArg;

    public RandomTeleportCommand(RandomTeleportConfig config) {
        super(config.getCommandName(), config.getCommandDescription());
        this.addAliases(config.getCommandAliases());
        this.setPermissionGroup(GameMode.Adventure);
        this.requirePermission(config.getUsePermission());
        this.targetPlayerArg = this.withOptionalArg("player", "Target player to teleport", ArgTypes.PLAYER_REF);
        this.warmupManager = new WarmupManager(config);
        this.config = config;
    }

    public void cleanup() {
        this.warmupManager.shutdown();
    }

    /**
     * Get the cooldown in seconds for a player based on their permission tier.
     * Checks from highest tier (diamond) to lowest (bronze), returns default if no
     * tier.
     */
    private int getCooldownForPlayer(Player player) {
        // Check bypass first
        if (player.hasPermission(config.getBypassCooldownPermission(), false)) {
            return 0;
        }

        // Check tiers from highest to lowest (diamond -> gold -> silver -> bronze)
        for (Map.Entry<String, RandomTeleportConfig.TierData> entry : config.getTiers().entrySet()) {
            if (player.hasPermission(entry.getValue().permission, false)) {
                return entry.getValue().cooldownSeconds;
            }
        }

        // No tier found, use default
        return config.getDefaultCooldownSeconds();
    }

    /**
     * Get the warmup in seconds for a player based on their permission tier.
     */
    private int getWarmupForPlayer(Player player) {
        // Check bypass first
        if (player.hasPermission(config.getBypassWarmupPermission(), false)) {
            return 0;
        }

        // Check tiers from highest to lowest
        for (Map.Entry<String, RandomTeleportConfig.TierData> entry : config.getTiers().entrySet()) {
            if (player.hasPermission(entry.getValue().permission, false)) {
                return entry.getValue().warmupSeconds;
            }
        }

        // No tier found, use default
        return config.getDefaultWarmupSeconds();
    }

    /**
     * Get the min/max distance for a player based on their permission tier.
     * Returns int[] {minDistance, maxDistance}
     */
    private int[] getDistanceForPlayer(Player player) {
        // Check tiers from highest to lowest
        for (Map.Entry<String, RandomTeleportConfig.TierData> entry : config.getTiers().entrySet()) {
            if (player.hasPermission(entry.getValue().permission, false)) {
                RandomTeleportConfig.TierData tier = entry.getValue();
                int minDist = tier.minDistance > 0 ? tier.minDistance : config.getMinDistance();
                int maxDist = tier.maxDistance > 0 ? tier.maxDistance : config.getMaxDistance();
                return new int[] { minDist, maxDist };
            }
        }
        // No tier found, use defaults
        return new int[] { config.getMinDistance(), config.getMaxDistance() };
    }

    /**
     * Get the min/max height for a player based on their permission tier.
     * Returns int[] {minHeight, maxHeight}
     */
    private int[] getHeightForPlayer(Player player) {
        for (Map.Entry<String, RandomTeleportConfig.TierData> entry : config.getTiers().entrySet()) {
            if (player.hasPermission(entry.getValue().permission, false)) {
                RandomTeleportConfig.TierData tier = entry.getValue();
                int minH = tier.minHeight >= 0 ? tier.minHeight : config.getMinHeight();
                int maxH = tier.maxHeight > 0 ? tier.maxHeight : config.getMaxHeight();
                return new int[] { minH, maxH };
            }
        }
        return new int[] { config.getMinHeight(), config.getMaxHeight() };
    }

    @NonNullDecl
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        boolean targetingOther = targetPlayerArg.provided(commandContext);

        if (targetingOther) {
            // /rtp <player> — admin or console targeting another player
            if (sender instanceof Player senderPlayer
                    && !senderPlayer.hasPermission(config.getTeleportOtherPermission(), false)) {
                senderPlayer.sendMessage(MessageUtil.parseColored(config.getMessageNoPermissionOther()));
                return CompletableFuture.completedFuture(null);
            }

            PlayerRef targetPlayerRef = targetPlayerArg.get(commandContext);
            Ref<EntityStore> targetRef = targetPlayerRef.getReference();
            if (targetRef == null || !targetRef.isValid()) {
                sender.sendMessage(MessageUtil.parseColored(config.getMessageTargetNotFound()));
                return CompletableFuture.completedFuture(null);
            }

            Store<EntityStore> store = targetRef.getStore();
            World world = store.getExternalData().getWorld();
            return CompletableFuture.runAsync(() -> {
                UUID targetUuid = targetPlayerRef.getUuid();
                // Admin teleport skips cooldown and warmup
                executeRandomTeleport(sender, targetPlayerRef, targetRef, store, world, targetUuid, true);
            }, world);

        } else if (sender instanceof Player player) {
            // /rtp — self teleport (existing behavior)
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                return CompletableFuture.runAsync(() -> {
                    PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                    if (playerRef == null)
                        return;

                    UUID playerUuid = playerRef.getUuid();
                    long currentTime = System.currentTimeMillis();

                    // Get tier-based cooldown for this player
                    int cooldownSeconds = getCooldownForPlayer(player);
                    long cooldownMs = cooldownSeconds * 1000L;

                    // Check cooldown (skip if bypass or cooldown is 0)
                    if (cooldownMs > 0 && cooldowns.containsKey(playerUuid)) {
                        long lastUsed = cooldowns.get(playerUuid);
                        long timePassed = currentTime - lastUsed;

                        if (timePassed < cooldownMs) {
                            long remainingMs = cooldownMs - timePassed;
                            String remainingTime = formatTime(remainingMs);
                            String msg = config.getMessageCooldown().replace("{time}", remainingTime);
                            player.sendMessage(MessageUtil.parseColored(msg));
                            return;
                        }
                    }

                    // Get tier-based warmup for this player
                    int warmupSeconds = getWarmupForPlayer(player);

                    if (warmupSeconds <= 0) {
                        // Bypass warmup - teleport immediately
                        executeRandomTeleport(sender, playerRef, ref, store, world, playerUuid, false);
                    } else {
                        warmupManager.startWarmup(playerRef, ref, store, world, warmupSeconds, () -> {
                            executeRandomTeleport(sender, playerRef, ref, store, world, playerUuid, false);
                        });
                    }

                }, world);
            } else {
                player.sendMessage(MessageUtil.parseColored(config.getMessageNoWorld()));
                return CompletableFuture.completedFuture(null);
            }
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    private void executeRandomTeleport(CommandSender sender, PlayerRef targetPlayerRef,
            Ref<EntityStore> ref, Store<EntityStore> store, World world, UUID playerUuid, boolean isAdmin) {
        // Start the retry loop with attempt 1
        tryRandomLocation(sender, targetPlayerRef, ref, store, world, playerUuid, 1, isAdmin);
    }

    private void tryRandomLocation(CommandSender sender, PlayerRef targetPlayerRef,
            Ref<EntityStore> ref, Store<EntityStore> store, World world,
            UUID playerUuid, int attempt, boolean isAdmin) {
        int maxAttempts = config.getMaxAttempts();

        if (attempt > maxAttempts) {
            sender.sendMessage(MessageUtil.parseColored(config.getMessageNoSafeSpot()));
            System.out.println("[RTP] Failed after " + maxAttempts + " attempts!");
            return;
        }

        // Show searching message
        String searchMsg = config.getMessageSearching()
                .replace("{attempt}", String.valueOf(attempt))
                .replace("{max}", String.valueOf(maxAttempts));
        sender.sendMessage(MessageUtil.parseColored(searchMsg));

        // Get distance range — use defaults for admin teleports, tier-based for self
        int[] distanceRange;
        if (!isAdmin && sender instanceof Player player) {
            distanceRange = getDistanceForPlayer(player);
        } else {
            distanceRange = new int[] { config.getMinDistance(), config.getMaxDistance() };
        }
        int min = distanceRange[0];
        int max = distanceRange[1];
        double distance = min + random.nextDouble() * (max - min);
        double angle = random.nextDouble() * 2 * Math.PI;

        double randomX = Math.cos(angle) * distance;
        double randomZ = Math.sin(angle) * distance;

        final int worldX = (int) Math.floor(randomX);
        final int worldZ = (int) Math.floor(randomZ);
        final double fDistance = distance;
        final double fRandomX = randomX;
        final double fRandomZ = randomZ;
        final int centerChunkX = worldX >> 4;
        final int centerChunkZ = worldZ >> 4;

        System.out.println("[RTP] Attempt " + attempt + "/" + maxAttempts +
                ": X=" + worldX + " Z=" + worldZ);

        // Preload 3x3 chunk grid around target
        java.util.List<CompletableFuture<com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk>> futures = new java.util.ArrayList<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long idx = ((long) (centerChunkX + dx) << 32) | ((centerChunkZ + dz) & 0xFFFFFFFFL);
                futures.add(world.getChunkAsync(idx));
            }
        }

        // Wait for all chunks to load then check for safe spot
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS).execute(() -> {
                world.execute(() -> {
                    // Get height range — use defaults for admin, tier-based for self
                    int[] heightRange;
                    if (!isAdmin && sender instanceof Player player) {
                        heightRange = getHeightForPlayer(player);
                    } else {
                        heightRange = new int[] { config.getMinHeight(), config.getMaxHeight() };
                    }
                    int safeY = findSafeSurfaceY(world, worldX, worldZ, heightRange[0], heightRange[1]);

                    if (safeY < 0) {
                        // Try another location
                        System.out.println("[RTP] Attempt " + attempt + " failed - no safe spot, retrying...");
                        tryRandomLocation(sender, targetPlayerRef, ref, store, world, playerUuid, attempt + 1, isAdmin);
                        return;
                    }

                    double teleportY = safeY + 1.0;
                    System.out.println("[RTP] Found safe ground at Y=" + safeY + " on attempt " + attempt);

                    var transform = store.getComponent(ref, TransformComponent.getComponentType());
                    if (transform != null) {
                        Vector3d target = new Vector3d(fRandomX, teleportY, fRandomZ);

                        Teleport teleport = Teleport.createForPlayer(target, transform.getRotation());
                        store.addComponent(ref, Teleport.getComponentType(), teleport);

                        System.out.println("[RTP] Teleported: X=" + fRandomX + " Y=" + teleportY + " Z=" + fRandomZ);
                        cooldowns.put(playerUuid, System.currentTimeMillis());

                        if (isAdmin) {
                            String msg = config.getMessageTeleportedOther()
                                    .replace("{player}", targetPlayerRef.getUsername())
                                    .replace("{x}", String.format("%.0f", fRandomX))
                                    .replace("{y}", String.format("%.0f", teleportY))
                                    .replace("{z}", String.format("%.0f", fRandomZ))
                                    .replace("{distance}", String.format("%.0f", fDistance));
                            sender.sendMessage(MessageUtil.parseColored(msg));
                        } else {
                            String msg = config.getMessageTeleported()
                                    .replace("{x}", String.format("%.0f", fRandomX))
                                    .replace("{y}", String.format("%.0f", teleportY))
                                    .replace("{z}", String.format("%.0f", fRandomZ))
                                    .replace("{distance}", String.format("%.0f", fDistance));
                            sender.sendMessage(MessageUtil.parseColored(msg));
                        }
                    } else {
                        sender.sendMessage(MessageUtil.parseColored(config.getMessageError()));
                    }
                });
            });
        });
    }

    /**
     * Find safe surface Y coordinate using world coordinates (ScreamingRTP
     * pattern).
     * Scans from max height down to find solid ground with headspace and edge
     * safety.
     * 
     * @return Y coordinate of ground block, or -1 if no safe spot found
     */
    private int findSafeSurfaceY(World world, int x, int z, int minHeight, int maxHeight) {
        for (int y = maxHeight; y >= minHeight; y--) {
            try {
                int ground = world.getBlock(x, y, z);
                // Check: solid ground + 2 blocks of AIR headspace (no blocks AND no fluids)
                if (ground != 0
                        && world.getBlock(x, y + 1, z) == 0
                        && world.getBlock(x, y + 2, z) == 0
                        && world.getFluidId(x, y + 1, z) == 0 // Headspace not underwater
                        && world.getFluidId(x, y + 2, z) == 0 // Headspace not underwater
                        && hasSolidAround(world, x, y, z)) {
                    return y;
                }
            } catch (Exception e) {
                // Skip if block query fails
            }
        }
        return -1;
    }

    /**
     * Check if the 4 direct neighbors (N, S, E, W) at the same Y level are solid.
     * Automatically handles cross-chunk boundaries.
     */
    private boolean hasSolidAround(World world, int x, int y, int z) {
        return world.getBlock(x + 1, y, z) != 0
                && world.getBlock(x - 1, y, z) != 0
                && world.getBlock(x, y, z + 1) != 0
                && world.getBlock(x, y, z - 1) != 0;
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        minutes = minutes % 60;
        seconds = seconds % 60;

        if (hours > 0) {
            return String.format("%d hour%s %d minute%s", hours, hours == 1 ? "" : "s", minutes,
                    minutes == 1 ? "" : "s");
        } else if (minutes > 0) {
            return String.format("%d minute%s %d second%s", minutes, minutes == 1 ? "" : "s", seconds,
                    seconds == 1 ? "" : "s");
        } else {
            return String.format("%d second%s", seconds, seconds == 1 ? "" : "s");
        }
    }
}
