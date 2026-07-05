package yorickbm.skyblockaddon.enhanced;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface EnhancedGuiHandler {
    void open(ServerPlayer player, CompoundTag data);
}
