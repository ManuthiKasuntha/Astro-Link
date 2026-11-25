package server;

import common.Config;
import common.Log;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

// List of stations. Used by many threads, so every public method is synchronized.
public class StationRegistry {

    private HashMap<String, StationInfo> stations = new HashMap<>();

    // Returns null if OK, otherwise the reason for rejecting
    public synchronized String register(String stationId, String location, String ip, PrintWriter writer) {
        StationInfo old = stations.get(stationId);
        if (old != null && old.writer != null) {
            return stationId + " is already connected";
        }
        if (countConnected() >= Config.MAX_STATIONS) {
            return "network is full (" + Config.MAX_STATIONS + " stations)";
        }
        stations.put(stationId, new StationInfo(stationId, location, ip, writer));
        return null;
    }

    public synchronized void disconnect(String stationId, boolean saidBye) {
        if (saidBye) {
            stations.remove(stationId);
            return;
        }
        StationInfo station = stations.get(stationId);
        if (station != null) {
            station.writer = null;   // keep it so the monitor can show OFFLINE
        }
    }

    public synchronized void sendTo(String stationId, String line) {
        StationInfo station = stations.get(stationId);
        if (station != null && station.writer != null) {
            station.writer.println(line);
        }
    }

    public synchronized int broadcast(String line) {
        int count = 0;
        for (StationInfo station : stations.values()) {
            if (station.writer != null) {
                station.writer.println(line);
                count++;
            }
        }
        return count;
    }

    // Stations that can answer a FOLLOW_UP
    public synchronized ArrayList<String> getOnlineIdsExcept(String sourceId) {
        ArrayList<String> ids = new ArrayList<>();
        for (StationInfo station : stations.values()) {
            boolean online = station.writer != null && station.status.equals("ONLINE");
            if (online && !station.stationId.equals(sourceId)) {
                ids.add(station.stationId);
            }
        }
        return ids;
    }

    public synchronized void recordHeartbeat(String stationId, int seq) {
        StationInfo station = stations.get(stationId);
        if (station == null) {
            Log.log("HEARTBEAT from unknown station " + stationId + " - ignored");
            return;
        }
        station.lastHeartbeatTime = System.currentTimeMillis();
        if (station.lastHeartbeatSeq == 0) {
            Log.log("HEARTBEAT first from " + stationId + " (seq " + seq + ") - UDP monitoring started");
        } else if (seq > station.lastHeartbeatSeq + 1) {
            int lost = seq - station.lastHeartbeatSeq - 1;
            Log.log("UDP GAP " + stationId + ": expected seq " + (station.lastHeartbeatSeq + 1)
                    + " but got " + seq + " - " + lost + " datagram(s) lost");
        } else if (seq <= station.lastHeartbeatSeq) {
            Log.log("UDP ORDER " + stationId + ": seq " + seq + " arrived after seq "
                    + station.lastHeartbeatSeq + " (late or duplicate)");
            return;
        }
        station.lastHeartbeatSeq = seq;
        if (station.status.equals("OFFLINE")) {
            station.status = "ONLINE";
            Log.log("ONLINE again " + stationId + " - heartbeats are back");
        }
    }

    // Called every 5 s by the Heartbeat-Monitor thread
    public synchronized void checkHeartbeats() {
        long now = System.currentTimeMillis();
        for (StationInfo station : stations.values()) {
            long silentMs = now - station.lastHeartbeatTime;
            if (station.status.equals("ONLINE") && silentMs > Config.HEARTBEAT_TIMEOUT_MS) {
                station.status = "OFFLINE";
                Log.log("OFFLINE " + station.stationId + " - no heartbeat for " + (silentMs / 1000) + " s");
            }
        }
    }

    public synchronized void printList() {
        System.out.println("---- Stations (" + countConnected() + " connected over TCP) ----");
        if (stations.isEmpty()) {
            System.out.println("  (no stations yet)");
        }
        long now = System.currentTimeMillis();
        for (StationInfo station : stations.values()) {
            String tcp = (station.writer != null) ? "TCP connected" : "TCP closed";
            long seconds = (now - station.lastHeartbeatTime) / 1000;
            System.out.println("  " + station.stationId + "  " + station.location + "  " + station.ipAddress
                    + "  " + station.status + "  " + tcp + "  last heartbeat " + seconds
                    + " s ago (seq " + station.lastHeartbeatSeq + ")");
        }
    }

    private int countConnected() {
        int count = 0;
        for (StationInfo station : stations.values()) {
            if (station.writer != null) {
                count++;
            }
        }
        return count;
    }
}
