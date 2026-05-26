# Deep Audit Snapshot - 2026-05-27

**Scope:** Issue, visitors can use effortlessbuilding-1.18-2.40.jar to build on other peoples island
**SHA:** 76933b7
**Verdict:** 🔴 RED

Snapshot from one audit pass. Findings reflect the code as of the SHA above; line numbers and severities will drift as fixes land. Do not treat this as a living spec - re-run `/deep-audit` for a current view.

## CRITICAL

- [x] [1.18.2/src/main/java/yorickbm/skyblockaddon/events/PermissionEvents.java:301] Place-block protection relies solely on `BlockEvent.EntityPlaceEvent` - EffortlessBuilding bypasses this with direct `world.setBlock()` calls for multi-block placement modes.
  - why: `SurvivalHelper.placeBlock` (effortlessbuilding) calls `world.m_7731_(pos, blockState, 3)` directly. The vanilla `ForgeEventFactory.onBlockPlace` is never invoked for blocks placed via Line/Wall/Cube/Sphere/etc modes, so SkyblockAddon's only block-placement hook never fires for the affected positions. Visitors get unrestricted placement on any island for all blocks past the first.

## HIGH

- [x] [1.18.2/src/main/java/yorickbm/skyblockaddon/mixins/] No `EffortlessBuildingMixinConfig` exists despite the same bypass pattern being already known/handled for BuildingGadgets, Quark, Buildscape, and Vault Treasure Pedestal.
  - why: The mixin folder shows the same bypass pattern is already known and patched for other mods. EffortlessBuilding is uncovered.

## MEDIUM

- [x] [1.18.2/src/main/resources/mixins/skyblockaddon.mixin.json] No mixin config for EffortlessBuilding registered; mod-bypass coverage is per-mod and incomplete by design - any future block-placement mod that calls `world.setBlock()` directly will need its own mixin.

## Recommended next actions

1. Create `EffortlessBuildingMixinConfig` mixing into `nl.requios.effortlessbuilding.helper.SurvivalHelper.placeBlock` at HEAD, calling `InteractionHandler.checkPlayerInteraction(...)` with `"onPlaceBlock"` trigger for each position; cancel the call (return false) if the visitor lacks `place_blocks` on the target island.
2. Add an `effortlessbuilding.mixin.json` config file and reference it from `MixinConnector`.
3. Use `@Restriction(require = @Condition(type = Type.MOD, value = "effortlessbuilding"))` per the Wolds Vaults pattern - so the mixin compiles even without effortlessbuilding loaded.
4. Test with all build modes: Normal, Line, Wall, Floor, Cube, Sphere, Cylinder, Pyramid, Upright Pillar, Diagonal Line.
5. Also check breaking via Build modes (`BuildModes.onBlockBroken` / `SurvivalHelper.breakBlock`) - likely has the same bypass for `destroy_blocks` permission.
