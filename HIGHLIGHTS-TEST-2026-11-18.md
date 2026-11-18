# Clan Highlights test — 18 November 2026

Source channel: `1255348400019214447`. The channel contains mainly screenshot posts and replies. The feature accepts image attachments or text starting with `Achievement:`, `Drop:`, `Pet:` or `Milestone:`. It displays caption, poster display name and posting time, rather than inferring item names or achievements from screenshots. The source-channel button opens Discord to view originals.

The bot backfilled ten real entries. One clearly labelled test-only announcement was posted to the authorised channel. It reached the live `/v1/feed` highlights list, appeared after constructing a fresh store from disk, and left bounty-notice history unchanged. The same test post was deleted after verification; source deletion removed it from the feed and restored ten real entries. No test achievement remains in the list.

The latest ten are persisted in the bot's private `clan-highlights-state.json`. They remain until replaced by newer posts or removed from the source. The client retains visible entries during feed failures with a stale warning; restarting the client loads the bot's saved list when the feed is reachable. No additional player-side cache was added. Highlights retention is separate from seven-day officer activity history.

Installed only highlights hooks and the new module in the local bot, with a backup named `highlights-backup-20261118-112801`. The existing feed's password behaviour was preserved. Bounty posting was paused at startup and rank-sync configuration was not changed. Credentials, saved highlight content and bot state are excluded from GitHub.

Validation: Java build/formatting tests and Python filtering, persistence, restart/backfill and source-failure tests pass. The live test verified Discord-to-feed delivery and persistence. An in-game visual check of the new tab requires restarting the development client and remains separate from the completed feed test.
