package org.yudev.airtillery.station;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.yudev.airtillery.ArtilleryManager;
import org.yudev.airtillery.ArtilleryPlugin;

import java.util.Map;

/**
 * Everything the artillery station does in response to a player.
 *
 * <p>Every click inside the window is cancelled and then acted on by hand.
 * Letting vanilla move items and correcting afterwards is how duplication bugs
 * happen: shift-click, hotbar swap, double-click gather and drag all move stacks
 * in ways that are awkward to undo once they have happened.
 */
public final class StationListener implements Listener {

    private final ArtilleryPlugin plugin;
    private final ArtilleryManager artillery;
    private final StationState state;

    public StationListener(ArtilleryPlugin plugin, ArtilleryManager artillery,
                           StationState state) {
        this.plugin = plugin;
        this.artillery = artillery;
        this.state = state;
    }

    private int maxPacks() {
        return plugin.getConfig().getInt("max-packs-per-volley", 64);
    }

    // ------------------------------------------------------------------
    // The block
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack placed = event.getItemInHand();
        if (!isStationItem(placed)) {
            return;
        }
        state.initialise(event.getBlockPlaced());
        event.getPlayer().sendMessage(ChatColor.GREEN
                + "Артиллерийская станция установлена. ПКМ - открыть.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!state.isStation(block)) {
            return;
        }
        // Suppress the shulker box's own inventory; the station window replaces
        // it. Both hands fire an interact event and cancelling only the main one
        // would let the off-hand event open the vanilla container underneath.
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("artillery.use")) {
            player.sendMessage(ChatColor.RED + "Нет доступа к артиллерийской станции.");
            return;
        }
        StationMenu menu = new StationMenu(block, state, maxPacks());
        menu.render();
        player.openInventory(menu.getInventory());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        ShulkerBox tile = state.tileOf(block);
        if (tile == null) {
            return;
        }

        ProjectileKind kind = state.getKind(tile);
        int paid = state.getPaid(tile);
        String coords = state.getCoordsText(tile);
        String angle = state.getAngleText(tile);
        String packs = state.getPacksText(tile);

        // Vanilla would drop a plain shulker box carrying the station tag in its
        // block-entity data, which on replacement would restore a station with a
        // balance nobody paid for. Drop a clean station item and the credit
        // separately instead.
        event.setDropItems(false);

        Location where = block.getLocation().add(0.5, 0.5, 0.5);
        World world = block.getWorld();
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            world.dropItemNaturally(where, createStationItem());
        }
        dropCurrency(world, where, kind.getCurrency(), paid);
        dropPaper(world, where, coords);
        dropPaper(world, where, angle);
        dropPaper(world, where, packs);
    }

    private void dropCurrency(World world, Location where, Material material, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stack = Math.min(remaining, material.getMaxStackSize());
            world.dropItemNaturally(where, new ItemStack(material, stack));
            remaining -= stack;
        }
    }

    private void dropPaper(World world, Location where, String name) {
        if (name == null) {
            return;
        }
        world.dropItemNaturally(where, namedPaper(name));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(state::isStation);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(state::isStation);
    }

    // ------------------------------------------------------------------
    // The window
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof StationMenu)) {
            return;
        }
        int top = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < top) {
                // Dragging would spread a stack across control slots.
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof StationMenu)) {
            return;
        }
        StationMenu menu = (StationMenu) event.getInventory().getHolder();
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        ShulkerBox tile = state.tileOf(menu.getBlock());
        if (tile == null) {
            event.setCancelled(true);
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Станция больше не существует.");
            return;
        }

        boolean inMenu = event.getRawSlot() >= 0
                && event.getRawSlot() < event.getView().getTopInventory().getSize();

        if (!inMenu) {
            // Shift-clicking from the backpack would push items into control
            // slots, so it is routed by hand; everything else is ordinary
            // inventory handling and is left alone.
            if (event.isShiftClick()) {
                event.setCancelled(true);
                handleShiftIn(player, menu, tile, event.getClickedInventory(),
                        event.getCurrentItem(), event.getSlot());
            }
            return;
        }

        event.setCancelled(true);
        StationLayout.Role role = StationLayout.roleOf(event.getRawSlot());

        switch (role) {
            case SELECTOR:
                cycleKind(player, menu, tile);
                break;
            case INPUT_COORDS:
            case INPUT_ANGLE:
            case INPUT_PACKS:
                handlePaperSlot(player, menu, tile, role, event);
                break;
            case PAYMENT:
                handlePayment(player, menu, tile, event);
                break;
            case FIRE:
                handleFire(player, menu, tile);
                break;
            case RESET:
                handleReset(player, menu, tile);
                break;
            case LABEL:
            case FILLER:
            default:
                break;
        }
    }

    /**
     * Changing ammunition changes the currency, so any balance in the old one is
     * handed back rather than silently reinterpreted as the new one.
     */
    private void cycleKind(Player player, StationMenu menu, ShulkerBox tile) {
        ProjectileKind previous = state.getKind(tile);
        int paid = state.getPaid(tile);
        ProjectileKind next = previous.next();

        if (paid > 0 && next.getCurrency() != previous.getCurrency()) {
            refund(player, previous.getCurrency(), paid);
            state.setPaid(tile, 0);
            player.sendMessage(ChatColor.YELLOW + "Тип снаряда изменён, оплата в "
                    + previous.getCurrencyName() + " возвращена.");
        }

        state.setKind(tile, next);
        menu.render();
    }

    private void handlePaperSlot(Player player, StationMenu menu, ShulkerBox tile,
                                 StationLayout.Role role, InventoryClickEvent event) {
        String stored = readSlot(tile, role);

        if (stored != null) {
            // Taking the paper back also clears the value it stood for.
            giveOrDrop(player, namedPaper(stored));
            writeSlot(tile, role, null);
            menu.render();
            return;
        }

        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType() != Material.PAPER) {
            player.sendMessage(ChatColor.RED
                    + "Сюда кладётся лист бумаги, переименованный в наковальне.");
            return;
        }
        String name = displayName(cursor);
        if (name == null) {
            player.sendMessage(ChatColor.RED
                    + "У этого листа нет имени. Переименуйте его в наковальне.");
            return;
        }

        StationInputs.Parsed<?> parsed = parseFor(role, name);
        if (!parsed.isValid()) {
            player.sendMessage(ChatColor.RED + "Неверный формат: " + parsed.getError());
            return;
        }

        // Take exactly one sheet; the rest stays on the cursor.
        ItemStack remaining = cursor.clone();
        remaining.setAmount(remaining.getAmount() - 1);
        event.getView().setCursor(remaining.getAmount() > 0 ? remaining : null);

        writeSlot(tile, role, name);
        menu.render();
    }

    private void handlePayment(Player player, StationMenu menu, ShulkerBox tile,
                               InventoryClickEvent event) {
        ProjectileKind kind = state.getKind(tile);
        ItemStack cursor = event.getCursor();

        if (cursor == null || cursor.getType() == Material.AIR) {
            player.sendMessage(ChatColor.GRAY + "Положите сюда " + kind.getCurrencyName()
                    + ". Внесено: " + state.getPaid(tile));
            return;
        }
        if (cursor.getType() != kind.getCurrency()) {
            // Wrong currency: the stack stays on the cursor untouched.
            player.sendMessage(ChatColor.RED + "Нужен " + kind.getCurrencyName()
                    + ", а не " + cursor.getType().name().toLowerCase() + ".");
            return;
        }
        if (hasCustomMeta(cursor)) {
            player.sendMessage(ChatColor.RED
                    + "Переименованные или зачарованные предметы не принимаются.");
            return;
        }

        credit(player, menu, tile, cursor.getAmount());
        event.getView().setCursor(null);
    }

    /** Shift-click from the backpack, routed to whichever slot can take it. */
    private void handleShiftIn(Player player, StationMenu menu, ShulkerBox tile,
                               org.bukkit.inventory.Inventory source,
                               ItemStack clicked, int sourceSlot) {
        if (source == null || clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        ProjectileKind kind = state.getKind(tile);

        if (clicked.getType() == kind.getCurrency() && !hasCustomMeta(clicked)) {
            credit(player, menu, tile, clicked.getAmount());
            source.setItem(sourceSlot, null);
            return;
        }

        if (clicked.getType() == Material.PAPER) {
            String name = displayName(clicked);
            if (name == null) {
                player.sendMessage(ChatColor.RED + "У этого листа нет имени.");
                return;
            }
            for (StationLayout.Role role : new StationLayout.Role[]{
                    StationLayout.Role.INPUT_COORDS,
                    StationLayout.Role.INPUT_ANGLE,
                    StationLayout.Role.INPUT_PACKS}) {
                if (readSlot(tile, role) != null) {
                    continue;
                }
                if (!parseFor(role, name).isValid()) {
                    continue;
                }
                ItemStack remaining = clicked.clone();
                remaining.setAmount(remaining.getAmount() - 1);
                source.setItem(sourceSlot, remaining.getAmount() > 0 ? remaining : null);
                writeSlot(tile, role, name);
                menu.render();
                return;
            }
            player.sendMessage(ChatColor.RED
                    + "Этот лист не подходит ни к одному свободному слоту.");
        }
    }

    private void credit(Player player, StationMenu menu, ShulkerBox tile, int amount) {
        int paid = state.getPaid(tile) + amount;
        state.setPaid(tile, paid);
        StationMenu.Order order = menu.render();
        if (order.getRequired() > 0 && paid >= order.getRequired()) {
            player.sendMessage(ChatColor.GREEN + "Оплата внесена: " + paid
                    + " из " + order.getRequired() + ".");
        } else if (order.getRequired() > 0) {
            player.sendMessage(ChatColor.GRAY + "Внесено " + paid
                    + " из " + order.getRequired() + ".");
        } else {
            player.sendMessage(ChatColor.GRAY + "Зачислено " + amount
                    + ", всего " + paid + ". Укажите число пакетов.");
        }
    }

    private void handleReset(Player player, StationMenu menu, ShulkerBox tile) {
        ProjectileKind kind = state.getKind(tile);
        int paid = state.getPaid(tile);
        if (paid <= 0) {
            player.sendMessage(ChatColor.GRAY + "Возвращать нечего.");
            return;
        }
        refund(player, kind.getCurrency(), paid);
        state.setPaid(tile, 0);
        menu.render();
        player.sendMessage(ChatColor.GREEN + "Возвращено " + paid + " "
                + kind.getCurrencyName() + ".");
    }

    private void handleFire(Player player, StationMenu menu, ShulkerBox tile) {
        StationMenu.Order order = menu.render();
        if (!order.isReady()) {
            player.sendMessage(ChatColor.RED + "Заказ не заполнен:");
            for (String line : order.missing()) {
                player.sendMessage(ChatColor.RED + " - " + line);
            }
            return;
        }

        StationInputs.Coordinates target = order.getCoordinates();
        World world = menu.getBlock().getWorld();
        Location launch = menu.getBlock().getLocation().add(0.5, 1.0, 0.5);
        Location impact = new Location(world, target.getX(), target.getY(), target.getZ());

        if (impact.getY() < world.getMinHeight() || impact.getY() > world.getMaxHeight()) {
            player.sendMessage(ChatColor.RED + "Высота цели вне мира ("
                    + world.getMinHeight() + ".." + world.getMaxHeight() + ").");
            return;
        }

        // Charge only once the shot is known to be solvable, so a refused order
        // never costs anything.
        ArtilleryManager.FireReport report = artillery.fire(player, launch, impact,
                order.getKind(), order.getKind().totalRounds(order.getPacks()),
                order.getAngleDegrees());

        if (!report.isFired()) {
            player.sendMessage(ChatColor.RED + "Залп не выполнен: " + report.getReason());
            for (String hint : report.getHints()) {
                player.sendMessage(ChatColor.YELLOW + hint);
            }
            return;
        }

        state.setPaid(tile, Math.max(0, state.getPaid(tile) - order.getRequired()));
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Залп: " + report.getLaunched()
                + " снарядов по " + target + ".");
        if (report.getSkipped() > 0) {
            player.sendMessage(ChatColor.YELLOW + "Пропущено недостижимых точек: "
                    + report.getSkipped());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String readSlot(ShulkerBox tile, StationLayout.Role role) {
        switch (role) {
            case INPUT_COORDS:
                return state.getCoordsText(tile);
            case INPUT_ANGLE:
                return state.getAngleText(tile);
            case INPUT_PACKS:
                return state.getPacksText(tile);
            default:
                return null;
        }
    }

    private void writeSlot(ShulkerBox tile, StationLayout.Role role, String value) {
        switch (role) {
            case INPUT_COORDS:
                state.setCoordsText(tile, value);
                break;
            case INPUT_ANGLE:
                state.setAngleText(tile, value);
                break;
            case INPUT_PACKS:
                state.setPacksText(tile, value);
                break;
            default:
                break;
        }
    }

    private StationInputs.Parsed<?> parseFor(StationLayout.Role role, String name) {
        switch (role) {
            case INPUT_COORDS:
                return StationInputs.parseCoordinates(name);
            case INPUT_ANGLE:
                return StationInputs.parseAngle(name);
            case INPUT_PACKS:
                return StationInputs.parsePacks(name, maxPacks());
            default:
                return StationInputs.parseAngle(name);
        }
    }

    private void refund(Player player, Material currency, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stack = Math.min(remaining, currency.getMaxStackSize());
            giveOrDrop(player, new ItemStack(currency, stack));
            remaining -= stack;
        }
    }

    /** Adds to the inventory, dropping at the player's feet when it is full. */
    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    private static String displayName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        return ChatColor.stripColor(meta.getDisplayName()).trim();
    }

    /** Rejects renamed or enchanted currency so named items are not consumed. */
    private static boolean hasCustomMeta(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && (meta.hasDisplayName() || meta.hasEnchants() || meta.hasLore());
    }

    private static ItemStack namedPaper(String name) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            paper.setItemMeta(meta);
        }
        return paper;
    }

    public boolean isStationItem(ItemStack item) {
        if (item == null || item.getType() != Material.SHULKER_BOX || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .has(state.getStationKey(), org.bukkit.persistence.PersistentDataType.BYTE);
    }

    /** The item that places a station. */
    public ItemStack createStationItem() {
        ItemStack item = new ItemStack(Material.SHULKER_BOX);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Артиллерийская станция");
            meta.setLore(java.util.Arrays.asList(
                    ChatColor.GRAY + "Поставьте блок и нажмите ПКМ.",
                    ChatColor.GRAY + "Точка запуска - клетка над блоком.",
                    "",
                    ChatColor.DARK_GRAY + "Координаты, угол падения и число",
                    ChatColor.DARK_GRAY + "пакетов задаются листами бумаги,",
                    ChatColor.DARK_GRAY + "переименованными в наковальне."));
            meta.getPersistentDataContainer().set(state.getStationKey(),
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }
}
