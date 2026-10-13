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


def inventory():
    result = subprocess.run(
        ['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'],
        cwd=ROOT, check=True, capture_output=True,
    )
    return {
        p for p in result.stdout.decode().split('\0') if p and
        (p.startswith(('android/', 'scripts/', 'deploy/')) or p in EXTRA)
    }


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
        '## Existing documentation and historical evidence', '',
        '| Location | Role |', '| --- | --- |',
        '| [README](../../README.md) | Installation, overview and links into the current guides. |',
        '| [Development](../DEVELOPMENT.md) | Prerequisites, wrapper builds, signing, release publishing and upstream maintenance. |',
        '| [Liquid Glass](../LIQUID_GLASS.md) | Extended design references and presentation rationale. |',
        '| [Overhaul baseline](../liquid-glass-overhaul-2026-10-13/README.md) | Immutable pre-implementation images, provenance and approved brief. |',
        '| [Overhaul implementation](../liquid-glass-implementation-2026-10-13/README.md) | App-wide material changes, emulator acceptance, screenshots, normal-speed recordings and frame timing. |',
        '| [Feature parity](../../FEATURE_PARITY.md) | Dated web/native capability comparison, with exclusions and server requirements. |',
        '| [Verification](../../VERIFICATION.md) | Chronological commands, results, device limitations and release evidence. |',
        '| [Visual audit report](../visual-audit-2026-10-11/REPORT.md) | Dated screenshot/motion audit, EVIDENCE index, manifests, collector/grouping scripts, temporary harness/fixture patches and diagnostic logs. |',
        '| [Visual fixes](../visual-fixes-2026-10-11/FIXES.md) | Dated fixes, evidence manifests, temporary capture harness, motion and build/test logs. |',
        '| [Ambient verification](../ambient-mode-2026-10-11/VERIFICATION.md) | Ambient screenshots, motion, machine-readable verification and acceptance notes. |',
        '| [Player feedback](../player-feedback-2026-10-11/README.md) | Feedback-specific screenshots and verification notes. |',
        '', 'Evidence images/video/GIF/JSON/text files are supporting records of their '
        'collection, not additional runtime features. Temporary `.kt.txt` capture '
        'harnesses and fixture patches are reproducibility material, not app sources.', '',
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
    catalogs = {'SOURCE_MAP.md': source_catalog(files), 'TEST_CATALOG.md': test_catalog(files)}
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
