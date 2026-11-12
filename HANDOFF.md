# FreeToScape — developer handoff

Update, 12 November 2026: current source is published at https://github.com/claxtomr01/FreeToScape. Later work added the supplied logo, red Clan Broadcast prefix, Warden-and-above exclusion and session-only masked officer setup. All 40 Java tests pass. See PLUGIN-HUB-README.md and LIVE-TEST-2026-11-11.md for current behaviour and live evidence; older development counts and publication status below are historical. Installer scripts now require an explicit `-BotDirectory`; the legacy officer launcher accepts `-BotSettingsPath` or `F2S_BOT_SETTINGS`. Local path overrides remain ignored under `.tools`.

## Project and current state

Java RuneLite plugin (RuneLite 1.13.6, Java release target 11; build/run with JDK 17 and Gradle 8.14.3) plus Python 3.11+ Discord bot integration. Latest checks: **31 Java tests + 17 Python tests passed**. Development build only: no Plugin Hub/Jagex approval and no public hosting.

Implemented:
- Home welcome; compact Events overview listing active names only. Selector includes all active events plus five most recently completed events, ordered by end date.
- SOTW XP and BOTW kill-count standings; automatic event selection with manual ID overrides. Completed winners/ties, final scores, shared ranks and score gaps. Names/totals are bold; gaps muted.
- Slaughterhouse team totals, lead/tie comparison and stale-data handling.
- Refresh button with 30-second cooldown; separate service status/last-success times. Competitions refresh every seven minutes, feed about every 15 seconds.
- Local new-bounty chat notice is exactly `a new bounty has been posted`. No bounty description or arbitrary Discord chat is relayed.
- Optional simple active-bounty password overlay. **Password-feed source is prepared but was NOT installed**, pending the owner's approval of who can access the password.
- Officer-linked clan activity reporter, authenticated bot receiver, seven-day activity history, rank mapping and dry-run role plans. Plugin reporting is off by default. Bot extension was installed locally in dry-run mode; restarting it was left to the owner. No real account links or role sync have been performed by the assistant.

## Important configuration

- Wise Old Man group: `8036` — https://wiseoldman.net/groups/8036
- Discord invite: https://discord.gg/ZBQZDtg2H
- Discord server: `1236133761859584010`
- Rank reference channel: `1472292257611911372`
- Bounty channel: `1569362451974201436`
- Private bot-testing command channel: `1255502022237491244`
- Slaughterhouse sheet: https://docs.google.com/spreadsheets/d/1xNNRwzHp87BnufzNRDU_M_kpB_8FIroqPykUcGDSCsM/edit
- Score/bounty feed: loopback `8787/v1/feed`; authenticated clan receiver: loopback `8788/v1/clan/...`.

Latest read-only Discord check: bot has Administrator (therefore effective Manage Roles), highest role position 93 vs Maxxed 72, and bot-testing access succeeds. Owner made these changes. Prefer narrower Manage Roles/channel permissions later; do not change permissions as part of routine development.

## Privacy and role-sync boundaries

Only officer-approved linked accounts may be tracked. Store activity for seven days; links persist until explicit unlink. Channel absence is **not proof of global offline status**. Expired/disconnected reporters become unknown and cannot trigger role removal. Never send worlds, locations, inventory, game chat or login credentials.

Role assignment remains **dry-run** (`CLAN_SYNC_APPLY_ROLES=false`). Cleric and PvP are deliberately unmapped; staff/admin roles are excluded. Touch only mapped rank roles. Use verified account links; never match Discord nicknames automatically. Review actual in-game rank titles and dry-run output before considering live assignment. A reporter token is a trusted officer credential and must never be bundled with the distributed plugin.

Normal plugin network requests disable disk caching, cookies, redirects and inherited authentication. Remote operators can still see/log connecting IPs. The new optional activity uploader changes the original read-only privacy model and needs a fresh release review.

## Build and test

```powershell
# JDK 17 installed, JAVA_HOME set
.\gradlew.bat test jar
.\gradlew.bat run

# Separate Python virtual environment
python -m pip install -r bot-integration/requirements.txt
python -m unittest discover -s bot-integration -p 'test_*.py' -v
```

Unit tests use synthetic data; they do not require production credentials or sending Discord messages. Do not launch the production bot during development. Bot runtime challenge lists, state, credentials and activity database are intentionally absent from the source bundle. Use a separate test bot/server for integration work.

## Main files / parallel work

| Area | Files | Suggested owner |
| --- | --- | --- |
| RuneLite UI and events | `FreeToScapePlugin`, `ClanEvents`, `LeaderboardPane`, `Standings`, `EventFormat` | Developer A |
| Clan reporter and role sync | `ClanActivityReporter`, `bot-integration/clan_sync.py`, `clan_sync_roles.json` | Developer B |
| Bot feed | `bot-integration/runelite_feed.py`, `bot.py` | Coordinate before editing |

Agree ownership of `FreeToScapeConfig.java`, bot startup hooks and the HTTP schema before changing them. Use separate Git branches/clones; exchange small commits/patches rather than overwriting the owner's live folder. This ZIP is a snapshot, not a live shared checkout. No repository has been published by the assistant.

## Next work

1. Validate one officer-approved account link and the observed in-game title against the mapping, in dry-run mode.
2. Review role authority, activity retention/privacy and the reporter threat model before enabling live assignments.
3. Decide password-feed access policy; do not deploy that pending source change implicitly.
4. Add HTTPS hosting for remote members/reporters, then live UI/integration checks and Plugin Hub submission preparation.

Owner preferences: ask before adding unrelated QoL features. The adjustable-font/overlay-styling/preview trial was cancelled and removed. Do not reintroduce it. Do not send announcements, resume bounty posting, deploy, or enable live role changes merely to test a UI change.
