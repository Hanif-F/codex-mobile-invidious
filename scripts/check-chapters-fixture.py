#!/usr/bin/env python3
"""Verify the localhost chapter fixture and its forbidden-preview request tracing."""
import json
from fixture_support import fixture_server
from urllib.error import HTTPError
from urllib.request import Request, urlopen

def request(path, body=None):
    data = None if body is None else json.dumps(body).encode()
    with urlopen(Request(base + path, data=data, headers={'Content-Type': 'application/json'}), timeout=5) as response:
        return json.load(response)

with fixture_server() as base:
    request('/test/reset', {})
    assert 'storyboards' not in request('/api/v1/videos/testvideo01')
    request('/test/chapters', {})
    details = request('/api/v1/videos/testvideo01')
    assert details['description'].splitlines() == ['0:00 Introduction', '0:30 日本語 & details', '1:00 Final section']
    assert details['storyboards'][0]['url'] == '/api/v1/storyboards/testvideo01'
    assert request('/test/state')['chapterAssetRequests'] == []
    for path in ('/api/v1/storyboards/testvideo01', '/sb/fixture/M0.jpg'):
        try:
            request(path)
            raise AssertionError('Preview asset must be refused')
        except HTTPError as error:
            assert error.code == 404
    assert len(request('/test/state')['chapterAssetRequests']) == 2
    request('/test/reset', {})
    assert request('/test/state')['chapterAssetRequests'] == []
    assert 'storyboards' not in request('/api/v1/videos/testvideo01')
    print('Chapter description/legacy contracts and forbidden-preview request tracing passed (localhost only).')
