#!/bin/sh
# Compiles the networking code (src) + the GUI (gui/src) and starts the GUI. Mac / Linux.
# Run with:  sh gui/run-gui.sh        (optional: sh gui/run-gui.sh server | station)
cd "$(dirname "$0")/.." || exit 1
mkdir -p gui/build
javac -d gui/build src/common/*.java src/server/*.java src/station/*.java gui/src/gui/*.java || {
    echo "Compile failed. Is the JDK 17+ installed and javac on the PATH?"
    exit 1
}
java -cp gui/build gui.AstroLinkGui "$@"
