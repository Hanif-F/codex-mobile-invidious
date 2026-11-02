"""HTTP contract checks for the local access-gate fixture; never contacts production."""
import json
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import unittest
from concurrent.futures import ThreadPoolExecutor
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]


class InstanceAccessFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.media = tempfile.TemporaryDirectory()
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            port = sock.getsockname()[1]
        cls.base = f'http://127.0.0.1:{port}'
        cls.process = subprocess.Popen(['python3', str(ROOT / 'scripts/fixture-server.py'),
                                       '--port', str(port), '--media-dir', cls.media.name],
                                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(100):
            try:
                urlopen(cls.base + '/api/v1/verification', timeout=1).close()
                break
            except OSError:
                if cls.process.poll() is not None:
                    raise RuntimeError('Fixture exited during startup')
                time.sleep(.05)
        else:
            cls.process.terminate()
            raise RuntimeError('Fixture failed to start')

    @classmethod
    def tearDownClass(cls):
        cls.process.terminate()
        cls.process.wait(timeout=5)
        cls.media.cleanup()

    def request(self, path, data=None, headers=None, method=None):
        request = Request(self.base + path, data=json.dumps(data).encode() if data is not None else None,
                          headers=headers or {}, method=method)
        try:
            response = urlopen(request, timeout=5)
        except HTTPError as error:
            response = error
        with response:
            body = response.read()
            return response.status, response.headers, (json.loads(body) if 'application/json' in response.headers.get('Content-Type', '') else body.decode()) if body else None

    def setUp(self):
        self.request('/test/reset', {})

    def test_edge_challenges_bootstrap_and_requires_matching_cookie_and_agent(self):
        self.request('/test/access', dict(edgeEnabled=True))
        for path in ('/api/v1/verification', '/api/v1/mobile/registration', '/api/v1/popular', '/verify?client=native'):
            status, headers, body = self.request(path)
            self.assertEqual(403, status)
            self.assertEqual('challenge', headers['cf-mitigated'])
            self.assertIn('edge-complete', body)
        _, headers, _ = self.request('/test/edge-complete', {}, {'User-Agent': 'Fixture WebView'})
        self.assertIn('HttpOnly', headers['Set-Cookie'])
        self.assertIn('Expires=', headers['Set-Cookie'])
        credentials = {'Cookie': headers['Set-Cookie'].split(';')[0], 'User-Agent': 'Fixture WebView'}
        self.assertEqual(200, self.request('/api/v1/verification', headers=credentials)[0])
        self.assertEqual(200, self.request('/media/file', headers=credentials, method='HEAD')[0])
        self.assertEqual(403, self.request('/api/v1/popular', headers=dict(credentials, **{'User-Agent': 'Wrong agent'}))[0])
        self.request('/test/access', dict(edgeRotateProbe=1))
        status, rotated, _ = self.request('/api/v1/verification', headers=credentials)
        self.assertEqual(200, status)
        self.assertIn('fixture-edge-2', rotated['Set-Cookie'])
        self.assertEqual(403, self.request('/api/v1/verification', headers=credentials)[0])
        credentials['Cookie'] = rotated['Set-Cookie'].split(';')[0]
        self.assertEqual(200, self.request('/api/v1/verification', headers=credentials)[0])
        self.request('/test/access', dict(edgePolicy=3))
        self.assertEqual(403, self.request('/api/v1/verification', headers=credentials)[0])
        self.request('/test/reset', {})
        self.assertFalse(self.request('/test/state')[2]['edgeEnabled'])

    def test_edge_and_backend_gates_remain_independent_and_session_cookie_has_no_expiry(self):
        self.request('/test/access', dict(edgeEnabled=True, edgeSession=True, accessEnabled=True, accessLogin=True))
        _, headers, _ = self.request('/test/edge-complete', {}, {'User-Agent': 'Fixture WebView'})
        self.assertNotIn('Expires=', headers['Set-Cookie'])
        credentials = {'Cookie': headers['Set-Cookie'].split(';')[0], 'User-Agent': 'Fixture WebView'}
        self.assertEqual('login_required', self.request('/api/v1/popular', headers=credentials)[2]['error'])
        credentials['Authorization'] = 'Bearer fixture-token'
        self.assertEqual('verification_required', self.request('/api/v1/popular', headers=credentials)[2]['error'])
        self.request('/test/access', dict(edgeRejectNative=True))
        self.assertEqual('challenge', self.request('/api/v1/verification', headers=credentials)[1]['cf-mitigated'])

    def test_delayed_edge_rotation_holds_the_replacement_while_old_clearance_is_challenged(self):
        self.request('/test/access', dict(edgeEnabled=True))
        _, headers, _ = self.request('/test/edge-complete', {}, {'User-Agent': 'Fixture WebView'})
        credentials = {'Cookie': headers['Set-Cookie'].split(';')[0], 'User-Agent': 'Fixture WebView'}
        self.request('/test/access', dict(edgeRotatePath='/api/v1/edge-rotation-fixture', edgeRotateNext=1))
        with ThreadPoolExecutor(max_workers=1) as executor:
            pending = executor.submit(self.request, '/api/v1/edge-rotation-fixture', headers=credentials)
            try:
                for _ in range(100):
                    if self.request('/test/state')[2]['edgeDelayedRotations']: break
                    time.sleep(.01)
                else: self.fail('Rotation did not reach the delay barrier')
                self.assertEqual('challenge', self.request('/api/v1/verification', headers=credentials)[1]['cf-mitigated'])
                self.assertFalse(pending.done())
            finally:
                self.request('/test/access', dict(edgeReleaseRotations=True))
            status, replacement, _ = pending.result(timeout=5)
        self.assertEqual(200, status)
        credentials['Cookie'] = replacement['Set-Cookie'].split(';')[0]
        self.assertEqual(200, self.request('/api/v1/verification', headers=credentials)[0])
        self.assertTrue(self.request('/test/state')[2]['edgeDelayedRotations'][0]['released'])
        self.request('/test/reset', {})
        self.assertEqual([], self.request('/test/state')[2]['edgeDelayedRotations'])

    def test_delayed_edge_challenge_keeps_its_decision_after_clearance_is_issued(self):
        self.request('/test/access', dict(edgeEnabled=True, accessDelayPath='/api/v1/popular', accessDelayNext=1))
        with ThreadPoolExecutor(max_workers=1) as executor:
            pending = executor.submit(self.request, '/api/v1/popular')
            try:
                for _ in range(100):
                    if self.request('/test/state')[2]['accessDelayedDenials']: break
                    time.sleep(.01)
                else: self.fail('Challenge did not reach the delay barrier')
                _, headers, _ = self.request('/test/edge-complete', {}, {'User-Agent': 'Fixture WebView'})
                credentials = {'Cookie': headers['Set-Cookie'].split(';')[0], 'User-Agent': 'Fixture WebView'}
                self.assertEqual(200, self.request('/api/v1/popular', headers=credentials)[0])
            finally:
                self.request('/test/access', dict(accessReleaseDenials=True))
            self.assertEqual('challenge', pending.result(timeout=5)[1]['cf-mitigated'])

    def test_both_gates_preserve_bootstrap_and_require_separate_credentials(self):
        self.request('/test/access', dict(accessEnabled=True, accessLogin=True))
        self.assertTrue(self.request('/api/v1/mobile/registration')[2]['loginRequired'])
        self.assertEqual(401, self.request('/api/v1/popular')[0])
        bearer = {'Authorization': 'Bearer fixture-token'}
        self.assertEqual(403, self.request('/api/v1/popular', headers=bearer)[0])
        status, _, result = self.request('/api/v1/verification/complete', dict(transport='native', turnstileToken='fixture-response'))
        self.assertEqual(200, status)
        headers = dict(bearer, **{'X-Invidious-Verification': result['verificationToken']})
        self.assertEqual(200, self.request('/api/v1/popular', headers=headers)[0])
        self.assertEqual(401, self.request('/api/v1/popular', headers={'X-Invidious-Verification': result['verificationToken']})[0])
        self.assertEqual(200, self.request('/media/file', headers=headers, method='HEAD')[0])
        self.assertEqual(403, self.request('/media/file', headers=bearer, method='HEAD')[0])

    def test_expiry_policy_change_and_provider_outage_are_distinct(self):
        self.request('/test/access', dict(accessEnabled=True))
        headers = {'X-Invidious-Verification': 'fixture-pass-1', 'Range': 'bytes=1-'}
        self.request('/test/access', dict(accessExpiry=1))
        status, response, body = self.request('/media/file', headers=headers)
        self.assertEqual(403, status)
        self.assertEqual('verification_expired', response['X-Invidious-Verification-Required'])
        self.assertEqual('verification_expired', body['error'])
        self.request('/test/access', dict(accessPolicy=2))
        self.assertEqual('verification_required', self.request('/media/file', headers=headers)[2]['error'])
        self.request('/test/access', dict(accessUnavailable=True))
        self.assertEqual(503, self.request('/media/file', headers=headers)[0])
        self.assertEqual(503, self.request('/api/v1/verification/complete', dict(transport='native', turnstileToken='fixture-response'))[0])

    def test_disabled_completion_and_missing_discovery_support_old_instances(self):
        self.assertFalse(self.request('/api/v1/verification')[2]['required'])
        self.assertFalse(self.request('/api/v1/verification/complete', dict(transport='native', turnstileToken='fixture-response'))[2]['enabled'])
        self.request('/test/access', dict(accessMissing=True))
        self.assertEqual(404, self.request('/api/v1/verification')[0])

    def test_delayed_denial_keeps_its_original_decision_after_verification(self):
        self.request('/test/access', dict(accessEnabled=True, accessDelayPath='/media/thumbnail.jpg', accessDelayNext=1))
        with ThreadPoolExecutor(max_workers=1) as executor:
            pending = executor.submit(self.request, '/media/thumbnail.jpg')
            try:
                for _ in range(100):
                    if self.request('/test/state')[2]['accessDelayedDenials']: break
                    time.sleep(.01)
                else: self.fail('Denial did not reach the delay barrier')
                _, _, completion = self.request('/api/v1/verification/complete', dict(transport='native', turnstileToken='fixture-response'))
                headers = {'X-Invidious-Verification': completion['verificationToken']}
                self.assertTrue(self.request('/api/v1/verification', headers=headers)[2]['verified'])
                self.assertFalse(pending.done())
            finally:
                self.request('/test/access', dict(accessReleaseDenials=True))
            status, _, body = pending.result(timeout=5)
        self.assertEqual(403, status)
        self.assertTrue(body['verification']['required'])
        self.assertTrue(self.request('/test/state')[2]['accessDelayedDenials'][0]['released'])
        self.request('/test/reset', {})
        self.assertEqual([], self.request('/test/state')[2]['accessDelayedDenials'])


if __name__ == '__main__':
    unittest.main()
