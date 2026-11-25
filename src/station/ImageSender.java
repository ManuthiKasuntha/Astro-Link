package station;

import common.Config;
import common.Log;

import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Random;
import javax.imageio.ImageIO;

// Makes a fake sky PNG and sends it to the server on TCP port 5002.
public class ImageSender {

    // Sends: eventId, fileName, size, then the bytes
    public static void sendImage(String serverIp, String eventId, String stationId) {
        File file = new File("sky_" + stationId + ".png");
        Socket socket = null;
        FileInputStream fileIn = null;
        try {
            makeSkyImage(file);
            socket = new Socket(serverIp, Config.FILE_PORT);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(eventId);
            out.writeUTF(stationId + ".png");
            out.writeLong(file.length());
            fileIn = new FileInputStream(file);
            byte[] buffer = new byte[Config.FILE_BUFFER_SIZE];
            int count = fileIn.read(buffer);
            while (count != -1) {
                out.write(buffer, 0, count);
                count = fileIn.read(buffer);
            }
            out.flush();
            Log.log("IMAGE sent for " + eventId + ": " + file.length() + " bytes to port " + Config.FILE_PORT);
        } catch (IOException e) {
            Log.log("IMAGE could not be sent: " + e.getMessage());
        } finally {
            try {
                if (fileIn != null) {
                    fileIn.close();
                }
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                Log.log("Could not close image transfer: " + e.getMessage());
            }
        }
    }

    // Black picture, random white stars, one bright flare in the middle
    private static void makeSkyImage(File file) throws IOException {
        int width = 200;
        int height = 150;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random();
        for (int i = 0; i < 150; i++) {
            image.setRGB(random.nextInt(width), random.nextInt(height), 0xFFFFFF);
        }
        int centerX = width / 2;
        int centerY = height / 2;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -6; dy <= 6; dy++) {
                if (dx * dx + dy * dy <= 36) {
                    image.setRGB(centerX + dx, centerY + dy, 0xFFFFAA);
                }
            }
        }
        ImageIO.write(image, "png", file);
    }
}
