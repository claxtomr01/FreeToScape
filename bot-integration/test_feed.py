import tempfile
import unittest
from unittest.mock import AsyncMock, patch
from pathlib import Path
from types import SimpleNamespace as NS
from runelite_feed import Feed, NOTICE, is_new_bounty, install, current_bounty


class FeedTests(unittest.TestCase):
    def test_password_only_from_active_posted_bounty(self):
        state = {'bounty': 'Secret objective. - Password: TEST-CODE', 'bounty_id': 'B001', 'next_at': 200}
        self.assertEqual(current_bounty(state, 100), {'id': 'B001', 'password': 'TEST-CODE', 'expiresAt': 200})
        self.assertIsNone(current_bounty(state, 200))
        state['bounty_stopped'] = True
        self.assertIsNone(current_bounty(state, 100))
        self.assertIsNone(current_bounty({}, 100))

    def test_missing_or_unsafe_password_is_not_published(self):
        for text in ['No code', 'Password: <col=ff0000>bad', 'Password: ' + 'x' * 97]:
            self.assertIsNone(current_bounty({'bounty': text, 'next_at': 200}, 100))
    def test_integration_disabled_by_default(self):
        bot = NS()
        with patch.dict('os.environ', {}, clear=True):
            install(bot, '.', lambda: [])
        self.assertFalse(hasattr(bot, 'setup_hook'))
    def test_only_new_regular_bounty(self):
        bot = NS(user=NS(id=12), bounty_channel=34)
        message = NS(author=NS(id=12), channel=NS(id=34),
                     content='@everyone\n**F2S Bounty B001 — 6-hour challenge** • deadline\nSECRET')
        self.assertTrue(is_new_bounty(message, bot))
        for content in ['hello', '**F2S Daily Challenge**', '**F2S Bonus Challenge — no time limit**',
                        '**Current bounty**\nSECRET']:
            message.content = content
            self.assertFalse(is_new_bounty(message, bot))
        message.content = '**F2S Bounty B001 — 6-hour challenge**'
        message.author.id = 99
        self.assertFalse(is_new_bounty(message, bot))
        message.author.id = 12
        message.channel.id = 99
        self.assertFalse(is_new_bounty(message, bot))

    def test_restart_dedup_and_content_exclusion(self):
        with tempfile.TemporaryDirectory() as root:
            feed = Feed(root)
            feed.record(123)
            feed.record(123)
            restored = Feed(root)
            restored.record(123)
            self.assertEqual(restored.snapshot()['events'], [{'id': '123', 'text': NOTICE}])
            self.assertEqual(set(restored.snapshot()), {'events', 'teams', 'scoresUpdatedAt', 'scoresStale'})

    def test_bounded_history(self):
        with tempfile.TemporaryDirectory() as root:
            feed = Feed(root)
            for i in range(105):
                feed.record(i)
            self.assertEqual(len(feed.events), 100)
            self.assertEqual(feed.events[0]['id'], '5')


class LifecycleTests(unittest.IsolatedAsyncioTestCase):
    async def test_start_stop_preserves_original_hooks_and_records_only_notice(self):
        import asyncio
        from unittest.mock import Mock
        with tempfile.TemporaryDirectory() as root:
            original_setup, original_close = AsyncMock(), AsyncMock()
            bot = NS(user=NS(id=12), bounty_channel=34, setup_hook=original_setup,
                     close=original_close, add_listener=Mock())
            runner = NS(setup=AsyncMock(), cleanup=AsyncMock())
            site = NS(start=AsyncMock())
            with patch.dict('os.environ', {'RUNELITE_FEED_ENABLED': 'true'}), \
                    patch('runelite_feed.web.AppRunner', return_value=runner), \
                    patch('runelite_feed.web.TCPSite', return_value=site):
                install(bot, root, lambda: [10, 20])
                await bot.setup_hook()
                await asyncio.sleep(0.02)
                listener = bot.add_listener.call_args.args[0]
                await listener(NS(id=555, author=NS(id=12), channel=NS(id=34),
                                  content='**F2S Bounty B001 — 6-hour challenge**\nprivate bounty text'))
                await bot.close()
            original_setup.assert_awaited_once()
            original_close.assert_awaited_once()
            runner.cleanup.assert_awaited_once()
            self.assertEqual(Feed(root).events, [{'id': '555', 'text': NOTICE}])


if __name__ == '__main__':
    unittest.main()
