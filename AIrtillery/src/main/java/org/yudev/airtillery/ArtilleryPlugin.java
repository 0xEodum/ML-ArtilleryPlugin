package org.yudev.airtillery;


import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.yudev.airtillery.ballistics.BallisticsRegistry;
import org.yudev.airtillery.ballistics.ProjectileBallistics;

public class ArtilleryPlugin extends JavaPlugin {
    private BallisticsRegistry ballistics;
    private ArtilleryManager artilleryManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ballistics = loadBallistics();
        artilleryManager = new ArtilleryManager(this, ballistics);

        getCommand("giveartillery").setExecutor(new ArtilleryCommandExecutor(this, artilleryManager));

        getServer().getPluginManager().registerEvents(new ArtilleryListener(this, artilleryManager), this);
        getServer().getPluginManager().registerEvents(new TntExplosionListener(this), this);

        getLogger().info("Artillery Plugin enabled (analytic ballistics, no external solver).");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("Artillery Plugin disabled!");
    }

    /**
     * Build the ballistics tables. The built-in constants match the game; the
     * config only has to be touched if a game update or another plugin changes
     * projectile physics.
     */
    private BallisticsRegistry loadBallistics() {
        BallisticsRegistry registry = new BallisticsRegistry(
                getConfig().getDouble("max-launch-speed", 12.0),
                getConfig().getDouble("max-flight-ticks", 600.0));

        ConfigurationSection physics = getConfig().getConfigurationSection("physics");
        if (physics == null) {
            return registry;
        }

        for (String type : physics.getKeys(false)) {
            ConfigurationSection section = physics.getConfigurationSection(type);
            if (section == null) {
                continue;
            }
            ProjectileBallistics defaults = registry.get(type);
            try {
                registry.override(type,
                        section.getDouble("gravity", defaults.getGravity()),
                        section.getDouble("drag", defaults.getDrag()),
                        section.getBoolean("gravity-before-move", defaults.isGravityBeforeMove()));
                getLogger().info("Ballistics override for " + type.toUpperCase() + ": "
                        + "gravity=" + registry.get(type).getGravity()
                        + ", drag=" + registry.get(type).getDrag()
                        + ", gravity-before-move=" + registry.get(type).isGravityBeforeMove());
            } catch (IllegalArgumentException e) {
                getLogger().warning("Ignoring invalid physics override for "
                        + type + ": " + e.getMessage());
            }
        }
        return registry;
    }

    public BallisticsRegistry getBallistics() {
        return ballistics;
    }

    public ArtilleryManager getArtilleryManager() {
        return artilleryManager;
    }
}
