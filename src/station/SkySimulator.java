package station;

import common.Config;
import common.Log;
import common.Protocol;

import java.util.Random;

// Fake telescope: one reading every 5 s, sends ALERT if brightness > 3 x baseline.
public class SkySimulator implements Runnable {

    private StationMain station;
    private Random random = new Random();
    private volatile boolean running = true;   // volatile: set by the menu thread, read by this thread

    public SkySimulator(StationMain station) {
        this.station = station;
    }

    public void stopRunning() {
        running = false;
    }

    @Override
    public void run() {
        Log.log("Sky simulation started: one observation every " + (Config.OBSERVATION_INTERVAL_MS / 1000) + " s");
        int count = 0;
        while (running) {
            try {
                Thread.sleep(Config.OBSERVATION_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
            if (!running) {
                break;
            }
            count++;
            observeOnce(count);
        }
        Log.log("Sky simulation stopped after " + count + " observations");
    }

    private void observeOnce(int count) {
        double ra = random.nextDouble() * 360;
        double dec = random.nextDouble() * 180 - 90;
        double baseline = Config.BASELINE_BRIGHTNESS;
        double brightness;
        if (random.nextInt(100) < Config.FLARE_PERCENT) {
            brightness = 300 + random.nextDouble() * 200;   // flare
        } else {
            brightness = baseline - 5 + random.nextDouble() * 10;   // normal
        }
        if (brightness > Config.ALERT_FACTOR * baseline) {
            Log.log("Observation #" + count + ": brightness " + Protocol.number(brightness)
                    + " - SUDDEN BRIGHTENING, sending ALERT");
            station.sendAlert(ra, dec, brightness, baseline);
        } else {
            Log.log("Observation #" + count + ": brightness " + Protocol.number(brightness) + " (normal)");
        }
    }
}
