package org.yudev.trajectoryrecorder;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TestLauncher {
    private final TrajectoryRecorderPlugin plugin;
    private final Map<String, TestSession> activeSessions = new ConcurrentHashMap<>();

    public TestLauncher(TrajectoryRecorderPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTestSession(Player player, double minSpeed, double maxSpeed, double speedStep, int batchSize) {
        startTestSession(player, minSpeed, maxSpeed, speedStep, batchSize, 45.0);
    }

    public void startTestSession(Player player, double minSpeed, double maxSpeed, double speedStep, int batchSize, double launchAngle) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String sessionId = dateFormat.format(new Date()) + "_" + player.getName();

        List<Double> speeds = new ArrayList<>();
        for (double speed = minSpeed; speed <= maxSpeed; speed += speedStep) {
            speeds.add(speed);
        }

        Location startLoc = player.getLocation().clone();
        double maxDistance = maxSpeed * 100; // Примерная оценка
        plugin.getChunkManager().loadChunksAlongPath(player, startLoc, maxDistance, launchAngle);

        TestSession session = new TestSession(player, sessionId, speeds, batchSize, launchAngle);
        activeSessions.put(sessionId, session);

        player.sendMessage("§aНачало тестирования с " + speeds.size() +
                " скоростями от " + minSpeed + " до " + maxSpeed +
                " под углом " + launchAngle + "°");

        session.launchNextBatch();
    }

    public void notifyTestCompleted(String sessionId, double speed) {
        TestSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.markSpeedCompleted(speed);
        }
    }

    public void cancelTestsForPlayer(Player player) {
        for (TestSession session : new ArrayList<>(activeSessions.values())) {
            if (session.getPlayer().equals(player)) {
                session.cancel();
                activeSessions.remove(session.getSessionId());
                player.sendMessage(ChatColor.RED + "All test sessions cancelled");
            }
        }
    }

    private class TestSession {
        private final Player player;
        private final String sessionId;
        private final List<Double> allSpeeds;
        private final int batchSize;
        private final double launchAngle;
        private final Set<Double> completedSpeeds = ConcurrentHashMap.newKeySet();
        private final Set<Double> activeSpeeds = ConcurrentHashMap.newKeySet();
        private int currentIndex = 0;

        public TestSession(Player player, String sessionId, List<Double> speeds, int batchSize, double launchAngle) {
            this.player = player;
            this.sessionId = sessionId;
            this.allSpeeds = speeds;
            this.batchSize = batchSize;
            this.launchAngle = launchAngle;
        }

        public Player getPlayer() {
            return player;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void launchNextBatch() {
            if (currentIndex >= allSpeeds.size()) {
                finishSession();
                return;
            }

            int endIndex = Math.min(currentIndex + batchSize, allSpeeds.size());
            List<Double> batchSpeeds = allSpeeds.subList(currentIndex, endIndex);

            player.sendMessage(ChatColor.YELLOW + "Launching batch " +
                    ((currentIndex / batchSize) + 1) + "/" +
                    ((allSpeeds.size() + batchSize - 1) / batchSize) +
                    " with speeds: " + batchSpeeds);

            for (double speed : batchSpeeds) {
                launchProjectile(speed);
                activeSpeeds.add(speed);
            }

            currentIndex = endIndex;
        }

        private void launchProjectile(double speed) {
            World world = player.getWorld();
            Location location = player.getLocation().clone();

            Vector direction = new Vector(1, 0, 0);

            location.add(0, 1, 0);

            Entity tnt = world.spawnEntity(location, EntityType.PRIMED_TNT);

            double angle = Math.toRadians(launchAngle);
            Vector velocity = new Vector(
                    Math.cos(angle) * direction.getX(),
                    Math.sin(angle),
                    Math.cos(angle) * direction.getZ()
            ).normalize().multiply(speed);

            tnt.setVelocity(velocity);

            if (tnt instanceof TNTPrimed) {
                ((TNTPrimed) tnt).setFuseTicks(800);
            }

            TrajectoryRecorder recorder = new TrajectoryRecorder(plugin, tnt, ProjectileType.TNT, sessionId, speed, launchAngle);
            recorder.startRecording();
        }

        public void markSpeedCompleted(double speed) {
            activeSpeeds.remove(speed);
            completedSpeeds.add(speed);

            if (activeSpeeds.isEmpty() && currentIndex < allSpeeds.size()) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        launchNextBatch();
                    }
                }.runTaskLater(plugin, 10L);
            }

            if (completedSpeeds.size() % 10 == 0 || completedSpeeds.size() == allSpeeds.size()) {
                player.sendMessage(ChatColor.GREEN + "Test progress: " +
                        completedSpeeds.size() + "/" + allSpeeds.size() +
                        " speeds completed");
            }
        }

        private void finishSession() {
            activeSessions.remove(sessionId);
            player.sendMessage(ChatColor.GREEN + "Test session completed! Tested " +
                    completedSpeeds.size() + " different speeds.");
            plugin.getLogger().info("Test session " + sessionId + " completed with " +
                    completedSpeeds.size() + " speeds.");
        }

        public void cancel() {
            player.sendMessage(ChatColor.RED + "Test session cancelled.");
            activeSessions.remove(sessionId);
        }
    }
}