package org.yudev.airtillery;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ArtilleryListener implements Listener {
    private final ArtilleryPlugin plugin;
    private final ArtilleryManager artilleryManager;

    public ArtilleryListener(ArtilleryPlugin plugin, ArtilleryManager artilleryManager) {
        this.plugin = plugin;
        this.artilleryManager = artilleryManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && artilleryManager.isArtilleryItem(item)) {
            event.setCancelled(true);

            ArtilleryManager.ArtillerySettings settings = artilleryManager.getArtillerySettings(item);
            if (settings == null) {
                player.sendMessage(ChatColor.RED + "Ошибка получения настроек артиллерии");
                return;
            }

            Location launchLocation = player.getLocation().clone().add(0, 3, 0);

            if (!plugin.getPythonClient().isServerAvailable()) {
                player.sendMessage(ChatColor.RED + "Python-сервер недоступен. Обстрел невозможен.");
                return;
            }

            player.sendMessage(ChatColor.YELLOW + "Подготовка артиллерийского обстрела...");
            artilleryManager.fireArtillery(player, launchLocation, settings);
        }
    }
}
