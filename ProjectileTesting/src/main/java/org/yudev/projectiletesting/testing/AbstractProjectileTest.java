package org.yudev.projectiletesting.testing;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.yudev.projectiletesting.utils.ProjectilePhysics;
import org.yudev.projectiletesting.utils.ProjectileType;

public abstract class AbstractProjectileTest {
    protected final JavaPlugin plugin;
    protected final Player player;
    protected final Location targetLocation;
    protected final Location launchLocation;
    protected final Vector direction;
    protected final Vector initialDirection;
    protected final ProjectileType projectileType;

    protected double currentVelocity;
    protected boolean testActive = false;
    protected Entity currentProjectile = null;

    protected double minVelocity = 0.1;
    protected double maxVelocity = 10.0;
    protected double lastDistance = Double.MAX_VALUE;
    protected int iterations = 0;
    protected int entityId = 0;
    protected final int MAX_ITERATIONS = 50;

    public AbstractProjectileTest(JavaPlugin plugin, Player player, Location targetLocation,
                                  ProjectileType projectileType) {
        this.plugin = plugin;
        this.player = player;
        this.targetLocation = targetLocation;
        this.projectileType = projectileType;

        this.launchLocation = player.getLocation().clone().add(0, 3, 0);

        double heightDifference = targetLocation.getY() - launchLocation.getY();
        double horizontalDistance = Math.sqrt(
                Math.pow(targetLocation.getX() - launchLocation.getX(), 2) +
                        Math.pow(targetLocation.getZ() - launchLocation.getZ(), 2)
        );

        double launchAngle = ProjectilePhysics.calculateOptimalLaunchAngle(
                horizontalDistance, heightDifference, projectileType);

        Vector horizontalDirection = new Vector(
                targetLocation.getX() - launchLocation.getX(),
                0,
                targetLocation.getZ() - launchLocation.getZ()
        ).normalize();

        this.direction = horizontalDirection.clone();
        this.direction.setY(Math.tan(launchAngle));
        this.direction.normalize();

        this.initialDirection = this.direction.clone();

        this.currentVelocity = ProjectilePhysics.estimateInitialVelocity(
                horizontalDistance, heightDifference, projectileType);

        player.sendMessage(ChatColor.YELLOW + "Тип снаряда: " + projectileType.getDisplayName());
        player.sendMessage(ChatColor.YELLOW + "Расчетная начальная скорость: " +
                String.format("%.2f", currentVelocity) + " блоков/тик");
        player.sendMessage(ChatColor.YELLOW + "Угол запуска: " +
                String.format("%.1f", Math.toDegrees(launchAngle)) + " градусов");

        if (heightDifference < 0) {
            player.sendMessage(ChatColor.YELLOW + "Запуск сверху вниз. Цель на " +
                    String.format("%.1f", Math.abs(heightDifference)) + " блоков ниже");
        } else if (heightDifference > 0) {
            player.sendMessage(ChatColor.YELLOW + "Запуск снизу вверх. Цель на " +
                    String.format("%.1f", heightDifference) + " блоков выше");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Цель на том же уровне");
        }
    }

    public void start() {
        testActive = true;

        visualizeTarget(player.getWorld(), targetLocation);

        onTestStart();
    }

    protected abstract void onTestStart();

    protected abstract void launchProjectile();

    public void cancel() {
        testActive = false;

        if (currentProjectile != null && currentProjectile.isValid()) {
            currentProjectile.remove();
        }
    }

    protected void visualizeTarget(World world, Location location) {
        new BukkitRunnable() {
            int counter = 0;
            @Override
            public void run() {
                if (counter >= 1200 || !testActive) {
                    this.cancel();
                    return;
                }

                world.spawnParticle(
                        Particle.END_ROD,
                        location.clone().add(0.5, 0.5, 0.5),
                        5, 0.25, 0.25, 0.25, 0.01
                );

                counter++;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    public double getCurrentVelocity() {
        return currentVelocity;
    }
}
