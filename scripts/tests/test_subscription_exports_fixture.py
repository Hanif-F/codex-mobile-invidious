"""Subscription exports match production formats, permissions and legacy failures."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from urllib.error import HTTPError
from urllib.request import Request, urlopen

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fixture_support import fixture_server


class SubscriptionExportsFixtureTest(unittest.TestCase):
    def test_formats_keep_all_subscriptions_and_correct_types_and_names(self):
        with tempfile.TemporaryDirectory() as media, fixture_server(media) as base:
            def post(path, data=None):
                with urlopen(Request(base + path, data=json.dumps(data or {}).encode())) as response:
                    return json.load(response)

            def export(format):
                return urlopen(Request(base + '/api/v1/auth/subscriptions/export?format=' + format,
                                       headers={'Authorization': 'Bearer fixture-token'}))

            channels = [dict(authorId='UC' + f'{i:022d}', author=f'Creator 日本語 & "{i}"') for i in range(165)]
            for rows in (channels, []):
                post('/test/home-subscriptions', {'subscriptionChannels': rows})
                for format in ('rss', 'newpipe', 'opml', 'newpipe_json'):
                    with export(format) as response:
                        body = response.read()
                        self.assertEqual('private, no-store', response.headers['Cache-Control'])
                        self.assertNotIn(b'fixture-token', body)
                        if format == 'newpipe_json':
                            self.assertEqual('application/json', response.headers['Content-Type'])
                            self.assertIn('filename="newpipe_subscriptions"', response.headers['Content-Disposition'])
                            data = json.loads(body)
                            self.assertEqual(len(rows), len(data['subscriptions']))
                            self.assertTrue(data['app_version'])
                            self.assertGreater(data['app_version_int'], 0)
                            self.assertEqual([row['author'] for row in rows], [entry['name'] for entry in data['subscriptions']])
                            self.assertTrue(all(entry['service_id'] == 0 and entry['url'].startswith('https://www.youtube.com/channel/') for entry in data['subscriptions']))
                        else:
                            self.assertEqual('application/xml', response.headers['Content-Type'])
                            self.assertTrue(response.headers['Content-Disposition'].endswith('.opml"'))
                            outlines = ET.fromstring(body).findall('.//outline[@xmlUrl]')
                            self.assertEqual(len(rows), len(outlines))
                            self.assertEqual([row['author'] for row in rows], [entry.get('text') for entry in outlines])
                            prefix = 'http://127.0.0.1:18080/feed/channel/' if format == 'rss' else 'https://www.youtube.com/feeds/videos.xml?channel_id='
                            self.assertTrue(all(entry.get('xmlUrl').startswith(prefix) for entry in outlines))
            post('/test/playlist-rss', {'exportLegacy': True})
            for format in ('opml', 'newpipe_json', 'bad'):
                with self.assertRaises(HTTPError) as failure:
                    export(format)
                self.assertEqual(400, failure.exception.code)
                failure.exception.close()
            with export('rss') as response:
                self.assertEqual(200, response.status)
            post('/test/playlist-rss', {'exportFailNext': True})
            with self.assertRaises(HTTPError) as failure:
                export('rss')
            self.assertEqual(503, failure.exception.code)
            failure.exception.close()
            with export('rss') as response:
                self.assertEqual(200, response.status)
            with self.assertRaises(HTTPError) as failure:
                urlopen(base + '/api/v1/auth/subscriptions/export?format=rss')
            self.assertEqual(403, failure.exception.code)
            failure.exception.close()
            post('/test/reset')
            with export('newpipe_json') as response:
                self.assertEqual(1, len(json.load(response)['subscriptions']))

    def test_preferences_reject_retired_comment_sources(self):
        with tempfile.TemporaryDirectory() as media, fixture_server(media) as base:
            def patch(comments):
                return urlopen(Request(base + '/api/v1/auth/preferences', method='PATCH',
                    data=json.dumps({'comments': comments}).encode(),
                    headers={'Authorization': 'Bearer fixture-token', 'Content-Type': 'application/json'}))
            for comments in (['reddit', ''], ['youtube', 'reddit'], 'youtube', ['', '', '']):
                with self.assertRaises(HTTPError) as failure:
                    patch(comments)
                self.assertEqual(400, failure.exception.code)
                failure.exception.close()
            for comments in (['youtube', ''], ['', ''], ['', 'youtube']):
                with patch(comments) as response:
                    self.assertEqual(comments, json.load(response)['comments'])


if __name__ == '__main__':
    unittest.main()
