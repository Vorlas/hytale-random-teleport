package com.mars.randomteleport;

/**
 * Random Teleport Plugin - Randomly teleports players to safe locations.
 * 
 * @author Mars
 * @version 1.0.0
 */
public class RandomTeleportPlugin {

    private static RandomTeleportPlugin instance;

    /**
     * Constructor - Called when plugin is loaded.
     */
    public RandomTeleportPlugin() {
        instance = this;
        System.out.println("[RandomTeleport] Plugin loaded!");
    }

    /**
     * Called when plugin is enabled.
     */
    public void onEnable() {
        System.out.println("[RandomTeleport] Plugin enabled!");

        // TODO: Initialize your plugin here
        // - Load configuration
        // - Register event listeners
        // - Register commands
        // - Start services
    }

    /**
     * Called when plugin is disabled.
     */
    public void onDisable() {
        System.out.println("[RandomTeleport] Plugin disabled!");

        // TODO: Cleanup your plugin here
        // - Save data
        // - Stop services
        // - Close connections
    }

    /**
     * Get plugin instance.
     */
    public static RandomTeleportPlugin getInstance() {
        return instance;
    }
}
