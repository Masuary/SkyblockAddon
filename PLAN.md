# ItemGrid Icon Overflow & Groups Layout Redesign

## Problem

ItemGrid items visually overflow their cell background boxes across all screens (Groups, Biomes, Members, Travel). The items are rendered at `cellSize - 2` pixels (e.g. 16px in an 18px cell), but Minecraft's 3D block rendering extends beyond the nominal item bounds, causing icons to bleed past cell borders.

Additionally, the Groups screen needs a layout redesign to separate default groups from custom groups.

## Fix 1: Item Scale Reduction (MasuGUI - Global)

**File:** `MasuGUI/src/main/java/com/masuary/masugui/client/render/ItemGridRenderer.java`

**Change:** Reduce `innerSize` from `cellSize - 2` to `cellSize - 4`, giving items a 2px margin on each side within the cell. This applies globally to all ItemGrid rendering.

- 18px cell: item renders at 14px (was 16px) - 2px padding per side
- 22px cell: item renders at 18px (was 20px) - 2px padding per side

Items scale proportionally to fill the cell minus padding. The extra margin prevents 3D block items from visually overflowing cell borders.

## Fix 2: Groups Screen Layout Redesign (SkyblockAddon)

**File:** `SkyblockAddon/1.18.2/src/main/java/yorickbm/skyblockaddon/enhanced/ScrollableListGui.java`

### Layout Spec

```
+--------------------------------------------+
|       Owner's Island - Groups          [X]  |
|       Click a group to edit permissions     |
|  ----------------------------------------  |
|                                             |
|   [Visitors]   [Members]                    |  <- Default groups (row 1, up to 5 cols)
|                                             |
|  ----------------------------------------  |  <- Divider
|                                             |
|   [Custom1]  [Custom2]  [Custom3] ...       |  <- Custom groups (row 2+, up to 5 cols)
|   -- or --                                  |
|   "No custom groups"                        |  <- Placeholder when empty
|                                             |
|  ----------------------------------------  |
|  [Back]                   [Create Group]    |
+--------------------------------------------+
```

### Details

- **Panel width:** 220px (same as other variants, unchanged)
- **Columns:** Up to 5, using default 18px cell size
- **Default groups row:** Visitors and Members (identified by `SkyblockAddonCore.MOD_UUID` / `MOD_UUID2`), always present, cannot be removed
- **Divider:** Horizontal line separating default groups from custom groups
- **Custom groups row:** All non-default groups, wrapping into additional rows if >5
- **Empty state:** When no custom groups exist, show "No custom groups" placeholder label below the divider
- **Create Group button:** Stays in the bottom nav bar (no change from current position)
- **Default groups are permanent:** Cannot be removed or renamed, only their permissions can be changed

### set_group Variant

Use the same 2-row layout (defaults on top, divider, customs below) for visual consistency. No Create Group button on this screen since you're just selecting a group to assign.

## Implementation Order

1. Fix `ItemGridRenderer.innerSize` calculation in MasuGUI (cellSize - 4)
2. Revert groups/set_group column and width overrides back to shared defaults (5 cols, 220px, 18px cells)
3. Split `buildGroupItems()` into `buildDefaultGroupItems()` and `buildCustomGroupItems()`
4. Add two separate ItemGrids + divider + placeholder label in `open()` for groups/set_group variants
5. Build and test both projects
