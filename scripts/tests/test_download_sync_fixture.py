"""Opt-in download sync media and failure controls preserve normal fixture behavior."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_support import fixture_server


class DownloadSyncFixtureTest(unittest.TestCase):
    def test_opt_in_long_download_failures_recovery_and_reset(self):
        with tempfile.TemporaryDirectory() as media, fixture_server(media) as base:
            downloads = Path(media) / 'downloads'
            downloads.mkdir()
            for name in ('video360.mp4', 'video144.mp4', 'video-vp8.webm', 'audioen.m4a', 'audioes.m4a', 'audioen-long.m4a'):
                (downloads / name).write_bytes(b'fixture')

            def request(path, data=None, method=None):
                req = Request(base + path, data=None if data is None else json.dumps(data).encode(), method=method,
                              headers={'Content-Type': 'application/json', 'Authorization': 'Bearer fixture-token'})
                with urlopen(req) as response:
                    body = response.read()
                    return json.loads(body) if body else None

            self.assertEqual(12, request('/api/v1/videos/testvideo01/downloads')['video']['lengthSeconds'])
            request('/test/download-sync', {'downloadSync': True, 'playbackWriteStatus': 503, 'playbackReadStatus': 401})
            catalog = request('/api/v1/videos/testvideo01/downloads')
            self.assertEqual(120, catalog['video']['lengthSeconds'])
            self.assertEqual('audioen-long.m4a', next(c for c in catalog['choices'] if c['key'] == 'aen')['filename'])
            with self.assertRaises(HTTPError) as failure:
                request('/api/v1/auth/playback/testvideo01', {'position': 45}, 'PUT')
            self.assertEqual(503, failure.exception.code)
            failure.exception.close()
            with self.assertRaises(HTTPError) as failure:
                request('/api/v1/auth/playback/testvideo01')
            self.assertEqual(401, failure.exception.code)
            failure.exception.close()
            request('/test/download-sync', {'playbackWriteStatus': 0, 'playbackReadStatus': 0})
            request('/api/v1/auth/playback/testvideo01', {'position': 45}, 'PUT')
            request('/api/v1/auth/history/testvideo01', {}, 'POST')
            self.assertEqual(45, request('/api/v1/auth/playback/testvideo01')['position'])
            request('/test/reset', {})
            self.assertEqual(12, request('/api/v1/videos/testvideo01/downloads')['video']['lengthSeconds'])
            self.assertEqual({}, request('/api/v1/auth/playback')['positions'])
            request('/api/v1/auth/playback/testvideo01', {'position': 20}, 'PUT')
            self.assertEqual(20, request('/api/v1/auth/playback/testvideo01')['position'])


if __name__ == '__main__':
    unittest.main()
