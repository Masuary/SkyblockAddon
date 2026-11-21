package yorickbm.skyblockaddon.util;

import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.SpawnEggItem;
import yorickbm.skyblockaddon.configs.ConfiguredIdListValue;

import java.util.Objects;

/**
 * Items that put an entity into the world when used. Vanilla runs them without a block placement, so no
 * EntityPlaceEvent ever judges them: the vanilla item classes plus the item ids configured for modded placers.
 */
public final class EntityPlacerItems {
    public static final String EXTRA_ENTITY_PLACER_ITEMS_CONFIG_KEY = "permissions.entity_placer_items";

    private static final ConfiguredIdListValue EXTRA_ENTITY_PLACER_ITEMS = new ConfiguredIdListValue(EXTRA_ENTITY_PLACER_ITEMS_CONFIG_KEY);

    private EntityPlacerItems() {
    }

    public static boolean isEntityPlacer(final ItemStack stack) {
        if (stack.isEmpty()) return false;
        final Item item = stack.getItem();
        if (item instanceof SpawnEggItem || item instanceof ArmorStandItem || item instanceof HangingEntityItem
                || item instanceof EndCrystalItem || item instanceof MinecartItem || item instanceof BoatItem) {
            return true;
        }
        return EXTRA_ENTITY_PLACER_ITEMS.ids().contains(itemId(item));
    }

    private static String itemId(final Item item) {
        return Objects.requireNonNull(item.getRegistryName(), "Unregistered item " + item).toString();
    }
}
