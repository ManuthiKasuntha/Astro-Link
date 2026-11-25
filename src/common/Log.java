package common;

import java.time.LocalTime;

// Prints: [HH:mm:ss] [thread-name] message
public class Log {

    // synchronized: lines from different threads never mix
    public static synchronized void log(String message) {
        LocalTime now = LocalTime.now();
        String time = String.format("%02d:%02d:%02d", now.getHour(), now.getMinute(), now.getSecond());
        System.out.println("[" + time + "] [" + Thread.currentThread().getName() + "] " + message);
    }
}
