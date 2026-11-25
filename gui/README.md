# AstroLink GUI (optional)

A window version of the Server and Station. **The networking code in `src/` is not changed.**
Each window starts the normal console program (`server.ServerMain` / `station.StationMain`) and
its buttons type the same commands you would type in the terminal. The terminal version still works.

Uses only Swing (part of every JDK), so it runs on Windows, Mac and Linux with **JDK 17 or newer**.

## Run

| How | Command |
|---|---|
| Windows | double-click `gui\run-gui.bat` |
| Mac / Linux | `sh gui/run-gui.sh` |
| VS Code | Run and Debug → **GUI (optional)** → ▶ |

`run-gui.bat server` / `run-gui.bat station` (or the same with `run-gui.sh`) opens that window directly.

## Windows

* **Server window**: *Start server* / *Stop server*. Buttons for `list`, `events`, `threads`, `help`,
  plus a box for any command. It shows all IP addresses of this computer.
* **Station window**: pick a station (1–5), optionally type the server IP, press *Connect*.
  Buttons for the menu: start/stop simulation, send test alert, info, connection + threads.
  *Disconnect* sends BYE. *Force close (crash)* kills the station, so the server shows `DISCONNECTED`
  and 15 s later `OFFLINE`.

Several station windows can be open on one computer (use a different station number in each).

## Files

| File | Job |
|---|---|
| `AstroLinkGui.java` | start window, `main` |
| `ServerWindow.java` | server window |
| `StationWindow.java` | station window |
| `ProcessConsole.java` | starts the console program, shows its output, sends typed lines |
| `Palette.java` | colours for ALERT / VERIFY / RESULT / OFFLINE lines |
