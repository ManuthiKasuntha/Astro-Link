package server;

import common.Protocol;

import java.util.ArrayList;

// Creates events, collects answers, decides the result. synchronized: many threads use it.
public class EventManager {

    private ArrayList<SkyEvent> events = new ArrayList<>();
    private int nextNumber = 1;

    public synchronized String createEvent(String sourceId, double ra, double dec, double brightness,
                                           double baseline, ArrayList<String> askedIds) {
        String eventId = String.format("EVT-%04d", nextNumber);
        nextNumber++;
        events.add(new SkyEvent(eventId, sourceId, ra, dec, brightness, baseline, askedIds));
        return eventId;
    }

    // Returns the RESULT line when the last answer arrives, otherwise null
    public synchronized String addAnswer(String eventId, String stationId, String answer) {
        SkyEvent event = findEvent(eventId);
        if (event == null || event.closed) {
            return null;
        }
        if (!event.askedIds.contains(stationId) || event.answers.containsKey(stationId)) {
            return null;
        }
        event.answers.put(stationId, answer);
        if (answer.equals(Protocol.CONFIRMED)) {
            event.confirmedCount++;
        }
        if (event.answers.size() < event.askedIds.size()) {
            return null;
        }
        int asked = event.askedIds.size();
        if (event.confirmedCount * 2 >= asked) {   // at least 50% confirmed
            event.result = Protocol.LIKELY_CONFIRMED;
        } else {
            event.result = Protocol.INCONCLUSIVE;
        }
        event.summary = event.confirmedCount + " of " + asked + " stations confirmed";
        event.closed = true;
        return resultLine(event);
    }

    // Timeout or nobody to ask: INCONCLUSIVE. Returns null if already decided.
    public synchronized String closeEvent(String eventId, String reason) {
        SkyEvent event = findEvent(eventId);
        if (event == null || event.closed) {
            return null;
        }
        event.result = Protocol.INCONCLUSIVE;
        event.summary = reason + ": " + event.answers.size() + " of " + event.askedIds.size()
                + " stations answered, " + event.confirmedCount + " confirmed";
        event.closed = true;
        return resultLine(event);
    }

    public synchronized void printEvents() {
        System.out.println("---- Events (" + events.size() + ") ----");
        for (SkyEvent event : events) {
            System.out.println("  " + event.eventId + "  from " + event.sourceId
                    + "  RA " + Protocol.number(event.ra) + "  Dec " + Protocol.number(event.dec)
                    + "  brightness " + Protocol.number(event.brightness) + "  " + event.result
                    + "  " + event.summary);
            System.out.println("      asked " + event.askedIds + "  answers " + event.answers);
        }
    }

    private SkyEvent findEvent(String eventId) {
        for (SkyEvent event : events) {
            if (event.eventId.equals(eventId)) {
                return event;
            }
        }
        return null;
    }

    private String resultLine(SkyEvent event) {
        return Protocol.build(Protocol.RESULT, event.eventId, event.result, event.summary);
    }
}
