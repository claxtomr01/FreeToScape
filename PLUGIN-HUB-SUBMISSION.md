# Plugin Hub submission preparation — 11 November 2026

Update, 12 November 2026: source and supporting project files are now published at https://github.com/claxtomr01/FreeToScape. Use the latest actual commit when preparing the Hub marker. The current build has 40 passing Java tests, including session credential isolation, and passes the isolated standard build. Live event/notice/code-display and one linked Owner observation are documented in LIVE-TEST-2026-11-11.md. Owner is excluded from rank sync; the new setup dialog still needs an interactive check. No Plugin Hub pull request or acceptance is claimed. The earlier preparation snapshot below is retained as history.

## Local changes

- Both HTTP JSON readers use RuneLite's injected Gson instance, avoiding the Hub's forbidden Gson constructors.
- `runelite-plugin.properties` declares `build=standard`.
- BSD 2-Clause licensing is provided in root `LICENSE` and in the jar at `META-INF/LICENSE`.
- `PLUGIN-HUB-README.md` is the public README used by the source export. It documents external connections, optional activity uploads, retention, credentials and feature limitations.
- The source export uses an explicit allowlist. It excludes bot settings, private account links, activity databases, local launchers, runtime state, tools, compiled jars and machine-specific handoff documents.

## Validation

The project build (`clean test jar`) passed all 31 Java tests. The exact exported source also passed `clean test jar`. An isolated copy compiled successfully using RuneLite's standard-build template, with the client dependency resolved to 1.13.6, including its licence resource. The exported jar contains 15 plugin classes, its embedded licence matches the root licence, and a direct bytecode scan found no references to the checked forbidden Gson/OkHttp constructors, legacy widget APIs, account/session APIs or obsolete item-stats overload. The 34-entry ZIP contains no private/runtime paths. This is a local compatibility check, not the complete official Plugin Hub API recorder, CI or review.

The live test remains paused at the owner's request. The previous session confirmed that the plugin launches, Wise Old Man standings load, Slaughterhouse connects and its zero team scores match the configured Sheet cells. The authenticated clan receiver has no approved account links and live role assignment is disabled. Logged-in chat delivery, password-feed deployment and actual rank-to-role dry runs remain unverified.

## Submission steps

1. Create a public GitHub repository under **claxtomr01**, for example `freetoscape`. The repository name is a suggestion; no repository has been created or verified by this preparation.
2. Run `powershell -ExecutionPolicy Bypass -File .\prepare-plugin-hub.ps1`. Extract the generated ZIP and upload its **contents**, with `src`, `LICENSE` and `runelite-plugin.properties` at the repository root. Do not upload only the ZIP or jar, or any generated `build`/`.gradle` folders from testing.
3. Commit/push the exported source. Resolve the paused live checks before presenting this as a production-tested release.
4. Fork [runelite/plugin-hub](https://github.com/runelite/plugin-hub). Add `plugins/freetoscape` containing `repository=https://github.com/claxtomr01/REPOSITORY.git` and `commit=FULL_40_CHARACTER_COMMIT_HASH`, replacing the placeholders with the actual public repository and commit.
5. In that same marker, propose a warning describing the external services and optional uploads, for example: `warning=Connects to Wise Old Man and a configured clan feed. Optional officer reporting uploads linked RuneScape names, clan ranks and observed clan-channel presence to a configured bot; activity is retained for seven days.` The Hub maintainers may revise this wording.
6. Open the Plugin Hub pull request, describe the optional uploader and feed/password limitations explicitly, and resolve official CI and maintainer feedback. Do not claim approval before acceptance.

No GitHub password or token is required in project files. There is no GitHub CLI/connector available in this session, so publishing has not been performed.

## Official references

- [Contribution, licensing and build instructions](https://github.com/runelite/plugin-hub/blob/master/README.md)
- [Disallowed APIs](https://github.com/runelite/plugin-hub-tooling/blob/master/package/src/main/resources/net/runelite/pluginhub/packager/disallowed-apis.txt)
- [Standard build template](https://github.com/runelite/plugin-hub-tooling/blob/master/package/src/main/resources/net/runelite/pluginhub/packager/standard-build.gradle)
