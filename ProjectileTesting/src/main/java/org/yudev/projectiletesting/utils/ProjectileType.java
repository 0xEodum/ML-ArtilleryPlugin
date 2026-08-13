package org.yudev.projectiletesting.utils;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public enum ProjectileType {
    ARROW(
            "Стрела",
            Material.ARROW,
            EntityType.ARROW,
            0.05,
            0.01,
            5.00,
            Material.ARROW,
            false
    ),
    POTION(
            "Зелье",
            Material.SPLASH_POTION,
            EntityType.SPLASH_POTION,
            0.05,
            0.01,
            5.00,
            Material.SPLASH_POTION,
            false
    ),
    TRIDENT(
            "Трезубец",
            Material.TRIDENT,
            EntityType.TRIDENT,
            0.05,
            0.01,
            5.00,
            Material.TRIDENT,
            false
    ),
    TNT(
            "Динамит",
            Material.TNT,
            EntityType.PRIMED_TNT,
            0.04,
            0.02,
            2.00,
            Material.TNT,
            true
    );

    private final String displayName;
    private final Material itemMaterial;
    private final EntityType entityType;
    private final double gravity;
    private final double drag;
    private final double terminalVelocity;
    private final Material iconMaterial;
    private final boolean gravityBeforeMove;

    ProjectileType(String displayName, Material itemMaterial, EntityType entityType,
                   double gravity, double drag, double terminalVelocity,
                   Material iconMaterial, boolean gravityBeforeMove) {
        this.displayName = displayName;
        this.itemMaterial = itemMaterial;
        this.entityType = entityType;
        this.gravity = gravity;
        this.drag = drag;
        this.terminalVelocity = terminalVelocity;
        this.iconMaterial = iconMaterial;
        this.gravityBeforeMove = gravityBeforeMove;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getItemMaterial() {
        return itemMaterial;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public double getGravity() {
        return gravity;
    }

    public double getDrag() {
        return drag;
    }

    public double getTerminalVelocity() {
        return terminalVelocity;
    }

    public Material getIconMaterial() {
        return iconMaterial;
    }

    /**
     * True when the entity applies gravity before moving, as PrimedTnt does.
     * Arrows, tridents and potions move first and only then apply drag and
     * gravity; the difference changes the trajectory noticeably.
     */
    public boolean isGravityBeforeMove() {
        return gravityBeforeMove;
    }
}