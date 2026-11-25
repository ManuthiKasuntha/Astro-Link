package server;

import java.util.Scanner;

// Server keyboard commands (own thread, because main is busy in accept()).
public class ServerConsole extends Thread {

    private StationRegistry registry;
    private EventManager events;

    public ServerConsole(StationRegistry registry, EventManager events) {
        super("Server-Console");
        this.registry = registry;
        this.events = events;
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        printHelp();
        while (scanner.hasNextLine()) {
            String command = scanner.nextLine().trim().toLowerCase();
            if (command.equals("list")) {
                registry.printList();
            } else if (command.equals("events")) {
                events.printEvents();
            } else if (command.equals("threads")) {
                ServerMain.printThreads();
            } else if (command.equals("help")) {
                printHelp();
            } else if (command.equals("quit")) {
                System.out.println("Server stopping.");
                System.exit(0);
            } else if (!command.isEmpty()) {
                System.out.println("Unknown command '" + command + "'. Type help.");
            }
        }
        scanner.close();
    }

    private void printHelp() {
        System.out.println("Commands: list | events | threads | help | quit");
    }
}
