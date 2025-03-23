package org.yudev.projectiletesting.testing;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.yudev.projectiletesting.utils.ProjectilePhysics;
import org.yudev.projectiletesting.utils.ProjectileType;

public class SimulatedProjectileTest extends AbstractProjectileTest {
    private double simulatedVelocity;
    private double actualDistance;
    private boolean testComplete = false;
    private final int MAX_VISUALIZATION_TICKS = 200;

    public SimulatedProjectileTest(JavaPlugin plugin, Player player, Location targetLocation,
                                   ProjectileType projectileType) {
        super(plugin, player, targetLocation, projectileType);
    }

    @Override
    protected void onTestStart() {
        player.sendMessage(ChatColor.YELLOW + "Начинаю моделирование траектории " +
                projectileType.getDisplayName() + "...");

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    simulatedVelocity = findOptimalVelocity();

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            onSimulationComplete(simulatedVelocity);
                        }
                    }.runTask(plugin);

                } catch (Exception e) {
                    final String errorMessage = e.getMessage();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.sendMessage(ChatColor.RED + "Ошибка моделирования: " + errorMessage);
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private class SimulationResult {
        public final double distance;
        public final boolean isOvershoot;
        public final Vector closestPoint;

        public SimulationResult(double distance, boolean isOvershoot, Vector closestPoint) {
            this.distance = distance;
            this.isOvershoot = isOvershoot;
            this.closestPoint = closestPoint;
        }
    }

    private double findOptimalVelocity() {
        double heightDifference = targetLocation.getY() - launchLocation.getY();
        double horizontalDistance = Math.sqrt(
                Math.pow(targetLocation.getX() - launchLocation.getX(), 2) +
                        Math.pow(targetLocation.getZ() - launchLocation.getZ(), 2)
        );

        double initialGuess = currentVelocity;

        double lowerBound = Math.max(minVelocity, initialGuess * 0.5);
        double upperBound = Math.min(maxVelocity, initialGuess * 2.5);

        double bestVelocity = initialGuess;
        double bestDistance = Double.MAX_VALUE;

        int iterations = 0;
        final int MAX_ITERATIONS = 30;
        final double PRECISION = 0.01;

        if (horizontalDistance > 200) {
            upperBound = Math.min(maxVelocity, initialGuess * 3.0);
        }

        boolean expandedRange = false;
        int consecutiveNonImprovements = 0;

        while (iterations < MAX_ITERATIONS && upperBound - lowerBound > PRECISION) {
            iterations++;

            double midVelocity = (lowerBound + upperBound) / 2;

            SimulationResult midResult = simulateTrajectory(midVelocity);

            if (midResult.distance < bestDistance) {
                bestDistance = midResult.distance;
                bestVelocity = midVelocity;
                consecutiveNonImprovements = 0;

                if (bestDistance < 0.5) {
                    break;
                }
            } else {
                consecutiveNonImprovements++;
            }

            SimulationResult lowerResult = simulateTrajectory(lowerBound);
            SimulationResult upperResult = simulateTrajectory(upperBound);

            if (iterations > 3 && !expandedRange) {
                if (!lowerResult.isOvershoot && !midResult.isOvershoot && !upperResult.isOvershoot) {
                    double oldUpperBound = upperBound;
                    upperBound = Math.min(maxVelocity, upperBound * 2.0);

                    if (upperBound > oldUpperBound) {
                        expandedRange = true;
                        continue;
                    }
                } else if (lowerResult.isOvershoot && midResult.isOvershoot && upperResult.isOvershoot) {
                    double oldLowerBound = lowerBound;
                    lowerBound = Math.max(minVelocity, lowerBound * 0.5);

                    if (lowerBound < oldLowerBound) {
                        expandedRange = true;
                        continue;
                    }
                }
            }

            if (midResult.isOvershoot) {
                upperBound = midVelocity;
            } else {
                lowerBound = midVelocity;
            }

            if (consecutiveNonImprovements >= 5) {
                double jumpFactor = 0.2 + Math.random() * 0.6;
                bestVelocity = lowerBound + (upperBound - lowerBound) * jumpFactor;

                SimulationResult jumpResult = simulateTrajectory(bestVelocity);
                if (jumpResult.distance < bestDistance) {
                    bestDistance = jumpResult.distance;
                }

                consecutiveNonImprovements = 0;
            }
        }

        return bestVelocity;
    }

    private SimulationResult simulateTrajectory(double velocity) {
        Vector position = launchLocation.toVector();
        Vector vel = direction.clone().multiply(velocity);

        double gravity = projectileType.getGravity();
        double drag = projectileType.getDrag();
        boolean dragBeforeAcceleration = projectileType.isDragBeforeAcceleration();

        double minDistance = Double.MAX_VALUE;
        Vector closestPoint = null;
        int tickAtClosestPoint = 0;

        Vector horizontalDirection = new Vector(direction.getX(), 0, direction.getZ()).normalize();

        for (int tick = 0; tick < MAX_VISUALIZATION_TICKS; tick++) {
            position.add(vel);

            if (dragBeforeAcceleration) {
                vel.multiply(1.0 - drag);
                vel.setY(vel.getY() - gravity);
            } else {
                vel.setY(vel.getY() - gravity);
                vel.multiply(1.0 - drag);
            }

            double distance = position.distance(targetLocation.toVector());
            if (distance < minDistance) {
                minDistance = distance;
                closestPoint = position.clone();
                tickAtClosestPoint = tick;
            }

            if (tick > tickAtClosestPoint + 20) {
                break;
            }

            if (position.getY() <= 0) {
                break;
            }
        }

        boolean isOvershoot = false;
        if (closestPoint != null) {
            Vector targetToClosest = closestPoint.clone().subtract(targetLocation.toVector());
            double projection = targetToClosest.clone().setY(0).dot(horizontalDirection);
            isOvershoot = projection > 0;
        }

        return new SimulationResult(minDistance, isOvershoot, closestPoint);
    }

    private void onSimulationComplete(double velocity) {
        currentVelocity = velocity;

        player.sendMessage(ChatColor.GREEN + "Моделирование завершено!");
        player.sendMessage(ChatColor.YELLOW + "Найдена оптимальная скорость: " +
                String.format("%.2f", velocity) + " блоков/тик");
        player.sendMessage(ChatColor.GRAY + "Используемая гравитация: " +
                String.format("%.3f", projectileType.getGravity()) + " блоков/тик²");

        visualizeSimulatedTrajectory(velocity);

        new BukkitRunnable() {
            @Override
            public void run() {
                launchProjectile();
            }
        }.runTaskLater(plugin, 20L);
    }

    private void visualizeSimulatedTrajectory(double velocity) {
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 100 || testComplete) {
                    this.cancel();
                    return;
                }

                count++;

                Vector position = launchLocation.toVector();
                Vector initialVelocity = direction.clone().multiply(velocity);
                Vector currentVelocity = initialVelocity.clone();

                double gravity = projectileType.getGravity();
                double drag = projectileType.getDrag();
                boolean dragBeforeAcceleration = projectileType.isDragBeforeAcceleration();

                for (int tick = 0; tick < MAX_VISUALIZATION_TICKS; tick++) {
                    if (tick % 3 != 0) {
                        position.add(currentVelocity);

                        if (dragBeforeAcceleration) {
                            currentVelocity.multiply(1.0 - drag);
                            currentVelocity.setY(currentVelocity.getY() - gravity);
                        } else {
                            currentVelocity.setY(currentVelocity.getY() - gravity);
                            currentVelocity.multiply(1.0 - drag);
                        }
                        continue;
                    }

                    position.add(currentVelocity);

                    if (dragBeforeAcceleration) {
                        currentVelocity.multiply(1.0 - drag);
                        currentVelocity.setY(currentVelocity.getY() - gravity);
                    } else {
                        currentVelocity.setY(currentVelocity.getY() - gravity);
                        currentVelocity.multiply(1.0 - drag);
                    }

                    player.getWorld().spawnParticle(
                            Particle.VILLAGER_HAPPY,
                            position.getX(), position.getY(), position.getZ(),
                            1, 0, 0, 0, 0
                    );

                    Location particleLocation = new Location(player.getWorld(),
                            position.getX(), position.getY(), position.getZ());
                    if (!particleLocation.getBlock().isPassable()) {
                        break;
                    }

                    double distance = position.distance(targetLocation.toVector());
                    if (distance < 0.5) {
                        player.getWorld().spawnParticle(
                                Particle.VILLAGER_HAPPY,
                                targetLocation,
                                10, 0.3, 0.3, 0.3, 0.05
                        );
                        break;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    @Override
    protected void launchProjectile() {
        if (!testActive) {
            return;
        }

        testComplete = false;
        entityId++;

        if (currentProjectile != null && currentProjectile.isValid()) {
            currentProjectile.remove();
        }

        player.sendMessage(ChatColor.GREEN + "Запускаю " + projectileType.getDisplayName() +
                " со скоростью " + String.format("%.2f", currentVelocity) + " блоков/тик");

        Entity entity = null;
        switch (projectileType) {
            case ARROW:
                entity = player.getWorld().spawnArrow(launchLocation, direction, (float)currentVelocity, 0);
                break;
            case POTION:
                entity = player.getWorld().spawn(launchLocation, org.bukkit.entity.ThrownPotion.class);
                entity.setVelocity(direction.clone().multiply(currentVelocity));
                break;
            case TRIDENT:
                entity = player.getWorld().spawn(launchLocation, org.bukkit.entity.Trident.class);
                entity.setVelocity(direction.clone().multiply(currentVelocity));
                break;
            case TNT:
                entity = player.getWorld().spawn(launchLocation, org.bukkit.entity.TNTPrimed.class);
                entity.setVelocity(direction.clone().multiply(currentVelocity));
                ((org.bukkit.entity.TNTPrimed)entity).setFuseTicks(200);
                break;
        }

        currentProjectile = entity;
        entity.setMetadata("simulated_test_projectile", new FixedMetadataValue(plugin, true));
        entity.setMetadata("test_projectile_id", new FixedMetadataValue(plugin, entityId));

        trackProjectile();
    }

    private void trackProjectile() {
        new BukkitRunnable() {
            private int ticks = 0;
            private double minDistance = Double.MAX_VALUE;
            private Location closestLocation = null;
            private boolean hasLanded = false;

            @Override
            public void run() {
                ticks++;

                if (currentProjectile == null || !currentProjectile.isValid() || ticks > 400) {
                    processResult();
                    this.cancel();
                    return;
                }

                Location currentLocation = currentProjectile.getLocation();

                double distance = currentLocation.distance(targetLocation);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestLocation = currentLocation.clone();
                }

                if (currentProjectile.isOnGround() ||
                        (currentProjectile instanceof org.bukkit.entity.Projectile &&
                                ((org.bukkit.entity.Projectile)currentProjectile).isOnGround()) ||
                        currentProjectile.getVelocity().lengthSquared() < 0.01) {
                    hasLanded = true;
                    processResult();
                    this.cancel();
                    return;
                }

                if (currentProjectile instanceof org.bukkit.entity.TNTPrimed &&
                        ((org.bukkit.entity.TNTPrimed)currentProjectile).getFuseTicks() <= 0) {
                    hasLanded = true;
                    processResult();
                    this.cancel();
                    return;
                }

                if (distance < 1.5) {
                    player.sendMessage(ChatColor.GREEN + projectileType.getDisplayName() +
                            " попал в цель! Расстояние: " +
                            String.format("%.2f", distance) + " блоков");

                    player.getWorld().spawnParticle(
                            Particle.FLAME,
                            targetLocation,
                            30, 0.3, 0.3, 0.3, 0.05
                    );

                    processResult();
                    this.cancel();
                    return;
                }
            }

            private void processResult() {
                testComplete = true;

                if (closestLocation != null && minDistance >= 1.5) {
                    player.sendMessage(ChatColor.YELLOW + "Ближайшее расстояние до цели: " +
                            String.format("%.2f", minDistance) + " блоков");

                    player.getWorld().spawnParticle(
                            Particle.VILLAGER_HAPPY,
                            closestLocation,
                            10, 0.2, 0.2, 0.2, 0.01
                    );

                    actualDistance = minDistance;

                    player.sendMessage(ChatColor.GRAY + "Проверка точности симуляции: расчетная скорость " +
                            String.format("%.2f", simulatedVelocity) + ", итоговое расстояние: " +
                            String.format("%.2f", minDistance));

                    boolean isOvershoot = ProjectilePhysics.isOvershot(initialDirection, closestLocation, targetLocation);
                    player.sendMessage(ChatColor.GRAY + "Результат: " +
                            (isOvershoot ? "перелет" : "недолет"));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}