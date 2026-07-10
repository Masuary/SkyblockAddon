# Changelog

## 10.0.0 - Unreleased

Last updated: 2026-07-10

Version 10.0.0 rebuilds the MasuCraft edition on YorickBM's 8.2 architecture, includes official `Version-8.X` changes through commit `eafb115`, and preserves the behavior from the previous local 9.10 branch. The major version changes because configuration layout, permission IDs, persistence rules, optional integrations, and migration behavior changed together.

Source implementation is complete. Dedicated-server startup was verified before the schema-v3 permission split update. Production approval still requires a refreshed staging startup, the complete interaction matrix, and migration comparison against copied production data.

### Added

- Added per-mod permission and group registries with deterministic loading, duplicate detection, regex validation, group-reference validation, and cycle detection. This makes optional-mod configuration reviewable and prevents malformed rules from failing during player interaction.
- Added permission schema version 3 and idempotent state migration. Split permissions inherit the previous specific value instead of receiving an unsafe broad default.
- Added `tools/migrate_config.py` for offline conversion of legacy configuration. It preserves the source, creates a reviewable output, retains a legacy registry backup, converts lore, and emits a machine-readable report.
- Added one-time full configuration backup and per-island permission-state backup before in-process migration.
- Added the bulk admin permission command with preview, confirmation, target selection, rollback, and result counts.
- Added shared island administration services so MasuGUI and Yorick's fallback inventory GUI use the same authorization and mutation logic.
- Added fallback confirmation for changing island spawn.
- Added optional integrations for Effortless Building batch and normal placement, PneumaticCraft pickup and protection paths, Buildscape, Vault Hunters Remastered, Regions Unexplored, and the custom-mod permissions represented by the reference pack.
- Added dedicated permission coverage for MobProcessor and corrected Wold's Vaults registry entries.
- Added granular Industrial Foregoing, Sophisticated Storage, Occultism, RFTools, Integrated Dynamics add-on, Ars Nouveau, and other post-8.2 permissions from current upstream, with migration from the previous broad toggles.
- Added separate Ars Nouveau machine and Warp Portal permissions. This makes Yorick's new portal mixin effective while retaining the legacy Ars permission value for both replacement controls.
- Added automated coverage for membership integrity, snapshot copying, NBT round trips, permission loading and migration, biome filtering, GUI resource loading, configuration backup, trigger completeness, and migration conversion.
- Added regression coverage for atomic-file fallback behavior, modified-chunk lifecycle and empty-chunk classification, and optional mixin selection.

### Changed

- Rebased the implementation on YorickBM 8.2 instead of directly merging the divergent local branch. This retains upstream's directory-based registries and newer GUI architecture without replaying incompatible history.
- Kept MasuGUI 1.0.0 optional and external. Clients without MasuGUI continue to use Yorick's inventory GUI.
- Left MasuGUI itself unchanged. All enhanced screens and compatibility behavior are implemented inside SkyblockAddon.
- Moved canonical member and group mutation into `Island`. Commands and GUIs no longer edit group membership independently.
- Made permission and GUI reload transactional. Invalid replacement configuration leaves the previous valid state active.
- Moved island registration, world changes, entity changes, teleportation, save iteration, and chunk-sensitive work onto the server thread.
- Replaced unbounded or stale island lookups with bounded, invalidated caches.
- Reworked temporary confirmation callbacks to use timestamped, single-consumption entries without a background scheduler.
- Changed biome loading to filter configured IDs against the live biome registry. Optional biomes disappear safely when their mod is absent.
- Changed chunk-load processing to wait until the chunk is visible through the server chunk cache before tracking or biome reapplication.
- Changed NBT writing to flush the temporary file before atomic replacement and to fail loudly on unexpected I/O errors.
- Changed placement permission matching to use the held `BlockItem`, which is the block actually being placed.
- Centralized atomic replacement for configuration and island NBT files. Only filesystems that explicitly reject atomic moves use the non-atomic fallback.

### Fixed

- Restored the mandatory Terralith incompatibility guard. Startup now fails before a world is opened when Terralith is installed, preventing its terrain generation from replacing the required void world and corrupting island data.
- Fixed optional mixin detection loading target classes before all mixin configurations were prepared. Detection now checks class resources without defining targets, preventing KubeJS `MixinTargetAlreadyLoadedException` startup crashes.
- Fixed legacy `minecraft:Unknown` biome values producing invalid-resource warnings during chunk-load biome reapplication. They are normalized to the unset biome sentinel during NBT load.
- Fixed custom-group assignments removing players from canonical island membership.
- Fixed copy construction and NBT loading dropping or duplicating members and group assignments.
- Fixed malformed legacy membership by deterministic load-time repair without granting visitor permissions.
- Fixed concurrent island creation passing duplicate cooldown or location checks.
- Fixed creation failures leaking reserved locations or cooldown entries.
- Fixed Quark Pickarang inventory work running on a new off-thread executor per use.
- Fixed FakePlayer probes leaking mounts or temporary entities, mutating cached Forge FakePlayers, or leaving menus open.
- Fixed Vault Wardrobe shift-use bypass and retained Vault pedestal and Buildscape item-transfer protection.
- Fixed Effortless Building visitor, batch, boundary, resynchronization, held-block, and non-overworld bypasses without production debug spam.
- Fixed PneumaticCraft denial coverage while retaining the narrow Amadron drone exception.
- Fixed the Wold's Vaults `vault_salager` typo to the real `vault_salvager` registry ID and normalized old configuration during migration.
- Fixed stale Create, Mekanism, Mekanism Generators, and Vault registry IDs.
- Fixed GUI class and constructor failures being discovered only after a menu was opened.
- Fixed biome reapplication using an obsolete heightmap and unsafe chunk-load timing.
- Fixed island-border particle tracking key and concurrency errors.

