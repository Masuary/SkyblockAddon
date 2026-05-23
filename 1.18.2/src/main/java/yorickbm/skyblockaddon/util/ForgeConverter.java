package yorickbm.skyblockaddon.util;

import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.registries.ForgeRegistries;
import yorickbm.guilibrary.GUIItemStackHolder;
import yorickbm.skyblockaddon.core.JSON.ItemStackJson;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ForgeConverter {

    public static yorickbm.skyblockaddon.core.util.geometry.Vec3i ForgeToInternalVec3i(Vec3i vec) {
        return new yorickbm.skyblockaddon.core.util.geometry.Vec3i(vec.getX(), vec.getY(), vec.getZ());
    }

    public static Vec3i InternalToForgeVec3i(yorickbm.skyblockaddon.core.util.geometry.Vec3i vec) {
        return new Vec3i(vec.getX(), vec.getY(), vec.getZ());
    }

    public static BoundingBox InternalToForgeBoundingBox(yorickbm.skyblockaddon.core.util.geometry.BoundingBox box) {
        return new BoundingBox((int)box.minX, (int)box.minY, (int)box.minZ, (int)box.maxX, (int)box.maxY, (int)box.maxZ);
    }

    public static GUIItemStackHolder JSONItemToGUIItemStackHolder(ItemStackJson jsonData) {
        if (jsonData == null) return null;

        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(jsonData.getItem().toLowerCase()));;

        List<String> display = (jsonData.getDisplay_name() != null)
                ? Arrays.asList(jsonData.getDisplay_name())
                : List.of();

        List<List<String>> lore = (jsonData.getLore() != null)
                ? Arrays.stream(jsonData.getLore())
                .map(Arrays::asList)
                .collect(Collectors.toList())
                : List.of();

        CompoundTag tag = new CompoundTag();

        return new GUIItemStackHolder(item, 1, tag, display, lore);
    }

}
