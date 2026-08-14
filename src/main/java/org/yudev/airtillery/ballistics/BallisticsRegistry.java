package org.yudev.airtillery.ballistics;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-projectile-type ballistics plus the firing limits, resolved once at
 * startup.
 *
 * <p>The physics constants are hard-coded defaults that match the game, but
 * they stay overridable: if a future game version or a server plugin retunes
 * gravity or drag, the fix is a config edit rather than a new dataset. The
 * class holds no Bukkit types so it can be constructed in tests.
 */
public final class BallisticsRegistry {

    private final Map<String, ProjectileBallistics> byType = new HashMap<>();
    private final double maxSpeed;
    private final double maxFlightTicks;

    public BallisticsRegistry(double maxSpeed) {
        this(maxSpeed, 600.0);
    }

    public BallisticsRegistry(double maxSpeed, double maxFlightTicks) {
        this.maxSpeed = maxSpeed;
        this.maxFlightTicks = maxFlightTicks;
        byType.put("ARROW", ProjectileBallistics.ARROW);
        byType.put("TRIDENT", ProjectileBallistics.TRIDENT);
        byType.put("POTION", ProjectileBallistics.POTION);
        byType.put("TNT", ProjectileBallistics.TNT);
    }

    /** Replace the constants for one projectile type. */
    public void override(String type, double gravity, double drag,
                         boolean gravityBeforeMove) {
        String key = normalise(type);
        byType.put(key, new ProjectileBallistics(key, gravity, drag, gravityBeforeMove));
    }

    /** Ballistics for a projectile type, falling back to arrow physics. */
    public ProjectileBallistics get(String type) {
        ProjectileBallistics found = byType.get(normalise(type));
        return found != null ? found : ProjectileBallistics.forType(type);
    }

    /** Largest launch speed the plugin is willing to use, blocks per tick. */
    public double getMaxSpeed() {
        return maxSpeed;
    }

    /** Longest flight the plugin is willing to fire, in ticks. */
    public double getMaxFlightTicks() {
        return maxFlightTicks;
    }

    /**
     * Aim at a target, preferring {@code angleRadians} but falling back to the
     * minimum-speed angle when the preferred one cannot reach.
     *
     * <p>The preferred angle comes from the caller's tactical heuristic and is
     * used whenever it works. Only when the target sits outside the reachable
     * set for that angle does the solver widen the search — a shot that a
     * mortar-like 45 degrees cannot make may still be possible on a flatter
     * trajectory.
     *
     * <p>Solutions that take longer than {@link #getMaxFlightTicks()} are
     * refused. Near the asymptotic range limit {@code v*cos(a)/drag} the
     * projectile creeps toward the target over thousands of ticks: such shots
     * are useless in practice (chunks unload, entities despawn, TNT fuses run
     * out) and are also the one regime where the arithmetic loses precision,
     * because there the arrival height becomes hypersensitive to launch speed.
     */
    public BallisticSolution aim(String type, double distance, double height,
                                 double angleRadians) {
        return aim(type, distance, height, angleRadians, true);
    }

    /**
     * As {@link #aim(String, double, double, double)}, but {@code allowFallback}
     * can forbid changing the angle.
     *
     * <p>When the player pinned an elevation, quietly firing on a different one
     * would defeat the point of pinning it, so the caller passes {@code false}
     * and gets an honest failure instead.
     */
    public BallisticSolution aim(String type, double distance, double height,
                                 double angleRadians, boolean allowFallback) {
        ProjectileBallistics ballistics = get(type);

        BallisticSolution direct = ballistics.solveSpeed(distance, height,
                angleRadians, maxSpeed);
        if (isUsable(direct)) {
            return direct;
        }

        if (!allowFallback) {
            if (direct.isSuccess()) {
                return BallisticSolution.failure(String.format(
                        "flight would take longer than %.0f ticks", maxFlightTicks));
            }
            return direct;
        }

        BallisticSolution flattest = ballistics.solveMinimumSpeed(distance, height, maxSpeed);
        if (isUsable(flattest)) {
            return flattest;
        }

        if (direct.isSuccess() || flattest.isSuccess()) {
            return BallisticSolution.failure(String.format(
                    "flight would take longer than %.0f ticks", maxFlightTicks));
        }
        // Neither angle reaches at all; the wider search has the better message.
        return flattest;
    }

