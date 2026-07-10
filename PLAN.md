# SkyblockAddon Upstream 8.2 Integration Plan

## Status

- Planning baseline: local `wolds` commit `1097a69` (`9.10`).
- Upstream baseline: Yorick `Version-8.X` commit `563ec4fc3b5126b1d7ceb23ba25663c958e6f338` (`8.2`).
- Latest official upstream checked on 2026-07-10: `eafb115496393279f04950cc7218c622213b4e57`. Post-base commits through `eafb115` are manually ported in Phase 9.
- Common ancestor: `0d99631e5ac146d84feb9787dee4bbb64875d903`.
- Strategy: start from upstream 8.2 and port only missing behavior. Do not merge the two branches directly.
- Resulting version: `10.0.0`, because this changes the source base, configuration layout, permission IDs, and persisted permission state.
- Source implementation status: complete.
- Release status: build and Backend 1 startup verified. Production approval remains blocked on copied production island data, the production mod inventory, and the staging runtime matrix.

## Goal

Produce a production-ready SkyblockAddon based on upstream 8.2 that:

1. Preserves all working Wold's Vaults behavior and live island data.
2. Retains every local security, persistence, biome, command, and MasuGUI improvement that upstream lacks.
3. Supports every mod represented by upstream registries and the deployed permission configuration.
4. Keeps Yorick's inventory GUI as the fallback for clients without MasuGUI.
5. Starts cleanly when optional mods are absent.
6. Has automated coverage for data migration, membership integrity, permissions, and configuration parsing.
7. Produces no compiler, linter, mixin, or IDE warnings without a documented reason.

## Authoritative Inputs

- Upstream 8.2 source and all 48 upstream commits after the common ancestor.
- Local 9.10 source and all 36 local commits after the common ancestor.
- Deployed configuration snapshot:
  `/home/masuary/Downloads/archive-2026-07-04T145717Z/`
- Prior audits under `audits/`, especially membership persistence and Effortless Building findings.
- Exact deployed mod JARs and their registry contents. These must be inventoried before compatibility is declared complete.
- Forge 1.18.2 and decompiled mod sources under `/mnt/data/AI/docs/minecraft/`.

The deployed configuration is authoritative over bundled defaults where they differ. The archive contains 71 permission IDs. Upstream contains 77 permission IDs. Neither set may be silently discarded.

## Non-Negotiable Invariants

- Every non-owner island member exists exactly once in the island's canonical member set and exactly once in one island group.
- Group assignment cannot remove canonical membership.
- Serialization and copy constructors preserve the same owner, members, groups, permissions, spawn, biome, visibility, and modified chunks.
- Island world mutation, entity mutation, teleportation, manager registration, and save iteration occur on the server thread.
- Slow structure parsing may run off-thread, but its result must be applied on the server thread.
- Configuration and NBT migrations are atomic, backed up, idempotent, and versioned.
- An unknown or invalid permission pattern fails during configuration loading with a file, permission ID, and pattern in the error.
- Optional-mod absence never causes class loading, mixin, registry, or configuration failures.
- Permission denial cannot leave ghost blocks, open containers, temporary entities, mounts, or item-transfer side effects.
- MasuGUI must not contain a separate implementation of island authorization or persistence rules.

## Phase 0 - Preserve Both Histories and Establish Reproducibility

- [x] Tag or otherwise preserve local `wolds` at `1097a69` before integration begins.
- [x] Create the integration branch directly from upstream `563ec4f`.
- [x] Record the upstream remote and common ancestor.
- [x] Keep local 9.10 available as a read-only porting reference.
- [x] Capture the exact Java, Forge, Gradle, and dependency versions.
- [ ] Inventory the actual server mod directory with file names, mod IDs, versions, and SHA-256 hashes.
- [ ] Copy the live SkyblockAddon configuration and island data to a read-only migration fixture.
- [ ] Identify the real live island data location. The archived `island.nbt` is only the island structure template and is not sufficient for membership or permission-state migration.
- [x] Run and record the clean integrated build with `bash gradlew buildAll --warning-mode all`. Separate historical baseline builds were superseded by the full source/history audit and are no longer a release gate.

