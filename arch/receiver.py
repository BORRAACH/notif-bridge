#!/usr/bin/env python3
"""Recebe notificacoes do app Android via HTTP POST e as exibe com notify-send."""
import hmac
import json
import os
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = os.environ.get("NOTIF_BRIDGE_TOKEN", "")
HOST = os.environ.get("NOTIF_BRIDGE_HOST", "0.0.0.0")
PORT = int(os.environ.get("NOTIF_BRIDGE_PORT", "8787"))
MAX_BODY = 64 * 1024
STATUS_FILE = Path(os.environ.get("XDG_RUNTIME_DIR", "/tmp")) / "notif-bridge" / "status.json"


STARTED_AT = int(time.time())
MAX_DEVICES = 20
_lock = threading.Lock()
_devices = []


def _load_devices():
    """Recupera o historico de aparelhos de antes de um restart do servico."""
    try:
        data = json.loads(STATUS_FILE.read_text())
        _devices.extend(d for d in data.get("devices", []) if isinstance(d, dict))
    except (OSError, ValueError, AttributeError):
        pass


def write_status(device=None, ip=None, app=""):
    """Grava estado e historico de aparelhos, para a UI do desktop (ex.: Caelestia) ler."""
    with _lock:
        now = int(time.time())
        if device is not None:
            entry = next((d for d in _devices if d.get("device") == device and d.get("ip") == ip), None)
            if entry is None:
                entry = {"device": device, "ip": ip, "firstSeen": now, "count": 0}
                _devices.append(entry)
            entry.update(lastSeen=now, lastApp=app, count=entry.get("count", 0) + 1)
            _devices.sort(key=lambda d: d.get("lastSeen", 0), reverse=True)
            del _devices[MAX_DEVICES:]
        last = _devices[0] if _devices else {}
        status = {
            "startedAt": STARTED_AT,
            "host": HOST,
            "port": PORT,
            "device": last.get("device", ""),
            "ip": last.get("ip", ""),
            "lastSeen": last.get("lastSeen", 0),
            "devices": _devices,
        }
        try:
            STATUS_FILE.parent.mkdir(parents=True, exist_ok=True)
            tmp = STATUS_FILE.with_suffix(".tmp")
            tmp.write_text(json.dumps(status))
            tmp.replace(STATUS_FILE)
        except OSError as e:
            sys.stderr.write(f"status: {e}\n")


def clear_history(*_):
    """SIGHUP: esquece o historico de aparelhos."""
    with _lock:
        _devices.clear()
    write_status()


class Handler(BaseHTTPRequestHandler):
    def _reply(self, code):
        self.send_response(code)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_POST(self):
        auth = self.headers.get("Authorization", "")
        if self.path != "/notify" or not hmac.compare_digest(auth, f"Bearer {TOKEN}"):
            return self._reply(403)

        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= MAX_BODY:
                return self._reply(400)
            data = json.loads(self.rfile.read(length))
            app = str(data.get("app", ""))[:100]
            title = str(data.get("title", ""))[:200]
            text = str(data.get("text", ""))[:1000]
            device = str(data.get("device", ""))[:100]
        except (ValueError, AttributeError):
            return self._reply(400)

        write_status(device or self.client_address[0], self.client_address[0], app)
        summary = f"{app}: {title}" if title else app
        subprocess.run(["notify-send", "-a", app or "Notif Bridge", "--", summary, text], check=False)
        self._reply(204)

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))


if __name__ == "__main__":
    if not TOKEN:
        sys.exit("Defina NOTIF_BRIDGE_TOKEN (ex.: openssl rand -hex 32)")
    _load_devices()
    signal.signal(signal.SIGHUP, clear_history)
    write_status()
    print(f"Escutando em {HOST}:{PORT}", file=sys.stderr)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
