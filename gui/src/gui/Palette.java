package gui;

import java.awt.Color;

// Colours for the output lines, so ALERT / VERIFY / RESULT / OFFLINE stand out in a demo.
public class Palette {

    public static final Color NORMAL = new Color(215, 220, 230);
    public static final Color ECHO = new Color(130, 140, 160);
    public static final Color ERROR = new Color(255, 105, 105);
    public static final Color GOOD = new Color(110, 220, 130);
    public static final Color ALERT = new Color(255, 170, 70);
    public static final Color FOLLOW = new Color(100, 200, 255);
    public static final Color RESULT = new Color(255, 220, 90);
    public static final Color WARN = new Color(230, 210, 120);

    public static Color colorFor(String text) {
        if (text.contains("ERROR") || text.contains("OFFLINE") || text.contains("DISCONNECTED")
                || text.contains("Could not") || text.contains("refused") || text.contains("Lost the connection")
                || text.contains("No server answered")) {
            return ERROR;
        }
        if (text.contains("RESULT") || text.contains("LIKELY_CONFIRMED") || text.contains("INCONCLUSIVE")
                || text.contains("------")) {
            return RESULT;
        }
        if (text.contains("ALERT")) {
            return ALERT;
        }
        if (text.contains("FOLLOW_UP") || text.contains("VERIFY")) {
            return FOLLOW;
        }
        if (text.contains("UDP GAP") || text.contains("UDP ORDER")) {
            return WARN;
        }
        if (text.contains("REGISTERED") || text.contains("Connected to") || text.contains("ONLINE")
                || text.contains("Server found") || text.contains("listening")) {
            return GOOD;
        }
        if (text.contains("HEARTBEAT")) {
            return ECHO;
        }
        return NORMAL;
    }
}
