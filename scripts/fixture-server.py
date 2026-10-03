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
             authorId='UC' + 'a' * 22, lengthSeconds=120, viewCount=1200, publishedText='today',
             videoThumbnails=[dict(quality='medium', url='/media/thumbnail.jpg')])
default_prefs = dict(watch_history=True, save_player_pos=True, dearrow_enabled=False, dearrow_show_original=True,
                     autoplay=True, listen=False, local=True, speed=1.0, quality_dash='auto', captions=['', '', ''],
                     dark_mode='', ui_density='balanced', thin_mode=False, default_home='Popular',
                     feed_menu=['Popular', 'Trending', 'Subscriptions', 'Playlists'], region='US',
                     related_videos=True, extend_desc=False, comments=['youtube', ''], max_results=40,
                     sort='published', latest_only=False, unseen_only=False, notifications_only=False,
                     default_playlist=None, unrelated_setting='preserved')
prefs = default_prefs.copy()
state = dict(position=0, watched=[], playlists=[], events=[], stream='dash', mediaRequests=0,
             identityReady=True, identityConfigured=False, failContribution=False, failSubmissions=False,
             originalMode='unlocked', titleLookups={}, contributions=[])
replacement = 'A calm scene'
recommended = dict(video, videoId='testvideo02', title='Another original title')
sponsor_categories = ('sponsor', 'selfpromo', 'interaction', 'intro', 'outro', 'preview', 'music_offtopic', 'filler')
sponsor_colors = dict(zip(sponsor_categories, ('#4caf50', '#ffeb3b', '#e91e63', '#00bcd4', '#2196f3', '#3f51b5', '#ff9800', '#9c27b0')))
def reset_sponsorblock():
    state.update(sponsorSegments=[], sponsorRequests=0, sponsorAuthorized=False, failSponsor=False, failPreferences=False, liveNow=False)
    prefs.update(sponsorblock_enabled=False, sponsorblock_modes=dict.fromkeys(sponsor_categories, 'manual'), sponsorblock_colors=sponsor_colors.copy(), sponsorblock_channel_overrides={})
reset_sponsorblock()

