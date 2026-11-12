# FreeToScape RuneLite plugin

Officer setup now lives in **Clan activity → Officer setup**: a masked credential field supplies the dedicated reporter credential for this plugin session without saving it in RuneLite settings. The tab shows the destination bot and how to view retained activity. See [PLUGIN-HUB-README.md](../PLUGIN-HUB-README.md) for current public setup instructions. Secure storage across restarts and remote HTTPS hosting remain future work.

Plugin Hub preparation (11 November 2026): see [PLUGIN-HUB-SUBMISSION.md](../PLUGIN-HUB-SUBMISSION.md). The source now uses injected Gson, declares the standard Hub build, and carries the BSD 2-Clause licence. Run `prepare-plugin-hub.ps1` to export public plugin source with the public-facing README; this excludes the local bot and private runtime data. The live test remains paused and no publication or official approval is claimed.

Optional officer-linked clan activity/rank sync: see [CLAN-SYNC-SETUP.md](../CLAN-SYNC-SETUP.md). Reporting is off by default; the installed bot extension is in dry-run mode and needs a restart and permission setup. This optional feature adds authorised member-data uploads and seven-day server-side activity storage.

The Events overview shows active event names only. Its selector contains all active events plus the five most recently completed events. The temporary text-size and overlay-styling controls have been removed, including the Appearance tab. Existing refresh, connection-status and formatting improvements are retained.

See [PRIVACY-AND-COMPLIANCE.md](../PRIVACY-AND-COMPLIANCE.md) for the code-review scope, data handling, privacy changes and outstanding release checks. This is an unreleased development plugin, not a Jagex-certified or Plugin Hub-approved release.

First implementation: SOTW XP standings, BOTW kill-count standings, Slaughterhouse team totals, and one local chat notice when F2S Bounty Bot posts a new regular bounty.

The notice is exactly:

> [Clan Broadcast] A new bounty has been posted!

The `[Clan Broadcast]` prefix is red; the sentence uses the normal game-message colour. No bounty details are included.

No bounty descriptions, daily/bonus messages or general Discord announcements are relayed to chat. Every member needs the plugin enabled to see this local message. It does not send messages to OSRS clan chat.

Optional **Show bounty password** in FreeToScape settings displays the currently posted regular bounty password in a movable on-screen overlay. It is off by default and leaves the chat sentence unchanged. The overlay clears when the bounty expires, is stopped, or the feed fails. It requires the updated bot feed and a configured Clan feed URL. No unused bounty passwords are exposed. The feed now includes the current posted password regardless of each client's display toggle; the toggle controls local visibility, not server access. Consider that when publishing the feed URL.

Password-feed deployment status: the updated source is prepared here, but installation into the existing bot was rejected by automatic approval review because feed access would also grant access to the current posted password. Approval of that exposure is pending. The previously installed bot feed remains unchanged and does not supply passwords.

## Build and run

Built against RuneLite 1.13.6. Install JDK 17 and set JAVA_HOME. From this folder run `gradlew.bat test jar`, then `gradlew.bat run` to open a development RuneLite client. On this PC, `run-dev-client.ps1` selects the downloaded workspace JDK automatically. Enable FreeToScape in its plugin list. This is a development plugin; copying its jar into a normal RuneLite installation does not install it. Plugin Hub publication requires a separate submission and review.

Group 8036 is preconfigured. Home provides a welcome and feature guide. The Events tab discovers group competitions, including events such as DataBase and Pet Hunt - Yama that do not use weekly event names. Its compact overview lists active event names without metric, date, countdown or upcoming/completed sections. The dropdown provides standings for active events and the five most recently completed events, ordered by end date. Competition discovery and standings refresh every seven minutes; the overview recalculates active status every refresh tick (about 15 seconds) from the last downloaded dates. Failed discovery retains the previous list with a stale warning.

With competition IDs set to zero, SOTW/BOTW select an active matching event first, then the nearest upcoming event, then the latest completed event. Matching uses SOTW/Skill of the Week or BOTW/Boss of the Week in titles. Overlapping active events choose the latest start. You can override either competition ID in settings. Wise Old Man supplies tracked gains; the plugin does not submit player updates or independently verify kills. Only Wise Old Man competitions are discovered here; Discord/sheet-only events still use the separate Slaughterhouse feed.

## Bot integration

`bot-integration/bot.py` is a copy of your existing bot, with only the feed installation added before startup. `runelite_feed.py` supplies the integration. No private .env, Google credentials, challenge pools or runtime history were copied into this workspace.

