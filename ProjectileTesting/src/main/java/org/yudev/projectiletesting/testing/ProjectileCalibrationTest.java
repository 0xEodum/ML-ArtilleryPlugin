package org.yudev.projectiletesting.testing;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.yudev.projectiletesting.utils.ProjectileType;

import java.util.HashMap;
import java.util.Map;

public class ProjectileCalibrationTest {
    private final JavaPlugin plugin;
    private final Player player;
    private final Location launchLocation;
    private final ProjectileType projectileType;
    private final double velocity;
    private final double launchAngle;
    private boolean testActive = false;
    private int testCounter = 0;
    private Entity currentProjectile = null;
    private final Map<Integer, Double> flightDistances = new HashMap<>();
    private final int totalShotsPerTest = 5;

    public ProjectileCalibrationTest(JavaPlugin plugin, Player player,
                                     ProjectileType projectileType, double velocity, double launchAngle) {
        this.plugin = plugin;
        this.player = player;
        this.projectileType = projectileType;
        this.velocity = velocity;
        this.launchAngle = Math.toRadians(launchAngle);
        this.launchLocation = player.getLocation().clone().add(0, 3, 0);
    }

    public void start() {
        testActive = true;
        testCounter = 0;
        flightDistances.clear();

        player.sendMessage(ChatColor.GREEN + "Начинаем калибровку " + projectileType.getDisplayName());
        player.sendMessage(ChatColor.YELLOW + "Фиксированная скорость: " +
                String.format("%.2f", velocity) + " блоков/тик");
        player.sendMessage(ChatColor.YELLOW + "Фиксированный угол запуска: " +
                String.format("%.1f", Math.toDegrees(launchAngle)) + " градусов");

        launchNextProjectile();
    }

    private void launchNextProjectile() {
        if (!testActive || testCounter >= totalShotsPerTest) {
            if (testActive) {
                finishTest();
            }
            return;
        }

        testCounter++;

        if (currentProjectile != null && currentProjectile.isValid()) {
            currentProjectile.remove();
        }

        Vector direction = new Vector(1, 0, 0);
        direction.setY(Math.tan(launchAngle));
        direction.normalize();

        Entity entity = null;
        switch (projectileType) {
            case ARROW:
                entity = player.getWorld().spawnArrow(launchLocation, direction, (float)velocity, 0);
                break;
            case POTION:
                entity = player.getWorld().spawn(launchLocation, org.bukkit.entity.ThrownPotion.class);
                entity.setVelocity(direction.clone().multiply(velocity));
                break;
            case TRIDENT:
                entity = player.getWorld().spawn(launchLocation, org.bukkit.entity.Trident.class);
                entity.setVelocity(direction.clone().multiply(velocity));
                break;
            case TNT:
                entity = player.getWorld().spawn(launchLocation, org.bukkit.entity.TNTPrimed.class);
                entity.setVelocity(direction.clone().multiply(velocity));
                ((org.bukkit.entity.TNTPrimed)entity).setFuseTicks(80);
                break;
        }

        currentProjectile = entity;
        entity.setMetadata("calibration_projectile", new FixedMetadataValue(plugin, true));
        entity.setMetadata("calibration_id", new FixedMetadataValue(plugin, testCounter));

        player.sendMessage(ChatColor.GRAY + "Запущен тестовый снаряд #" + testCounter);

        trackProjectile(entity, testCounter);
    }

