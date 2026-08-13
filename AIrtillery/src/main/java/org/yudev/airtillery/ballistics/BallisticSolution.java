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
    private final double residual;
    private final String reason;

    private BallisticSolution(boolean success, double speed, double angleRadians,
                              double flightTicks, double apexHeight,
                              double residual, String reason) {
        this.success = success;
        this.speed = speed;
        this.angleRadians = angleRadians;
        this.flightTicks = flightTicks;
        this.apexHeight = apexHeight;
        this.residual = residual;
        this.reason = reason;
    }

    static BallisticSolution success(double speed, double angleRadians,
                                     double flightTicks, double apexHeight,
                                     double residual) {
        return new BallisticSolution(true, speed, angleRadians, flightTicks,
                apexHeight, residual, null);
    }

    static BallisticSolution failure(String reason) {
        return new BallisticSolution(false, 0.0, 0.0, 0.0, 0.0, Double.NaN, reason);
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
                        + "flight=%.1f ticks, apex=%.1f, residual=%.2e]",
                speed, Math.toDegrees(angleRadians), flightTicks, apexHeight, residual);
    }
}
