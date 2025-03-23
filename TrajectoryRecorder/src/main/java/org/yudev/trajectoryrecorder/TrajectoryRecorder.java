package org.yudev.trajectoryrecorder;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrajectoryRecorder {
    private final TrajectoryRecorderPlugin plugin;
    private final Entity projectile;
    private final ProjectileType projectileType;
    private final List<Vector> positions = new ArrayList<>();
    private final List<Vector> velocities = new ArrayList<>();
    private final long startTime;
    private final Location startLocation;
    private final double terminalHeight;
    private final String testId;
    private final double initialSpeed;
    private final double launchAngle;

    public TrajectoryRecorder(TrajectoryRecorderPlugin plugin, Entity projectile, ProjectileType projectileType) {
        this(plugin, projectile, projectileType, null, -1, 45.0);
    }

    public TrajectoryRecorder(TrajectoryRecorderPlugin plugin, Entity projectile, ProjectileType projectileType,
                              String testId, double initialSpeed) {
        this(plugin, projectile, projectileType, testId, initialSpeed, 45.0);
    }

    public TrajectoryRecorder(TrajectoryRecorderPlugin plugin, Entity projectile, ProjectileType projectileType,
                              String testId, double initialSpeed, double launchAngleDegrees) {
        this.plugin = plugin;
        this.projectile = projectile;
        this.projectileType = projectileType;
        this.startTime = System.currentTimeMillis();
        this.startLocation = projectile.getLocation().clone();
        this.terminalHeight = startLocation.getY() - 50.0;
        this.testId = testId;
        this.initialSpeed = initialSpeed;
        this.launchAngle = Math.toRadians(launchAngleDegrees);
    }

    public void startRecording() {
        new BukkitRunnable() {
            private int ticks = 0;
            private boolean isLanded = false;

            @Override
            public void run() {
                if (!projectile.isValid()) {
                    saveTrajectory();
                    this.cancel();
                    return;
                }

                Vector position = projectile.getLocation().toVector();
                Vector velocity = projectile.getVelocity().clone();

                positions.add(position);
                velocities.add(velocity);

                if (position.getY() <= terminalHeight) {
                    saveTrajectory();
                    projectile.remove();
                    this.cancel();
                    return;
                }

                boolean shouldStop = false;

                if (projectile instanceof Projectile && ((Projectile) projectile).isOnGround()) {
                    shouldStop = true;
                } else if (projectile instanceof TNTPrimed && ((TNTPrimed) projectile).getFuseTicks() <= 0) {
                    shouldStop = true;
                } else if (projectile.isOnGround() || projectile.getVelocity().lengthSquared() < 0.01) {
                    shouldStop = true;
                }

                ticks++;

                if (ticks > 600 || shouldStop) {
                    saveTrajectory();
                    this.cancel();
                    if (projectile.isValid()) {
                        projectile.remove();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void saveTrajectory() {
        if (positions.isEmpty()) {
            return;
        }

        File dataFolder = new File(plugin.getDataFolder(), "trajectories");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String timestamp = dateFormat.format(new Date());

        String fileName;
        if (testId != null) {
            fileName = String.format("%s_test_%s_speed_%.2f_angle_%.1f.csv",
                    projectileType.name(), testId, initialSpeed, Math.toDegrees(launchAngle)).replace(',', '.');
        } else {
            fileName = projectileType.name() + "_" + timestamp + ".csv";
        }

        File outputFile = new File(dataFolder, fileName);

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("tick,x,y,z,vx,vy,vz,horizontal_distance,height_difference,relative_z,velocity,angle_radians,projectile_type\n");

            for (int i = 0; i < positions.size(); i++) {
                Vector pos = positions.get(i);
                Vector vel = velocities.get(i);
                Vector relativePos = pos.clone().subtract(startLocation.toVector());

                double horizontalDistance = Math.sqrt(
                        relativePos.getX() * relativePos.getX() +
                                relativePos.getZ() * relativePos.getZ()
                );

                double speed = initialSpeed;

                writer.write(String.format(Locale.US,
                        "%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%s\n",
                        i,
                        pos.getX(), pos.getY(), pos.getZ(),
                        vel.getX(), vel.getY(), vel.getZ(),
                        horizontalDistance, relativePos.getY(), relativePos.getZ(),
                        speed, launchAngle, projectileType.name()));
            }

            if (testId == null && projectile.getWorld().getPlayers().size() > 0) {
                projectile.getWorld().getPlayers().forEach(player ->
                        player.sendMessage(ChatColor.GREEN + "Trajectory data saved to " + fileName +
                                " (" + positions.size() + " points)")
                );
            } else if (testId != null) {
                plugin.getTestLauncher().notifyTestCompleted(testId, initialSpeed);
            }

            plugin.getLogger().info("Saved trajectory data to " + outputFile.getAbsolutePath());

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save trajectory data: " + e.getMessage());
        }
    }
}