Completion gate:

- Both baselines build reproducibly.
- The live mod and data inventories are complete.
- No implementation begins without recoverable backups.

## Phase 1 - Add the Regression Harness First

### Core unit tests

- [x] Add tests for owner, member, visitor, and custom-group assignment.
- [x] Assert the canonical membership invariant before and after NBT serialization.
- [x] Assert copy construction cannot drop custom-group members.
- [x] Assert removing a group moves its members to the intended default group.
- [x] Assert duplicate group membership is rejected or repaired deterministically.
- [x] Round-trip spawn, biome, visibility, modified chunks, group icons, names, and permissions.
- [x] Test permission alias and split migrations.
- [x] Test repeat migration to prove idempotence.
- [x] Test legacy and structured lore parsing for permission and GUI files.
- [x] Test invalid regex, missing group references, cyclic group references, and duplicate permission IDs.
- [x] Test atomic-write fallback only for `AtomicMoveNotSupportedException`; other I/O failures propagate without fallback.

### Forge integration and GameTests

- [ ] Add owner, member, visitor, OP, and FakePlayer interaction fixtures.
- [ ] Test block use, block break, block placement, entity interaction, item pickup/drop, GUI access, portals, vehicles, fluids, XP, beds, bonemeal, and spawn control.
- [ ] Verify denied interactions close predicted client menus and resend affected block entities/chunks.
- [x] Verify optional mixin selection loads a configuration only when its target class resource exists and never defines the target class during discovery.
- [ ] Add a dedicated-server startup test with none of the optional mods installed.
- [ ] Add a dedicated-server startup test with the complete Wold's Vaults mod set.

Completion gate:

- Every critical migration and persistence path has a failing regression test before its fix is ported.

## Phase 2 - Fix Upstream Correctness and Persistence Before Features

### Membership and groups

- [x] Port the 9.2 and 9.10 membership fixes into upstream's refactored `Island` model.
- [x] Make `Island` the mutation boundary for adding, removing, or reassigning members.
- [x] Remove direct `IslandGroup.addMember` calls from command and GUI mutation paths.
- [x] Repair the `ForgeIsland` copy constructor so it copies canonical membership and group assignment without replaying lossy mutation logic.
- [x] Add a load-time integrity validator that repairs recoverable split membership.
- [x] Ensure default-group recreation never grants all visitor permissions after malformed or legacy data is loaded.

### Saves, caches, and threading

- [x] Keep upstream's atomic NBT writer but narrow its fallback exception handling.
- [x] Preserve one authoritative save pipeline for all islands. Remove competing cache-filtered save behavior.
- [x] Clear manager maps and invalidate all caches before reload initialization.
- [x] Bound all reverse and bounding-box caches.
- [x] Keep structure parsing off-thread and all Minecraft world work on the server thread.
- [x] Make create reservations and cooldown insertion atomic so duplicate create requests cannot pass simultaneously.
- [x] Release reserved locations and cooldowns on every failed creation path.
- [x] Use a heightmap or explicit surface calculation that is valid after direct chunk block placement.
- [x] Shut down workers cleanly during server stop without swallowing `NoClassDefFoundError`.

### Configuration hardening

- [x] Reject invalid regex during load rather than during a player interaction.
- [x] Add cycle detection and a descriptive error to permission-group expansion.
- [x] Reject unresolved group references unless the reference belongs exclusively to an absent optional mod.
- [x] Stop swallowing directory-creation failures.
- [x] Define deterministic file ordering and duplicate-ID behavior.
- [x] Remove the per-use executor leak and off-thread inventory mutation from the Quark Pickarang compatibility path.

Completion gate:

- Membership, serialization, save, reload, and island creation tests pass under repeated and concurrent execution.

