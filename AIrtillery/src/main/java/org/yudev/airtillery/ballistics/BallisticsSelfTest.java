package org.yudev.airtillery.ballistics;

/**
 * Standalone verification of {@link ProjectileBallistics}.
 *
 * <p>Contains a tick-by-tick simulator that is a literal transcription of the
 * entity tick order in the game, and checks the closed-form solution and both
 * inverse solvers against it. Nothing here touches Bukkit, so it runs with
 * plain {@code javac}/{@code java}:
 *
 * <pre>
 *   javac -d /tmp/ballistics AIrtillery/src/main/java/org/yudev/airtillery/ballistics/*.java
 *   java  -cp /tmp/ballistics org.yudev.airtillery.ballistics.BallisticsSelfTest
 * </pre>
 */
public final class BallisticsSelfTest {

    private static int failures = 0;

    private BallisticsSelfTest() {
    }

    /** Literal transcription of the per-tick entity update. */
    private static double[][] simulate(ProjectileBallistics b, double speed,
                                       double angle, int ticks) {
        double g = b.getGravity();
        double c = b.getDrag();
        double vx = speed * Math.cos(angle);
        double vy = speed * Math.sin(angle);
        double x = 0.0;
        double y = 0.0;
        double[][] out = new double[ticks + 1][2];
        for (int n = 1; n <= ticks; n++) {
            if (b.isGravityBeforeMove()) {
                // PrimedTnt.tick(): gravity, move, drag
                vy -= g;
                x += vx;
                y += vy;
                vx *= (1.0 - c);
                vy *= (1.0 - c);
            } else {
                // AbstractArrow / ThrowableProjectile.tick(): move, drag, gravity
                x += vx;
                y += vy;
                vx *= (1.0 - c);
                vy *= (1.0 - c);
                vy -= g;
            }
            out[n][0] = x;
            out[n][1] = y;
        }
        return out;
    }

    /** Height when the simulated trajectory first crosses {@code distance}. */
    private static double simulatedHeightAtDistance(ProjectileBallistics b, double speed,
                                                    double angle, double distance) {
        double[][] traj = simulate(b, speed, angle, 6000);
        for (int i = 1; i < traj.length; i++) {
            if (traj[i][0] >= distance) {
                double x0 = traj[i - 1][0];
                double y0 = traj[i - 1][1];
                double x1 = traj[i][0];
                double y1 = traj[i][1];
                if (x1 == x0) {
                    return y1;
                }
                return y0 + (distance - x0) / (x1 - x0) * (y1 - y0);
            }
        }
        return Double.NaN;
    }

    private static void check(String label, double worst, double limit) {
        boolean ok = worst <= limit;
        if (!ok) {
            failures++;
        }
        System.out.printf("   %-46s %.3e  %s%n", label, worst, ok ? "OK" : "FAILED");
    }

    private static final ProjectileBallistics[] ALL = {
            ProjectileBallistics.ARROW,
            ProjectileBallistics.TRIDENT,
            ProjectileBallistics.POTION,
            ProjectileBallistics.TNT,
    };

    private static void testClosedForm() {
        System.out.println("1. Closed form vs tick-by-tick simulator (400 ticks)");
        for (ProjectileBallistics b : ALL) {
            double worst = 0.0;
            for (double speed : new double[]{0.5, 1.0, 2.5, 5.0, 9.0, 20.0}) {
                for (double deg : new double[]{-30, 0, 15, 30, 45, 60, 80}) {
                    double angle = Math.toRadians(deg);
                    double[][] traj = simulate(b, speed, angle, 400);
                    double u0h = speed * Math.cos(angle);
                    double u0y = b.initialStepVelocityY(speed * Math.sin(angle));
                    for (int n = 0; n <= 400; n += 7) {
                        worst = Math.max(worst,
                                Math.abs(traj[n][0] - b.horizontalDisplacement(u0h, n)));
                        worst = Math.max(worst,
                                Math.abs(traj[n][1] - b.verticalDisplacement(u0y, n)));
                    }
                }
            }
            check(b.getName() + " max position error, blocks", worst, 1e-8);
        }
        System.out.println();
    }

