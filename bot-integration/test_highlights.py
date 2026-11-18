import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from types import SimpleNamespace as NS
from unittest.mock import AsyncMock, patch
from clan_highlights import HighlightStore, announcement, install_highlights

def message(number, text='', channel=123, image=True):
    return NS(id=number, content=text, channel=NS(id=channel), author=NS(display_name='Test player'),
        created_at=datetime.fromtimestamp(number,timezone.utc),
        attachments=[NS(content_type='image/png',filename='drop.png',url='PRIVATE-CDN-URL')] if image else [])

class HighlightTests(unittest.TestCase):
    def test_keeps_latest_ten_and_survives_restart(self):
        with tempfile.TemporaryDirectory() as root:
            store=HighlightStore(root)
            for number in range(1,13): store.record(announcement(message(number),123))
            restored=HighlightStore(root)
            self.assertEqual([x['id'] for x in restored.items],[str(n) for n in range(12,2,-1)])
            self.assertTrue(restored.stale)
            restored.record(announcement(message(12,'Updated caption'),123))
            self.assertEqual(len(restored.items),10)
            self.assertEqual(restored.items[0]['summary'],'Updated caption')
    def test_skips_replies_and_wrong_channel(self):
        self.assertIsNone(announcement(message(1,'gz',image=False),123))
        self.assertIsNone(announcement(message(1,'Drop: test',channel=999),123))
        self.assertEqual(announcement(message(1,'Drop: Test item',image=False),123)['summary'],'Drop: Test item')
    def test_stores_no_attachments_or_ping_ids(self):
        with tempfile.TemporaryDirectory() as root:
            store=HighlightStore(root)
            store.record(announcement(message(1,'@everyone <@12345> https://private.example/file Screenshot'),123))
            saved=store.path.read_text()
            self.assertNotIn('PRIVATE-CDN-URL',saved)
            self.assertNotIn('private.example',saved)
            self.assertNotIn('@everyone',saved)
            self.assertNotIn('12345',saved)
            self.assertEqual(store.items[0]['summary'],'Screenshot')
    def test_disabled_source_registers_no_listeners(self):
        with tempfile.TemporaryDirectory() as root, patch.dict('os.environ',{},clear=True):
            install_highlights(NS(),HighlightStore(root))

class HighlightLifecycleTests(unittest.IsolatedAsyncioTestCase):
    async def test_backfill_and_source_failure_preserve_saved_entries(self):
        with tempfile.TemporaryDirectory() as root:
            store=HighlightStore(root)
            handlers={}
            async def history(limit):
                for number in range(20,0,-1): yield message(number)
            source=NS(history=history)
            bot=NS(get_channel=lambda channel:source, add_listener=lambda fn,name:handlers.update({name:fn}))
            with patch.dict('os.environ',{'CLAN_HIGHLIGHTS_CHANNEL_ID':'123'}):
                install_highlights(bot,store)
            await handlers['on_ready']()
            self.assertEqual(len(store.items),10)
            self.assertEqual(store.items[0]['id'],'20')
            self.assertFalse(store.stale)
            await handlers['on_message'](message(21,'Achievement: test',image=False))
            self.assertEqual(store.items[0]['id'],'21')
            source.history=lambda limit: (_ for _ in ()).throw(RuntimeError('offline'))
            await handlers['on_ready']()
            self.assertTrue(store.stale)
            self.assertEqual(store.items[0]['id'],'21')
