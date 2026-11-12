# FreeToScape — RuneLite clan plugin design

Status: first implementation built and unit-tested. Bot feed integration installed with backup; bot not started. Live in-game validation and HTTPS hosting remain outstanding. See README.md for implemented features and setup.

Latest UI update: Home is a generic welcome/feature guide. Events has a compact active-event-name overview; metric names, dates, countdowns, upcoming/results sections and the refresh-interval sentence have been removed from that overview. The selector contains all active events plus five most recently completed events by end date. Selected details retain full standings/results. Automatic SOTW/BOTW selection still prioritises active, then nearest upcoming, then latest completed. Discovery refreshes every seven minutes; the overview derives active status from cached dates between downloads. No event-start/end chat messages are added.

Approved QoL changes 1–3: manual refresh with a 30-second cooldown and queued-request suppression; separate Events/feed connection status and last successful request time; readable metric names, explicit score units, relative event timing and GMT/BST date labels. The feed's own Sheets polling interval is unchanged. Other suggested QoL changes remain unimplemented pending permission.

The temporary QoL trial was cancelled. The grouped overview from option 4 was subsequently simplified as described above. Options 6 and 7 remain removed, including their config controls, Appearance tab and dummy preview. Options 1–3 remain. Suggestion 5 (open selected event directly) remains unimplemented. No bot/password feed deployment is included.

Subsequent approved QoL suggestions 3 and 6: completed-event winner/tie/final-score summaries; shared ranks and gaps between leaderboard places; Slaughterhouse team lead/tie comparisons with exact decimal arithmetic and stale/missing-data handling. These refer to the later recommendation list, not the cancelled appearance trial.

## Purpose

Bring FreeToScape announcements, competitions and event information into the RuneLite sidebar. Inspired by Flux's feature categories, with original branding and configurable data sources.

Reference: https://github.com/abristow3/Flux-Runelite-Plugin (README reviewed 10 November 2026).

## Sidebar layout

Use RuneLite's dark panel styling, muted gold headings, teal accents and readable static podium highlights. Keep the layout usable at the normal narrow sidebar width. Show text alongside status colours.

```text
FreeToScape
Clan events & competitions
──────────────────────────
[Home] [SOTW] [BOTW]
[Slaughterhouse]

CLAN NOTICE
Announcements appear here

SKILL OF THE WEEK  •  Active
Skill name
Ends in 2d 04h
[View standings]

BOSS OF THE WEEK  •  Upcoming
Boss name · Starts 14 Nov

[Wise Old Man] [Discord]
Updated 3 minutes ago
                         [Refresh]
```

Illustrative event names, dates and standings must be clearly marked as demo data in any prototype. An unconfigured release shows setup guidance instead.

## Features

| Area | Proposed behaviour | Data needed |
| --- | --- | --- |
| Home | Announcements, upcoming/active events and useful links | Clan settings feed; Discord invite: https://discord.gg/ZBQZDtg2H |
| SOTW | Skill, countdown, XP gained, top ten and signed-in player's position; final winner after completion | Selected Wise Old Man competition |
| BOTW (Boss of the Week) | Boss, countdown and standings ranked by kill count only | Wise Old Man boss competition |
| Slaughterhouse | Display existing points and bounty announcements; retain existing scoring system | Existing Google Sheet and bot integration; schema and access to be confirmed |
| Overlay | Optional event code and UTC clock; user-adjustable colours and visibility | Active event metadata |
| Notifications | Optional event start, end and roll-call alerts; deduplicate alerts | Event identifiers and roll-call state |
| Admin | Manage notices, event references, team assignments and roll call | Authenticated administration service |

## First release

Deliver a read-only home panel and Wise Old Man competitions first. Default FreeToScape's group ID to `8036` (user-provided group: https://wiseoldman.net/groups/8036) and explicitly select the competitions to display. Keep the group ID configurable. Resolve group competitions into upcoming, active and completed sections, with the player's standing where available. Support XP and boss-kill metrics with clear units.

Live API verification confirmed group `8036` is FreeToScape and inspected competition `156838` as a boss kill-count event. Boss metric keys use names such as `king_black_dragon`, rather than a `_kills` suffix.

Integrate Slaughterhouse's existing sheet and bounty bot after confirming their interfaces. Add event overlays and roll call after the clan confirms their settings. Add administration last, once an authenticated service is available.

## Discord broadcasts

Confirmed server invite: https://discord.gg/ZBQZDtg2H. This replaces the previously supplied incorrect invite. The user can add a bot to the server; no bot has been added or configured yet.

Broadcast only a new regular bounty notification with the exact text `a new bounty has been posted`. Do not relay general announcements, bounty descriptions, passwords, deadlines or prefixes. These are local plugin messages; they do not send messages into shared OSRS clan chat.

Use a server-side Discord bot or extend the existing bot to publish a read-only HTTPS announcement feed. Keep bot credentials on the server. Restrict relay sources by server/channel IDs, and allow the existing bounty bot by its ID rather than excluding all bot messages. Confirm required Discord permissions and message-content intent during implementation.

Use stable message IDs to deduplicate broadcasts across refreshes and restarts. On first connection, initialise the cursor without replaying historical messages into chat. Retain recent announcements in the sidebar, bound message length and queue size, render plain text, and provide a broadcast toggle. Define reconnect catch-up and edit/delete handling before implementation. Use a separate, configurable announcement refresh interval from competition polling.

