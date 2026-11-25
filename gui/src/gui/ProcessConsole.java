package gui;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

// Runs one of the UNCHANGED console programs (server.ServerMain or station.StationMain)
// as a child process. Its output is shown in a coloured text pane, and send() types a
// line into it, exactly like a user typing in the terminal.
public class ProcessConsole {

    // Called on the Swing thread for every piece of output and when the program ends.
    public interface Listener {
        void onText(String text);
        void onExit();
    }

    private static final ArrayList<Process> ALL = new ArrayList<>();

    static {
        // never leave a server/station running in the background when the GUI closes
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (ALL) {
                for (Process p : ALL) {
                    p.destroy();
                }
            }
        }));
    }

    private final JTextPane pane = new JTextPane();
    private final JScrollPane scroll = new JScrollPane(pane);
    private Process process;
    private PrintWriter input;
    private Listener listener;

    public ProcessConsole() {
        pane.setEditable(false);
        pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        pane.setBackground(new Color(18, 22, 34));
        pane.setCaretColor(Color.WHITE);
    }

    public JScrollPane getView() {
        return scroll;
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    // Starts "java -cp <same classpath as this GUI> <mainClass>".
    public boolean start(String mainClass, Listener listener) {
        this.listener = listener;
        String home = System.getProperty("java.home");
        File java = new File(home, "bin" + File.separator + "java.exe");
        if (!java.exists()) {
            java = new File(home, "bin" + File.separator + "java");
        }
        ProcessBuilder builder = new ProcessBuilder(java.getPath(), "-cp",
                System.getProperty("java.class.path"), mainClass);
        builder.redirectErrorStream(true);
        try {
            process = builder.start();
        } catch (IOException e) {
            appendLine("Could not start " + mainClass + ": " + e.getMessage() + "\n", Palette.ERROR);
            return false;
        }
        synchronized (ALL) {
            ALL.add(process);
        }
        input = new PrintWriter(new OutputStreamWriter(process.getOutputStream()), true);
        Thread reader = new Thread(this::readOutput, "GUI-Output-Reader");
        reader.setDaemon(true);
        reader.start();
        return true;
    }

    // Types one line into the program, like pressing Enter in the terminal.
    public void send(String line, boolean echo) {
        if (!isRunning()) {
            return;
        }
        if (echo) {
            appendLine("> " + line + "\n", Palette.ECHO);
        }
        input.println(line);
    }

    // Sends the program's own exit command; if it has not ended after 4 s, it is killed.
    // The waiting thread is not a daemon, so the GUI waits for a clean goodbye (e.g. BYE).
    public void stopGracefully(String exitCommand) {
        if (!isRunning()) {
            return;
        }
        send(exitCommand, true);
        Process p = process;
        new Thread(() -> {
            try {
                if (!p.waitFor(4, TimeUnit.SECONDS)) {
                    p.destroy();
                }
            } catch (InterruptedException e) {
                p.destroy();
            }
        }, "GUI-Stopper").start();
    }

    // Kills the program at once, like a laptop crashing or closing the terminal.
    public void kill() {
        if (isRunning()) {
            process.destroyForcibly();
        }
    }

    public void clear() {
        pane.setText("");
    }

    // Reads the program's output character by character so prompts without a newline
    // (e.g. "Type the server IP address: ") are shown too.
    private void readOutput() {
        StringBuilder line = new StringBuilder();
        try (Reader reader = new InputStreamReader(process.getInputStream())) {
            char[] buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                for (int i = 0; i < count; i++) {
                    char c = buffer[i];
                    if (c == '\r') {
                        continue;
                    }
                    line.append(c);
                    if (c == '\n') {
                        deliver(line.toString());
                        line.setLength(0);
                    }
                }
                if (line.length() > 0 && !reader.ready()) {   // a prompt waiting for input
                    deliver(line.toString());
                    line.setLength(0);
                }
            }
        } catch (IOException e) {
            // stream closed: the program ended
        }
        if (line.length() > 0) {
            deliver(line.toString());
        }
        synchronized (ALL) {
            ALL.remove(process);
        }
        SwingUtilities.invokeLater(() -> {
            appendLine("\n[program ended]\n", Palette.ECHO);
            listener.onExit();
        });
    }

    private void deliver(String text) {
        SwingUtilities.invokeLater(() -> {
            appendLine(text, Palette.colorFor(text));
            listener.onText(text);
        });
    }

    private void appendLine(String text, Color color) {
        StyledDocument doc = pane.getStyledDocument();
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, color);
        try {
            doc.insertString(doc.getLength(), text, style);
            if (doc.getLength() > 300_000) {   // keep memory small during long demos
                doc.remove(0, doc.getLength() - 200_000);
            }
        } catch (BadLocationException e) {
            // cannot happen: we always insert at the end
        }
        pane.setCaretPosition(doc.getLength());
    }
}
