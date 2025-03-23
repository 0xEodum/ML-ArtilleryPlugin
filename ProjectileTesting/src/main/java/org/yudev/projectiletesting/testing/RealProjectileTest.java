package org.yudev.projectiletesting.testing;


import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.yudev.projectiletesting.utils.ProjectilePhysics;
import org.yudev.projectiletesting.utils.ProjectileType;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

public class RealProjectileTest extends AbstractProjectileTest {
    private double lowerBound;
    private double upperBound;
    private boolean lastWasOvershot = false;
    private boolean firstShot = true;

    private Queue<Double> lastDistances = new LinkedList<>();
    private Queue<Double> lastVelocities = new LinkedList<>();
    private int sameResultCount = 0;
    private double jitterAmount = 0.02;
    private Random random = new Random();
    private int strategyIndex = 0;
    private final int MAX_SAME_RESULTS = 3;
    private boolean useRandomJitter = false;
    private double lastVelocity = 0.0;
    private int consecutiveEqualVelocities = 0;

    private final String[] strategies = {
            "BINARY_SEARCH",
            "GRADIENT",
            "AGGRESSIVE",
            "RANDOM_WALK"
    };

    public RealProjectileTest(JavaPlugin plugin, Player player, Location targetLocation,
                              ProjectileType projectileType) {
        super(plugin, player, targetLocation, projectileType);

        this.lowerBound = this.currentVelocity * 0.5;
        this.upperBound = this.currentVelocity * 1.5;
    }

    @Override
    protected void onTestStart() {
        launchProjectile();

        visualizeOptimalTrajectory();
    }

    private void visualizeOptimalTrajectory() {
        new BukkitRunnable() {
            int counter = 0;
            @Override
            public void run() {
                if (counter >= 100 || !testActive) {
                    this.cancel();
                    return;
                }

                Vector velocity = direction.clone().multiply(currentVelocity);
                Vector position = launchLocation.toVector();
                double gravity = projectileType.getGravity();
                double drag = projectileType.getDrag();
                boolean dragBeforeAcceleration = projectileType.isDragBeforeAcceleration();

                for (int i = 0; i < 100; i++) {
                    position.add(velocity);

                    if (dragBeforeAcceleration) {
                        velocity.multiply(1.0 - drag);
                        velocity.setY(velocity.getY() - gravity);
                    } else {
                        velocity.setY(velocity.getY() - gravity);
                        velocity.multiply(1.0 - drag);
                    }

                    if (i % 5 == 0) {
                        player.getWorld().spawnParticle(
                                Particle.END_ROD,
                                position.getX(), position.getY(), position.getZ(),
                                1, 0, 0, 0, 0
                        );
                    }
                }

                counter++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    @Override
    protected void launchProjectile() {
        if (!testActive) {
            return;
        }

        if (iterations >= MAX_ITERATIONS) {
            cancel();
            player.sendMessage(ChatColor.RED + "Достигнуто максимальное количество итераций без успеха.");
            return;
        }

        iterations++;
        entityId++;

        if (currentProjectile != null && currentProjectile.isValid()) {
            currentProjectile.remove();
        }

        double finalVelocity = currentVelocity;
        if (useRandomJitter) {
            double jitter = (random.nextDouble() * 2 - 1) * jitterAmount;
            finalVelocity = currentVelocity * (1 + jitter);
            finalVelocity = Math.max(minVelocity, Math.min(maxVelocity, finalVelocity));
        }

        switch (projectileType) {
            case ARROW:
                Arrow arrow = player.getWorld().spawnArrow(launchLocation, direction, (float) finalVelocity, 0);
                arrow.setMetadata("test_projectile_id", new FixedMetadataValue(plugin, entityId));
                arrow.setMetadata("player_id", new FixedMetadataValue(plugin, player.getUniqueId().toString()));
                arrow.setPersistent(false);
                currentProjectile = arrow;
                break;

            case POTION:
                ThrownPotion potion = player.getWorld().spawn(launchLocation, ThrownPotion.class);
                potion.setVelocity(direction.clone().multiply(finalVelocity));
                potion.setMetadata("test_projectile_id", new FixedMetadataValue(plugin, entityId));
                potion.setMetadata("player_id", new FixedMetadataValue(plugin, player.getUniqueId().toString()));
                currentProjectile = potion;
                break;

            case TRIDENT:
                Trident trident = player.getWorld().spawn(launchLocation, Trident.class);
                trident.setVelocity(direction.clone().multiply(finalVelocity));
                trident.setMetadata("test_projectile_id", new FixedMetadataValue(plugin, entityId));
                trident.setMetadata("player_id", new FixedMetadataValue(plugin, player.getUniqueId().toString()));
                currentProjectile = trident;
                break;

            case TNT:
                TNTPrimed tnt = player.getWorld().spawn(launchLocation, TNTPrimed.class);
                tnt.setVelocity(direction.clone().multiply(finalVelocity));
                tnt.setFuseTicks(80);
                tnt.setMetadata("test_projectile_id", new FixedMetadataValue(plugin, entityId));
                tnt.setMetadata("player_id", new FixedMetadataValue(plugin, player.getUniqueId().toString()));
                currentProjectile = tnt;
                break;
        }

        ProjectileTracker tracker = new ProjectileTracker(entityId, currentProjectile, player.getUniqueId(),
                targetLocation, this);

        tracker.startTracking();

        player.sendMessage(ChatColor.YELLOW + "Запущен " + projectileType.getDisplayName() +
                " #" + iterations + " со скоростью " +
                String.format("%.2f", finalVelocity) + " блоков/тик");
    }

    public void onProjectileLanded(double distance, Location finalLocation) {
        if (!testActive) {
            return;
        }

        evaluateResult(distance, finalLocation);

        if (testActive) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (testActive) {
                        launchProjectile();
                    }
                }
            }.runTaskLater(plugin, 20L);
        }
    }

