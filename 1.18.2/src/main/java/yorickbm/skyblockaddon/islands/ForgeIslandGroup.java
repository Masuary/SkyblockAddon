package yorickbm.skyblockaddon.islands;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.permissions.PermissionStateMigrator;
import yorickbm.skyblockaddon.util.NBTSerializable;
import yorickbm.skyblockaddon.util.NBTUtil;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public class ForgeIslandGroup extends IslandGroup implements NBTSerializable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private ItemStack item;

    public ForgeIslandGroup() {
        super();
        this.item = new ItemStack(Items.PAPER);
    }

    public ForgeIslandGroup(UUID modUuid, ItemStack item, boolean b) {
        super(modUuid, b);
        this.item = item;
    }

    public ForgeIslandGroup(final ForgeIslandGroup group) {
        super(group.getId(), false);
        this.item = group.item.copy();
        this.members.clear();
        this.members.addAll(group.members);
        this.permissions.clear();
        this.permissions.putAll(group.permissions);
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
        tag.putInt("permissionSchemaVersion", PermissionStateMigrator.CURRENT_SCHEMA_VERSION);

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
        final Set<String> storedPermissionIds = new HashSet<>(permissions.getAllKeys());
        for(final String key : permissions.getAllKeys()) {
            this.permissions.put(key, permissions.getBoolean(key));
        }
        if (tag.getInt("permissionSchemaVersion") < PermissionStateMigrator.CURRENT_SCHEMA_VERSION) {
            final int migratedCount = PermissionStateMigrator.migrate(this.permissions, storedPermissionIds);
            LOGGER.info("Migrated {} permission states for island group {} to schema {}",
                    migratedCount, this.uuid, PermissionStateMigrator.CURRENT_SCHEMA_VERSION);
        }
    }
}
