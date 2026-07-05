# SkyblockAddon Technical Persistence

## Project identity

- Release line: 10.0.0
- Platform: Minecraft 1.18.2, Forge 40.x, Java 17
- Source base: YorickBM Version-8.X commit `563ec4f`
- Integration branch: `integration/upstream-8.2`
- Modules: `core` for configuration and island rules, `1.18.2` for Forge and mod integrations

## Durable invariants

- `Island` owns membership and group mutations. Callers must not edit group membership directly.
- Every non-owner member is present in the canonical member set and exactly one island group.
- New permissions never become allowed only because the island owner is allowed.
- World, entity, teleport, island registration, and save iteration work runs on the server thread.
- Configuration reload is atomic. A failed reload leaves the last valid registries and GUIs active.
- Island NBT and migration writes use a temporary file, flush it, and replace the destination atomically when supported.
- Configuration and island-NBT replacement use the shared `AtomicFileMover`. Only `AtomicMoveNotSupportedException` permits a non-atomic fallback; other I/O failures propagate.
- Optional integrations must remain safe when their target mod is absent and their dependency classes must not be packaged in SkyblockAddon.

## Configuration and migration

Version 10 uses per-mod files under `registries/permissions` and `registries/groups`. Legacy `PermissionRegistry.json` entries override matching bundled permissions while valid custom IDs are retained. Retired broad permissions are migrated with the shared rules in:

`core/src/main/resources/assets/skyblockaddon/registries/permission_migrations.json`

The offline converter is `tools/migrate_config.py`. It never changes its source directory, refuses to overwrite an output directory, preserves the old registry as `PermissionRegistry.pre-10.0.json`, and writes `migration-report.json`.

Before in-process configuration migration, the loader creates `config/skyblockaddon.pre-10.0-backup`. Island permission state migrates to schema version 2 and backs up affected files under `islanddata.pre-permission-v2-backup`. Schema version 2 splits the legacy Ars Nouveau, Industrial Foregoing, and Sophisticated Storage permissions while copying their stored values into every replacement.

Both legacy JSON-encoded lore strings and structured component lore are accepted. New bundled resources use structured lore.

## GUI paths

Yorick's inventory GUI is always the fallback. MasuGUI 1.0.0 is an optional enhanced path selected only for tracked compatible clients. Both paths call shared SkyblockAddon administration services for authorization and state mutation. MasuGUI itself is not modified or embedded.

## Optional integration boundaries

Terralith is not an optional integration. It is incompatible with SkyblockAddon because its world generation can replace the required void overworld and corrupt the island world. The Forge lifecycle check must throw `TerralithFoundException` before the dedicated server opens a world. Do not weaken this to a warning.

Direct optional hooks are guarded by resource-only target detection or Forge event registration. Mixin discovery must never use `Class.forName`, even with initialization disabled, because defining an optional target can load Minecraft supertypes before other mods prepare their mixins. Ars Nouveau Warp Portal protection filters `PortalTile.tick()` because that mod teleports players from a tile entity query rather than a normal block-use or portal-block callback. Compile-only dependencies are build inputs only. The final artifact must not contain dependency package trees.

The reference client-pack inventory and hashes are in `audits/reference-mod-inventory-2026-07-04.md`. It is not a substitute for inventorying the production server.

## Build and verification

Run:

```bash
bash gradlew cleanAll
bash gradlew buildAll --warning-mode all
python -m unittest tools/test_migrate_config.py
```

The Forge tests use Java 17. `buildAll` includes core and Forge tests. The expected artifact is `1.18.2/build/libs/skyblockaddon-10.0.0.jar`.

The current suite contains 58 Java tests plus the Python migration-converter test. It includes atomic-move fallback policy, optional mixin selection without target definition, modified-chunk persistence, and empty-chunk classification.

The known build warning is external to this repository: ForgeGradle 6.0.54 uses Gradle's deprecated `ResolvedConfiguration.getFirstLevelModuleDependencies(Spec)` API. Recheck it when ForgeGradle or Gradle changes.

## Production release boundary

Static verification is not production approval. Release still requires:

- the exact production `mods` inventory and hashes;
- copied live `world/islanddata`, not the bundled island structure template;
- two migration/start/clean-shutdown cycles on staging with before/after data counts;
- owner, member, custom-group, visitor, OP, automation, MasuGUI, fallback GUI, portal, vehicle, FakePlayer, and indirect placement tests;
- removal of duplicate Wold's Vaults and MasuTab JAR versions from the deployed pack.
