package org.yudev.airtillery.ballistics;

/**
 * Analytic ballistics for Minecraft projectiles.
 *
 * <p>Minecraft integrates projectile motion with a fixed tick step and a linear
 * drag term, so the motion is a first-order linear recurrence with a closed-form
 * solution. There is no need to simulate tick by tick, and no need for a learned
 * model: the exact position after {@code n} ticks is a two-term expression.
 *
 * <h2>Per-tick update</h2>
 * Every projectile performs three operations per tick, but the <em>order</em>
 * differs between entity classes:
 *
 * <pre>
 *   AbstractArrow.tick() / ThrowableProjectile.tick()   (arrow, trident, potion)
 *       pos += v;   v *= (1 - c);   v.y -= g
 *
 *   PrimedTnt.tick()                                    (TNT)
 *       v.y -= g;   pos += v;   v *= (1 - c)
 * </pre>
 *
 * <h2>Unified form</h2>
 * Let {@code u[n]} be the velocity actually used for the displacement on tick
 * {@code n}. Both orderings obey the same recurrence
 *
 * <pre>
 *     u[n+1] = d * u[n] - g * e_y ,      d = 1 - c
 * </pre>
 *
 * and differ only in the seed: {@code u[0] = v0} for arrow-like entities and
 * {@code u[0] = v0 - g * e_y} for TNT (gravity is applied before the move).
 * Solving the recurrence and summing the displacements gives, with
 * {@code S(n) = (1 - d^n) / c} and {@code vInf = -g / c}:
 *
 * <pre>
 *     x(n) = u0x * S(n)
 *     y(n) = vInf * n + (u0y - vInf) * S(n)
 *     z(n) = u0z * S(n)
 * </pre>
 *
 * These are exact for integer {@code n} (verified against a tick-by-tick
 * simulator to ~1e-12 blocks over 400 ticks). Within a tick the entity moves
 * linearly, so sub-tick positions are obtained by linear interpolation with
 * {@code u[n]}.
 *
 * <p>The class carries no Bukkit types on purpose: it is plain arithmetic and
 * can be unit tested outside the server (see {@link BallisticsSelfTest}).
 */
public final class ProjectileBallistics {

    /** Arrows and tridents: gravity 0.05, drag 0.01, terminal speed 5.0 b/t. */
    public static final ProjectileBallistics ARROW =
            new ProjectileBallistics("ARROW", 0.05, 0.01, false);

    /** Tridents share {@code AbstractArrow} physics with arrows. */
    public static final ProjectileBallistics TRIDENT =
            new ProjectileBallistics("TRIDENT", 0.05, 0.01, false);

    /**
     * Splash and lingering potions. {@code ThrownPotion} overrides the generic
     * throwable gravity of 0.03 with 0.05, which makes potion physics identical
     * to arrow physics.
     */
    public static final ProjectileBallistics POTION =
            new ProjectileBallistics("POTION", 0.05, 0.01, false);

    /** Primed TNT: gravity 0.04, drag 0.02, gravity applied before the move. */
    public static final ProjectileBallistics TNT =
            new ProjectileBallistics("TNT", 0.04, 0.02, true);

    private final String name;
    private final double gravity;
    private final double drag;
    private final boolean gravityBeforeMove;
    private final double retention;
    private final double terminalVelocity;
    private final double logRetention;

    public ProjectileBallistics(String name, double gravity, double drag,
                                boolean gravityBeforeMove) {
        if (drag <= 0.0 || drag >= 1.0) {
            throw new IllegalArgumentException("drag must be in (0, 1): " + drag);
        }
        if (gravity <= 0.0) {
            throw new IllegalArgumentException("gravity must be positive: " + gravity);
        }
        this.name = name;
        this.gravity = gravity;
        this.drag = drag;
        this.gravityBeforeMove = gravityBeforeMove;
        this.retention = 1.0 - drag;
        this.terminalVelocity = gravity / drag;
        this.logRetention = Math.log(this.retention);
    }

    /**
     * Ballistics for a projectile type name as used by the plugin.
     * Unknown names fall back to arrow physics.
     */
    public static ProjectileBallistics forType(String type) {
        if (type == null) {
            return ARROW;
        }
        switch (type.toUpperCase()) {
            case "TNT":
                return TNT;
            case "POTION":
            case "SPLASH_POTION":
            case "LINGERING_POTION":
                return POTION;
            case "TRIDENT":
                return TRIDENT;
            case "ARROW":
            case "FLAMING_ARROW":
            default:
                return ARROW;
        }
    }

