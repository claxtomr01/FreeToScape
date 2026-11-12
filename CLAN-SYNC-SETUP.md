# FreeToScape clan activity and rank sync

Update, 11 November 2026: the plugin now provides **Clan activity → Officer setup** for normal-client use after Plugin Hub installation. Enter the server base URL and dedicated reporter credential in its masked field, choose **Use for this session**, and separately enable reporting. The credential is held only in memory and must be re-entered after restarting the plugin/client. The special launcher below is an optional legacy fallback. The tab shows where activity is sent; the bot stores it in `clan_activity.sqlite3`, and officers view it with `!clanactivity DISCORD_USER_ID` in bot-testing. No automatic activity-channel posts or remote HTTPS hosting have been added.

Installed locally in dry-run mode on 10 November 2026. Bot source/settings backup: `clan-sync-backup-20261110-144533`. The assistant did not restart the bot, create real account links, or change Discord roles/permissions. Final checks passed: 31 Java tests and 17 Python tests. The owner subsequently updated permissions: the latest read-only check confirms Administrator/effective Manage Roles, highest role position 93 above Maxxed at 72, and bot-testing access HTTP 200. Reporting remains off by default and live assignment remains separately gated.

This optional feature uploads officer-linked RuneScape names, clan ranks and observed clan-channel presence. It stores seven days of activity on the bot PC. It never reads or reports world numbers, locations, inventory, account login credentials or game chat.

The plugin's **Officer clan activity reporting** switch is off by default. Only an authorised officer reporter should enable it. Ordinary clan members use the normal launcher with reporting off. A dedicated private reporter credential is kept in the bot's existing `.env`, not in the plugin jar or saved feed URL.

## Installation and first test

Installer scripts require `-BotDirectory` pointing to the existing bot folder. These scripts change that installation and must not be run merely to build or upload source. The legacy officer launcher accepts `-BotSettingsPath` pointing to that folder's `.env`, or `F2S_BOT_SETTINGS`; the masked in-plugin setup is preferred for normal-client use.

1. Keep the production bot's posting state as intended. Do not resume bounty posting just to test clan tracking.
2. Install `bot-integration/clan_sync.py`, `clan_sync_roles.json`, and the startup hook using `bot-integration/install-clan-sync.ps1`. The installer backs up the existing bot/settings, leaves the existing bounty/password feed file untouched, and sets role assignment to **dry-run**.
3. Restart the bot normally to load the extension. Its new receiver binds to loopback port 8788. It does not replace the existing bounty feed on port 8787.
4. On the local authorised officer PC, run `powershell -ExecutionPolicy Bypass -File .\run-officer-client.ps1`. This uses the local reporter credential without printing it.
5. In FreeToScape settings set **Clan sync server URL** to `http://127.0.0.1:8788`, read the data disclosure, and enable **Officer clan activity reporting**. Log into the clan normally yourself. The assistant must not automate authentication. The Clan activity tab shows reporting status.
6. In the private **bot-testing** channel `1255502022237491244`, an officer with Manage Server can link accounts with `!clanlink DISCORD_MEMBER_ID RuneScape Name`. Other approved officer roles can be listed explicitly in `CLAN_SYNC_OFFICER_ROLE_IDS`; there is no default grant to ordinary members.
7. Use `!clansyncstatus`, `!clanactivity DISCORD_MEMBER_ID` and `!clanrankpreview` in that private channel. The preview command does not change roles. If the bot cannot view/send in that private channel, its channel permissions must be configured first.

Only linked accounts are reported. Links are one-to-one; replacing one requires an explicit unlink. `!clanunlink DISCORD_MEMBER_ID` deletes the link and retained activity but leaves Discord roles unchanged. No identities are inferred from similar Discord nicknames.

## Rank mapping and role permissions

Server: `1236133761859584010`. Reference channel: `1472292257611911372` (ranks-explained). Rank mapping is in `clan_sync_roles.json` and comes from the supplied channel plus the current server role list. It maps actual observed title names (Topaz, Sapphire, etc.) to the existing total-level roles. Custom mappings use the title/icon names shown there: Hellcat→Veteran, Beast→Eliteist, Coordinator→collector, Goblin→Raider, Feeder→Feeder. Unknown titles are skipped; the reporter does not calculate ranks from XP or personal accomplishments.

Cleric and PvP are intentionally unmapped, as requested. Staff/admin roles are not mapped. The planner refuses managed or privileged roles and refuses changes beyond the bot's role hierarchy. It adds/removes only mapped clan-rank roles and leaves other roles unchanged.

The bot needs Manage Roles and its highest role must sit above the mapped clan-rank roles, including Maxxed, while staying below staff. Do not grant Administrator. The bot cannot give itself these privileges using its current API permissions. Live assignment is separately gated by `CLAN_SYNC_APPLY_ROLES=true` in the bot `.env`; leave it false until the dry-run has been reviewed and permissions verified.

## Activity accuracy and retention

The recorded states are **observed online in the clan channel**, **not observed in that channel**, and **unknown**. Being absent from the channel is not conclusive evidence of globally offline status. A logged-out/disconnected/wrong-clan reporter supplies unknown observations; after 180 seconds without fresh reports, observations become unknown and are excluded from role planning.

History stores state/rank transitions, not minute-by-minute gameplay or exact play time. Activity older than seven days is purged while the service runs and before reads/updates. A boundary state can be carried forward at the start of the retention window; it is not presented as a new login event. If the bot is shut down, expiry runs when it restarts; SQLite backups/host backups need their own retention policy. Approved account links persist until explicitly unlinked. Reporter credentials and the bot's existing Google/Discord credentials have separate purposes and must remain private.

One reporter is accepted at a time. Another reporter cannot replace its active lease within 180 seconds. Configure HTTPS with a trusted reverse proxy before reporting from another PC; no public hosting has been provisioned. This data-upload feature needs an updated Plugin Hub review and is not covered by the earlier read-only compliance assessment.
