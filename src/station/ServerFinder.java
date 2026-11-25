package station;

import common.Config;
import common.Protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Scanner;

// Finds the server with a UDP broadcast. If no reply in 2 s, the user types the IP.
public class ServerFinder {

    public static String findServer(Scanner scanner) {
        System.out.println("Looking for the AstroNet server with a UDP broadcast...");
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(Config.DISCOVERY_TIMEOUT_MS);
            byte[] data = Protocol.DISCOVER.getBytes();
            InetAddress everyone = InetAddress.getByName("255.255.255.255");
            InetAddress thisComputer = InetAddress.getByName("127.0.0.1");   // server on the same laptop
            socket.send(new DatagramPacket(data, data.length, everyone, Config.UDP_PORT));
            socket.send(new DatagramPacket(data, data.length, thisComputer, Config.UDP_PORT));

            byte[] buffer = new byte[Config.UDP_BUFFER_SIZE];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            socket.receive(reply);   // waits max 2 s
            String text = new String(reply.getData(), 0, reply.getLength());
            if (text.startsWith(Protocol.SERVER_HERE)) {
                String ip = reply.getAddress().getHostAddress();   // the reply came from the server
                System.out.println("Server found at " + ip + " (reply: " + text + ")");
                return ip;
            }
            System.out.println("Unexpected reply: " + text);
        } catch (SocketTimeoutException e) {
            System.out.println("No server answered within " + (Config.DISCOVERY_TIMEOUT_MS / 1000) + " s.");
        } catch (IOException e) {
            System.out.println("UDP discovery failed: " + e.getMessage());
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
        System.out.print("Type the server IP address (shown on the server screen): ");
        if (!scanner.hasNextLine()) {
            System.exit(0);
        }
        return scanner.nextLine().trim();
    }
}