    private void evaluateResult(double distance, Location finalLocation) {
        double previousVelocity = currentVelocity;
        if (distance < 1.5) {
            player.sendMessage(ChatColor.GREEN + "Попадание в цель! Оптимальная скорость: " +
                    String.format("%.2f", currentVelocity) + " блоков/тик");
            cancel();
            return;
        }
        lastDistances.add(distance);
        lastVelocities.add(currentVelocity);

        if (lastDistances.size() > 5) {
            lastDistances.poll();
            lastVelocities.poll();
        }

        if (lastDistances.size() >= 2) {
            double[] distArray = lastDistances.stream().mapToDouble(Double::doubleValue).toArray();
            boolean similarDistances = true;

            for (int i = 1; i < distArray.length; i++) {
                if (Math.abs(distArray[0] - distArray[i]) > 0.1) {
                    similarDistances = false;
                    break;
                }
            }

            if (similarDistances) {
                sameResultCount++;

                if (sameResultCount >= MAX_SAME_RESULTS) {
                    changeStrategy();
                    sameResultCount = 0;
                }
            } else {
                sameResultCount = 0;
            }
        }

        boolean isOvershot = ProjectilePhysics.isOvershot(initialDirection, finalLocation, targetLocation);

        switch (strategies[strategyIndex]) {
            case "BINARY_SEARCH":
                adjustVelocityBinarySearch(distance, isOvershot);
                break;
            case "GRADIENT":
                adjustVelocityGradient(distance, isOvershot);
                break;
            case "AGGRESSIVE":
                adjustVelocityAggressive(distance, isOvershot);
                break;
            case "RANDOM_WALK":
                adjustVelocityRandomWalk(distance, isOvershot);
                break;
            default:
                adjustVelocityBinarySearch(distance, isOvershot);
        }

        currentVelocity = Math.max(minVelocity, Math.min(maxVelocity, currentVelocity));

        if (Math.abs(currentVelocity - previousVelocity) < 0.001) {
            consecutiveEqualVelocities++;

            if (consecutiveEqualVelocities >= 3) {
                if (!isOvershot) {
                    currentVelocity += 0.05;
                    player.sendMessage(ChatColor.GOLD + "Обнаружено застревание! Принудительное увеличение скорости.");
                } else {
                    currentVelocity -= 0.05;
                    player.sendMessage(ChatColor.GOLD + "Обнаружено застревание! Принудительное уменьшение скорости.");
                }
                consecutiveEqualVelocities = 0;
            }
        } else {
            consecutiveEqualVelocities = 0;
        }

        String shotResult = isOvershot ? "перелет" : "недолет";
        String strategyName = getStrategyName(strategies[strategyIndex]);

        player.sendMessage(ChatColor.GRAY + "Результат: " + shotResult +
                ". Стратегия: " + strategyName +
                ". Новая скорость: " + String.format("%.2f", currentVelocity) +
                " блоков/тик");

        lastDistance = distance;
        lastWasOvershot = isOvershot;
        lastVelocity = previousVelocity;
    }

