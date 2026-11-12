# FreeToScape privacy and client-rules review

Officer setup update, 11 November 2026: a masked, session-only credential dialog now supports ordinary-client setup without a special launcher. The server address is saved; the credential is not a ConfigItem and is not written to files or synced by this plugin. It is bound to its entered receiver, its stored character array is overwritten on replacement/forget/shutdown, and cancellation/generation checks stop obsolete reporting work. Network libraries necessarily use temporary credential/header strings in process memory. Persistent secure storage is not implemented. Activity goes to the displayed authenticated bot receiver, not directly to a Discord activity channel.

Release preparation update, 11 November 2026: BSD 2-Clause licensing and standard-build metadata are now present, and JSON requests use injected Gson. All 31 Java tests and a separate standard-template compilation pass. The public source README describes the optional uploader separately. This supersedes the older licensing/build/test-count notes below. A public committed revision, official Hub CI/maintainer review and completion of the paused live checks are still outstanding; no formal approval is claimed.

Reviewed 10 November 2026. Scope: the custom Java plugin under `src/main`, its development launcher and the separately prepared bot feed integration. This review is not Jagex approval, RuneLite Plugin Hub approval or a guarantee against account action. Other installed RuneLite plugins, RuneLite's general data handling and hosting providers are outside the custom plugin's control.

Subsequent scope change: the user requested officer-approved account links, seven-day clan activity history and Discord rank sync. The optional reporter is off by default and the bot is configured for dry-run role planning. When enabled, it sends linked RuneScape names, clan ranks and observed channel presence to an authenticated bot receiver. The bot stores account links until unlink and activity for seven days (purged while running and at startup/read/update). It sends no worlds, locations, inventory or game chat. The earlier no-player-upload description below applies to the original read-only functionality, not to this explicitly enabled feature. This addition needs a fresh Plugin Hub assessment before release; no formal approval is claimed.

## Rules assessment

No prohibited gameplay feature was identified in the reviewed plugin. Its features display externally supplied clan competitions, standings, team totals, a fixed local notice and an optional event-code overlay. This is a code-review assessment against the sources below, rather than a formal compliance certification.

| Rule area | Reviewed implementation |
| --- | --- |
| Gameplay automation | No generated clicks, key presses, movement, combat actions, prayer switches, inventory actions or server chat submissions. |
| Combat/PvP assistance | No combat predictions, mechanic timers, target scouting, NPC focus indicators or automatic position guidance. |
| Game communication | No packet interception, packet modification, login bypass or direct game-world requests. |
| UI changes | Own sidebar and overlay only; no combat-interface click-zone changes or hidden game controls exposed. |
| Clan notice | Fixed local text added through RuneLite's chat API, not transmitted to other players. No arbitrary Discord chat relay. |
| Password overlay | Displays a clan event coordination code, not an account credential or a game mechanic prediction. Off by default. Updated feed deployment is pending separate permission. |
| External services | GET requests to Wise Old Man and a user-configured feed. Links open only when the user clicks them. |
| Unofficial content | Privacy tab identifies the plugin as unofficial and not provided, supported or moderated by Jagex. |

Jagex's guidelines are non-exhaustive and may change. Their rules also cover how clan events themselves are run; this plugin cannot validate event conduct, rewards or content hosted elsewhere. Normal use of approved RuneLite does not confer approval on this unreleased custom plugin. The project has not been submitted to Plugin Hub. A public source repository, suitable licensing, the Hub build checks and maintainer review remain release work.

## What the plugin processes

| Data | Purpose | Persistence controlled by this plugin |
| --- | --- | --- |
| Public player display names and competition gains | Leaderboard text | Temporary memory/UI only; no files or database. |
| Public competition metadata | Event discovery and standings | Temporary memory/UI only. |
| Team totals and timestamps | Slaughterhouse panel | Temporary memory/UI only. |
| Discord post IDs | Suppress repeated local notices | Bounded in-memory set; cleared on disable. No Discord author/user ID is requested by the plugin. |
| Current posted event password, if supplied | Optional overlay | Temporary memory only; cleared on expiry, disable or feed error. |
| Group ID, competition overrides, feed URL and display preferences | Plugin configuration | Saved by RuneLite's configuration system; may follow RuneLite's profile/sync settings. Users must not put credentials or personal details into the URL or other settings. |
| Logged-in game state | Avoid chat notices while logged out | Read transiently. No local player's name, account identifier or login credential is read. |

