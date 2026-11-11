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

Automatic rank sync excludes **Warden and every higher in-game rank**. The reporter resolves Warden's position from the clan's configured titles and withholds the rank for members at or above that position; their approved presence tracking continues. A withheld rank cannot trigger Discord role additions or removals. If Warden's position cannot be resolved, all automatic rank reporting is withheld until it can be resolved. Other Discord roles remain under manual control.

The companion bot retains activity for seven days and account links until an officer unlinks them. Discord IDs remain on the bot and are not needed by the game plugin. Server access/logging/backups and retention are the operator's responsibility. This optional feature processes account identifiers; it must not be described as storing no personal data across the whole system.

## Officer setup

Once the plugin is installed in normal RuneLite, officers can open **Clan activity → Officer setup**, enter the clan-sync server base URL and paste the dedicated reporter credential into the masked field. Choose **Use for this session**, then separately enable **Officer clan activity reporting** in FreeToScape settings. This does not require the development/officer launcher. The plugin still needs Plugin Hub acceptance before normal-client installation is available.

The receiver is `http://127.0.0.1:8788` when the bot runs on the same PC. For another PC, the operator must supply an HTTPS-hosted receiver. Hosting has not been provisioned. The credential is the bot operator's dedicated `CLAN_SYNC_REPORTER_TOKEN`, not a Discord bot token, Google credential or Jagex password. It is 32–256 printable characters without spaces. The setup screen saves only the server address in RuneLite settings.

The credential stays in process memory, bound to the selected receiver, until the plugin closes or **Forget session credential** is used. Re-enter it after restarting; there is no persistent credential store in this first version. Changing receiver requires its credential again. Replacing or forgetting it cancels pending reporting work. The legacy launcher/environment variable remains an optional fallback, imported once and bound to the configured receiver; forgetting the credential suppresses that fallback for the rest of the process.

The Clan activity tab shows the receiver address, credential readiness and reporting status. Activity is sent to that bot, which stores it in `clan_activity.sqlite3`; it is not automatically posted to Discord. Officers can use `!clanactivity DISCORD_USER_ID` and `!clanrankpreview` in the bot's private bot-testing channel. Keep reporting disabled on ordinary members' clients. Never put credentials in feed URLs, saved RuneLite settings, GitHub or chat.

## Development

Install JDK 17 and run:

```powershell
.\gradlew.bat test jar
.\gradlew.bat run
```

The development launcher enables Java assertions. Source targets Java 11. The Plugin Hub uses `build=standard`; it replaces the development build files with its own build setup. Runtime dependencies come from RuneLite; the bot is not bundled in the plugin jar.

## Release status

Source is public; the plugin has not yet been accepted or published through Plugin Hub. Publication requires a pinned commit, the official Hub CI checks and maintainer review. Live checks verified events, team scores, synthetic chat notices/code display and one actual linked account's presence/rank upload. The new masked setup flow still needs an interactive check after restart. The currently installed clan feed does not yet supply passwords. A mapped non-staff rank preview and actual Discord-to-feed bounty delivery remain separate checks. Live Discord role assignment remains disabled and is a separate operator decision.

This plugin is unofficial and is not provided, supported or moderated by Jagex. Other installed plugins and external services have their own data practices.

## Licence

BSD 2-Clause; see [LICENSE](LICENSE). A copy is included in the plugin jar.
