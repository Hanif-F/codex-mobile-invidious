#!/usr/bin/env python3
"""Check public rich-video/channel/hashtag contracts against the disposable fixture."""
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
with socket.socket() as sock:
    sock.bind(('127.0.0.1', 0))
    port = sock.getsockname()[1]
base = f'http://127.0.0.1:{port}'

def request(path, body=None):
    data = None if body is None else json.dumps(body).encode()
    with urlopen(Request(base + path, data=data, headers={'Content-Type': 'application/json'}), timeout=5) as response:
        return json.load(response)

with tempfile.TemporaryDirectory() as media:
    process = subprocess.Popen([sys.executable, str(root / 'scripts/fixture-server.py'), '--port', str(port), '--media-dir', media],
                               stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    try:
        for attempt in range(50):
            try:
                request('/test/state')
                break
            except URLError:
                if process.poll() is not None:
                    raise RuntimeError(process.stderr.read().decode())
                time.sleep(.1)
        else:
            raise RuntimeError('Fixture did not start')
        request('/test/reset', {})
        request('/test/content-links', {})
        video = request('/api/v1/videos/testvideo01?local=false&region=ID')
        assert '<b>Rich description</b>' in video['descriptionHtml']
        assert all(target in video['descriptionHtml'] for target in ('/watch?', '/@fixture/shorts', '/hashtag/music'))
        assert video['likeCount'] == 42 and video['authorVerified'] is True
        assert video['isListed'] is False and video['license'] == ''
        assert video['allowedRegions'] == ['ID', 'US'] and video['musicTracks'][0]['song'] == 'Song'
        query = urlencode({'url': 'https://www.youtube.com/@fixture'})
        resolved = request('/api/v1/resolveurl?' + query)
        assert resolved['ucid'] == 'UC' + 'a' * 22
        request('/test/content-links', {'resolveFailNext': True})
        try:
            request('/api/v1/resolveurl?' + query)
            raise AssertionError('Resolution failure not returned')
        except HTTPError as failure:
            assert failure.code == 503
        assert request('/api/v1/resolveurl?' + query)['ucid'] == resolved['ucid']
        assert len(request('/api/v1/hashtag/music?page=1')['results']) == 60
        assert len(request('/api/v1/hashtag/music?page=2')['results']) == 1
        state = request('/test/state')
        assert not any(item['authorized'] for item in state['resolveRequests'] + state['hashtagRequests'])
        assert state['videoDetailRequests'][0]['query'] == {'local': ['false'], 'region': ['ID']}
        request('/test/reset', {})
        assert 'descriptionHtml' not in request('/api/v1/videos/testvideo01')
        print('Rich video metadata, public resolution/retry, hashtag pagination, request tracing and legacy fallback passed.')
    finally:
        process.terminate()
        process.wait(timeout=5)
