package server;

import java.io.PrintWriter;

// What the server remembers about one station.
public class StationInfo {

    String stationId;
    String location;
    String ipAddress;
    PrintWriter writer;        // null when the TCP connection is gone
    String status;             // ONLINE or OFFLINE
    long lastHeartbeatTime;
    int lastHeartbeatSeq;      // 0 = no heartbeat yet

    public StationInfo(String stationId, String location, String ipAddress, PrintWriter writer) {
        this.stationId = stationId;
        this.location = location;
        this.ipAddress = ipAddress;
        this.writer = writer;
        this.status = "ONLINE";
        this.lastHeartbeatTime = System.currentTimeMillis();
        this.lastHeartbeatSeq = 0;
    }
}
