package org.yudev.trajectoryrecorder;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class TestingStickManager implements Listener {
    private final TrajectoryRecorderPlugin plugin;
    private final NamespacedKey testingStickKey;
    private final NamespacedKey batchSizeKey;
    private final NamespacedKey speedStepKey;
    private final NamespacedKey minSpeedKey;
    private final NamespacedKey maxSpeedKey;
    private final NamespacedKey launchAngleKey;

    public TestingStickManager(TrajectoryRecorderPlugin plugin) {
        this.plugin = plugin;
        this.testingStickKey = new NamespacedKey(plugin, "testing_stick");
        this.batchSizeKey = new NamespacedKey(plugin, "batch_size");
        this.speedStepKey = new NamespacedKey(plugin, "speed_step");
        this.minSpeedKey = new NamespacedKey(plugin, "min_speed");
        this.maxSpeedKey = new NamespacedKey(plugin, "max_speed");
        this.launchAngleKey = new NamespacedKey(plugin, "launch_angle");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void giveTestingStick(Player player, int batchSize, double speedStep, double minSpeed, double maxSpeed, double launchAngle) {
        ItemStack stick = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = stick.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Trajectory Testing Stick");
            meta.setLore(Arrays.asList(
                    ChatColor.YELLOW + "Right click to start a test session",
                    ChatColor.GRAY + "Batch size: " + batchSize,
                    ChatColor.GRAY + "Speed range: " + minSpeed + " - " + maxSpeed + " (step: " + speedStep + ")",
                    ChatColor.GRAY + "Launch angle: " + launchAngle + "°"
            ));

            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(testingStickKey, PersistentDataType.BYTE, (byte) 1);
            container.set(batchSizeKey, PersistentDataType.INTEGER, batchSize);
            container.set(speedStepKey, PersistentDataType.DOUBLE, speedStep);
            container.set(minSpeedKey, PersistentDataType.DOUBLE, minSpeed);
            container.set(maxSpeedKey, PersistentDataType.DOUBLE, maxSpeed);
            container.set(launchAngleKey, PersistentDataType.DOUBLE, launchAngle);

            stick.setItemMeta(meta);

            player.getInventory().addItem(stick);
            player.sendMessage(ChatColor.GREEN + "You have received a Trajectory Testing Stick!");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.hasItemMeta() && event.getAction() == Action.RIGHT_CLICK_AIR) {
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer container = meta.getPersistentDataContainer();

            if (container.has(testingStickKey, PersistentDataType.BYTE)) {
                int batchSize = container.getOrDefault(batchSizeKey, PersistentDataType.INTEGER, 3);
                double speedStep = container.getOrDefault(speedStepKey, PersistentDataType.DOUBLE, 0.5);
                double minSpeed = container.getOrDefault(minSpeedKey, PersistentDataType.DOUBLE, 0.5);
                double maxSpeed = container.getOrDefault(maxSpeedKey, PersistentDataType.DOUBLE, 12.0);
                double launchAngle = container.getOrDefault(launchAngleKey, PersistentDataType.DOUBLE, 45.0);

                plugin.getTestLauncher().startTestSession(player, minSpeed, maxSpeed, speedStep, batchSize, launchAngle);

                event.setCancelled(true);
            }
        }
    }
}