### Removed

- Removed obsolete selectors, dead compatibility code, stale permission IDs, and per-use worker creation.
- Removed retired broad permission IDs after their values are migrated into their granular replacements.

### Previous 9.10 parity

All 36 commits after the common ancestor of the previous `wolds` branch are represented in 10.0.0. Some were ported directly, while others were replaced by stricter implementations.

| Previous commit(s) | Preserved behavior | Why it remains |
| --- | --- | --- |
| `16184b2` | Island spawn persistence | Spawn and center survive snapshots, NBT round trips, reloads, and restart. |
| `00aeedc`, `29b74a5`, `aa2d9ec`, `21a9fa9`, `a662dd7`, `7d1b863`, `de67b6b`, `c0cec8f`, `b8fdbf9`, `2c15425`, `2d8118c` | Complete optional MasuGUI flow, layout, icons, confirmations, leave undo message, and protected set-spawn | The enhanced GUI remains available without replacing the unmodded-client fallback or duplicating authorization logic. |
| `9be9d72`, `9aede63`, `bd010cd`, `b91de2d`, `2da9380` | Vault Hunters Remastered stations, pedestal protection, permission cleanup, Terralith fail-fast safety, and Wardrobe protection | These close direct interaction and item-transfer bypasses while protecting the required void world from incompatible terrain generation. |
| `b3119c8`, `c64d09f`, `dd2fe8e` | Sophisticated Storage icon and final safe default | The temporary default-enabled behavior remains reverted, matching 9.10. |
| `c1a1b20`, `24e7abb` | Regions Unexplored biomes, lore, admin parity, chunk reapply, and race fix | Biome selection remains persistent while absent optional biomes are filtered safely. |
| `cd67b63`, `c223c47` | PneumaticCraft protection and Amadron exception | Non-members remain blocked without breaking legitimate Amadron drones. |
| `7714c80` | Bulk admin permission command | The new implementation adds preview, confirmation, rollback, and result counts. |
| `f4254ea`, `76933b7` | FakePlayer exception handling, dismounting, and temporary-entity cleanup | Probe failures are logged and cleaned without killing Forge's cached FakePlayer. |
| `9a49f02` | Dynamic Mods permission category in MasuGUI | Categories now come from the active registry rather than a hardcoded list. |
| `8beeff0`, `de94247`, `4a32988`, `d4c93e7`, `a3e00a4` | Full Effortless Building protection | Normal and batch paths, visitors, held blocks, resync, carve-outs, dimensions, and logging behavior are retained. |
| `118925f` | Membership-loss, threading, and performance fixes | Canonical mutations, server-thread boundaries, bounded caches, and transactional creation supersede the original patch. |
| `3b174ba` | Modified-chunk tracking and `/cleanchunks` | Tracking and cleanup remain in the upstream-based implementation. |
| `1097a69` | Group assignment membership fix | Group reassignment cannot remove canonical island membership. |

### Phase 9 upstream synchronization

Phase 9 is complete and integrates the official commits added after the original 8.2 integration base:

- `2586419` adds granular Industrial Foregoing and Sophisticated Storage permissions and corrects the optional mod ID from `creeper_power` to `creeperpower`. Version 10 adds schema-v3 split mappings so existing island values remain unchanged.
- `35af3ff` adds an Ars Nouveau Warp Portal mixin. Version 10 also adds the missing matching portal permission; the upstream mixin alone would not claim `ars_nouveau:portal` through any existing `onEnterPortal` rule.
- `eafb115` adds Remastered and Wold's Vaults permission resources. Version 10 ports the missing IDs and retires local broad permissions that would otherwise shadow the new granular controls.

The Ars redirect target was verified against Ars Nouveau 2.9.0. Its optional mixin configuration is loaded only when `PortalTile` is available, and Ars Nouveau classes are not bundled in the SkyblockAddon JAR.

### Permission migration mappings

Permission state schema 3 preserves existing island access by copying each legacy value into every replacement:

| Legacy permission | Schema-v3 replacement |
| --- | --- |
| `mod_ars_nouveau` | `ars_nouveau_machines`, `ars_nouveau_portal` |
| `open_ID` | `idyn_logic`, `integratednbt_extractor`, `integrated_addons` |
| `mod_occultism` | `occultism_rituals`, `occultism_storage` |
| `mod_industrialforegoing` | `industrialforegoing_machines` |
| `open_sophstorage` | `sophisticatedstorage_storage`, `sophisticatedstorage_link` |

Explicitly stored replacement values win over migrated values. Retired IDs remain in NBT for rollback but are not loaded as active permission definitions.

### Verification status

- 60 Java tests and one migration-converter test pass.
- Clean `buildAll` succeeds and produces the 10.0.0 JAR without embedding optional dependency packages.
- The supplied legacy configuration converts to 95 groups and 112 permissions with no retired permission left active.
- The Phase 9 artifact contains 11 mixin configurations and no optional dependency packages.
- The previous final JAR started with the full Backend 1 test mod pack, reached `Done`, loaded two islands at schema version 2, and completed a clean world save on shutdown. Schema version 3 still requires refreshed staging startup verification before production.
- The dedicated-server run confirms that optional mixin discovery no longer loads Minecraft targets before KubeJS prepares its mixins. It also confirms that legacy namespaced `Unknown` biome values load without invalid-resource warnings.
- The first runtime attempt exposed an unrelated MasuPlots and Supplementaries book-pile crash after startup. This is outside SkyblockAddon and did not recur because the final verification run was stopped immediately after startup validation.
- Production approval still requires copied production island-data comparison and the remaining runtime compatibility matrix.