    private static void testSpeedSolver() {
        System.out.println("2. solveSpeed at 45 deg, verified against the simulator");
        double[] distances = {5, 10, 25, 50, 100, 200, 350, 500, 800, 1200};
        double[] ratios = {-0.6, -0.2, 0.0, 0.2, 0.6};
        for (ProjectileBallistics b : ALL) {
            double worst = 0.0;
            int solved = 0;
            int outOfRange = 0;
            for (double distance : distances) {
                for (double ratio : ratios) {
                    double height = distance * ratio;
                    double angle = Math.toRadians(45.0);
                    BallisticSolution s = b.solveSpeed(distance, height, angle, 40.0);
                    if (!s.isSuccess()) {
                        outOfRange++;
                        continue;
                    }
                    solved++;
                    double actual = simulatedHeightAtDistance(b, s.getSpeed(), angle, distance);
                    if (!Double.isNaN(actual)) {
                        worst = Math.max(worst, Math.abs(actual - height));
                    }
                }
            }
            // 1e-3 rather than 1e-6: a handful of these targets sit right at
            // the asymptotic range limit v*cos(a)/drag, where arrival height
            // changes by hundreds of blocks per 1e-5 of launch speed, so the
            // last bits of the bisection show up as a fraction of a millimetre.
            // BallisticsRegistry refuses those shots on flight time anyway.
            check(String.format("%s %d targets (%d out of range)",
                    b.getName(), solved, outOfRange), worst, 1e-3);
        }
        System.out.println();
    }

    private static void testAngleSolver() {
        System.out.println("3. solveAngle at fixed speed, both arcs");
        for (ProjectileBallistics b : ALL) {
            double worst = 0.0;
            int solved = 0;
            for (double speed : new double[]{2.0, 4.0, 8.0}) {
                for (double distance : new double[]{20, 60, 150}) {
                    for (boolean high : new boolean[]{false, true}) {
                        BallisticSolution s = b.solveAngle(distance, 0.0, speed, high);
                        if (!s.isSuccess()) {
                            continue;
                        }
                        solved++;
                        double actual = simulatedHeightAtDistance(
                                b, speed, s.getAngleRadians(), distance);
                        if (!Double.isNaN(actual)) {
                            worst = Math.max(worst, Math.abs(actual));
                        }
                    }
                }
            }
            check(b.getName() + " " + solved + " solutions", worst, 1e-6);
        }
        System.out.println();
    }

    private static void testMinimumSpeedSolver() {
        System.out.println("4. solveMinimumSpeed: hits the target and beats fixed 45 deg");
        for (ProjectileBallistics b : ALL) {
            double worst = 0.0;
            double worstSaving = 0.0;
            int solved = 0;
            for (double distance : new double[]{20, 60, 150, 400, 900}) {
                for (double ratio : new double[]{-1.5, -0.5, 0.0, 0.5, 1.5}) {
                    double height = distance * ratio;
                    BallisticSolution s = b.solveMinimumSpeed(distance, height, 40.0);
                    if (!s.isSuccess()) {
                        continue;
                    }
                    solved++;
                    double actual = simulatedHeightAtDistance(
                            b, s.getSpeed(), s.getAngleRadians(), distance);
                    if (!Double.isNaN(actual)) {
                        worst = Math.max(worst, Math.abs(actual - height));
                    }
                    BallisticSolution fixed45 = b.solveSpeed(
                            distance, height, Math.toRadians(45.0), 40.0);
                    if (fixed45.isSuccess()) {
                        // negative would mean the optimiser found a worse angle
                        worstSaving = Math.min(worstSaving,
                                fixed45.getSpeed() - s.getSpeed());
                    }
                }
            }
            check(b.getName() + " " + solved + " targets, height error", worst, 1e-6);
            check(b.getName() + " never worse than 45 deg (min margin)",
                    -worstSaving, 1e-9);
        }
        System.out.println();
    }

    private static void testFlightTime() {
        System.out.println("5. Predicted flight time vs simulator (TNT fuse timing)");
        ProjectileBallistics b = ProjectileBallistics.TNT;
        double worst = 0.0;
        for (double distance : new double[]{30, 80, 150, 250, 400}) {
            BallisticSolution s = b.solveSpeed(distance, 0.0, Math.toRadians(45.0), 40.0);
            if (!s.isSuccess()) {
                continue;
            }
            double[][] traj = simulate(b, s.getSpeed(), Math.toRadians(45.0), 6000);
            for (int i = 1; i < traj.length; i++) {
                if (traj[i][0] >= distance) {
                    double f = (distance - traj[i - 1][0]) / (traj[i][0] - traj[i - 1][0]);
                    worst = Math.max(worst, Math.abs((i - 1 + f) - s.getFlightTicks()));
                    break;
                }
            }
        }
        check("TNT max flight-time error, ticks", worst, 1e-6);
        System.out.println();
    }

