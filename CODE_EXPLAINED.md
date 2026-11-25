# AstroNet – Full Code Explanation (line by line)

This guide covers how the whole project works, how **threads** are used, which **thread
methods** are called and why, and then every file in `src/` explained line by line.

Line numbers (`L12`) match the files in `src/` exactly. Open the file beside this guide.

---

## Part 1 – How the project works (big picture)

Two programs exist:

| Program | Main class | Runs on | Job |
|---|---|---|---|
| **Server** | `server.ServerMain` | ONE laptop | Keeps the list of stations, receives alerts, asks others to check, decides the result |
| **Station** | `station.StationMain` | each other laptop (max 5) | A fake telescope: sends alerts, answers follow-ups, sends heartbeats and images |

Three network ports (all in `common/Config.java`):

| Port | Type | What travels on it |
|---|---|---|
| 5000 | TCP | Text lines: `REGISTER`, `WELCOME`, `ALERT`, `ALERT_OK`, `FOLLOW_UP`, `VERIFY`, `RESULT`, `BYE`, `ERROR` |
| 5001 | UDP | `DISCOVER_ASTRONET` broadcast → `ASTRONET_SERVER|5000` reply; `HEARTBEAT|id|seq` every 5 s |
| 5002 | TCP | A PNG sky image (binary bytes) |

### The life of one alert

```
 ASTRO-01 (station)               SERVER                         ASTRO-02, ASTRO-03
 ------------------               ------                         ------------------
 UDP broadcast DISCOVER  ──────►  UDP-Service replies SERVER_HERE
 TCP connect :5000       ──────►  main thread accept() → new Handler-1 thread
 REGISTER|ASTRO-01|Sri Lanka ──►  Handler-1: registry.register()  
                         ◄──────  WELCOME|ASTRO-01
 HEARTBEAT|ASTRO-01|1 (UDP) ───►  UDP-Service: registry.recordHeartbeat()   (every 5 s)

 ALERT|ASTRO-01|ra|dec|b|base ─►  Handler-1: events.createEvent() → EVT-0001
                         ◄──────  ALERT_OK|EVT-0001
 image bytes on :5002    ──────►  File-Receiver saves received_images/EVT-0001_ASTRO-01.png
                                  Handler-1 ── FOLLOW_UP|EVT-0001|ra|dec ──────►  (both stations)
                                  Handler-1 starts Timer-EVT-0001 (15 s)
                                                                 Server-Listener thread "observes"
                                  Handler-2 ◄── VERIFY|EVT-0001|ASTRO-02|CONFIRMED|...
                                  Handler-3 ◄── VERIFY|EVT-0001|ASTRO-03|NOT_DETECTED|...
                                  last answer → events.addAnswer() returns RESULT line
 RESULT|EVT-0001|LIKELY_CONFIRMED|... ◄── broadcast to every station ──►
```

Rule (in `EventManager.addAnswer`): if **confirmed × 2 ≥ asked** (at least 50 %) →
`LIKELY_CONFIRMED`, otherwise `INCONCLUSIVE`. If not all answers come within 15 s, the
`VerificationTimer` closes the event as `INCONCLUSIVE` ("timeout").

---

## Part 2 – Thread concepts used in this project

### 2.1 Process vs thread
* A **process** = one running program (one JVM). The server is one process; each station is one process.
* A **thread** = one path of execution *inside* a process. All threads of a process share the
  same memory (same objects), which is why we need `synchronized`.
* Why threads? Many calls **block** (wait): `accept()`, `readLine()`, `receive()`, `Scanner.nextLine()`,
  `sleep()`. If one thread did everything, waiting for one station would freeze the whole server.
  With threads, each blocking job waits in its own thread.

### 2.2 The two ways to create a thread (both are used)

**Way 1 – `extends Thread`** (class *is* a thread)
```java
public class UdpService extends Thread {
    public UdpService(...) { super("UDP-Service"); }   // super(name) sets the thread name
    @Override public void run() { ... }                 // the work
}
new UdpService(registry).start();
```
Used in: `UdpService`, `HeartbeatMonitor`, `FileReceiver`, `ServerConsole` (server) and
`HeartbeatSender` (station).

**Way 2 – `implements Runnable`** (class is a *task*, a separate `Thread` object runs it)
```java
public class StationHandler implements Runnable {
    @Override public void run() { ... }
}
Thread t = new Thread(new StationHandler(...), "Handler-1");   // Thread(Runnable, name)
t.start();
```
Used in: `StationHandler`, `VerificationTimer` (server) and `ServerListener`, `SkySimulator` (station).

**Why use Runnable?** Java has single inheritance – a class that `extends Thread` cannot extend
anything else. `Runnable` keeps the *task* separate from the *thread*, which is the cleaner design.
We use both on purpose to show both ways.

### 2.3 Thread methods used and where

| Method | What it does | Where in the code |
|---|---|---|
| `start()` | Creates the new OS thread, which then calls `run()`. **Never call `run()` directly** – that would just run it in the current thread. | `ServerMain.startThread` L62, `StationHandler` L136, `StationMain` L87, L89, L138 |
| `run()` | The code the new thread executes. When `run()` returns, the thread is TERMINATED. | every thread class |
| `super("Name")` / `new Thread(r, "Name")` | Gives the thread a name (shown in logs and the `threads` command). | all thread classes |
| `Thread.currentThread()` | Returns the thread running this line. | `Log` L12, `ServerMain` L21, `StationMain` L172 |
| `getName()` | The thread's name. | `Log` L12, `ServerMain` L72 |
| `getState()` | NEW / RUNNABLE / BLOCKED / WAITING / TIMED_WAITING / TERMINATED. | `ServerMain` L72, L76; `StationMain` L172–178 |
| `Thread.sleep(ms)` | Pauses the current thread; state becomes TIMED_WAITING. Throws `InterruptedException`. | `HeartbeatMonitor` L22, `VerificationTimer` L22, `HeartbeatSender` L42, `SkySimulator` L30, `ServerListener` L67 |
| `join(ms)` | Waits (max ms) until another thread finishes. | `StationMain` L189 |
| `isAlive()` | true between `start()` and the end of `run()`. | `StationMain` L130, L163 |
| `synchronized` method | Only one thread at a time may run any synchronized method of the same object (one lock per object). | `StationRegistry`, `EventManager`, `Log`, `ServerMain.track/printThreads`, `StationMain.send/sendAlert/setLeaving/isLeaving` |
| `volatile` field | Every thread always sees the newest value (no caching). | `SkySimulator` L14 `running` |
| `System.exit()` | Ends the whole process, i.e. **all** threads. | `ServerConsole` L33, `ServerMain` L56, `StationMain` L197, `ServerListener` L39 |

