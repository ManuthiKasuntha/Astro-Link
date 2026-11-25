package station;

import common.Config;
import common.Log;
import common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;
import java.util.Scanner;

// One observation station: find server (UDP), connect (TCP), start threads, show menu.
public class StationMain {

    private String stationId;
    private String location;
    private String serverIp;
    private Socket socket;
    private PrintWriter out;
    private Thread listenerThread;
    private HeartbeatSender heartbeatSender;
    private SkySimulator simulator;
    private Thread simulatorThread;
    private int alertsSent = 0;
    private boolean leaving = false;

    public StationMain(String stationId, String location) {
        this.stationId = stationId;
        this.location = location;
    }

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("        ASTRONET OBSERVATION STATION");
        System.out.println("==========================================");
        Scanner scanner = new Scanner(System.in);
        int choice = chooseStation(scanner);
        StationMain station = new StationMain(Config.STATION_IDS[choice], Config.LOCATIONS[choice]);
        String serverIp = ServerFinder.findServer(scanner);
        if (station.connect(serverIp)) {
            station.runMenu(scanner);
        }
        scanner.close();
    }

    private static int chooseStation(Scanner scanner) {
        for (int i = 0; i < Config.STATION_IDS.length; i++) {
            System.out.println("  " + (i + 1) + ". " + Config.STATION_IDS[i] + " (" + Config.LOCATIONS[i] + ")");
        }
        while (true) {
            System.out.print("Choose your station (1-" + Config.STATION_IDS.length + "): ");
            if (!scanner.hasNextLine()) {
                System.exit(0);
            }
            String text = scanner.nextLine().trim();
            try {
                int number = Integer.parseInt(text);
                if (number >= 1 && number <= Config.STATION_IDS.length) {
                    return number - 1;
                }
            } catch (NumberFormatException e) {
                // ask again
            }
            System.out.println("Please type a number from 1 to " + Config.STATION_IDS.length + ".");
        }
    }

    // TCP connect + REGISTER. Returns true when the server says WELCOME.
    private boolean connect(String serverIp) {
        this.serverIp = serverIp;
        try {
            socket = new Socket(serverIp, Config.TCP_PORT);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println(Protocol.build(Protocol.REGISTER, stationId, location));
            String reply = in.readLine();
            if (reply == null || !reply.startsWith(Protocol.WELCOME)) {
                System.out.println("Server refused us: " + reply);
                socket.close();
                return false;
            }
            Log.log("Connected to " + serverIp + ":" + Config.TCP_PORT + " as " + stationId + " - " + reply);
            listenerThread = new Thread(new ServerListener(this, in), "Server-Listener");
            listenerThread.start();
            heartbeatSender = new HeartbeatSender(serverIp, stationId);
            heartbeatSender.start();
            return true;
        } catch (IOException e) {
            System.out.println("Could not connect to " + serverIp + ":" + Config.TCP_PORT
                    + " (" + e.getMessage() + ") - is the server running? Is the firewall open?");
            return false;
        }
    }

    private void runMenu(Scanner scanner) {
        while (true) {
            System.out.println();
            System.out.println("--- " + stationId + " menu ---");
            System.out.println("1. Start/stop sky simulation");
            System.out.println("2. Send test alert (with sky image)");
            System.out.println("3. Station info");
            System.out.println("4. Connection + thread status");
            System.out.println("5. Exit");
            System.out.print("Select option: ");
            if (!scanner.hasNextLine()) {   // input closed
                leave();
                return;
            }
            String option = scanner.nextLine().trim();
            if (option.equals("1")) {
                toggleSimulation();
            } else if (option.equals("2")) {
                Random random = new Random();
                sendAlert(random.nextDouble() * 360, random.nextDouble() * 180 - 90, 450.5, Config.BASELINE_BRIGHTNESS);
            } else if (option.equals("3")) {
                printInfo();
            } else if (option.equals("4")) {
                printStatus();
            } else if (option.equals("5")) {
                leave();
                return;
            }
        }
    }

    private void toggleSimulation() {
        if (simulatorThread != null && simulatorThread.isAlive()) {
            simulator.stopRunning();
            System.out.println("Stopping the sky simulation (it ends after its current sleep).");
            return;
        }
        // a thread can only be started once, so make a new one
        simulator = new SkySimulator(this);
        simulatorThread = new Thread(simulator, "Sky-Simulator");
        simulatorThread.start();
    }

    // synchronized: menu and simulator threads both send alerts
    public synchronized void sendAlert(double ra, double dec, double brightness, double baseline) {
        alertsSent++;
        send(Protocol.build(Protocol.ALERT, stationId, Protocol.number(ra), Protocol.number(dec),
                Protocol.number(brightness), Protocol.number(baseline)));
        Log.log("ALERT sent: RA " + Protocol.number(ra) + " Dec " + Protocol.number(dec)
                + " brightness " + Protocol.number(brightness));
    }

    // synchronized: several threads write to the same socket
    public synchronized void send(String line) {
        out.println(line);
        if (out.checkError()) {
            Log.log("Could not send to the server: " + line);
        }
    }

    private void printInfo() {
        System.out.println("Station ID : " + stationId);
        System.out.println("Location   : " + location);
        System.out.println("Server     : " + serverIp);
        System.out.println("Alerts sent: " + alertsSent);
        System.out.println("Simulation : " + (simulatorThread != null && simulatorThread.isAlive() ? "running" : "stopped"));
    }

    private void printStatus() {
        System.out.println("TCP  : local port " + socket.getLocalPort() + " to " + serverIp + ":" + Config.TCP_PORT
                + "  connected=" + socket.isConnected() + "  closed=" + socket.isClosed());
        System.out.println("UDP  : heartbeat to " + serverIp + ":" + Config.UDP_PORT + " every "
                + (Config.HEARTBEAT_INTERVAL_MS / 1000) + " s");
        System.out.println("Threads of this station process:");
        System.out.println("  main              " + Thread.currentThread().getState() + "  (this menu)");
        System.out.println("  Server-Listener   " + listenerThread.getState() + "  (blocked in readLine())");
        System.out.println("  Heartbeat-Sender  " + heartbeatSender.getState() + "  (sleeps between heartbeats)");
        if (simulatorThread == null) {
            System.out.println("  Sky-Simulator     not created yet (option 1)");
        } else {
            System.out.println("  Sky-Simulator     " + simulatorThread.getState());
        }
    }

    private void leave() {
        setLeaving();
        if (simulator != null) {
            simulator.stopRunning();
        }
        send(Protocol.build(Protocol.BYE, stationId));
        try {
            listenerThread.join(3000);   // wait until the server closes the connection
            socket.close();
        } catch (InterruptedException e) {
            Log.log("Interrupted while leaving");
        } catch (IOException e) {
            Log.log("Could not close socket: " + e.getMessage());
        }
        System.out.println("Goodbye from " + stationId + ".");
        System.exit(0);
    }

    private synchronized void setLeaving() {
        leaving = true;
    }

    public synchronized boolean isLeaving() {
        return leaving;
    }

    public String getStationId() {
        return stationId;
    }

    public String getServerIp() {
        return serverIp;
    }
}
