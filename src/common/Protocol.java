package common;

// Message names. Every message is one line: TYPE|field1|field2|...
public class Protocol {

    // TCP (port 5000)
    public static final String REGISTER = "REGISTER";   // REGISTER|id|location
    public static final String WELCOME = "WELCOME";     // WELCOME|id
    public static final String ERROR = "ERROR";         // ERROR|reason
    public static final String ALERT = "ALERT";         // ALERT|id|ra|dec|brightness|baseline
    public static final String ALERT_OK = "ALERT_OK";   // ALERT_OK|eventId
    public static final String FOLLOW_UP = "FOLLOW_UP"; // FOLLOW_UP|eventId|ra|dec
    public static final String VERIFY = "VERIFY";       // VERIFY|eventId|id|answer|note
    public static final String RESULT = "RESULT";       // RESULT|eventId|status|summary
    public static final String BYE = "BYE";             // BYE|id

    // UDP (port 5001)
    public static final String DISCOVER = "DISCOVER_ASTRONET";
    public static final String SERVER_HERE = "ASTRONET_SERVER";  // ASTRONET_SERVER|5000
    public static final String HEARTBEAT = "HEARTBEAT";          // HEARTBEAT|id|seq

    public static final String CONFIRMED = "CONFIRMED";
    public static final String NOT_DETECTED = "NOT_DETECTED";
    public static final String UNAVAILABLE = "UNAVAILABLE";

    public static final String LIKELY_CONFIRMED = "LIKELY_CONFIRMED";
    public static final String INCONCLUSIVE = "INCONCLUSIVE";

    // Joins the fields with '|'
    public static String build(String type, String... fields) {
        String line = type;
        for (int i = 0; i < fields.length; i++) {
            line = line + "|" + fields[i].replace("|", "/");
        }
        return line;
    }

    // Splits a line into fields ('|' must be escaped in split)
    public static String[] parse(String line) {
        return line.split("\\|");
    }

    // Number with 2 decimals and always a '.'
    public static String number(double value) {
        return String.valueOf(Math.round(value * 100) / 100.0);
    }
}
