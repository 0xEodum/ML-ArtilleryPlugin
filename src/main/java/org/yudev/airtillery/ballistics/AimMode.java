package org.yudev.airtillery.ballistics;

import java.util.Locale;

/**
 * How a firing solution should choose its trajectory.
 *
 * <p>The solver can hit a reachable target on many different arcs. This type
 * says which one is wanted:
 *
 * <ul>
 *   <li>{@link Kind#AUTO} — the caller's tactical heuristic (the historical
 *       behaviour: 45 degrees, raised toward a target above the launcher).</li>
 *   <li>{@link Kind#LAUNCH} — a fixed elevation angle. A steeper launch drops
 *       the rounds more vertically at the cost of speed and flight time.</li>
 *   <li>{@link Kind#IMPACT} — a fixed <em>descent</em> angle at the target.
 *       The launch angle is whatever produces it, which is usually what is
 *       actually wanted when the goal is to clear a wall or come down inside a
 *       courtyard.</li>
 *   <li>{@link Kind#FLATTEST} — the minimum-speed shot, the one that reaches
 *       furthest for a given speed cap.</li>
 * </ul>
 *
 * <p>Parsed from a short string so it can live in item metadata and in a
 * command argument: {@code auto}, {@code 60}, {@code impact:75}, {@code flat}.
 */
public final class AimMode {

    public enum Kind {
        AUTO,
        LAUNCH,
        IMPACT,
        FLATTEST
    }

    public static final AimMode AUTO = new AimMode(Kind.AUTO, 0.0);
    public static final AimMode FLATTEST = new AimMode(Kind.FLATTEST, 0.0);

    private final Kind kind;
    private final double angleRadians;

    private AimMode(Kind kind, double angleRadians) {
        this.kind = kind;
        this.angleRadians = angleRadians;
    }

    public static AimMode launch(double degrees) {
        return new AimMode(Kind.LAUNCH, Math.toRadians(degrees));
    }

    public static AimMode impact(double degrees) {
        return new AimMode(Kind.IMPACT, Math.toRadians(degrees));
    }

    public Kind getKind() {
        return kind;
    }

    /** The requested angle in radians; meaningless for AUTO and FLATTEST. */
    public double getAngleRadians() {
        return angleRadians;
    }

    public double getAngleDegrees() {
        return Math.toDegrees(angleRadians);
    }

    /** True when the caller pinned a specific trajectory rather than asking for one. */
    public boolean isExplicit() {
        return kind == Kind.LAUNCH || kind == Kind.IMPACT;
    }

    /**
     * Parse a user-supplied aim specification.
     *
     * <pre>
     *   auto            the tactical heuristic (default)
     *   60   60.5       fixed launch elevation, degrees
     *   launch:60       the same, written explicitly
     *   impact:75       come down at 75 degrees below horizontal
     *   flat  min       minimum-speed shot
     * </pre>
     *
     * @throws IllegalArgumentException with a message meant for the player
     */
    public static AimMode parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return AUTO;
        }
        String value = text.trim().toLowerCase(Locale.ROOT);

        if (value.equals("auto")) {
            return AUTO;
        }
        if (value.equals("flat") || value.equals("min")) {
            return FLATTEST;
        }

        String number = value;
        Kind kind = Kind.LAUNCH;
        int colon = value.indexOf(':');
        if (colon >= 0) {
            String prefix = value.substring(0, colon);
            number = value.substring(colon + 1);
            if (prefix.equals("impact")) {
                kind = Kind.IMPACT;
            } else if (!prefix.equals("launch") && !prefix.equals("angle")) {
                throw new IllegalArgumentException(
                        "unknown aim prefix '" + prefix + "', expected launch: or impact:");
            }
        }

        double degrees;
        try {
            degrees = Double.parseDouble(number);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not an angle");
        }

        if (kind == Kind.IMPACT) {
            // A descent angle of 0 would mean arriving perfectly level, which
            // no falling projectile does; 90 would mean straight down, which
            // needs infinite elevation.
            if (!(degrees > 0.0 && degrees < 90.0)) {
                throw new IllegalArgumentException(
                        "impact angle must be between 0 and 90 degrees, got " + degrees);
            }
            return impact(degrees);
        }

        if (!(degrees > -90.0 && degrees < 90.0)) {
            throw new IllegalArgumentException(
                    "launch angle must be between -90 and 90 degrees, got " + degrees);
        }
        return launch(degrees);
    }

    /** Round-trips through {@link #parse(String)}. */
    public String serialise() {
        switch (kind) {
            case LAUNCH:
                return String.format(Locale.ROOT, "launch:%.4f", getAngleDegrees());
            case IMPACT:
                return String.format(Locale.ROOT, "impact:%.4f", getAngleDegrees());
            case FLATTEST:
                return "flat";
            case AUTO:
            default:
                return "auto";
        }
    }

    @Override
    public String toString() {
        switch (kind) {
            case LAUNCH:
                return String.format(Locale.ROOT, "%.1f° запуска", getAngleDegrees());
            case IMPACT:
                return String.format(Locale.ROOT, "%.1f° падения", getAngleDegrees());
            case FLATTEST:
                return "минимальная скорость";
            case AUTO:
            default:
                return "авто";
        }
    }
}
