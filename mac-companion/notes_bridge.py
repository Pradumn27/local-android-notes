#!/usr/bin/env python3
"""Wi-Fi bridge between the Android Notes app and Notes.app on this Mac."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import random
import secrets
import socket
import subprocess
import sys
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

PORT = 18765
BEACON_PORT = 18766
PROTOCOL_VERSION = 1
HERE = Path(__file__).resolve().parent
JXA = HERE / "jxa" / "notes_ops.js"
STATE_PATH = Path.home() / ".local-notes-bridge.json"
AGENT_LABEL = "com.localnotes.bridge"
AGENT_PLIST = Path.home() / "Library/LaunchAgents" / f"{AGENT_LABEL}.plist"
NOTES_DIR = Path.home() / "Library/Group Containers/group.com.apple.notes"
NOTES_LOCK = threading.Lock()
CACHE_LOCK = threading.Lock()
ATT_ROOT = Path.home() / "Library/Caches/local-notes-bridge/attachments"
MAX_EMBED_BYTES = 6 * 1024 * 1024
MEDIA_CACHE: dict[str, dict] = {}


def computer_name() -> str:
    try:
        return subprocess.check_output(
            ["scutil", "--get", "ComputerName"], text=True
        ).strip()
    except Exception:
        return socket.gethostname()


def lan_ip() -> str:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 80))
        return sock.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        sock.close()


def run_jxa(payload: dict) -> dict:
    if not JXA.exists():
        raise RuntimeError(f"missing JXA helper at {JXA}")
    with tempfile.NamedTemporaryFile(
        "w", suffix=".json", delete=False, encoding="utf-8"
    ) as handle:
        json.dump(payload, handle)
        path = handle.name
    try:
        completed = subprocess.run(
            ["osascript", "-l", "JavaScript", str(JXA), path],
            check=False,
            capture_output=True,
            text=True,
            timeout=120,
        )
    finally:
        os.unlink(path)
    if completed.returncode != 0:
        err = (completed.stderr or completed.stdout or "osascript failed").strip()
        raise RuntimeError(err)
    raw = (completed.stdout or "").strip()
    if not raw:
        raise RuntimeError("Notes helper returned no output")
    parsed = json.loads(raw)
    if not parsed.get("ok"):
        raise RuntimeError(parsed.get("error") or "Notes helper failed")
    return parsed.get("result") or {}


def mime_for(path: Path) -> str:
    ext = path.suffix.lower()
    return {
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".gif": "image/gif",
        ".heic": "image/heic",
        ".heif": "image/heif",
        ".webp": "image/webp",
        ".pdf": "application/pdf",
        ".m4a": "audio/mp4",
        ".mp3": "audio/mpeg",
        ".wav": "audio/wav",
        ".caf": "audio/x-caf",
        ".aac": "audio/aac",
        ".mov": "video/quicktime",
        ".mp4": "video/mp4",
    }.get(ext, "application/octet-stream")


def prepare_attachment(src: Path, dest_dir: Path) -> Path | None:
    if not src.exists() or src.stat().st_size <= 0:
        return None
    mime = mime_for(src)
    if mime.startswith("image/"):
        out = dest_dir / (src.stem + ".jpg")
        try:
            subprocess.run(
                ["sips", "-s", "format", "jpeg", "-Z", "1600", str(src), "--out", str(out)],
                check=False,
                capture_output=True,
                timeout=30,
            )
            if out.exists() and out.stat().st_size > 0:
                return out
        except Exception:
            pass
    if src.stat().st_size > MAX_EMBED_BYTES:
        return None
    return src


def data_uri_for(path: Path) -> str | None:
    try:
        raw = path.read_bytes()
    except OSError:
        return None
    if not raw or len(raw) > MAX_EMBED_BYTES:
        return None
    import base64

    mime = mime_for(path)
    return "data:%s;base64,%s" % (mime, base64.b64encode(raw).decode("ascii"))


def attachment_key(atts: list) -> tuple:
    return tuple(
        (str(item.get("id") or ""), str(item.get("name") or ""), str(item.get("contentIdentifier") or ""))
        for item in atts
    )


def load_media_entry(apple_id: str, atts: list) -> dict:
    dest = ATT_ROOT / hashlib.sha1(apple_id.encode("utf-8", "replace")).hexdigest()
    dest.mkdir(parents=True, exist_ok=True)
    existing = [p for p in dest.iterdir() if p.is_file() and p.stat().st_size > 0]
    if len(existing) < len(atts):
        try:
            run_jxa({"op": "export_attachments", "appleId": apple_id, "destDir": str(dest)})
        except Exception as exc:
            sys.stderr.write("export attachments failed: %s\n" % exc)
        existing = [p for p in dest.iterdir() if p.is_file() and p.stat().st_size > 0]
    files = []
    prepared_dir = dest / "ready"
    prepared_dir.mkdir(exist_ok=True)
    for path in sorted(existing):
        if path.parent == prepared_dir:
            continue
        ready = prepare_attachment(path, prepared_dir)
        if ready is None:
            continue
        uri = data_uri_for(ready)
        if not uri:
            continue
        files.append(
            {
                "name": path.name,
                "mime": mime_for(ready),
                "uri": uri,
            }
        )
    extras = []
    for item in files:
        mime = item["mime"]
        uri = item["uri"]
        name = item["name"]
        safe_name = (
            name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")
        )
        if mime.startswith("image/"):
            extras.append('<div><img src="%s" alt="%s"/></div>' % (uri, safe_name))
        elif mime.startswith("audio/"):
            extras.append('<div><audio controls src="%s"></audio></div>' % uri)
        else:
            extras.append(
                '<div><a href="%s" download="%s">%s</a></div>' % (uri, safe_name, safe_name)
            )
    return {"extras": "".join(extras), "files": files}


def embed_note_media(snapshot: dict, *, full: bool) -> dict:
    if not snapshot or snapshot.get("passwordProtected"):
        return snapshot
    apple_id = snapshot.get("appleId")
    if not apple_id:
        return snapshot
    atts = snapshot.get("attachments") or []
    if not atts:
        return snapshot
    key = attachment_key(atts)
    entry = MEDIA_CACHE.get(apple_id)
    if entry is None or entry.get("key") != key:
        if not full:
            return snapshot
        entry = {"key": key, **load_media_entry(apple_id, atts)}
        MEDIA_CACHE[apple_id] = entry
    extras = entry.get("extras") or ""
    html = snapshot.get("html") or ""
    if extras and extras not in html:
        snapshot = dict(snapshot)
        snapshot["html"] = html + extras
    return snapshot


def notes_disk_stamp() -> str:
    stamps = []
    for path in (
        NOTES_DIR,
        Path.home() / "Library/Containers/com.apple.Notes/Data/Library/Notes",
    ):
        try:
            stamps.append(str(path.stat().st_mtime_ns))
        except OSError:
            continue
    return ":".join(stamps) if stamps else str(int(time.time() * 1000))


def normalize_pin(raw: str) -> str:
    digits = "".join(ch for ch in str(raw) if ch.isdigit())
    if len(digits) < 4 or len(digits) > 8:
        raise SystemExit("PIN must be 4 to 8 digits")
    return digits


def load_saved() -> dict:
    if not STATE_PATH.exists():
        return {}
    try:
        return json.loads(STATE_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {}


class BridgeState:
    def __init__(self, pin: str | None = None, new_pin: bool = False) -> None:
        saved = load_saved()
        self.device_name = computer_name()
        env_pin = os.environ.get("NOTES_BRIDGE_PIN")
        if pin:
            self.pin = normalize_pin(pin)
        elif new_pin:
            self.pin = f"{random.randint(0, 999999):06d}"
        elif env_pin:
            self.pin = normalize_pin(env_pin)
        elif saved.get("pin"):
            self.pin = normalize_pin(saved["pin"])
        else:
            self.pin = f"{random.randint(0, 999999):06d}"
        self.token = str(saved.get("token") or secrets.token_hex(16))
        self.host = lan_ip()
        self.catalog: dict = {"folders": [], "notes": []}
        self.revision = "0"
        self.live: dict | None = None
        self.live_hash = ""
        self.persist()

    def persist(self) -> None:
        STATE_PATH.write_text(
            json.dumps({"pin": self.pin, "token": self.token}),
            encoding="utf-8",
        )
        try:
            os.chmod(STATE_PATH, 0o600)
        except OSError:
            pass

    def set_catalog(self, catalog: dict) -> None:
        notes = catalog.get("notes") or []
        folders = catalog.get("folders") or []
        newest = "0"
        for note in notes:
            modified = str(note.get("modifiedAt") or "")
            if modified > newest:
                newest = modified
        self.catalog = catalog
        disk = notes_disk_stamp()
        live_part = self.live_hash or "0"
        self.revision = f"{newest}|{len(notes)}|{len(folders)}|{disk}|{live_part}"


STATE: BridgeState


def refresh_catalog() -> dict:
    with NOTES_LOCK:
        catalog = run_jxa({"op": "catalog"})
    with CACHE_LOCK:
        STATE.set_catalog(catalog)
    return catalog


def cached_catalog() -> dict:
    with CACHE_LOCK:
        if STATE.catalog.get("notes") or STATE.catalog.get("folders"):
            return STATE.catalog
    return refresh_catalog()


def authorized(handler: BaseHTTPRequestHandler) -> bool:
    header = handler.headers.get("X-Notes-Token") or handler.headers.get("Authorization", "")
    if header.startswith("Bearer "):
        header = header[7:]
    return header == STATE.token


class Handler(BaseHTTPRequestHandler):
    server_version = "LocalNotesBridge/1"

    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _send(self, code: int, body: dict) -> None:
        data = json.dumps(body).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _read_json(self) -> dict:
        length = int(self.headers.get("Content-Length") or "0")
        if length <= 0:
            return {}
        raw = self.rfile.read(length)
        if not raw:
            return {}
        return json.loads(raw.decode("utf-8"))

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/v1/hello":
            self._send(
                200,
                {
                    "protocolVersion": PROTOCOL_VERSION,
                    "deviceName": STATE.device_name,
                    "port": PORT,
                    "pinRequired": True,
                    "live": True,
                    "tokenValid": authorized(self),
                },
            )
            return
        if not authorized(self):
            self._send(401, {"error": "pin required", "needsPin": True})
            return
        qs = parse_qs(parsed.query)
        try:
            if parsed.path == "/v1/revision":
                with CACHE_LOCK:
                    revision = STATE.revision
                    live = STATE.live
                self._send(
                    200,
                    {
                        "revision": revision,
                        "liveAppleId": (live or {}).get("appleId"),
                    },
                )
                return
            if parsed.path == "/v1/live":
                with CACHE_LOCK:
                    live = STATE.live
                if not live:
                    self._send(200, {"active": False})
                    return
                self._send(200, live)
                return
            if parsed.path == "/v1/catalog":
                self._send(200, cached_catalog())
                return
            if parsed.path == "/v1/notes":
                apple_id = (qs.get("id") or [None])[0]
                if not apple_id:
                    self._send(400, {"error": "id required"})
                    return
                with CACHE_LOCK:
                    live = STATE.live
                if live and live.get("appleId") == apple_id:
                    self._send(200, live)
                    return
                with NOTES_LOCK:
                    result = run_jxa({"op": "get", "appleId": apple_id})
                self._send(200, embed_note_media(result, full=True))
                return
            self._send(404, {"error": "not found"})
        except Exception as exc:
            self._send(500, {"error": str(exc)})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        try:
            body = self._read_json()
        except Exception:
            self._send(400, {"error": "invalid json"})
            return
        if parsed.path == "/v1/pair":
            pin = str(body.get("pin") or "").strip()
            if pin != STATE.pin:
                time.sleep(0.4)
                self._send(403, {"error": "wrong pin"})
                return
            self._send(
                200,
                {
                    "token": STATE.token,
                    "deviceName": STATE.device_name,
                    "protocolVersion": PROTOCOL_VERSION,
                    "live": True,
                },
            )
            return
        if not authorized(self):
            self._send(401, {"error": "pin required", "needsPin": True})
            return
        try:
            if parsed.path == "/v1/folders":
                with NOTES_LOCK:
                    result = run_jxa(
                        {
                            "op": "create_folder",
                            "name": body.get("name") or "New Folder",
                            "accountAppleId": body.get("accountAppleId"),
                        }
                    )
                refresh_catalog()
                self._send(200, result)
                return
            if parsed.path == "/v1/notes/move":
                with NOTES_LOCK:
                    result = run_jxa(
                        {
                            "op": "move",
                            "appleId": body.get("appleId"),
                            "folderAppleId": body.get("folderAppleId"),
                        }
                    )
                refresh_catalog()
                self._send(200, result)
                return
            self._send(404, {"error": "not found"})
        except Exception as exc:
            self._send(500, {"error": str(exc)})

    def do_PUT(self) -> None:
        if not authorized(self):
            self._send(401, {"error": "pin required", "needsPin": True})
            return
        parsed = urlparse(self.path)
        try:
            body = self._read_json()
        except Exception:
            self._send(400, {"error": "invalid json"})
            return
        if parsed.path != "/v1/notes":
            self._send(404, {"error": "not found"})
            return
        try:
            with NOTES_LOCK:
                result = run_jxa(
                    {
                        "op": "upsert",
                        "appleId": body.get("appleId"),
                        "folderAppleId": body.get("folderAppleId"),
                        "html": body.get("html") or "<div><br></div>",
                    }
                )
            refresh_catalog()
            self._send(200, result)
        except Exception as exc:
            self._send(500, {"error": str(exc)})

    def do_DELETE(self) -> None:
        if not authorized(self):
            self._send(401, {"error": "pin required", "needsPin": True})
            return
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)
        apple_id = (qs.get("id") or [None])[0]
        if parsed.path != "/v1/notes" or not apple_id:
            self._send(400, {"error": "id required"})
            return
        try:
            with NOTES_LOCK:
                result = run_jxa({"op": "delete", "appleId": apple_id})
            refresh_catalog()
            self._send(200, result)
        except Exception as exc:
            self._send(500, {"error": str(exc)})


def beacon_loop(stop: threading.Event) -> None:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    while not stop.is_set():
        STATE.host = lan_ip()
        message = json.dumps(
            {
                "service": "local-notes",
                "v": PROTOCOL_VERSION,
                "name": STATE.device_name,
                "port": PORT,
                "host": STATE.host,
            }
        ).encode("utf-8")
        try:
            sock.sendto(message, ("255.255.255.255", BEACON_PORT))
            sock.sendto(message, (STATE.host.rsplit(".", 1)[0] + ".255", BEACON_PORT))
        except OSError:
            pass
        stop.wait(2.0)
    sock.close()


def apply_live_snapshot(snapshot: dict) -> bool:
    if not snapshot or not snapshot.get("active"):
        return False
    fingerprint = snapshot.get("fingerprint") or snapshot.get("plaintext") or ""
    digest = hashlib.sha1(fingerprint.encode("utf-8", "replace")).hexdigest()
    with CACHE_LOCK:
        if digest == STATE.live_hash:
            return False
        STATE.live = snapshot
        STATE.live_hash = digest
        # Keep catalog timestamps moving so a full pull also sees this note as newer.
        notes = (STATE.catalog.get("notes") or [])
        for note in notes:
            if note.get("appleId") == snapshot.get("appleId"):
                note["modifiedAt"] = snapshot.get("modifiedAt") or note.get("modifiedAt")
                note["title"] = snapshot.get("title") or note.get("title")
                break
        STATE.set_catalog(STATE.catalog)
        STATE.revision = f"live|{snapshot.get('appleId')}|{digest}|{int(time.time() * 1000)}"
    return True


def watch_live(stop: threading.Event) -> None:
    while not stop.is_set():
        try:
            with NOTES_LOCK:
                snapshot = run_jxa({"op": "live"})
            apply_live_snapshot(snapshot)
        except Exception as exc:
            sys.stderr.write("live snapshot failed: %s\n" % exc)
        stop.wait(0.4)


def watch_notes(stop: threading.Event) -> None:
    last = notes_disk_stamp()
    last_refresh = 0.0
    while not stop.is_set():
        stamp = notes_disk_stamp()
        now = time.time()
        changed = stamp != last
        stale = now - last_refresh > 6.0
        if changed or stale:
            if changed:
                stop.wait(0.8)
            try:
                refresh_catalog()
                last = notes_disk_stamp()
                last_refresh = time.time()
            except Exception as exc:
                sys.stderr.write("catalog refresh failed: %s\n" % exc)
        stop.wait(0.7)


def write_launch_agent() -> None:
    python = sys.executable or "/usr/bin/python3"
    AGENT_PLIST.parent.mkdir(parents=True, exist_ok=True)
    log_dir = Path.home() / "Library/Logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    plist = f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>{AGENT_LABEL}</string>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>WorkingDirectory</key>
  <string>{HERE}</string>
  <key>ProgramArguments</key>
  <array>
    <string>{python}</string>
    <string>{HERE / "notes_bridge.py"}</string>
  </array>
  <key>EnvironmentVariables</key>
  <dict>
    <key>PYTHONUNBUFFERED</key>
    <string>1</string>
    <key>LAUNCHED_BY_LAUNCHD</key>
    <string>1</string>
  </dict>
  <key>StandardOutPath</key>
  <string>{log_dir / "local-notes-bridge.log"}</string>
  <key>StandardErrorPath</key>
  <string>{log_dir / "local-notes-bridge.log"}</string>
</dict>
</plist>
"""
    AGENT_PLIST.write_text(plist, encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Talk to Notes.app and wait for the Android Notes app on Wi-Fi.",
    )
    parser.add_argument(
        "--pin",
        help="Choose the PIN the phone will type (4-8 digits). Saved for next time.",
    )
    parser.add_argument(
        "--new-pin",
        action="store_true",
        help="Throw away the saved PIN and print a new random one.",
    )
    return parser.parse_args()


