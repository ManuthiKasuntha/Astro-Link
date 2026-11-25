@echo off
rem Compiles the networking code (src) + the GUI (gui\src) and starts the GUI. Windows.
rem Optional: run-gui.bat server   or   run-gui.bat station
cd /d "%~dp0.."
if not exist gui\build mkdir gui\build
javac -d gui\build src\common\*.java src\server\*.java src\station\*.java gui\src\gui\*.java
if errorlevel 1 (
    echo.
    echo Compile failed. Is the JDK 17+ installed and "javac" on the PATH?
    pause
    exit /b 1
)
java -cp gui\build gui.AstroLinkGui %1