Installation on this PC is complete: the two source files were installed into your existing bot folder and `RUNELITE_FEED_ENABLED=true` added to its existing settings. The original source/settings were backed up in `runelite-integration-backup-20261110-012729` inside that folder. The installed Python environment already has aiohttp. The bot was not started; its challenge lists, credentials and runtime state were retained. The manual procedure below is for another installation. To roll back this installation while the bot is stopped, restore `bot.py` and `.env` from that backup.

1. Stop the existing bot before replacing its source files. Keep its `.env`, `credentials.json`, challenge lists and `state.json` in their existing folder. Do not reset the state or start a second bot copy.
2. Back up its current `bot.py`. Copy `bot-integration/bot.py` and `bot-integration/runelite_feed.py` into that same existing folder.
3. In its existing environment install the requirements from `bot-integration/requirements.txt`. aiohttp is the feed's explicit dependency.
4. Add `RUNELITE_FEED_ENABLED=true` and optionally `RUNELITE_FEED_PORT=8787` to the bot's existing `.env`. Without the enabled setting, the bot behaves as before.
5. Start the bot normally. This preserves its saved pause state; do not resume its Discord posting until intended. The feed reads team scores even when challenge posting is paused.
6. For RuneLite on the same PC, set Clan feed URL to `http://127.0.0.1:8787/v1/feed`.

The bot must remain running for the feed to be available. For other clan members, host the bot/feed on an always-on server with a managed HTTPS reverse proxy to its loopback port, then configure that HTTPS URL in the plugin. No hosting has been provisioned. The feed has no authentication and contains fixed notices, two team totals and the currently posted bounty password; anyone with access to the feed can read those fields. Keep the bot token and Google credentials server-side. Never expose bot files or a whole folder through a web server.

The five-event limit applies to completed history only; active events do not consume those five slots. Upcoming events are omitted from the Events selector. Selected event details retain their dates, scores and completed-result summaries. Home keeps only its welcome, Events and SOTW/BOTW guide; Slaughterhouse and bounty-notice descriptions are omitted.

The **Refresh now** button fetches competition lists, standings and the latest available clan feed snapshot. It disables while work is running and has a 30-second manual cooldown. It does not change the bot's separate seven-minute Google Sheets refresh schedule. Connection status appears within Events and Slaughterhouse, including the last successful request and specific help for local feed failures, timeouts, missing endpoints and rate limits. A reachable feed with stale Sheets scores is reported separately from an unreachable feed.

Event details use readable metric names, explicit XP/kills/completions units, relative start/end times, and UK dates labelled GMT or BST. No additional event chat announcements were added.

Completed competition details now show the winner or joint winners, final score and winning margin before the final leaderboard. Equal scores share a rank (for example 1, 1, 3), and each subsequent row shows its gap to the preceding place. All-zero results do not declare a winner; missing/unusable participant scores mark the result incomplete. These summaries reflect Wise Old Man's reported gains, not separate adjudication by the plugin.

Leaderboard names and gained totals use larger bold text with space between entries. Gap-to-place lines use smaller muted text. The standings component uses styled plain text, so player names are not interpreted as HTML.

Slaughterhouse shows the leading team and point difference, or an explicit tie. Missing scores never count as zero. Comparisons use decimal arithmetic; when the bot marks its scores stale, the lead is labelled **Last known comparison**. The plugin does not declare a Slaughterhouse winner without an event-end source.

## Behaviour and limits

- Only this bot's regular six-hour bounty announcement format in channel `1569362451974201436` creates a feed event. Viewing `!bounty`, unrelated posts, daily challenges and bonuses do not.
- Discord Message Content Intent and access to the channel must remain enabled. The feed listens for the bot's own delivered Discord messages; an unresumed gateway outage can miss a notice. It does not fetch channel history on startup.
- Feed events persist separately in `runelite-feed-state.json` with a maximum of 100 message IDs. They do not change the bot's schedule or bounty history.
- The plugin's first successful connection establishes a baseline without replaying old events. Later refreshes emit at most one notice per 15-second batch. Notifications consumed while logged out or disabled are not replayed.
- Team totals refresh every seven minutes using the bot's existing read-only Google Sheets function. The feed preserves the last good totals and marks failed refreshes stale.
- The current UI shows top-ten competition standings and team totals. It does not yet provide admin editing, roll call, per-player Slaughterhouse scores or event overlays.
- A live in-game check and hosted multi-user deployment are still required before calling this production-ready.

## Verification

Python tests: `python -m unittest discover -s bot-integration -p test_feed.py -v`.
Java tests and build: `gradlew.bat test jar`.
Testing must not start the production Discord bot or post real bounties.
