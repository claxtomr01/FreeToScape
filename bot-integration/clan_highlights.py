"""Latest ten voluntary highlight posts; no image downloads or ordinary chat relay."""
import asyncio
import json
import logging
import os
from pathlib import Path
import re

LIMIT = 10

def clean(value, limit):
    text = re.sub(r'<[@#][!&]?\d+>', '', str(value or ''))
    text = re.sub(r'https?://\S+', '', text)
    return re.sub(r'\s+', ' ', text.replace('@everyone', '').replace('@here', '')).strip()[:limit]

def announcement(message, channel_id):
    if message.channel.id != channel_id:
        return None
    caption = str(message.content or '')
    image = any((getattr(a, 'content_type', '') or '').startswith('image/') or
                str(getattr(a, 'filename', '')).lower().endswith(('.png','.jpg','.jpeg','.webp','.gif'))
                for a in message.attachments)
    labelled = re.match(r'^\s*(achievement|drop|pet|milestone)\s*:', caption, re.I)
    if not image and not labelled:
        return None
    summary = clean(caption, 240) or 'Achievement/drop screenshot'
    return {'id': str(message.id), 'poster': clean(message.author.display_name, 64) or 'Clan member',
            'summary': summary, 'postedAt': int(message.created_at.timestamp())}

class HighlightStore:
    def __init__(self, root):
        self.path = Path(root) / 'clan-highlights-state.json'
        self.items = []
        self.stale = True
        if self.path.exists():
            data = json.loads(self.path.read_text(encoding='utf-8'))
            self.items = self.ordered(data.get('highlights', []))

    @staticmethod
    def ordered(items):
        rows = {str(x['id']): {'id': str(x['id']), 'poster': clean(x['poster'],64),
                'summary': clean(x['summary'],240), 'postedAt': int(x['postedAt'])}
                for x in items if str(x.get('id','')).isdigit()}
        return sorted(rows.values(), key=lambda x: (x['postedAt'], int(x['id'])), reverse=True)[:LIMIT]

    def replace(self, items):
        rows = self.ordered(items)
        temporary = self.path.with_suffix('.tmp')
        temporary.write_text(json.dumps({'highlights': rows}), encoding='utf-8')
        temporary.replace(self.path)
        self.items = rows
        self.stale = False

    def record(self, item):
        self.replace([x for x in self.items if x['id'] != item['id']] + [item])

def install_highlights(bot, store):
    channel = os.getenv('CLAN_HIGHLIGHTS_CHANNEL_ID', '')
    if not channel.isdecimal():
        return
    channel_id = int(channel)
    lock = asyncio.Lock()
    async def load():
        try:
            source = bot.get_channel(channel_id) or await bot.fetch_channel(channel_id)
            rows = []
            async for message in source.history(limit=100):
                item = announcement(message, channel_id)
                if item:
                    rows.append(item)
                    if len(rows) == LIMIT: break
            # Replace from source history so deleted posts are not resurrected.
            store.replace(rows)
        except Exception:
            store.stale = True
            logging.getLogger('f2s').warning('Clan highlights unavailable; saved entries retained.')
    async def ready():
        async with lock:
            await load()
    async def message_received(message):
        item = announcement(message, channel_id)
        if item:
            try:
                async with lock:
                    store.record(item)
            except OSError:
                store.stale = True
                logging.getLogger('f2s').warning('Clan highlight could not be saved.')
    async def edited(payload):
        if payload.channel_id == channel_id:
            await ready()
    async def deleted(payload):
        if payload.channel_id == channel_id:
            await ready()
    bot.add_listener(ready, 'on_ready')
    bot.add_listener(message_received, 'on_message')
    bot.add_listener(edited, 'on_raw_message_edit')
    bot.add_listener(deleted, 'on_raw_message_delete')
    bot.add_listener(deleted, 'on_raw_bulk_message_delete')
