package yorickbm.skyblockaddon.components;

import net.minecraft.nbt.CompoundTag;
import yorickbm.skyblockaddon.core.util.DataComponent;

import java.util.UUID;

public class ItemStackComponent extends DataComponent {

    public CompoundTag getCompound() {
        CompoundTag tag = new CompoundTag();

        super.getDataMap().forEach((k,v)-> {
            if(v instanceof UUID) tag.putUUID(k, (UUID)v);
            if(v instanceof String) tag.putString(k, (String)v);
            if(v instanceof CompoundTag) tag.put(k, (CompoundTag)v);
            if(v instanceof Boolean) tag.putBoolean(k, (Boolean)v);
            if(v instanceof Integer) tag.putInt(k, (Integer)v);
        });

        return tag;
    }
}
