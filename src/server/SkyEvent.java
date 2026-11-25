package server;

import java.util.ArrayList;
import java.util.HashMap;

// One reported brightening and the answers for it.
public class SkyEvent {

    String eventId;
    String sourceId;
    double ra;
    double dec;
    double brightness;
    double baseline;
    ArrayList<String> askedIds;
    HashMap<String, String> answers = new HashMap<>();   // station ID, answer
    int confirmedCount = 0;
    boolean closed = false;
    String result = "WAITING";
    String summary = "";

    public SkyEvent(String eventId, String sourceId, double ra, double dec,
                    double brightness, double baseline, ArrayList<String> askedIds) {
        this.eventId = eventId;
        this.sourceId = sourceId;
        this.ra = ra;
        this.dec = dec;
        this.brightness = brightness;
        this.baseline = baseline;
        this.askedIds = askedIds;
    }
}