## Phase 3 - Migrate Live Configuration and Permission State

### Parser compatibility

- [x] Extend both lore deserializers to accept the legacy nested JSON-string form and upstream's structured segment form.
- [x] Keep `display_name` and title parsing compatible with their existing serialized-component representation.
- [x] Preserve all custom colors, text, placeholders, actions, slots, fillers, and conditions.
- [x] Add a migration warning that identifies legacy files without preventing startup.

### Configuration conversion

- [x] Back up the complete `config/skyblockaddon` directory before writing.
- [x] Convert all 10 deployed GUI files to structured lore in a separate migration output.
- [x] Preserve `config.toml`, `language.json`, `BiomeRegistry.json`, and `island.nbt` unchanged in the migration output.
- [x] Convert the single 71-entry `PermissionRegistry.json` into validated per-mod permission files.
- [x] Preserve the old registry as `PermissionRegistry.pre-10.0.json` only after all new files parse and validate.
- [x] Prevent the old registry from shadowing upstream's new permission directory.
- [x] Merge language keys, keeping server-customized values and adding missing upstream keys.
- [x] Generate a machine-readable migration report containing every retained, normalized, split, added, custom, and retired permission.

### Permission-state migration

- [x] Add a persisted permission-schema version and an idempotent migration marker.
- [x] Copy old values into every replacement when one old permission splits into multiple new permissions.
- [x] Preserve IDs that have no safe replacement as custom per-mod permissions.
- [x] Derive new granular Create, Mekanism, Thermal, storage, and Vault permissions from the most specific matching old permission, not from a global default.
- [x] Never default a newly introduced member permission to allowed merely because an island owner is allowed.
- [x] Back up every island NBT file before migration and write replacements atomically.
- [ ] Validate pre-migration and post-migration island counts, owners, members, groups, and permission counts.
- [ ] Produce a list of memberships that cannot be recovered from current NBT so they can be checked against older backups or logs.

Required legacy mapping rules include:

| Legacy permission | Migration target |
| --- | --- |
| `interact_animal_pen` | `vh_animal_pen` |
| `vh_crystals_crafting` | `vh_crystal_modification` plus retained Wold's crafting stations |
| `vh_gear_tools` | `vh_gear` and `vh_tools` |
| `vh_jewels_trinkets` | `vh_jewels` plus retained trinket and Wold's stations |
| `vh_decks_cards` | `vh_cards` |
| `vh_animals_farming` | `vh_animal_pen` and `vh_animatrix` |
| `vh_personal` | Preserve and seed `vh_ascension` and `vh_wardrobe` from its previous value where appropriate |
| `vh_miscellaneous` | Split by exact registered block into the new upstream Vault permissions; retain unmatched Wold's blocks |
| `mod_create` | Seed granular Create machine, seat, railway, controls, assembly, copycat, electrical, and burner permissions |
| `mod_mekanism` | Seed `mekanism_machines` and `mekanism_qio` |
| `mod_thermal` | Seed `thermal_machines` |

The implementation must generate a complete machine-readable mapping from the final registries. The table above is not the complete mapping.

Completion gate:

- A dry run against copied live data reports zero unexplained permission or island losses.
- Running the migration twice produces no additional changes.

## Phase 4 - Port Local-Only Behavior

### Security and interaction compatibility

- [x] Port the complete Effortless Building 8.9 through 9.9 integration:
  - normal and batch placement interception;
  - visitor protection;
  - held-block permission matching;
  - legitimate interaction carve-outs;
  - boundary-straddling behavior;
  - client resynchronization;
  - non-overworld handling;
  - no production debug spam.
