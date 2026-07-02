"""
Запуск Cloudflare Quick Tunnel без домена и без логина.

Работает как pyngrok: сам скачивает бинарник cloudflared (один раз,
кладёт рядом с проектом), запускает его сабпроцессом, парсит из его
логов публичный URL вида https://xxxx-xxxx-xxxx.trycloudflare.com.

Всё управление — чистый Python (subprocess), никакой ручной консоли
на хостинге не требуется.
"""
import logging
import os
import platform
import re
import stat
import subprocess
import threading
import time
import urllib.request
from typing import Optional

logger = logging.getLogger("netcore.cloudflared")

_TUNNEL_URL_RE = re.compile(r"https://[a-zA-Z0-9\-]+\.trycloudflare\.com")

_RELEASE_BASE = "https://github.com/cloudflare/cloudflared/releases/latest/download"

_ARCH_MAP = {
    ("linux", "x86_64"): "cloudflared-linux-amd64",
    ("linux", "amd64"): "cloudflared-linux-amd64",
    ("linux", "aarch64"): "cloudflared-linux-arm64",
    ("linux", "arm64"): "cloudflared-linux-arm64",
}

_process: Optional[subprocess.Popen] = None


def _asset_name() -> str:
    system = platform.system().lower()
    machine = platform.machine().lower()
    asset = _ARCH_MAP.get((system, machine))
    if not asset:
        raise RuntimeError(f"Неизвестная платформа для cloudflared: {system}/{machine}")
    return asset


def _ensure_binary(bin_path: str) -> str:
    if os.path.isfile(bin_path) and os.access(bin_path, os.X_OK):
        return bin_path
    asset = _asset_name()
    url = f"{_RELEASE_BASE}/{asset}"
    logger.info("Скачиваю cloudflared: %s", url)
    tmp_path = bin_path + ".download"
    urllib.request.urlretrieve(url, tmp_path)
    os.replace(tmp_path, bin_path)
    st = os.stat(bin_path)
    os.chmod(bin_path, st.st_mode | stat.S_IEXEC | stat.S_IXGRP | stat.S_IXOTH)
    logger.info("cloudflared скачан: %s", bin_path)
    return bin_path


def start_quick_tunnel(local_port: int, bin_path: str = "./cloudflared", timeout: float = 25.0) -> Optional[str]:
    """
    Запускает `cloudflared tunnel --url http://localhost:<port>` и
    возвращает публичный https://...trycloudflare.com URL, как только
    он появится в выводе процесса. При неудаче возвращает None.
    """
    global _process
    try:
        binary = _ensure_binary(bin_path)
    except Exception:
        logger.exception("Не удалось получить бинарник cloudflared")
        return None

    cmd = [binary, "tunnel", "--url", f"http://localhost:{local_port}", "--no-autoupdate"]
    logger.info("Запускаю: %s", " ".join(cmd))

    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    _process = proc

    found_url: dict = {"url": None}

    def _reader():
        assert proc.stdout is not None
        for line in proc.stdout:
            line = line.rstrip()
            if line:
                logger.info("[cloudflared] %s", line)
            m = _TUNNEL_URL_RE.search(line)
            if m and found_url["url"] is None:
                found_url["url"] = m.group(0)

    t = threading.Thread(target=_reader, daemon=True)
    t.start()

    waited = 0.0
    step = 0.25
    while waited < timeout and found_url["url"] is None:
        if proc.poll() is not None:
            logger.error("cloudflared завершился раньше времени (код %s)", proc.returncode)
            break
        time.sleep(step)
        waited += step

    return found_url["url"]


def stop_quick_tunnel():
    global _process
    if _process is not None and _process.poll() is None:
        try:
            _process.terminate()
        except Exception:
            pass
    _process = None
