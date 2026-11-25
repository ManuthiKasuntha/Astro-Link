package gui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

// Start window of the optional GUI. The networking code in src/ is not changed:
// each window runs the normal console program and shows its output.
//
//   java -cp <classes> gui.AstroLinkGui            -> start window
//   java -cp <classes> gui.AstroLinkGui server     -> server window directly
//   java -cp <classes> gui.AstroLinkGui station    -> station window directly
public class AstroLinkGui {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // keep the default look
            }
            String mode = args.length > 0 ? args[0].toLowerCase() : "";
            if (mode.equals("server")) {
                new ServerWindow().setVisible(true);
            } else if (mode.equals("station")) {
                new StationWindow().setVisible(true);
            } else {
                showLauncher();
            }
        });
    }

    private static void showLauncher() {
        JFrame frame = new JFrame("AstroLink");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JLabel title = new JLabel("AstroNet - Astronomical Event Detection Network", JLabel.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JLabel hint = new JLabel("<html><center>Open the Server on ONE computer, a Station on the others.<br>"
                + "You can open several stations here to test on one computer.</center></html>", JLabel.CENTER);

        JButton serverButton = new JButton("Open Server window");
        JButton stationButton = new JButton("Open Station window");
        serverButton.addActionListener(e -> new ServerWindow().setVisible(true));
        stationButton.addActionListener(e -> new StationWindow().setVisible(true));

        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
        buttons.add(serverButton);
        buttons.add(stationButton);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        content.add(title, BorderLayout.NORTH);
        content.add(hint, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        frame.setContentPane(content);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
