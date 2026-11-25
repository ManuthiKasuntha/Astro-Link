package common;

// All ports, timeouts and limits in one place.
public class Config {

    public static final int TCP_PORT = 5000;   // text messages
    public static final int UDP_PORT = 5001;   // discovery + heartbeats
    public static final int FILE_PORT = 5002;  // sky images

    public static final int MAX_STATIONS = 2;
    public static final int UDP_BUFFER_SIZE = 256;
    public static final int FILE_BUFFER_SIZE = 4096;

    public static final int VERIFY_TIMEOUT_MS = 15000;
    public static final int DISCOVERY_TIMEOUT_MS = 2000;
    public static final int HEARTBEAT_INTERVAL_MS = 5000;
    public static final int HEARTBEAT_TIMEOUT_MS = 15000;
    public static final int MONITOR_INTERVAL_MS = 5000;
    public static final int OBSERVATION_INTERVAL_MS = 5000;

    public static final double BASELINE_BRIGHTNESS = 100.0;
    public static final double ALERT_FACTOR = 3.0;        // alert if brightness > 3 x baseline
    public static final int FLARE_PERCENT = 15;
    public static final int HEARTBEAT_DROP_PERCENT = 10;  // skip some heartbeats to show "UDP GAP" (0 = off)

    public static final String IMAGE_FOLDER = "received_images";

    public static final String[] STATION_IDS = {"ASTRO-01", "ASTRO-02"};
    public static final String[] LOCATIONS = {"Sri Lanka", "India"};
}
