"""Opt-in view-count responses use the same fields as the production API."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
from urllib.request import Request, urlopen

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_support import fixture_server


class ViewCountsFixtureTest(unittest.TestCase):
    def test_counts_cover_nested_sources_history_duplicates_and_pagination(self):
        with tempfile.TemporaryDirectory() as media, fixture_server(media) as base:
            downloads = Path(media) / 'downloads'
            downloads.mkdir()
            for filename in ('video360.mp4', 'video144.mp4', 'video-vp8.webm', 'audioen.m4a', 'audioes.m4a'):
                (downloads / filename).write_bytes(b'catalog size fixture')
            def get(path):
                with urlopen(Request(base + path, headers={'Authorization': 'Bearer fixture-token'})) as response:
                    return json.load(response)

            def post(path, data=None):
                with urlopen(Request(base + path, data=json.dumps(data or {}).encode(),
                                     headers={'Content-Type': 'application/json'})) as response:
                    return json.load(response)

            self.assertEqual(1200, get('/api/v1/popular')[0]['viewCount'])
            post('/test/view-counts')
            details = get('/api/v1/videos/testvideo01')
            self.assertEqual(0, details['viewCount'])
            self.assertEqual('1.2K', details['recommendedVideos'][0]['viewCountText'])
            self.assertNotIn('viewCount', details['recommendedVideos'][0])
            self.assertEqual('watching', details['recommendedVideos'][1]['viewCountType'])
            self.assertEqual('unknown', details['recommendedVideos'][1]['viewCountPrecision'])
            self.assertNotIn('viewCount', details['recommendedVideos'][2])
            first = get('/api/v1/playlists/PLcounts')
            second = get('/api/v1/playlists/PLcounts?index=2')
            self.assertEqual(9000, first['viewCount'])
            self.assertEqual(0, first['videos'][0]['viewCount'])
            self.assertEqual(first['videos'][0]['videoId'], second['videos'][0]['videoId'])
            self.assertEqual([2, 3], [row['index'] for row in second['videos']])
            self.assertNotIn('viewCount', second['videos'][1])
            history = get('/api/v1/auth/history?organized=true&details=true&max_results=10')
            self.assertEqual({'views', 'watching', None}, {entry.get('viewCountType') for entry in history['entries']})
            mix = get('/api/v1/mixes/RDfixture')['videos']
            self.assertEqual(0, mix[0]['viewCount'])
            self.assertEqual('1.2K', mix[1]['viewCountText'])
            post('/test/clips', {'count': 1})
            clip = get('/api/v1/auth/clips')[0]
            self.assertNotIn('viewCount', clip)
            self.assertEqual(0, clip['video']['viewCount'])
            self.assertEqual(0, get('/api/v1/videos/testvideo01/downloads')['video']['viewCount'])
            self.assertEqual(1, len(get('/test/state')['videoDetailRequests']))
            post('/test/reset')
            self.assertEqual(1200, get('/api/v1/popular')[0]['viewCount'])


if __name__ == '__main__':
    unittest.main()
