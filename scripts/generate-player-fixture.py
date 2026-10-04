#!/usr/bin/env python3
"""Generate real multi-representation DASH media for native player acceptance tests."""
import argparse
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('media_dir', type=Path)
args = parser.parse_args()
def generate_rich():
    directory = args.media_dir / 'rich'
    manifest = directory / 'dash.mpd'
    if manifest.exists() and (directory / '.complete').exists():
        return
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


def generate_codecs():
    directory = args.media_dir / 'codec'
    if (directory / '.complete-v2').exists():
        return
    directory.mkdir(parents=True, exist_ok=True)
    manifest = directory / 'dash.mpd'
    # Short real media keeps codec-specific acceptance independent of the
    # long seek/audio fixture. Representation IDs are FFmpeg stream indexes.
    sizes = [360, 720, 720, 360, 720, 720, 720]
    outputs = ''.join(f'[v{i}]' for i in range(len(sizes)))
    filters = f'[0:v]split={len(sizes)}{outputs};' + ';'.join(
        f'[v{i}]scale={height * 16 // 9}:{height}[out{i}]' for i, height in enumerate(sizes))
    command = ['ffmpeg', '-y', '-hide_banner', '-loglevel', 'error', '-filter_complex_threads', '1',
               '-i', str(args.media_dir / 'fixture.mp4'), '-filter_complex', filters]
    for i in range(len(sizes)):
        command += ['-map', f'[out{i}]']
    command += ['-map', '0:a:0', '-t', '12', '-threads:v', '2', '-g', '48']
    for i, bitrate in enumerate(['200k', '900k', '450k', '100k', '400k', '250k', '120k']):
        command += [f'-c:v:{i}', 'libx264' if i < 3 else 'libaom-av1', f'-b:v:{i}', bitrate]
        if i < 3:
            command += [f'-preset:v:{i}', 'ultrafast', f'-sc_threshold:v:{i}', '0']
        else:
            command += [f'-cpu-used:v:{i}', '8', f'-row-mt:v:{i}', '1']
    command += ['-c:a', 'aac', '-b:a', '64k', '-f', 'dash', '-seg_duration', '2',
                '-adaptation_sets', 'id=0,streams=0,1,2 id=1,streams=3,4,5,6 id=2,streams=7', str(manifest)]
    subprocess.run(command, check=True)
    ns = 'urn:mpeg:dash:schema:mpd:2011'
    ET.register_namespace('', ns)
    tree = ET.parse(manifest)
    for representation in tree.findall(f'.//{{{ns}}}Representation'):
        if representation.get('codecs', '').startswith('av01'):
            representation.set('codecs', 'av01.0.99M.08')
            # Android can ignore an unrecognized profile/level string; an
            # impossible coded size also makes renderer capability rejection deterministic.
            representation.set('width', '32768')
            representation.set('height', '32768')
    tree.write(directory / 'unsupported.mpd', encoding='utf-8', xml_declaration=True)
    tree = ET.parse(manifest)
    for group in tree.findall(f'.//{{{ns}}}AdaptationSet'):
        for representation in list(group.findall(f'{{{ns}}}Representation')):
            if representation.get('id') == '3':
                group.remove(representation)
    tree.write(directory / 'missing.mpd', encoding='utf-8', xml_declaration=True)
    (directory / '.complete-v2').touch()


generate_rich()
generate_codecs()


def generate_shapes():
    directory = args.media_dir / 'shapes'
    directory.mkdir(parents=True, exist_ok=True)
    for name, size in [('portrait', '360x640'), ('square', '480x480'), ('landscape', '640x360'), ('ultrawide', '960x360')]:
        target = directory / (name + '.mp4')
        if target.exists():
            continue
        subprocess.run(['ffmpeg', '-y', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi',
                        '-i', 'testsrc2=size=' + size + ':rate=24', '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=48000',
                        '-t', '20', '-c:v', 'libx264', '-preset', 'ultrafast', '-threads', '2', '-c:a', 'aac',
                        '-movflags', '+faststart', str(target)], check=True)

generate_shapes()
