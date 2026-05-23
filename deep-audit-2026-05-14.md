# Deep Audit Snapshot - AdminPermissionCommand

**Scope:** the change you just did (AdminPermissionCommand + ModEvents registration)
**Date:** 2026-05-14
**Git SHA:** c223c47
**Verdict:** 🟡 YELLOW

Snapshot from one audit pass. Findings reflect the code as of the SHA above; line numbers and severities will drift as fixes land. Do not treat this as a living spec - re-run `/deep-audit` for a current view.

## HIGH

- [ ] [1.18.2/src/main/java/yorickbm/skyblockaddon/commands/op/AdminPermissionCommand.java:77, 119, 126, 132] Case-sensitivity mismatch between validation and storage. Validation uses `equalsIgnoreCase` but `setPermission` stores the caller's exact casing into the group's permission map, which is keyed by canonical lowercase IDs from `Permission.getId()`. Runtime `canDo` does strict `containsKey` lookups against canonical keys, so a wrong-case input silently no-ops while writing an orphan entry to NBT.
  - why: `IslandGroup.java:17` seeds the map with canonical IDs; `IslandGroup.java:35` reads with strict `containsKey`; an orphan key inserted by `setPermission("INTERACT_...", v)` is never read by event handlers that use the canonical `interact_...`. Admin sees green success that did nothing. Fix: resolve to canonical ID once (`getPermissions().stream().filter(p -> p.getId().equalsIgnoreCase(input)).findFirst().map(Permission::getId)`) and use that canonical string for every `setPermission` call.

## MEDIUM

- [ ] [AdminPermissionCommand.java:140-148] `persistAllIslands` silently does nothing if the world capability is absent, yet line 107 still reports green success. Emit a failure when the capability isn't present.
- [ ] [AdminPermissionCommand.java:146-147] Synchronous NBT save of the full island collection on the server thread. On servers with many islands this freezes the tick. Either offload to a worker thread or document the cost.

## Recommended next actions
1. Fix the case-sensitivity bug: canonicalize `permissionId` once after validation, use that everywhere.
2. Make `persistAllIslands` fail loudly when the world capability is absent.
3. Optionally: replace hardcoded English with `SkyBlockAddonLanguage` keys.
4. Optionally: `filter(instanceof ForgeIsland)` before the cast in `persistAllIslands`.
