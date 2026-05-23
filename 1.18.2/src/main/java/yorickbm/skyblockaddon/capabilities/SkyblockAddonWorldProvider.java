package yorickbm.skyblockaddon.capabilities;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SkyblockAddonWorldProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    public static final Capability<SkyblockAddonWorldCapability> SKYBLOCKADDON_WORLD_CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
    });

    private final MinecraftServer serverInstance;

    /**
     * Basic singleton instance management
     */
    public static SkyblockAddonWorldProvider instance;

    public static SkyblockAddonWorldProvider getInstance() {
        return instance;
    }

    public SkyblockAddonWorldProvider(final MinecraftServer server) {
        instance = this;
        serverInstance = server;
    }

    private SkyblockAddonWorldCapability capability;
    private final LazyOptional<SkyblockAddonWorldCapability> optional = LazyOptional.of(this::createCapability);

    @NotNull
    private SkyblockAddonWorldCapability createCapability() {
        if (this.capability == null) this.capability = new SkyblockAddonWorldCapability(serverInstance);
        return this.capability;
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull final Capability<T> cap, @Nullable final Direction side) {
        if (cap != SKYBLOCKADDON_WORLD_CAPABILITY) return LazyOptional.empty();
        return optional.cast();
    }

    @Override
    public CompoundTag serializeNBT() {
        final CompoundTag nbt = new CompoundTag();
        createCapability().saveNBTData(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(final CompoundTag nbt) {
        createCapability().loadNBTData(nbt);
    }
}