- [ ] Complete the Effortless Building runtime matrix against the deployed client and server JARs.
- [ ] Complete runtime verification of PneumaticCraft protection for blocks, items, entities, projectiles, placement, pickup, and damage. The local event coverage is ported, but the deployed JAR is still required for packet-level verification.
- [x] Preserve the Amadron drone exception without opening a general drone bypass.
- [x] Restore `EventPriority.HIGHEST` for Vault Wardrobe shift-right-click protection.
- [x] Port FakePlayer interaction hardening:
  - catch and log mod block exceptions with context;
  - always close menus;
  - always dismount;
  - discard only entities created by the probe;
  - prevent Create seat or other probe side effects.
- [x] Retain the exact upstream-equivalent Vault pedestal and Buildscape pillar theft fixes without duplicating mixins.

### Commands and server behavior

- [x] Port the bulk admin permission command with preview, target filtering, confirmation, rollback, and result counts.
- [x] Restore the mandatory Terralith incompatibility guard so startup fails before any world is opened.
- [x] Verify island spawn persistence, already present upstream, against local regression tests.
- [x] Verify modified-chunk uniqueness, persistence, removal, and empty-chunk classification against local regression tests.
- [ ] Verify `/cleanchunks` command execution and operator permissions on staging.

### Biomes

- [x] Merge Regions Unexplored biomes into the upstream registry.
- [x] Keep meaningful per-biome icons instead of upstream's all-dead-bush defaults.
- [x] Port biome picker lore and admin-menu parity.
- [x] Preserve upstream chunk-load biome reapplication.
- [x] Defer chunk-load tracking and biome reapplication until the chunk is visible through the server chunk cache.
- [x] Keep missing optional biomes inactive without failing chunk load or showing invalid GUI entries.

Completion gate:

- Every local-only commit is either ported, proven equivalent upstream, or explicitly rejected with a documented reason.

## Phase 5 - Full Permission and Optional-Mod Support

### Support definition for each mod

A mod is considered fully supported only when all applicable checks pass:

- [ ] Its exact deployed version and mod ID are recorded.
- [ ] Registry names are extracted from the real JAR, not inferred from language keys.
- [ ] Every relevant block, item, entity, GUI, packet, and indirect interaction path is classified.
- [ ] Normal `Block.use` interactions use generic events rather than unnecessary mixins.
- [ ] Direct world writes, packets, entity collisions, and global event listeners receive narrow verified hooks.
- [ ] Owner, allowed member, denied member, visitor, OP, automation, and FakePlayer behavior is specified.
- [ ] Denied behavior produces no server-side or client-side residue.
- [ ] Its permissions appear in both Yorick's fallback GUI and MasuGUI.
- [ ] Configuration is extracted only when the mod is loaded, or is safely inactive while absent.
- [ ] Startup succeeds with the mod absent.
- [ ] At least one runtime integration test covers each non-generic hook.

### Wold's Vaults and locally required integrations

| Integration | Required coverage |
| --- | --- |
| Vault Hunters Remastered | All current stations, altar debounce, portals, wardrobe shift-use, animal pen, animatrix, crates, companions, ascension, crucible, artifacts, gear, tools, jewels, cards, recycling, personal blocks, item-transfer safety |
| Wold's Vaults | Augment crafting, both vault infusers, weaving station, mod-box workstation, doll dismantler, crate cracker, vault salvager, Wold's vault crates |
| Buildscape | Pillar item-transfer/theft prevention using upstream's verified fix |
| Effortless Building | Single and batch placements, replace/build modes, boundary crossing, resync, dimensions |
| PneumaticCraft | Machines, item interactions, placement, projectiles, drones, FakePlayers, Amadron exception |
| MasuGUI | Optional enhanced presentation with unmodded-client fallback; the MasuGUI library itself remains unchanged |
| Regions Unexplored | Biome selection, icon/lore, persistence, chunk reload, absent-mod behavior |
| Sophisticated Storage | Dedicated interaction permission and controller icon |
| Companion Work Station | Companion workbench and recycler |
| Vending Companions | Companion vending machine |
| Mining Dimension | Teleporter and dimension behavior |

### Storage and logistics integrations

