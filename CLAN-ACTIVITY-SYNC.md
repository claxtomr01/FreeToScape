# Proposed clan activity and Discord rank sync

Status: the optional reporter and seven-day activity service are implemented and tested. The bot extension is installed in dry-run mode, pending a bot restart; the plugin reporting switch is off. No links or member observations have been created and no Discord roles or permissions have been changed. See CLAN-SYNC-SETUP.md for activation and prerequisites.

## Confirmed user choices

- Retain observed online activity for seven days, with automatic expiry; this new feature therefore requires a revised privacy disclosure and data storage.
- Only track accounts with officer-approved RuneScape-to-Discord links.
- Use Discord channel `1472292257611911372` as the rank/linking reference. The user described it as a channel, so it is not being treated as the Discord server ID.
- Reference-channel access succeeded on retry. The channel belongs to server `1236133761859584010`. Its published rank explanations and existing role IDs are captured in clan_sync_roles.json.
- Cleric and PvP are intentionally unmapped at the user's request; staff roles are excluded.
- A subsequent read-only check found Manage Roles missing, the bot's highest role at position 24, Maxxed at 72, and HTTP 403 for the private bot-testing channel. Those permission/hierarchy changes require the user's manual action; they were not made through the UI or API.

## Requested behaviour

An authorised FreeToScape reporter observes the in-game clan roster and channel. A bot associates approved RuneScape accounts with Discord accounts, shows observed online status and mirrors mapped in-game ranks to designated Discord clan-rank roles.

RuneLite 1.13.6 provides `Client.getClanSettings()`, clan roster membership/rank, `ClanSettings.titleForRank(...)` and the online clan channel membership. No game actions, packet inspection, passwords or combat information are needed. Clan-channel presence is an observation, not proof of a player's global online/offline status.

## Decisions required

1. Confirm the dry-run mappings against actual in-game title observations before enabling live role assignment.
2. Account linking is confirmed as officer-approved RuneScape-name/Discord-ID pairs. Obtain the reference information and define the approval workflow. Names must not be matched to Discord nicknames automatically.
3. Retention is confirmed as seven days of activity history with automatic expiry. Account-link mappings need separate persistence for rank sync and must be removable through an officer-approved unlink workflow.
4. Which authorised reporter PC/account will supply observations, and the HTTPS service that will receive them. A local bot can receive loopback reports during testing; continuous coverage requires an online reporter connected to the clan channel.

## Concrete initial design

- An explicitly enabled reporter reads only FreeToScape's own roster/channel on RuneLite's client thread. Reporting is disabled by default, with a disclosure of the fields and destination before activation.
- Only approved linked members are transmitted. Proposed fields: RuneScape display name, in-game rank value/title and presence observation. Do not send world numbers, locations, chat, inventory, account credentials or the unused challenge pool.
- Use a dedicated authenticated reporter credential over HTTPS. Do not embed a shared secret in the plugin jar or put credentials in a feed URL. Never trust an arbitrary member's self-reported rank as permission to assign roles.
- The bot checks the authorised reporter, clan, configured server, verified account links and allowed rank-role mapping. Rank titles alone do not establish trust.
- Present a dry-run change plan first. It should show the current mapped rank role and proposed mapped rank role for each linked account, with no writes to Discord during validation.
- On activation, add/remove only the configured cosmetic clan-rank roles. Leave unrelated roles unchanged. Fail closed for ambiguous links, unknown ranks, missing mappings or insufficient bot permissions. Do not grant Discord administrator/moderation privileges through rank sync.
- Apply updates only on actual rank changes, with bounded retries and Discord rate-limit handling. Never infer a demotion or clan removal merely because a member is offline or a snapshot is incomplete.
- Track `observed online`, `not observed in channel` and `unknown/stale` distinctly. Expire reporter observations after a short heartbeat timeout. A disconnected or logged-out reporter makes observations stale; it does not establish that clan members logged out.
- With current-status-only retention, keep observations in server memory and expire them; no attendance timeline or last-seen history. Persist only the explicitly approved account links and configuration. A 7-day history, if chosen, requires a clear retention/deletion policy and updated privacy disclosures.

## Discord requirements

The bot needs Manage Roles and must have its own highest role above every mapped clan-rank role. Server-wide Administrator is unnecessary. It also needs access to the private bot-testing command channel. No bot permissions have been changed during this investigation.

## Tests required before enabling

- Correct rank/title mapping; renamed RuneScape accounts; multiple accounts linked to one Discord member; duplicate/ambiguous links.
- Reject unauthenticated reports and attempts to sync into another clan/server or to assign unmapped/privileged roles.
- Offline member, missing channel, reporter shutdown, incomplete roster and expired heartbeat cannot revoke roles or produce false global offline claims.
- Repeated reports produce no duplicate role updates. Hierarchy/permission failures preserve unrelated roles and report a useful error.
- Retention and deletion behaviour match the chosen setting. Logs omit report credentials and avoid dumping rosters.

## Primary references checked

- [RuneLite ClanSettings](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/clan/ClanSettings.html)
- [RuneLite ClanChannel](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/clan/ClanChannel.html)
- [RuneLite ClanChannelMember](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/clan/ClanChannelMember.html)
- [Discord role permissions and hierarchy](https://github.com/discord/discord-api-docs/blob/main/developers/topics/permissions.mdx)
- [RuneLite external-server disclosure guidance](https://github.com/runelite/runelite/wiki/Information-about-the-Plugin-Hub)

RuneLite's current external-server guidance requires disclosure of transmitted data. This new feature also needs its own Plugin Hub review; the earlier read-only code review does not cover it.
