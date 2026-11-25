package server;

import common.Config;
import common.Log;

// Every 5 s: marks stations OFFLINE after 15 s without a heartbeat.
public class HeartbeatMonitor extends Thread {

    private StationRegistry registry;

    public HeartbeatMonitor(StationRegistry registry) {
        super("Heartbeat-Monitor");
        this.registry = registry;
    }

    @Override
    public void run() {
        Log.log("Heartbeat monitor started: OFFLINE after " + (Config.HEARTBEAT_TIMEOUT_MS / 1000)
                + " s without a heartbeat");
        while (true) {
            try {
                Thread.sleep(Config.MONITOR_INTERVAL_MS);
            } catch (InterruptedException e) {
                Log.log("Heartbeat monitor stopped");
                return;
            }
            registry.checkHeartbeats();
        }
    }
}
