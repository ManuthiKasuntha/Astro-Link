package station;

import common.Config;
import common.Log;
import common.Protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;

// Sends "HEARTBEAT|id|seq" over UDP every 5 s.
public class HeartbeatSender extends Thread {

    private String serverIp;
    private String stationId;
    private Random random = new Random();

    public HeartbeatSender(String serverIp, String stationId) {
        super("Heartbeat-Sender");
        this.serverIp = serverIp;
        this.stationId = stationId;
    }

    @Override
    public void run() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            InetAddress server = InetAddress.getByName(serverIp);
            int seq = 1;
            while (true) {
                if (random.nextInt(100) < Config.HEARTBEAT_DROP_PERCENT) {
                    Log.log("(simulated UDP loss) heartbeat seq " + seq + " not sent");
                } else {
                    byte[] data = Protocol.build(Protocol.HEARTBEAT, stationId, String.valueOf(seq)).getBytes();
                    DatagramPacket packet = new DatagramPacket(data, data.length, server, Config.UDP_PORT);
                    socket.send(packet);   // no guarantee it arrives
                }
                seq++;
                Thread.sleep(Config.HEARTBEAT_INTERVAL_MS);
            }
        } catch (IOException e) {
            Log.log("Heartbeat error: " + e.getMessage());
        } catch (InterruptedException e) {
            Log.log("Heartbeat sender stopped");
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }
}
