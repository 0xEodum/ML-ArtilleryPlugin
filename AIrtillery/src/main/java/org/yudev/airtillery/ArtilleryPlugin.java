package org.yudev.airtillery;


import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;

public class ArtilleryPlugin extends JavaPlugin {
    private PythonClient pythonClient;
    private ArtilleryManager artilleryManager;
    private Process pythonProcess;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String serverUrl = getConfig().getString("python-server-url", "http://localhost:5000");
        pythonClient = new PythonClient(this, serverUrl);

        if (getConfig().getBoolean("start-python-server", true)) {
            startPythonServer();
        }

        artilleryManager = new ArtilleryManager(this, pythonClient);

        getCommand("giveartillery").setExecutor(new ArtilleryCommandExecutor(this, artilleryManager));

        getServer().getPluginManager().registerEvents(new ArtilleryListener(this, artilleryManager), this);
        getServer().getPluginManager().registerEvents(new TntExplosionListener(this), this);

        getLogger().info("Artillery Plugin enabled!");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        stopPythonServer();
        getLogger().info("Artillery Plugin disabled!");
    }

    private void startPythonServer() {
        try {
            String pythonPath = getConfig().getString("python-path", "python");
            String scriptPath = getConfig().getString("script-path", "plugins/ArtilleryPlugin/flask_server.py");

            ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptPath);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            pythonProcess = pb.start();

            Thread.sleep(2000);

            getLogger().info("Python server started!");
        } catch (IOException | InterruptedException e) {
            getLogger().severe("Failed to start Python server: " + e.getMessage());
        }
    }

    private void stopPythonServer() {
        if (pythonProcess != null && pythonProcess.isAlive()) {
            pythonProcess.destroy();
            getLogger().info("Python server stopped.");
        }
    }

    public PythonClient getPythonClient() {
        return pythonClient;
    }

    public ArtilleryManager getArtilleryManager() {
        return artilleryManager;
    }
}