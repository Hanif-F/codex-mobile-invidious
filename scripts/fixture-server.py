#!/usr/bin/env python3
"""Disposable emulator API fixture; never imported by the application.
Run with --media-dir containing fixture.mp4, dash.mpd, hls.m3u8 and segments.
Bind localhost only; ADB reverse exposes it to the emulator for instrumentation.
"""
import argparse
from datetime import date
import json
import time
import xml.etree.ElementTree as ET
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlparse, parse_qs

parser = argparse.ArgumentParser()
parser.add_argument('--port', type=int, default=18080)
parser.add_argument('--media-dir', type=Path, required=True)
args = parser.parse_args()
def rich_formats(manifest=None):
    ns = {'d': 'urn:mpeg:dash:schema:mpd:2011'}
    manifest = manifest or args.media_dir / 'rich' / 'dash.mpd'
    if not manifest.exists(): return []
    formats = []
    for group in ET.parse(manifest).findall('.//d:AdaptationSet', ns):
        for representation in group.findall('d:Representation', ns):
            attr = representation.attrib
            item = dict(itag=attr['id'], type=f"{attr['mimeType']}; codecs=\"{attr['codecs']}\"", bitrate=attr['bandwidth'])
            if group.get('contentType') == 'video':
                item.update(size=f"{attr['width']}x{attr['height']}", fps=24)
            else:
                label = group.findtext('d:Label', namespaces=ns)
                role = group.find('d:Role', ns)
                if label or group.get('lang'):
                    item['audioTrack'] = dict(id=group.get('lang', 'und') + '.1', displayName=label or '')
                    if role is not None: item['audioTrack']['audioIsDefault'] = role.get('value') == 'main'
                item['isDrc'] = 'Stable Volume' in (label or '')
            formats.append(item)
    return formats
video = dict(videoId='testvideo01', title='A quiet moment · playback fixture', author='Mobivious Studio',
             authorId='UC' + 'a' * 22, lengthSeconds=120, viewCount=1200, publishedText='today',
             videoThumbnails=[dict(quality='medium', url='/media/thumbnail.jpg')],
             authorThumbnails=[dict(url='/ggpht/studio=s88', width=88, height=88)])
default_prefs = dict(watch_history=True, save_player_pos=True, dearrow_enabled=False, dearrow_show_original=True,
                     autoplay=True, continue_autoplay=True, video_loop=False, listen=False, local=True, speed=1.0, quality_dash='auto', video_codec='auto', captions=['', '', ''],
                     dark_mode='', ui_density='balanced', thin_mode=False, default_home='Popular',
                     feed_menu=['Popular', 'Trending', 'Subscriptions', 'Playlists'], region='US',
                     related_videos=True, extend_desc=False, comments=['youtube', ''], max_results=40,
                     sort='published', latest_only=False, unseen_only=False, notifications_only=False,
                     default_playlist=None, **{'continue': False}, show_member_videos=False, unrelated_setting='preserved')
prefs = default_prefs.copy()
state = dict(position=0, watched=[], playlists=[], savedPlaylists=[], playlistRss=False, sourceTitle='Live owner playlist', failSubscribe=False, events=[], stream='dash', mediaRequests=0, mediaPaths=[],
             identityReady=True, identityConfigured=False, failContribution=False, failSubmissions=False,
             originalMode='unlocked', titleLookups={}, contributions=[], avatarRequests=[], avatarFail=False)
def reset_comments():
    state.update(commentRequests=[], commentFailNext=False, commentDelayNext=0, commentEmpty=False)
reset_comments()

def fixture_comment(id, author='Viewer', text=None, **extra):
    return dict(commentId=id, author=author, authorId=video['authorId'], authorUrl='/channel/' + video['authorId'],
                authorThumbnail='/ggpht/commenter=s48', content=text or ('Comment body ' + id), likeCount=3,
                publishedText='today', **extra)

def source_playlist(id='PLlive'):
    if id.startswith('RD'):
        return dict(type='playlist', playlistId=id, mixId=id, title='Fixture mix', videoCount=-1, isMix=True,
                    seedVideoId='testvideo01', isOwned=False, isSaved=id in state['savedPlaylists'], privacy='public',
                    videos=[dict(video, index=0), dict(recommended, index=1)])
    return dict(type='playlist', playlistId=id, title=state['sourceTitle'], videoCount=2, privacy='unlisted',
                isOwned=False, isSaved=id in state['savedPlaylists'], author='Source owner', authorId=video['authorId'],
                playlistThumbnail='/media/thumbnail.jpg', authorThumbnails=video['authorThumbnails'], videos=[dict(video, index=0), dict(recommended, index=1)])

