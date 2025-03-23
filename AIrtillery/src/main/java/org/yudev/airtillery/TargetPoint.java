package org.yudev.airtillery;

import org.bukkit.Location;

public class TargetPoint {
    private final Location location;
    private final double horizontalDistance;
    private final double heightDifference;
    private final double angleRadians;
    private double velocity;

    public TargetPoint(Location location, double horizontalDistance,
                       double heightDifference, double angleRadians) {
        this.location = location;
        this.horizontalDistance = horizontalDistance;
        this.heightDifference = heightDifference;
        this.angleRadians = angleRadians;
    }

    public Location getLocation() {
        return location;
    }

    public double getHorizontalDistance() {
        return horizontalDistance;
    }

    public double getHeightDifference() {
        return heightDifference;
    }

    public double getAngleRadians() {
        return angleRadians;
    }

    public double getVelocity() {
        return velocity;
    }

    public void setVelocity(double velocity) {
        this.velocity = velocity;
    }
}