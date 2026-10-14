#!/usr/bin/env python3
"""Local fixture process lifecycle shared by standalone contract checks."""

from contextlib import contextmanager
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time
from urllib.error import URLError
from urllib.request import urlopen


@contextmanager
def fixture_server(media_dir=None):
    with tempfile.TemporaryDirectory() as temporary, tempfile.TemporaryFile(mode='w+') as log:
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            port = listener.getsockname()[1]
        base = f'http://127.0.0.1:{port}'
        process = subprocess.Popen(
            [sys.executable, str(Path(__file__).with_name('fixture-server.py')),
             '--port', str(port), '--media-dir', str(media_dir or temporary)],
            stdout=subprocess.DEVNULL, stderr=log,
        )
        try:
            for attempt in range(100):
                if process.poll() is not None:
                    log.seek(0)
                    raise RuntimeError('Fixture exited before readiness:\n' + log.read())
                try:
                    with urlopen(base + '/test/state', timeout=1) as response:
                        if response.status == 200:
                            break
                except (URLError, OSError):
                    time.sleep(.05)
            else:
                raise RuntimeError('Fixture did not become ready')
            yield base
        finally:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
