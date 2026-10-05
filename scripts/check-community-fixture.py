#!/usr/bin/env python3
"""Check the disposable channel/post API fixture without an Android device or upstream requests."""
import argparse
import json
from pathlib import Path
import socket
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--media-dir', type=Path, default=Path('.tools/test-media'))
    args = parser.parse_args()
    if not (args.media_dir / 'thumbnail.jpg').is_file():
        parser.error('Generate local test media with scripts/test-android.sh first.')
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    server = subprocess.Popen([sys.executable, str(Path(__file__).with_name('fixture-server.py')),
                               '--port', str(port), '--media-dir', str(args.media_dir)],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    base = f'http://127.0.0.1:{port}'

    def read(path, body=None):
        request = Request(base + path, data=json.dumps(body).encode() if body is not None else None)
        with urlopen(request, timeout=5) as response:
            return response.headers, response.read()

    def api(path, body=None):
        return json.loads(read(path, body)[1] or b'null')

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
        api('/test/reset', {})
        api('/test/community', {})
        owner = 'UC' + 'a' * 22
        channel_path = '/api/v1/channels/' + owner
        metadata = api(channel_path)
        assert metadata['tabs'] == ['videos', 'shorts', 'streams', 'podcasts', 'releases', 'courses', 'playlists', 'posts', 'channels']
        assert metadata['authorVerified'] and metadata['pronouns'] and metadata['descriptionHtml']
        assert metadata['authorBanners'][0]['width'] > metadata['authorBanners'][0]['height'] * 2
        for tab in metadata['tabs']:
            key = 'videos' if tab in ('videos', 'shorts', 'streams') else 'relatedChannels' if tab == 'channels' else 'comments' if tab == 'posts' else 'playlists'
            page = api(channel_path + '/' + tab)
            assert page[key], tab
            token = page['continuation']
            followup = api(channel_path + '/' + tab + '?' + urlencode({'continuation': token}))
            assert key in followup and not followup.get('continuation'), tab
        posts = api(channel_path + '/posts')['comments']
        assert [p.get('attachment', {}).get('type') if p.get('attachment') else None for p in posts] == [None, 'image', 'multiImage', 'video', 'playlist', 'poll', 'quiz', 'unknown']
        detail = api('/api/v1/post/Ugpost3')
        assert detail['authorId'] == owner and detail['singlePost'] and detail['comments'][0]['commentId'] == 'Ugpost3'
        api('/test/community', {'postFailNext': True})
        try:
            api('/api/v1/post/Ugpost1')
            raise AssertionError('Post failure injection did not return an error')
        except HTTPError as error:
            assert error.code == 503
        assert api('/api/v1/post/Ugpost1')['comments']
        api('/test/community', {'postLongText': True})
        long_post = api(channel_path + '/posts')['comments'][0]
        assert long_post['content'].count('\n') == 12 and 'Long post line 12' in long_post['contentHtml']
        assert long_post == api('/api/v1/post/Ugpost1')['comments'][0]
        api('/test/community', {'postLongText': False})
        comments_path = '/api/v1/post/Ugpost1/comments?'
        query = {'ucid': owner, 'sort_by': 'top'}
        comments = api(comments_path + urlencode(query))
        assert comments['postId'] == 'Ugpost1' and comments['comments'][0]['replies']['continuation']
        query['continuation'] = comments['comments'][0]['replies']['continuation']
        replies = api(comments_path + urlencode(query))
        assert len(replies['comments']) == 2
        query['continuation'] = replies['continuation']
        assert len(api(comments_path + urlencode(query))['comments']) == 2
        newest = api(comments_path + urlencode({'ucid': owner, 'sort_by': 'new'}))
        assert newest['comments'][0]['commentId'] == 'newest'
        log = api('/test/state')
        assert all(not request['authorized'] and request['ucid'] == owner for request in log['commentRequests'])
        assert all(not request['authorized'] for request in log['postRequests'])
        image_path = '/ggpht/post-photo=s1280'
        headers, image = read(image_path)
        assert headers['Content-Type'] == 'image/jpeg' and image
        print('Channel/community fixture passed: nine tabs, pagination, header metadata, all attachments, post resolution/retry, comment sorting/replies, public requests and proxy images.')
    finally:
        server.terminate()
        server.wait(timeout=5)


if __name__ == '__main__':
    main()