| Integration | Required coverage |
| --- | --- |
| Applied Energistics 2 | Core terminals and blocks |
| AE2 Things | AE2 permission inheritance with verified registry entries |
| MEGA Cells | AE2 permission inheritance |
| Applied Botanics | AE2 permission inheritance |
| Applied Mekanistics | AE2 permission inheritance |
| Refined Storage | Core network access |
| Extra Storage | Refined Storage permission inheritance |
| Integrated Dynamics | Blocks and terminals |
| Integrated NBT | Blocks and terminals |
| Integrated Terminals | Terminal access |
| Integrated Tunnels | Blocks and interfaces |
| Integrated Crafting | Blocks and interfaces |
| Tom's Simple Storage | `toms_storage:ts*` blocks |
| RFTools Storage | Storage scanner and related blocks |
| Cloud Storage | Storage blocks and menus |
| Sophisticated Backpacks | Block and item-backed backpack access as applicable |
| Sophisticated Storage | All storage blocks and controllers |
| Storage Drawers | Right-click and left-click drawer behavior |
| Functional Storage | Right-click and left-click drawer behavior |
| Framed Compact Drawers | Storage Drawers inheritance |
| Simple Storage Network | Network access |
| Colossal Chests | All chest components with an actual interaction path |
| Chest Monster | Interactable blocks only |
| Cooking for Blockheads | Kitchen storage and non-storage interactables |

### Machines, magic, transport, and building integrations

| Integration | Required coverage |
| --- | --- |
| Create | Machines, seats, train controls, assembly, copycats, redstone links; preserve seat carve-outs |
| Create Crafts & Additions | Electrical machines |
| Create: Steam 'n' Rails | Railway controls and machines |
| More Burners | Create burner interactions |
| Mekanism | Machines and QIO split |
| Thermal | Machines |
| Botania | Interactive blocks, with special paths verified rather than namespace-only assumptions |
| Immersive Engineering | Interactive blocks and multiblocks |
| Industrial Foregoing | Interactive machines |
| RFTools Base | Interactive blocks |
| RFTools Power | Interactive blocks |
| RFTools Utility | Redstone logic blocks and other interactables |
| Super Factory Manager | Manager and cable interactions |
| Occultism | Storage and ritual-related interactables |
| Ars Nouveau | Interactive blocks and entities |
| Mystical Agriculture | Interactive blocks |
| Mystical Agradditions | Mystical Agriculture permission inheritance |
| Dave's Potioneering | Interactive stations |
| Building Gadgets | Build, destruction, exchange, and copy-paste gadgets |
| BlockCarpentry | Interactive framed blocks and detectors |
| MrCrayfish's Furniture | Cabinets, mailbox, sink, cooler, fridge |
| Easy Villagers | Block interaction and villager pickup packet |
| Easy Piglins | Barterer interaction and piglin pickup packet |
| Elevator Mod | Teleport packet handler |
| Flux Networks | Network blocks |
| Iron Furnaces | Furnace interaction inheritance |
| Every Compat | Doors, trapdoors, fences, and related inheritance |
| Macaw's Fences | Fence-gate interaction inheritance |
| Quark | Pickarang, glass frames, placement paths, and client resync |
| Supplementaries | Slingshot and relevant interactive blocks |
| Waystones | Waystone activation |

### Permission-registry rules

- [ ] Replace broad namespace permissions only when granular permissions improve safety without changing deployed access unexpectedly.
- [ ] Retain broad custom permissions where the mod's interaction surface is intentionally managed as one unit.
- [ ] Preserve explicit negative patterns and ordering semantics from the deployed registry.
- [ ] Verify every broad namespace against the actual mod JAR to avoid protecting decorative or non-interactable blocks unnecessarily.
- [x] Ensure every permission category referenced by a permission exists in both GUI implementations.
- [x] Ensure no permission ID is duplicated across per-mod files.
- [x] Ensure every bundled trigger maps to an implemented event or mixin path.

### Permission-ID completeness inventory

