"""HTTP contract checks for the local access-gate fixture; never contacts production."""
import json
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import unittest
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
            return response.status, response.headers, json.loads(body) if body else None

    def setUp(self):
        self.request('/test/reset', {})

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


if __name__ == '__main__':
    unittest.main()
