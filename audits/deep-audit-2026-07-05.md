# SkyblockAddon Follow-up Audit - 2026-07-05

## Verdict: YELLOW

All 36 changes from the previous local 9.10 branch are represented in the integration source. The branch is not fully current with official upstream because Yorick added two commits after the 8.2 base, including one protection hook.

## Critical

None found.

## High

- [ ] [`MixinConnector.java:42`] Ars Nouveau Warp Portals do not receive the new upstream `onEnterPortal` filter.
  why: Ars Nouveau teleports players from `PortalTile.tick()` rather than the normal block-use or Nether/Vault portal paths. The current connector registers no Ars Nouveau mixin, so a denied player can still be selected and teleported by that tile.

## Medium

- [ ] [`industrialforegoing.json:5`] The branch retains broad `mod_industrialforegoing` instead of upstream's new granular `industrialforegoing_machines` permission. This is safe coverage but not current-upstream schema parity.
- [ ] [`sophisticatedstorage.json:5`] The branch retains broad `open_sophstorage` instead of separate storage and link permissions. Migrating blindly would risk changing existing island access, so explicit split rules are required.
- [ ] [`creeper_power.json:2`] The optional resource gate still uses `creeper_power`; current upstream corrected the mod ID to `creeperpower`.
- [ ] Production server JAR inventory, live island data migration, and staging runtime coverage remain unavailable.

## Low

- [ ] The external ForgeGradle 6.0.54 Gradle 9 deprecation remains documented and unresolved upstream.

## Verified Clean

- Previous local branch: every one of the 36 commits from the common ancestor through local 9.10 has an implementation equivalent recorded in `CHANGELOG.md`.
- Automated checks: 44 Java tests and one converter test pass.
- Migration fixture: 84 groups, 98 permissions, and zero active retired IDs.
- Artifact: optional dependencies are not embedded and all existing mixin configurations are present.

## Recommended Next Actions

1. Port the two official post-8.2 upstream commits with permission migration rules and optional-mod guards.
2. Re-run the clean build, artifact inspection, and migrated-configuration semantic load.
3. Complete production inventory, live-data rehearsal, and staging runtime verification before deployment.
