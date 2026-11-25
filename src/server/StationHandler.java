package server;

import common.Log;
import common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;

// Talks to ONE station over TCP. One thread per station.
public class StationHandler implements Runnable {

    private Socket socket;
    private StationRegistry registry;
    private EventManager events;
    private PrintWriter out;
    private String stationId = null;
    private boolean saidBye = false;

    public StationHandler(Socket socket, StationRegistry registry, EventManager events) {
        this.socket = socket;
        this.registry = registry;
        this.events = events;
    }

    @Override
    public void run() {
        String ip = socket.getInetAddress().getHostAddress();
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            String firstLine = in.readLine();   // must be REGISTER
            if (!register(firstLine, ip)) {
                return;
            }
            while (true) {
                String line = in.readLine();   // blocks until the station sends a line
                if (line == null) {
                    break;   // station closed the connection
                }
                if (!handleMessage(line)) {
                    break;   // BYE
                }
            }
        } catch (IOException e) {
            Log.log("Connection to " + nameOrIp(ip) + " lost: " + e.getMessage());
        } finally {
            if (stationId != null) {
                registry.disconnect(stationId, saidBye);
                if (saidBye) {
                    Log.log("LEFT " + stationId + " (said BYE)");
                } else {
                    Log.log("DISCONNECTED " + stationId + " (connection closed without BYE)");
                }
            }
            try {
                socket.close();
            } catch (IOException e) {
                Log.log("Could not close socket: " + e.getMessage());
            }
        }
    }

    private boolean register(String line, String ip) {
        if (line == null) {
            return false;
        }
        String[] parts = Protocol.parse(line);
        if (!parts[0].equals(Protocol.REGISTER) || parts.length < 3) {
            out.println(Protocol.build(Protocol.ERROR, "first message must be REGISTER|id|location"));
            Log.log("REJECTED " + ip + ": bad first message '" + line + "'");
            return false;
        }
        String error = registry.register(parts[1], parts[2], ip, out);
        if (error != null) {
            out.println(Protocol.build(Protocol.ERROR, error));
            Log.log("REJECTED " + parts[1] + " from " + ip + ": " + error);
            return false;
        }
        stationId = parts[1];
        out.println(Protocol.build(Protocol.WELCOME, stationId));
        Log.log("REGISTERED " + stationId + " (" + parts[2] + ") from " + ip);
        return true;
    }

    // Returns false when the station said BYE
    private boolean handleMessage(String line) {
        String[] parts = Protocol.parse(line);
        try {
            if (parts[0].equals(Protocol.ALERT) && parts.length >= 6) {
                handleAlert(parts);
            } else if (parts[0].equals(Protocol.VERIFY) && parts.length >= 5) {
                handleVerify(parts);
            } else if (parts[0].equals(Protocol.BYE)) {
                saidBye = true;
                return false;
            } else {
                Log.log("UNKNOWN message from " + stationId + ": " + line);
                out.println(Protocol.build(Protocol.ERROR, "unknown message"));
            }
        } catch (NumberFormatException e) {
            Log.log("BAD numbers from " + stationId + ": " + line);
            out.println(Protocol.build(Protocol.ERROR, "bad number in message"));
        }
        return true;
    }

    // ALERT: create an event and ask the other stations
    private void handleAlert(String[] parts) {
        double ra = Double.parseDouble(parts[2]);
        double dec = Double.parseDouble(parts[3]);
        double brightness = Double.parseDouble(parts[4]);
        double baseline = Double.parseDouble(parts[5]);
        ArrayList<String> others = registry.getOnlineIdsExcept(stationId);
        String eventId = events.createEvent(stationId, ra, dec, brightness, baseline, others);

        Log.log("ALERT " + eventId + " from " + stationId + ": RA " + parts[2] + " Dec " + parts[3]
                + " brightness " + parts[4] + " (" + Protocol.number(brightness / baseline) + " x baseline)");
        registry.sendTo(stationId, Protocol.build(Protocol.ALERT_OK, eventId));

        String followUp = Protocol.build(Protocol.FOLLOW_UP, eventId, parts[2], parts[3]);
        for (String otherId : others) {
            registry.sendTo(otherId, followUp);
            Log.log("FOLLOW_UP " + eventId + " sent to " + otherId);
        }
        if (others.isEmpty()) {
            publish(events.closeEvent(eventId, "no other station online"));
            return;
        }
        // timer thread waits 15 s, this thread keeps reading
        Thread timer = new Thread(new VerificationTimer(eventId, registry, events), "Timer-" + eventId);
        ServerMain.track(timer);
        timer.start();
    }

    private void handleVerify(String[] parts) {
        Log.log("VERIFY " + parts[1] + " from " + stationId + ": " + parts[3] + " (" + parts[4] + ")");
        publish(events.addAnswer(parts[1], stationId, parts[3]));
    }

    private void publish(String resultLine) {
        if (resultLine == null) {
            return;
        }
        int count = registry.broadcast(resultLine);
        Log.log("RESULT broadcast to " + count + " station(s): " + resultLine);
    }

    private String nameOrIp(String ip) {
        if (stationId != null) {
            return stationId;
        }
        return ip;
    }
}
