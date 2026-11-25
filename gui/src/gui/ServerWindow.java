package gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

// Window for the central server. Buttons type the same commands as the terminal version.
public class ServerWindow extends JFrame {

    private final ProcessConsole console = new ProcessConsole();
    private final JButton startButton = new JButton("Start server");
    private final JButton stopButton = new JButton("Stop server");
    private final JLabel status = new JLabel("Server is not running.");
    private final ArrayList<JButton> commandButtons = new ArrayList<>();
    private final JTextField commandField = new JTextField(14);

    public ServerWindow() {
        super("AstroLink - Central Server");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                console.stopGracefully("quit");
                dispose();
            }
        });

        JPanel top = new JPanel(new BorderLayout());
        JPanel startStop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        startStop.add(startButton);
        startStop.add(stopButton);
        startStop.add(status);
        top.add(startStop, BorderLayout.NORTH);
        JLabel ips = new JLabel("  This computer's IP address(es): " + localAddresses()
                + "   - stations type one of these if they cannot find the server.");
        ips.setBorder(BorderFactory.createEmptyBorder(0, 4, 6, 4));
        top.add(ips, BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addCommand(bottom, "List stations", "list");
        addCommand(bottom, "Events", "events");
        addCommand(bottom, "Threads", "threads");
        addCommand(bottom, "Help", "help");
        bottom.add(new JLabel("   Command:"));
        bottom.add(commandField);
        JButton sendButton = new JButton("Send");
        bottom.add(sendButton);
        commandButtons.add(sendButton);
        JButton clearButton = new JButton("Clear screen");
        bottom.add(clearButton);

        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> console.stopGracefully("quit"));
        sendButton.addActionListener(e -> sendTyped());
        commandField.addActionListener(e -> sendTyped());
        clearButton.addActionListener(e -> console.clear());

        add(top, BorderLayout.NORTH);
        add(console.getView(), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setRunning(false);
        setSize(950, 600);
        setLocationByPlatform(true);
    }

    private void addCommand(JPanel panel, String label, String command) {
        JButton button = new JButton(label);
        button.addActionListener(e -> console.send(command, true));
        panel.add(button);
        commandButtons.add(button);
    }

    private void startServer() {
        boolean started = console.start("server.ServerMain", new ProcessConsole.Listener() {
            @Override
            public void onText(String text) {
                if (text.contains("TCP server listening")) {
                    status.setText("Server is RUNNING  (TCP 5000, UDP 5001, files 5002)");
                }
            }

            @Override
            public void onExit() {
                setRunning(false);
                status.setText("Server is not running.");
            }
        });
        if (started) {
            setRunning(true);
            status.setText("Server is starting...");
        }
    }

    private void sendTyped() {
        String text = commandField.getText().trim();
        if (!text.isEmpty()) {
            console.send(text, true);
            commandField.setText("");
        }
    }

    private void setRunning(boolean running) {
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        commandField.setEnabled(running);
        for (JButton button : commandButtons) {
            button.setEnabled(running);
        }
    }

    // All IPv4 addresses of this computer (Wi-Fi, hotspot, Ethernet ...), not only one.
    private static String localAddresses() {
        StringBuilder text = new StringBuilder();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address) {
                        if (text.length() > 0) {
                            text.append(", ");
                        }
                        text.append(address.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            return "unknown";
        }
        return text.length() == 0 ? "127.0.0.1 (no network)" : text.toString();
    }
}