    public String getName() {
        return name;
    }

    /** Gravity per tick squared, blocks. */
    public double getGravity() {
        return gravity;
    }

    /** Fraction of velocity lost per tick. */
    public double getDrag() {
        return drag;
    }

    /** True when the entity applies gravity before moving (TNT). */
    public boolean isGravityBeforeMove() {
        return gravityBeforeMove;
    }

    /** Steady-state fall speed, {@code gravity / drag}, blocks per tick. */
    public double getTerminalVelocity() {
        return terminalVelocity;
    }

    // ------------------------------------------------------------------
    // Forward problem
    // ------------------------------------------------------------------

    /**
     * Vertical component of {@code u[0]}, the velocity used for the first
     * displacement. TNT applies gravity before moving, so its first step is
     * already one gravity tick slower.
     */
    public double initialStepVelocityY(double launchVelocityY) {
        return gravityBeforeMove ? launchVelocityY - gravity : launchVelocityY;
    }

    /** {@code S(n) = sum of d^k for k in [0, n) = (1 - d^n) / c}. */
    private double geometricSum(double ticks) {
        return (1.0 - Math.pow(retention, ticks)) / drag;
    }

    /** Horizontal displacement after {@code ticks} ticks. */
    public double horizontalDisplacement(double u0Horizontal, double ticks) {
        return u0Horizontal * geometricSum(ticks);
    }

    /** Vertical displacement after {@code ticks} ticks. */
    public double verticalDisplacement(double u0Y, double ticks) {
        double vInf = -terminalVelocity;
        return vInf * ticks + (u0Y - vInf) * geometricSum(ticks);
    }

    /** Horizontal component of the step velocity on tick {@code n}. */
    public double horizontalStepVelocity(double u0Horizontal, double ticks) {
        return u0Horizontal * Math.pow(retention, ticks);
    }

    /** Vertical component of the step velocity on tick {@code n}. */
    public double verticalStepVelocity(double u0Y, double ticks) {
        double vInf = -terminalVelocity;
        return vInf + (u0Y - vInf) * Math.pow(retention, ticks);
    }

    /**
     * The horizontal distance the projectile can never exceed, no matter how
     * long it flies: {@code u0Horizontal / drag}. Useful for a fast
     * out-of-range check before any root finding.
     */
    public double maxHorizontalRange(double u0Horizontal) {
        return u0Horizontal / drag;
    }

    /**
     * Tick (fractional) at which the projectile has covered {@code distance}
     * horizontally, or {@link Double#NaN} if it never gets that far.
     *
     * <p>{@code x(t) = u0h (1 - d^t) / c = distance} solves in closed form.
     */
    public double ticksToHorizontalDistance(double u0Horizontal, double distance) {
        if (u0Horizontal <= 0.0) {
            return Double.NaN;
        }
        double arg = 1.0 - drag * distance / u0Horizontal;
        if (arg <= 0.0) {
            return Double.NaN;
        }
        return Math.log(arg) / logRetention;
    }

    /**
     * Exact arrival time at {@code distance}, in ticks.
     *
     * <p>{@link #ticksToHorizontalDistance} inverts the smooth exponential, but
     * the entity actually moves along a chord inside each tick, so the true
     * crossing happens slightly later. The whole tick is taken from the closed
     * form and the fraction inside it is resolved on the chord, which is what
     * the entity really does. Returns {@link Double#NaN} if the projectile
     * never covers the distance.
     */
    public double exactTicksToDistance(double u0Horizontal, double distance) {
        double t = ticksToHorizontalDistance(u0Horizontal, distance);
        if (Double.isNaN(t)) {
            return Double.NaN;
        }
        // The chord lies below the concave curve, so the true tick index is
        // floor(t) or, right at a tick boundary, one more.
        double n = Math.max(0.0, Math.floor(t));
        for (int guard = 0; guard < 4; guard++) {
            double xn = horizontalDisplacement(u0Horizontal, n);
            double ux = horizontalStepVelocity(u0Horizontal, n);
            if (ux <= 0.0) {
                return Double.NaN;
            }
            double fraction = (distance - xn) / ux;
            if (fraction < 0.0 && n > 0.0) {
                n -= 1.0;
                continue;
            }
            if (fraction >= 1.0) {
                n += 1.0;
                continue;
            }
            return n + fraction;
        }
        return t;
    }

