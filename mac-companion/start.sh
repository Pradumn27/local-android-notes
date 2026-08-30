#!/bin/sh
# Start the Mac helper. Pass --pin 123456 to choose the phone PIN.
cd "$(dirname "$0")"
exec python3 notes_bridge.py "$@"
