package yorickbm.skyblockaddon.enhanced;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public final class EnhancedGuiHelper {

    private EnhancedGuiHelper() {}

    public static void addLore(ItemStack item, Component... lines) {
        CompoundTag display = item.getOrCreateTagElement("display");
        ListTag lore = display.contains("Lore") ? display.getList("Lore", 8) : new ListTag();
        for (Component line : lines) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        display.put("Lore", lore);
    }

    public static ItemStack getPlayerHead(UUID playerUuid) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        CompoundTag skullOwner = new CompoundTag();
        skullOwner.putUUID("Id", playerUuid);
        head.getOrCreateTag().put("SkullOwner", skullOwner);
        return head;
    }
}