    /**
     * Height, relative to the launch point, at the moment the projectile passes
     * horizontal distance {@code distance}. {@link Double#NaN} when the
     * projectile falls short. This is the function the inverse problem inverts.
     *
     * <p>The tick is located in closed form and the position is then linearly
     * interpolated inside that tick, which is exactly how the entity moves.
     */
    public double heightAtDistance(double speed, double angleRadians, double distance) {
        double u0h = speed * Math.cos(angleRadians);
        double u0y = initialStepVelocityY(speed * Math.sin(angleRadians));

        double t = exactTicksToDistance(u0h, distance);
        if (Double.isNaN(t)) {
            return Double.NaN;
        }
        double n = Math.floor(t);
        return verticalDisplacement(u0y, n)
                + (t - n) * verticalStepVelocity(u0y, n);
    }

    /** Highest point of the trajectory relative to the launch point, in blocks. */
    public double apexHeight(double speed, double angleRadians) {
        double u0y = initialStepVelocityY(speed * Math.sin(angleRadians));
        if (u0y <= 0.0) {
            return 0.0;
        }
        double vInf = -terminalVelocity;
        double t = Math.log(-vInf / (u0y - vInf)) / logRetention;
        return verticalDisplacement(u0y, t);
    }

    // ------------------------------------------------------------------
    // Inverse problems
    // ------------------------------------------------------------------

    /**
     * Find the launch speed that puts the projectile at {@code (distance,
     * height)} relative to the launch point, when fired at {@code angleRadians}.
     *
     * <p>{@link #heightAtDistance} is strictly increasing in speed for a fixed
     * angle — a faster shot arrives at any given horizontal distance sooner and
     * therefore higher — so the root is unique and bisection always converges.
     * The lower bracket is the speed below which the target is beyond the
     * asymptotic range; the upper bracket is grown until the shot overflies.
     *
     * @param distance   horizontal distance to the target, blocks
     * @param height     target height minus launch height, blocks (may be negative)
     * @param angleRadians elevation angle of the shot
     * @param maxSpeed   largest launch speed to consider, blocks per tick
     */
    public BallisticSolution solveSpeed(double distance, double height,
                                        double angleRadians, double maxSpeed) {
        if (!(distance > 0.0)) {
            return BallisticSolution.failure("horizontal distance must be positive");
        }
        double cos = Math.cos(angleRadians);
        if (cos <= 1e-9) {
            return BallisticSolution.failure("launch angle is too steep");
        }

        // Below this speed the asymptotic range v*cos/c is short of the target.
        double lo = drag * distance / cos * (1.0 + 1e-12);
        if (lo >= maxSpeed) {
            return BallisticSolution.failure(
                    "target out of range for maximum speed " + maxSpeed);
        }

        // Grow the upper bracket until the shot clears the target, always
        // capped at maxSpeed and always testing maxSpeed itself before giving
        // up — otherwise a reachable target would be rejected whenever the
        // first guess happens to overshoot the cap.
        double hi = Math.min(Math.max(lo * 2.0, 1.0), maxSpeed);
        boolean bracketed = false;
        while (true) {
            double value = heightAtDistance(hi, angleRadians, distance);
            if (!Double.isNaN(value) && value >= height) {
                bracketed = true;
                break;
            }
            if (hi >= maxSpeed) {
                break;
            }
            hi = Math.min(hi * 1.6, maxSpeed);
        }
        if (!bracketed) {
            return BallisticSolution.failure(
                    "target out of range for maximum speed " + maxSpeed);
        }

        for (int i = 0; i < 200 && hi - lo > 1e-13; i++) {
            double mid = 0.5 * (lo + hi);
            double value = heightAtDistance(mid, angleRadians, distance);
            if (Double.isNaN(value) || value < height) {
                lo = mid;
            } else {
                hi = mid;
            }
        }

        double speed = 0.5 * (lo + hi);
        return describe(speed, angleRadians, distance, height);
    }