The migration test must compare sets and fail if any ID is absent from its migration report. The current set difference is fixed and reviewable:

Deployed-only IDs that must be retained or explicitly mapped:

```text
interact_animal_pen
interact_functional_drawers
mod_ars_nouveau
mod_botania
mod_create
mod_davespotioneering
mod_immersiveengineering
mod_industrialforegoing
mod_mekanism
mod_mysticalagriculture
mod_occultism
mod_pneumaticcraft
mod_rftoolsbase
mod_rftoolspower
mod_sfm
mod_thermal
open_ID
open_Tom
open_cloud
open_rftools
open_sophstorage
redstone_detectors
rftools_redstone
vh_alchemy_drinks
vh_animals_farming
vh_crystals_crafting
vh_decks_cards
vh_etchings_artifacts
vh_eternals
vh_gear_tools
vh_jewels_trinkets
vh_miscellaneous
```

Upstream-only IDs that require an initial-state source or an explicit safe default:

```text
blockcarpentry_interact
cfm_cabinets
cfm_cooler
cfm_fridge
cfm_kitchen_sink
cfm_mailbox
chestmonster_interact
cookingforblockheads_interact
cookingforblockheads_storage
create_burners
create_copycats
create_electrical
create_machines
create_railways
create_seats
create_train_assembly
create_train_controls
easy_piglins_interact
elevators_use
flux_networks
interact_fence_gates
interact_furnaces
mekanism_machines
mekanism_qio
ssn_access
thermal_machines
use_anvils
vh_animal_pen
vh_animatrix
vh_ascension
vh_cards
vh_crucible
vh_crystal_modification
vh_enter_vault
vh_gear
vh_jewels
vh_tools
vh_wardrobe
```

Shared IDs that require semantic comparison even though their names match:

```text
activate_waystones
admin_menu
destroy_blocks
disable_hostile_spawns
disable_passive_spawns
interact_XP
interact_bed
interact_doors
interact_easy_villagers
interact_entities
interact_fluids
interact_generic
interact_item_frame
interact_items
interact_misc
interact_spawn
interact_storage_drawers
open_AE
open_RF
open_backpacks
open_barrels
open_chests
open_colossalchests
open_shulker_box
place_blocks
redstone_buttons_and_levers
redstone_containers
redstone_create_links
redstone_delays
redstone_hoppers
trample_framland
transport_misc
travel_nether
use_vehicles
vh_artifacts
vh_companions
vh_crates
vh_personal
vh_recycle
```

Equal names do not prove equal behavior. Stored values, precedence, triggers, patterns, defaults, item icons, and categories must all be compared.

Exact upstream permission-resource mod IDs:

```text
ae2
blockcarpentry
cfm
chestmonster
colossalchests
cookingforblockheads
create
createaddition
creeper_power
easy_piglins
easy_villagers
elevatorid
fluxnetworks
mekanism
moreburners
railways
refinedstorage
sophisticatedbackpacks
storagedrawers
storagenetwork
the_vault
thermal
waystones
```

Upstream also has group-only resource files for `everycomp`, `framedcompactdrawers`, `ironfurnaces`, `mcwfences`, `quark`, and `supplementaries`. These must be validated even though they do not own a dedicated permission file.

Completion gate:

- The compatibility matrix contains no unverified deployed mod.
- Every archive-only permission is retained or has an approved, tested migration target.

## Phase 6 - Port the MasuGUI Integration Without Changing MasuGUI

Implemented architecture: optional MasuGUI compatibility remains inside SkyblockAddon, while `masugui-1.0.0.jar` stays unchanged and separate. Runtime registration and Forge metadata are optional, so Yorick's inventory GUI remains the fallback.