### 2.4 Thread states you can show in the demo
* **NEW** – object created, `start()` not called yet.
* **RUNNABLE** – running *or* ready to run. Note: a thread blocked in socket I/O (`accept()`,
  `readLine()`, `receive()`) is also shown as RUNNABLE by Java, because it is waiting inside native code.
* **BLOCKED** – waiting to get a `synchronized` lock another thread holds.
* **WAITING** – waiting with no time limit (e.g. `join()` without a number).
* **TIMED_WAITING** – `sleep(ms)` or `join(ms)`. Heartbeat-Monitor and Timer threads are usually here.
* **TERMINATED** – `run()` has finished. Timer threads end in this state after 15 s.

### 2.5 All threads in the project

**Server process**

| Thread name | Class | How created | Blocks on | Ends when |
|---|---|---|---|---|
| `main` | `ServerMain.main` | JVM | `serverSocket.accept()` | error / exit |
| `UDP-Service` | `UdpService` | extends Thread | `socket.receive()` | error / exit |
| `Heartbeat-Monitor` | `HeartbeatMonitor` | extends Thread | `Thread.sleep(5000)` | interrupted / exit |
| `File-Receiver` | `FileReceiver` | extends Thread | `serverSocket.accept()` on 5002 | error / exit |
| `Server-Console` | `ServerConsole` | extends Thread | `scanner.hasNextLine()` | `quit` |
| `Handler-1`, `Handler-2`, … | `StationHandler` | Runnable, **one per station** | `in.readLine()` | station disconnects / BYE |
| `Timer-EVT-0001`, … | `VerificationTimer` | Runnable, **one per event** | `Thread.sleep(15000)` | after 15 s |

**Station process**

| Thread name | Class | How created | Blocks on | Ends when |
|---|---|---|---|---|
| `main` | `StationMain.main` | JVM | `scanner.nextLine()` (menu) | option 5 |
| `Server-Listener` | `ServerListener` | Runnable | `in.readLine()` | server closes connection |
| `Heartbeat-Sender` | `HeartbeatSender` | extends Thread | `Thread.sleep(5000)` | exit |
| `Sky-Simulator` | `SkySimulator` | Runnable, made new each time option 1 is chosen | `Thread.sleep(5000)` | `running = false` |

`ServerFinder` and `ImageSender` are **not** threads – they are plain static methods run by
whichever thread calls them (`main` and `Server-Listener` respectively).

### 2.6 Shared data and why it is safe
| Shared object | Used by threads | Protection |
|---|---|---|
| `StationRegistry` (the station `HashMap`) | all Handlers, UDP-Service, Heartbeat-Monitor, Timers, Console | every public method `synchronized` |
| `EventManager` (event list, counter) | all Handlers, Timers, Console | every public method `synchronized` |
| `ServerMain.threads` list | main, Handlers (add timers), Console (print/remove) | `static synchronized` |
| `System.out` via `Log.log` | everyone | `static synchronized` → whole lines never mix |
| Station's socket `PrintWriter out` | main (ALERT, BYE), Sky-Simulator (ALERT), Server-Listener (VERIFY) | `StationMain.send` is `synchronized` |
| `SkySimulator.running` | main writes, Sky-Simulator reads | `volatile` |
| `StationMain.leaving` | main writes, Server-Listener reads | `synchronized` getter/setter |

**The most important race:** the last `VERIFY` (Handler thread) and the 15 s timeout
(Timer thread) can happen at the same moment. Both `addAnswer` and `closeEvent` are
`synchronized` on the same `EventManager` and both check `event.closed` first. So exactly one
of them wins and the RESULT is broadcast **exactly once**.

---

## Part 3 – Line by line: `src/common/`

### 3.1 `Config.java` – all settings in one place
| Lines | Explanation |
|---|---|
| L1 | `package common;` – this file belongs to the `common` package (shared by server and station). |
| L3 | Comment: purpose of the class. |
| L4 | `public class Config` – only constants, never instantiated. |
| L6 | `TCP_PORT = 5000` – port for text messages. `public static final` = global constant. |
| L7 | `UDP_PORT = 5001` – port for discovery and heartbeats. |
| L8 | `FILE_PORT = 5002` – port for image files. |
| L10 | `MAX_STATIONS = 5` – the server rejects a 6th station. |
| L11 | `UDP_BUFFER_SIZE = 256` – byte array size for one UDP datagram (our messages are short). |
| L12 | `FILE_BUFFER_SIZE = 4096` – image is copied in 4 KB chunks. |
| L14 | `VERIFY_TIMEOUT_MS = 15000` – how long the Timer thread waits for VERIFY answers. |
| L15 | `DISCOVERY_TIMEOUT_MS = 2000` – station waits 2 s for the server to answer the broadcast. |
| L16 | `HEARTBEAT_INTERVAL_MS = 5000` – station sends a heartbeat every 5 s. |
| L17 | `HEARTBEAT_TIMEOUT_MS = 15000` – no heartbeat for 15 s → OFFLINE. |
| L18 | `MONITOR_INTERVAL_MS = 5000` – how often Heartbeat-Monitor checks. |
| L19 | `OBSERVATION_INTERVAL_MS = 5000` – Sky-Simulator takes one reading every 5 s. |
| L21 | `BASELINE_BRIGHTNESS = 100.0` – normal sky brightness. |
| L22 | `ALERT_FACTOR = 3.0` – brightness > 3 × baseline means "sudden brightening". |
| L23 | `FLARE_PERCENT = 15` – 15 % chance that a simulated reading is a flare. |
| L24 | `HEARTBEAT_DROP_PERCENT = 10` – the station deliberately skips 10 % of heartbeats to show that UDP can lose packets ("UDP GAP" on the server). |
| L26 | `IMAGE_FOLDER` – where the server saves images. |
| L28–29 | The five station IDs and their locations; index 0 = ASTRO-01 / Sri Lanka, etc. |

