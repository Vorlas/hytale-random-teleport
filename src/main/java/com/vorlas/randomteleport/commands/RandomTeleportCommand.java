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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.protocol.packets.player.ClientTeleport;
import com.hypixel.hytale.protocol.ModelTransform;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.Direction;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import com.vorlas.randomteleport.utils.WarmupManager;
import com.vorlas.randomteleport.config.RandomTeleportConfig;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;

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

    public RandomTeleportCommand(RandomTeleportConfig config) {
        super("rtp", "Randomly teleports you away from spawn");
        this.addAliases("randomtp", "randomteleport");
        this.setPermissionGroup(GameMode.Adventure);
        this.warmupManager = new WarmupManager(config);
        this.config = config;
    }

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
                    long currentTime = System.currentTimeMillis();
                    long cooldownMs = config.getCooldownSeconds() * 1000L;

                    if (cooldowns.containsKey(playerUuid)) {
                        long lastUsed = cooldowns.get(playerUuid);
                        long timePassed = currentTime - lastUsed;

                        if (timePassed < cooldownMs) {
                            long remainingMs = cooldownMs - timePassed;
                            String remainingTime = formatTime(remainingMs);
                            String msg = config.getMessageCooldown().replace("{time}", remainingTime);
                            player.sendMessage(Message.raw(msg));
                            return;
                        }
                    }

                    // Show warning message
                    player.sendMessage(Message.raw(config.getMessageWarning1()));
                    player.sendMessage(Message.raw(config.getMessageWarning2()));

                    warmupManager.startWarmup(playerRef, ref, store, world, config.getWarmupSeconds(), () -> {
                        executeRandomTeleport(player, ref, store, world, playerUuid);
                    });

                }, world);
            } else {
                player.sendMessage(Message.raw(config.getMessageNoWorld()));
                return CompletableFuture.completedFuture(null);
            }
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    private void executeRandomTeleport(Player player, Ref<EntityStore> ref, Store<EntityStore> store, World world,
            UUID playerUuid) {
        int min = config.getMinDistance();
        int max = config.getMaxDistance();
        double distance = min + random.nextDouble() * (max - min);
        double angle = random.nextDouble() * 2 * Math.PI;

        double randomX = Math.cos(angle) * distance;
        double randomZ = Math.sin(angle) * distance;

        final int worldX = (int) Math.floor(randomX);
        final int worldZ = (int) Math.floor(randomZ);
        final double fDistance = distance;
        final int centerChunkX = worldX >> 4;
        final int centerChunkZ = worldZ >> 4;

        System.out.println(
                "[RTP] Target: X=" + worldX + " Z=" + worldZ + " (chunk " + centerChunkX + "," + centerChunkZ + ")");

        // Step 1: Preload 3x3 chunk grid around target
        System.out.println("[RTP] Preloading 3x3 chunk grid...");
        java.util.List<CompletableFuture<com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk>> futures = new java.util.ArrayList<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long idx = ((long) (centerChunkX + dx) << 32) | ((centerChunkZ + dz) & 0xFFFFFFFFL);
                futures.add(world.getChunkAsync(idx));
            }
        }

        // Wait for all chunks to load then delay 1 second for terrain generation
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            System.out.println("[RTP] 9 chunks preloaded. Waiting 1s for terrain...");

            CompletableFuture.delayedExecutor(1000, TimeUnit.MILLISECONDS).execute(() -> {
                world.execute(() -> {
                    long chunkIndex = ((long) centerChunkX << 32) | (centerChunkZ & 0xFFFFFFFFL);
                    var chunk = world.getChunk(chunkIndex);

                    if (chunk == null) {
                        player.sendMessage(Message.raw(config.getMessageError()));
                        System.out.println("[RTP] Chunk null after preload!");
                        return;
                    }

                    chunk.setKeepLoaded(true);
                    System.out.println("[RTP] INIT=" + chunk.is(ChunkFlag.INIT) + " NEWLY_GENERATED="
                            + chunk.is(ChunkFlag.NEWLY_GENERATED));

                    int localX = worldX & 15;
                    int localZ = worldZ & 15;
                    if (localX < 0)
                        localX += 16;
                    if (localZ < 0)
                        localZ += 16;

                    // Get heightmap as starting point
                    short heightmapY = chunk.getHeight(localX, localZ);
                    System.out.println("[RTP] Heightmap Y=" + heightmapY);

                    // Debug block data around heightmap
                    System.out.println("[RTP-DEBUG] Blocks around heightmap:");
                    for (int dy = 5; dy >= -5; dy--) {
                        int y = heightmapY + dy;
                        if (y < 0 || y > 255)
                            continue;
                        int bid = chunk.getBlock(localX, y, localZ);
                        System.out.println("[RTP-DEBUG]   Y=" + y + " blockId=" + bid);
                    }

                    double teleportY = -1;
                    boolean found = false;

                    // Scan from heightmap+5 down to heightmap-30
                    int scanStart = Math.min(heightmapY + 5, 255);
                    int scanEnd = Math.max(heightmapY - 30, 0);

                    for (int y = scanStart; y >= scanEnd; y--) {
                        if (chunk.getFluidId(localX, y, localZ) != 0)
                            continue;

                        int blockId = chunk.getBlock(localX, y, localZ);
                        if (blockId != 0) {
                            // Check 3 blocks of head space
                            int b1 = chunk.getBlock(localX, y + 1, localZ);
                            int b2 = chunk.getBlock(localX, y + 2, localZ);
                            int b3 = chunk.getBlock(localX, y + 3, localZ);

                            if (b1 == 0 && b2 == 0 && b3 == 0) {
                                teleportY = y + 1.0;
                                found = true;
                                System.out.println("[RTP] Found ground Y=" + y + " (blockId=" + blockId + ")");
                                break;
                            }
                        }
                    }

                    if (!found) {
                        player.sendMessage(Message.raw(config.getMessageNoSafeSpot()));
                        System.out.println("[RTP] No safe spot found!");
                        return;
                    }

                    var transform = store.getComponent(ref, TransformComponent.getComponentType());
                    if (transform != null) {
                        Vector3d target = new Vector3d(randomX, teleportY, randomZ);

                        transform.teleportPosition(target);

                        Position pos = new Position(target.x, target.y, target.z);
                        Direction body = new Direction(0f, 0f, 0f);
                        Direction look = new Direction(0f, 0f, 0f);
                        ModelTransform mt = new ModelTransform(pos, body, look);
                        player.getPlayerConnection().write(new ClientTeleport((byte) 0, mt, true));

                        System.out.println("[RTP] Teleported: X=" + randomX + " Y=" + teleportY + " Z=" + randomZ);
                        cooldowns.put(playerUuid, System.currentTimeMillis());

                        String msg = config.getMessageTeleported()
                                .replace("{x}", String.format("%.0f", randomX))
                                .replace("{y}", String.format("%.0f", teleportY))
                                .replace("{z}", String.format("%.0f", randomZ))
                                .replace("{distance}", String.format("%.0f", fDistance));
                        player.sendMessage(Message.raw(msg));
                    } else {
                        player.sendMessage(Message.raw(config.getMessageError()));
                    }
                });
            });
        });
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
