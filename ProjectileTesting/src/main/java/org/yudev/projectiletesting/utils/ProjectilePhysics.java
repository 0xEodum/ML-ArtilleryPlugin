package org.yudev.projectiletesting.utils;

import org.bukkit.Location;
import org.bukkit.util.Vector;


public class ProjectilePhysics {

    /**
     * Advance a simulated projectile by one tick, in the same operation order
     * the game uses.
     *
     * <pre>
     *   arrow / trident / potion:  pos += v;  v *= (1 - drag);  v.y -= gravity
     *   TNT:                       v.y -= gravity;  pos += v;   v *= (1 - drag)
     * </pre>
     *
     * TNT applying gravity before the move is what makes its trajectory differ
     * from the others beyond the constants alone.
     */
    public static void advanceTick(Vector position, Vector velocity, ProjectileType type) {
        double gravity = type.getGravity();
        double retention = 1.0 - type.getDrag();

        if (type.isGravityBeforeMove()) {
            velocity.setY(velocity.getY() - gravity);
            position.add(velocity);
            velocity.multiply(retention);
        } else {
            position.add(velocity);
            velocity.multiply(retention);
            velocity.setY(velocity.getY() - gravity);
        }
    }

    public static double calculateOptimalLaunchAngle(double horizontalDistance, double heightDifference,
                                                     ProjectileType projectileType) {
        double baseAngle = 45;
        if (heightDifference <= 0) {

            return Math.toRadians(baseAngle);
        } else {
            double ratio = heightDifference / horizontalDistance;
            return Math.max(Math.toRadians(30),
                    Math.min(Math.toRadians(70), Math.atan(ratio) + Math.toRadians(baseAngle)));
        }
    }

    public static double estimateInitialVelocity(double horizontalDistance, double heightDifference,
                                                 ProjectileType projectileType) {
        double gravity = projectileType.getGravity();
        double baseVelocity = Math.sqrt(horizontalDistance) * 0.2;

        if (heightDifference > 0) {
            baseVelocity *= (1 + heightDifference / horizontalDistance * 0.7);
        } else if (heightDifference < 0) {
            baseVelocity *= (1 - Math.abs(heightDifference) / horizontalDistance * 0.3);
        }

        if (projectileType == ProjectileType.POTION) {
            baseVelocity *= 0.95;
        } else if (projectileType == ProjectileType.TNT) {
            baseVelocity *= 1;
        } else if (projectileType == ProjectileType.TRIDENT) {
            baseVelocity *= 0.95;
        }

        return baseVelocity;
    }

    public static boolean isOvershot(Vector launchDirection, Location finalLocation, Location targetLocation) {
        if (finalLocation.distance(targetLocation) < 1.2) {
            return false;
        }

        Vector horizontalDirection = new Vector(launchDirection.getX(), 0, launchDirection.getZ()).normalize();

        Vector finalToTarget = new Vector(
                targetLocation.getX() - finalLocation.getX(),
                0,
                targetLocation.getZ() - finalLocation.getZ()
        );

        if (finalToTarget.lengthSquared() < 1.0) {
            return false;
        }

        finalToTarget.normalize();

        double dotProduct = horizontalDirection.dot(finalToTarget);

        return dotProduct < 0;
    }
}