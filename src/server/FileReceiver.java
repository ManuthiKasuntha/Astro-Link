package server;

import common.Config;
import common.Log;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

// TCP port 5002: receives sky images. Separate port so image bytes don't mix with text lines.
public class FileReceiver extends Thread {

    public FileReceiver() {
        super("File-Receiver");
    }

    @Override
    public void run() {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(Config.FILE_PORT);
            new File(Config.IMAGE_FOLDER).mkdirs();
            Log.log("File receiver listening on port " + Config.FILE_PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                receiveOneFile(socket);
            }
        } catch (IOException e) {
            Log.log("File receiver error: " + e.getMessage());
        } finally {
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                Log.log("Could not close file server socket: " + e.getMessage());
            }
        }
    }

    // Reads: eventId, fileName, size, then the bytes (same order as ImageSender)
    private void receiveOneFile(Socket socket) {
        FileOutputStream fileOut = null;
        try {
            socket.setSoTimeout(10000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            String eventId = in.readUTF();
            String fileName = in.readUTF();
            long size = in.readLong();
            String safeName = new File(eventId + "_" + fileName).getName();   // no folders allowed
            File file = new File(Config.IMAGE_FOLDER, safeName);
            fileOut = new FileOutputStream(file);

            byte[] buffer = new byte[Config.FILE_BUFFER_SIZE];
            long remaining = size;
            while (remaining > 0) {
                int wanted = (int) Math.min(buffer.length, remaining);
                int count = in.read(buffer, 0, wanted);
                if (count == -1) {
                    throw new IOException("connection closed after " + (size - remaining) + " of " + size + " bytes");
                }
                fileOut.write(buffer, 0, count);
                remaining = remaining - count;
            }
            Log.log("IMAGE saved " + file.getPath() + " (" + size + " bytes from "
                    + socket.getInetAddress().getHostAddress() + ")");
        } catch (IOException e) {
            Log.log("IMAGE transfer failed: " + e.getMessage());
        } finally {
            try {
                if (fileOut != null) {
                    fileOut.close();
                }
                socket.close();
            } catch (IOException e) {
                Log.log("Could not close file transfer: " + e.getMessage());
            }
        }
    }
}
