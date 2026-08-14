package org.yudev.airtillery.station;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The station window: renders block state into slots, and reports whether the
 * order is complete enough to fire.
 *
 * <p>Implements {@link InventoryHolder} so a click can be traced back to the
 * block it belongs to without keeping a side table of open windows.
 */
public final class StationMenu implements InventoryHolder {

    private static final String TITLE = ChatColor.DARK_GRAY + "Артиллерийская станция";

    private final Block block;
    private final StationState state;
    private final int maxPacks;
    private final Inventory inventory;

    public StationMenu(Block block, StationState state, int maxPacks) {
        this.block = block;
        this.state = state;
        this.maxPacks = maxPacks;
        this.inventory = Bukkit.createInventory(this, StationLayout.SIZE, TITLE);
    }

    public Block getBlock() {
        return block;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Snapshot of the order, recomputed from block state on every render. */
    public static final class Order {
        ProjectileKind kind = ProjectileKind.ARROW;
        StationInputs.Parsed<StationInputs.Coordinates> coords;
        StationInputs.Parsed<Double> angle;
        StationInputs.Parsed<Integer> packs;
        int paid;
        int required;

        public ProjectileKind getKind() {
            return kind;
        }

        public StationInputs.Coordinates getCoordinates() {
            return coords != null && coords.isValid() ? coords.get() : null;
        }

        public double getAngleDegrees() {
            return angle != null && angle.isValid() ? angle.get() : Double.NaN;
        }

        public int getPacks() {
            return packs != null && packs.isValid() ? packs.get() : 0;
        }

        public int getPaid() {
            return paid;
        }

        public int getRequired() {
            return required;
        }

        public boolean isReady() {
            return getCoordinates() != null
                    && !Double.isNaN(getAngleDegrees())
                    && getPacks() > 0
                    && paid >= required
                    && required > 0;
        }

        /** Everything still standing between the order and a launch. */
        public List<String> missing() {
            List<String> missing = new ArrayList<>();
            if (coords == null) {
                missing.add("координаты цели");
            } else if (!coords.isValid()) {
                missing.add("координаты: " + coords.getError());
            }
            if (angle == null) {
                missing.add("угол падения");
            } else if (!angle.isValid()) {
                missing.add("угол: " + angle.getError());
            }
            if (packs == null) {
                missing.add("число пакетов");
            } else if (!packs.isValid()) {
                missing.add("пакеты: " + packs.getError());
            }
            if (required > 0 && paid < required) {
                missing.add("оплата: внесено " + paid + " из " + required);
            }
            return missing;
        }
    }

    /** Reads the block and rebuilds every slot. */
    public Order render() {
        ShulkerBox tile = state.tileOf(block);
        Order order = new Order();
        if (tile == null) {
            return order;
        }

        order.kind = state.getKind(tile);
        String coordsText = state.getCoordsText(tile);
        String angleText = state.getAngleText(tile);
        String packsText = state.getPacksText(tile);
        order.paid = state.getPaid(tile);

        order.coords = coordsText == null ? null : StationInputs.parseCoordinates(coordsText);
        order.angle = angleText == null ? null : StationInputs.parseAngle(angleText);
        order.packs = packsText == null ? null : StationInputs.parsePacks(packsText, maxPacks);
        order.required = order.getPacks() > 0 ? order.kind.totalPrice(order.getPacks()) : 0;

        ItemStack filler = filler();
        for (int slot = 0; slot < StationLayout.SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(StationLayout.SELECTOR, selector(order.kind));

        inventory.setItem(StationLayout.LABEL_COORDS, label(Material.COMPASS,
                ChatColor.AQUA + "Координаты цели",
                "Положите вниз лист бумаги,",
                "переименованный в наковальне",
                "в формате " + ChatColor.WHITE + "X Y Z",
                "",
                ChatColor.DARK_GRAY + "например: 120 64 -350"));

        inventory.setItem(StationLayout.LABEL_ANGLE, label(Material.SPECTRAL_ARROW,
                ChatColor.AQUA + "Угол падения",
                "Положите вниз лист бумаги",
                "с одним числом от 0 до 90 -",
                "под каким углом снаряды",
                "будут падать на цель",
                "",
                ChatColor.DARK_GRAY + "например: 75"));

        inventory.setItem(StationLayout.LABEL_PACKS, label(Material.BUNDLE,
                ChatColor.AQUA + "Число пакетов",
                "Положите вниз лист бумаги",
                "с целым числом - сколько",
                "пакетов выпустить",
                "",
                ChatColor.GRAY + "1 пакет = " + ChatColor.WHITE
                        + order.kind.getPackSize() + " шт. " + order.kind.getDisplayName(),
                ChatColor.DARK_GRAY + "не больше " + Math.min(maxPacks,
                        StationInputs.ABSOLUTE_MAX_PACKS)));

        inventory.setItem(StationLayout.LABEL_PAYMENT, label(order.kind.getCurrency(),
                ChatColor.AQUA + "Оплата",
                "Положите вниз " + ChatColor.WHITE + order.kind.getCurrencyName(),
                ChatColor.GRAY + "Предметы засчитываются и исчезают,",
                ChatColor.GRAY + "так что можно платить больше стака",
                "",
                order.required > 0
                        ? ChatColor.GRAY + "Нужно: " + ChatColor.WHITE + order.required
                        : ChatColor.GRAY + "Сначала укажите число пакетов",
                ChatColor.GRAY + "Внесено: " + ChatColor.WHITE + order.paid));

        inventory.setItem(StationLayout.INPUT_COORDS, paperFor(coordsText, order.coords));
        inventory.setItem(StationLayout.INPUT_ANGLE, paperFor(angleText, order.angle));
        inventory.setItem(StationLayout.INPUT_PACKS, paperFor(packsText, order.packs));
        inventory.setItem(StationLayout.INPUT_PAYMENT, null);

        inventory.setItem(StationLayout.BUTTON_FIRE, fireButton(order));
        inventory.setItem(StationLayout.BUTTON_RESET, resetButton(order));

        return order;
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    private static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            // A blank name rather than none, so the slot shows no tooltip text
            // at all instead of "Gray Stained Glass Pane".
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private static ItemStack label(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(line.startsWith(String.valueOf(ChatColor.COLOR_CHAR))
                        ? line : ChatColor.GRAY + line);
            }
            lines.add("");
            lines.add(ChatColor.DARK_GRAY + "Подсказка, забрать нельзя");
            meta.setLore(lines);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack selector(ProjectileKind kind) {
        ItemStack item = new ItemStack(kind.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(kind.getColouredName());
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Цена: " + ChatColor.WHITE + kind.describePrice(),
                    "",
                    ChatColor.YELLOW + "Клик - следующий тип",
                    ChatColor.DARK_GRAY + "Забрать нельзя"));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Rebuilds the paper a player put in, tinted by whether it parsed. */
    private static ItemStack paperFor(String text, StationInputs.Parsed<?> parsed) {
        if (text == null) {
            return null;
        }
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(text);
            List<String> lore = new ArrayList<>();
            if (parsed != null && parsed.isValid()) {
                lore.add(ChatColor.GREEN + "Принято: " + ChatColor.WHITE + parsed.get());
            } else if (parsed != null) {
                lore.add(ChatColor.RED + "Не принято: " + parsed.getError());
            }
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Клик - забрать обратно");
            meta.setLore(lore);
            paper.setItemMeta(meta);
        }
        return paper;
    }

    private static ItemStack fireButton(Order order) {
        boolean ready = order.isReady();
        ItemStack item = new ItemStack(ready ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ready
                    ? ChatColor.GREEN + "" + ChatColor.BOLD + "ОГОНЬ"
                    : ChatColor.RED + "Не готово");
            List<String> lore = new ArrayList<>();
            if (ready) {
                int rounds = order.kind.totalRounds(order.getPacks());
                lore.add(ChatColor.GRAY + "Цель: " + ChatColor.WHITE + order.getCoordinates());
                lore.add(ChatColor.GRAY + "Угол падения: " + ChatColor.WHITE
                        + String.format("%.1f°", order.getAngleDegrees()));
                lore.add(ChatColor.GRAY + "Снарядов: " + ChatColor.WHITE + rounds
                        + ChatColor.GRAY + " (" + order.getPacks() + " пакетов)");
                lore.add(ChatColor.GRAY + "Спишется: " + ChatColor.WHITE + order.required
                        + " " + order.kind.getCurrencyName());
                lore.add("");
                lore.add(ChatColor.YELLOW + "Клик - запуск");
            } else {
                lore.add(ChatColor.GRAY + "Осталось указать:");
                for (String line : order.missing()) {
                    lore.add(ChatColor.RED + " - " + line);
                }
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack resetButton(Order order) {
        ItemStack item = new ItemStack(Material.IRON_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Сброс");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Возвращает внесённую оплату",
                    ChatColor.GRAY + "К возврату: " + ChatColor.WHITE + order.paid
                            + " " + order.kind.getCurrencyName(),
                    "",
                    ChatColor.YELLOW + "Клик - вернуть"));
            item.setItemMeta(meta);
        }
        return item;
    }
}