replacement = 'A calm scene'
recommended = dict(video, videoId='testvideo02', title='Another original title')
unknown_video = dict(video, videoId='unknownvid1', title='Unknown duration fixture', lengthSeconds=0)
live_video = dict(video, videoId='streamvid01', title='Live indicator fixture', liveNow=True)
def reset_playback():
    state.update(positions={}, playbackRequests=0, failPlayback=False, playbackDelayNext=0, indicatorVideos=False)
reset_playback()

visibility_member = dict(recommended, videoId='membervid01', title='Members-only fixture video', isMember=True, authorId='UC' + 'b' * 22)
visibility_other = dict(video, videoId='othervideo1', title='Other channel video', authorId='UC' + 'c' * 22)
def reset_visibility():
    state.update(visibilityVideos=False, hiddenFirstPage=False, blockedChannels={}, failBlockedRead=False, failBlockedWrite=False, visibilityReads=[], memberCurrent=False)
reset_visibility()

def browse_videos():
    if state['visibilityVideos']: return [video, visibility_member, visibility_other]
    return [video, recommended, unknown_video, live_video] if state['indicatorVideos'] else [video]

sponsor_categories = ('sponsor', 'selfpromo', 'interaction', 'intro', 'outro', 'preview', 'music_offtopic', 'filler')
sponsor_colors = dict(zip(sponsor_categories, ('#4caf50', '#ffeb3b', '#e91e63', '#00bcd4', '#2196f3', '#3f51b5', '#ff9800', '#9c27b0')))
def reset_sponsorblock():
    state.update(sponsorSegments=[], sponsorRequests=0, sponsorAuthorized=False, failSponsor=False, failPreferences=False, liveNow=False)
    prefs.update(sponsorblock_enabled=False, sponsorblock_modes=dict.fromkeys(sponsor_categories, 'manual'), sponsorblock_colors=sponsor_colors.copy(), sponsorblock_channel_overrides={})
reset_sponsorblock()

def reset_channels():
    state.update(channelTabs=['videos', 'streams'], channelRequests=[], channelFailNext=False, channelDelayNext=None)
reset_channels()

def reset_home_subscriptions():
    state.update(subscriptionChannels=[dict(author=video['author'], authorId=video['authorId'], authorThumbnails=video['authorThumbnails'])],
                 subscriptionRequests=[], subscriptionDelayNext=0, failSubscriptionRead=False,
                 discoveryRequests=[], discoveryDelayNext=0, discoveryDistinct=False)
reset_home_subscriptions()

def reset_search_history():
    state.update(searchTest=False, searchRequests=[], searchDelayNext=0, searchFailNext=False,
                 historyRequests=[], historyEntries=[], historyToday='2026-10-04', historyLegacy=False)
reset_search_history()

