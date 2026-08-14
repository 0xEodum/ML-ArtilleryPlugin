package org.yudev.airtillery.ballistics;

import java.util.Locale;

/**
 * Java versus JNI, measured rather than assumed.
 *
 * <pre>
 *   mvn -q test-compile
 *   java -cp target/classes:target/test-classes \
 *        org.yudev.airtillery.ballistics.SolverBenchmark
 * </pre>
 *
 * <p>Not a JUnit test: a benchmark that fails a build on a noisy machine is
 * worse than no benchmark. It reports the median of several timed reps after a
 * warm-up long enough for HotSpot to have compiled the Java path.
 */
public final class SolverBenchmark {

    private static final int WARMUP_REPS = 5;
    private static final int TIMED_REPS = 9;
    private static final int VOLLEY = 100;
    private static final int VOLLEYS = 2000;
    private static final double MAX_SPEED = 40.0;

    private static final ProjectileBallistics TYPE = ProjectileBallistics.TNT;

    private SolverBenchmark() {
    }

    /** A realistic volley: points scattered around one aim point. */
    private static double[][] volley(int size) {
        double[] distances = new double[size];
        double[] heights = new double[size];
        double[] angles = new double[size];
        for (int i = 0; i < size; i++) {
            distances[i] = 150.0 + (i % 17) - 8.0;
            heights[i] = ((i % 5) - 2) * 1.5;
            angles[i] = Math.toRadians(45.0);
        }
        return new double[][]{distances, heights, angles};
    }

    private interface Run {
        double once(double[] distances, double[] heights, double[] angles, double[] out);
    }

    private static double javaLoop(double[] d, double[] h, double[] a, double[] out) {
        double sink = 0.0;
        for (int i = 0; i < d.length; i++) {
            BallisticSolution s = TYPE.solveSpeed(d[i], h[i], a[i], MAX_SPEED);
            out[i] = s.isSuccess() ? s.getSpeed() : -1.0;
            sink += out[i];
        }
        return sink;
    }

    private static double nativePerCall(double[] d, double[] h, double[] a, double[] out) {
        double sink = 0.0;
        for (int i = 0; i < d.length; i++) {
            out[i] = NativeSolver.nativeSolveSpeed(TYPE.getGravity(), TYPE.getDrag(),
                    TYPE.isGravityBeforeMove(), d[i], h[i], a[i], MAX_SPEED);
            sink += out[i];
        }
        return sink;
    }

    private static double nativeBatch(double[] d, double[] h, double[] a, double[] out) {
        NativeSolver.nativeSolveSpeedBatch(TYPE.getGravity(), TYPE.getDrag(),
                TYPE.isGravityBeforeMove(), d, h, a, MAX_SPEED, out);
        double sink = 0.0;
        for (double v : out) {
            sink += v;
        }
        return sink;
    }

    /** Median nanoseconds per volley. */
    private static double measure(Run run, double[][] input) {
        double[] out = new double[input[0].length];
        double sink = 0.0;

        for (int rep = 0; rep < WARMUP_REPS; rep++) {
            for (int i = 0; i < VOLLEYS; i++) {
                sink += run.once(input[0], input[1], input[2], out);
            }
        }

        double[] samples = new double[TIMED_REPS];
        for (int rep = 0; rep < TIMED_REPS; rep++) {
            long start = System.nanoTime();
            for (int i = 0; i < VOLLEYS; i++) {
                sink += run.once(input[0], input[1], input[2], out);
            }
            samples[rep] = (System.nanoTime() - start) / (double) VOLLEYS;
        }
        java.util.Arrays.sort(samples);

        if (sink == 1.2345e300) {
            System.out.print("");  // keep the optimiser honest
        }
        return samples[TIMED_REPS / 2];
    }

    private static void row(String label, double nanosPerVolley, double baseline) {
        double perPoint = nanosPerVolley / VOLLEY;
        String relative = baseline <= 0.0
                ? ""
                : String.format(Locale.ROOT, "  %5.2fx", baseline / nanosPerVolley);
        System.out.printf(Locale.ROOT, "  %-34s %9.1f us %9.3f us%s%n",
                label, nanosPerVolley / 1000.0, perPoint / 1000.0, relative);
    }

    public static void main(String[] args) {
        System.out.println("Solver benchmark");
        System.out.printf(Locale.ROOT, "  JVM         %s %s%n",
                System.getProperty("java.vm.name"), System.getProperty("java.version"));
        System.out.printf(Locale.ROOT, "  OS          %s %s%n",
                System.getProperty("os.name"), System.getProperty("os.arch"));
        System.out.printf(Locale.ROOT, "  volley      %d aim points, %d volleys per rep, "
                + "median of %d reps%n%n", VOLLEY, VOLLEYS, TIMED_REPS);

        double[][] input = volley(VOLLEY);

        System.out.printf(Locale.ROOT, "  %-34s %12s %12s %8s%n",
                "", "per volley", "per point", "speedup");

        double java = measure(SolverBenchmark::javaLoop, input);
        row("Java", java, 0.0);

        if (!NativeSolver.isAvailable()) {
            System.out.println();
            System.out.println("  native solver unavailable: " + NativeSolver.getStatus());
            System.out.println("  build it with ./native/build.sh to compare");
            return;
        }

        row("JNI, one call per point", measure(SolverBenchmark::nativePerCall, input), java);
        row("JNI, one call per volley", measure(SolverBenchmark::nativeBatch, input), java);

        System.out.println();
        System.out.printf(Locale.ROOT,
                "  For scale, one server tick is 50000 us and a volley is fired once.%n");
    }
}