def submissions():
    titles = [dict(title=replacement, original=False, votes=3, locked=False, UUID='proposal'),
              dict(title='Locked community title', original=False, votes=5, locked=True, UUID='locked')]
    if state['originalMode'] != 'missing':
        titles.insert(0, dict(title=video['title'], original=True, votes=1,
                             locked=state['originalMode'] == 'locked', UUID='original'))
    return titles

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
        if p.startswith('/api/v1/auth/dearrow/') and self.headers.get('Authorization') != 'Bearer fixture-token':
            return self.respond(dict(error='Request must be authenticated'), 403)
        if p.startswith('/media/'):
            state['mediaRequests'] += 1
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
        elif p == '/test/state': self.respond(dict(state, preferences=prefs))
        elif p.startswith('/api/v1/sponsorblock/'):
            state['sponsorRequests'] += 1
            state['sponsorAuthorized'] = bool(self.headers.get('Authorization') or self.headers.get('Cookie'))
            self.respond(dict(error='Segments unavailable') if state['failSponsor'] else dict(segments=state['sponsorSegments']), 503 if state['failSponsor'] else 200)
        elif p in ('/api/v1/popular', '/api/v1/trending', '/api/v1/search'): self.respond([video])
        elif p == '/api/v1/auth/feed': self.respond(dict(notifications=[], videos=[video]))
        elif p == '/api/v1/videos/testvideo01':
            self.respond(dict(**video, description='A generated test video. No YouTube access is involved.',
                              dashUrl='/media/dash.mpd', hlsUrl='/media/master.m3u8' if state['stream'] == 'hls' else '', captions=[dict(label='English', language_code='en', url='/media/captions.vtt')],
                              recommendedVideos=[recommended], liveNow=state['liveNow']))
        elif p.startswith('/api/v1/dearrow/'):
            video_id = p.rsplit('/', 1)[-1]
            state['titleLookups'][video_id] = state['titleLookups'].get(video_id, 0) + 1
            self.respond(dict(title={'testvideo01': replacement, 'testvideo02': 'Another calm scene'}.get(video_id)))
        elif p == '/api/v1/auth/dearrow/identity':
            self.respond(dict(ready=state['identityReady'], configured=state['identityConfigured']))
        elif p.endswith('/submissions') and p.startswith('/api/v1/auth/dearrow/'):
            self.respond(dict(error='Fixture submissions unavailable') if state['failSubmissions'] else dict(titles=submissions()), 502 if state['failSubmissions'] else 200)
        elif p == '/api/v1/auth/preferences': self.respond(prefs)
        elif p == '/api/v1/auth/subscriptions': self.respond([dict(author='Mobivious Studio', authorId=video['authorId'])])
        elif p == '/api/v1/auth/playlists': self.respond(state['playlists'])
        elif p.startswith('/api/v1/auth/playlists/'):
            pl = next((x for x in state['playlists'] if x['playlistId'] == p.split('/')[-1]), None)
            self.respond(pl or {}, 200 if pl else 404)
        elif p == '/api/v1/auth/history': self.respond([dict(video_id=video['videoId'], title=video['title'], channel_name=video['author'], channel_id=video['authorId'], length_seconds=120)] if state['watched'] else [])
        elif p.startswith('/api/v1/auth/playback/'):
            self.respond(dict(position=state['position'], videoId='testvideo01'))
        elif p == '/api/v1/channels/' + video['authorId']: self.respond(dict(author='Mobivious Studio', authorId=video['authorId'], description='Fixture channel', subCount=42))
        elif p == '/api/v1/channels/' + video['authorId'] + '/videos': self.respond(dict(videos=[video]))
        elif p == '/api/v1/comments/testvideo01': self.respond(dict(comments=[dict(author='Viewer', content='A test comment.', likeCount=3, publishedText='today')]))
        else: self.respond({'error': 'Fixture endpoint not found'}, 404)

    def mutate(self):
        p = urlparse(self.path).path
        data = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or b'{}')
        if p == '/test/reset':
            state.update(position=0, watched=[], playlists=[], events=[], stream='dash', mediaRequests=0,
                         identityReady=True, identityConfigured=False, failContribution=False, failSubmissions=False,
                         originalMode='unlocked', titleLookups={}, contributions=[])
            prefs.clear()
            prefs.update(default_prefs)
            reset_sponsorblock()
            return self.respond({})
        if p == '/test/sponsorblock':
            for key in ('sponsorSegments', 'failSponsor', 'failPreferences', 'liveNow'):
                if key in data: state[key] = data[key]
            for key in ('sponsorblock_enabled', 'sponsorblock_modes', 'sponsorblock_colors', 'sponsorblock_channel_overrides'):
                if key in data: prefs[key] = data[key]
            return self.respond({})
        if p == '/test/dearrow':
            for key in ('identityReady', 'failContribution', 'failSubmissions', 'originalMode'):
                if key in data: state[key] = data[key]
            for key in ('dearrow_enabled', 'dearrow_show_original'):
                if key in data: prefs[key] = data[key]
            if data.get('seedPlaylist'):
                state['playlists'] = [dict(playlistId='IVfixture', title='DeArrow fixture playlist', privacy='private', videoCount=1, videos=[dict(video, indexId='A')])]
            return self.respond({})
        if p == '/test/stream':
            state['stream'] = data['type']
            return self.respond({})
        # Do not log credentials or bearer values, even in disposable fixtures.
        state['events'].append(dict(method=self.command, path=p))
        if p.startswith('/api/v1/auth/dearrow/'):
            if self.headers.get('Authorization') != 'Bearer fixture-token': return self.respond(dict(error='Request must be authenticated'), 403)
            if not state['identityReady']: return self.respond(dict(error='The instance administrator must configure DeArrow contribution storage.'), 503)
            if p.endswith('/identity'):
                # Retain only configured status; never retain or expose an imported private ID.
                if data.get('privateId', '').strip(): state['identityConfigured'] = True
                return self.respond(dict(ok=True))
            if state['failContribution']: return self.respond(dict(error='DeArrow did not confirm this action. Refresh submissions before trying again.'), 502)
            state['contributions'].append(data)
            state['identityConfigured'] = True
            return self.respond(dict(ok=True))
        if p == '/api/v1/mobile/login':
            self.respond(dict(accessToken='fixture-token', username=data.get('username', 'Viewer'), expiresAt=9999999999))
        elif p == '/api/v1/auth/preferences':
            if state['failPreferences']: return self.respond(dict(error='Fixture settings could not be saved'), 503)
            for key, value in data.items():
                if key in ('sponsorblock_modes', 'sponsorblock_colors'): prefs[key].update(value)
                elif key == 'sponsorblock_channel_overrides':
                    for channel, entry in value.items():
                        if entry is None or (entry.get('enabled') is None and not entry.get('modes')): prefs[key].pop(channel, None)
                        else: prefs[key][channel] = dict(entry, name=prefs[key].get(channel, {}).get('name', 'Mobivious Studio'))
                else: prefs[key] = value
            self.respond(prefs)
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
