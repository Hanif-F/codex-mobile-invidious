"""Regression checks for fixture ownership and release signing safeguards.

Uses a disposable repository, a fake ADB executable, and a localhost HTTP server.
No real device, release identity or deployed service is modified.
"""

import json
import hashlib
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]

FAKE_ADB = '''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
root = Path(os.environ['FAKE_ANDROID_STATE'])
state = json.loads((root / 'state.json').read_text())
args = sys.argv[1:]
with (root / 'adb.jsonl').open('a') as log:
    log.write(json.dumps(args) + '\\n')
if args == ['devices']:
    print('List of devices attached')
    for serial in state['devices']: print(serial + '\\tdevice')
elif args == ['get-state']:
    print(state['deviceState'])
elif args == ['reverse', '--list']:
    print(state['mapping'])
elif args == ['reverse', 'tcp:18080', 'tcp:18080']:
    if state.get('failReverse'): sys.exit(1)
    state['mapping'] = 'emu tcp:18080 tcp:18080'
    (root / 'state.json').write_text(json.dumps(state))
elif args == ['reverse', '--remove', 'tcp:18080']:
    state['mapping'] = ''
    (root / 'state.json').write_text(json.dumps(state))
else: sys.exit(2)
'''

SERVER = '''import os
from pathlib import Path
from http.server import BaseHTTPRequestHandler, HTTPServer
Path('pid.txt').write_text(str(os.getpid()))
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200); self.end_headers(); self.wfile.write(b'{}')
    def log_message(self, *args): pass
HTTPServer(('127.0.0.1', 18080), Handler).serve_forever()
'''


class WorkflowTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / 'scripts/lib').mkdir(parents=True)
        (self.root / 'android').mkdir()
        (self.root / 'sdk/platform-tools').mkdir(parents=True)
        (self.root / 'jbr/bin').mkdir(parents=True)
        shutil.copy2(ROOT / 'scripts/lib/android-fixture.sh', self.root / 'scripts/lib')
        shutil.copy2(ROOT / 'scripts/build-release.sh', self.root / 'scripts')
        (self.root / 'scripts/fixture-server.py').write_text(SERVER)
        adb = self.root / 'sdk/platform-tools/adb'
        adb.write_text(FAKE_ADB)
        adb.chmod(0o755)
        self.environment = dict(os.environ, ANDROID_HOME=str(self.root / 'sdk'),
                                JAVA_HOME=str(self.root / 'jbr'), FAKE_ANDROID_STATE=str(self.root))
        self.environment.pop('ANDROID_SERIAL', None)
        self.state(devices=['emu'], deviceState='device', mapping='')

    def state(self, **changes):
        path = self.root / 'state.json'
        state = json.loads(path.read_text()) if path.exists() else {}
        state.update(changes)
        path.write_text(json.dumps(state))

    def calls(self):
        path = self.root / 'adb.jsonl'
        return [json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []

    def setup_command(self, body=''):
        return ('set -euo pipefail; source scripts/lib/android-fixture.sh; '
                'android_test_environment; android_start_fixture media fixture.log; ' + body)

    def run_shell(self, command):
        return subprocess.run(['bash', '-c', command], cwd=self.root, env=self.environment,
                              text=True, capture_output=True, timeout=15)

    def assert_server_stopped(self):
        path = self.root / 'pid.txt'
        if path.exists():
            with self.assertRaises(ProcessLookupError):
                os.kill(int(path.read_text()), 0)

    def fake_release_build(self, mapping='fixture mapping', apk=b'fixture APK'):
        gradle = self.root / 'android/gradlew'
        gradle.write_text('#!/usr/bin/env python3\nfrom pathlib import Path\n'
                          'p=Path("app/build/outputs/apk/release"); p.mkdir(parents=True, exist_ok=True)\n'
                          f'(p/"app-release.apk").write_bytes({apk!r})\n'
                          '(p/"output-metadata.json").write_text(\'{"elements":[{"versionName":"fixture"}]}\')\n'
                          'r=Path("app/build/outputs/mapping/release"); r.mkdir(parents=True, exist_ok=True)\n'
                          f'mapping={mapping!r}\n'
                          'if mapping is None: (r/"mapping.txt").unlink(missing_ok=True)\n'
                          'else: (r/"mapping.txt").write_text(mapping)\n'
                          '(r/"configuration.txt").write_text("fixture rules")\n'
                          '(r/"usage.txt").write_text("unused fixture classes")\n')
        gradle.chmod(0o755)

    def r8_archive(self, apk=b'fixture APK'):
        return self.root / 'artifacts/r8/fixture' / hashlib.sha256(apk).hexdigest()

    def test_only_owned_mapping_is_removed_on_normal_exit(self):
        result = self.run_shell(self.setup_command('kill -0 "$fixture_pid"'))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(['reverse', 'tcp:18080', 'tcp:18080'], self.calls())
        self.assertIn(['reverse', '--remove', 'tcp:18080'], self.calls())
        self.assert_server_stopped()

    def test_preexisting_mapping_survives(self):
        self.state(mapping='emu tcp:18080 tcp:18080')
        result = self.run_shell(self.setup_command('kill -0 "$fixture_pid"'))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn(['reverse', '--remove', 'tcp:18080'], self.calls())
        self.assertNotIn(['reverse', 'tcp:18080', 'tcp:18080'], self.calls())
        self.assert_server_stopped()

    def test_replaced_mapping_survives_cleanup(self):
        replacement = "python3 -c 'import json; from pathlib import Path; p=Path(\"state.json\"); s=json.loads(p.read_text()); s[\"mapping\"]=\"emu tcp:18080 tcp:19000\"; p.write_text(json.dumps(s))'"
        result = self.run_shell(self.setup_command(replacement))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn(['reverse', '--remove', 'tcp:18080'], self.calls())
        self.assert_server_stopped()

    def test_conflicting_mapping_prevents_fixture_start(self):
        self.state(mapping='emu tcp:18080 tcp:19000')
        result = self.run_shell(self.setup_command())
        self.assertNotEqual(0, result.returncode)
        self.assertIn('another port', result.stderr)
        self.assertFalse((self.root / 'pid.txt').exists())
        self.assertNotIn(['reverse', '--remove', 'tcp:18080'], self.calls())

    def test_multiple_devices_require_an_explicit_serial(self):
        self.state(devices=['first', 'second'])
        result = self.run_shell(self.setup_command())
        self.assertNotEqual(0, result.returncode)
        self.assertIn('ANDROID_SERIAL', result.stderr)
        self.assertFalse((self.root / 'pid.txt').exists())

    def test_occupied_host_port_is_preserved(self):
        with socket.socket() as occupied:
            occupied.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            occupied.bind(('127.0.0.1', 18080))
            occupied.listen()
            result = self.run_shell(self.setup_command())
            self.assertNotEqual(0, result.returncode)
            self.assertIn('already in use', result.stderr)
            self.assertFalse((self.root / 'pid.txt').exists())

    def test_reverse_failure_stops_its_fixture(self):
        self.state(failReverse=True)
        result = self.run_shell(self.setup_command())
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn(['reverse', '--remove', 'tcp:18080'], self.calls())
        self.assert_server_stopped()

    def test_signal_cleans_up_fixture_and_owned_mapping(self):
        process = subprocess.Popen(['bash', '-c', self.setup_command('echo READY; read -r line')],
                                   cwd=self.root, env=self.environment, text=True,
                                   stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.addCleanup(lambda: process.kill() if process.poll() is None else None)
        self.assertEqual('READY\n', process.stdout.readline())
        process.terminate()
        _, stderr = process.communicate(timeout=10)
        self.assertEqual(143, process.returncode, stderr)
        self.assertIn(['reverse', '--remove', 'tcp:18080'], self.calls())
        self.assert_server_stopped()

    def test_missing_signing_configuration_never_creates_an_identity(self):
        result = self.run_shell('scripts/build-release.sh')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Restore the existing', result.stderr)
        self.assertFalse((self.root / 'android/keystore').exists())

    def test_initialization_refuses_existing_signing_material(self):
        key = self.root / 'android/keystore/old.jks'
        key.parent.mkdir()
        key.write_bytes(b'existing signing identity')
        result = self.run_shell('scripts/build-release.sh --init-signing')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(b'existing signing identity', key.read_bytes())
        self.assertFalse((self.root / 'android/signing.properties').exists())

    def test_initialization_refuses_existing_configuration(self):
        configuration = self.root / 'android/signing.properties'
        configuration.write_text('storeFile=preserved.jks\n')
        result = self.run_shell('scripts/build-release.sh --init-signing')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual('storeFile=preserved.jks\n', configuration.read_text())
        self.assertFalse((self.root / 'android/keystore').exists())

    def test_explicit_first_identity_creation_writes_private_config_and_checksums(self):
        keytool = self.root / 'jbr/bin/keytool'
        keytool.write_text('#!/usr/bin/env python3\nimport sys\nfrom pathlib import Path\n'
                           'Path(sys.argv[sys.argv.index("-keystore") + 1]).write_bytes(b"fixture identity")\n')
        keytool.chmod(0o755)
        self.fake_release_build()
        result = self.run_shell('scripts/build-release.sh --init-signing')
        self.assertEqual(0, result.returncode, result.stderr)
        for name in ('android/keystore/mobivious.jks', 'android/signing.properties'):
            self.assertEqual(0o600, (self.root / name).stat().st_mode & 0o777)
        self.assertEqual([self.root / 'android/keystore/mobivious.jks'],
                         list((self.root / 'android/keystore').iterdir()))
        apk = self.root / 'artifacts/Mobivious-fixture.apk'
        self.assertEqual(b'fixture APK', apk.read_bytes())
        self.assertEqual(hashlib.sha256(apk.read_bytes()).hexdigest() + '  Mobivious-fixture.apk\n',
                         apk.with_suffix('.apk.sha256').read_text())
        archive = self.r8_archive()
        self.assertEqual('fixture mapping', (archive / 'mapping.txt').read_text())
        self.assertEqual('fixture rules', (archive / 'configuration.txt').read_text())
        self.assertEqual('unused fixture classes', (archive / 'usage.txt').read_text())
        self.assertEqual(apk.with_suffix('.apk.sha256').read_text(), (archive / 'apk.sha256').read_text())
        for line in (archive / 'SHA256SUMS').read_text().splitlines():
            digest, name = line.split('  ', 1)
            self.assertEqual(digest, hashlib.sha256((archive / name).read_bytes()).hexdigest())

    def test_missing_or_empty_r8_mapping_preserves_existing_release_artifacts(self):
        (self.root / 'android/signing.properties').write_text('storeFile=preserved.jks\n')
        apk = self.root / 'artifacts/Mobivious-fixture.apk'
        apk.parent.mkdir()
        apk.write_bytes(b'previous release')
        checksum = apk.with_suffix('.apk.sha256')
        checksum.write_text('previous checksum')
        for mapping in (None, ''):
            with self.subTest(mapping=mapping):
                self.fake_release_build(mapping=mapping)
                result = self.run_shell('scripts/build-release.sh')
                self.assertNotEqual(0, result.returncode)
                self.assertIn('Missing R8 mapping.txt', result.stderr)
                self.assertEqual(b'previous release', apk.read_bytes())
                self.assertEqual('previous checksum', checksum.read_text())
                self.assertFalse((self.root / 'artifacts/r8').exists())

    def test_r8_archives_preserve_both_builds_of_the_same_version(self):
        (self.root / 'android/signing.properties').write_text('storeFile=preserved.jks\n')
        self.fake_release_build()
        first = self.run_shell('scripts/build-release.sh')
        self.assertEqual(0, first.returncode, first.stderr)
        original = {path.name: path.read_bytes() for path in self.r8_archive().iterdir()}
        self.fake_release_build(mapping='second mapping', apk=b'second APK')
        second = self.run_shell('scripts/build-release.sh')
        self.assertEqual(0, second.returncode, second.stderr)
        self.assertEqual(original, {path.name: path.read_bytes() for path in self.r8_archive().iterdir()})
        self.assertEqual('second mapping', (self.r8_archive(b'second APK') / 'mapping.txt').read_text())

    def test_rebuilding_the_same_apk_preserves_its_r8_archive(self):
        (self.root / 'android/signing.properties').write_text('storeFile=preserved.jks\n')
        self.fake_release_build()
        first = self.run_shell('scripts/build-release.sh')
        self.assertEqual(0, first.returncode, first.stderr)
        mapping = self.r8_archive() / 'mapping.txt'
        original_time = mapping.stat().st_mtime_ns
        second = self.run_shell('scripts/build-release.sh')
        self.assertEqual(0, second.returncode, second.stderr)
        self.assertEqual(original_time, mapping.stat().st_mtime_ns)

    def test_conflicting_mapping_for_an_existing_apk_is_rejected(self):
        (self.root / 'android/signing.properties').write_text('storeFile=preserved.jks\n')
        self.fake_release_build()
        first = self.run_shell('scripts/build-release.sh')
        self.assertEqual(0, first.returncode, first.stderr)
        self.fake_release_build(mapping='conflicting mapping')
        second = self.run_shell('scripts/build-release.sh')
        self.assertNotEqual(0, second.returncode)
        self.assertIn('preserved the archive', second.stderr)
        self.assertEqual('fixture mapping', (self.r8_archive() / 'mapping.txt').read_text())

    def test_failed_key_generation_leaves_no_partial_signing_material(self):
        keytool = self.root / 'jbr/bin/keytool'
        keytool.write_text('#!/usr/bin/env bash\nexit 7\n')
        keytool.chmod(0o755)
        result = self.run_shell('scripts/build-release.sh --init-signing')
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.root / 'android/signing.properties').exists())
        self.assertEqual([], list((self.root / 'android/keystore').iterdir()))


if __name__ == '__main__':
    unittest.main()
