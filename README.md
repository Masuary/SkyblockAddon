# SkyblockAddon 10.0.0

SkyblockAddon provides multiplayer island generation, protection, travel, permissions, and administration for the MasuCraft Wold's Vaults server. Version 10.0.0 is based on YorickBM's 8.2 source and carries forward the local integrations that are not present upstream.

## Runtime requirements

- Minecraft 1.18.2
- Forge 40.3.11 or a compatible Forge 40.x build
- Java 17
- Vault Hunters 3 when Vault-specific permissions and mixins are used
- A void overworld
- Terralith must not be installed. SkyblockAddon aborts startup when Terralith is detected because it can replace void-world generation and corrupt the island world.

SkyblockAddon remains server-side compatible. MasuGUI 1.0.0 is optional and is not embedded in the SkyblockAddon JAR. Install MasuGUI on the server and client to use the enhanced interface. Players without MasuGUI continue to use Yorick's inventory GUI.

Optional integrations activate only when their target mod is installed. Direct hooks were compiled and source-verified against Vault Hunters `3.21.5.6573`, Effortless Building `2.40`, PneumaticCraft `3.6.4-45`, Buildscape `3.0.2-VH`, Ars Nouveau `2.9.0`, Elevator Mod `1.8.4`, and MasuGUI `1.0.0`. Reference compatibility also covers Wold's Vaults `0.31.1`, Companion Locker `3.21.0`, MobProcessor `1.0`, Regions Unexplored biomes, and the storage and technology mods represented in `registries/groups` and `registries/permissions`.

The complete reference-pack JAR and hash inventory is recorded in `audits/reference-mod-inventory-2026-07-04.md`. The actual production server directory must still be compared before deployment.

## Configuration migration

Back up the world and `config/skyblockaddon` before first startup. The loader also creates a one-time `config/skyblockaddon.pre-10.0-backup` snapshot before it writes migration changes.

The 10.0.0 loader accepts both legacy string-encoded lore and 8.2 structured lore. If `registries/PermissionRegistry.json` exists, its still-valid permission definitions override bundled definitions by ID while new permissions are added from the per-mod registry directory. Retired broad permissions are not kept active beside their granular replacements.

To create a reviewable new-format copy before startup, run:

```bash
tools/migrate_config.py /path/to/config/skyblockaddon /path/to/skyblockaddon-10.0-migrated
```

The converter never modifies the source directory and refuses to overwrite its output. It converts GUI and permission lore, replaces the monolithic registry with validated per-mod files, retains a `PermissionRegistry.pre-10.0.json` copy, and writes `migration-report.json` with every retained, retired, custom, added, split, and normalized permission.

On island load, permission state is migrated to schema version 3. Version 3 splits retired broad mod toggles into granular replacements, including Ars Nouveau, Industrial Foregoing, Sophisticated Storage, Occultism, RFTools, Integrated Dynamics add-ons, and the other per-mod permissions represented in `permission_migrations.json`. Before migration, affected island files are copied to `islanddata.pre-permission-v3-backup`. Membership inconsistencies that can be recovered are repaired and logged. Unreadable island files abort loading instead of silently removing an island from memory.

Customized language values are retained. Missing bundled language keys are merged into `language.json`, with the original saved once as `language.json.pre-10.0.bak`. Old category permission files are moved into `registries/permissions/legacy-category-backup` rather than deleted.

The supplied archive was converted without modifying it. The current schema-v3 output is `/home/masuary/Downloads/archive-2026-07-04T145717Z-skyblockaddon-10.0-migrated-v3`.

## Administration

- `/island admin reload` atomically reloads permission groups, permissions, and GUI files. A failed reload leaves the previous configuration active.
- `/island admin permission set <permission> members|visitors|all <true|false>` previews a bulk permission change.
- Append `confirm` to the bulk permission command to apply and persist it.
- `/island admin cleanchunks` validates and prunes empty modified-chunk records.

## Build

Run:

```bash
bash gradlew buildAll --warning-mode all
```

The release JAR is written to `1.18.2/build/libs/skyblockaddon-10.0.0.jar`.

## Rollback

Stop the server before rollback. Restore the previous SkyblockAddon JAR, the complete backed-up `config/skyblockaddon` directory, and the world `islanddata` directory together. Do not combine pre-migration island files with post-migration configuration.

Upstream documentation remains available in the [YorickBM SkyblockAddon wiki](https://github.com/YorickBM/SkyblockAddon/wiki).