    private boolean isUsable(BallisticSolution solution) {
        return solution.isSuccess() && solution.getFlightTicks() <= maxFlightTicks;
    }

    /**
     * Turn an {@link AimMode} into a concrete launch elevation for a volley.
     *
     * <p>Resolved once against the centre of the target area rather than per
     * aim point, for two reasons. The IMPACT and FLATTEST modes each cost a
     * nested root find, which is wasteful to repeat for every round; and the
     * points of a volley are metres apart on a target hundreds of metres away,
     * so solving each one separately would only jitter the elevation by a
     * fraction of a degree while breaking the visual coherence of rounds
     * arriving on one trajectory family.
     *
     * @param heuristicAngle elevation to use for {@link AimMode.Kind#AUTO}
     * @return the resolved elevation, or a failed solution explaining why not
     */
    public AngleResolution resolveLaunchAngle(String type, AimMode mode, double distance,
                                              double height, double heuristicAngle) {
        ProjectileBallistics ballistics = get(type);

        switch (mode.getKind()) {
            case LAUNCH:
                return AngleResolution.of(mode.getAngleRadians());

            case FLATTEST: {
                BallisticSolution s = ballistics.solveMinimumSpeed(distance, height, maxSpeed);
                if (!s.isSuccess()) {
                    return AngleResolution.failed(s.getReason());
                }
                return AngleResolution.of(s.getAngleRadians());
            }

            case IMPACT: {
                BallisticSolution s = ballistics.solveImpactAngle(
                        distance, height, mode.getAngleRadians(), maxSpeed);
                if (!s.isSuccess()) {
                    return AngleResolution.failed(s.getReason());
                }
                return AngleResolution.of(s.getAngleRadians());
            }

            case AUTO:
            default:
                return AngleResolution.of(heuristicAngle);
        }
    }

    /**
     * The descent angles reachable for a target, as {@code {shallowest,
     * steepest}} in radians, or {@code null} when it cannot be reached at all.
     * Used to tell a player what they could have asked for.
     */
    public double[] achievableImpactAngles(String type, double distance, double height) {
        ProjectileBallistics ballistics = get(type);
        double[] range = ballistics.feasibleAngleRange(distance, height, maxSpeed);
        if (range == null) {
            return null;
        }
        double[] out = new double[2];
        for (int i = 0; i < 2; i++) {
            BallisticSolution s = ballistics.solveSpeed(distance, height, range[i], maxSpeed);
            if (!s.isSuccess()) {
                return null;
            }
            out[i] = s.getImpactAngleRadians();
        }
        return out;
    }

    /** Outcome of resolving an {@link AimMode} to an elevation. */
    public static final class AngleResolution {
        private final boolean success;
        private final double angleRadians;
        private final String reason;

        private AngleResolution(boolean success, double angleRadians, String reason) {
            this.success = success;
            this.angleRadians = angleRadians;
            this.reason = reason;
        }

        static AngleResolution of(double angleRadians) {
            return new AngleResolution(true, angleRadians, null);
        }

        static AngleResolution failed(String reason) {
            return new AngleResolution(false, 0.0, reason);
        }

        public boolean isSuccess() {
            return success;
        }

        public double getAngleRadians() {
            return angleRadians;
        }

        public String getReason() {
            return reason;
        }
    }

    private static String normalise(String type) {
        if (type == null) {
            return "ARROW";
        }
        switch (type.toUpperCase()) {
            case "FLAMING_ARROW":
                return "ARROW";
            case "SPLASH_POTION":
            case "LINGERING_POTION":
                return "POTION";
            default:
                return type.toUpperCase();
        }
    }
}
