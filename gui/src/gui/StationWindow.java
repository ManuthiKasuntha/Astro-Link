package gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import common.Config;

// Window for one observation station. Buttons type the station menu options 1-5.
public class StationWindow extends JFrame {

    private final ProcessConsole console = new ProcessConsole();
    private final JComboBox<String> stationChoice = new JComboBox<>();
    private final JTextField serverIpField = new JTextField(12);
    private final JButton connectButton = new JButton("Connect");
    private final JButton exitButton = new JButton("Disconnect");
    private final JButton crashButton = new JButton("Force close (crash)");
    private final JButton simulationButton = new JButton("Start sky simulation");
    private final JLabel status = new JLabel("Not connected.");
    private final ArrayList<JButton> menuButtons = new ArrayList<>();
    private boolean simulationOn = false;

    public StationWindow() {
        super("AstroLink - Station");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                console.stopGracefully("5");   // menu option 5 = Exit (sends BYE)
                dispose();
            }
        });

        for (int i = 0; i < Config.STATION_IDS.length; i++) {
            stationChoice.addItem((i + 1) + ". " + Config.STATION_IDS[i] + " (" + Config.LOCATIONS[i] + ")");
        }
        serverIpField.setToolTipText("Leave empty: the server is found with a UDP broadcast. "
                + "If that fails, this IP is used.");

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Station:"));
        top.add(stationChoice);
        top.add(new JLabel("  Server IP (optional):"));
        top.add(serverIpField);
        top.add(connectButton);
        top.add(exitButton);
        top.add(crashButton);

        JPanel bottom = new JPanel(new BorderLayout());
        JPanel menu = new JPanel(new FlowLayout(FlowLayout.LEFT));
        menu.add(simulationButton);
        menuButtons.add(simulationButton);
        addMenuButton(menu, "Send test alert", "2");
        addMenuButton(menu, "Station info", "3");
        addMenuButton(menu, "Connection + threads", "4");
        JButton clearButton = new JButton("Clear screen");
        menu.add(clearButton);
        bottom.add(menu, BorderLayout.NORTH);
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusBar.add(status);
        bottom.add(statusBar, BorderLayout.SOUTH);

        connectButton.addActionListener(e -> connect());
        exitButton.addActionListener(e -> console.stopGracefully("5"));
        crashButton.addActionListener(e -> console.kill());
        simulationButton.addActionListener(e -> toggleSimulation());
        clearButton.addActionListener(e -> console.clear());

        add(top, BorderLayout.NORTH);
        add(console.getView(), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setState(false, false);
        setSize(950, 560);
        setLocationByPlatform(true);
    }

    private void addMenuButton(JPanel panel, String label, String option) {
        JButton button = new JButton(label);
        button.addActionListener(e -> console.send(option, true));
        panel.add(button);
        menuButtons.add(button);
    }

    private void connect() {
        int number = stationChoice.getSelectedIndex() + 1;
        setTitle("AstroLink - Station " + Config.STATION_IDS[number - 1]);
        boolean started = console.start("station.StationMain", new ProcessConsole.Listener() {
            @Override
            public void onText(String text) {
                if (text.contains("Type the server IP address")) {
                    answerIpQuestion();
                } else if (text.contains("Connected to")) {
                    setState(true, true);
                    status.setText("CONNECTED as " + Config.STATION_IDS[number - 1]);
                }
            }

            @Override
            public void onExit() {
                setState(false, false);
                status.setText("Not connected.");
            }
        });
        if (started) {
            setState(true, false);
            status.setText("Looking for the server...");
            console.send(String.valueOf(number), false);   // answer "Choose your station (1-2)"
        }
    }

    // The broadcast found no server: use the typed IP, or ask for one.
    private void answerIpQuestion() {
        String ip = serverIpField.getText().trim();
        if (ip.isEmpty()) {
            ip = JOptionPane.showInputDialog(this,
                    "No server answered the UDP broadcast.\nType the server IP address (shown in the server window):",
                    "Server IP", JOptionPane.QUESTION_MESSAGE);
        }
        if (ip == null || ip.trim().isEmpty()) {
            console.kill();
            return;
        }
        serverIpField.setText(ip.trim());
        console.send(ip.trim(), true);
    }

    private void toggleSimulation() {
        console.send("1", true);
        simulationOn = !simulationOn;
        simulationButton.setText(simulationOn ? "Stop sky simulation" : "Start sky simulation");
    }

    // running = the station program is started; connected = the server said WELCOME
    private void setState(boolean running, boolean connected) {
        stationChoice.setEnabled(!running);
        serverIpField.setEnabled(!running);
        connectButton.setEnabled(!running);
        exitButton.setEnabled(connected);
        crashButton.setEnabled(running);
        for (JButton button : menuButtons) {
            button.setEnabled(connected);
        }
        if (!running) {
            simulationOn = false;
            simulationButton.setText("Start sky simulation");
        }
    }
}