    private static void testThroughput() {
        System.out.println("6. Throughput");
        ProjectileBallistics b = ProjectileBallistics.TNT;
        double angle = Math.toRadians(45.0);
        // warm up the JIT before measuring
        for (int i = 0; i < 50_000; i++) {
            b.solveSpeed(50.0 + (i % 400), 0.0, angle, 40.0);
        }
        int n = 200_000;
        long start = System.nanoTime();
        double sink = 0.0;
        for (int i = 0; i < n; i++) {
            sink += b.solveSpeed(50.0 + (i % 400), 0.0, angle, 40.0).getSpeed();
        }
        long elapsed = System.nanoTime() - start;
        System.out.printf("   %d solutions in %.1f ms -> %.2f us per projectile%n",
                n, elapsed / 1e6, elapsed / 1e3 / n);
        System.out.printf("   a 100-projectile volley costs about %.2f ms%n",
                elapsed / 1e6 / n * 100);
        if (sink == 12345.6789) {
            System.out.println();
        }
        System.out.println();
    }

    private static void printTables() {
        System.out.println("7. Launch speed at 45 deg, target level with the launcher");
        System.out.println("      dL     ARROW       TNT   (blocks/tick)");
        for (double distance : new double[]{20, 50, 100, 200, 300, 500, 1000, 2000}) {
            BallisticSolution arrow = ProjectileBallistics.ARROW
                    .solveSpeed(distance, 0.0, Math.toRadians(45.0), 1e6);
            BallisticSolution tnt = ProjectileBallistics.TNT
                    .solveSpeed(distance, 0.0, Math.toRadians(45.0), 1e6);
            System.out.printf("   %5.0f  %8.4f  %8.4f%n",
                    distance, arrow.getSpeed(), tnt.getSpeed());
        }
        System.out.println();
    }

    /** Exercises the exact entry point the plugin calls per aim point. */
    private static void testRegistry() {
        System.out.println("8. BallisticsRegistry.aim (the plugin's entry point)");
        BallisticsRegistry registry = new BallisticsRegistry(12.0, 600.0);

        String[] types = {"ARROW", "FLAMING_ARROW", "TRIDENT", "SPLASH_POTION",
                "LINGERING_POTION", "POTION", "TNT"};
        double worst = 0.0;
        for (String type : types) {
            ProjectileBallistics b = registry.get(type);
            for (double distance : new double[]{15, 75, 220}) {
                for (double height : new double[]{-40, 0, 40}) {
                    BallisticSolution s = registry.aim(type, distance, height,
                            Math.toRadians(45.0));
                    if (!s.isSuccess()) {
                        continue;
                    }
                    double actual = simulatedHeightAtDistance(
                            b, s.getSpeed(), s.getAngleRadians(), distance);
                    if (!Double.isNaN(actual)) {
                        worst = Math.max(worst, Math.abs(actual - height));
                    }
                }
            }
        }
        check("all type aliases, height error", worst, 1e-6);

        // A steep target that 45 degrees cannot reach within the speed cap,
        // but a flatter shot can: the fallback must find it.
        BallisticSolution fixed = ProjectileBallistics.TNT
                .solveSpeed(300.0, 0.0, Math.toRadians(45.0), 8.0);
        BallisticSolution viaRegistry =
                new BallisticsRegistry(8.0, 600.0).aim("TNT", 300.0, 0.0, Math.toRadians(45.0));
        System.out.printf("   %-46s %s%n", "fixed 45 deg at cap 8.0 b/t",
                fixed.isSuccess() ? "reaches" : "out of range");
        System.out.printf("   %-46s %s%n", "registry fallback",
                viaRegistry.isSuccess()
                        ? String.format("reaches at %.1f deg, v=%.4f",
                        Math.toDegrees(viaRegistry.getAngleRadians()), viaRegistry.getSpeed())
                        : "out of range");
        if (!fixed.isSuccess() && !viaRegistry.isSuccess()) {
            failures++;
            System.out.println("   fallback FAILED to recover a reachable target");
        }

        // The physics override path must actually take effect.
        BallisticsRegistry tuned = new BallisticsRegistry(12.0, 600.0);
        tuned.override("TNT", 0.05, 0.01, false);
        boolean applied = tuned.get("TNT").getGravity() == 0.05
                && tuned.get("TNT").getDrag() == 0.01
                && !tuned.get("TNT").isGravityBeforeMove();
        System.out.printf("   %-46s %s%n", "config override applied",
                applied ? "OK" : "FAILED");
        if (!applied) {
            failures++;
        }
        System.out.println();
    }

    public static void main(String[] args) {
        System.out.println("======================================================");
        System.out.println("Minecraft analytic ballistics self-test");
        System.out.println("======================================================");
        System.out.println();
        testClosedForm();
        testSpeedSolver();
        testAngleSolver();
        testMinimumSpeedSolver();
        testFlightTime();
        testThroughput();
        printTables();
        testRegistry();
        if (failures > 0) {
            System.out.println(failures + " check(s) FAILED");
            System.exit(1);
        }
        System.out.println("All checks passed.");
    }
}
