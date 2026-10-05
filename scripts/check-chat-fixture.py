#!/usr/bin/env python3
"""Exercise the isolated replay fixture; no upstream or real account requests."""
import json
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

root = Path(__file__).resolve().parent.parent
with socket.socket() as listener:
    listener.bind(('127.0.0.1', 0))
    port = listener.getsockname()[1]
base = f'http://127.0.0.1:{port}'

def request(path, body=None, method=None, auth=False):
    headers = {'Content-Type': 'application/json'}
    if auth:
        headers['Authorization'] = 'Bearer fixture-token'
    req = Request(base + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    with urlopen(req, timeout=5) as response:
        return json.load(response)

def expect_error(code, path, **kwargs):
    try:
        request(path, **kwargs)
        raise AssertionError(f'{path} unexpectedly succeeded')
    except HTTPError as error:
        assert error.code == code

with tempfile.TemporaryDirectory() as media:
    process = subprocess.Popen([sys.executable, str(root / 'scripts/fixture-server.py'), '--port', str(port), '--media-dir', media], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    try:
        for attempt in range(50):
            try:
                request('/test/state')
                break
            except URLError:
                if process.poll() is not None:
                    raise RuntimeError(process.stderr.read().decode())
                time.sleep(.1)
        request('/test/reset', {})
        assert request('/api/v1/videos/testvideo01')['liveChatReplay'] is False
        expect_error(404, '/api/v1/live_chat/testvideo01')
        request('/test/chat', {})
        assert request('/api/v1/videos/testvideo01')['liveChatReplay'] is True
        first = request('/api/v1/live_chat/testvideo01?offset_ms=0')
        assert [m['offsetMs'] for m in first['messages']] == [0, 5000, 10000, 15000]
        second = request('/api/v1/live_chat/testvideo01?' + urlencode({'offset_ms': 15000, 'continuation': first['continuation']}))
        assert second['removedIds'] == ['chat-0']
        assert second['messages'][-1]['id'] == 'chat-1'
        assert second['messages'][-1]['text'] == 'Updated membership'
        request('/test/chat', {'chatSparse': True})
        assert not request('/api/v1/live_chat/testvideo01')['messages']
        request('/test/chat', {'chatFailNext': True})
        expect_error(503, '/api/v1/live_chat/testvideo01')
        assert request('/api/v1/live_chat/testvideo01')['continuation']
        request('/api/v1/auth/chat_preferences', {'chat_word_blacklist': 'spam /bad/'}, method='PATCH', auth=True)
        saved = request('/api/v1/auth/preferences')
        assert saved['unrelated_setting'] == 'preserved' and saved['chat_word_blacklist'] == 'spam /bad/'
        expect_error(401, '/api/v1/auth/chat_timing/testvideo01')
        request('/api/v1/auth/chat_timing/testvideo01', {'offsetMs': -1234}, method='PUT', auth=True)
        assert request('/api/v1/auth/chat_timing/testvideo01', auth=True)['offsetMs'] == -1234
        assert request('/api/v1/auth/chat_timing/testvideo02', auth=True)['offsetMs'] == 0
        expect_error(400, '/api/v1/auth/chat_timing/testvideo01', body={'offsetMs': 3600001}, method='PUT', auth=True)
        request('/test/chat', {'chatScopeFail': True})
        expect_error(403, '/api/v1/auth/chat_timing/testvideo01', auth=True)
        assert all(event['authorization'] is None for event in request('/test/state')['chatRequests'])
        request('/test/reset', {})
        assert request('/test/state')['chatRequests'] == []
        print('Chat replay, sparse/replacement/removal/error chunks, authenticated sparse saves and timing isolation passed.')
    finally:
        process.terminate()
        process.wait(timeout=5)
