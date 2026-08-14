package org.yudev.airtillery.ballistics;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The native solver must be indistinguishable from the Java one.
 *
 * <p>Skipped when no library was built, so the build stays green without a C++
 * toolchain. When a library <em>is</em> present the bar is exact equality: both
 * run the same closed form with the same iteration counts, and the C++ side is
 * compiled with {@code -ffp-contract=off} to forbid the fused multiply-adds
 * that Java's semantics do not allow. A drift here means the two have genuinely
 * diverged, not that floating point is imprecise.
 */
class NativeSolverParityTest {

    @BeforeAll
    static void requireNative() {
        assumeTrue(NativeSolver.isAvailable(),
                () -> "native solver unavailable: " + NativeSolver.getStatus());
    }

    @Test
    @DisplayName("native and Java agree exactly, one point at a time")
    void singleCallParity() {
        int compared = 0;
        for (ProjectileBallistics b : new ProjectileBallistics[]{
                ProjectileBallistics.ARROW, ProjectileBallistics.TNT}) {
            for (double distance : new double[]{5, 25, 100, 300, 700}) {
                for (double height : new double[]{-120, -30, 0, 30, 120}) {
                    for (double deg : new double[]{15, 30, 45, 60, 75}) {
                        double angle = Math.toRadians(deg);
                        BallisticSolution java = b.solveSpeed(distance, height, angle, 40.0);
                        double nativeSpeed = NativeSolver.nativeSolveSpeed(
                                b.getGravity(), b.getDrag(), b.isGravityBeforeMove(),
                                distance, height, angle, 40.0);

                        if (!java.isSuccess()) {
                            assertTrue(nativeSpeed < 0.0,
                                    "native solved what Java refused at "
                                            + distance + "/" + height + "/" + deg);
                            continue;
                        }
                        assertEquals(java.getSpeed(), nativeSpeed, 0.0,
                                "disagreement at " + distance + "/" + height + "/" + deg);
                        compared++;
                    }
                }
            }
        }
        assertTrue(compared > 100, "expected a broad comparison, got " + compared);
    }

    @Test
    @DisplayName("the batch entry point matches the single one")
    void batchParity() {
        ProjectileBallistics b = ProjectileBallistics.TNT;
        int n = 128;
        double[] distances = new double[n];
        double[] heights = new double[n];
        double[] angles = new double[n];
        double[] out = new double[n];

        for (int i = 0; i < n; i++) {
            distances[i] = 20.0 + i * 2.0;
            heights[i] = ((i % 7) - 3) * 8.0;
            angles[i] = Math.toRadians(30.0 + (i % 5) * 8.0);
        }

        NativeSolver.nativeSolveSpeedBatch(b.getGravity(), b.getDrag(),
                b.isGravityBeforeMove(), distances, heights, angles, 40.0, out);

        for (int i = 0; i < n; i++) {
            BallisticSolution java = b.solveSpeed(distances[i], heights[i], angles[i], 40.0);
            if (java.isSuccess()) {
                assertEquals(java.getSpeed(), out[i], 0.0, "batch disagreed at index " + i);
            } else {
                assertTrue(out[i] < 0.0, "batch solved what Java refused at index " + i);
            }
        }
    }
}
