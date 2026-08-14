package org.yudev.airtillery.ballistics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correctness of the analytic solver, checked against {@link TickSimulator}.
 */
class BallisticsTest {

    private static final double MAX_SPEED = 40.0;

    static Stream<ProjectileBallistics> allTypes() {
        return Stream.of(ProjectileBallistics.ARROW, ProjectileBallistics.TRIDENT,
                ProjectileBallistics.POTION, ProjectileBallistics.TNT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTypes")
    @DisplayName("closed form reproduces the tick simulator")
    void closedFormMatchesSimulator(ProjectileBallistics b) {
        double worst = 0.0;
        for (double speed : new double[]{0.5, 1.0, 2.5, 5.0, 9.0, 20.0}) {
            for (double deg : new double[]{-30, 0, 15, 30, 45, 60, 80}) {
                double angle = Math.toRadians(deg);
                double[][] traj = TickSimulator.trajectory(b, speed, angle, 400);
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
        double drift = worst;
        assertTrue(drift < 1e-8, () -> b.getName() + " drifted by " + drift + " blocks");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTypes")
    @DisplayName("solveSpeed lands on the target from 5 to 1200 blocks")
    void speedSolverHitsTargets(ProjectileBallistics b) {
        double worst = 0.0;
        int solved = 0;
        for (double distance : new double[]{5, 10, 25, 50, 100, 200, 350, 500, 800, 1200}) {
            for (double ratio : new double[]{-0.6, -0.2, 0.0, 0.2, 0.6}) {
                double height = distance * ratio;
                double angle = Math.toRadians(45.0);
                BallisticSolution s = b.solveSpeed(distance, height, angle, MAX_SPEED);
                if (!s.isSuccess()) {
                    continue;
                }
                solved++;
                double actual = TickSimulator.heightAtDistance(b, s.getSpeed(), angle, distance);
                if (!Double.isNaN(actual)) {
                    worst = Math.max(worst, Math.abs(actual - height));
                }
            }
        }
        assertTrue(solved >= 40, "expected most targets to be solvable, got " + solved);
        // A handful of these sit at the asymptotic range limit, where arrival
        // height moves by hundreds of blocks per 1e-5 of launch speed; the last
        // bits of the bisection surface as a fraction of a millimetre.
        double missed = worst;
        assertTrue(missed < 1e-3, () -> b.getName() + " missed by " + missed + " blocks");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTypes")
    @DisplayName("solveAngle hits on both the flat and the lobbed arc")
    void angleSolverHitsBothArcs(ProjectileBallistics b) {
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
                    double actual = TickSimulator.heightAtDistance(
                            b, speed, s.getAngleRadians(), distance);
                    if (!Double.isNaN(actual)) {
                        worst = Math.max(worst, Math.abs(actual));
                    }
                }
            }
        }
        assertTrue(solved >= 10, "expected solutions, got " + solved);
        double missed = worst;
        assertTrue(missed < 1e-6, () -> b.getName() + " missed by " + missed + " blocks");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTypes")
    @DisplayName("solveMinimumSpeed hits, and never needs more speed than 45 degrees")
    void minimumSpeedSolverIsOptimal(ProjectileBallistics b) {
        double worstHeight = 0.0;
        for (double distance : new double[]{20, 60, 150, 400, 900}) {
            for (double ratio : new double[]{-1.5, -0.5, 0.0, 0.5, 1.5}) {
                double height = distance * ratio;
                BallisticSolution best = b.solveMinimumSpeed(distance, height, MAX_SPEED);
                if (!best.isSuccess()) {
                    continue;
                }
                double actual = TickSimulator.heightAtDistance(
                        b, best.getSpeed(), best.getAngleRadians(), distance);
                if (!Double.isNaN(actual)) {
                    worstHeight = Math.max(worstHeight, Math.abs(actual - height));
                }
                BallisticSolution fixed45 = b.solveSpeed(
                        distance, height, Math.toRadians(45.0), MAX_SPEED);
                if (fixed45.isSuccess()) {
                    assertTrue(best.getSpeed() <= fixed45.getSpeed() + 1e-9,
                            () -> "minimum-speed shot was slower than 45 degrees at "
                                    + distance + "/" + height);
                }
            }
        }
        double missed = worstHeight;
        assertTrue(missed < 1e-6, () -> b.getName() + " missed by " + missed);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTypes")
    @DisplayName("predicted flight time matches the simulator (TNT fuse timing)")
    void flightTimeMatchesSimulator(ProjectileBallistics b) {
        double worst = 0.0;
        for (double distance : new double[]{30, 80, 150, 250, 400}) {
            BallisticSolution s = b.solveSpeed(distance, 0.0, Math.toRadians(45.0), MAX_SPEED);
            if (!s.isSuccess()) {
                continue;
            }
            double expected = TickSimulator.ticksToDistance(
                    b, s.getSpeed(), Math.toRadians(45.0), distance);
            if (!Double.isNaN(expected)) {
                worst = Math.max(worst, Math.abs(expected - s.getFlightTicks()));
            }
        }
        double off = worst;
        assertTrue(off < 1e-6, () -> b.getName() + " flight time off by " + off + " ticks");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTypes")
    @DisplayName("a steeper launch always gives a steeper descent")
    void impactAngleIsMonotoneInLaunchAngle(ProjectileBallistics b) {
        // The impact-angle solver bisects on this assumption, so it is checked
        // rather than assumed.
        int compared = 0;
        for (double distance : new double[]{20, 60, 150, 300}) {
            for (double height : new double[]{-60, -10, 0, 10, 60}) {
                double previous = Double.NaN;
                for (double deg = -60; deg <= 85; deg += 1.0) {
                    BallisticSolution s = b.solveSpeed(
                            distance, height, Math.toRadians(deg), MAX_SPEED);
                    if (!s.isSuccess()) {
                        continue;
                    }
                    double impact = s.getImpactAngleRadians();
                    if (!Double.isNaN(previous)) {
                        double drop = previous - impact;
                        String where = String.format("%.0f deg for %.0f/%.0f",
                                deg, distance, height);
                        assertTrue(drop < 1e-12, () -> "descent got shallower at " + where);
                        compared++;
                    }
                    previous = impact;
                }
            }
        }
        assertTrue(compared > 1000, "expected a dense sweep, compared " + compared);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTypes")
    @DisplayName("solveImpactAngle reports the descent the entity really has")
    void impactAngleIsWhatTheEntityDoes(ProjectileBallistics b) {
        double worst = 0.0;
        for (double distance : new double[]{20, 60, 150, 300}) {
            for (double wanted : new double[]{30, 45, 60, 75, 85}) {
                BallisticSolution s = b.solveImpactAngle(
                        distance, 0.0, Math.toRadians(wanted), MAX_SPEED);
                if (!s.isSuccess()) {
                    continue;
                }
                double simulated = TickSimulator.impactAngle(
                        b, s.getSpeed(), s.getAngleRadians(), distance);
                if (!Double.isNaN(simulated)) {
                    worst = Math.max(worst, Math.abs(simulated - s.getImpactAngleRadians()));
                }
            }
        }
        double gap = worst;
        assertTrue(gap < 1e-9,
                () -> b.getName() + " reported a descent " + Math.toDegrees(gap)
                        + " degrees away from the simulated one");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTypes")
    @DisplayName("solveImpactAngle gets as close to the request as any launch angle can")
    void impactAngleSolverIsOptimal(ProjectileBallistics b) {
        // Velocity is constant within a tick, so achievable descents come in
        // steps and an exact request may be unreachable. What must hold is that
        // no other launch angle lands closer.
        for (double distance : new double[]{20, 60, 150}) {
            for (double height : new double[]{-50, 0, 50}) {
                for (double wanted : new double[]{30, 45, 60, 75}) {
                    BallisticSolution s = b.solveImpactAngle(
                            distance, height, Math.toRadians(wanted), MAX_SPEED);
                    if (!s.isSuccess()) {
                        continue;
                    }
                    double missed = Math.abs(
                            Math.toDegrees(s.getImpactAngleRadians()) - wanted);
                    double bySweep = closestDescentBySweep(b, distance, height, wanted);
                    assertTrue(missed <= bySweep + 1e-6,
                            () -> String.format(
                                    "%s at %.0f/%.0f wanted %.0f deg: solver missed by %.4f, "
                                            + "a sweep found %.4f",
                                    b.getName(), distance, height, wanted, missed, bySweep));

                    double landed = TickSimulator.heightAtDistance(
                            b, s.getSpeed(), s.getAngleRadians(), distance);
                    assertEquals(height, landed, 1e-6, "impact-angle shot missed the target");
                }
            }
        }
    }

    private static double closestDescentBySweep(ProjectileBallistics b, double distance,
                                                double height, double wantedDegrees) {
        double best = Double.MAX_VALUE;
        for (double deg = -85.0; deg <= 88.0; deg += 0.05) {
            BallisticSolution s = b.solveSpeed(distance, height, Math.toRadians(deg), MAX_SPEED);
            if (s.isSuccess()) {
                best = Math.min(best,
                        Math.abs(Math.toDegrees(s.getImpactAngleRadians()) - wantedDegrees));
            }
        }
        return best;
    }

    @Test
    @DisplayName("the registry solves every projectile alias")
    void registryHandlesAllAliases() {
        BallisticsRegistry registry = new BallisticsRegistry(12.0, 600.0);
        for (String type : new String[]{"ARROW", "FLAMING_ARROW", "TRIDENT",
                "SPLASH_POTION", "LINGERING_POTION", "POTION", "TNT"}) {
            ProjectileBallistics b = registry.get(type);
            for (double distance : new double[]{15, 75, 220}) {
                for (double height : new double[]{-40, 0, 40}) {
                    BallisticSolution s = registry.aim(type, distance, height,
                            Math.toRadians(45.0));
                    if (!s.isSuccess()) {
                        continue;
                    }
                    double landed = TickSimulator.heightAtDistance(
                            b, s.getSpeed(), s.getAngleRadians(), distance);
                    assertEquals(height, landed, 1e-6, type + " missed at " + distance);
                }
            }
        }
    }

    @Test
    @DisplayName("the registry widens the angle only when allowed to")
    void registryFallbackIsOptIn() {
        BallisticsRegistry registry = new BallisticsRegistry(8.0, 600.0);
        double distance = 300.0;
        double angle = Math.toRadians(45.0);

        assertFalse(ProjectileBallistics.TNT.solveSpeed(distance, 0.0, angle, 8.0).isSuccess(),
                "test premise: 45 degrees should not reach at this cap");

        BallisticSolution widened = registry.aim("TNT", distance, 0.0, angle, true);
        assertTrue(widened.isSuccess(), "fallback should have found a flatter shot");
        assertTrue(Math.toDegrees(widened.getAngleRadians()) < 40.0,
                "fallback should be flatter than 45 degrees");

        BallisticSolution pinned = registry.aim("TNT", distance, 0.0, angle, false);
        assertFalse(pinned.isSuccess(), "a pinned angle must not be silently changed");
    }

    @Test
    @DisplayName("config overrides replace the built-in constants")
    void registryHonoursOverrides() {
        BallisticsRegistry registry = new BallisticsRegistry(12.0, 600.0);
        registry.override("TNT", 0.05, 0.01, false);
        assertEquals(0.05, registry.get("TNT").getGravity());
        assertEquals(0.01, registry.get("TNT").getDrag());
        assertFalse(registry.get("TNT").isGravityBeforeMove());
    }

    @Test
    @DisplayName("the flight-time cap refuses shots that creep to the target")
    void registryRefusesEndlessFlights() {
        BallisticsRegistry brief = new BallisticsRegistry(40.0, 40.0);
        BallisticSolution s = brief.aim("TNT", 1200.0, 0.0, Math.toRadians(45.0));
        assertFalse(s.isSuccess(), "a multi-thousand-tick flight should be refused");
    }

    @Test
    @DisplayName("AimMode parses the documented forms and rejects the rest")
    void aimModeParsing() {
        assertEquals(AimMode.Kind.AUTO, AimMode.parse("auto").getKind());
        assertEquals(AimMode.Kind.AUTO, AimMode.parse("").getKind());
        assertEquals(AimMode.Kind.AUTO, AimMode.parse(null).getKind());
        assertEquals(AimMode.Kind.FLATTEST, AimMode.parse("flat").getKind());
        assertEquals(AimMode.Kind.FLATTEST, AimMode.parse("min").getKind());
        assertEquals(AimMode.Kind.LAUNCH, AimMode.parse("60").getKind());
        assertEquals(60.0, AimMode.parse("60").getAngleDegrees(), 1e-9);
        assertEquals(-15.5, AimMode.parse("-15.5").getAngleDegrees(), 1e-9);
        assertEquals(AimMode.Kind.LAUNCH, AimMode.parse("launch:70").getKind());
        assertEquals(AimMode.Kind.IMPACT, AimMode.parse("impact:75").getKind());
        assertEquals(75.0, AimMode.parse("impact:75").getAngleDegrees(), 1e-9);

        for (String bad : new String[]{"banana", "impact:0", "impact:90", "impact:120",
                "120", "-120", "weird:45", "impact:abc"}) {
            assertThrows(IllegalArgumentException.class, () -> AimMode.parse(bad), bad);
        }
    }

    @Test
    @DisplayName("AimMode survives a save/load round trip")
    void aimModeRoundTrip() {
        for (AimMode mode : new AimMode[]{AimMode.AUTO, AimMode.FLATTEST,
                AimMode.launch(62.5), AimMode.impact(77.25)}) {
            AimMode again = AimMode.parse(mode.serialise());
            assertEquals(mode.getKind(), again.getKind());
            assertEquals(mode.getAngleDegrees(), again.getAngleDegrees(), 1e-3);
        }
    }
}
