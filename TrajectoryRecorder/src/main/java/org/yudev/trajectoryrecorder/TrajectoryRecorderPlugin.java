package org.yudev.trajectoryrecorder;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TrajectoryRecorderPlugin extends JavaPlugin {
    private TestLauncher testLauncher;
    private TestingStickManager testingStickManager;
    private ChunkManager chunkManager;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        chunkManager = new ChunkManager(this);
        testLauncher = new TestLauncher(this);
        testingStickManager = new TestingStickManager(this);

        getCommand("givetestingstick").setExecutor(this);
        getCommand("canceltests").setExecutor(this);

        getLogger().info("TrajectoryRecorder has been enabled!");
    }

    @Override
    public void onDisable() {
        chunkManager.releaseAllChunks();

        getLogger().info("TrajectoryRecorder has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("givetestingstick")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be used by players");
                return true;
            }

            Player player = (Player) sender;

            if (!player.hasPermission("trajectoryrecorder.testing")) {
                player.sendMessage("You don't have permission to use this command");
                return true;
            }

            int batchSize = 3;
            double speedStep = 0.5;
            double minSpeed = 0.5;
            double maxSpeed = 12.0;
            double launchAngle = 45.0;

            if (args.length >= 1) {
                try {
                    batchSize = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid batch size: " + args[0]);
                    return false;
                }
            }

            if (args.length >= 2) {
                try {
                    speedStep = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid speed step: " + args[1]);
                    return false;
                }
            }

            if (args.length >= 3) {
                try {
                    minSpeed = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid minimum speed: " + args[2]);
                    return false;
                }
            }

            if (args.length >= 4) {
                try {
                    maxSpeed = Double.parseDouble(args[3]);
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid maximum speed: " + args[3]);
                    return false;
                }
            }

            if (args.length >= 5) {
                try {
                    launchAngle = Double.parseDouble(args[4]);
                    if (launchAngle <= 0 || launchAngle >= 90) {
                        player.sendMessage("Launch angle must be between 0 and 90 degrees");
                        return false;
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid launch angle: " + args[4]);
                    return false;
                }
            }

            if (minSpeed >= maxSpeed) {
                player.sendMessage("Minimum speed must be less than maximum speed");
                return false;
            }

            testingStickManager.giveTestingStick(player, batchSize, speedStep, minSpeed, maxSpeed, launchAngle);

            player.sendMessage("Динамит будет запускаться в направлении оси X+ под углом " + launchAngle + " градусов.");
            return true;
        }
        else if (command.getName().equalsIgnoreCase("canceltests")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be used by players");
                return true;
            }

            Player player = (Player) sender;

            if (!player.hasPermission("trajectoryrecorder.testing")) {
                player.sendMessage("You don't have permission to use this command");
                return true;
            }

            // Отменяем тесты
            testLauncher.cancelTestsForPlayer(player);
            return true;
        }

        return false;
    }

    public TestLauncher getTestLauncher() {
        return testLauncher;
    }
    
    public ChunkManager getChunkManager() {
        return chunkManager;
    }
}