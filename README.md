# Local Notes

Sync **Apple Notes on your Mac** with an Android app that looks and organizes like Notes. The two devices talk over your home Wi-Fi. Nothing is uploaded to a cloud you do not already use — the Mac helper reads and writes Notes.app the same way you do, then the phone keeps a local copy.

Use it if you live in Apple Notes on a Mac and want the same folders and notes on an Android phone, including a home-screen widget.

## How it fits together

```
  Mac                              same Wi-Fi                         Android
 ┌─────────────────────┐         PIN once, then           ┌──────────────────────┐
 │ Notes.app           │         automatic                │ Notes app            │
 │        ▲            │                                  │ folders / editor     │
 │        │ AppleScript│         JSON over HTTP           │        ▲             │
 │        ▼            │     ← ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ►      │        │ Room         │
 │ notes_bridge.py     │         port 18765               │ live listener        │
 │ (this repo)         │                                  │ (opt-in)             │
 └─────────────────────┘                                  │ widgets              │
                                                          └──────────────────────┘
```

1. A small Python helper on the Mac talks to Notes.app.
2. The Android app finds that helper on the LAN and pairs with a PIN **you choose**.
3. After that, edits move both ways while the helper is running and the phone can see the Mac.
4. Home-screen widgets stay live **only if you turn that on** and allow notifications.

## What you need

- A Mac with Notes signed in (iCloud or On My Mac)
- Python 3 (already on macOS)
- An Android phone (Android 8 / API 26 or newer)
- Both devices on the **same Wi-Fi**
- Android Studio, or just the Android SDK + JDK 17, to install the app

The first time the helper runs, macOS will ask whether Terminal / Python may control Notes. Click **OK**. If the Mac firewall asks about incoming connections, allow it.

## 1. Choose a PIN and start the Mac helper

The PIN is not random-forever unless you want it to be. You pick it.

```bash
cd mac-companion
chmod +x start.sh

# Pick a PIN you will type on the phone (4–8 digits)
./start.sh --pin 482916
```

The window prints something like:

```
  Device : Pradumn’s MacBook Air
  Address: http://192.168.1.102:18765
  PIN    : 482916
```

**Keep that window open** while you use the phone.

### Other ways to set the PIN

| How | Example |
|---|---|
| Flag (saved for next launch) | `./start.sh --pin 482916` |
| Environment variable | `NOTES_BRIDGE_PIN=482916 ./start.sh` |
| Edit the saved file | `~/.local-notes-bridge.json` → `"pin": "482916"` |
| New random PIN | `./start.sh --new-pin` |

The PIN and a pairing token live only on your Mac in `~/.local-notes-bridge.json` (mode `600`). Changing the PIN does not wipe notes. The phone must type the current PIN once if the token was never stored, or if you deleted that file.

To start the helper at login (optional):

```bash
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.localnotes.bridge.plist
```

The helper writes that plist the first time it runs. After that, log out/in and it comes back with the **same PIN**.

## 2. Install the Android app

On the Mac, with the phone unlocked and USB debugging on:

```bash
# from the repo root
./gradlew :app:installDebug
```

Or open the folder in Android Studio and click Run.

If `adb` says the device vanished mid-install (common on Samsung):

- Unlock the phone and leave the screen on
- Notification shade → USB → **Transferring files** or **MIDI**
- Then: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Do not run `adb kill-server` on a flaky Samsung cable.

## 3. Pair the phone

1. Open **Notes** on the phone.
2. Go to **Folders** (back arrow if you are in a list).
3. Tap the **gear**.
4. If your Mac appears under Nearby, tap it. Otherwise type the address the helper printed (`192.168.1.102`).
5. Type the **PIN you chose** (for example `482916`).
6. Tap **Connect**.

The first sync copies folders and notes from the Mac. After that, with the helper running and both on the same Wi-Fi:

- Type on the phone → the Mac note updates
- Type in Notes.app → the phone updates while the app is open
- Delete / move / new folder follow the same path

You should not need **Sync Now** unless something was offline.

## 4. Widgets (optional, opt-in)

There are two widgets:

- **Note** — one note, with lists and checklists kept as lines. Scroll the body; tap a line or the title to open it.
- **Folder** — a folder and its latest notes.

Add them: long-press the home screen → **Widgets** → **Notes**. Long-press a widget later to pick a different note or folder.

### Live widget updates

By default the widget does **not** keep a listener running. It updates when you open the app (or when the app is already in memory).

To let the widget follow Mac typing **while Notes is closed**:

1. Pair the phone (step 3).
2. Folders → gear.
3. Tap **Enable live widgets**.
4. Allow **notifications** when Android asks.

A quiet “Notes is live” notification means the listener is on. The widget then refreshes as the Mac helper sees edits.

Turn it off the same place: **Turn off live widgets**. No notification, no background listener.

On Samsung, if you enable live widgets and they still freeze after a few minutes: **Settings → Apps → Notes → Battery → Unrestricted**.

## Everyday use

| You do | What should happen |
|---|---|
| Edit a note on the Mac | Phone (and live widget, if enabled) updates in about a second |
| Edit on the phone | Mac Notes.app updates after you pause typing |
| Create a folder on either side | It appears on the other side |
| Delete a note on the phone | Mac moves it to Recently Deleted |
| Lock a note on the Mac | Phone stores the title only |

Locked / password-protected notes are not decrypted.

Last write wins if you type in the same note on both devices at once.

## Privacy

- Traffic stays on your LAN (`http://<mac>:18765`).
- The phone must send the PIN once; later requests use a token stored on the device.
- Anyone on the same Wi-Fi still cannot read notes without that PIN/token.
- The helper never sends notes to a server besides your own Mac.

## Project layout

```
app/                  Android app (Kotlin, Jetpack Compose, Room)
mac-companion/
  notes_bridge.py     Mac helper (Python 3, stdlib only)
  jxa/notes_ops.js    Talks to Notes.app
  start.sh            Convenience launcher
```

## Troubleshooting

**Phone cannot find the Mac**  
Same Wi-Fi? Helper window still open? Type the printed address by hand. Allow Python incoming connections in System Settings → Network → Firewall.

**“Enter the PIN again”**  
You deleted `~/.local-notes-bridge.json` or started with `--new-pin`. Type the new PIN on the phone.

**macOS “osascript is not allowed to send keystrokes”**  
Ignore it. The helper does not send keystrokes anymore. It only needs permission to **control Notes**.

**USB install fails with `device not found`**  
Samsung dropped ADB. Unlock, set USB to MIDI or file transfer, install the APK with `adb install -r` (do not restart the adb server).

**Widget does not move unless I open the app**  
Live widgets are off, or notifications were denied. Folders → gear → Enable live widgets → allow notifications. After you leave Wi-Fi and come back, the listener reconnects on its own; leave the “Notes is live” notification on.

**I switched Wi-Fi, edited on the Mac, then came back — widget stayed stale**  
Turn live widgets on (notifications allowed) and set **Settings → Apps → Notes → Battery → Unrestricted**. The helper must still be running on the Mac. The phone now reconnects when Wi-Fi returns and pulls whatever changed while you were gone.

**Notes.app did not update from the phone**  
The helper must be running. Check the terminal still shows the PIN banner.

## License

MIT. See [LICENSE](LICENSE).
