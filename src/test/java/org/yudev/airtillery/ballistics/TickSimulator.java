package org.yudev.airtillery.ballistics;

/**
 * A literal transcription of the per-tick entity update in the game, used as
 * the independent reference the closed form is checked against.
 *
 * <p>Deliberately naive: it steps one tick at a time and shares no code with
 * {@link ProjectileBallistics}, so agreement between the two is evidence and
 * not a tautology.
 */
final class TickSimulator {

    private TickSimulator() {
    }

    /** Positions at every whole tick, {@code [tick][x, y]}, relative to launch. */
    static double[][] trajectory(ProjectileBallistics b, double speed, double angle, int ticks) {
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

    /** Height where the trajectory first crosses {@code distance}, or NaN. */
    static double heightAtDistance(ProjectileBallistics b, double speed,
                                   double angle, double distance) {
        double[][] traj = trajectory(b, speed, angle, 6000);
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

    /**
     * Direction of travel on the tick that reaches {@code distance}, as an
     * angle below horizontal. This is the segment the entity visibly moves
     * along, so it is what an impact angle has to mean.
     */
    static double impactAngle(ProjectileBallistics b, double speed,
                              double angle, double distance) {
        double[][] traj = trajectory(b, speed, angle, 6000);
        for (int i = 1; i < traj.length; i++) {
            if (traj[i][0] >= distance) {
                return Math.atan2(-(traj[i][1] - traj[i - 1][1]),
                        traj[i][0] - traj[i - 1][0]);
            }
        }
        return Double.NaN;
    }

    /** Tick index, with fraction, at which {@code distance} is crossed. */
    static double ticksToDistance(ProjectileBallistics b, double speed,
                                  double angle, double distance) {
        double[][] traj = trajectory(b, speed, angle, 6000);
        for (int i = 1; i < traj.length; i++) {
            if (traj[i][0] >= distance) {
                double span = traj[i][0] - traj[i - 1][0];
                double fraction = span == 0.0 ? 0.0 : (distance - traj[i - 1][0]) / span;
                return (i - 1) + fraction;
            }
        }
        return Double.NaN;
    }
}
