package org.yudev.airtillery;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ArtilleryCommandExecutor implements CommandExecutor, TabCompleter {
    private final ArtilleryPlugin plugin;
    private final ArtilleryManager artilleryManager;

    private final List<String> PROJECTILE_TYPES = Arrays.asList(
            "ARROW", "FLAMING_ARROW", "TRIDENT",
            "SPLASH_POTION", "LINGERING_POTION", "TNT");
    private final List<String> PATTERNS = Arrays.asList("UNIFORM", "CONCENTRATED", "RANDOM");
    private final List<String> FIRE_MODES = Arrays.asList("RAIN", "BURST");
    private final List<String> BOOLEANS = Arrays.asList("true", "false");
    private final List<String> POTION_EFFECTS = Arrays.stream(PotionEffectType.values())
            .filter(effect -> effect != null)
            .map(effect -> effect.getName().toUpperCase())
            .collect(Collectors.toList());

    public ArtilleryCommandExecutor(ArtilleryPlugin plugin, ArtilleryManager artilleryManager) {
        this.plugin = plugin;
        this.artilleryManager = artilleryManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эта команда может быть использована только игроком");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 7) {
            player.sendMessage(ChatColor.RED + "Использование: /giveartillery <isDebug> <FireMode> <Projectile> <Pattern> <MAX_RANGE> <Projectile_count> <R> [PotionEffect] [PotionDuration] [PotionAmplifier]");
            return false;
        }

        try {
            boolean isDebug = Boolean.parseBoolean(args[0]);
            String fireMode = args[1].toUpperCase();
            String projectileType = args[2].toUpperCase();
            String pattern = args[3].toUpperCase();
            int maxRange = Integer.parseInt(args[4]);
            int projectileCount = Integer.parseInt(args[5]);
            double radius = Double.parseDouble(args[6]);

            if (maxRange <= 0) {
                player.sendMessage(ChatColor.RED + "MAX_RANGE должен быть положительным числом");
                return true;
            }

            if (projectileCount <= 0) {
                player.sendMessage(ChatColor.RED + "Projectile_count должен быть положительным числом");
                return true;
            }

            if (radius < 0) {
                player.sendMessage(ChatColor.RED + "R должен быть неотрицательным числом");
                return true;
            }

            if (!FIRE_MODES.contains(fireMode)) {
                player.sendMessage(ChatColor.RED + "Неизвестный режим огня. Допустимые режимы: "
                        + String.join(", ", FIRE_MODES));
                return true;
            }

            if (!PROJECTILE_TYPES.contains(projectileType)) {
                player.sendMessage(ChatColor.RED + "Неизвестный тип снаряда. Допустимые типы: "
                        + String.join(", ", PROJECTILE_TYPES));
                return true;
            }

            if (!PATTERNS.contains(pattern)) {
                player.sendMessage(ChatColor.RED + "Неизвестный паттерн. Допустимые паттерны: "
                        + String.join(", ", PATTERNS));
                return true;
            }

            String potionEffect = null;
            int potionDuration = 200;
            int potionAmplifier = 0;

            if ((projectileType.equals("SPLASH_POTION") || projectileType.equals("LINGERING_POTION")) && args.length > 7) {
                potionEffect = args[7].toUpperCase();

                if (PotionEffectType.getByName(potionEffect) == null) {
                    player.sendMessage(ChatColor.RED + "Неизвестный эффект зелья: " + potionEffect);
                    return true;
                }

                if (args.length > 8) {
                    potionDuration = Integer.parseInt(args[8]);
                    if (potionDuration <= 0) {
                        player.sendMessage(ChatColor.RED + "Длительность эффекта должна быть положительным числом");
                        return true;
                    }
                }

                if (args.length > 9) {
                    potionAmplifier = Integer.parseInt(args[9]);
                    if (potionAmplifier < 0) {
                        player.sendMessage(ChatColor.RED + "Уровень эффекта должен быть неотрицательным числом");
                        return true;
                    }
                }
            }

            artilleryManager.giveArtilleryItem(player, isDebug, fireMode, projectileType, pattern,
                    maxRange, projectileCount, radius, potionEffect, potionDuration, potionAmplifier);

            return true;
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Некорректные числовые параметры");
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("giveartillery")) {
            if (args.length == 1) {
                return filterStartingWith(args[0], BOOLEANS);
            } else if (args.length == 2) {
                return filterStartingWith(args[1], FIRE_MODES);
            } else if (args.length == 3) {
                return filterStartingWith(args[2], PROJECTILE_TYPES);
            } else if (args.length == 4) {
                return filterStartingWith(args[3], PATTERNS);
            } else if (args.length == 5) {
                return filterStartingWith(args[4], Arrays.asList("50", "100", "150", "200"));
            } else if (args.length == 6) {
                return filterStartingWith(args[5], Arrays.asList("1", "5", "10", "20"));
            } else if (args.length == 7) {
                return filterStartingWith(args[6], Arrays.asList("0", "3", "5", "10"));
            } else if (args.length == 8) {
                String projectileType = args[2].toUpperCase();
                if (projectileType.equals("SPLASH_POTION") || projectileType.equals("LINGERING_POTION")) {
                    return filterStartingWith(args[7], POTION_EFFECTS);
                }
            } else if (args.length == 9) {
                String projectileType = args[2].toUpperCase();
                if (projectileType.equals("SPLASH_POTION") || projectileType.equals("LINGERING_POTION")) {
                    return filterStartingWith(args[8], Arrays.asList("100", "200", "400", "600"));
                }
            } else if (args.length == 10) {
                String projectileType = args[2].toUpperCase();
                if (projectileType.equals("SPLASH_POTION") || projectileType.equals("LINGERING_POTION")) {
                    return filterStartingWith(args[9], Arrays.asList("0", "1", "2", "3"));
                }
            }
        }

        return new ArrayList<>();
    }

    private List<String> filterStartingWith(String prefix, List<String> options) {
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}