def main() -> int:
    global STATE
    args = parse_args()
    STATE = BridgeState(pin=args.pin, new_pin=args.new_pin)
    if not JXA.exists():
        print(f"missing {JXA}", file=sys.stderr)
        return 1
    write_launch_agent()
    try:
        refresh_catalog()
    except Exception as exc:
        print(f"  Notes is not ready yet ({exc}). The bridge will retry.", file=sys.stderr)
    stop = threading.Event()
    threading.Thread(target=beacon_loop, args=(stop,), daemon=True).start()
    threading.Thread(target=watch_notes, args=(stop,), daemon=True).start()
    threading.Thread(target=watch_live, args=(stop,), daemon=True).start()
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print()
    print("  Local Notes — Mac bridge")
    print(f"  Device : {STATE.device_name}")
    print(f"  Address: http://{STATE.host}:{PORT}")
    print(f"  PIN    : {STATE.pin}")
    print()
    print("  This PIN is yours. Type it on the phone under Folders → gear.")
    print("  Change it anytime, then reinstall the login item:")
    print("      python3 notes_bridge.py --pin 482916")
    print("      ./install-login-item.sh")
    print("  Or set NOTES_BRIDGE_PIN. It is stored in ~/.local-notes-bridge.json")
    print()
    if os.environ.get("LAUNCHED_BY_LAUNCHD"):
        print("  Running in the background via launchd. Close the terminal.")
        print("  Log: ~/Library/Logs/local-notes-bridge.log")
        print("  Stop: ./uninstall-login-item.sh")
    else:
        print("  This is a one-off terminal run. Closing this window stops sync.")
        print("  To keep it running after you log out or close the terminal:")
        print("      ./install-login-item.sh")
    print()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nstopped")
    finally:
        stop.set()
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
