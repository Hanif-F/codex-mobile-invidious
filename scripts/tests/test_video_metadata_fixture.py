"""Video metadata failures are opt-in, single-use and reset with the fixture."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_support import fixture_server


class VideoMetadataFixtureTest(unittest.TestCase):
    def test_empty_response_recovers_and_reset_clears_pending_failures(self):
        with tempfile.TemporaryDirectory() as media, fixture_server(media) as base:
            def post(path, data=None):
                with urlopen(Request(base + path, data=json.dumps(data or {}).encode(),
                                     headers={'Content-Type': 'application/json'})) as response:
                    return json.load(response)

            def video():
                return urlopen(base + '/api/v1/videos/testvideo01')

            post('/test/stream', {'type': 'dash', 'emptyNext': True})
            with video() as response:
                self.assertEqual(200, response.status)
                self.assertEqual('application/json', response.headers['Content-Type'])
                self.assertEqual(b'', response.read())
            with video() as response:
                self.assertEqual('testvideo01', json.load(response)['videoId'])

            post('/test/stream', {'type': 'dash', 'emptyNext': True})
            post('/test/reset')
            with video() as response:
                self.assertEqual('testvideo01', json.load(response)['videoId'])

            post('/test/stream', {'type': 'dash', 'failNext': True})
            with self.assertRaises(HTTPError) as failure:
                video()
            self.assertEqual(503, failure.exception.code)
            failure.exception.close()
            with video() as response:
                self.assertEqual('testvideo01', json.load(response)['videoId'])


if __name__ == '__main__':
    unittest.main()