def history_group(watched):
    if not watched: return 4
    days = (date.fromisoformat(state['historyToday']) - date.fromisoformat(watched)).days
    return 0 if days == 0 else 1 if days == 1 else 2 if 2 <= days <= 6 else 3 if 7 <= days <= 29 else 4

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

    def xml(self, text, content_type='application/xml'):
        self.send_response(200)
        self.send_header('Content-Type', content_type)
        self.send_header('Cache-Control', 'private, no-store')
        self.end_headers()
        self.wfile.write(text.encode())

    def channel(self, tab, query):
        # Keep blank values: continuation= must fail just as it does on Invidious.
        params = parse_qs(query, keep_blank_values=True)
        token = params.get('continuation', [None])[0]
        event = dict(tab=tab, continuation=token, completed=False)
        state['channelRequests'].append(event)
        tabs = list(state['channelTabs'])
        delay = state['channelDelayNext']
        if delay and delay['tab'] == tab:
            state['channelDelayNext'] = None
            time.sleep(min(5000, max(0, delay['millis'])) / 1000)
        try:
            if tab == 'metadata':
                return self.respond(dict(author='Mobivious Studio', authorId=video['authorId'], description='Fixture channel', subCount=42, tabs=tabs, authorThumbnails=video['authorThumbnails']))
            if token is not None and not token.strip():
                return self.respond(dict(error='Error: non 200 status code. Youtube API returned status code 400.'), 500)
            if state['channelFailNext']:
                state['channelFailNext'] = False
                return self.respond(dict(error='Fixture channel temporarily unavailable'), 503)
            if tab not in tabs:
                return self.respond(dict(videos=[]))
            next_token = tab + '+/page=2%&'
            if token not in (None, next_token):
                return self.respond(dict(error='Invalid channel continuation'), 400)
            if tab == 'playlists':
                state['events'].append(dict(action='channel-playlists', sort=params.get('sort_by', ['last'])[0], continuation=token))
                return self.respond(dict(playlists=[source_playlist('PLlive' if token is None else 'RDopaque')], continuation=next_token if token is None else None))
            if tab == 'videos':
                item = video if token is None else dict(video, videoId='testvideo02', title='Another channel upload')
            else:
                item = dict(video, videoId='streamvid01' if token is None else 'streamvid02', title='Channel stream one' if token is None else 'Channel stream two', liveNow=token is None)
            if state['visibilityVideos'] and token is None: return self.respond(dict(videos=[video, visibility_member], continuation=next_token))
            return self.respond(dict(videos=[item], continuation=next_token) if token is None else dict(videos=[item]))
        finally:
            event['completed'] = True

    def search(self, scope, query):
        params = parse_qs(query, keep_blank_values=True)
        q = params.get('q', [''])[0]
        page = int(params.get('page', ['1'])[0])
        event = dict(scope=scope, q=q, page=page, authorized=bool(self.headers.get('Authorization')), completed=False)
        state['searchRequests'].append(event)
        delay = state['searchDelayNext']; state['searchDelayNext'] = 0
        items = []
        if q.strip() and q != 'missing':
            if q == 'hidden':
                items = [dict(visibility_member, videoId=f'member{i:05d}') for i in range(20)] if page == 1 else [video] if page == 2 else []
            elif q == 'many':
                items = [dict(video, videoId=f'scope{i:06d}', title=f'Search result {i}') for i in range(20)] if page == 1 else [recommended] if page == 2 else []
            else: items = [recommended if q == 'second' else video] if page == 1 else []
        fail = state['searchFailNext']; state['searchFailNext'] = False
        if delay: time.sleep(min(5000, max(0, delay)) / 1000)
        self.respond(dict(error='Fixture search temporarily unavailable') if fail else items, 503 if fail else 200)
        event['completed'] = True

    def do_GET(self):
        url = urlparse(self.path)
        p = url.path
        if p.startswith(('/api/v1/auth/dearrow/', '/api/v1/auth/playback', '/api/v1/auth/history', '/api/v1/auth/blocked_channels', '/api/v1/auth/subscriptions')) and self.headers.get('Authorization') != 'Bearer fixture-token':
            return self.respond(dict(error='Request must be authenticated'), 403)
        if p.startswith('/ggpht/'):
            state['avatarRequests'].append(dict(path=self.path, authorized=bool(self.headers.get('Authorization')), cookie=bool(self.headers.get('Cookie'))))
            if state['avatarFail']: return self.respond({}, 404)
            # Serve only local test media; no upstream requests are possible.
            file = args.media_dir / 'thumbnail.jpg'
            if not file.is_file(): return self.respond({}, 404)
            data = file.read_bytes()
            self.send_response(200)
            self.send_header('Content-Type', 'image/jpeg')
            self.send_header('Cache-Control', 'public, max-age=86400')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            try: self.wfile.write(data)
            except (BrokenPipeError, ConnectionResetError): pass
        elif p.startswith('/media/'):
            state['mediaRequests'] += 1
            state['mediaPaths'].append(p)
            root = args.media_dir.resolve()
            file = (root / p.removeprefix('/media/')).resolve()
            if not file.is_relative_to(root) or not file.is_file():
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
        elif p == '/api/v1/auth/blocked_channels':
            self.respond(dict(error='Fixture block list unavailable') if state['failBlockedRead'] else
                         [dict(authorId=id, author=name) for id, name in sorted(state['blockedChannels'].items(), key=lambda x: (x[1], x[0]))],
                         503 if state['failBlockedRead'] else 200)
        elif p in ('/api/v1/popular', '/api/v1/trending', '/api/v1/search'):
            if p in ('/api/v1/popular', '/api/v1/trending'):
                kind = p.rsplit('/', 1)[-1]
                event = dict(kind=kind, completed=False)
                state['discoveryRequests'].append(event)
                delay = state['discoveryDelayNext']; state['discoveryDelayNext'] = 0
                distinct = state['discoveryDistinct']
                if delay: time.sleep(min(5000, max(0, delay)) / 1000)
                event['completed'] = True
                if distinct: return self.respond([dict(video, title=f'{kind} discovery fixture')])
            if p == '/api/v1/search' and parse_qs(url.query).get('type') == ['playlist']:
                return self.respond([source_playlist('PLlive'), source_playlist('RDopaque'), source_playlist('IVother')] if parse_qs(url.query).get('page', ['1']) == ['1'] else [])
            if p == '/api/v1/search' and state['searchTest']: return self.search('global', url.query)
            if state['visibilityVideos']:
                state['visibilityReads'].append(dict(path=p, query=parse_qs(url.query), authorized=bool(self.headers.get('Authorization') or self.headers.get('Cookie'))))
            items = browse_videos()
            if p == '/api/v1/search' and state['hiddenFirstPage']:
                items = {1: [visibility_member], 2: [video]}.get(int(parse_qs(url.query).get('page', ['1'])[0]), [])
            self.respond(items)
        elif p == '/api/v1/auth/feed': self.respond(dict(notifications=[], videos=[v for v in browse_videos() if not prefs['unseen_only'] or v['videoId'] not in state['watched']]))
        elif p in ('/api/v1/videos/testvideo01', '/api/v1/videos/testvideo02', '/api/v1/videos/testvideo03'):
            selected = video if p.endswith('testvideo01') else dict(recommended, videoId=p.rsplit('/', 1)[-1])
            codec_manifest = {'codec': 'dash.mpd', 'codec-unsupported': 'unsupported.mpd', 'codec-missing': 'missing.mpd'}.get(state['stream'])
            dash = f'/media/codec/{codec_manifest}' if codec_manifest else '/media/rich/dash.mpd' if state['stream'] == 'rich' else '/media/dash.mpd'
            self.respond(dict(**selected, description='A generated test video. No YouTube access is involved.',
                              dashUrl=dash, adaptiveFormats=rich_formats(args.media_dir / dash.removeprefix('/media/')) if codec_manifest or state['stream'] == 'rich' else [],
                              hlsUrl='/media/master.m3u8' if state['stream'] == 'hls' else '', captions=[dict(label='English', language_code='en', url='/media/captions.vtt')],
                              recommendedVideos=[recommended, visibility_member, visibility_other] if state['visibilityVideos'] else ([recommended, unknown_video, live_video] if state['indicatorVideos'] else [recommended]), liveNow=state['liveNow'], isMember=state['memberCurrent']))
        elif p.startswith('/api/v1/dearrow/'):
            video_id = p.rsplit('/', 1)[-1]
            state['titleLookups'][video_id] = state['titleLookups'].get(video_id, 0) + 1
            self.respond(dict(title={'testvideo01': replacement, 'testvideo02': 'Another calm scene'}.get(video_id)))
        elif p == '/api/v1/auth/dearrow/identity':
            self.respond(dict(ready=state['identityReady'], configured=state['identityConfigured']))
        elif p.endswith('/submissions') and p.startswith('/api/v1/auth/dearrow/'):
            self.respond(dict(error='Fixture submissions unavailable') if state['failSubmissions'] else dict(titles=submissions()), 502 if state['failSubmissions'] else 200)
        elif p == '/api/v1/auth/preferences': self.respond(prefs)
        elif p == '/api/v1/auth/subscriptions':
            event = dict(completed=False, authorized=self.headers.get('Authorization') == 'Bearer fixture-token')
            state['subscriptionRequests'].append(event)
            channels = list(state['subscriptionChannels'])
            delay = state['subscriptionDelayNext']; state['subscriptionDelayNext'] = 0
            fail = state['failSubscriptionRead']
            if delay: time.sleep(min(5000, max(0, delay)) / 1000)
            event['completed'] = True
            self.respond(dict(error='Fixture subscriptions temporarily unavailable') if fail else channels, 503 if fail else 200)
        elif p == '/api/v1/auth/subscriptions/search': self.search('subscriptions', url.query)
        elif p == '/api/v1/auth/feed/rss': self.respond(dict(feedPath='/feed/private?token=fixture-rss-secret'))
        elif p == '/api/v1/auth/subscriptions/export':
            feed = 'https://www.youtube.com/feeds/videos.xml?channel_id=' if parse_qs(url.query).get('format') == ['newpipe'] else 'http://127.0.0.1:18080/feed/channel/'
            self.xml('<opml version="1.1"><body><outline text="Subscriptions"><outline type="rss" xmlUrl="' + feed + video['authorId'] + '"/></outline></body></opml>')
        elif p.endswith('/feed') and p.startswith('/api/v1/auth/playlists/'):
            self.xml('<feed xmlns="http://www.w3.org/2005/Atom"><id>iv:playlist:IVowned</id><title>My private playlist</title><updated>2026-10-04T00:00:00Z</updated></feed>', 'application/atom+xml')
        elif p == '/api/v1/auth/playlists': self.respond([dict(pl, isOwned=True, isSaved=False) for pl in state['playlists']] + [source_playlist(id) for id in state['savedPlaylists']])
        elif p.startswith('/api/v1/auth/playlists/') or p.startswith('/api/v1/playlists/'):
            plid = p.rsplit('/', 1)[-1]
            if plid.startswith('RD'):
                continuation = parse_qs(url.query).get('continuation', ['testvideo01'])[0]
                upcoming = 'testvideo02' if continuation == 'testvideo01' else 'testvideo03'
                return self.respond(dict(source_playlist(plid), videos=[dict(video, index=0, videoId=continuation), dict(recommended, index=1, videoId=upcoming)]))
            pl = next((dict(x, isOwned=True) for x in state['playlists'] if x['playlistId'] == plid), None)
            if state['playlistRss'] and plid in ('PLlive', 'IVother'): pl = source_playlist(plid)
            if plid == 'PLfixture':
                pl = dict(playlistId=plid, title='Public queue', videoCount=103,
                          videos=[dict(video if i % 2 == 0 else recommended, indexId='', index=i) for i in range(103)])
            if pl:
                params = parse_qs(url.query)
                offset = int(params.get('index', ['0'])[0])
                videos = [dict(v, index=i) for i, v in enumerate(pl['videos'])]
                self.respond(dict(pl, videos=videos[max(0, offset - 50):max(0, offset - 50) + 100]))
            else: self.respond(dict(error='Playlist does not exist.'), 404)
        elif p.startswith('/api/v1/mixes/'):
            continuation = parse_qs(url.query).get('continuation', ['testvideo01'])[0]
            upcoming = 'testvideo02' if continuation == 'testvideo01' else 'testvideo03'
            self.respond(dict(mixId=p.rsplit('/', 1)[-1], title='Fixture mix', videos=[dict(video, index=0, videoId=continuation), dict(recommended, index=1, videoId=upcoming)]))
        elif p == '/api/v1/auth/history':
            params = parse_qs(url.query, keep_blank_values=True)
            q = params.get('q', [''])[0].strip().lower()
            page = max(1, int(params.get('page', ['1'])[0]))
            state['historyRequests'].append(dict(q=q, page=page))
            catalog = {v['videoId']: v for v in [video, recommended, unknown_video, live_video]}
            saved = {e['video_id']: e for e in state['historyEntries']}
            entries = [saved.get(id, dict(video_id=id, title=catalog.get(id, {}).get('title'), channel_name=video['author'], channel_id=video['authorId'], length_seconds=catalog.get(id, {}).get('lengthSeconds', 0), authorThumbnails=video['authorThumbnails'])) for id in reversed(state['watched'])]
            organized = params.get('organized') == ['true'] and not state['historyLegacy']
            if organized:
                entries = [e for e in entries if not q or q in (e.get('title') or '').lower() or q in (e.get('channel_name') or '').lower()]
                entries.sort(key=lambda e: (history_group(e.get('latest_watched')), e.get('latest_watched') is None, -date.fromisoformat(e.get('latest_watched') or '0001-01-01').toordinal()))
            total = len(entries)
            size = int(params.get('max_results', [prefs['max_results']])[0])
            entries = entries[(page-1)*size:page*size]
            self.respond(dict(entries=entries, total=total, hasMore=size > 0 and page*size < total, today=state['historyToday'], timezone='Asia/Jakarta') if organized else entries)
        elif p == '/api/v1/auth/playback':
            state['playbackRequests'] += 1
            payload = dict(positions=dict(state['positions']), watched=list(state['watched']))
            delay = state['playbackDelayNext']; state['playbackDelayNext'] = 0
            if delay: time.sleep(min(5000, max(0, delay)) / 1000)
            self.respond(dict(error='Fixture indicators unavailable') if state['failPlayback'] else payload, 503 if state['failPlayback'] else 200)
        elif p.startswith('/api/v1/auth/playback/'):
            id = p.rsplit('/', 1)[-1]
            self.respond(dict(position=state['positions'][id], videoId=id) if id in state['positions'] else dict(error='Playback position does not exist.'), 200 if id in state['positions'] else 404)
        elif p == '/api/v1/channels/' + video['authorId']: self.channel('metadata', url.query)
        elif p in ('/api/v1/channels/' + video['authorId'] + '/videos', '/api/v1/channels/' + video['authorId'] + '/streams', '/api/v1/channels/' + video['authorId'] + '/playlists'): self.channel(p.rsplit('/', 1)[-1], url.query)
        elif p == '/api/v1/channels/' + video['authorId'] + '/search': self.search('channel', url.query)
        elif p.startswith('/api/v1/comments/'): self.comments(p.rsplit('/', 1)[-1], url.query)
        else: self.respond({'error': 'Fixture endpoint not found'}, 404)

    def comments(self, video_id, query):
        params = parse_qs(query)
        token = params.get('continuation', [''])[0]
        sort = params.get('sort_by', ['top'])[0]
        entry = dict(videoId=video_id, source=params.get('source', [''])[0], sort=sort,
                     continuation=token, completed=False, authorized=self.headers.get('Authorization') is not None)
        state['commentRequests'].append(entry)
        delay = state['commentDelayNext']; state['commentDelayNext'] = 0
        fail = state['commentFailNext']; state['commentFailNext'] = False
        empty = state['commentEmpty']
        if delay: time.sleep(min(5000, max(0, delay)) / 1000)
        entry['completed'] = True
        if fail: return self.respond(dict(error='Fixture comments temporarily unavailable'), 503)
        if empty: return self.respond(dict(comments=[], commentCount=0))
        parent = fixture_comment('parent', author='Fixture creator', text='A test comment. Jump to 0:30. 😀',
            contentHtml='<b>A test comment.</b><br>Jump to <a href="/watch?v=' + video_id + '&amp;t=30">0:30</a>. 😀 <img src="/media/thumbnail.jpg" alt=":wave:" />',
            authorIsChannelOwner=True, verified=True, isPinned=True, isEdited=True, isSponsor=True,
            creatorHeart=dict(creatorName='Mobivious Studio', creatorThumbnail='/ggpht/studio=s88'),
            replies=dict(replyCount=3, continuation='replies+/page=1%&'))
        replies = [fixture_comment('reply1', author='First reply'), fixture_comment('reply2', author='Second reply')]
        if token == 'replies+/page=1%&':
            return self.respond(dict(comments=replies, continuation='replies+/page=2%&'))
        if token == 'replies+/page=2%&':
            return self.respond(dict(comments=[replies[-1], fixture_comment('reply3', author='Third reply')]))
        tail = [fixture_comment('tail-' + str(i), author='Viewer ' + str(i)) for i in range(12)]
        if token == 'comments+/page=2%&':
            return self.respond(dict(comments=[tail[-1], fixture_comment('last', author='Last viewer')]))
        first = fixture_comment('newest', author='Newest viewer') if sort == 'new' else parent
        long = fixture_comment('long', author='Long commenter', text='\n'.join('Long comment line ' + str(i) for i in range(12)))
        self.respond(dict(comments=[first, long] + tail, continuation='comments+/page=2%&', commentCount=1234))

    def mutate(self):
        p = urlparse(self.path).path
        data = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or b'{}')
        if p == '/test/playlist-rss':
            state['playlistRss'] = True
            for key in ('sourceTitle', 'failSubscribe'):
                if key in data: state[key] = data[key]
            state['channelTabs'] = ['videos', 'streams', 'playlists']
            if not state['playlists']: state['playlists'] = [dict(playlistId='IVowned', title='My private playlist', privacy='private', videoCount=1, videos=[dict(video, indexId='A', index=0)])]
            return self.respond(state)
        if p == '/test/queue':
            state['playlists'] = [dict(playlistId='IVqueue', title='Queue fixture', privacy='private', videoCount=3, videos=[dict(video, indexId='A', index=0), dict(video, indexId='B', index=1), dict(recommended, indexId='C', index=2)])]
            state['failPlaylistSave'] = bool(data.get('failSave', False))
            return self.respond(state)
        elif p == '/test/avatars':
            state['avatarFail'] = data.get('fail', False)
            state['avatarRequests'] = []
            return self.respond(status=204)
        elif p == '/test/reset':
            state.update(position=0, watched=[], playlists=[], savedPlaylists=[], playlistRss=False, sourceTitle='Live owner playlist', failSubscribe=False, events=[], stream='dash', mediaRequests=0, mediaPaths=[], failPlaylistSave=False,
                         identityReady=True, identityConfigured=False, failContribution=False, failSubmissions=False,
                         originalMode='unlocked', titleLookups={}, contributions=[], avatarRequests=[], avatarFail=False)
            prefs.clear()
            prefs.update(default_prefs)
            reset_sponsorblock()
            reset_channels()
            reset_home_subscriptions()
            reset_playback()
            reset_visibility()
            reset_search_history()
            reset_comments()
            return self.respond({})
        if p == '/test/comments':
            for key in ('commentFailNext', 'commentDelayNext', 'commentEmpty'):
                if key in data: state[key] = data[key]
            return self.respond({})
        if p == '/test/preferences':
            prefs.update(data)
            return self.respond(prefs)
        if p == '/test/media-reset':
            state['mediaPaths'].clear()
            return self.respond({})
        if p == '/test/watched':
            for key in ('watched', 'positions', 'failPlayback', 'playbackDelayNext', 'indicatorVideos'):
                if key in data: state[key] = data[key]
            state['position'] = state['positions'].get('testvideo01', 0)
            if data.get('seedPlaylist'):
                state['playlists'] = [dict(playlistId='IVfixture', title='Indicator fixture playlist', privacy='private', videoCount=2,
                                          videos=[dict(video, indexId='A'), dict(recommended, indexId='B')])]
            return self.respond({})
        if p == '/test/visibility':
            for key in ('visibilityVideos', 'hiddenFirstPage', 'blockedChannels', 'failBlockedRead', 'failBlockedWrite', 'memberCurrent'):
                if key in data: state[key] = data[key]
            if 'show_member_videos' in data: prefs['show_member_videos'] = data['show_member_videos']
            if data.get('seedPlaylist'):
                state['playlists'] = [dict(playlistId='IVfixture', title='Visibility fixture playlist', privacy='private', videoCount=2,
                                          videos=[dict(video, indexId='A'), dict(visibility_member, indexId='B')])]
            return self.respond({})
        if p == '/test/channel':
            for key in ('channelTabs', 'channelFailNext', 'channelDelayNext'):
                if key in data: state[key] = data[key]
            return self.respond({})
        if p == '/test/home-subscriptions':
            for key in ('subscriptionChannels', 'subscriptionDelayNext', 'failSubscriptionRead', 'discoveryDelayNext', 'discoveryDistinct'):
                if key in data: state[key] = data[key]
            return self.respond({})
        if p == '/test/search-history':
            for key in ('searchTest', 'searchDelayNext', 'searchFailNext', 'historyEntries', 'historyToday', 'historyLegacy'):
                if key in data: state[key] = data[key]
            if 'historyEntries' in data: state['watched'] = [entry['video_id'] for entry in data['historyEntries']]
            if 'max_results' in data: prefs['max_results'] = data['max_results']
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
        if p.startswith(('/api/v1/auth/playback', '/api/v1/auth/history', '/api/v1/auth/blocked_channels')) and self.headers.get('Authorization') != 'Bearer fixture-token':
            return self.respond(dict(error='Request must be authenticated'), 403)
        if p.startswith('/api/v1/auth/blocked_channels/'):
            if self.headers.get('Authorization') != 'Bearer fixture-token': return self.respond(dict(error='Request must be authenticated'), 403)
            if state['failBlockedWrite']: return self.respond(dict(error='Fixture channel update failed'), 503)
            id = p.rsplit('/', 1)[-1]
            if self.command == 'POST': state['blockedChannels'].setdefault(id, data.get('name', '').strip()[:200] or id)
            elif self.command == 'DELETE': state['blockedChannels'].pop(id, None)
            return self.respond(None, 204)
        if p.startswith('/api/v1/auth/subscriptions/'):
            if self.headers.get('Authorization') != 'Bearer fixture-token': return self.respond(dict(error='Request must be authenticated'), 403)
            id = p.rsplit('/', 1)[-1]
            if self.command == 'POST' and all(c['authorId'] != id for c in state['subscriptionChannels']):
                state['subscriptionChannels'].append(dict(authorId=id, author=video['author'], authorThumbnails=video['authorThumbnails']))
            elif self.command == 'DELETE':
                state['subscriptionChannels'] = [c for c in state['subscriptionChannels'] if c['authorId'] != id]
            return self.respond(status=204)
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
            if not prefs['save_player_pos']: state['positions'].clear(); state['position'] = 0
            self.respond(prefs)
        elif p.startswith('/api/v1/auth/playback/'):
            id = p.rsplit('/', 1)[-1]
            if self.command == 'DELETE': state['positions'].pop(id, None)
            else:
                if not prefs['save_player_pos']: return self.respond(dict(error='Saving playback position is disabled in preferences.'), 409)
                state['positions'][id] = data.get('position', 0)
            state['position'] = state['positions'].get('testvideo01', 0)
            self.respond(status=204)
        elif p.startswith('/api/v1/auth/history'):
            if p == '/api/v1/auth/history' and self.command == 'DELETE':
                state['watched'] = []; state['positions'].clear(); state['position'] = 0
            else:
                if not prefs['watch_history']: return self.respond(dict(error='Watch history is disabled in preferences.'), 409)
                id = p.rsplit('/', 1)[-1]
                state['watched'] = [entry for entry in state['watched'] if entry != id]
                if self.command != 'DELETE': state['watched'].append(id)
            self.respond(status=204)
        elif p == '/api/v1/auth/playlists' and self.command == 'POST':
            plid = 'IVfixture' if not state['playlists'] else f"IVfixture{len(state['playlists'])}"
            state['playlists'].append(dict(playlistId=plid, title=data['title'], privacy=data['privacy'], videoCount=0, videos=[]))
            state['events'].append(dict(action='playlist-create', playlist=plid))
            self.respond(dict(playlistId=plid, title=data['title']), 201)
        elif p.startswith('/api/v1/auth/saved_playlists/'):
            plid = p.rsplit('/', 1)[-1]
            if state['failSubscribe']:
                state['failSubscribe'] = False
                return self.respond(dict(error='Fixture subscribe failed; retry'), 502)
            if self.command == 'PUT':
                if plid not in state['savedPlaylists']: state['savedPlaylists'].append(plid)
            elif plid in state['savedPlaylists']: state['savedPlaylists'].remove(plid)
            state['events'].append(dict(action='playlist-subscribe' if self.command == 'PUT' else 'playlist-unsubscribe', playlist=plid, seed=data.get('seedVideoId')))
            return self.respond(source_playlist(plid)) if self.command == 'PUT' else self.respond(status=204)
        elif p.startswith('/api/v1/auth/playlists/'):
            plid = p.split('/')[5]
            pl = next((x for x in state['playlists'] if x['playlistId'] == plid), None)
            if not pl: return self.respond(dict(error='Missing playlist'), 404)
            if p.endswith('/videos'):
                if state.get('failPlaylistSave'):
                    state['failPlaylistSave'] = False
                    return self.respond(dict(error='One-shot save failure'), 502)
                entry = dict(video if data['videoId'] == video['videoId'] else recommended, videoId=data['videoId'], indexId=f"{len(pl['videos']) + 10:X}", index=len(pl['videos']))
                pl['videos'].append(entry); pl['videoCount'] = len(pl['videos'])
                state['events'].append(dict(action='playlist-save', playlist=plid, video=data['videoId']))
                return self.respond(entry, 201)
            elif '/videos/' in p:
                stable = p.rsplit('/', 1)[-1]
                pl['videos'] = [v for v in pl['videos'] if v['indexId'] != stable]; pl['videoCount'] = len(pl['videos'])
            elif self.command == 'PATCH': pl.update(data)
            elif self.command == 'DELETE': state['playlists'].remove(pl)
            self.respond(status=204)
        else: self.respond(status=204)

    do_POST = do_PATCH = do_PUT = do_DELETE = mutate

print(f'Fixture API listening on 127.0.0.1:{args.port}', flush=True)
ThreadingHTTPServer(('127.0.0.1', args.port), Handler).serve_forever()
