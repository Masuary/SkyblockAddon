package yorickbm.skyblockaddon.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;

import java.util.Objects;

public class NBTUtil {

    public static CompoundTag Vec3iToNBT(final Vec3i location) {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("x", location.getX());
        tag.putInt("y", location.getY());
        tag.putInt("z", location.getZ());
        return tag;
    }

    public static Vec3i NBTToVec3i(final CompoundTag tag) {
        return new Vec3i(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    public static CompoundTag ItemStackToNBT(final ItemStack item) {
        final CompoundTag tag = new CompoundTag();

        tag.putString("registryName", Objects.requireNonNull(item.getItem().getRegistryName()).toString());
        tag.put("NBT", item.getOrCreateTag());

        return tag;
    }

    public static ItemStack NBTToItemStack(final CompoundTag tag) {
        final ItemStack item = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(tag.getString("registryName"))));
        item.setTag(tag.getCompound("NBT"));
        return item;
    }
}