The invite provides a user-facing link; it does not grant API access. Confirmed announcements channel ID: `1569362451974201436` (store Discord snowflakes as strings). The existing bot is **F2S Bounty Bot**, and the user can modify it. Prefer extending this bot with the announcement feed when its source and hosting are available.

Slaughterhouse scoring sheet: https://docs.google.com/spreadsheets/d/1xNNRwzHp87BnufzNRDU_M_kpB_8FIroqPykUcGDSCsM/edit?usp=sharing

Sheet ID: `1xNNRwzHp87BnufzNRDU_M_kpB_8FIroqPykUcGDSCsM`. The user confirmed bounty channel ID `1569362451974201436`, the same ID previously supplied for announcements. Use this as the shared source unless later clarified. Tabs, columns and access permissions remain unverified. Do not change sheet sharing or request publication until the intended data exposure is agreed. An authenticated bot can expose only the required standings through the feed if the sheet is private.

## Data and refresh behaviour

- Read group and competition information from Wise Old Man. Confirm current API endpoints and response schemas during implementation.
- Proposed refresh interval: seven minutes, subject to the provider's current limits. Coalesce manual refresh requests and use backoff after errors.
- Run network work off the RuneLite client thread and Swing event dispatch thread. Apply Swing UI changes on its event dispatch thread.
- Preserve the last successful snapshot after a failed refresh. Display its timestamp and a stale-data message; never replace failed scores with zero.
- Distinguish loading, unconfigured, empty, unavailable, upcoming, active and completed states.
- Use UTC instants for event boundaries and UTC labels for event coordination. Treat time remaining as approximate when the local clock is inaccurate.
- Make every source configurable. Do not use Flux clan endpoints or identifiers.
- A published spreadsheet can provide a read-only score source if needed. Agree a fixed schema with event ID, player, team, score, unit and update time; validate it before displaying results.

## Suggested implementation

### Existing bot located

Originally found in the owner's separate local F2S-Bounty-Bot folder, linked to the previous chat **Set up F2S Bounty Bot**. Source and README were initially inspected read-only. Later integration and live-test changes are documented separately; private paths, credentials and runtime data are not part of this repository.

The bot uses Python, discord.py and the Google Sheets API with read-only access. Its `read_scores()` reads two configured team ranges; `score_text()` formats the totals. Reuse this score-reading path for Slaughterhouse. Existing scheduled and manual challenge posts can publish structured feed events after successful Discord delivery. Do not expose the full unused challenge pool or credentials through the feed.

Documented channels: bounty `1569362451974201436`, scoreboard `1569365067781242970`, daily `1569445535322996808`, bonus `1569445633696211035`. Daily/bonus relay is optional and has not been requested. The supplied announcement ID is also the bounty ID; retain the user's shared-channel instruction.

The previous chat last reported the bot paused and shut down; this is historical status, not a current process check. Preserve its posting state and schedules during integration. Implementation now installs an opt-in loopback HTTP feed with the existing bot; source and .env backups were saved under `runelite-integration-backup-20261110-012729`. No bot was started and no hosting was provisioned. Live connectivity remains to be verified when the bot starts.

Java RuneLite plugin with a Swing PluginPanel, a ClientToolbar navigation button and an optional OverlayPanel.

```text
FreeToScapePlugin       lifecycle, toolbar registration, scheduling
FreeToScapeConfig       group ID, selected events, links, display options
FreeToScapePanel        home and competition views
CompetitionService     refresh, state resolution, last-good snapshots
WiseOldManClient       API requests and response mapping
ClanFeedClient         optional announcements and custom scoring
FreeToScapeOverlay     optional event code and UTC clock
model/                 competition, participant, team and feed models
```

Stop scheduled tasks on shutdown and discard late results from an earlier plugin session. Render names and announcements as plain text. Open only validated HTTPS links through deliberate user actions. Expose user-readable errors without logging credentials.

## Administration and privacy

In-game rank may control which controls are visible, but cannot authorize remote writes. The server must authenticate administrators and authorize each change. Never embed a shared admin secret in the distributed plugin. Event codes displayed to all members are coordination labels, not authentication secrets.

Document outbound services and what requests include. Contacting Wise Old Man exposes the requesting network address to that service. Keep the first release read-only, with no chat uploads, Discord messages or automatic score submissions.

## Acceptance checks for implementation

- Validate real Wise Old Man responses and supported metrics with a configured FreeToScape competition.
- Check event boundaries, ties, player-name matching, empty standings and missing group membership.
- Verify timeouts and malformed responses preserve the previous standings and mark them stale.
- Verify UI responsiveness, narrow-sidebar layout, shutdown cleanup and safe refresh/re-enable behaviour.
- Keep unconfigured integrations visibly unconfigured; never imply custom points or team scores are live before they are connected.
- Build and launch in the RuneLite development client before claiming the plugin works. Plugin Hub distribution requires a separate submission and review.

## Details still needed

1. Wise Old Man group supplied: `8036`; verify its live details and select competitions during implementation.
2. Discord invite confirmed: https://discord.gg/ZBQZDtg2H. Announcements/bounty channel: `1569362451974201436` (user confirmed the same ID).
3. BOTW confirmed: kill count only.
4. Slaughterhouse sheet link supplied above. F2S Bounty Bot can be modified by the user. Inspect relevant tabs/columns and obtain bot source/hosting details to implement the integration.
5. Clan logo and preferred colours; proposed defaults are teal and gold.
