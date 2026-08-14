package org.yudev.airtillery;

import org.bukkit.Location;

public class TargetPoint {
    private final Location location;
    private final double horizontalDistance;
    private final double heightDifference;
    private double angleRadians;
    private double velocity;
    private double flightTicks;
    private double apexHeight;
    private double impactAngleRadians;
    private boolean solved;
    private String failureReason;

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

    /**
     * The solver may pick a different angle than the tactical heuristic asked
     * for when the preferred one cannot reach the target.
     */
    public void setAngleRadians(double angleRadians) {
        this.angleRadians = angleRadians;
    }

    public double getVelocity() {
        return velocity;
    }

    public void setVelocity(double velocity) {
        this.velocity = velocity;
    }

    /** Ticks from launch to impact, used to time TNT fuses. */
    public double getFlightTicks() {
        return flightTicks;
    }

    public void setFlightTicks(double flightTicks) {
        this.flightTicks = flightTicks;
    }

    /** Peak height of the trajectory above the launch point, blocks. */
    public double getApexHeight() {
        return apexHeight;
    }

    public void setApexHeight(double apexHeight) {
        this.apexHeight = apexHeight;
    }

    /** Angle below horizontal at which the round arrives, radians. */
    public double getImpactAngleRadians() {
        return impactAngleRadians;
    }

    public void setImpactAngleRadians(double impactAngleRadians) {
        this.impactAngleRadians = impactAngleRadians;
    }

    /** False when no launch speed reaches this point; the shot is skipped. */
    public boolean isSolved() {
        return solved;
    }

    public void setSolved(boolean solved) {
        this.solved = solved;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
