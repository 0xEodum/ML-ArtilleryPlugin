package org.yudev.projectiletesting;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.yudev.projectiletesting.testing.AbstractProjectileTest;
import org.yudev.projectiletesting.testing.ProjectileCalibrationTest;
import org.yudev.projectiletesting.testing.RealProjectileTest;
import org.yudev.projectiletesting.testing.SimulatedProjectileTest;
import org.yudev.projectiletesting.utils.ProjectileType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProjectileTestingPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, AbstractProjectileTest> activeTests = new HashMap<>();

    private final NamespacedKey projectileTypeKey;
    private final NamespacedKey targetXKey;
    private final NamespacedKey targetYKey;
    private final NamespacedKey targetZKey;
    private final NamespacedKey simulateKey;

    public ProjectileTestingPlugin() {
        this.projectileTypeKey = new NamespacedKey(this, "projectile_type");
        this.targetXKey = new NamespacedKey(this, "target_x");
        this.targetYKey = new NamespacedKey(this, "target_y");
        this.targetZKey = new NamespacedKey(this, "target_z");
        this.simulateKey = new NamespacedKey(this, "simulate");
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ProjectileTestingPlugin has been enabled!");

        getCommand("givetestingarrow").setExecutor(this);
        getCommand("givetestingpotion").setExecutor(this);
        getCommand("givetestingtrident").setExecutor(this);
        getCommand("givetestingtnt").setExecutor(this);
        getCommand("calibrateprojectile").setExecutor(this);
    }

    @Override
    public void onDisable() {
        for (AbstractProjectileTest test : activeTests.values()) {
            test.cancel();
        }
        activeTests.clear();

        getLogger().info("ProjectileTestingPlugin has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эта команда может быть выполнена только игроком.");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("calibrateprojectile")) {
            ProjectileCalibrationTest.runCommand(this, sender, args);
            return true;
        }

        ProjectileType projectileType = null;

        if (command.getName().equalsIgnoreCase("givetestingarrow")) {
            projectileType = ProjectileType.ARROW;
        } else if (command.getName().equalsIgnoreCase("givetestingpotion")) {
            projectileType = ProjectileType.POTION;
        } else if (command.getName().equalsIgnoreCase("givetestingtrident")) {
            projectileType = ProjectileType.TRIDENT;
        } else if (command.getName().equalsIgnoreCase("givetestingtnt")) {
            projectileType = ProjectileType.TNT;
        }

        if (projectileType == null) {
            return false;
        }

        if (args.length < 3 || args.length > 4) {
            player.sendMessage(ChatColor.RED + "Использование: /" + command.getName() + " <X> <Y> <Z> [isSimulating]");
            player.sendMessage(ChatColor.GRAY + "isSimulating: 1 для симуляции, 0 для пристрелки (по умолчанию 0)");
            return true;
        }

        try {
            double x = Double.parseDouble(args[0]);
            double y = Double.parseDouble(args[1]);
            double z = Double.parseDouble(args[2]);
            boolean isSimulating = args.length == 4 && args[3].equals("1");

            giveTestingProjectile(player, x, y, z, projectileType, isSimulating);
            player.sendMessage(ChatColor.GREEN + "Тестировочный " + projectileType.getDisplayName() +
                    " с целью (" + x + ", " + y + ", " + z + ") был выдан!" +
                    (isSimulating ? " (с симуляцией)" : " (пристрелка)"));
            return true;
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Координаты должны быть числами.");
            return true;
        }
    }

    private void giveTestingProjectile(Player player, double x, double y, double z,
                                       ProjectileType projectileType, boolean isSimulating) {
        ItemStack item = new ItemStack(projectileType.getItemMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Тестировочный " + projectileType.getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Цель: " + ChatColor.WHITE + x + ", " + y + ", " + z);
            lore.add(ChatColor.GRAY + "Режим: " + (isSimulating ? "Симуляция" : "Пристрелка"));
            lore.add(ChatColor.GRAY + "Используйте ПКМ для запуска теста");
            meta.setLore(lore);

            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(projectileTypeKey, PersistentDataType.STRING, projectileType.name());
            container.set(targetXKey, PersistentDataType.DOUBLE, x);
            container.set(targetYKey, PersistentDataType.DOUBLE, y);
            container.set(targetZKey, PersistentDataType.DOUBLE, z);
            container.set(simulateKey, PersistentDataType.BYTE, isSimulating ? (byte)1 : (byte)0);

            item.setItemMeta(meta);
        }

        player.getInventory().addItem(item);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && event.getAction().name().contains("RIGHT_CLICK")) {
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();

                if (container.has(projectileTypeKey, PersistentDataType.STRING)) {
                    event.setCancelled(true);

                    ProjectileType projectileType = ProjectileType.valueOf(
                            container.get(projectileTypeKey, PersistentDataType.STRING));
                    double targetX = container.get(targetXKey, PersistentDataType.DOUBLE);
                    double targetY = container.get(targetYKey, PersistentDataType.DOUBLE);
                    double targetZ = container.get(targetZKey, PersistentDataType.DOUBLE);
                    boolean isSimulating = container.get(simulateKey, PersistentDataType.BYTE) == 1;

                    startProjectileTest(player, targetX, targetY, targetZ, projectileType, isSimulating);
                }
            }
        }
    }

    private void startProjectileTest(Player player, double targetX, double targetY, double targetZ,
                                     ProjectileType projectileType, boolean isSimulating) {
        if (activeTests.containsKey(player.getUniqueId())) {
            activeTests.get(player.getUniqueId()).cancel();
            activeTests.remove(player.getUniqueId());
        }

        Location targetLocation = new Location(player.getWorld(), targetX, targetY, targetZ);

        AbstractProjectileTest test;
        if (isSimulating) {
            test = new SimulatedProjectileTest(this, player, targetLocation, projectileType);
        } else {
            test = new RealProjectileTest(this, player, targetLocation, projectileType);
        }

        activeTests.put(player.getUniqueId(), test);
        test.start();
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!event.getEntity().hasMetadata("test_projectile_id")) {
            return;
        }

        event.getEntity().getWorld().spawnParticle(
                Particle.FLAME,
                event.getEntity().getLocation(),
                20, 0.2, 0.2, 0.2, 0.01
        );
    }
}
