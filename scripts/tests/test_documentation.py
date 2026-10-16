"""Check review gates against a disposable Git inventory, without app builds."""

from contextlib import redirect_stderr, redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'check-documentation.py'
SPEC = importlib.util.spec_from_file_location('documentation_checker', SCRIPT)
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)
COVERAGE_CATALOG = checker.coverage_catalog


class DocumentationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        reference = self.root / 'docs/reference'
        reference.mkdir(parents=True)
        build = self.root / 'android/app/build.gradle.kts'
        build.parent.mkdir(parents=True)
        build.write_text('versionName = "fixture"\nversionCode = 42\n')
        self.source = self.root / 'android/app/src/main/java/App.kt'
        self.source.parent.mkdir(parents=True)
        self.source.write_text('class App\n')
        documents = ['README.md', 'docs/guide.md', 'docs/reference/SOURCE_MAP.md',
                     'docs/reference/TEST_CATALOG.md', 'docs/reference/COVERAGE.md']
        for name in documents:
            (self.root / name).write_text('# Fixture\n')
        self.manifest = reference / 'coverage.json'
        self.manifest.write_text(json.dumps({'files': {
            name: {'area': 'Fixture', 'description': 'Reviewed fixture source', 'guide': 'docs/guide.md'}
            for name in ('android/app/build.gradle.kts', 'android/app/src/main/java/App.kt')
        }, 'documents': documents}))
        # Keep catalog formatting separate from inventory/link-gate tests.
        for name, value in [('ROOT', self.root), ('REFERENCE', reference), ('MANIFEST', self.manifest),
                            ('source_catalog', lambda _: '# Sources\n'),
                            ('test_catalog', lambda _: '# Tests\n'),
                            ('coverage_catalog', lambda _, __: '# Coverage\n')]:
            context = patch.object(checker, name, value)
            context.start()
            self.addCleanup(context.stop)
        code, output = self.run_checker(refresh=True)
        self.assertEqual(0, code, output)

    def run_checker(self, refresh=False):
        output = io.StringIO()
        arguments = [str(SCRIPT)] + (['--refresh'] if refresh else [])
        with patch('sys.argv', arguments), redirect_stdout(output), redirect_stderr(output):
            code = checker.main()
        return code, output.getvalue()

    def test_new_markdown_requires_review_registration_even_during_refresh(self):
        (self.root / 'docs/new.md').write_text('# New document\n')
        code, output = self.run_checker(refresh=True)
        self.assertEqual(1, code)
        self.assertIn('Undocumented Markdown document: docs/new.md', output)

    def test_new_source_requires_an_explicit_guide_and_description(self):
        (self.source.parent / 'Added.kt').write_text('class Added\n')
        code, output = self.run_checker(refresh=True)
        self.assertEqual(1, code)
        self.assertIn('Undocumented maintained file:', output)

    def test_removed_source_requires_removing_its_inventory_entry(self):
        self.source.unlink()
        code, output = self.run_checker(refresh=True)
        self.assertEqual(1, code)
        self.assertIn('Remove obsolete coverage entry:', output)

    def test_changed_source_requires_explicit_fingerprint_review(self):
        self.source.write_text('class UpdatedApp\n')
        code, output = self.run_checker()
        self.assertEqual(1, code)
        self.assertIn('Review changed source, update prose then --refresh:', output)
        self.assertEqual(0, self.run_checker(refresh=True)[0])
        self.assertEqual(0, self.run_checker()[0])

    def test_broken_document_links_are_checked_during_refresh(self):
        document = self.root / 'docs/review/RESULTS.md'
        document.parent.mkdir()
        document.write_text('[Removed source](../../android/missing.kt)\n')
        manifest = json.loads(self.manifest.read_text())
        manifest['documents'].append('docs/review/RESULTS.md')
        self.manifest.write_text(json.dumps(manifest))
        code, output = self.run_checker(refresh=True)
        self.assertEqual(1, code)
        self.assertIn('docs/review/RESULTS.md: missing link', output)

    def test_coverage_report_tracks_the_build_version_and_real_inventory(self):
        manifest = json.loads(self.manifest.read_text())
        report = COVERAGE_CATALOG(manifest['files'], manifest['documents'])
        self.assertIn('Mobivious **fixture** (version code **42**)', report)
        self.assertIn('| **Total** | **2** |', report)
        self.assertIn('all 5 repository Markdown documents', report)

    def test_registered_historical_snapshot_keeps_original_broken_links(self):
        snapshot = self.root / 'FEATURE_PARITY.md'
        original = b'[Historical verification](VERIFICATION.md)\n'
        snapshot.write_bytes(original)
        manifest = json.loads(self.manifest.read_text())
        manifest['documents'].append('FEATURE_PARITY.md')
        manifest['historicalDocuments'] = ['FEATURE_PARITY.md']
        self.manifest.write_text(json.dumps(manifest))
        self.assertEqual(0, self.run_checker(refresh=True)[0])
        self.assertEqual(original, snapshot.read_bytes())


if __name__ == '__main__':
    unittest.main()
