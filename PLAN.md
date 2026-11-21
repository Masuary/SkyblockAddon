# SkyblockAddon (Skyvault line): phase R plan for Update 4

Source of truth for the findings and sir's answers: `/mnt/data/Projects/skyvault-update4-2026-11-20/9-mod-review/findings/15-skyblockaddon.md` (B1-B14, F1-F7, cleanup stages, upstream comparison, answers 2026-11-21). The previous ItemGrid redesign plan was superseded by the ListView redesign (`efe1458`) and is in git history.

Branch `gui-redesign`, version stays 8.7 until the deploy (not shipped yet). No pushes. Wolds sibling (`SkyblockAddon-Wolds`) is read only: fixes are ported from it, never edited there.

## Decisions (sir, 2026-11-21)

- Keep our line; port the Wolds fixes (`118925f`, `1097a69`, `3b174ba`, `4a32988`, `76933b7` where still relevant) and these upstream (`YorickBM/SkyblockAddon` `Version-8.X`) additions, adapted to our permission checker: Easy Villagers / Easy Piglins mixins, Vault Portal / Nether Portal / Elevatorid mixins, console-usable admin commands. No upstream permission-engine change, no altar debounce.
- Every bug B1-B14 is fixed as recommended; all six cleanup stages run before the deploy; F1-F5 and F7 are built before the deploy, F6 as a read-only report.
- Independent review of the fix diff: Fable subagent (dupe/item-loss risk), given code and spec only.

## Phases

| Phase | Contents | Status |
|---|---|---|
| P1 Build and housekeeping | B10 + cleanup stages 1-2: `.gitattributes` and renormalise (own commit); root build with `core` from source; VH 7209, MasuGUI, Buildscape through `-P<name>_jar` overrides or CurseMaven, committed jars removed from the tree; `-Xlint` zero warnings; unused/wildcard imports, commented-out and dead code, typos, INFO debug lines to DEBUG; `jar-compare` vs the 8.7 jar shows only intended differences | open |
| P2 Core correctness + tests | Cleanup stage 6 (JUnit in core) first, then failing tests and fixes: B1 (Wolds membership + one overworld save path), B8 one-group invariant in `Island`, B11, B14, B9 (atomic + strict loader/saver), B5 (fail fast for permission registry, language and GUI files) | open |
| P3 Island creation | B4 (Wolds 9.2 pipeline: parse on the worker, reserve/register/place/teleport on the server thread, refund on failure, timing log) + F2 queue with position message and action bar; B2 code guard + F1 (`getNextIslandGen` skips occupied and reserved slots, `island.reserved_slots` config default 0,0, `/island admin reserve`, `island.create.enabled` switch) | open |
| P4 Protection | B3 side-effect-free interactability (capabilities, menu provider, cached `use` override check, config list; keep the 8.5 sneak rule), B6 drop the POWERED toggle and resend blocks, cleanup stages 3-4 (constants, `deny` helper, precompiled patterns, table-driven `PermissionEvents`), upstream mixins: Easy Villagers/Piglins, Vault/Nether portal, Elevatorid (with trigger entries in the bundled permission defaults) | open |
| P5 Chunks, purge, admin | B7 + F7 (port Wolds `3b174ba`: overworld only, block-event chunk tracking, `cleanchunks`), B12 (purge completion on the server thread + abandonment recheck), B13, console-usable admin commands, F6 abandoned-island report | open |
| P6 Actions and GUI features | Cleanup stage 5 (one island-action service for both GUI stacks and commands) with B8 gating (owner / `admin_menu` / op), F3 expel + per-island ban list, F4 ownership transfer, F5 travel list polish; visual check in both GUI stacks; coordinate with the MasuGUI visual overhaul (phase R decision, lands before the deploy) | open |
| P7 Review and testnet | Fable review of the full diff; testnet (lock `6-test-network/LOCK`): protection checklist, restarts with custom-group members, two-client create rush, vault biome, purge, B2 carried spawn island on a fresh TestA world; record the B2 deploy step (copy the live spawn island file `a753cd61-...` with its chunk list cleared into new MAIN `world/islanddata/` before players join) for the deploy runbook; tracker row in `9-mod-review/README.md` | open |

Each phase: failing test first where testable, `gradlew build` and tests green with zero warnings, commits on `gui-redesign`.