- [x] Leave `masugui-1.0.0` unchanged.
- [x] Add a stable `OpenMenuEvent.getGuiId()` API to SkyblockAddon. Do not identify menus by localized title text.
- [x] Intercept `OpenMenuEvent` at `HIGH` priority only for clients confirmed by `PlayerTracker`.
- [x] Cancel Yorick's menu only after invoking the matching MasuGUI handler.
- [x] Keep Yorick's JSON GUI untouched as the fallback for clients without MasuGUI.
- [x] Port hub, travel, members, biomes, groups, group assignment, permission categories, permission toggles, and confirmation screens.
- [x] Preserve confirmation for leaving, removing groups, creating groups, and changing spawn.
- [x] Populate permission categories and entries dynamically from upstream registries. Remove the hardcoded seven-category list.
- [x] Use group icon items and preserve default/custom group separation.
- [x] Route every mutation through shared SkyblockAddon services or action handlers. MasuGUI callbacks do not directly manipulate protected state.
- [x] Match authorization semantics between fallback GUI and MasuGUI for owner, group admin permission, and server OP.
- [ ] Test modded client, unmodded client, dedicated server, reconnect, menu transitions, scrolling, and stale-session handling.
- [x] Make dependency metadata match optional deployment behavior.

Completion gate:

- The same action produces the same authorization and data result through both GUI implementations.
- Removing the MasuGUI compatibility artifact restores the fallback GUI without startup failure.

## Phase 7 - Validate Upstream-Only Features We Are Adopting

- [x] Per-mod permission and group resource extraction, including preservation of deployed files.
- [ ] Permission debug output and reload commands.
- [x] Interaction validator precedence, negation, group expansion, and mod-load filtering.
- [ ] Nether and Vault portal protection.
- [ ] Easy Villagers and Easy Piglins mixins.
- [ ] Elevator packet protection.
- [ ] Create, Copycats, railway, electrical, burner, and seat permissions.
- [ ] Vault altar delayed revalidation.
- [ ] Chunk tracking and cleaning.
- [ ] Island structure preload and server-thread placement.
- [ ] Void protection configuration.
- [ ] GUI conditional lore, joins, dynamic fillers, pagination, and admin conditions.
- [ ] Public island events and action hooks.
- [ ] Version checker metadata and update URL.

Fix before release:

- [x] `RegistryGuiEvents` provides the real admin state to conditional lore.
- [x] `version.json`, `gradle.properties`, `mods.toml`, artifacts, and documentation agree on 10.0.0.
- [x] Resolve public Mixin target warnings and deprecated `ResourceLocation` usage.
- [x] Remove stale selectors and dead compatibility code made obsolete by directory-based registries.

## Phase 8 - End-to-End Verification and Release

### Static and build verification

- [x] Run all unit and integration tests.
- [x] Run `bash gradlew cleanAll` followed by `bash gradlew buildAll --warning-mode all` from clean build outputs.
- [x] Treat every warning as a release blocker unless documented with evidence.
- [x] Inspect the final JAR for core classes, resources, all mixin configs, refmaps, and metadata.
- [x] Confirm optional mod JARs are not accidentally bundled.

Verification evidence:

- 60 Java tests and one converter test pass with zero failures.
- The only warning is ForgeGradle 6.0.54 calling Gradle's deprecated `ResolvedConfiguration.getFirstLevelModuleDependencies(Spec)` API. It is external build-plugin code and is recorded in the dated audit.
- The JAR contains all 11 mixin configs, `skyblockaddon.refmap.json`, `META-INF/mods.toml`, fallback GUI resources, permission migration rules, and custom-mod permission resources.
- No optional dependency package is embedded in the JAR.
- The previously verified JAR reached `Done` with the full Backend 1 test mod pack, loaded two schema-v2 islands, and completed a clean shutdown save. The current schema-v3 permission changes still need a refreshed staging startup and copied-data comparison before production.

### Migration rehearsal

- [ ] Restore a staging server from a production backup.
- [ ] Run migration in report-only mode.
- [ ] Review every permission mapping and membership repair.
- [ ] Run the real migration on a fresh copy.
- [ ] Restart twice and compare island NBT hashes after the second clean shutdown.
- [ ] Compare island, owner, member, group, permission, spawn, biome, and modified-chunk counts before and after.
- [ ] Confirm no legacy registry shadows the migrated per-mod files.