### 3.2 `Log.java` – thread-safe printing
| Lines | Explanation |
|---|---|
| L3 | Imports `LocalTime` to get the current time. |
| L6 | `public class Log`. |
| L9 | `public static synchronized void log(...)` – **static synchronized** locks on `Log.class`, so only one thread prints at a time; lines from different threads never get mixed together. |
| L10 | Gets the current time. |
| L11 | Formats it as `HH:mm:ss` (`%02d` = 2 digits with leading zero). |
| L12 | Prints `[time] [thread name] message`. `Thread.currentThread().getName()` shows **which thread** wrote the line – very useful in the demo (e.g. `[Handler-2]`, `[Timer-EVT-0001]`). |

### 3.3 `Protocol.java` – message format
| Lines | Explanation |
|---|---|
| L3 | Every message is one text line: `TYPE|field1|field2|…`. |
| L7–15 | TCP message type names with their format in the comment. E.g. `ALERT|id|ra|dec|brightness|baseline`. Using constants avoids typing mistakes. |
| L18 | `DISCOVER` – the UDP broadcast text a station sends to find the server. |
| L19 | `SERVER_HERE` – the server's UDP reply (`ASTRONET_SERVER|5000`). |
| L20 | `HEARTBEAT` – UDP "I am alive" message with a sequence number. |
| L22–24 | The three possible VERIFY answers. |
| L26–27 | The two possible final results. |
| L30 | `build(String type, String... fields)` – `String...` is *varargs*: any number of fields. |
| L31 | Start the line with the type. |
| L32–34 | Add `|` + each field. `.replace("|", "/")` stops a field from containing `|` and breaking the format. |
| L35 | Return the full line. |
| L39–41 | `parse` splits a line into fields. `split` uses a regular expression and `|` means "OR" in regex, so it must be escaped: `"\\|"`. |
| L44–46 | `number` rounds to 2 decimals: `Math.round(v*100)/100.0`. `String.valueOf(double)` always uses `.` (not `,`), so the other side can `Double.parseDouble` it on any language setting. |

---

## Part 4 – Line by line: `src/server/`