The plugin has no analytics, tracking identifier, player telemetry, screenshots, chat-history collection, account-password access or player-data upload. It contains no direct filesystem writes. Ordinary requests send an IP address, requested resource path/group or competition ID and a User-Agent identifying the client/plugin. Remote operators and network intermediaries can observe or log requests; the plugin cannot enforce their retention policy. Opening Discord/Wise Old Man in a browser uses that browser's own cookies and account state.

## Privacy changes made

- Explicitly disable inherited HTTP disk caching and send `Cache-Control: no-store`.
- Use no HTTP cookies and no inherited HTTP authentication handler. Disable redirects, so a configured feed cannot silently move a request to another destination.
- Require HTTPS for remote feeds; permit loopback HTTP for same-PC development. Reject URL user information, query strings and fragments to discourage credentials in stored feed URLs.
- Clear fetched competition data, notice IDs, event choices, leaderboard UI text and the password overlay when the plugin is disabled.
- Add an in-plugin Privacy tab documenting processing and the limits of the privacy claim.

Earlier plugin versions inherited RuneLite's shared HTTP cache. Existing entries in that shared cache have not been deleted; this change prevents further caching by this plugin. RuneLite itself can retain client logs, configuration and local chat messages. No claim is made that the entire RuneLite installation or operating system stores no personal information.

## Separate bot and hosting boundary

The existing Discord bot necessarily has a Discord token, Google service-account credentials and an owner-ID setting. Those are not shipped in the Java plugin. Its existing challenge/history files, backups and Discord/Google data remain separate from this plugin review. They have not been deleted or altered by this privacy work.

The installed feed persists a bounded list of Discord post IDs, not IP addresses or Discord author names, and disables its aiohttp access log. A future reverse proxy or hosting service may log IP addresses independently. The prepared password-feed version would expose the current posted event code to anyone who can access the feed; it remains uninstalled following the earlier automatic approval rejection. No approval of that exposure is inferred from this compliance request.

## Verification and release limits

Unit tests cover event selection and boundaries, the five-event limit, feed URL restrictions, removal of inherited caching/cookies/authentication/redirects, bounty filtering, deduplication, feed lifecycle and active-password expiry/filtering. After the privacy changes, Java compilation, all 11 Java tests and jar packaging passed. The seven Python feed tests passed during the preceding password-feature work; this review did not change the bot feed. In-game checks and RuneLite's official review are still needed before public release. Tests demonstrate particular behaviours; they do not establish legal compliance.

## Useful next changes

1. Prepare a Plugin Hub submission for maintainer review and straightforward clan installation.
2. Add a manual refresh button with a cooldown, plus a clear connection/last-success indicator on Events and Slaughterhouse.
3. Separate live and completed results visually, with clear XP/kill-count units for custom events.
4. Before clan-wide hosting, decide who can read the event password and configure appropriate feed access and server-log retention.
5. Add a supported metrics source so newly released bosses do not require editing the BOTW allow-list.

## Primary sources checked

- [Jagex Third Party Client Guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
- [Jagex game rules](https://legal.jagex.com/docs/rules/rules-of-runescape)
- [Jagex Terms and Conditions](https://legal.jagex.com/docs/terms/terms-and-conditions)
- [RuneLite Plugin Hub contribution and review requirements](https://github.com/runelite/plugin-hub/blob/master/README.md)
- [RuneLite rejected/rolled-back features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- [RuneLite 1.13.6 HTTP-client implementation](https://github.com/runelite/runelite/blob/runelite-parent-1.13.6/runelite-client/src/main/java/net/runelite/client/RuneLite.java)
