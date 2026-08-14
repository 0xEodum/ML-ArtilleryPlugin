package org.yudev.airtillery;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Trident;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.yudev.airtillery.ballistics.AimMode;
import org.yudev.airtillery.ballistics.BallisticSolution;
import org.yudev.airtillery.ballistics.BallisticsRegistry;
import org.yudev.airtillery.station.ProjectileKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Fire control: turns a firing order into projectiles in the air.
 *
 * <p>Aiming is one call per aim point into the analytic solver. There is no
 * external process, no model, and nothing to wait on, so a volley is computed
 * inline and fired in the same tick it was ordered.
 */
public class ArtilleryManager {

    private final ArtilleryPlugin plugin;
    private final BallisticsRegistry ballistics;
    private final Random random = new Random();

    private final Map<Entity, BukkitTask> trackers = new HashMap<>();

    public ArtilleryManager(ArtilleryPlugin plugin, BallisticsRegistry ballistics) {
        this.plugin = plugin;
        this.ballistics = ballistics;
    }

    /** Outcome of a firing order. */
    public static final class FireReport {
        private final boolean fired;
        private final int launched;
        private final int skipped;
        private final String reason;
        private final List<String> hints;

        private FireReport(boolean fired, int launched, int skipped,
                           String reason, List<String> hints) {
            this.fired = fired;
            this.launched = launched;
            this.skipped = skipped;
            this.reason = reason;
            this.hints = hints;
        }

        static FireReport success(int launched, int skipped) {
            return new FireReport(true, launched, skipped, null, Collections.emptyList());
        }

        static FireReport failed(String reason, List<String> hints) {
            return new FireReport(false, 0, 0, reason, hints);
        }

        public boolean isFired() {
            return fired;
        }

        public int getLaunched() {
            return launched;
        }

        /** Aim points that had no solution and were not fired at. */
        public int getSkipped() {
            return skipped;
        }

        public String getReason() {
            return reason;
        }

        /** Extra lines worth showing the player, such as the reachable angles. */
        public List<String> getHints() {
            return hints;
        }
    }

    /**
     * Fire {@code rounds} projectiles so they arrive around {@code target} at
     * roughly {@code impactAngleDegrees} below horizontal.
     *
     * <p>The elevation is resolved once for the centre of the impact area and
     * then pinned for every round: the points of a volley are metres apart on a
     * target that is usually hundreds of metres away, so solving each one
     * separately would only jitter the elevation by a fraction of a degree while
     * costing a nested root find per round.
     */
    public FireReport fire(Player initiator, Location launchLocation, Location target,
                           ProjectileKind kind, int rounds, double impactAngleDegrees) {
        double dx = target.getX() - launchLocation.getX();
        double dz = target.getZ() - launchLocation.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double heightDifference = target.getY() - launchLocation.getY();

        if (horizontalDistance < 1.0) {
            return FireReport.failed(
                    "цель слишком близко по горизонтали ("
                            + String.format("%.1f", horizontalDistance) + " блока)",
                    Collections.emptyList());
        }

        String type = kind.getBallisticsType();
        AimMode mode = AimMode.impact(impactAngleDegrees);

        long startNanos = System.nanoTime();
        BallisticsRegistry.AngleResolution resolved = ballistics.resolveLaunchAngle(
                type, mode, horizontalDistance, heightDifference, Math.toRadians(45.0));

        if (!resolved.isSuccess()) {
            return FireReport.failed(resolved.getReason(),
                    describeEnvelope(type, horizontalDistance, heightDifference));
        }

        double launchAngle = resolved.getAngleRadians();
        List<TargetPoint> points = generateTargetPoints(
                launchLocation, target, rounds, plugin.getConfig().getDouble("impact-radius", 3.0),
                launchAngle);

        int solved = 0;
        for (TargetPoint point : points) {
            // No angle fallback: the player asked for a descent angle, and
            // quietly firing on a different arc would not be that.
            BallisticSolution solution = ballistics.aim(type,
                    point.getHorizontalDistance(), point.getHeightDifference(),
                    launchAngle, false);
            if (!solution.isSuccess()) {
                point.setSolved(false);
                point.setFailureReason(solution.getReason());
                continue;
            }
            point.setSolved(true);
            point.setVelocity(solution.getSpeed());
            point.setAngleRadians(solution.getAngleRadians());
            point.setFlightTicks(solution.getFlightTicks());
            point.setApexHeight(solution.getApexHeight());
            point.setImpactAngleRadians(solution.getImpactAngleRadians());
            solved++;
        }
        long elapsedNanos = System.nanoTime() - startNanos;

        if (solved == 0) {
            return FireReport.failed(points.get(0).getFailureReason(),
                    describeEnvelope(type, horizontalDistance, heightDifference));
        }

        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            TargetPoint centre = points.get(0);
            plugin.getLogger().info(String.format(
                    "Volley: %s x%d, dL=%.1f dH=%.1f, launch %.1f deg, impact %.1f deg, "
                            + "v=%.4f, flight %.0f ticks, solved in %.3f ms",
                    kind.name(), solved, horizontalDistance, heightDifference,
                    Math.toDegrees(centre.getAngleRadians()),
                    Math.toDegrees(centre.getImpactAngleRadians()),
                    centre.getVelocity(), centre.getFlightTicks(), elapsedNanos / 1e6));
        }