### 4.1 `ServerMain.java` – starts everything, accepts stations
| Lines | Explanation |
|---|---|
| L6–10 | Imports: `ServerSocket`/`Socket` (TCP), `InetAddress` (IP), `ArrayList`. |
| L15 | `threads` – a list of every thread started, so the `threads` command can print their states. `static` because `main` is static. |
| L17 | `main` – the program starts here, in the thread called **`main`**. |
| L18–20 | Banner. |
| L21 | `track(Thread.currentThread())` – add the main thread itself to the list. |
| L23 | Create **one** `StationRegistry` – shared by all threads. |
| L24 | Create **one** `EventManager` – shared by all threads. |
| L25 | Start thread **UDP-Service** (discovery + heartbeats). |
| L26 | Start thread **Heartbeat-Monitor** (marks stations OFFLINE). |
| L27 | Start thread **File-Receiver** (port 5002 images). |
| L28 | Start thread **Server-Console** (keyboard commands). It needs its own thread because `main` will be blocked in `accept()` forever. |
| L30 | Declared outside `try` so `finally` can close it. |
| L32 | `new ServerSocket(5000)` – open TCP port 5000 for listening. Fails if another program already uses it. |
| L33–34 | Print the port and this computer's IP (stations may need to type it). |
| L35 | Counter used for thread names `Handler-1`, `Handler-2`, … |
| L36 | Loop forever – the server never stops accepting. |
| L37 | `serverSocket.accept()` – **blocks** until a station connects, then returns a new `Socket` just for that station. |
| L38–39 | Log the station's IP and port. |
| L41 | **Thread-per-client**: wrap the socket in a `StationHandler` (Runnable) and give it to `new Thread(runnable, name)`. |
| L42 | `startThread` → tracks it and calls `start()`. The new thread now talks to that station; `main` goes straight back to `accept()` for the next station. That is how many stations are served **at the same time**. |
| L43 | Next handler number. |
| L45–47 | If the port can't be opened (or another I/O error), print a helpful message. |
| L48–57 | `finally`: close the server socket and `System.exit(1)` – this also stops all other threads. |
| L60–63 | `startThread`: remember the thread, then `thread.start()` (creates the real thread which calls `run()`). |
| L65–67 | `track` is `static synchronized`: Handler threads also call it (for timers, `StationHandler` L135) while the Console thread may be reading the list – the lock prevents `ArrayList` corruption. |
| L69 | `printThreads` – called by the Console thread for the `threads` command. |
| L70 | Number of threads in this one process. |
| L71–73 | For each thread print name and `getState()` (`%-18s` = left-aligned, 18 wide). |
| L75–79 | Loop **backwards** and remove TERMINATED threads (backwards so removing doesn't skip elements). A finished timer is shown once as TERMINATED, then forgotten. |

### 4.2 `StationInfo.java` – data about one station
| Lines | Explanation |
|---|---|
| L3 | `PrintWriter` – the object used to write text to that station's socket. |
| L8–10 | ID, location, IP address. |
| L11 | `writer` – the TCP output. `null` means the TCP connection is gone. |
| L12 | `status` – `"ONLINE"` or `"OFFLINE"` (decided by heartbeats). |
| L13 | Time (ms) of the last heartbeat. |
| L14 | Last heartbeat sequence number; 0 = none yet. |
| L16–24 | Constructor: fills the fields, starts ONLINE, and sets `lastHeartbeatTime` to now so the station isn't instantly OFFLINE. |

Fields have no `private` (package-private) so classes in `server` can read them directly. They
are only touched inside `synchronized` methods of `StationRegistry`.

### 4.3 `StationRegistry.java` – shared list of stations (thread-safe)
| Lines | Explanation |
|---|---|
| L10 | Many threads use this object, so **every public method is `synchronized`** – one lock (this object) for the whole map. |
| L13 | `HashMap<String, StationInfo>` – station ID → info. `HashMap` is *not* thread-safe by itself; the `synchronized` methods make it safe. |
| L16 | `register` – called by a Handler thread. Returns `null` if OK, or an error text. |
| L17 | Look up an existing entry with that ID. |
| L18–20 | If it exists **and** is still TCP-connected → reject (duplicate ID). Because the method is synchronized, two stations registering "ASTRO-02" at the same moment can't both pass this check. |
| L21–23 | If 5 stations are already connected → reject. |
| L24 | Add (or replace an old disconnected entry). |
| L25 | `null` = success. |
| L28 | `disconnect` – called from a Handler's `finally` when its connection ends. |
| L29–32 | Said BYE → remove the station completely. |
| L33–36 | Crashed (no BYE) → keep the entry but set `writer = null`; Heartbeat-Monitor will later mark it OFFLINE (shows the 15 s detection in the demo). |
| L39–44 | `sendTo` – send one line to one station. Handler threads call this to write to **other** stations' sockets (FOLLOW_UP); the lock makes sure two threads don't write to the same socket at once. |
| L46–55 | `broadcast` – send to every connected station; returns how many got it. |
| L58–67 | `getOnlineIdsExcept` – builds the list of stations to ask for FOLLOW_UP: TCP-connected **and** ONLINE, but not the station that sent the alert. Returns a **new** list (a copy), so the caller can use it after the lock is released. |
| L69 | `recordHeartbeat` – called by the UDP-Service thread. |
| L70–74 | Unknown ID (not registered) → ignore. |
| L75 | Remember the time of this heartbeat. |
| L76–77 | First heartbeat ever → log it. |
| L78–81 | Sequence jumped (e.g. 4 → 6) → a UDP datagram was **lost**; log how many. Shows UDP is unreliable. |
| L82–86 | Sequence is old or repeated → UDP **out of order / duplicate**; log and ignore. |
| L87 | Save the new sequence number. |
| L88–91 | Was OFFLINE but heartbeats are back → ONLINE again. |
| L95 | `checkHeartbeats` – called every 5 s by **Heartbeat-Monitor**. |
| L96 | Current time. |
| L97–103 | For each station: how long silent? If ONLINE and silent > 15 s → set OFFLINE and log. |
| L106–119 | `printList` – the `list` command: ID, location, IP, ONLINE/OFFLINE, TCP state, seconds since last heartbeat. `(cond) ? a : b` is the ternary operator. |
| L121–129 | `countConnected` – private helper (only called from synchronized methods, so it already holds the lock): counts stations with a non-null writer. |

### 4.4 `StationHandler.java` – one thread per station (`implements Runnable`)
| Lines | Explanation |
|---|---|
| L14 | `implements Runnable` – this is a task; `ServerMain` L41 wraps it in `new Thread(...)`. |
| L16 | This station's own socket (from `accept()`). |
| L17–18 | References to the **shared** registry and event manager. |
| L19 | `out` – writer to this station. |
| L20 | `stationId` – unknown (`null`) until REGISTER succeeds. |
| L21 | `saidBye` – true if the station left politely. |
| L23–27 | Constructor runs in the **main** thread (just stores values). |
| L30 | `run()` – from here on, code runs in the **Handler-N** thread. |
| L31 | Station's IP as text. |
| L33 | `BufferedReader(InputStreamReader(socket.getInputStream()))` – turns the byte stream into text so we can read whole lines. |
| L34 | `PrintWriter(..., true)` – `true` = **auto-flush**: every `println` is sent immediately. |
| L35 | Read the first line; it must be REGISTER. |
| L36–38 | If registration fails → `return` (the `finally` still runs and closes the socket). |
| L39 | Main loop of this thread. |
| L40 | `in.readLine()` – **blocks** this thread until the station sends a line. Other Handler threads are unaffected. |
| L41–43 | `null` = the station closed the connection (crash / window closed) → leave loop. |
| L44–46 | `handleMessage` returns false on BYE → leave loop. |
| L48–49 | Socket error (e.g. connection reset) → log it. |
| L50 | `finally` always runs when the thread finishes. |
| L51–58 | If the station had registered: tell the registry (`disconnect`) and log LEFT or DISCONNECTED. |
| L59–63 | Close the socket. After `run()` returns, the thread is **TERMINATED**. |
| L67 | `register` – checks the first line. |
| L68–70 | No line at all → fail. |
| L71 | Split into parts. |
| L72–76 | Not `REGISTER` or not enough fields → send `ERROR` and fail. |
| L77 | Ask the shared registry (synchronized) to add us. |
| L78–82 | Rejected (duplicate / full) → send `ERROR|reason`, fail. |
| L83 | Remember our ID. |
| L84 | Send `WELCOME|id`. |
| L85–86 | Log and succeed. |
| L90 | `handleMessage` – decides what to do with one line. |
| L93–94 | `ALERT` with at least 6 parts → `handleAlert`. |
| L95–96 | `VERIFY` with at least 5 parts → `handleVerify`. |
| L97–99 | `BYE` → set `saidBye`, return false (ends loop). |
| L100–103 | Anything else → log and reply `ERROR`. |
| L104–107 | `Double.parseDouble` failed (bad number) → reply `ERROR` instead of crashing the thread. |
| L108 | true = keep reading. |
| L112 | `handleAlert` – what happens when a station reports a brightening. |
| L113–116 | Convert text fields to numbers. |
| L117 | Ask the registry which **other** online stations exist. |
| L118 | Create the event (synchronized) → gets e.g. `EVT-0001`. |
| L120–121 | Log it, including how many times brighter than baseline. |
| L122 | Send `ALERT_OK|EVT-0001` back to the reporter (it will then send the image). |
| L124 | Build the `FOLLOW_UP|eventId|ra|dec` line once. |
| L125–128 | Send it to every other station (through the registry, which locks). |
| L129–132 | Nobody else online → close immediately as INCONCLUSIVE and broadcast. |
| L134 | **Create a new thread** for the 15 s timeout: `new Thread(new VerificationTimer(...), "Timer-EVT-0001")`. If this Handler thread slept 15 s itself, it couldn't read any more messages from its station. |
| L135 | Add it to the tracked list (synchronized). |
| L136 | `timer.start()` – the timer now runs in parallel. |
| L139–142 | `handleVerify`: log the answer; `events.addAnswer(...)` returns the RESULT line when this was the last answer, otherwise `null`; pass it to `publish`. Note that the VERIFY of ASTRO-02 is handled by **Handler-2**, not by the thread that handled the alert. |
| L144–150 | `publish`: if there is a result line, broadcast it to all stations and log how many got it. |
| L152–157 | `nameOrIp`: use the ID in messages if known, else the IP. |

### 4.5 `SkyEvent.java` – data for one event
| Lines | Explanation |
|---|---|
| L9–14 | Event ID, who reported it, position (RA = right ascension, Dec = declination), brightness, baseline. |
| L15 | `askedIds` – stations that got a FOLLOW_UP. |
| L16 | `answers` – station ID → answer. |
| L17 | Number of CONFIRMED answers. |
| L18 | `closed` – true once a result is decided (protects against a double result). |
| L19 | `result` – starts as `"WAITING"`. |
| L20 | `summary` – text like "2 of 3 stations confirmed". |
| L22–31 | Constructor stores the values. |

### 4.6 `EventManager.java` – creates events and decides results (thread-safe)
| Lines | Explanation |
|---|---|
| L7 | All public methods `synchronized` because Handler threads, Timer threads and the Console use it. |
| L10 | List of all events. |
| L11 | Counter for the next ID. |
| L13 | `createEvent` – synchronized so two simultaneous alerts never get the same number. |
| L15 | `String.format("EVT-%04d", 1)` → `EVT-0001`. |
| L16 | Increase the counter. |
| L17 | Store a new `SkyEvent`. |
| L18 | Return the ID. |
| L22 | `addAnswer` – called by a Handler thread when a VERIFY arrives. |
| L23 | Find the event. |
| L24–26 | Unknown or already closed (e.g. timer already fired) → ignore. |
| L27–29 | Station wasn't asked, or already answered → ignore (stops duplicates / cheating). |
| L30 | Store the answer. |
| L31–33 | Count CONFIRMED. |
| L34–36 | Not all asked stations answered yet → `null` (no result yet). |
| L37 | Number asked. |
| L38 | `confirmed * 2 >= asked` = at least 50 % (integer maths, no decimals). |
| L39–42 | Set LIKELY_CONFIRMED or INCONCLUSIVE. |
| L43 | Summary text. |
| L44 | Mark closed – now the timer can't produce a second result. |
| L45 | Return `RESULT|...` line. |
| L49 | `closeEvent` – called by the **Timer** thread after 15 s, or by the Handler when nobody is online. |
| L51–53 | Already closed (all answers came in) → `null`, nothing to do. |
| L54–58 | Force INCONCLUSIVE, write who answered, mark closed, return the line. |
| L61–70 | `printEvents` – the `events` command. |
| L72–79 | `findEvent` – simple linear search (private; caller already holds the lock). |
| L81–83 | `resultLine` – builds `RESULT|eventId|result|summary`. |

### 4.7 `VerificationTimer.java` – one short-lived thread per event (`implements Runnable`)
| Lines | Explanation |
|---|---|
| L7 | Runnable task; started by `StationHandler` L134–136 with the name `Timer-EVT-xxxx`. |
| L9–11 | Which event, plus shared registry and event manager. |
| L13–17 | Constructor stores them. |
| L20 | `run()` – runs in the Timer thread. |
| L22 | `Thread.sleep(15000)` – state **TIMED_WAITING** for 15 s. |
| L23–26 | If another thread calls `interrupt()` on it, sleep throws `InterruptedException` → log and stop. |
| L27 | Try to close the event; returns `null` if all answers already arrived. |
| L28–31 | If the timer closed it → broadcast INCONCLUSIVE result. |
| (end) | `run()` returns → thread is **TERMINATED**. The `threads` command shows it once as TERMINATED. |

### 4.8 `UdpService.java` – UDP port 5001 (`extends Thread`)
| Lines | Explanation |
|---|---|
| L8–9 | `DatagramPacket` (one UDP message) and `DatagramSocket` (UDP endpoint). |
| L12 | `extends Thread`. |
| L14 | Shared registry (for heartbeats). |
| L16–19 | `super("UDP-Service")` sets the thread name. |
| L22 | `run()` – runs in thread UDP-Service. |
| L25 | `new DatagramSocket(5001)` – listen on UDP 5001. No connection, no `accept()` – UDP is connectionless. |
| L27 | Buffer for incoming bytes. |
| L28 | Loop forever. |
| L29 | Empty packet that will receive data. |
| L30 | `socket.receive(packet)` – **blocks** until any datagram arrives from anyone. |
| L31 | Turn the received bytes into text (only `getLength()` bytes are valid). |
| L32 | Handle it. |
| L34–35 | Error (e.g. port in use) → log. |
| L36–40 | Close the socket. |
| L43 | `handle` – decide what the datagram is. |
| L45 | `DISCOVER_ASTRONET` from a station's broadcast. |
| L46 | Reply text `ASTRONET_SERVER|5000`. |
| L49 | Reply to `packet.getAddress()` + `packet.getPort()` – the sender's address. That is how the station learns the server's IP. |
| L50 | Send the reply. |
| L51–52 | Log. |
| L53–55 | `HEARTBEAT|id|seq` → `registry.recordHeartbeat` (synchronized). |
| L56–58 | Seq not a number → ignore. |
| L59–61 | Anything else → ignore. |

### 4.9 `HeartbeatMonitor.java` – periodic checker (`extends Thread`)
| Lines | Explanation |
|---|---|
| L7 | `extends Thread`. |
| L11–14 | Name `Heartbeat-Monitor`, store registry. |
| L17 | `run()`. |
| L18–19 | Log the rule. |
| L20 | Loop forever. |
| L22 | Sleep 5 s (TIMED_WAITING most of the time). |
| L23–26 | Interrupted → stop the thread cleanly. |
| L27 | `registry.checkHeartbeats()` – marks silent stations OFFLINE (synchronized). |

### 4.10 `FileReceiver.java` – images on TCP 5002 (`extends Thread`)
| Lines | Explanation |
|---|---|
| L6–11 | `DataInputStream` (read typed data), `File`, `FileOutputStream`, sockets. |
| L13 | A separate port so binary image bytes never mix with the text lines on port 5000. |
| L14 | `extends Thread`. |
| L16–18 | Thread name `File-Receiver`. |
| L21 | `run()`. |
| L24 | Listen on TCP 5002. |
| L25 | Create the `received_images` folder if needed. |
| L27 | Loop forever. |
| L28 | `accept()` – blocks until a station connects to send an image. |
| L29 | Receive it **in this same thread** (images are small, one at a time is fine). |
| L31–41 | Error → log; `finally` closes the server socket. |
| L45 | `receiveOneFile`. |
| L48 | `setSoTimeout(10000)` – if a station stops sending, `read` throws after 10 s instead of hanging this thread forever. |
| L49 | `DataInputStream` to read the header. |
| L50 | `readUTF()` – event ID (same order as `ImageSender` writes). |
| L51 | `readUTF()` – file name. |
| L52 | `readLong()` – file size in bytes. |
| L53 | `new File(...).getName()` keeps only the last part of the name → a malicious name like `../../x` can't write outside the folder. |
| L54 | Final file path inside `received_images`. |
| L55 | Open the file for writing. |
| L57 | 4 KB buffer. |
| L58 | Bytes still to read. |
| L59 | Loop until all bytes arrived. |
| L60 | Don't read more than the remaining size. |
| L61 | `read` returns how many bytes actually arrived (can be less than asked – TCP is a stream). |
| L62–64 | `-1` = connection closed too early → error. |
| L65 | Write those bytes to the file. |
| L66 | Subtract from remaining. |
| L68–69 | Log the saved path, size and sender IP. |
| L70–81 | Error → log; `finally` closes file and socket. |

### 4.11 `ServerConsole.java` – keyboard commands (`extends Thread`)
| Lines | Explanation |
|---|---|
| L5 | Own thread because `main` is busy in `accept()`. |
| L6 | `extends Thread`. |
| L11–15 | Name `Server-Console`, store registry and events. |
| L18 | `run()`. |
| L19 | `Scanner` on the keyboard. |
| L20 | Show help. |
| L21 | `hasNextLine()` – **blocks** until the user presses Enter. |
| L22 | Read command, trim spaces, lower case. |
| L23–24 | `list` → station table. |
| L25–26 | `events` → event table. |
| L27–28 | `threads` → all threads and their states. |
| L29–30 | `help`. |
| L31–33 | `quit` → `System.exit(0)` ends the process (all threads). |
| L34–36 | Unknown non-empty command → message. |
| L38 | Close scanner when input ends. |

---

## Part 5 – Line by line: `src/station/`

### 5.1 `StationMain.java` – the station program
| Lines | Explanation |
|---|---|
| L7–13 | Imports for TCP text I/O, socket, random, keyboard. |
| L18–20 | ID, location, server IP. |
| L21 | TCP socket to the server. |
| L22 | `out` – writer to the server (shared by 3 threads → protected by `send`). |
| L23 | `listenerThread` – the Server-Listener thread object. |
| L24 | `heartbeatSender` – the Heartbeat-Sender thread object. |
| L25–26 | Simulator task and its thread (created on option 1). |
| L27 | Count of alerts sent. |
| L28 | `leaving` – true while exiting, so the listener doesn't treat the closed socket as an error. |
| L30–33 | Constructor. |
| L35 | `main` – runs in thread **main**. |
| L36–38 | Banner. |
| L39 | Keyboard scanner. |
| L40 | Ask which station (index 0–4). |
| L41 | Create the station object with ID and location from `Config`. |
| L42 | Find the server by UDP broadcast (or typed IP). |
| L43–45 | If the TCP connection + registration works, show the menu. |
| L46 | Close the scanner. |
| L49 | `chooseStation`. |
| L50–52 | Print the five choices. |
| L53 | Ask until valid. |
| L55–57 | Input closed (e.g. Ctrl+D) → exit. |
| L58 | Read a line. |
| L59–63 | Convert to number; 1–5 → return index (0–4). |
| L64–66 | Not a number → ask again. |
| L67 | Error message. |
| L72 | `connect` – TCP connect and REGISTER. |
| L73 | Remember the IP. |
| L75 | `new Socket(ip, 5000)` – the TCP 3-way handshake happens here; on the server, `accept()` returns. |
| L76 | Reader for lines from the server. |
| L77 | Writer with auto-flush. |
| L78 | Send `REGISTER|ASTRO-01|Sri Lanka`. |
| L79 | Wait for the reply (still in main thread – before any other thread exists). |
| L80–84 | Not WELCOME (e.g. `ERROR|ASTRO-02 is already connected`) → print, close, fail. |
| L85 | Connected. |
| L86 | **Create thread 2**: `new Thread(new ServerListener(this, in), "Server-Listener")` (Runnable way). |
| L87 | `start()` – it now reads server messages in the background. |
| L88 | **Create thread 3**: `new HeartbeatSender(...)` (extends-Thread way). |
| L89 | `start()` – heartbeats every 5 s. |
| L90 | Success. |
| L91–95 | Can't connect → helpful message. |
| L98 | `runMenu` – the main thread now only handles the keyboard. |
| L100–107 | Print the menu. |
| L108–111 | Input closed → leave. |
| L112 | Read option (blocks main thread – but listener and heartbeat threads keep working). |
| L113–114 | 1 → start/stop simulation. |
| L115–117 | 2 → send a test alert at a random sky position with brightness 450.5 (> 3 × 100). |
| L118–119 | 3 → info. |
| L120–121 | 4 → connection and thread states. |
| L122–125 | 5 → leave and return. |
| L129 | `toggleSimulation`. |
| L130 | If a simulator thread exists **and** `isAlive()`… |
| L131 | …ask it to stop (sets the `volatile` flag). |
| L132–133 | Tell the user; it stops after its current sleep. |
| L135 | Comment: **a Thread object can only be started once** – calling `start()` twice throws `IllegalThreadStateException`. |
| L136 | New task. |
| L137 | New thread named `Sky-Simulator` (Runnable way). |
| L138 | `start()`. |
| L142 | `sendAlert` is `synchronized` – the main thread (option 2) and Sky-Simulator thread can both call it; `alertsSent++` is not atomic, so it needs the lock. |
| L143 | Count. |
| L144–145 | Build and send `ALERT|id|ra|dec|brightness|baseline`. `send` is also synchronized – the same thread can re-enter a lock it already holds (**reentrant lock**). |
| L146–147 | Log. |
| L151 | `send` is `synchronized` – main (ALERT, BYE), Sky-Simulator (ALERT) and Server-Listener (VERIFY) all write to the same socket; the lock stops two lines being mixed. |
| L152 | Write the line (auto-flush sends it). |
| L153–155 | `checkError()` – `PrintWriter` hides exceptions; this tells us if sending failed. |
| L158–164 | `printInfo` – option 3. |
| L166 | `printStatus` – option 4, used in the threads demo. |
| L167–168 | TCP local port, server address, connected/closed. |
| L169–170 | UDP heartbeat info. |
| L172 | State of `main` – RUNNABLE (it's running this code). |
| L173 | State of Server-Listener – RUNNABLE (blocked inside socket `readLine()`, which Java reports as RUNNABLE). |
| L174 | State of Heartbeat-Sender – usually TIMED_WAITING (sleeping). |
| L175–179 | Sky-Simulator: not created, TIMED_WAITING (running), or TERMINATED (stopped). |
| L182 | `leave` – option 5. |
| L183 | Set `leaving = true` first. |
| L184–186 | Stop the simulator if it exists. |
| L187 | Send `BYE|id`. |
| L189 | `listenerThread.join(3000)` – main thread **waits** up to 3 s for the Server-Listener thread to end (it ends when the server closes the connection after BYE). State of main: TIMED_WAITING. |
| L190 | Close the socket. |
| L191–195 | Handle exceptions. |
| L196–197 | Goodbye and `System.exit(0)` – also stops Heartbeat-Sender (which loops forever). |
| L200–206 | `setLeaving` / `isLeaving` are `synchronized` so the Server-Listener thread is guaranteed to see the value the main thread wrote (same idea as `volatile`). |
| L208–214 | Getters used by `ServerListener`. |

### 5.2 `ServerFinder.java` – find the server with UDP broadcast (not a thread)
| Lines | Explanation |
|---|---|
| L16 | Static method, runs in the **main** thread. |
| L18 | Socket variable for `finally`. |
| L20 | `new DatagramSocket()` – no port number → the OS picks a free port. |
| L21 | `setBroadcast(true)` – allow sending to the broadcast address. |
| L22 | `setSoTimeout(2000)` – `receive` waits at most 2 s. |
| L23 | Bytes of `DISCOVER_ASTRONET`. |
| L24 | `255.255.255.255` = everyone on the local network. |
| L25 | `127.0.0.1` = this same computer (broadcast may not reach a server on the same laptop). |
| L26–27 | Send to both. |
| L29–30 | Prepare an empty packet for the reply. |
| L31 | `receive` – waits for the server's reply (max 2 s). |
| L32 | Bytes → text. |
| L33–37 | Reply starts with `ASTRONET_SERVER` → the sender's address **is** the server IP; return it. |
| L38 | Anything else → message. |
| L39–40 | No reply within 2 s → `SocketTimeoutException`. |
| L41–42 | Other UDP error. |
| L43–47 | Close the socket. |
| L48–52 | Fallback: ask the user to type the IP. |

### 5.3 `ServerListener.java` – background reader (`implements Runnable`)
| Lines | Explanation |
|---|---|
| L10 | Comment: needed because the main thread is blocked on the keyboard menu. Without this thread, a FOLLOW_UP or RESULT would only be seen after the user pressed a key. |
| L11 | `implements Runnable`. |
| L13 | Back-reference to the station (for `send`, ID, server IP). |
| L14 | The reader created in `connect`. |
| L15 | Random for simulated observation. |
| L17–20 | Constructor (runs in main). |
| L23 | `run()` – thread **Server-Listener**. |
| L25 | Loop forever. |
| L26 | `readLine()` – **blocks** until the server sends something. |
| L27–29 | `null` = server closed the connection → stop. |
| L30 | Handle the line. |
| L32–36 | Socket error – log it unless we're leaving on purpose. |
| L37–40 | If the connection ended and we did **not** choose to leave → the server is gone; exit the station. |
| L43 | `handle`. |
| L44 | Split the line. |
| L45–47 | `ALERT_OK|EVT-xxxx` → send the sky image via `ImageSender` (runs in this listener thread). |
| L48–49 | `FOLLOW_UP|eventId|ra|dec` → `observe`. |
| L50–55 | `RESULT` → print the FINAL RESULT box. |
| L56–57 | `ERROR` → log. |
| L58–60 | Anything else → log. |
| L64 | `observe` – simulates pointing the telescope. |
| L65 | Log. |
| L67 | `Thread.sleep(1000 + random 0–2000)` – 1–3 s "observation" (this thread is TIMED_WAITING; any server message waits in the TCP buffer meanwhile). |
| L68–70 | Interrupted → give up. |
| L73 | Random number 0–99. |
| L74–76 | 0–59 (60 %) → CONFIRMED with a high brightness. |
| L77–79 | 60–84 (25 %) → NOT_DETECTED with normal brightness. |
| L80–84 | 85–99 (15 %) → UNAVAILABLE with a reason (cloudy, daytime, below horizon). |
| L85 | Send `VERIFY|eventId|id|answer|note` using the synchronized `station.send`. |
| L86 | Log. |

### 5.4 `HeartbeatSender.java` – UDP "I am alive" (`extends Thread`)
| Lines | Explanation |
|---|---|
| L14 | `extends Thread`. |
| L16–18 | Server IP, station ID, random (to simulate loss). |
| L20–24 | `super("Heartbeat-Sender")` sets the name; store values. |
| L27 | `run()`. |
| L30 | New UDP socket (OS-chosen port). |
| L31 | Convert the IP text into an `InetAddress`. |
| L32 | Sequence number starts at 1. |
| L33 | Loop forever. |
| L34–35 | 10 % of the time → **don't** send (simulated UDP loss, so the server shows "UDP GAP"). |
| L37 | Build `HEARTBEAT|id|seq` as bytes. |
| L38 | Packet addressed to server:5001. |
| L39 | `send` – UDP gives no delivery guarantee and no reply. |
| L41 | Next sequence number (also increased when skipped, so the server sees the gap). |
| L42 | Sleep 5 s – TIMED_WAITING. |
| L44–47 | I/O error or interruption → log and end. |
| L48–52 | Close the socket. |

### 5.5 `SkySimulator.java` – fake telescope (`implements Runnable`)
| Lines | Explanation |
|---|---|
| L10 | `implements Runnable`. |
| L12 | Station reference (to call `sendAlert`). |
| L14 | `private volatile boolean running = true;` – **volatile**: written by the main thread (`stopRunning`), read by the Sky-Simulator thread. Without volatile, the simulator thread might keep a cached copy and never see `false`. |
| L16–18 | Constructor. |
| L20–22 | `stopRunning` – called from the main thread; sets the flag. This is the safe way to stop a thread (the old `Thread.stop()` is deprecated/unsafe). |
| L25 | `run()` – thread **Sky-Simulator**. |
| L26 | Log start. |
| L27 | Observation counter. |
| L28 | Loop while the flag is true. |
| L30 | Sleep 5 s. |
| L31–33 | Interrupted → stop. |
| L34–36 | Check the flag again after waking (it may have changed during the sleep). |
| L37–38 | Count and observe once. |
| L40 | Log stop; `run()` ends → TERMINATED. |
| L43 | `observeOnce`. |
| L44 | Random RA 0–360°. |
| L45 | Random Dec −90°…+90°. |
| L46 | Baseline 100. |
| L48–49 | 15 % chance → flare brightness 300–500. |
| L50–52 | Otherwise normal 95–105. |
| L53–56 | Brighter than 3 × baseline → log and `station.sendAlert(...)` (synchronized, called from **this** thread). |
| L57–59 | Otherwise log a normal reading. |

### 5.6 `ImageSender.java` – send a PNG over TCP 5002 (not a thread)
| Lines | Explanation |
|---|---|
| L6–13 | `BufferedImage`/`ImageIO` (make a PNG), `DataOutputStream` (write typed data), file and socket classes. |
| L19 | Static method; called from the **Server-Listener** thread after `ALERT_OK`. |
| L20 | Local file `sky_ASTRO-01.png`. |
| L24 | Draw the fake sky image. |
| L25 | `new Socket(ip, 5002)` – new TCP connection only for this file. |
| L26 | `DataOutputStream` writes Java types in a fixed binary format. |
| L27 | `writeUTF(eventId)` – header field 1. |
| L28 | `writeUTF(fileName)` – header field 2. |
| L29 | `writeLong(size)` – header field 3, so the receiver knows when the file ends. |
| L30 | Open the PNG for reading. |
| L31 | 4 KB buffer. |
| L32–36 | Read a chunk, write it to the socket, until `read` returns −1 (end of file). |
| L37 | `flush()` – push any buffered bytes out. |
| L38 | Log. |
| L39–52 | Error → log; `finally` closes file and socket. |
| L56 | `makeSkyImage`. |
| L57–59 | 200 × 150 RGB image (black by default). |
| L61–63 | 150 random white pixels = stars. |
| L64–65 | Centre point. |
| L66–72 | For every point within radius 6 (`dx²+dy² ≤ 36`) paint a pale yellow pixel = the flare. |
| L73 | Save as PNG. |

---

## Part 6 – Who does what during one alert (thread view)

1. **Station `main`** (option 2) → `sendAlert` (synchronized) → `send` → ALERT line goes out.
2. **Server `Handler-1`** was blocked in `readLine()`; it wakes, `handleAlert` → `getOnlineIdsExcept`
   (registry lock) → `createEvent` (event lock) → `sendTo` ALERT_OK and FOLLOW_UPs → starts **`Timer-EVT-0001`**
   → goes back to `readLine()`.
3. **Station 1 `Server-Listener`** gets ALERT_OK → `ImageSender.sendImage` → **Server `File-Receiver`**
   `accept()` wakes and saves the file.
4. **Stations 2 and 3 `Server-Listener`** get FOLLOW_UP → sleep 1–3 s → send VERIFY.
5. **Server `Handler-2` / `Handler-3`** each read their VERIFY → `addAnswer` (event lock). The last one gets the
   RESULT line → `broadcast` (registry lock).
6. **`Timer-EVT-0001`** wakes after 15 s → `closeEvent` returns `null` (already closed) → does nothing → TERMINATED.
7. All **`Server-Listener`** threads print the FINAL RESULT box.

Meanwhile, without being disturbed: `UDP-Service` records heartbeats, `Heartbeat-Monitor` checks every 5 s,
`Server-Console` waits for typed commands, each station's `Heartbeat-Sender` sleeps and sends.

---

## Part 7 – Likely questions and short answers

* **Why one thread per station?** `readLine()` blocks. One thread per station lets every station be served at the same time; a slow station doesn't stop the others.
* **`start()` vs `run()`?** `start()` makes a new thread that runs `run()`. Calling `run()` directly runs it in the current thread – no concurrency.
* **`extends Thread` vs `implements Runnable`?** Both used. Runnable is preferred: the class can still extend another class, and the task is separate from the thread.
* **What does `synchronized` do?** Only one thread at a time can be inside any synchronized method of the same object. Prevents race conditions, e.g. two stations registering the same ID, or two RESULTs for one event.
* **Why `volatile` in SkySimulator?** One thread writes `running`, another reads it; `volatile` guarantees the reader sees the change.
* **Why a separate timer thread?** If the Handler slept 15 s, it couldn't read that station's messages during that time.
* **Why can't you restart the simulator thread?** A `Thread` can be started only once; we create a new one each time.
* **What does `join` do?** The station's main thread waits for the listener thread to finish before closing.
* **How is a crashed station detected?** Twice: TCP `readLine()` returns `null`/throws immediately → DISCONNECTED; heartbeats stop → after 15 s Heartbeat-Monitor marks it OFFLINE.
* **Why TCP and UDP?** TCP is reliable and ordered – needed for alerts and answers. UDP is light and connectionless – fine for broadcast discovery and frequent heartbeats where losing one doesn't matter.
* **Why a third port for images?** Binary bytes on port 5000 would get mixed into the text-line protocol. A separate connection keeps them apart, and the size header tells the receiver where the file ends.
