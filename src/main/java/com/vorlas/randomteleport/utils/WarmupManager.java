package com.vorlas.randomteleport.utils;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.vorlas.randomteleport.config.RandomTeleportConfig;
import com.vorlas.randomteleport.utils.MessageUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class WarmupManager {

    private final ScheduledExecutorService scheduler;
    private final Map<UUID, WarmupData> activeWarmups = new ConcurrentHashMap<>();
    private final RandomTeleportConfig config;

    public WarmupManager(RandomTeleportConfig config) {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.config = config;
    }

    public void shutdown() {
        for (UUID playerId : activeWarmups.keySet()) {
            cancelWarmup(playerId);
        }
        scheduler.shutdown();
    }

    public void startWarmup(PlayerRef playerData, Ref<EntityStore> playerRef, Store<EntityStore> store,
            World world, int warmupSeconds, Runnable teleportAction) {

        UUID playerId = playerData.getUuid();
        cancelWarmup(playerId);

        if (warmupSeconds <= 0) {
            teleportAction.run();
            return;
        }

        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null)
            return;

        Vector3d startPos = transform.getPosition();
        String msg = config.getMessageWarmupStart().replace("{seconds}", String.valueOf(warmupSeconds));
        playerData.sendMessage(MessageUtil.parseColored(msg));

        WarmupData data = new WarmupData(playerData, playerRef, store, world,
                startPos.x, startPos.y, startPos.z, config.getMovementThreshold());

        ScheduledFuture<?> checkFuture = scheduler.scheduleAtFixedRate(() -> {
            checkMovement(playerId, data);
        }, 500, 500, TimeUnit.MILLISECONDS);

        ScheduledFuture<?> teleportFuture = scheduler.schedule(() -> {
            completeWarmup(playerId, teleportAction);
        }, warmupSeconds, TimeUnit.SECONDS);

        data.checkFuture = checkFuture;
        data.teleportFuture = teleportFuture;
        activeWarmups.put(playerId, data);
    }

    private void checkMovement(UUID playerId, WarmupData data) {
        if (!activeWarmups.containsKey(playerId))
            return;

        try {
            data.world.execute(() -> {
                if (!activeWarmups.containsKey(playerId))
                    return;

                try {
                    TransformComponent transform = data.store.getComponent(data.playerRef,
                            TransformComponent.getComponentType());
                    if (transform == null)
                        return;

                    Vector3d currentPos = transform.getPosition();
                    double dx = currentPos.x - data.startX;
                    double dy = currentPos.y - data.startY;
                    double dz = currentPos.z - data.startZ;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (distance > data.movementThreshold) {
                        data.playerData.sendMessage(MessageUtil.parseColored(config.getMessageMovedCancelled()));
                        cancelWarmup(playerId);
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void completeWarmup(UUID playerId, Runnable teleportAction) {
        WarmupData data = activeWarmups.remove(playerId);
        if (data == null)
            return;

        if (data.checkFuture != null)
            data.checkFuture.cancel(false);
        teleportAction.run();
    }

    public void cancelWarmup(UUID playerId) {
        WarmupData data = activeWarmups.remove(playerId);
        if (data != null) {
            if (data.checkFuture != null)
                data.checkFuture.cancel(false);
            if (data.teleportFuture != null)
                data.teleportFuture.cancel(false);
        }
    }

    private static class WarmupData {
        final PlayerRef playerData;
        final Ref<EntityStore> playerRef;
        final Store<EntityStore> store;
        final World world;
        final double startX, startY, startZ;
        final double movementThreshold;
        ScheduledFuture<?> checkFuture;
        ScheduledFuture<?> teleportFuture;

        WarmupData(PlayerRef playerData, Ref<EntityStore> playerRef, Store<EntityStore> store,
                World world, double startX, double startY, double startZ, double movementThreshold) {
            this.playerData = playerData;
            this.playerRef = playerRef;
            this.store = store;
            this.world = world;
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.movementThreshold = movementThreshold;
        }
    }
}
