import http.server
import threading
import subprocess
import time
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PORT = 8798
OUT_DIR = Path("/Users/dongge/.gemini/antigravity/brain/559ad4bb-7b06-4120-93a6-f437888ab1ef")
OUT_DIR.mkdir(parents=True, exist_ok=True)

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

server = http.server.ThreadingHTTPServer(('127.0.0.1', PORT), Handler)
thread = threading.Thread(target=server.serve_forever, daemon=True)
thread.start()
time.sleep(1)

pages = [
    ("home", "ui_home.png", "http://127.0.0.1:8798/#home"),
    ("profile", "ui_profile.png", "http://127.0.0.1:8798/#profile"),
    ("order-detail", "ui_order_detail.png", "http://127.0.0.1:8798/#order-detail?id=PORD_VISUAL_ACTIVE"),
    ("coupons", "ui_coupons.png", "http://127.0.0.1:8798/#coupons"),
    ("safety", "ui_safety.png", "http://127.0.0.1:8798/#safety")
]

for name, filename, url in pages:
    out_path = OUT_DIR / filename
    cmd = [
        CHROME,
        "--headless=new",
        "--hide-scrollbars",
        "--window-size=430,932",
        f"--screenshot={str(out_path)}",
        url
    ]
    try:
        subprocess.run(cmd, check=True, timeout=15)
        print(f"Captured: {filename} ({out_path.stat().st_size} bytes)")
    except Exception as e:
        print(f"Failed to capture {filename}: {e}")

server.shutdown()
print("All screenshots completed.")
