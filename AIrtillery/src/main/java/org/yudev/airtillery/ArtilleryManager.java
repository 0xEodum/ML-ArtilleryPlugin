package org.yudev.airtillery;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ArtilleryManager {
    private final ArtilleryPlugin plugin;
    private final PythonClient pythonClient;
    private final Random random = new Random();
    private final double heightRatio;

    private final NamespacedKey IS_ARTILLERY_KEY;
    private final NamespacedKey DEBUG_KEY;
    private final NamespacedKey FIRE_MODE_KEY;
    private final NamespacedKey PROJECTILE_TYPE_KEY;
    private final NamespacedKey PATTERN_KEY;
    private final NamespacedKey MAX_RANGE_KEY;
    private final NamespacedKey PROJECTILE_COUNT_KEY;
    private final NamespacedKey RADIUS_KEY;
    private final NamespacedKey POTION_EFFECT_KEY;
    private final NamespacedKey POTION_DURATION_KEY;
    private final NamespacedKey POTION_AMPLIFIER_KEY;

    private final Map<Entity, BukkitTask> firedProjectiles = new HashMap<>();
    private final Map<Entity, BukkitTask> visualizationTasks = new HashMap<>();

    public ArtilleryManager(ArtilleryPlugin plugin, PythonClient pythonClient) {
        this.plugin = plugin;
        this.pythonClient = pythonClient;
        this.heightRatio = plugin.getConfig().getDouble("height-ratio", 0.2);

        this.IS_ARTILLERY_KEY = new NamespacedKey(plugin, "is_artillery");
        this.DEBUG_KEY = new NamespacedKey(plugin, "debug");
        this.FIRE_MODE_KEY = new NamespacedKey(plugin, "fire_mode");
        this.PROJECTILE_TYPE_KEY = new NamespacedKey(plugin, "projectile_type");
        this.PATTERN_KEY = new NamespacedKey(plugin, "pattern");
        this.MAX_RANGE_KEY = new NamespacedKey(plugin, "max_range");
        this.PROJECTILE_COUNT_KEY = new NamespacedKey(plugin, "projectile_count");
        this.RADIUS_KEY = new NamespacedKey(plugin, "radius");
        this.POTION_EFFECT_KEY = new NamespacedKey(plugin, "potion_effect");
        this.POTION_DURATION_KEY = new NamespacedKey(plugin, "potion_duration");
        this.POTION_AMPLIFIER_KEY = new NamespacedKey(plugin, "potion_amplifier");
    }

    /**
     * Создает и выдает артиллерийский предмет игроку
     */
    public void giveArtilleryItem(Player player, boolean isDebug, String fireMode, String projectileType,
                                  String pattern, int maxRange, int projectileCount, double radius,
                                  String potionEffect, int potionDuration, int potionAmplifier) {
        Material material;
        String displayName;

        switch (projectileType.toUpperCase()) {
            case "ARROW":
                material = Material.ARROW;
                displayName = ChatColor.GOLD + "Артиллерия со стрелами";
                break;
            case "FLAMING_ARROW":
                material = Material.ARROW;
                displayName = ChatColor.RED + "Артиллерия с огненными стрелами";
                break;
            case "TRIDENT":
                material = Material.PRISMARINE_SHARD;
                displayName = ChatColor.AQUA + "Артиллерия с трезубцами";
                break;
            case "SPLASH_POTION":
                material = Material.SPLASH_POTION;
                displayName = ChatColor.LIGHT_PURPLE + "Артиллерия с взрывными зельями";
                break;
            case "LINGERING_POTION":
                material = Material.LINGERING_POTION;
                displayName = ChatColor.DARK_PURPLE + "Артиллерия с оседающими зельями";
                break;
            case "TNT":
                material = Material.GUNPOWDER;
                displayName = ChatColor.RED + "Артиллерия с динамитом";
                break;
            default:
                player.sendMessage(ChatColor.RED + "Неизвестный тип снаряда: " + projectileType);
                return;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);

        if (projectileType.equals("FLAMING_ARROW")) {
            meta.addEnchant(Enchantment.FIRE_ASPECT, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        if (projectileType.equals("SPLASH_POTION") || projectileType.equals("LINGERING_POTION")) {
            PotionMeta potionMeta = (PotionMeta) meta;
            if (potionEffect != null) {
                PotionEffectType effectType = PotionEffectType.getByName(potionEffect);
                if (effectType != null) {
                    potionMeta.setColor(getPotionColor(effectType));
                    potionMeta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
                }
            }
        }

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Режим отладки: " + (isDebug ? "Вкл" : "Выкл"));
        lore.add(ChatColor.GRAY + "Режим огня: " + fireMode);
        lore.add(ChatColor.GRAY + "Тип снаряда: " + projectileType);
        lore.add(ChatColor.GRAY + "Шаблон: " + pattern);
        lore.add(ChatColor.GRAY + "Макс. дальность: " + maxRange);
        lore.add(ChatColor.GRAY + "Количество снарядов: " + projectileCount);
        lore.add(ChatColor.GRAY + "Радиус рассеивания: " + radius);

        if ((projectileType.equals("SPLASH_POTION") || projectileType.equals("LINGERING_POTION"))
                && potionEffect != null) {
            lore.add(ChatColor.GRAY + "Эффект зелья: " + potionEffect);
            lore.add(ChatColor.GRAY + "Длительность: " + potionDuration / 20.0 + " сек");
            lore.add(ChatColor.GRAY + "Уровень: " + (potionAmplifier + 1));
        }

        lore.add("");
        lore.add(ChatColor.YELLOW + "ПКМ чтобы запустить артиллерию");

        meta.setLore(lore);

        PersistentDataContainer dataContainer = meta.getPersistentDataContainer();
        dataContainer.set(IS_ARTILLERY_KEY, PersistentDataType.BYTE, (byte) 1);
        dataContainer.set(DEBUG_KEY, PersistentDataType.BYTE, isDebug ? (byte) 1 : (byte) 0);
        dataContainer.set(FIRE_MODE_KEY, PersistentDataType.STRING, fireMode);
        dataContainer.set(PROJECTILE_TYPE_KEY, PersistentDataType.STRING, projectileType);
        dataContainer.set(PATTERN_KEY, PersistentDataType.STRING, pattern);
        dataContainer.set(MAX_RANGE_KEY, PersistentDataType.INTEGER, maxRange);
        dataContainer.set(PROJECTILE_COUNT_KEY, PersistentDataType.INTEGER, projectileCount);
        dataContainer.set(RADIUS_KEY, PersistentDataType.DOUBLE, radius);

        if (potionEffect != null) {
            dataContainer.set(POTION_EFFECT_KEY, PersistentDataType.STRING, potionEffect);
            dataContainer.set(POTION_DURATION_KEY, PersistentDataType.INTEGER, potionDuration);
            dataContainer.set(POTION_AMPLIFIER_KEY, PersistentDataType.INTEGER, potionAmplifier);
        }

        item.setItemMeta(meta);

        player.getInventory().addItem(item);
        player.sendMessage(ChatColor.GREEN + "Вы получили " + displayName);
    }


    private Color getPotionColor(PotionEffectType type) {
        if (type == PotionEffectType.HARM || type == PotionEffectType.POISON) {
            return Color.fromRGB(124, 39, 8);
        } else if (type == PotionEffectType.HEAL || type == PotionEffectType.REGENERATION) {
            return Color.fromRGB(248, 36, 35);
        } else if (type == PotionEffectType.SPEED || type == PotionEffectType.JUMP) {
            return Color.fromRGB(124, 175, 198);
        } else if (type == PotionEffectType.WEAKNESS || type == PotionEffectType.SLOW) {
            return Color.fromRGB(74, 66, 66);
        } else {
            return Color.fromRGB(101, 153, 210);
        }
    }

    public boolean isArtilleryItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer dataContainer = meta.getPersistentDataContainer();

        return dataContainer.has(IS_ARTILLERY_KEY, PersistentDataType.BYTE) &&
                dataContainer.get(IS_ARTILLERY_KEY, PersistentDataType.BYTE) == (byte) 1;
    }

    public ArtillerySettings getArtillerySettings(ItemStack item) {
        if (!isArtilleryItem(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer dataContainer = meta.getPersistentDataContainer();

        boolean isDebug = dataContainer.get(DEBUG_KEY, PersistentDataType.BYTE) == (byte) 1;
        String fireMode = dataContainer.getOrDefault(FIRE_MODE_KEY, PersistentDataType.STRING, "RAIN");
        String projectileType = dataContainer.get(PROJECTILE_TYPE_KEY, PersistentDataType.STRING);
        String pattern = dataContainer.get(PATTERN_KEY, PersistentDataType.STRING);
        int maxRange = dataContainer.get(MAX_RANGE_KEY, PersistentDataType.INTEGER);
        int projectileCount = dataContainer.get(PROJECTILE_COUNT_KEY, PersistentDataType.INTEGER);
        double radius = dataContainer.get(RADIUS_KEY, PersistentDataType.DOUBLE);

        String potionEffect = null;
        int potionDuration = 200;
        int potionAmplifier = 0;

        if (dataContainer.has(POTION_EFFECT_KEY, PersistentDataType.STRING)) {
            potionEffect = dataContainer.get(POTION_EFFECT_KEY, PersistentDataType.STRING);
            potionDuration = dataContainer.getOrDefault(POTION_DURATION_KEY, PersistentDataType.INTEGER, 200);
            potionAmplifier = dataContainer.getOrDefault(POTION_AMPLIFIER_KEY, PersistentDataType.INTEGER, 0);
        }

        return new ArtillerySettings(isDebug, fireMode, projectileType, pattern,
                maxRange, projectileCount, radius,
                potionEffect, potionDuration, potionAmplifier);
    }


    private String getBasicProjectileType(String detailedType) {
        if (detailedType.equals("ARROW") || detailedType.equals("FLAMING_ARROW")) {
            return "ARROW";
        } else if (detailedType.equals("SPLASH_POTION") || detailedType.equals("LINGERING_POTION")) {
            return "POTION";
        } else {
            return detailedType;
        }
    }

    public void fireArtillery(Player player, Location launchLocation, ArtillerySettings settings) {
        if (!pythonClient.isServerAvailable()) {
            player.sendMessage(ChatColor.RED + "Python-сервер недоступен. Обстрел невозможен.");
            return;
        }

        Entity target = findTarget(player, launchLocation, settings.isDebug(), settings.getMaxRange());
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Цель не найдена в пределах " + settings.getMaxRange() + " блоков");
            return;
        }

        Location targetLocation = target.getLocation();
        double horizontalDistance = Math.sqrt(
                Math.pow(targetLocation.getX() - launchLocation.getX(), 2) +
                        Math.pow(targetLocation.getZ() - launchLocation.getZ(), 2)
        );
        double heightDifference = targetLocation.getY() - launchLocation.getY();
        double actualHeightRatio = Math.abs(heightDifference) / horizontalDistance;
        double maxAllowedHeightDifference = horizontalDistance * heightRatio;

        if (actualHeightRatio > heightRatio) {
            player.sendMessage(ChatColor.RED + "Невозможно запустить артиллерию: слишком большая разница высот!");
            player.sendMessage(ChatColor.RED + "Максимально допустимая разница высот: " +
                    String.format("%.1f", maxAllowedHeightDifference) + " блоков");
            player.sendMessage(ChatColor.RED + "Текущая разница высот: " +
                    String.format("%.1f", Math.abs(heightDifference)) + " блоков");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Цель найдена: " +
                target.getType().name() + " на расстоянии " +
                String.format("%.1f", launchLocation.distance(targetLocation)) + " блоков");

        String basicProjectileType = getBasicProjectileType(settings.getProjectileType());

        visualizeImpactArea(targetLocation, settings.getProjectileType(), settings.getRadius());

        List<TargetPoint> targetPoints = generateTargetPoints(
                launchLocation, targetLocation, basicProjectileType,
                settings.getPattern(), settings.getProjectileCount(), settings.getRadius());

        visualizeTargetPoints(targetPoints);

        try {
            List<Double> velocities = pythonClient.getVelocities(targetPoints, basicProjectileType);

            for (int i = 0; i < targetPoints.size(); i++) {
                targetPoints.get(i).setVelocity(velocities.get(i));
            }

            if (settings.getFireMode().equals("BURST")) {
                fireBurstProjectiles(player, launchLocation, targetPoints, settings);
            } else {
                fireRainProjectiles(player, launchLocation, targetPoints, settings);
            }

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Ошибка при получении скоростей: " + e.getMessage());
            plugin.getLogger().severe("Error getting velocities: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void fireRainProjectiles(Player player, Location launchLocation,
                                     List<TargetPoint> targetPoints,
                                     ArtillerySettings settings) {
        player.sendMessage(ChatColor.GREEN + "Запуск артиллерийского обстрела (режим RAIN)!");

        for (int i = 0; i < targetPoints.size(); i++) {
            final int index = i;

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                TargetPoint point = targetPoints.get(index);
                Entity projectile = launchProjectile(player, launchLocation, point, settings);

                if (projectile != null) {
                    launchLocation.getWorld().spawnParticle(
                            Particle.FLAME,
                            launchLocation,
                            5, 0.1, 0.1, 0.1, 0.01
                    );
                }
            }, i * 5L);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage(ChatColor.GREEN + "Обстрел завершен! Запущено " +
                    targetPoints.size() + " снарядов.");
        }, targetPoints.size() * 5L + 10L);
    }

    private void fireBurstProjectiles(Player player, Location launchLocation,
                                      List<TargetPoint> targetPoints,
                                      ArtillerySettings settings) {
        player.sendMessage(ChatColor.GREEN + "Запуск артиллерийского обстрела (режим BURST)!");

        for (TargetPoint point : targetPoints) {
            Entity projectile = launchProjectile(player, launchLocation, point, settings);
        }

        launchLocation.getWorld().spawnParticle(
                Particle.EXPLOSION_LARGE,
                launchLocation,
                1, 0, 0, 0, 0
        );

        launchLocation.getWorld().spawnParticle(
                Particle.FLAME,
                launchLocation,
                30, 0.5, 0.5, 0.5, 0.1
        );

        player.sendMessage(ChatColor.GREEN + "Залп выпущен! Запущено " +
                targetPoints.size() + " снарядов.");
    }

    private Entity launchProjectile(Player player, Location launchLocation,
                                    TargetPoint point, ArtillerySettings settings) {
        Vector horizontalDirection = new Vector(
                point.getLocation().getX() - launchLocation.getX(),
                0,
                point.getLocation().getZ() - launchLocation.getZ()
        ).normalize();

        Vector direction = horizontalDirection.clone();
        direction.setY(Math.tan(point.getAngleRadians()));
        direction.normalize();

        Entity projectile = null;

        switch (settings.getProjectileType().toUpperCase()) {
            case "ARROW":
                Arrow arrow = player.getWorld().spawnArrow(
                        launchLocation, direction, (float) point.getVelocity(), 0);
                arrow.setPersistent(false);
                projectile = arrow;
                break;

            case "FLAMING_ARROW":
                Arrow flamingArrow = player.getWorld().spawnArrow(
                        launchLocation, direction, (float) point.getVelocity(), 0);
                flamingArrow.setPersistent(false);
                flamingArrow.setFireTicks(Integer.MAX_VALUE);
                projectile = flamingArrow;
                break;

            case "TRIDENT":
                Trident trident = player.getWorld().spawn(launchLocation, Trident.class);
                trident.setVelocity(direction.clone().multiply(point.getVelocity()));
                projectile = trident;
                break;

            case "SPLASH_POTION":
                ItemStack splashPotionItem = new ItemStack(Material.SPLASH_POTION);
                PotionMeta splashPotionMeta = (PotionMeta) splashPotionItem.getItemMeta();

                if (settings.getPotionEffect() != null) {
                    PotionEffectType effectType = PotionEffectType.getByName(settings.getPotionEffect());
                    if (effectType != null) {
                        splashPotionMeta.addCustomEffect(new PotionEffect(
                                effectType, settings.getPotionDuration(), settings.getPotionAmplifier()), true);
                        splashPotionMeta.setColor(getPotionColor(effectType));
                        splashPotionItem.setItemMeta(splashPotionMeta);
                    }
                }

                ThrownPotion splashPotion = player.getWorld().spawn(launchLocation, ThrownPotion.class);
                splashPotion.setItem(splashPotionItem);
                splashPotion.setVelocity(direction.clone().multiply(point.getVelocity()));
                projectile = splashPotion;
                break;

            case "LINGERING_POTION":
                ItemStack lingeringPotionItem = new ItemStack(Material.LINGERING_POTION);
                PotionMeta lingeringPotionMeta = (PotionMeta) lingeringPotionItem.getItemMeta();

                if (settings.getPotionEffect() != null) {
                    PotionEffectType effectType = PotionEffectType.getByName(settings.getPotionEffect());
                    if (effectType != null) {
                        lingeringPotionMeta.addCustomEffect(new PotionEffect(
                                effectType, settings.getPotionDuration(), settings.getPotionAmplifier()), true);
                        lingeringPotionMeta.setColor(getPotionColor(effectType));
                        lingeringPotionItem.setItemMeta(lingeringPotionMeta);
                    }
                }

                ThrownPotion lingeringPotion = player.getWorld().spawn(launchLocation, LingeringPotion.class);
                lingeringPotion.setItem(lingeringPotionItem);
                lingeringPotion.setVelocity(direction.clone().multiply(point.getVelocity()));
                projectile = lingeringPotion;
                break;

            case "TNT":
                TNTPrimed tnt = player.getWorld().spawn(launchLocation, TNTPrimed.class);
                tnt.setVelocity(direction.clone().multiply(point.getVelocity()));
                tnt.setFuseTicks(1000);

                tnt.setMetadata("artillery_tnt", new org.bukkit.metadata.FixedMetadataValue(plugin, true));

                projectile = tnt;
                break;
        }

        if (projectile != null) {
            startProjectileVisualization(projectile, settings.getProjectileType());
            trackProjectile(projectile, settings.getProjectileType());
        }

        return projectile;
    }

    private void startProjectileVisualization(Entity projectile, String projectileType) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!projectile.isValid() || projectile.isDead()) {
                    this.cancel();
                    visualizationTasks.remove(projectile);
                    return;
                }

                if (projectileType.equalsIgnoreCase("TNT") && Math.random() > 0.7) {
                    Location loc = projectile.getLocation();
                    projectile.getWorld().spawnParticle(Particle.SMOKE_NORMAL, loc, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
        visualizationTasks.put(projectile, task);
    }

    private void trackProjectile(Entity projectile, String projectileType) {
        BukkitTask task = new BukkitRunnable() {
            private int groundTicks = 0;
            private boolean isGrounded = false;
            private Vector lastVelocity = projectile.getVelocity().clone();
            private int unchangedVelocityTicks = 0;

            @Override
            public void run() {
                if (!projectile.isValid() || projectile.isDead()) {
                    this.cancel();
                    firedProjectiles.remove(projectile);

                    if (visualizationTasks.containsKey(projectile)) {
                        visualizationTasks.get(projectile).cancel();
                        visualizationTasks.remove(projectile);
                    }

                    return;
                }

                Vector currentVelocity = projectile.getVelocity();

                if (lastVelocity.distance(currentVelocity) < 0.001) {
                    unchangedVelocityTicks++;
                } else {
                    unchangedVelocityTicks = 0;
                }

                boolean velocityNearZero = currentVelocity.lengthSquared() < 0.01;
                boolean velocityUnchanged = unchangedVelocityTicks > 5;

                boolean isInBlock = false;
                if (projectile instanceof Arrow) {
                    Arrow arrow = (Arrow) projectile;
                    isInBlock = arrow.isInBlock();
                }

                if (!isGrounded && (velocityNearZero || velocityUnchanged || isInBlock)) {
                    isGrounded = true;
                    onProjectileGrounded(projectile, projectileType);

                    if (plugin.getConfig().getBoolean("debug-mode", false)) {
                        plugin.getLogger().info("Projectile grounded: " + projectileType +
                                " (velocityNearZero=" + velocityNearZero +
                                ", velocityUnchanged=" + velocityUnchanged +
                                ", isInBlock=" + isInBlock + ")");
                    }
                }

                if (isGrounded && (projectileType.equals("ARROW") ||
                        projectileType.equals("FLAMING_ARROW") ||
                        projectileType.equals("TRIDENT"))) {
                    groundTicks++;

                    if (groundTicks >= 60) {
                        projectile.remove();
                        this.cancel();
                        firedProjectiles.remove(projectile);

                        if (visualizationTasks.containsKey(projectile)) {
                            visualizationTasks.get(projectile).cancel();
                            visualizationTasks.remove(projectile);
                        }

                        if (plugin.getConfig().getBoolean("debug-mode", false)) {
                            plugin.getLogger().info("Removed grounded projectile: " + projectileType);
                        }
                    }
                }

                lastVelocity = currentVelocity.clone();
            }
        }.runTaskTimer(plugin, 5L, 1L);

        firedProjectiles.put(projectile, task);
    }

    private void onProjectileGrounded(Entity projectile, String projectileType) {
        Location location = projectile.getLocation();

        switch (projectileType.toUpperCase()) {
            case "TNT":
                if (projectile instanceof TNTPrimed) {
                    TNTPrimed tnt = (TNTPrimed) projectile;

                    tnt.setFuseTicks(0);

                    location.getWorld().spawnParticle(
                            Particle.EXPLOSION_LARGE,
                            location,
                            5, 0.5, 0.5, 0.5, 0
                    );
                }
                break;

            case "ARROW":
                location.getWorld().spawnParticle(
                        Particle.CRIT,
                        location,
                        10, 0.2, 0.2, 0.2, 0.1
                );
                break;

            case "FLAMING_ARROW":
                location.getWorld().spawnParticle(
                        Particle.FLAME,
                        location,
                        10, 0.2, 0.2, 0.2, 0.05
                );
                location.getWorld().spawnParticle(
                        Particle.LAVA,
                        location,
                        3, 0.1, 0.1, 0.1, 0
                );
                break;

            case "TRIDENT":
                location.getWorld().spawnParticle(
                        Particle.ENCHANTMENT_TABLE,
                        location,
                        20, 0.5, 0.5, 0.5, 1
                );
                break;

        }
    }

    private Entity findTarget(Player player, Location location, boolean isDebug, int maxRange) {
        Entity target = null;
        double minDistance = maxRange;

        for (Entity entity : location.getWorld().getEntities()) {
            if (location.distance(entity.getLocation()) > maxRange) {
                continue;
            }

            if (isDebug) {
                if (entity.getType() == EntityType.IRON_GOLEM) {
                    double distance = location.distance(entity.getLocation());
                    if (distance < minDistance) {
                        minDistance = distance;
                        target = entity;
                    }
                }
            }
            else {
                if (entity instanceof Player && !entity.equals(player)) {
                    double distance = location.distance(entity.getLocation());
                    if (distance < minDistance) {
                        minDistance = distance;
                        target = entity;
                    }
                }
            }
        }

        return target;
    }

    private List<TargetPoint> generateTargetPoints(Location launchLocation, Location targetLocation,
                                                   String projectileType, String pattern,
                                                   int projectileCount, double radius) {
        List<TargetPoint> points = new ArrayList<>();

        Vector direction = targetLocation.toVector().subtract(launchLocation.toVector());
        double horizontalDistance = Math.sqrt(
                Math.pow(direction.getX(), 2) + Math.pow(direction.getZ(), 2));
        double heightDifference = direction.getY();

        double heightRatio = Math.abs(heightDifference) / horizontalDistance;
        if (heightRatio > 0.2) {
            heightDifference = Math.signum(heightDifference) * horizontalDistance * 0.2;
        }

        double angleRadians = calculateLaunchAngle(horizontalDistance, heightDifference, projectileType);

        points.add(new TargetPoint(
                targetLocation.clone(),
                horizontalDistance,
                heightDifference,
                angleRadians
        ));

        if (projectileCount <= 1) {
            return points;
        }

        switch (pattern.toUpperCase()) {
            case "UNIFORM":
                generateUniformCirclePoints(points, launchLocation, targetLocation,
                        radius, projectileCount - 1, projectileType);
                break;
            case "CONCENTRATED":
                generateConcentratedCirclePoints(points, launchLocation, targetLocation,
                        radius, projectileCount - 1, projectileType);
                break;
            case "RANDOM":
            default:
                generateRandomCirclePoints(points, launchLocation, targetLocation,
                        radius, projectileCount - 1, projectileType);
                break;
        }

        return points;
    }

    private double calculateLaunchAngle(double horizontalDistance, double heightDifference, String projectileType) {
        if (projectileType.equalsIgnoreCase("TNT")) {
            return Math.toRadians(45);
        }
        if (heightDifference <= 0) {
            return Math.toRadians(45);
        } else {
            double ratio = heightDifference / horizontalDistance;
            double angleDegrees = Math.toDegrees(Math.atan(ratio)) + 45;

            angleDegrees = Math.max(30, Math.min(70, angleDegrees));

            return Math.toRadians(angleDegrees);
        }
    }

    private void generateUniformCirclePoints(List<TargetPoint> points, Location launchLocation,
                                             Location centerLocation, double radius,
                                             int pointCount, String projectileType) {
        double angleStep = 2 * Math.PI / pointCount;

        for (int i = 0; i < pointCount; i++) {
            double angle = i * angleStep;

            double x = centerLocation.getX() + radius * Math.cos(angle);
            double z = centerLocation.getZ() + radius * Math.sin(angle);

            Location pointLocation = new Location(
                    centerLocation.getWorld(), x, centerLocation.getY(), z);

            calculateAndAddTargetPoint(points, launchLocation, pointLocation, projectileType);
        }
    }

    private void generateConcentratedCirclePoints(List<TargetPoint> points, Location launchLocation,
                                                  Location centerLocation, double radius,
                                                  int pointCount, String projectileType) {
        int rings = Math.min(5, pointCount / 5);
        if (rings < 1) rings = 1;

        int remainingPoints = pointCount;

        for (int ring = 0; ring < rings; ring++) {
            double ringWeight = (rings - ring) / (double)rings;
            int ringPoints = (int)(remainingPoints * ringWeight);
            if (ring == rings - 1) ringPoints = remainingPoints;

            double ringRadius = radius * (0.2 + 0.8 * (ring + 1) / rings);

            double angleStep = 2 * Math.PI / ringPoints;
            for (int i = 0; i < ringPoints; i++) {
                double angle = i * angleStep;

                double x = centerLocation.getX() + ringRadius * Math.cos(angle);
                double z = centerLocation.getZ() + ringRadius * Math.sin(angle);

                Location pointLocation = new Location(
                        centerLocation.getWorld(), x, centerLocation.getY(), z);

                calculateAndAddTargetPoint(points, launchLocation, pointLocation, projectileType);
            }

            remainingPoints -= ringPoints;
            if (remainingPoints <= 0) break;
        }
    }

    private void generateRandomCirclePoints(List<TargetPoint> points, Location launchLocation,
                                            Location centerLocation, double radius,
                                            int pointCount, String projectileType) {
        for (int i = 0; i < pointCount; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = radius * Math.sqrt(random.nextDouble());

            double x = centerLocation.getX() + distance * Math.cos(angle);
            double z = centerLocation.getZ() + distance * Math.sin(angle);

            double y = centerLocation.getY() + (random.nextDouble() - 0.5);

            Location pointLocation = new Location(
                    centerLocation.getWorld(), x, y, z);

            calculateAndAddTargetPoint(points, launchLocation, pointLocation, projectileType);
        }
    }

    private void calculateAndAddTargetPoint(List<TargetPoint> points, Location launchLocation,
                                            Location targetLocation, String projectileType) {
        Vector direction = targetLocation.toVector().subtract(launchLocation.toVector());

        double horizontalDistance = Math.sqrt(
                Math.pow(direction.getX(), 2) + Math.pow(direction.getZ(), 2));
        double heightDifference = direction.getY();

        double heightRatio = Math.abs(heightDifference) / horizontalDistance;
        if (heightRatio > this.heightRatio) {
            heightDifference = Math.signum(heightDifference) * horizontalDistance * this.heightRatio;
        }

        double angle = calculateLaunchAngle(horizontalDistance, heightDifference, projectileType);

        points.add(new TargetPoint(
                targetLocation.clone(),
                horizontalDistance,
                heightDifference,
                angle
        ));
    }

    private void visualizeTargetPoints(List<TargetPoint> points) {
        for (TargetPoint point : points) {
            point.getLocation().getWorld().spawnParticle(
                    Particle.VILLAGER_HAPPY,
                    point.getLocation(),
                    5, 0.2, 0.2, 0.2, 0.01
            );
        }
    }

    private void visualizeImpactArea(Location targetLocation, String projectileType, double radius) {
        Particle particleType;
        Object particleData = null;

        switch (projectileType.toUpperCase()) {
            case "ARROW":
                particleType = Particle.FIREWORKS_SPARK;
                break;
            case "FLAMING_ARROW":
                particleType = Particle.FLAME;
                break;
            case "TRIDENT":
                particleType = Particle.ENCHANTMENT_TABLE;
                break;
            case "SPLASH_POTION":
            case "LINGERING_POTION":
                particleType = Particle.SPELL_WITCH;
                break;
            case "TNT":
                particleType = Particle.REDSTONE;
                particleData = new DustOptions(Color.RED, 1.0F);
                break;
            default:
                particleType = Particle.CLOUD;
                break;
        }

        final Particle finalParticleType = particleType;
        final Object finalParticleData = particleData;

        new BukkitRunnable() {
            private int ticks = 0;
            private final int maxTicks = 200;

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    this.cancel();
                    return;
                }

                for (int i = 0; i < 10; i++) {
                    double angle = Math.random() * 2 * Math.PI;
                    double distance = Math.random() * radius;

                    double x = targetLocation.getX() + distance * Math.cos(angle);
                    double z = targetLocation.getZ() + distance * Math.sin(angle);
                    double y = targetLocation.getY() + Math.random() * 0.5;

                    Location particleLocation = new Location(targetLocation.getWorld(), x, y, z);

                    if (finalParticleData != null && finalParticleType == Particle.REDSTONE) {
                        targetLocation.getWorld().spawnParticle(
                                finalParticleType, particleLocation, 1, 0, 0, 0, 0, finalParticleData);
                    } else {
                        targetLocation.getWorld().spawnParticle(
                                finalParticleType, particleLocation, 1, 0.05, 0.05, 0.05, 0.01);
                    }
                }

                if (projectileType.equalsIgnoreCase("TRIDENT") && ticks % 5 == 0) {
                    for (int i = 0; i < 5; i++) {
                        double angle = Math.random() * 2 * Math.PI;
                        double distance = Math.random() * radius;

                        double x = targetLocation.getX() + distance * Math.cos(angle);
                        double z = targetLocation.getZ() + distance * Math.sin(angle);
                        double y = targetLocation.getY() + Math.random() * 1.0;

                        Location particleLocation = new Location(targetLocation.getWorld(), x, y, z);
                        targetLocation.getWorld().spawnParticle(
                                Particle.CRIT_MAGIC, particleLocation, 2, 0.1, 0.1, 0.1, 0.01);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public static class ArtillerySettings {
        private final boolean isDebug;
        private final String fireMode;
        private final String projectileType;
        private final String pattern;
        private final int maxRange;
        private final int projectileCount;
        private final double radius;
        private final String potionEffect;
        private final int potionDuration;
        private final int potionAmplifier;

        public ArtillerySettings(boolean isDebug, String fireMode, String projectileType, String pattern,
                                 int maxRange, int projectileCount, double radius,
                                 String potionEffect, int potionDuration, int potionAmplifier) {
            this.isDebug = isDebug;
            this.fireMode = fireMode;
            this.projectileType = projectileType;
            this.pattern = pattern;
            this.maxRange = maxRange;
            this.projectileCount = projectileCount;
            this.radius = radius;
            this.potionEffect = potionEffect;
            this.potionDuration = potionDuration;
            this.potionAmplifier = potionAmplifier;
        }

        public boolean isDebug() {
            return isDebug;
        }

        public String getFireMode() {
            return fireMode;
        }

        public String getProjectileType() {
            return projectileType;
        }

        public String getPattern() {
            return pattern;
        }

        public int getMaxRange() {
            return maxRange;
        }

        public int getProjectileCount() {
            return projectileCount;
        }

        public double getRadius() {
            return radius;
        }

        public String getPotionEffect() {
            return potionEffect;
        }

        public int getPotionDuration() {
            return potionDuration;
        }

        public int getPotionAmplifier() {
            return potionAmplifier;
        }
    }
}