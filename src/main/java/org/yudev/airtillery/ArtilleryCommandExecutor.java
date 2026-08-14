package org.yudev.airtillery;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.yudev.airtillery.station.StationListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /artillery give [player]} — hands out the station block.
 *
 * <p>Everything else the artillery can be told is set on the block itself, so
 * the command surface is deliberately one verb wide.
 */
public class ArtilleryCommandExecutor implements CommandExecutor, TabCompleter {

    private final ArtilleryPlugin plugin;
    private final StationListener stations;

    public ArtilleryCommandExecutor(ArtilleryPlugin plugin, StationListener stations) {
        this.plugin = plugin;
        this.stations = stations;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.YELLOW + "Использование: /" + label + " give [игрок]");
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Игрок '" + args[1] + "' не найден.");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Из консоли укажите игрока: /"
                    + label + " give <игрок>");
            return true;
        }

        target.getInventory().addItem(stations.createStationItem())
                .forEach((slot, rest) ->
                        target.getWorld().dropItemNaturally(target.getLocation(), rest));

        target.sendMessage(ChatColor.GREEN + "Вы получили артиллерийскую станцию.");
        if (!target.equals(sender)) {
            sender.sendMessage(ChatColor.GREEN + "Станция выдана игроку " + target.getName() + ".");
        }
        plugin.getLogger().info(sender.getName() + " gave an artillery station to "
                + target.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], Collections.singletonList("give"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(args[1], Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName).collect(Collectors.toList()));
        }
        return new ArrayList<>();
    }

    private List<String> filter(String prefix, List<String> options) {
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
