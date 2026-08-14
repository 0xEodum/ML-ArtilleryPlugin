package org.yudev.airtillery.station;

import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * What the station can fire, and what a pack of it costs.
 *
 * <p>Ammunition is bought in packs: one unit of currency buys {@link #packSize}
 * rounds, and the station's "packets" input counts packs rather than rounds, so
 * asking for 10 packs of arrows fires 100 arrows for 10 gold ingots.
 *
 * <p>Potions are deliberately absent. They carry effects, duration and
 * amplifier, which need a configuration surface of their own rather than a
 * single toggle; the ballistics layer already supports them for when that
 * surface exists.
 */
public enum ProjectileKind {

    ARROW("Стрелы", ChatColor.WHITE, Material.ARROW,
            Material.GOLD_INGOT, "золотой слиток", 10, 1, "ARROW"),

    FLAMING_ARROW("Огненные стрелы", ChatColor.GOLD, Material.FIRE_CHARGE,
            Material.GOLD_INGOT, "золотой слиток", 10, 1, "FLAMING_ARROW"),

    TNT("Динамит", ChatColor.RED, Material.TNT,
            Material.DIAMOND, "алмаз", 5, 1, "TNT"),

    TRIDENT("Трезубцы", ChatColor.AQUA, Material.TRIDENT,
            Material.DIAMOND, "алмаз", 1, 1, "TRIDENT");

    private final String displayName;
    private final ChatColor colour;
    private final Material icon;
    private final Material currency;
    private final String currencyName;
    private final int packSize;
    private final int pricePerPack;
    private final String ballisticsType;

    ProjectileKind(String displayName, ChatColor colour, Material icon,
                   Material currency, String currencyName,
                   int packSize, int pricePerPack, String ballisticsType) {
        this.displayName = displayName;
        this.colour = colour;
        this.icon = icon;
        this.currency = currency;
        this.currencyName = currencyName;
        this.packSize = packSize;
        this.pricePerPack = pricePerPack;
        this.ballisticsType = ballisticsType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatColor getColour() {
        return colour;
    }

    /** Coloured name, as shown on the selector item. */
    public String getColouredName() {
        return colour + displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public Material getCurrency() {
        return currency;
    }

    /** Currency name in the accusative, for "нужно N ...". */
    public String getCurrencyName() {
        return currencyName;
    }

    /** Rounds fired per pack. */
    public int getPackSize() {
        return packSize;
    }

    /** Units of {@link #getCurrency()} per pack. */
    public int getPricePerPack() {
        return pricePerPack;
    }

    /** Key understood by {@code BallisticsRegistry}. */
    public String getBallisticsType() {
        return ballisticsType;
    }

    public int totalRounds(int packs) {
        return packs * packSize;
    }

    public int totalPrice(int packs) {
        return packs * pricePerPack;
    }

    /** "10 стрел за 1 золотой слиток" */
    public String describePrice() {
        return packSize + " шт. за " + pricePerPack + " " + currencyName;
    }

    public ProjectileKind next() {
        ProjectileKind[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static ProjectileKind byName(String name) {
        if (name != null) {
            for (ProjectileKind kind : values()) {
                if (kind.name().equals(name)) {
                    return kind;
                }
            }
        }
        return ARROW;
    }
}
