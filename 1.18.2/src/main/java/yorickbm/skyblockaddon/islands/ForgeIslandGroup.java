package yorickbm.skyblockaddon.islands;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.islands.GroupNames;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.util.NBTSerializable;
import yorickbm.skyblockaddon.util.NBTUtil;

import java.util.UUID;

public class ForgeIslandGroup extends IslandGroup implements NBTSerializable {
    private ItemStack item;

    public ForgeIslandGroup() {
        super();
        this.item = new ItemStack(Items.PAPER);
    }

    public ForgeIslandGroup(UUID modUuid, ItemStack item, boolean b) {
        super(modUuid, b);
        this.item = singleItemCopyOf(item);
    }

    private static ItemStack singleItemCopyOf(final ItemStack source) {
        final ItemStack singleItem = source.copy();
        if (!singleItem.isEmpty()) singleItem.setCount(1);
        return singleItem;
    }

    @Override
    public String getName() {
        return nameOf(this.item);
    }

    /**
     * The name a group made from this item gets: the item's hover name, without the brackets of its display name.
     */
    public static String nameOf(final ItemStack item) {
        return GroupNames.normalise(item.getHoverName().getString());
    }

    public ItemStack getItem() {
        if(this.item.getItem() == Items.AIR) {
            this.item = new ItemStack(Items.PAPER, 1);
        }

        ItemStack withData = this.item.copy();
        withData.getOrCreateTag().putString("group_name", this.getName());
        withData.getOrCreateTag().putUUID(GuiDataKeys.GROUP_ID, this.getId());

        return withData;
    }

    @Override
    public CompoundTag serializeNBT() {
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid", this.uuid);

        final CompoundTag members = new CompoundTag();
        for(int i = 0; i < this.members.size(); i++) {
            members.putUUID(i+"", this.members.get(i));
        }
        tag.put("members", members);

        if(this.item.getItem() == Items.AIR) { //Security for air items
            this.item = new ItemStack(Items.PAPER, 1);
        }
        tag.put("item", NBTUtil.itemStackToNBT(this.item));

        final CompoundTag permissions = new CompoundTag();
        for(final var permission : this.permissions.entrySet()) {
            permissions.putBoolean(permission.getKey(), permission.getValue());
        }
        tag.put("permissions", permissions);

        return tag;
    }

    @Override
    public void deserializeNBT(final CompoundTag tag) {
        this.uuid = tag.getUUID("uuid");

        final CompoundTag members = tag.getCompound("members");
        for(final String key : members.getAllKeys()) {
            this.members.add(members.getUUID(key));
        }

        this.item = NBTUtil.nbtToItemStack(tag.getCompound("item"));

        final CompoundTag permissions = tag.getCompound("permissions");
        for(final String key : permissions.getAllKeys()) {
            this.permissions.put(key, permissions.getBoolean(key));
        }
    }
}