        dispatch(initiator, launchLocation, points, kind);
        return FireReport.success(solved, points.size() - solved);
    }

    private List<String> describeEnvelope(String type, double distance, double height) {
        double[] impacts = ballistics.achievableImpactAngles(type, distance, height);
        if (impacts == null) {
            return Collections.singletonList(
                    "Цель недостижима ни под каким углом при скорости до "
                            + String.format("%.1f", ballistics.getMaxSpeed()) + " блоков/тик.");
        }
        return Collections.singletonList(String.format(
                "Для этой цели доступны углы падения от %.1f° до %.1f°",
                Math.toDegrees(impacts[0]), Math.toDegrees(impacts[1])));
    }

    // ------------------------------------------------------------------
    // Aim points
    // ------------------------------------------------------------------

    /**
     * The centre of the impact area plus a random scatter of the remaining
     * rounds inside {@code radius}, distributed by area rather than by radius so
     * the pattern does not clump in the middle.
     */
    private List<TargetPoint> generateTargetPoints(Location launchLocation, Location centre,
                                                   int rounds, double radius,
                                                   double launchAngle) {
        List<TargetPoint> points = new ArrayList<>(rounds);
        points.add(pointFor(launchLocation, centre, launchAngle));

        for (int i = 1; i < rounds; i++) {
            double bearing = random.nextDouble() * 2.0 * Math.PI;
            double distance = radius * Math.sqrt(random.nextDouble());
            Location scattered = new Location(centre.getWorld(),
                    centre.getX() + distance * Math.cos(bearing),
                    centre.getY(),
                    centre.getZ() + distance * Math.sin(bearing));
            points.add(pointFor(launchLocation, scattered, launchAngle));
        }
        return points;
    }

    private TargetPoint pointFor(Location launchLocation, Location target, double launchAngle) {
        double dx = target.getX() - launchLocation.getX();
        double dz = target.getZ() - launchLocation.getZ();
        return new TargetPoint(target.clone(),
                Math.sqrt(dx * dx + dz * dz),
                target.getY() - launchLocation.getY(),
                launchAngle);
    }

    // ------------------------------------------------------------------
    // Launching
    // ------------------------------------------------------------------

    private void dispatch(Player initiator, Location launchLocation,
                          List<TargetPoint> points, ProjectileKind kind) {
        boolean burst = "BURST".equalsIgnoreCase(
                plugin.getConfig().getString("fire-mode", "RAIN"));
        long spacing = Math.max(0L, plugin.getConfig().getLong("rain-spacing-ticks", 3L));

        int index = 0;
        for (TargetPoint point : points) {
            if (!point.isSolved()) {
                continue;
            }
            if (burst || spacing == 0L) {
                launchProjectile(initiator, launchLocation, point, kind);
            } else {
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> launchProjectile(initiator, launchLocation, point, kind),
                        index * spacing);
            }
            index++;
        }

        launchLocation.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, launchLocation,
                1, 0, 0, 0, 0);
        launchLocation.getWorld().spawnParticle(Particle.FLAME, launchLocation,
                20, 0.4, 0.4, 0.4, 0.05);
    }

    private Entity launchProjectile(Player initiator, Location launchLocation,
                                    TargetPoint point, ProjectileKind kind) {
        Vector horizontal = new Vector(
                point.getLocation().getX() - launchLocation.getX(),
                0,
                point.getLocation().getZ() - launchLocation.getZ());
        if (horizontal.lengthSquared() < 1e-9) {
            return null;
        }
        horizontal.normalize();

        Vector direction = horizontal.clone();
        direction.setY(Math.tan(point.getAngleRadians()));
        direction.normalize();

        Entity projectile = null;
        switch (kind) {
            case ARROW:
            case FLAMING_ARROW: {
                Arrow arrow = launchLocation.getWorld().spawnArrow(
                        launchLocation, direction, (float) point.getVelocity(), 0);
                arrow.setPersistent(false);
                if (initiator != null) {
                    arrow.setShooter(initiator);
                }
                if (kind == ProjectileKind.FLAMING_ARROW) {
                    arrow.setFireTicks(Integer.MAX_VALUE);
                }
                projectile = arrow;
                break;
            }
            case TRIDENT: {
                Trident trident = launchLocation.getWorld().spawn(launchLocation, Trident.class);
                trident.setVelocity(direction.clone().multiply(point.getVelocity()));
                trident.setPersistent(false);
                if (initiator != null) {
                    trident.setShooter(initiator);
                }
                projectile = trident;
                break;
            }
            case TNT: {
                TNTPrimed tnt = launchLocation.getWorld().spawn(launchLocation, TNTPrimed.class);
                tnt.setVelocity(direction.clone().multiply(point.getVelocity()));
                // Detonate on arrival rather than after a fixed fuse, so the
                // charge bursts on the aim point instead of landing, bouncing
                // and rolling past it.
                tnt.setFuseTicks(Math.max(1, (int) Math.round(point.getFlightTicks())));
                tnt.setMetadata("artillery_tnt", new FixedMetadataValue(plugin, true));
                projectile = tnt;
                break;
            }
            default:
                break;
        }

        if (projectile != null) {
            track(projectile, kind);
        }
        return projectile;
    }

    // ------------------------------------------------------------------
    // Cleanup
    // ------------------------------------------------------------------

    /**
     * Removes spent arrows and tridents a minute after they stick, so a large
     * volley does not leave hundreds of entities loaded.
     */
    private void track(Entity projectile, ProjectileKind kind) {
        if (kind == ProjectileKind.TNT) {
            return;  // the fuse already ends its life
        }
        BukkitTask task = new BukkitRunnable() {
            private int groundedTicks = 0;

            @Override
            public void run() {
                if (!projectile.isValid() || projectile.isDead()) {
                    finish();
                    return;
                }
                boolean stuck = projectile instanceof Arrow && ((Arrow) projectile).isInBlock();
                if (!stuck && projectile.getVelocity().lengthSquared() >= 0.01) {
                    groundedTicks = 0;
                    return;
                }
                if (++groundedTicks == 1) {
                    onImpact(projectile, kind);
                }
                if (groundedTicks >= 1200) {
                    projectile.remove();
                    finish();
                }
            }

            private void finish() {
                cancel();
                trackers.remove(projectile);
            }
        }.runTaskTimer(plugin, 5L, 5L);

        trackers.put(projectile, task);
    }

    private void onImpact(Entity projectile, ProjectileKind kind) {
        Location where = projectile.getLocation();
        switch (kind) {
            case FLAMING_ARROW:
                where.getWorld().spawnParticle(Particle.FLAME, where, 10, 0.2, 0.2, 0.2, 0.05);
                break;
            case TRIDENT:
                where.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, where,
                        20, 0.5, 0.5, 0.5, 1);
                break;
            case ARROW:
            default:
                where.getWorld().spawnParticle(Particle.CRIT, where, 8, 0.2, 0.2, 0.2, 0.1);
                break;
        }
    }

    /** Stops every tracker; called on plugin disable. */
    public void shutdown() {
        for (BukkitTask task : trackers.values()) {
            task.cancel();
        }
        trackers.clear();
    }
}
