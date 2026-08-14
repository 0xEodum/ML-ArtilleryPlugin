package org.yudev.airtillery.ballistics;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Optional C++ implementation of the inverse solver, reached through JNI.
 *
 * <p>This exists as an experiment. The solver is a few dozen floating-point
 * operations per bisection step with no allocation, which is the shape of code
 * HotSpot compiles well, so the interesting question is whether crossing the
 * JNI boundary costs more than the arithmetic saves. Run
 * {@code SolverBenchmark} for the measurement on your own hardware.
 *
 * <p>Loading is entirely best-effort. The library is looked for on
 * {@code java.library.path} first, then extracted from the jar for the current
 * platform. If anything fails — no library shipped, wrong architecture, a
 * read-only temp directory — {@link #isAvailable()} stays false and every
 * caller silently uses the Java path. The plugin therefore has no build-time
 * or run-time dependency on a C++ toolchain.
 */
public final class NativeSolver {

    private static final String LIBRARY_NAME = "airtillery_ballistics";

    private static volatile boolean available;
    private static volatile String status = "not initialised";

    private NativeSolver() {
    }

    static {
        try {
            load();
            // Prove the symbols actually resolve before advertising the library:
            // a stale build with a renamed method would otherwise blow up later
            // in the middle of a volley.
            double probe = nativeSolveSpeed(0.05, 0.01, false, 100.0, 0.0,
                    Math.PI / 4.0, 40.0);
            if (probe > 0.0 && !Double.isNaN(probe)) {
                available = true;
                status = "loaded";
            } else {
                status = "self-check returned " + probe;
            }
        } catch (Throwable t) {
            // UnsatisfiedLinkError, SecurityException, IOException, anything.
            status = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private static void load() throws IOException {
        try {
            System.loadLibrary(LIBRARY_NAME);
            return;
        } catch (UnsatisfiedLinkError ignored) {
            // Not on java.library.path; fall through to the bundled copy.
        }

        String resource = "/natives/" + platformLibraryName();
        try (InputStream in = NativeSolver.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("no bundled library at " + resource);
            }
            Path dir = Files.createTempDirectory("airtillery-native");
            Path target = dir.resolve(platformLibraryName());
            try (OutputStream out = Files.newOutputStream(target)) {
                in.transferTo(out);
            }
            System.load(target.toAbsolutePath().toString());
            target.toFile().deleteOnExit();
            dir.toFile().deleteOnExit();
        }
    }

    private static String platformLibraryName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return LIBRARY_NAME + ".dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "lib" + LIBRARY_NAME + ".dylib";
        }
        return "lib" + LIBRARY_NAME + ".so";
    }

    /** True when the native library loaded and answered a probe correctly. */
    public static boolean isAvailable() {
        return available;
    }

    /** Human-readable load outcome, for the startup log. */
    public static String getStatus() {
        return status;
    }

    /**
     * Launch speed for a target, or a negative value when unreachable.
     * Mirrors {@link ProjectileBallistics#solveSpeed}.
     */
    static native double nativeSolveSpeed(double gravity, double drag,
                                          boolean gravityBeforeMove,
                                          double distance, double height,
                                          double angleRadians, double maxSpeed);

    /**
     * Solve a batch of aim points in one crossing of the JNI boundary.
     *
     * <p>Batching is the only way a native solver can plausibly win here: one
     * call per volley amortises the transition over every round, where one call
     * per round would pay it a hundred times.
     *
     * @param distances  horizontal distance per point
     * @param heights    height difference per point
     * @param angles     launch elevation per point
     * @param out        receives the launch speed per point, negative if unreachable
     */
    static native void nativeSolveSpeedBatch(double gravity, double drag,
                                             boolean gravityBeforeMove,
                                             double[] distances, double[] heights,
                                             double[] angles, double maxSpeed,
                                             double[] out);

    /** Removes the extracted temporary library directory, if any. */
    static void cleanup(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