    private String getStrategyName(String strategy) {
        switch (strategy) {
            case "BINARY_SEARCH": return "Бинарный поиск";
            case "GRADIENT": return "Градиентный спуск";
            case "AGGRESSIVE": return "Агрессивный подбор";
            case "RANDOM_WALK": return "Случайные вариации";
            default: return strategy;
        }
    }

    private void changeStrategy() {
        strategyIndex = (strategyIndex + 1) % strategies.length;

        useRandomJitter = strategies[strategyIndex].equals("RANDOM_WALK");

        jitterAmount *= 1.5;

        if (strategies[strategyIndex].equals("AGGRESSIVE")) {
            if (lastWasOvershot) {
                currentVelocity *= 0.7;
            } else {
                currentVelocity *= 1.3;
            }
        }

        player.sendMessage(ChatColor.GOLD + "Обнаружено зацикливание! Переключение на стратегию: " +
                getStrategyName(strategies[strategyIndex]));
    }

    private void adjustVelocityBinarySearch(double distance, boolean isOvershot) {
        if (firstShot) {
            firstShot = false;

            if (isOvershot) {
                upperBound = currentVelocity;
                currentVelocity *= 0.8;
            } else {
                lowerBound = currentVelocity;
                currentVelocity *= 1.2;
            }

            return;
        }

        if (isOvershot != lastWasOvershot) {
            if (isOvershot) {
                upperBound = currentVelocity;
            } else {
                lowerBound = currentVelocity;
            }

            currentVelocity = (lowerBound + upperBound) / 2;
        } else {
            if (isOvershot) {
                upperBound = currentVelocity;
                currentVelocity = (lowerBound + currentVelocity) / 2;
            } else {
                lowerBound = currentVelocity;

                double weight = 0.6;
                currentVelocity = currentVelocity * (1 - weight) + upperBound * weight;

                if (upperBound - currentVelocity < 0.3) {
                    upperBound = upperBound * 1.5;
                    player.sendMessage(ChatColor.GOLD + "Расширение диапазона поиска. Новая верхняя граница: " +
                            String.format("%.2f", upperBound));
                }
            }
        }
    }

    private void adjustVelocityGradient(double distance, boolean isOvershot) {
        double velocityChange;

        if (distance < lastDistance) {
            if (isOvershot) {
                velocityChange = -0.05 * currentVelocity;
            } else {
                velocityChange = 0.05 * currentVelocity;
            }
        } else {
            if (isOvershot) {
                velocityChange = -0.1 * currentVelocity;
            } else {
                velocityChange = 0.1 * currentVelocity;
            }
        }

        currentVelocity += velocityChange;
    }

    private void adjustVelocityAggressive(double distance, boolean isOvershot) {
        double changePercent;

        if (distance > 5.0) {
            changePercent = 0.3;
        } else if (distance > 2.0) {
            changePercent = 0.2;
        } else {
            changePercent = 0.1;
        }

        if (isOvershot) {
            currentVelocity *= (1 - changePercent);
        } else {
            currentVelocity *= (1 + changePercent);
        }
    }

    private void adjustVelocityRandomWalk(double distance, boolean isOvershot) {
        double baseChange;
        if (isOvershot) {
            baseChange = -0.05 * currentVelocity;
        } else {
            baseChange = 0.05 * currentVelocity;
        }

        double randomFactor = (random.nextDouble() * 2 - 1) * jitterAmount * currentVelocity;

        currentVelocity += baseChange + randomFactor;
    }

