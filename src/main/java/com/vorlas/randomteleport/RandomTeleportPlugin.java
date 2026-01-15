package com.vorlas.randomteleport;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.vorlas.randomteleport.commands.RandomTeleportCommand;
import com.vorlas.randomteleport.config.RandomTeleportConfig;
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

        // Initialize configuration
        RandomTeleportConfig config = new RandomTeleportConfig(this.getDataDirectory());

        // Register the /rtp command
        this.getCommandRegistry().registerCommand(new RandomTeleportCommand(config));

        this.getLogger().at(Level.INFO).log("RandomTeleport plugin enabled! Use /rtp to teleport randomly.");
    }

    /**
     * Get plugin instance.
     */
    public static RandomTeleportPlugin getInstance() {
        return instance;
    }
}
