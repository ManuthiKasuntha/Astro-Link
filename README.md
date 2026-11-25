# AstroNet – Astronomical Event Detection Network

One **server** and up to **5 stations** (ASTRO-01 Sri Lanka, 02 India, 03 Australia, 04 Japan,
05 South Africa) on different laptops.

1. A station sees a sudden brightening and sends an **ALERT** (plus a small sky image).
2. The server asks the **other** stations to check the same spot (**FOLLOW_UP**).
3. Each station answers CONFIRMED / NOT_DETECTED / UNAVAILABLE (**VERIFY**).
4. The server broadcasts the **RESULT**: ≥ 50% confirmed → `LIKELY_CONFIRMED`, otherwise or after 15 s → `INCONCLUSIVE`.

| Port | Protocol | Used for |
|---|---|---|
| 5000 | TCP | REGISTER, ALERT, FOLLOW_UP, VERIFY, RESULT, BYE (one text line each, `TYPE|a|b`) |
| 5001 | UDP | finding the server (broadcast) + heartbeats every 5 s |
| 5002 | TCP | sky image file |

## Setup (every laptop)

1. Install **JDK 17 or newer** and **VS Code** with the **Extension Pack for Java**.
2. Copy this folder to the laptop and open it in VS Code (*File → Open Folder*).
3. Wait until the Java status bar says the project is ready.
4. All laptops on the **same network**. A phone hotspot is best. On Windows set the network to
   **Private**, and click **Allow** when the firewall asks about Java.

## Run

Open *Run and Debug* (Ctrl+Shift+D), pick a configuration in the dropdown, press ▶:

* **Server**: on ONE laptop. It prints its IP address.
* **Station**: on each other laptop. Type the station number (1–5) in the terminal.
  The server is found automatically. If not, type the server's IP.

You can also run everything on one laptop: start **Server** once, then **Station** several times
(VS Code asks "start another instance?" → Yes). Use a different number each time.

**Optional GUI:** a window version is in [gui/](gui/README.md) (`gui\run-gui.bat` or `sh gui/run-gui.sh`). It runs the same code.

**Server commands:** `list` · `events` · `threads` · `help` · `quit`
**Station menu:** 1 start/stop sky simulation · 2 send test alert · 3 info · 4 connection + threads · 5 exit

## Demo steps (about 2 minutes)

1. Start **Server**. Show `TCP server listening on port 5000`.
2. Start stations 1, 2 and 3. Server shows `REGISTERED ASTRO-01 ...` for each. Type `list`.
3. On ASTRO-01 press **2**. The server shows `ALERT` → `FOLLOW_UP` → `VERIFY` → `RESULT`. All stations show
   the **FINAL RESULT** box. The image appears in `received_images/`.
4. Type `threads` on the server: `Handler-1..3` (one per station), `UDP-Service`, `Heartbeat-Monitor`,
   `Timer-EVT-0001`.
5. On ASTRO-02 press **1** (simulation), then **4**: `Sky-Simulator TIMED_WAITING`. Press **1** again to stop.
6. Close ASTRO-03 (trash-can icon on its terminal). The server shows `DISCONNECTED ASTRO-03`, then
   about 15 s later `OFFLINE ASTRO-03`.
7. Start another Station and choose **2** again. It shows `ERROR|ASTRO-02 is already connected`.

## 3-minute presentation (3 members)

**Member 1: idea + architecture (1 min)**
> "Our project is AstroNet. When one observatory sees something suddenly get brighter in the sky, other
> observatories must check it quickly. NASA's GCN network does this for real. We built a small version with
> one server and five stations on different laptops. It is a client–server system: the server waits with
> `accept()`, and stations connect to it. We use TCP on port 5000 for important messages, because they
> must not be lost. UDP on port 5001 is for finding the server and for heartbeats, and a second TCP port,
> 5002, carries the sky image."
> *(start the server and two stations while talking)*

**Member 2: live demo (1 min)**
> "ASTRO-01 now sends an alert." *(press 2)* "The server creates EVT-0001 and sends a FOLLOW_UP to the
> other stations. Each one observes and sends VERIFY. When 50% or more confirm, the result is
> LIKELY_CONFIRMED; if they don't answer in 15 seconds, it is INCONCLUSIVE. The result is broadcast to everyone,
> and the image is saved in `received_images`. Now I close one station. TCP notices at once, and because
> its UDP heartbeats stop, the server marks it OFFLINE after 15 seconds."

**Member 3: threads (1 min)**
> *(type `threads`)* "Each program is one process with many threads. The server makes one Handler thread
> per station, using `implements Runnable`, so all stations are served at the same time. UDP-Service and
> Heartbeat-Monitor use `extends Thread`. Here you can see the states: RUNNABLE, TIMED_WAITING while
> sleeping, and TERMINATED when finished. Shared data like the station list uses `synchronized`
> methods, so two threads can never register the same station ID. Thank you."

## Troubleshooting

| Problem | Fix |
|---|---|
| `No server answered within 2 s` | Type the IP the server printed. University Wi-Fi often blocks broadcast, so use a hotspot. |
| `Connection refused` | Start the server first. Check the IP. Allow Java in the firewall (Private network). |
| `port 5000 already in use` | An old server is still running. Close its terminal. |
| Station shows `OFFLINE` but works | The firewall blocks UDP. Allow Java on Private networks. |
| Typing in the menu does nothing | Use the **Run and Debug** configs above. They run in the terminal, where input works. |
