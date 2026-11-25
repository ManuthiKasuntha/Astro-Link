package station;

import common.Log;
import common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Random;

// Reads server messages at any time (main thread is busy with the menu).
public class ServerListener implements Runnable {

    private StationMain station;
    private BufferedReader in;
    private Random random = new Random();

    public ServerListener(StationMain station, BufferedReader in) {
        this.station = station;
        this.in = in;
    }

    @Override
    public void run() {
        try {
            while (true) {
                String line = in.readLine();   // blocks until the server sends a line
                if (line == null) {
                    break;   // server closed the connection
                }
                handle(line);
            }
        } catch (IOException e) {
            if (!station.isLeaving()) {
                Log.log("Connection error: " + e.getMessage());
            }
        }
        if (!station.isLeaving()) {
            Log.log("Lost the connection to the server - is it still running? Station stops.");
            System.exit(1);
        }
    }

    private void handle(String line) {
        String[] parts = Protocol.parse(line);
        if (parts[0].equals(Protocol.ALERT_OK) && parts.length >= 2) {
            Log.log("Server created " + parts[1] + " for our alert - sending the sky image");
            ImageSender.sendImage(station.getServerIp(), parts[1], station.getStationId());
        } else if (parts[0].equals(Protocol.FOLLOW_UP) && parts.length >= 4) {
            observe(parts[1], parts[2], parts[3]);
        } else if (parts[0].equals(Protocol.RESULT) && parts.length >= 4) {
            System.out.println();
            System.out.println("------------------------------------------");
            System.out.println("  FINAL RESULT " + parts[1] + ": " + parts[2]);
            System.out.println("  " + parts[3]);
            System.out.println("------------------------------------------");
        } else if (parts[0].equals(Protocol.ERROR)) {
            Log.log("Server says ERROR: " + line);
        } else {
            Log.log("Unknown message from server: " + line);
        }
    }

    // Simulated observation: random answer after 1-3 s
    private void observe(String eventId, String ra, String dec) {
        Log.log("FOLLOW_UP " + eventId + ": pointing telescope at RA " + ra + " Dec " + dec + " ...");
        try {
            Thread.sleep(1000 + random.nextInt(2000));
        } catch (InterruptedException e) {
            return;
        }
        String answer;
        String note;
        int roll = random.nextInt(100);
        if (roll < 60) {
            answer = Protocol.CONFIRMED;
            note = "brightness " + Protocol.number(300 + random.nextDouble() * 200);
        } else if (roll < 85) {
            answer = Protocol.NOT_DETECTED;
            note = "brightness " + Protocol.number(95 + random.nextDouble() * 10);
        } else {
            answer = Protocol.UNAVAILABLE;
            String[] reasons = {"CLOUDY_SKY", "DAYTIME", "BELOW_HORIZON"};
            note = reasons[random.nextInt(reasons.length)];
        }
        station.send(Protocol.build(Protocol.VERIFY, eventId, station.getStationId(), answer, note));
        Log.log("VERIFY sent for " + eventId + ": " + answer + " (" + note + ")");
    }
}