### Runtime matrix

- [ ] Owner, ordinary member, custom-group member, visitor, OP, and automation account.
- [ ] MasuGUI client and client without MasuGUI.
- [ ] Every deployed custom mod's allowed and denied interaction paths.
- [ ] Island creation during save, simultaneous creates, restart during creation, and creation failure.
- [ ] Group create, assign, reassign, remove, save, restart, and reload.
- [ ] Biome change, chunk unload/reload, relog, and server restart.
- [ ] Portal, vehicle, storage, FakePlayer, and indirect placement paths.
- [ ] `/cleanchunks`, bulk permission command, permission reload, and GUI reload.

### Documentation and deployment

- [x] Replace the stale README with current server/client requirements, optional MasuGUI behavior, supported Minecraft/Forge/Vault versions, and migration instructions.
- [x] Add technical persistence documentation for membership, threading, configuration schema, and migration versions.
- [ ] Document every optional integration and its tested version after the production mod inventory is supplied.
- [x] Write rollback instructions that restore both configuration and island data.
- [x] Build release artifacts and record hashes.
- [ ] Deploy to staging first, then production only after the runtime matrix passes.

## Release Definition of Done

Release 10.0.0 is complete only when:

- No local-only commit remains unclassified.
- No deployed permission ID remains unmapped.
- No deployed mod remains unverified.
- All island and configuration migrations pass twice on copied production data.
- Membership cannot be lost through commands, either GUI, save, reload, or restart.
- Both GUI paths produce equivalent authorization and mutations.
- Optional mods can be independently absent without startup failures.
- All tests and builds pass with no unexplained warnings.
- Staging completes at least one full save and restart cycle without data drift.

## Phase 9 - Upstream Drift After the 8.2 Base

Yorick added three commits after this integration was implemented:

- [x] Integrate `2586419` granular Industrial Foregoing and Sophisticated Storage permissions.
- [x] Add migration rules from `mod_industrialforegoing` to `industrialforegoing_machines` and from `open_sophstorage` to `sophisticatedstorage_storage` plus `sophisticatedstorage_link` without changing existing island access unexpectedly.
- [x] Rename the inactive optional resource gate from `creeper_power` to the corrected `creeperpower` mod ID.
- [x] Integrate `35af3ff` Ars Nouveau Warp Portal protection using optional mixin loading.
- [x] Verify the mixin target against the exact deployed Ars Nouveau 2.9.0 JAR and cover migration, permission matching, and absent-mod resource loading with automated tests.
- [x] Port `eafb115` registry additions for Remastered and Wold's Vaults permission coverage, then retire or split shadowing local broad IDs so the new granular permissions control live interactions.
- [ ] Run allowed member, denied member, visitor, owner, OP, and absent-mod portal behavior on the staging server.

Source parity with the current official `Version-8.X` branch is complete. Runtime approval remains part of the staging matrix.

## Required Inputs Before Implementation Can Be Declared Complete

- Exact production `mods/` directory or a complete file/hash listing.
- Copy of the live SkyblockAddon island data, not only the configuration archive.
- [x] MasuGUI packaging decision: compatibility remains inside SkyblockAddon while MasuGUI itself remains an optional, unchanged external JAR.
- A production backup old enough to check members lost before the 9.2 and 9.10 fixes, if historical recovery is required.

## Current Build Artifact

- Path: `1.18.2/build/libs/skyblockaddon-10.0.0.jar`
- Size: 591,311 bytes
- SHA-256: `42a2ffdbfa7e002badf9bd53308d87f1b6a0c8245adaf2c4274aef23d2dedfe4`
- Status: clean build verified with schema-v3 permission resources. Dedicated-server startup must be rerun before production because the last startup verification used the earlier schema-v2 artifact.
