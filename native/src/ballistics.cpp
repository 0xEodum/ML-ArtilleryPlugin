// C++ port of the analytic projectile solver, exposed through JNI.
//
// This is a line-for-line translation of ProjectileBallistics so that the two
// can be compared on equal terms: same closed form, same bracketing, same
// iteration counts. Any speed difference is therefore attributable to the
// runtime rather than to a different algorithm.
//
// See native/README.md for how to build, and SolverBenchmark for the numbers.

#include <cmath>
#include <jni.h>

namespace {

struct Ballistics {
    double gravity;
    double drag;
    bool gravity_before_move;

    double retention() const { return 1.0 - drag; }
    double terminal() const { return gravity / drag; }
    double log_retention() const { return std::log(1.0 - drag); }

    double initial_step_velocity_y(double vy) const {
        return gravity_before_move ? vy - gravity : vy;
    }

    // S(n) = (1 - d^n) / c
    double geometric_sum(double ticks) const {
        return (1.0 - std::pow(retention(), ticks)) / drag;
    }

    double horizontal_displacement(double u0h, double ticks) const {
        return u0h * geometric_sum(ticks);
    }

    double vertical_displacement(double u0y, double ticks) const {
        const double v_inf = -terminal();
        return v_inf * ticks + (u0y - v_inf) * geometric_sum(ticks);
    }

    double horizontal_step_velocity(double u0h, double ticks) const {
        return u0h * std::pow(retention(), ticks);
    }

    double vertical_step_velocity(double u0y, double ticks) const {
        const double v_inf = -terminal();
        return v_inf + (u0y - v_inf) * std::pow(retention(), ticks);
    }

    // Arrival tick, resolved on the chord inside the tick the way the entity
    // actually moves. NaN when the projectile never covers the distance.
    double exact_ticks_to_distance(double u0h, double distance) const {
        if (u0h <= 0.0) {
            return NAN;
        }
        const double arg = 1.0 - drag * distance / u0h;
        if (arg <= 0.0) {
            return NAN;
        }
        double n = std::floor(std::log(arg) / log_retention());
        if (n < 0.0) {
            n = 0.0;
        }
        for (int guard = 0; guard < 4; ++guard) {
            const double xn = horizontal_displacement(u0h, n);
            const double ux = horizontal_step_velocity(u0h, n);
            if (ux <= 0.0) {
                return NAN;
            }
            const double fraction = (distance - xn) / ux;
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
        return std::log(arg) / log_retention();
    }

    double height_at_distance(double speed, double angle, double distance) const {
        const double u0h = speed * std::cos(angle);
        const double u0y = initial_step_velocity_y(speed * std::sin(angle));
        const double t = exact_ticks_to_distance(u0h, distance);
        if (std::isnan(t)) {
            return NAN;
        }
        const double n = std::floor(t);
        return vertical_displacement(u0y, n) + (t - n) * vertical_step_velocity(u0y, n);
    }

    // Returns the launch speed, or -1 when the target cannot be reached.
    double solve_speed(double distance, double height, double angle,
                       double max_speed) const {
        if (!(distance > 0.0)) {
            return -1.0;
        }
        const double cosine = std::cos(angle);
        if (cosine <= 1e-9) {
            return -1.0;
        }

        double lo = drag * distance / cosine * (1.0 + 1e-12);
        if (lo >= max_speed) {
            return -1.0;
        }

        double hi = std::fmin(std::fmax(lo * 2.0, 1.0), max_speed);
        bool bracketed = false;
        for (;;) {
            const double value = height_at_distance(hi, angle, distance);
            if (!std::isnan(value) && value >= height) {
                bracketed = true;
                break;
            }
            if (hi >= max_speed) {
                break;
            }
            hi = std::fmin(hi * 1.6, max_speed);
        }
        if (!bracketed) {
            return -1.0;
        }

        for (int i = 0; i < 200 && hi - lo > 1e-13; ++i) {
            const double mid = 0.5 * (lo + hi);
            const double value = height_at_distance(mid, angle, distance);
            if (std::isnan(value) || value < height) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }
};

}  // namespace

extern "C" {

JNIEXPORT jdouble JNICALL
Java_org_yudev_airtillery_ballistics_NativeSolver_nativeSolveSpeed(
        JNIEnv*, jclass, jdouble gravity, jdouble drag, jboolean gravity_before_move,
        jdouble distance, jdouble height, jdouble angle, jdouble max_speed) {
    const Ballistics b{gravity, drag, gravity_before_move == JNI_TRUE};
    return b.solve_speed(distance, height, angle, max_speed);
}

JNIEXPORT void JNICALL
Java_org_yudev_airtillery_ballistics_NativeSolver_nativeSolveSpeedBatch(
        JNIEnv* env, jclass, jdouble gravity, jdouble drag, jboolean gravity_before_move,
        jdoubleArray distances, jdoubleArray heights, jdoubleArray angles,
        jdouble max_speed, jdoubleArray out) {
    const Ballistics b{gravity, drag, gravity_before_move == JNI_TRUE};

    const jsize count = env->GetArrayLength(distances);

    // Critical access avoids copying the arrays; the region is short and makes
    // no JNI calls, which is what the critical contract requires.
    auto* d = static_cast<jdouble*>(env->GetPrimitiveArrayCritical(distances, nullptr));
    auto* h = static_cast<jdouble*>(env->GetPrimitiveArrayCritical(heights, nullptr));
    auto* a = static_cast<jdouble*>(env->GetPrimitiveArrayCritical(angles, nullptr));
    auto* o = static_cast<jdouble*>(env->GetPrimitiveArrayCritical(out, nullptr));

    if (d != nullptr && h != nullptr && a != nullptr && o != nullptr) {
        for (jsize i = 0; i < count; ++i) {
            o[i] = b.solve_speed(d[i], h[i], a[i], max_speed);
        }
    }

    if (o != nullptr) env->ReleasePrimitiveArrayCritical(out, o, 0);
    if (a != nullptr) env->ReleasePrimitiveArrayCritical(angles, a, JNI_ABORT);
    if (h != nullptr) env->ReleasePrimitiveArrayCritical(heights, h, JNI_ABORT);
    if (d != nullptr) env->ReleasePrimitiveArrayCritical(distances, d, JNI_ABORT);
}

}  // extern "C"
