package server;

import common.Config;
import common.Log;

// Waits 15 s, then closes the event as INCONCLUSIVE if answers are missing.
public class VerificationTimer implements Runnable {

    private String eventId;
    private StationRegistry registry;
    private EventManager events;

    public VerificationTimer(String eventId, StationRegistry registry, EventManager events) {
        this.eventId = eventId;
        this.registry = registry;
        this.events = events;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(Config.VERIFY_TIMEOUT_MS);
        } catch (InterruptedException e) {
            Log.log("Timer for " + eventId + " was interrupted");
            return;
        }
        String resultLine = events.closeEvent(eventId, "timeout");   // null if already decided
        if (resultLine != null) {
            int count = registry.broadcast(resultLine);
            Log.log("RESULT broadcast to " + count + " station(s) after TIMEOUT: " + resultLine);
        }
    }
}
