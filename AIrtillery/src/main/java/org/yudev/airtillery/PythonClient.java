package org.yudev.airtillery;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PythonClient {
    private final String serverUrl;
    private final ArtilleryPlugin plugin;

    public PythonClient(ArtilleryPlugin plugin, String serverUrl) {
        this.plugin = plugin;
        this.serverUrl = serverUrl;
    }

    public List<Double> getVelocities(List<TargetPoint> targets, String projectileType) throws Exception {
        MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();

        packer.packMapHeader(1);
        packer.packString("targets");

        packer.packArrayHeader(targets.size());
        for (TargetPoint point : targets) {
            packer.packMapHeader(4);

            packer.packString("horizontal_distance");
            packer.packDouble(point.getHorizontalDistance());

            packer.packString("height_difference");
            packer.packDouble(point.getHeightDifference());

            packer.packString("angle_radians");
            packer.packDouble(point.getAngleRadians());

            packer.packString("projectile_type");
            packer.packString(projectileType);
        }

        byte[] requestBody = packer.toByteArray();
        packer.close();

        plugin.getLogger().info("Sending MessagePack request to Python server: " + targets.size() + " targets");

        HttpURLConnection connection = null;
        try {
            URL url = new URL(serverUrl + "/predict");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/msgpack");
            connection.setRequestProperty("Accept", "application/msgpack");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            try (java.io.OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBody, 0, requestBody.length);
            }

            int responseCode = connection.getResponseCode();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream inputStream;

            if (responseCode >= 200 && responseCode < 300) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }

            if (inputStream != null) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                inputStream.close();
            }

            byte[] responseBody = baos.toByteArray();

            if (responseCode != 200) {
                String errorMessage = "Error getting velocities from server. Code: " + responseCode;
                try {
                    MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(responseBody);
                    if (unpacker.hasNext()) {
                        org.msgpack.core.MessageFormat format = unpacker.getNextFormat();
                        if (format == org.msgpack.core.MessageFormat.MAP32 ||
                                format == org.msgpack.core.MessageFormat.MAP16 ||
                                format == org.msgpack.core.MessageFormat.FIXMAP) {
                            int mapSize = unpacker.unpackMapHeader();
                            for (int i = 0; i < mapSize; i++) {
                                String key = unpacker.unpackString();
                                if (key.equals("error")) {
                                    errorMessage += ", Message: " + unpacker.unpackString();
                                    break;
                                } else {
                                    unpacker.skipValue();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not unpack error message: " + e.getMessage());
                }
                throw new Exception(errorMessage);
            }

            MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(responseBody);

            unpacker.unpackMapHeader(); // Должно быть 1
            String key = unpacker.unpackString();
            if (!key.equals("velocities")) {
                throw new Exception("Unexpected response format: missing velocities key");
            }

            int arraySize = unpacker.unpackArrayHeader();
            List<Double> velocities = new ArrayList<>(arraySize);
            for (int i = 0; i < arraySize; i++) {
                velocities.add(unpacker.unpackDouble());
            }

            plugin.getLogger().info("Received and unpacked velocities: " + velocities);
            return velocities;
        } catch (Exception e) {
            plugin.getLogger().severe("Error in getVelocities: " + e.getMessage());
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public boolean isServerAvailable() {
        try {
            URL url = new URL(serverUrl + "/health");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to connect to Python server: " + e.getMessage());
            return false;
        }
    }
}