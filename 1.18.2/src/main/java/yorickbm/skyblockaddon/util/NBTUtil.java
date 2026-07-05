package yorickbm.skyblockaddon.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
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
        final String registryName = tag.getString("registryName");
        final Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(registryName));
        if (item == null) {
            LogManager.getLogger().warn("NBTToItemStack: unknown item '{}', using barrier as fallback", registryName);
            return new ItemStack(Items.BARRIER);
        }
        final ItemStack stack = new ItemStack(item);
        stack.setTag(tag.getCompound("NBT"));
        return stack;
    }
}
