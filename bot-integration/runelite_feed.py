"""Read-only feed: notices, scores and only the currently posted bounty password."""
import asyncio
import json
import logging
import os
import re
import time
from pathlib import Path
from aiohttp import web

NOTICE = 'a new bounty has been posted'


def current_bounty(state, now=None):
    now = time.time() if now is None else now
    expires = state.get('next_at', 0)
    if (not state.get('bounty') or state.get('bounty_stopped')
            or not isinstance(expires, (int, float)) or expires <= now):
        return None
    match = re.search(r'\bPassword:[ \t]*([^\r\n]+)', state['bounty'], re.IGNORECASE)
    if not match:
        return None
    password = match.group(1).strip().strip('`*_ ').strip()
    if not password or len(password) > 96 or '<' in password or '>' in password:
        return None
    return {'id': str(state.get('bounty_id', '')), 'password': password, 'expiresAt': int(expires)}


def is_new_bounty(message, bot):
    # Only this bot's successful scheduled regular-bounty announcement qualifies.
    return (bot.user is not None and message.author.id == bot.user.id
            and message.channel.id == bot.bounty_channel
            and re.search(r'^\*\*F2S Bounty [A-Z0-9_-]+ — 6-hour challenge\*\*',
                          message.content, re.MULTILINE) is not None)


class Feed:
    def __init__(self, root):
        self.path = Path(root) / 'runelite-feed-state.json'
        self.events = []
        if self.path.exists():
            data = json.loads(self.path.read_text(encoding='utf-8'))
            # Rebuild from IDs only; no old message contents enter the feed.
            self.events = [{'id': str(e['id']), 'text': NOTICE}
                           for e in data.get('events', [])][-100:]
        self.scores = None
        self.score_updated_at = None
        self.score_error = True

    def record(self, message_id):
        event_id = str(message_id)
        if any(e['id'] == event_id for e in self.events):
            return
        events = (self.events + [{'id': event_id, 'text': NOTICE}])[-100:]
        temporary = self.path.with_suffix('.tmp')
        temporary.write_text(json.dumps({'events': events}), encoding='utf-8')
        temporary.replace(self.path)
        self.events = events

    def snapshot(self):
        return {'events': list(self.events), 'teams': self.scores,
                'scoresUpdatedAt': self.score_updated_at,
                'scoresStale': self.score_error}


def install(bot, root, read_scores):
    """Opt-in; loopback binding requires a TLS reverse proxy for remote clients."""
    if os.getenv('RUNELITE_FEED_ENABLED', '').lower() != 'true':
        return
    feed = Feed(root)
    runner = None
    score_task = None
    original_setup = bot.setup_hook
    original_close = bot.close

    async def refresh_scores():
        while True:
            try:
                values = await asyncio.to_thread(read_scores)
                feed.scores = [{'name': 'Team 1', 'score': str(values[0])},
                               {'name': 'Team 2', 'score': str(values[1])}]
                feed.score_updated_at = int(time.time())
                feed.score_error = False
            except Exception:
                feed.score_error = True
                logging.getLogger('f2s').warning('RuneLite score refresh unavailable.')
            await asyncio.sleep(420)

    async def snapshot(request):
        data = feed.snapshot()
        data['currentBounty'] = current_bounty(bot.state)
        return web.json_response(data, headers={'Cache-Control': 'no-store'})

    async def setup():
        nonlocal runner, score_task
        app = web.Application()
        app.router.add_get('/v1/feed', snapshot)
        runner = web.AppRunner(app, access_log=None)
        await runner.setup()
        try:
            await web.TCPSite(runner, '127.0.0.1', int(os.getenv('RUNELITE_FEED_PORT', '8787'))).start()
            await original_setup()
            score_task = asyncio.create_task(refresh_scores())
        except BaseException:
            await runner.cleanup()
            raise

    async def close():
        if score_task:
            score_task.cancel()
            try:
                await score_task
            except asyncio.CancelledError:
                pass
        if runner:
            await runner.cleanup()
        await original_close()

    async def on_message(message):
        if is_new_bounty(message, bot):
            try:
                feed.record(message.id)
            except OSError:
                logging.getLogger('f2s').warning('RuneLite notification could not be saved.')

    bot.setup_hook = setup
    bot.close = close
    bot.add_listener(on_message, 'on_message')
