package yorickbm.skyblockaddon.islands;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        this.item = item;
    }

    @Override
    public String getName() {
        return this.item.getDisplayName().getString().trim();
    }

    public ItemStack getItem() {
        if(this.item.getItem() == Items.AIR) {
            this.item = new ItemStack(Items.PAPER, 1);
        }

        ItemStack withData = this.item.copy();
        withData.getOrCreateTag().putString("group_name", this.getName());
        withData.getOrCreateTag().putUUID("group_id", this.getId());

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
        tag.put("item", NBTUtil.ItemStackToNBT(this.item));

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

        this.item = NBTUtil.NBTToItemStack(tag.getCompound("item"));

        final CompoundTag permissions = tag.getCompound("permissions");
        for(final String key : permissions.getAllKeys()) {
            this.permissions.put(key, permissions.getBoolean(key));
        }
    }
}