    private void trackProjectile(Entity projectile, int id) {
        new BukkitRunnable() {
            private int ticks = 0;
            private Location lastLocation = projectile.getLocation().clone();
            private boolean processed = false;
            private final int MAX_TRACKING_TICKS = 400;

            @Override
            public void run() {
                ticks++;

                if (projectile == null || !projectile.isValid() || ticks > MAX_TRACKING_TICKS) {
                    if (!processed) {
                        processResult(lastLocation, id);
                    }
                    this.cancel();
                    return;
                }

                lastLocation = projectile.getLocation().clone();

                if (projectile.isOnGround() ||
                        (projectile instanceof org.bukkit.entity.Projectile &&
                                ((org.bukkit.entity.Projectile)projectile).isOnGround()) ||
                        projectile.getVelocity().lengthSquared() < 0.01) {

                    if (!processed) {
                        processResult(lastLocation, id);
                    }
                    this.cancel();
                    return;
                }

                if (projectile instanceof org.bukkit.entity.TNTPrimed &&
                        ((org.bukkit.entity.TNTPrimed)projectile).getFuseTicks() <= 0) {

                    if (!processed) {
                        processResult(lastLocation, id);
                    }
                    this.cancel();
                    return;
                }
            }

            private void processResult(Location finalLocation, int id) {
                processed = true;

                double dx = finalLocation.getX() - launchLocation.getX();
                double dz = finalLocation.getZ() - launchLocation.getZ();
                double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

                flightDistances.put(id, horizontalDistance);

                finalLocation.getWorld().spawnParticle(
                        Particle.FLAME,
                        finalLocation,
                        20, 0.2, 0.2, 0.2, 0.01
                );

                player.sendMessage(ChatColor.GRAY + "Тестовый снаряд #" + id +
                        " пролетел " + String.format("%.2f", horizontalDistance) + " блоков");

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (testActive) {
                            launchNextProjectile();
                        }
                    }
                }.runTaskLater(plugin, 20L);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void finishTest() {
        testActive = false;

        if (flightDistances.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Не удалось собрать данные о дистанции полета снарядов");
            return;
        }

        double totalDistance = 0;
        for (double distance : flightDistances.values()) {
            totalDistance += distance;
        }
        double averageDistance = totalDistance / flightDistances.size();

        player.sendMessage(ChatColor.GREEN + "Результаты калибровки для " + projectileType.getDisplayName() + ":");
        player.sendMessage(ChatColor.YELLOW + "Средняя дистанция полета: " +
                String.format("%.2f", averageDistance) + " блоков");


        double sin2a = Math.sin(2 * launchAngle);
        double estimatedGravity = (velocity * velocity * sin2a) / averageDistance;
        double currentGravity = projectileType.getGravity();

        player.sendMessage(ChatColor.YELLOW + "Оценка гравитации: " +
                String.format("%.4f", estimatedGravity) + " блоков/тик²");
        player.sendMessage(ChatColor.YELLOW + "Текущее значение: " +
                String.format("%.4f", currentGravity) + " блоков/тик²");

        if (Math.abs(estimatedGravity - currentGravity) / currentGravity > 0.1) {
            player.sendMessage(ChatColor.GOLD + "Рекомендуется обновить значение гравитации в ProjectileType.java");
        } else {
            player.sendMessage(ChatColor.GREEN + "Текущее значение гравитации близко к реальному");
        }
    }

    public void cancel() {
        testActive = false;

        if (currentProjectile != null && currentProjectile.isValid()) {
            currentProjectile.remove();
        }
    }

    public static void runCommand(JavaPlugin plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эта команда может быть выполнена только игроком.");
            return;
        }

        Player player = (Player) sender;

        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Использование: /calibrateprojectile <тип> <скорость> <угол>");
            player.sendMessage(ChatColor.GRAY + "Типы: ARROW, POTION, TRIDENT, TNT");
            return;
        }

        ProjectileType type;
        try {
            type = ProjectileType.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Неизвестный тип снаряда. Доступные типы: ARROW, POTION, TRIDENT, TNT");
            return;
        }

        double velocity;
        try {
            velocity = Double.parseDouble(args[1]);
            if (velocity <= 0 || velocity > 10) {
                player.sendMessage(ChatColor.RED + "Скорость должна быть в диапазоне (0, 10]");
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Некорректное значение скорости");
            return;
        }

        double angle;
        try {
            angle = Double.parseDouble(args[2]);
            if (angle <= 0 || angle >= 90) {
                player.sendMessage(ChatColor.RED + "Угол должен быть в диапазоне (0, 90)");
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Некорректное значение угла");
            return;
        }

        ProjectileCalibrationTest test = new ProjectileCalibrationTest(plugin, player, type, velocity, angle);
        test.start();
    }
}