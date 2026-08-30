#!/bin/sh
# Stop the background helper and remove the login item.
set -e
LABEL="com.localnotes.bridge"
PLIST="$HOME/Library/LaunchAgents/${LABEL}.plist"
UID_NUM="$(id -u)"

launchctl bootout "gui/${UID_NUM}/${LABEL}" 2>/dev/null || true
rm -f "$PLIST"

if command -v lsof >/dev/null 2>&1; then
  PIDS=$(lsof -nP -iTCP:18765 -sTCP:LISTEN -t 2>/dev/null || true)
  if [ -n "$PIDS" ]; then
    kill $PIDS 2>/dev/null || true
  fi
fi

echo "Helper is stopped and will not start at login."
echo "Your PIN is still in ~/.local-notes-bridge.json if you want it later."