    public class ProjectileTracker {
        private final int id;
        private final Entity entity;
        private final java.util.UUID playerId;
        private final Location targetLocation;
        private final RealProjectileTest projectileTest;

        private Location lastLocation;
        private double minDistance = Double.MAX_VALUE;
        private Location closestLocation = null;

        public boolean hitDetected = false;
        public Location hitLocation = null;
        private boolean hasLanded = false;
        private boolean processed = false;

        private int trackingTicks = 0;
        private final int MAX_TRACKING_TICKS = 400;
        private Vector lastVelocity = null;
        private int stationaryTicks = 0;

        public ProjectileTracker(int id, Entity entity, java.util.UUID playerId,
                                 Location targetLocation, RealProjectileTest projectileTest) {
            this.id = id;
            this.entity = entity;
            this.playerId = playerId;
            this.targetLocation = targetLocation;
            this.projectileTest = projectileTest;
            this.lastLocation = entity.getLocation().clone();
        }

        public void startTracking() {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (processed || trackingTicks >= MAX_TRACKING_TICKS) {
                        if (!processed) {
                            processProjectileLanding();
                        }
                        this.cancel();
                        return;
                    }

                    trackingTicks++;

                    if (entity == null || !entity.isValid()) {
                        if (!processed) {
                            processProjectileLanding();
                        }
                        this.cancel();
                        return;
                    }

                    Location currentLocation = entity.getLocation();

                    if (lastVelocity != null) {
                        Vector currentVelocity = entity.getVelocity();
                        double velocityChange = lastVelocity.distance(currentVelocity);

                        if (velocityChange < 0.01 && currentVelocity.lengthSquared() < 0.01) {
                            stationaryTicks++;
                        } else {
                            stationaryTicks = 0;
                        }

                        if (stationaryTicks >= 10) {
                            hasLanded = true;
                        }
                    }

                    lastVelocity = entity.getVelocity().clone();

                    double distance = currentLocation.distance(targetLocation);
                    if (distance < minDistance) {
                        minDistance = distance;
                        closestLocation = currentLocation.clone();
                    }

                    if (distance <= 1.5) {
                        Player player = player = plugin.getServer().getPlayer(playerId);
                        if (player != null) {
                            player.sendMessage(ChatColor.GREEN + "Попадание в цель! Расстояние: " +
                                    String.format("%.2f", distance) + " блоков.");

                            player.sendMessage(ChatColor.GOLD + "Оптимальная скорость: " +
                                    String.format("%.2f", projectileTest.getCurrentVelocity()) +
                                    " блоков/тик");
                        }

                        projectileTest.cancel();
                        processed = true;
                        this.cancel();
                        return;
                    }

                    if (hitDetected || entity.isOnGround() ||
                            (entity instanceof Projectile && ((Projectile)entity).isOnGround())) {
                        hasLanded = true;
                    }

                    if (entity instanceof TNTPrimed && ((TNTPrimed)entity).getFuseTicks() <= 0) {
                        hasLanded = true;
                    }

                    if (hasLanded && !processed) {
                        processProjectileLanding();
                        this.cancel();
                    }

                    lastLocation = currentLocation.clone();
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }

        private void processProjectileLanding() {
            processed = true;

            Location finalLocation = hitLocation != null ? hitLocation : lastLocation;

            double finalDistance = minDistance;
            Location bestLocation = closestLocation != null ? closestLocation : finalLocation;

            if (finalLocation != null) {
                finalLocation.getWorld().spawnParticle(
                        Particle.FLAME,
                        finalLocation,
                        20, 0.2, 0.2, 0.2, 0.01
                );
            }

            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(ChatColor.GRAY + projectileType.getDisplayName() +
                        " упал на расстоянии " +
                        String.format("%.2f", finalDistance) + " блоков от цели");
            }

            projectileTest.onProjectileLanded(finalDistance, bestLocation);
        }
    }
}
