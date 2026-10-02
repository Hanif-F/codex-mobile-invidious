#!/usr/bin/env python3
"""Disposable emulator API fixture; never imported by the application.
Run with --media-dir containing fixture.mp4, dash.mpd, hls.m3u8 and segments.
Bind localhost only; ADB reverse exposes it to the emulator for instrumentation.
"""
import argparse
import json
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlparse, parse_qs

parser = argparse.ArgumentParser()
parser.add_argument('--port', type=int, default=18080)
parser.add_argument('--media-dir', type=Path, required=True)
args = parser.parse_args()
video = dict(videoId='testvideo01', title='A quiet moment · playback fixture', author='Mobivious Studio',
             authorId='UCfixture', lengthSeconds=120, viewCount=1200, publishedText='today',
             videoThumbnails=[dict(quality='medium', url='/media/thumbnail.jpg')])
prefs = dict(watch_history=True, save_player_pos=True, unrelated_setting='preserved')
state = dict(position=0, watched=[], playlists=[], events=[])

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def respond(self, data=None, status=200):
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        if data is not None:
            self.wfile.write(json.dumps(data).encode())

    def do_GET(self):
        url = urlparse(self.path)
        p = url.path
        if p.startswith('/media/'):
            file = args.media_dir / Path(p).name
            if not file.is_file():
                return self.respond({}, 404)
            data = file.read_bytes()
            self.send_response(200)
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Content-Type', {'.mp4':'video/mp4', '.mpd':'application/dash+xml', '.m3u8':'application/vnd.apple.mpegurl', '.vtt':'text/vtt', '.jpg':'image/jpeg'}.get(file.suffix, 'application/octet-stream'))
            self.end_headers()
            try:
                self.wfile.write(data)
            except (BrokenPipeError, ConnectionResetError):
                pass
        elif p == '/test/state': self.respond(state)
        elif p in ('/api/v1/popular', '/api/v1/trending', '/api/v1/search'): self.respond([video])
        elif p == '/api/v1/auth/feed': self.respond(dict(notifications=[], videos=[video]))
        elif p == '/api/v1/videos/testvideo01':
            self.respond(dict(**video, description='A generated test video. No YouTube access is involved.',
                              dashUrl='/media/dash.mpd', captions=[dict(label='English', language_code='en', url='/media/captions.vtt')],
                              recommendedVideos=[]))
        elif p == '/api/v1/auth/preferences': self.respond(prefs)
        elif p == '/api/v1/auth/subscriptions': self.respond([dict(author='Mobivious Studio', authorId='UCfixture')])
        elif p == '/api/v1/auth/playlists': self.respond(state['playlists'])
        elif p.startswith('/api/v1/auth/playlists/'):
            pl = next((x for x in state['playlists'] if x['playlistId'] == p.split('/')[-1]), None)
            self.respond(pl or {}, 200 if pl else 404)
        elif p == '/api/v1/auth/history': self.respond([dict(video_id=video['videoId'], title=video['title'], channel_name=video['author'], channel_id=video['authorId'], length_seconds=120)] if state['watched'] else [])
        elif p.startswith('/api/v1/auth/playback/'):
            self.respond(dict(position=state['position'], videoId='testvideo01'))
        elif p == '/api/v1/channels/UCfixture': self.respond(dict(author='Mobivious Studio', authorId='UCfixture', description='Fixture channel', subCount=42))
        elif p == '/api/v1/channels/UCfixture/videos': self.respond(dict(videos=[video]))
        elif p == '/api/v1/comments/testvideo01': self.respond(dict(comments=[dict(author='Viewer', content='A test comment.', likeCount=3, publishedText='today')]))
        else: self.respond({'error': 'Fixture endpoint not found'}, 404)

    def mutate(self):
        p = urlparse(self.path).path
        data = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or b'{}')
        # Do not log credentials or bearer values, even in disposable fixtures.
        state['events'].append(dict(method=self.command, path=p))
        if p == '/api/v1/mobile/login':
            self.respond(dict(accessToken='fixture-token', username=data.get('username', 'Viewer'), expiresAt=9999999999))
        elif p == '/api/v1/auth/preferences': prefs.update(data); self.respond(prefs)
        elif p.startswith('/api/v1/auth/playback/'):
            state['position'] = data.get('position', 0) if self.command != 'DELETE' else 0
            self.respond(status=204)
        elif p.startswith('/api/v1/auth/history'):
            state['watched'] = [] if self.command == 'DELETE' else ['testvideo01']
            self.respond(status=204)
        elif p == '/api/v1/auth/playlists' and self.command == 'POST':
            state['playlists'].append(dict(playlistId='IVfixture', title=data['title'], privacy=data['privacy'], videoCount=0, videos=[]))
            self.respond(dict(playlistId='IVfixture'), 201)
        elif p.startswith('/api/v1/auth/playlists/'):
            pl = state['playlists'][0]
            if p.endswith('/videos'):
                pl['videos'].append(dict(**video, indexId='A')); pl['videoCount'] = len(pl['videos'])
            elif '/videos/' in p: pl['videos'] = []; pl['videoCount'] = 0
            elif self.command == 'PATCH': pl.update(data)
            elif self.command == 'DELETE': state['playlists'].clear()
            self.respond(status=204)
        else: self.respond(status=204)

    do_POST = do_PATCH = do_PUT = do_DELETE = mutate

print(f'Fixture API listening on 127.0.0.1:{args.port}', flush=True)
ThreadingHTTPServer(('127.0.0.1', args.port), Handler).serve_forever()
