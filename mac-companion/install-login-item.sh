#!/bin/sh
# Install (or refresh) the Mac helper so it runs in the background
# at login and restarts if it dies. Does not need a terminal window.
set -e
cd "$(dirname "$0")"
LABEL="com.localnotes.bridge"
PLIST="$HOME/Library/LaunchAgents/${LABEL}.plist"
PYTHON="$(command -v python3)"
HELPER="$(pwd)/notes_bridge.py"
LOG="$HOME/Library/Logs/local-notes-bridge.log"
UID_NUM="$(id -u)"

mkdir -p "$HOME/Library/LaunchAgents" "$HOME/Library/Logs"

cat > "$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>${LABEL}</string>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>WorkingDirectory</key>
  <string>$(pwd)</string>
  <key>ProgramArguments</key>
  <array>
    <string>${PYTHON}</string>
    <string>${HELPER}</string>
  </array>
  <key>EnvironmentVariables</key>
  <dict>
    <key>PYTHONUNBUFFERED</key>
    <string>1</string>
    <key>LAUNCHED_BY_LAUNCHD</key>
    <string>1</string>
  </dict>
  <key>StandardOutPath</key>
  <string>${LOG}</string>
  <key>StandardErrorPath</key>
  <string>${LOG}</string>
</dict>
</plist>
EOF

# Free the port if a terminal copy is sitting on it.
if command -v lsof >/dev/null 2>&1; then
  PIDS=$(lsof -nP -iTCP:18765 -sTCP:LISTEN -t 2>/dev/null || true)
  if [ -n "$PIDS" ]; then
    echo "Stopping existing helper: $PIDS"
    kill $PIDS 2>/dev/null || true
    sleep 1
  fi
fi

launchctl bootout "gui/${UID_NUM}/${LABEL}" 2>/dev/null || true
launchctl bootstrap "gui/${UID_NUM}" "$PLIST"
launchctl enable "gui/${UID_NUM}/${LABEL}"
launchctl kickstart -k "gui/${UID_NUM}/${LABEL}"

echo
echo "Helper is installed and running in the background."
echo "  PIN file : $HOME/.local-notes-bridge.json"
echo "  Log      : $LOG"
echo "  Stop     : launchctl bootout gui/${UID_NUM}/${LABEL}"
echo
sleep 1
python3 - "$HOME/.local-notes-bridge.json" <<'PY'
import json, sys
from pathlib import Path
p = Path(sys.argv[1])
if p.exists():
    data = json.loads(p.read_text())
    print("  PIN      :", data.get("pin", "(missing)"))
PY
echo "It will start again when you log in. You can close the terminal."
