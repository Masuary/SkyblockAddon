package yorickbm.skyblockaddon.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The stacks a right click into the air may run. Usually the held stack; an Effortless Building randomizer bag also
 * runs the use of a random stack from its inventory (sneaking opens the bag), so the bag and every stack in it are
 * judged. The bag is recognised by class name so Effortless Building stays an optional dependency.
 */
public final class RightClickedStacks {
    private static final String RANDOMIZER_BAG_CLASS_NAME = "nl.requios.effortlessbuilding.item.AbstractRandomizerBagItem";

    private RightClickedStacks() {
    }

    public static List<ItemStack> stacksUsedBy(final Player player, final ItemStack heldStack) {
        if (player.isShiftKeyDown() || !isRandomizerBag(heldStack.getItem())) return List.of(heldStack);
        final List<ItemStack> stacks = new ArrayList<>();
        stacks.add(heldStack);
        heldStack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null).ifPresent(inventory -> stacks.addAll(nonEmptyStacks(inventory)));
        return stacks;
    }

    private static boolean isRandomizerBag(final Item item) {
        for (Class<?> type = item.getClass(); type != null; type = type.getSuperclass()) {
            if (RANDOMIZER_BAG_CLASS_NAME.equals(type.getName())) return true;
        }
        return false;
    }

    private static List<ItemStack> nonEmptyStacks(final IItemHandler inventory) {
        final List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        return stacks;
    }
}
