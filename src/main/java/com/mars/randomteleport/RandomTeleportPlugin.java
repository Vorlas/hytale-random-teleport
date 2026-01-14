package com.mars.randomteleport;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.mars.randomteleport.commands.RandomTeleportCommand;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.logging.Level;

/**
 * Random Teleport Plugin - Randomly teleports players to safe locations.
 * 
 * Command: /rtp - Teleports player 2000-5000 blocks from spawn with 1 hour
 * cooldown
 * 
 * @author Mars
 * @version 1.0.0
 */
public class RandomTeleportPlugin extends JavaPlugin {

    private static RandomTeleportPlugin instance;

    /**
     * Constructor - Called when plugin is loaded by the server.
     * 
     * @param init The plugin initialization data provided by the server
     */
    public RandomTeleportPlugin(@NonNullDecl JavaPluginInit init) {
        super(init);
        instance = this;
    }

    /**
     * Called when plugin is set up.
     */
    @Override
    protected void setup() {
        super.setup();

        // Register the /rtp command
        this.getCommandRegistry().registerCommand(new RandomTeleportCommand());

        this.getLogger().at(Level.INFO).log("RandomTeleport plugin enabled! Use /rtp to teleport randomly.");
    }

    /**
     * Get plugin instance.
     */
    public static RandomTeleportPlugin getInstance() {
        return instance;
    }
}
