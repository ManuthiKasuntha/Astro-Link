package server;

import common.Config;
import common.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

// Starts the server: helper threads, then the TCP accept() loop.
public class ServerMain {

    private static ArrayList<Thread> threads = new ArrayList<>();   // for the "threads" command

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("        ASTRONET CENTRAL SERVER");
        System.out.println("==========================================");
        track(Thread.currentThread());

        StationRegistry registry = new StationRegistry();
        EventManager events = new EventManager();
        startThread(new UdpService(registry));
        startThread(new HeartbeatMonitor(registry));
        startThread(new FileReceiver());
        startThread(new ServerConsole(registry, events));

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(Config.TCP_PORT);
            Log.log("TCP server listening on port " + Config.TCP_PORT + ", this computer's IP: "
                    + InetAddress.getLocalHost().getHostAddress());
            int handlerNumber = 1;
            while (true) {
                Socket socket = serverSocket.accept();   // blocks until a station connects
                Log.log("TCP connection accepted from " + socket.getInetAddress().getHostAddress()
                        + ":" + socket.getPort());
                // one new thread per station
                Thread handler = new Thread(new StationHandler(socket, registry, events), "Handler-" + handlerNumber);
                startThread(handler);
                handlerNumber++;
            }
        } catch (IOException e) {
            Log.log("Server error: " + e.getMessage() + " - is another server already using port "
                    + Config.TCP_PORT + "?");
        } finally {
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                Log.log("Could not close the server socket: " + e.getMessage());
            }
            System.exit(1);
        }
    }

    private static void startThread(Thread thread) {
        track(thread);
        thread.start();
    }

    public static synchronized void track(Thread thread) {
        threads.add(thread);
    }

    public static synchronized void printThreads() {
        System.out.println("---- Server threads (one process, " + threads.size() + " threads) ----");
        for (Thread thread : threads) {
            System.out.println(String.format("  %-18s %s", thread.getName(), thread.getState()));
        }
        // remove finished threads after showing them once
        for (int i = threads.size() - 1; i >= 0; i--) {
            if (threads.get(i).getState() == Thread.State.TERMINATED) {
                threads.remove(i);
            }
        }
    }
}
