package server;

import common.Config;
import common.Log;
import common.Protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

// UDP port 5001: answers DISCOVER and records HEARTBEATs.
public class UdpService extends Thread {

    private StationRegistry registry;

    public UdpService(StationRegistry registry) {
        super("UDP-Service");
        this.registry = registry;
    }

    @Override
    public void run() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(Config.UDP_PORT);
            Log.log("UDP service listening on port " + Config.UDP_PORT + " (discovery + heartbeats)");
            byte[] buffer = new byte[Config.UDP_BUFFER_SIZE];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);   // blocks until a datagram arrives
                String text = new String(packet.getData(), 0, packet.getLength());
                handle(socket, packet, text);
            }
        } catch (IOException e) {
            Log.log("UDP error: " + e.getMessage() + " - is port " + Config.UDP_PORT + " already in use?");
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private void handle(DatagramSocket socket, DatagramPacket packet, String text) throws IOException {
        String[] parts = Protocol.parse(text);
        if (parts[0].equals(Protocol.DISCOVER)) {
            String reply = Protocol.build(Protocol.SERVER_HERE, String.valueOf(Config.TCP_PORT));
            byte[] data = reply.getBytes();
            // reply to the sender's address and port
            DatagramPacket answer = new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort());
            socket.send(answer);
            Log.log("DISCOVER from " + packet.getAddress().getHostAddress() + ":" + packet.getPort()
                    + " - replied " + reply);
        } else if (parts[0].equals(Protocol.HEARTBEAT) && parts.length >= 3) {
            try {
                registry.recordHeartbeat(parts[1], Integer.parseInt(parts[2]));
            } catch (NumberFormatException e) {
                Log.log("Bad heartbeat ignored: " + text);
            }
        } else {
            Log.log("Unknown UDP datagram ignored: " + text);
        }
    }
}
