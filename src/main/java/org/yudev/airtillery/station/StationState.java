package org.yudev.airtillery.station;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Everything a station remembers, stored on the shulker box's tile entity.
 *
 * <p>The block is the source of truth rather than the open window, so two
 * players looking at the same station see the same balance, and closing the
 * window loses nothing. What is stored is the <em>text</em> written on each
 * input paper, not the paper item: a renamed sheet of paper carries no other
 * state worth preserving, and keeping strings means the stored value and the
 * validation message always agree.
 */
public final class StationState {

    private final NamespacedKey stationKey;
    private final NamespacedKey kindKey;
    private final NamespacedKey coordsKey;
    private final NamespacedKey angleKey;
    private final NamespacedKey packsKey;
    private final NamespacedKey paidKey;

    public StationState(Plugin plugin) {
        this.stationKey = new NamespacedKey(plugin, "artillery_station");
        this.kindKey = new NamespacedKey(plugin, "station_kind");
        this.coordsKey = new NamespacedKey(plugin, "station_coords");
        this.angleKey = new NamespacedKey(plugin, "station_angle");
        this.packsKey = new NamespacedKey(plugin, "station_packs");
        this.paidKey = new NamespacedKey(plugin, "station_paid");
    }

    /** The tag that distinguishes an artillery shulker box from a storage one. */
    public NamespacedKey getStationKey() {
        return stationKey;
    }

    /** The station's tile entity, or null if this block is not one. */
    public ShulkerBox tileOf(Block block) {
        if (block == null) {
            return null;
        }
        BlockState state = block.getState();
        if (!(state instanceof ShulkerBox)) {
            return null;
        }
        ShulkerBox shulker = (ShulkerBox) state;
        if (!shulker.getPersistentDataContainer()
                .has(stationKey, PersistentDataType.BYTE)) {
            return null;
        }
        return shulker;
    }

    public boolean isStation(Block block) {
        return tileOf(block) != null;
    }

    /** Marks a freshly placed shulker box as a station. */
    public void initialise(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof ShulkerBox)) {
            return;
        }
        ShulkerBox shulker = (ShulkerBox) state;
        PersistentDataContainer pdc = shulker.getPersistentDataContainer();
        pdc.set(stationKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(kindKey, PersistentDataType.STRING, ProjectileKind.ARROW.name());
        pdc.set(paidKey, PersistentDataType.INTEGER, 0);
        shulker.update();
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    public ProjectileKind getKind(ShulkerBox tile) {
        return ProjectileKind.byName(tile.getPersistentDataContainer()
                .get(kindKey, PersistentDataType.STRING));
    }

    /** Text on the coordinates paper, or null when the slot is empty. */
    public String getCoordsText(ShulkerBox tile) {
        return tile.getPersistentDataContainer().get(coordsKey, PersistentDataType.STRING);
    }

    public String getAngleText(ShulkerBox tile) {
        return tile.getPersistentDataContainer().get(angleKey, PersistentDataType.STRING);
    }

    public String getPacksText(ShulkerBox tile) {
        return tile.getPersistentDataContainer().get(packsKey, PersistentDataType.STRING);
    }

    /** Currency units credited to the station, in the current kind's currency. */
    public int getPaid(ShulkerBox tile) {
        Integer paid = tile.getPersistentDataContainer()
                .get(paidKey, PersistentDataType.INTEGER);
        return paid == null ? 0 : paid;
    }

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    public void setKind(ShulkerBox tile, ProjectileKind kind) {
        tile.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind.name());
        tile.update();
    }

    public void setCoordsText(ShulkerBox tile, String text) {
        write(tile, coordsKey, text);
    }

    public void setAngleText(ShulkerBox tile, String text) {
        write(tile, angleKey, text);
    }

    public void setPacksText(ShulkerBox tile, String text) {
        write(tile, packsKey, text);
    }

    public void setPaid(ShulkerBox tile, int paid) {
        tile.getPersistentDataContainer().set(paidKey, PersistentDataType.INTEGER,
                Math.max(0, paid));
        tile.update();
    }

    private void write(ShulkerBox tile, NamespacedKey key, String text) {
        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        if (text == null) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.STRING, text);
        }
        tile.update();
    }
}
