package yorickbm.skyblockaddon.util;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public interface NBTSerializable {
    default CompoundTag serializeNBT() {
        return new CompoundTag();
    }

    void deserializeNBT(CompoundTag tag);

    public UUID getId();
}
