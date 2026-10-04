#!/usr/bin/env python3
"""Verify the disposable avatar API/image fixture without contacting an upstream host."""
import argparse
import json
from pathlib import Path
import socket
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--media-dir', type=Path, default=Path('.tools/test-media'))
    args = parser.parse_args()
    if not (args.media_dir / 'thumbnail.jpg').is_file():
        parser.error('Local test media is required; generate it with scripts/test-android.sh first.')
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    server = subprocess.Popen([sys.executable, str(Path(__file__).with_name('fixture-server.py')),
                               '--port', str(port), '--media-dir', str(args.media_dir)],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    base = f'http://127.0.0.1:{port}'

    def read(path, *, body=None, auth=False):
        request = Request(base + path, data=json.dumps(body).encode() if body is not None else None)
        if auth:
            request.add_header('Authorization', 'Bearer fixture-token')
        with urlopen(request, timeout=5) as response:
            return response.status, response.headers, response.read()

    def api(path, **kwargs):
        return json.loads(read(path, **kwargs)[2] or b'null')

    try:
        for attempt in range(100):
            try:
                api('/test/state')
                break
            except URLError:
                if server.poll() is not None:
                    raise RuntimeError('Fixture server exited before starting')
                time.sleep(.05)
        else:
            raise RuntimeError('Fixture server did not start')
        api('/test/reset', body={})
        video = api('/api/v1/popular')[0]
        channel = api('/api/v1/channels/' + video['authorId'])
        assert channel['authorThumbnails'] == video['authorThumbnails']
        details = api('/api/v1/videos/' + video['videoId'])
        assert details['authorThumbnails'] and details['recommendedVideos'][0]['authorThumbnails']
        subscriptions = api('/api/v1/auth/subscriptions', auth=True)
        assert subscriptions[0]['authorThumbnails']
        api('/test/playlist-rss', body={})
        playlist = api('/api/v1/playlists/PLlive')
        assert playlist['authorThumbnails'] and playlist['videos'][0]['authorThumbnails']
        api('/test/watched', body={'watched': [video['videoId']]})
        history = api('/api/v1/auth/history?details=true&organized=true', auth=True)
        assert history['entries'][0]['authorThumbnails']
        comments = api('/api/v1/comments/' + video['videoId'] + '?source=youtube&sort_by=top')
        assert comments['comments'][0]['authorThumbnail'].startswith('/ggpht/')
        assert comments['comments'][0]['creatorHeart']['creatorThumbnail'].startswith('/ggpht/')
        assert not api('/test/state')['avatarRequests'], 'Metadata reads fetched avatar images'
        image_path = '/ggpht/studio=s176?key=a%2Bb&size=88'
        status, headers, image = read(image_path)
        assert status == 200 and headers['Content-Type'] == 'image/jpeg' and image
        assert headers['Cache-Control'] == 'public, max-age=86400'
        logged = api('/test/state')['avatarRequests']
        assert logged == [{'path': image_path, 'authorized': False, 'cookie': False}]
        api('/test/avatars', body={'fail': True})
        try:
            read('/ggpht/studio=s176')
            raise AssertionError('Image failure did not return HTTP 404')
        except HTTPError as error:
            assert error.code == 404
        print('Avatar API/image fixture passed: placements, deferred images, query preservation, caching headers, credential exclusion and image failures.')
    finally:
        server.terminate()
        server.wait(timeout=5)


if __name__ == '__main__':
    main()
