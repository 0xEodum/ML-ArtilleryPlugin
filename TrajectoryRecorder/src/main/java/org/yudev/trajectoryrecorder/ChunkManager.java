package org.yudev.trajectoryrecorder;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ChunkManager {
    private final TrajectoryRecorderPlugin plugin;
    private final Map<String, Set<ChunkCoord>> forcedChunks = new HashMap<>();
    private final boolean useForceLoadCommand;

    public ChunkManager(TrajectoryRecorderPlugin plugin) {
        this.plugin = plugin;
        Server server = plugin.getServer();
        useForceLoadCommand = server.getBukkitVersion().contains("1.14") ||
                server.getBukkitVersion().contains("1.15") ||
                server.getBukkitVersion().contains("1.16") ||
                server.getBukkitVersion().contains("1.17") ||
                server.getBukkitVersion().contains("1.18") ||
                server.getBukkitVersion().contains("1.19") ||
                server.getBukkitVersion().contains("1.20");

        plugin.getLogger().info("Using forceload command: " + useForceLoadCommand);
    }

    public void loadChunksAlongPath(Player player, Location startLoc, double distance, double angle) {
        String worldName = startLoc.getWorld().getName();
        Set<ChunkCoord> chunks = new HashSet<>();

        double radians = Math.toRadians(angle);
        double dx = Math.cos(radians);
        double dy = Math.sin(radians);

        double step = 16.0; // Размер чанка
        double maxSteps = distance / step;

        for (double t = 0; t <= 1.0; t += 0.05) {
            for (double s = 0; s <= maxSteps; s++) {
                double x = startLoc.getX() + s * step * dx;
                double maxHeight = distance * Math.sin(radians);
                double y = startLoc.getY() + 4 * maxHeight * t * (1 - t) + s * step * dy;
                double z = startLoc.getZ();

                int chunkX = (int) Math.floor(x) >> 4;
                int chunkZ = (int) Math.floor(z) >> 4;

                chunks.add(new ChunkCoord(chunkX, chunkZ));
            }
        }

        for (ChunkCoord coord : chunks) {
            forceLoadChunk(player, startLoc.getWorld(), coord.x, coord.z);
        }

        forcedChunks.put(player.getName(), chunks);

        player.sendMessage("§aЗагружено " + chunks.size() + " чанков для тестирования");

        new BukkitRunnable() {
            @Override
            public void run() {
                releaseChunks(player);
            }
        }.runTaskLater(plugin, 20 * 60 * 10);
    }


    private void forceLoadChunk(Player player, World world, int chunkX, int chunkZ) {
        if (useForceLoadCommand) {
            String command = "forceload add " + chunkX + " " + chunkZ;
            plugin.getServer().dispatchCommand(
                    plugin.getServer().getConsoleSender(), command
            );
        } else {
            Chunk chunk = world.getChunkAt(chunkX, chunkZ);
            chunk.load(true);

            world.setChunkForceLoaded(chunkX, chunkZ, true);
        }
    }

    public void releaseChunks(Player player) {
        Set<ChunkCoord> chunks = forcedChunks.remove(player.getName());

        if (chunks != null && !chunks.isEmpty()) {
            World world = player.getWorld();

            for (ChunkCoord coord : chunks) {
                if (useForceLoadCommand) {
                    String command = "forceload remove " + coord.x + " " + coord.z;
                    plugin.getServer().dispatchCommand(
                            plugin.getServer().getConsoleSender(), command
                    );
                } else {
                    world.setChunkForceLoaded(coord.x, coord.z, false);
                }
            }

            player.sendMessage("§eВыгружено " + chunks.size() + " чанков");
        }
    }

    public void releaseAllChunks() {
        for (Map.Entry<String, Set<ChunkCoord>> entry : forcedChunks.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                releaseChunks(player);
            }
        }
        forcedChunks.clear();
    }

    private static class ChunkCoord {
        final int x;
        final int z;

        ChunkCoord(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkCoord that = (ChunkCoord) o;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }
}