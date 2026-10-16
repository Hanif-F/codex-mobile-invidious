#!/usr/bin/env python3
"""Check documentation inventory, reviewed fingerprints and local Markdown links.

--refresh acknowledges reviewed source hashes and regenerates reference catalogs;
it deliberately refuses to invent descriptions or guides for new files.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parent.parent
REFERENCE = ROOT / 'docs/reference'
MANIFEST = REFERENCE / 'coverage.json'
EXTRA = {'.env.example', '.gitignore', 'docker-compose.yml', 'LICENSE'}


def repository_files():
    result = subprocess.run(
        ['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'],
        cwd=ROOT, check=True, capture_output=True,
    )
    return {p for p in result.stdout.decode().split('\0') if p and (ROOT / p).is_file()}


def inventory():
    return {p for p in repository_files() if p.startswith(('android/', 'scripts/', 'deploy/')) or p in EXTRA}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_catalog(files):
    lines = [
        '# Source map', '', '[Documentation index](../README.md) · '
        '[Coverage](COVERAGE.md) · [Test catalog](TEST_CATALOG.md)', '',
        'Generated from the reviewed descriptions in [coverage.json](coverage.json). '
        'Each maintained implementation, resource, build, script and deployment file '
        'has an owning guide. Test files and their methods are listed separately below.', '',
    ]
    groups = {}
    for path, entry in sorted(files.items()):
        if '/src/test/' in path or '/src/androidTest/' in path:
            continue
        groups.setdefault(entry['area'], []).append((path, entry))
    for group, entries in groups.items():
        lines += [f'## {group}', '', '| File | Responsibility | Guide |', '| --- | --- | --- |']
        for path, entry in entries:
            lines.append(f'| [{path}](../../{path}) | {entry["description"]} | '
                         f'[{Path(entry["guide"]).stem}](../../{entry["guide"]}) |')
        lines.append('')
    lines += [
        '## Tests and test infrastructure', '',
        'Every JVM and device Kotlin file is indexed in [the test catalog](TEST_CATALOG.md), '
        'with exact declared methods and explicit Ignore annotations. '
        'The [testing guide](../TESTING.md) explains runners and acceptance boundaries.', '',
        '## Current documentation', '',
        '| Location | Role |', '| --- | --- |',
        '| [README](../../README.md) | Installation, overview and links into the current guides. |',
        '| [Documentation index](../README.md) | Current subsystem guides and terminology. |',
        '| [Development](../DEVELOPMENT.md) | Prerequisites, builds, signing, releases and upstream maintenance. |',
        '| [UI](../UI.md) | Opaque Material components, navigation, responsive layouts and player presentation. |',
        '| [Features](../FEATURES.md) and [API](../API.md) | Supported capabilities and server contracts. |',
        '| [Testing](../TESTING.md) | Repeatable checks and acceptance boundaries. |',
        '| [Cleanup](../CLEANUP.md) | Latest repository audit changes, validation and known baseline failures. |',
        '',
        '## Local and generated files', '',
        '| Location | Role / handling |', '| --- | --- |',
        '| `android/local.properties` | Local Android SDK path, ignored. |',
        '| `android/signing.properties`, `android/keystore/` | Personal release signing secrets; preserve securely for future updates, never commit. |',
        '| `.env` | Real deployment secrets/settings; `.env.example` is the committed shape. |',
        '| `android/.gradle/`, `.kotlin/`, `.idea/`, module `build/` | Tool caches, IDE state and generated builds/reports. |',
        '| `.tools/` | Generated fixture media and disposable local verification output. |',
        '| `artifacts/` | Release APKs/checksums/release notes from the build workflow. |',
        '| `.git/` | Repository metadata, not application code. |',
        '', 'Ignored/generated/private files are excluded from fingerprints. Their '
        'contents and secrets are not reproduced in documentation.', '',
    ]
    return '\n'.join(lines)


def test_catalog(files):
    lines = ['# Test catalog', '', '[Documentation index](../README.md) · '
             '[Testing guide](../TESTING.md)', '',
             'Generated inventory of declared `@Test` methods. Names describe intended '
             'coverage; this is not a run report. Ignore annotations below remain '
             'acceptance gaps until executed on a supported target.', '']
    total = 0
    for suite, title in [('test', 'JVM tests'), ('androidTest', 'Android device tests')]:
        entries = [(p, e) for p, e in sorted(files.items()) if f'/src/{suite}/' in p]
        lines += [f'## {title}', '']
        for path, entry in entries:
            source = (ROOT / path).read_text()
            names = re.findall(r'@Test\b(?:(?!\bfun\b).)*\bfun\s+([A-Za-z_]\w*)', source, re.S)
            ignored = re.findall(r'@Ignore\(([^\n]+)\)', source)
            total += len(names)
            lines += [f'### {Path(path).stem}', '',
                      f'[Source](../../{path}) — {entry["description"]}', '']
            if ignored:
                lines += [f'Explicit Ignore: {reason}' for reason in ignored] + ['']
            lines += ([f'- `{name}`' for name in names] if names else
                      ['Test support/helper file; no declared `@Test` methods.'])
            lines.append('')
    lines += [f'Inventory total: **{total} declared test methods** (including ignored tests).', '']
    return '\n'.join(lines)


def coverage_catalog(files, documents):
    build = (ROOT / 'android/app/build.gradle.kts').read_text()
    version = re.search(r'versionName\s*=\s*"([^"]+)"', build).group(1)
    code = re.search(r'versionCode\s*=\s*(\d+)', build).group(1)
    groups = [
        ('Main application Kotlin', [p for p in files if p.endswith('.kt') and '/app/src/main/' in p]),
        ('JVM test Kotlin', [p for p in files if p.endswith('.kt') and '/src/test/' in p]),
        ('Android device test/support Kotlin', [p for p in files if p.endswith('.kt') and '/src/androidTest/' in p]),
    ]
    other = len(files) - sum(len(paths) for _, paths in groups)
    lines = [
        '# Documentation coverage and review boundaries', '',
        '[Documentation index](../README.md) · [Source map](SOURCE_MAP.md) · '
        '[Test catalog](TEST_CATALOG.md) · [Review manifest](coverage.json)', '',
        f'The current guides describe Mobivious **{version}** (version code **{code}**). '
        'This report is generated from the current Gradle configuration and reviewed '
        'inventory; counts describe files, not measured execution coverage.', '',
        '## File coverage', '', '| Maintained files | Count |', '| --- | ---: |',
    ]
    lines += [f'| {label} | {len(paths)} |' for label, paths in groups]
    lines += [
        f'| Build, benchmark, resources, scripts, deployment and repository support | {other} |',
        f'| **Total** | **{len(files)}** |', '',
        'Every maintained file has a reviewed SHA-256 fingerprint, responsibility '
        'and owning guide in `coverage.json`. The source map indexes implementations; '
        'the test catalog lists every declared JVM/device test and explicit Ignore.', '',
        '## Review scope', '',
        'The inventory includes tracked and non-ignored untracked files under '
        '`android/`, `scripts/` and `deploy/`, plus `.env.example`, `.gitignore`, '
        '`docker-compose.yml` and `LICENSE`. Locally deleted files are excluded. '
        'The sibling Invidious checkout is reviewed at its integration boundary; '
        'it is not an Android module.', '',
        'The [architecture](../ARCHITECTURE.md), [features](../FEATURES.md), '
        '[settings](../SETTINGS.md), [API](../API.md), [playback](../PLAYBACK.md), '
        '[library](../LIBRARY.md), [storage/security](../STORAGE_AND_SECURITY.md), '
        '[UI](../UI.md), [development](../DEVELOPMENT.md) and [testing](../TESTING.md) '
        'guides own current behavior. Generated logs and captures belong under '
        'ignored `.tools/`, with source and device provenance.', '',
        'Ignored builds, caches, IDE files, SDK paths, fixture media and release '
        'artifacts are explained by category in the source map. Local secrets '
        'and third-party library internals are excluded from fingerprints; secret '
        'values are never reproduced in documentation.', '',
        '## Verification and freshness', '',
        '`python3 scripts/check-documentation.py` checks inventory completeness, '
        'guide assignments, reviewed fingerprints and all three generated catalogs. '
        f'It checks local file links in **all {len(documents)} repository Markdown documents**, '
        'including the cleanup report. Remote URLs and Markdown section anchors are '
        'outside this local check.', '',
        'After reviewing changed sources and updating their guides, run '
        '`python3 scripts/check-documentation.py --refresh`, then the checker '
        'without refresh. New sources and documents require explicit inventory '
        'registration first. [Testing](../TESTING.md#documentation-checks) explains '
        'the review workflow.', '',
        'Fingerprints and catalogs establish inventory freshness; semantic '
        'accuracy, emulator/device behavior, production compatibility, performance '
        'and release acceptance require their relevant checks. Test names and '
        'previous passing totals do not certify a current run.', '',
    ]
    return '\n'.join(lines)


def local_links(document):
    # Link paths only. Remote links are intentionally not fetched by this local check.
    for match in re.finditer(r'!?\[[^\]\n]*\]\(([^)\n]+)\)', document.read_text()):
        value = match.group(1).strip().split(' "', 1)[0].strip('<>')
        parsed = urlsplit(value)
        if parsed.scheme or parsed.netloc or not parsed.path:
            continue
        target = (document.parent / unquote(parsed.path)).resolve()
        if not target.exists():
            yield f'{document.relative_to(ROOT)}: missing link {value}'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--refresh', action='store_true', help='Acknowledge reviewed hashes and regenerate catalogs')
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text())
    files = manifest['files']
    found = inventory()
    errors = []
    errors += [f'Undocumented maintained file: {p}' for p in sorted(found - files.keys())]
    errors += [f'Remove obsolete coverage entry: {p}' for p in sorted(files.keys() - found)]
    documents = set(manifest['documents'])
    actual_documents = {p for p in repository_files() if p.endswith('.md')}
    errors += [f'Undocumented Markdown document: {p}' for p in sorted(actual_documents - documents)]
    errors += [f'Remove obsolete document entry: {p}' for p in sorted(documents - actual_documents)]
    for path, entry in files.items():
        if not (ROOT / path).is_file():
            errors.append(f'Missing source: {path}')
            continue
        if not entry.get('description') or not entry.get('area') or not (ROOT / entry.get('guide', '')).is_file():
            errors.append(f'Invalid documentation assignment: {path}')
        if not args.refresh and entry.get('sha256') != digest(ROOT / path):
            errors.append(f'Review changed source, update prose then --refresh: {path}')
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    if args.refresh:
        for path, entry in files.items():
            entry['sha256'] = digest(ROOT / path)
        MANIFEST.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + '\n')
    catalogs = {'SOURCE_MAP.md': source_catalog(files), 'TEST_CATALOG.md': test_catalog(files),
                'COVERAGE.md': coverage_catalog(files, documents)}
    for name, expected in catalogs.items():
        path = REFERENCE / name
        if args.refresh:
            path.write_text(expected)
        elif not path.exists() or path.read_text() != expected:
            errors.append(f'Catalog out of date: docs/reference/{name}; review then --refresh')
    for name in manifest['documents']:
        path = ROOT / name
        if not path.is_file():
            errors.append(f'Missing documentation: {name}')
        else:
            # Recovered historical snapshots retain their original links and bytes.
            if name not in manifest.get('historicalDocuments', []):
                errors.extend(local_links(path))
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    print(f'Documentation checks passed: {len(files)} maintained files, '
          f'{len(manifest["documents"])} documents, reviewed fingerprints and local file links.')
    print('Semantic accuracy and runtime acceptance require human/code review and the relevant app checks.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
