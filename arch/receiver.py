#!/usr/bin/env python3
"""Recebe notificacoes do app Android via HTTP POST e as exibe com notify-send."""
import hmac
import json
import os
import subprocess
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = os.environ.get("NOTIF_BRIDGE_TOKEN", "")
HOST = os.environ.get("NOTIF_BRIDGE_HOST", "0.0.0.0")
PORT = int(os.environ.get("NOTIF_BRIDGE_PORT", "8787"))
MAX_BODY = 64 * 1024


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
        except (ValueError, AttributeError):
            return self._reply(400)

        summary = f"{app}: {title}" if title else app
        subprocess.run(["notify-send", "-a", app or "Notif Bridge", "--", summary, text], check=False)
        self._reply(204)

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))


if __name__ == "__main__":
    if not TOKEN:
        sys.exit("Defina NOTIF_BRIDGE_TOKEN (ex.: openssl rand -hex 32)")
    print(f"Escutando em {HOST}:{PORT}", file=sys.stderr)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
