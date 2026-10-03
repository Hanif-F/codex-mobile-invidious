#!/usr/bin/env python3
"""Generate real multi-representation DASH media for native player acceptance tests."""
import argparse
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('media_dir', type=Path)
args = parser.parse_args()
directory = args.media_dir / 'rich'
manifest = directory / 'dash.mpd'
if manifest.exists() and (directory / '.complete').exists():
    raise SystemExit(0)
directory.mkdir(parents=True, exist_ok=True)
command = ['ffmpeg', '-y', '-hide_banner', '-loglevel', 'error', '-i', str(args.media_dir / 'fixture.mp4'),
           '-filter_complex', '[0:v]split=4[v0][v1][v2][v3];[v0]scale=256:144[small];[v1]scale=640:360[medium];[v2]scale=1280:720[high];[v3]scale=1280:720[low]']
for stream in ('[small]', '[medium]', '[high]', '[low]'):
    command += ['-map', stream]
for _ in range(4):
    command += ['-map', '0:a:0']
command += ['-c:v', 'libx264', '-preset', 'ultrafast', '-threads:v', '2', '-g', '48', '-sc_threshold', '0',
            '-b:v:0', '120k', '-b:v:1', '600k', '-b:v:2', '1400k', '-b:v:3', '700k',
            '-c:a', 'aac', '-b:a:0', '128k', '-b:a:1', '64k', '-b:a:2', '96k', '-b:a:3', '80k',
            '-f', 'dash', '-seg_duration', '4', '-adaptation_sets',
            'id=0,streams=v id=1,streams=4 id=2,streams=5 id=3,streams=6 id=4,streams=7', str(manifest)]
subprocess.run(command, check=True)
ns = 'urn:mpeg:dash:schema:mpd:2011'
ET.register_namespace('', ns)
tree = ET.parse(manifest)
audio = tree.findall(f'.//{{{ns}}}AdaptationSet[@contentType="audio"]')
for group, (label, lang, role) in zip(audio, [('English', 'en', 'main'), ('English', 'en', 'main'),
                                          ('English Stable Volume', 'en', 'main'), ('Spanish', 'es', 'alternate')]):
    group.set('lang', lang)
    ET.SubElement(group, f'{{{ns}}}Label').text = label
    ET.SubElement(group, f'{{{ns}}}Role', {'schemeIdUri': 'urn:mpeg:dash:role:2011', 'value': role})
tree.write(manifest, encoding='utf-8', xml_declaration=True)
(directory / '.complete').touch()
