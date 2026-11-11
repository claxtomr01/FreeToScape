# FreeToScape

A RuneLite sidebar for FreeToScape clan events, Wise Old Man standings and Slaughterhouse team scores.

- Events: active competitions and the five most recently completed competitions.
- SOTW: Skill of the Week XP standings.
- BOTW: Boss of the Week kill-count standings.
- Slaughterhouse: team totals from a separately operated clan feed.
- Bounty notices: a local game-chat message saying `[Clan Broadcast] A new bounty has been posted!`, with the `[Clan Broadcast]` prefix in red.
- Optional bounty-password overlay, off by default, when the configured feed supplies the current event code.
- Optional officer activity reporter, off by default, for approved account links and Discord rank-sync dry runs.

## Setup

Wise Old Man group **8036** is preconfigured. Competition IDs of `0` select named SOTW/BOTW competitions automatically, preferring active, then upcoming, then completed competitions. Standings refresh every seven minutes; the refresh button has a 30-second cooldown.

The optional clan feed must provide `/v1/feed` over HTTPS. Loopback HTTP is supported for a feed on the same PC, for example `http://127.0.0.1:8787/v1/feed`. A localhost address does not let other clan members access the owner's bot. Feed hosting, access controls and the Discord bot are separate from this plugin.

Bounty broadcasts appear only in the local client's chat box. They do not send messages to other players. Existing notices are not replayed on startup. The password overlay requires a compatible feed; it does not retrieve Discord messages itself. The operator must approve who can read event codes before exposing that feed.

## Privacy

The plugin contacts public Wise Old Man endpoints and the configured feed. These services see the connecting IP address. Public leaderboard names, scores, notice IDs and optional event codes are held in memory. HTTP disk caching, cookies, redirects and inherited HTTP authentication are disabled. RuneLite saves plugin configuration. The plugin has no analytics and does not write player data to files.

Officer reporting is **off by default**. Enabling it sends only officer-linked RuneScape names, clan ranks and observed presence in the reporter's own clan channel to the configured authenticated receiver. Channel absence is not proof that a player is globally offline. Worlds, locations, inventory, game chat and game-login credentials are not reported.

The companion bot retains activity for seven days and account links until an officer unlinks them. Discord IDs remain on the bot and are not needed by the game plugin. Server access/logging/backups and retention are the operator's responsibility. This optional feature processes account identifiers; it must not be described as storing no personal data across the whole system.

The reporter uses a dedicated `CLAN_SYNC_REPORTER_TOKEN` environment variable of at least 32 characters, supplied to the RuneLite process by the authorised operator, plus the configured clan-sync base URL. Never put the bot token, Google credentials or reporter token in this repository or in a feed URL. The ordinary client does not inherit credentials from a development launcher. Use only a trusted receiver and keep reporting disabled on ordinary members' clients.

## Development

Install JDK 17 and run:

```powershell
.\gradlew.bat test jar
.\gradlew.bat run
```

The development launcher enables Java assertions. Source targets Java 11. The Plugin Hub uses `build=standard`; it replaces the development build files with its own build setup. Runtime dependencies come from RuneLite; the bot is not bundled in the plugin jar.

## Release status

Prepared for Plugin Hub submission; not yet submitted, approved or published. Publication requires a public source repository, a pinned commit, the official Hub CI checks and maintainer review. Logged-in broadcasts, password delivery and actual linked-account rank reporting still need the paused live test to be completed. The currently installed clan feed does not yet supply passwords. Live Discord role assignment remains a separate operator decision.

This plugin is unofficial and is not provided, supported or moderated by Jagex. Other installed plugins and external services have their own data practices.

## Licence

BSD 2-Clause; see [LICENSE](LICENSE). A copy is included in the plugin jar.