    /**
     * Find the elevation angle that hits {@code (distance, height)} at a fixed
     * launch speed. Two solutions normally exist; {@code highArc} selects the
     * lobbed one.
     *
     * <p>The reachable angles satisfy {@code speed * cos(a) / c > distance},
     * which brackets the search exactly. Inside that interval the height at the
     * target distance is unimodal, so a golden-section search locates the
     * maximum and bisection finds the requested root on either side of it.
     */
    public BallisticSolution solveAngle(double distance, double height,
                                        double speed, boolean highArc) {
        if (!(distance > 0.0)) {
            return BallisticSolution.failure("horizontal distance must be positive");
        }
        double cosMin = drag * distance / speed;
        if (cosMin >= 1.0) {
            return BallisticSolution.failure("target out of range at speed " + speed);
        }
        double aMax = Math.acos(cosMin) * (1.0 - 1e-12);
        double loA = -aMax;
        double hiA = aMax;

        double golden = (Math.sqrt(5.0) - 1.0) / 2.0;
        double a = loA;
        double b = hiA;
        for (int i = 0; i < 300 && b - a > 1e-14; i++) {
            double c1 = b - golden * (b - a);
            double c2 = a + golden * (b - a);
            if (residual(speed, c1, distance, height) < residual(speed, c2, distance, height)) {
                a = c1;
            } else {
                b = c2;
            }
        }
        double aPeak = 0.5 * (a + b);
        if (residual(speed, aPeak, distance, height) < 0.0) {
            return BallisticSolution.failure("target unreachable at speed " + speed);
        }

        double lo = highArc ? aPeak : loA;
        double hi = highArc ? hiA : aPeak;
        for (int i = 0; i < 300 && hi - lo > 1e-14; i++) {
            double mid = 0.5 * (lo + hi);
            boolean positive = residual(speed, mid, distance, height) >= 0.0;
            if (positive == highArc) {
                lo = mid;
            } else {
                hi = mid;
            }
        }

        return describe(speed, 0.5 * (lo + hi), distance, height);
    }

    /**
     * Find the shot that reaches {@code (distance, height)} with the smallest
     * possible launch speed, choosing the angle freely.
     *
     * <p>This is the widest-reaching shot available: if this fails, no angle at
     * or below {@code maxSpeed} can make it. Useful both as a fallback when a
     * caller's preferred angle falls short, and as a way to answer "can this
     * target be hit at all".
     *
     * <p>Required speed as a function of angle is unimodal — too flat and drag
     * eats the range, too steep and the shot goes up instead of out — so a
     * golden-section search finds the optimum.
     */
    public BallisticSolution solveMinimumSpeed(double distance, double height,
                                               double maxSpeed) {
        if (!(distance > 0.0)) {
            return BallisticSolution.failure("horizontal distance must be positive");
        }
        double lo = Math.toRadians(-80.0);
        double hi = Math.toRadians(89.0);
        double golden = (Math.sqrt(5.0) - 1.0) / 2.0;
        for (int i = 0; i < 120 && hi - lo > 1e-10; i++) {
            double c1 = hi - golden * (hi - lo);
            double c2 = lo + golden * (hi - lo);
            double s1 = requiredSpeed(distance, height, c1, maxSpeed);
            double s2 = requiredSpeed(distance, height, c2, maxSpeed);
            // Unreachable angles score +infinity and are discarded from
            // whichever end they sit on, so the plateaus do not trap the search.
            if (s1 < s2) {
                hi = c2;
            } else {
                lo = c1;
            }
        }
        double angle = 0.5 * (lo + hi);
        BallisticSolution best = solveSpeed(distance, height, angle, maxSpeed);
        if (best.isSuccess()) {
            return best;
        }
        return BallisticSolution.failure(
                "target unreachable at any angle with maximum speed " + maxSpeed);
    }

    /** Speed needed at a given angle, or +infinity when unreachable. */
    private double requiredSpeed(double distance, double height, double angle,
                                 double maxSpeed) {
        // A little headroom above maxSpeed so the optimum is not clipped by the
        // limit while the search is still narrowing in on it.
        BallisticSolution s = solveSpeed(distance, height, angle, maxSpeed * 4.0);
        return s.isSuccess() ? s.getSpeed() : Double.POSITIVE_INFINITY;
    }

    /** {@code heightAtDistance - height}, with NaN mapped to negative infinity. */
    private double residual(double speed, double angle, double distance, double height) {
        double value = heightAtDistance(speed, angle, distance);
        return Double.isNaN(value) ? Double.NEGATIVE_INFINITY : value - height;
    }

    private BallisticSolution describe(double speed, double angleRadians,
                                       double distance, double height) {
        double u0h = speed * Math.cos(angleRadians);
        double ticks = exactTicksToDistance(u0h, distance);
        double reached = heightAtDistance(speed, angleRadians, distance);
        double error = Double.isNaN(reached) ? Double.NaN : Math.abs(reached - height);
        return BallisticSolution.success(speed, angleRadians,
                Double.isNaN(ticks) ? 0.0 : ticks,
                apexHeight(speed, angleRadians), error);
    }
}
