package org.yudev.airtillery.ballistics;

/**
 * Result of an inverse ballistics query.
 *
 * <p>A failed solution carries the reason instead of throwing, because an
 * unreachable target is an ordinary outcome of aiming, not an error.
 */
public final class BallisticSolution {

    private final boolean success;
    private final double speed;
    private final double angleRadians;
    private final double flightTicks;
    private final double apexHeight;
    private final double impactAngleRadians;
    private final double residual;
    private final String reason;

    private BallisticSolution(boolean success, double speed, double angleRadians,
                              double flightTicks, double apexHeight,
                              double impactAngleRadians, double residual, String reason) {
        this.success = success;
        this.speed = speed;
        this.angleRadians = angleRadians;
        this.flightTicks = flightTicks;
        this.apexHeight = apexHeight;
        this.impactAngleRadians = impactAngleRadians;
        this.residual = residual;
        this.reason = reason;
    }

    static BallisticSolution success(double speed, double angleRadians,
                                     double flightTicks, double apexHeight,
                                     double impactAngleRadians, double residual) {
        return new BallisticSolution(true, speed, angleRadians, flightTicks,
                apexHeight, impactAngleRadians, residual, null);
    }

    static BallisticSolution failure(String reason) {
        return new BallisticSolution(false, 0.0, 0.0, 0.0, 0.0, Double.NaN,
                Double.NaN, reason);
    }

    public boolean isSuccess() {
        return success;
    }

    /** Launch speed, blocks per tick. */
    public double getSpeed() {
        return speed;
    }

    /** Elevation angle of the shot, radians. */
    public double getAngleRadians() {
        return angleRadians;
    }

    /** Ticks from launch until the projectile reaches the target, fractional. */
    public double getFlightTicks() {
        return flightTicks;
    }

    /** Ticks rounded to the nearest whole tick, for scheduling and TNT fuses. */
    public int getFlightTicksRounded() {
        return (int) Math.round(flightTicks);
    }

    /** Peak height of the trajectory above the launch point, blocks. */
    public double getApexHeight() {
        return apexHeight;
    }

    /**
     * Angle below horizontal at which the projectile reaches the target,
     * radians. Zero is level, {@code PI/2} is straight down.
     */
    public double getImpactAngleRadians() {
        return impactAngleRadians;
    }

    /** Remaining vertical miss of the solution, blocks. Diagnostic only. */
    public double getResidual() {
        return residual;
    }

    /** Why the target could not be solved for, or {@code null} on success. */
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        if (!success) {
            return "BallisticSolution[failed: " + reason + "]";
        }
        return String.format("BallisticSolution[v=%.4f b/t, angle=%.2f deg, "
                        + "flight=%.1f ticks, apex=%.1f, impact=%.2f deg, residual=%.2e]",
                speed, Math.toDegrees(angleRadians), flightTicks, apexHeight,
                Math.toDegrees(impactAngleRadians), residual);
    }
}
