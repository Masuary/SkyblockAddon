package yorickbm.skyblockaddon.events;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.guilibrary.GUIFiller;
import yorickbm.guilibrary.GUIItemStackHolder;
import yorickbm.guilibrary.GUILibraryRegistry;
import yorickbm.guilibrary.events.GuiDrawFillerEvent;
import yorickbm.guilibrary.interfaces.ServerInterface;
import yorickbm.guilibrary.util.FillerPattern;
import yorickbm.skyblockaddon.capabilities.SkyblockAddonWorldProvider;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.registries.interfaces.SkyblockAddonRegistry;
import yorickbm.skyblockaddon.core.util.DataComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Cancelable
public class RegistryEvents extends GuiDrawFillerEvent {
    private static final Logger LOGGER = LogManager.getLogger();

    protected SkyblockAddonRegistry<DataComponent> registry;
    Map<String, String> replacements = new HashMap<>();

    public RegistryEvents(final ServerInterface instance, final GUIFiller filler, final int slots) {
        super(instance, filler, slots);
        if (getPattern() != FillerPattern.INSIDE) { this.setCanceled(true); } //Pattern is not compatible with registry type

    }

    private int getItemsPerPage() {
        final int fillerRows = ((getSlots() / 9) - 2);
        return ((fillerRows * 9) - //Turn rows back into slots
                (fillerRows * 2)); // Remove 2 slots per each row
    }

    public SkyblockAddonRegistry<DataComponent> getRegistry() {
        return this.registry;
    }

    protected void initRegistryPagination() {
        final int maxPage = (int) Math.ceil((double) registry.getSize() / getItemsPerPage());
        setMaxPage(maxPage);
        registry.setIndex(((getCurrentPage() - 1) * getItemsPerPage()) - 1);
    }

    public GUIItemStackHolder processHolder(final GUIItemStackHolder holder, final CompoundTag data) {
        //Add skullowner data into the item
        if(data.contains("SkullOwner")) {
            holder.putData("SkullOwner", data.getString("SkullOwner"));
        }
        if(data.contains("SkullTexture")) {
            holder.putData("SkullTexture", data.getString("SkullTexture"));
        }

        //Add all data as replaceable keys
        for(final String key : data.getAllKeys()) {
            replacements.put("%data_"+key+"%", data.getString(key).trim());
        }

        //Add all Library Mod as replaceable keys
        for(final String key : holder.getData().getAllKeys()) {
            replacements.put("%data_"+key+"%", holder.getData().getString(key).trim());
        }

        //Replace found data keys with their values
        holder.setDisplayName(holder.getDisplayName().stream()
                .map(component -> {
                    final String modifiedText = replacements.entrySet().stream()
                            .reduce(component.getString(), (text, entry) -> text.replace(entry.getKey(), entry.getValue()), (a, b) -> b);
                    return new TextComponent(modifiedText).withStyle(component.getStyle());
                })
                .filter(c -> c instanceof TextComponent) // Ensure it's a TextComponent
                .map(c -> (TextComponent) c) // Cast to TextComponent
                .collect(Collectors.toList()));

        holder.setLore(
                holder.getLore().stream()
                        .map(innerList -> innerList.stream() // Process each inner List<TextComponent>
                                .map(component -> {
                                    final String modifiedText = replacements.entrySet().stream()
                                            .reduce(component.getString(), (text, entry) -> text.replace(entry.getKey(), entry.getValue()), (a, b) -> b);
                                    return new TextComponent(modifiedText).withStyle(component.getStyle());
                                })
                                .filter(c -> c instanceof TextComponent) // Ensure it's a TextComponent
                                .map(c -> (TextComponent) c) // Cast to TextComponent
                                .collect(Collectors.toList()) // Collect modified components back into List<TextComponent>
                        )
                        .collect(Collectors.toList()) // Maintain the original List<List<TextComponent>> structure
        );

        return holder;
    }

    @Cancelable
    public static class BiomeRegistry extends RegistryEvents {
        public BiomeRegistry(final ServerInterface instance, final GUIFiller filler, final int slots) {
            super(instance, filler, slots);
            super.registry = new yorickbm.skyblockaddon.core.registries.BiomeRegistry(
                    FMLPaths.CONFIGDIR.get(),
                    ForgeRegistries.BIOMES.getValues()
                            .stream()
                            .map(biome -> Objects.requireNonNull(biome.getRegistryName()).toString())
                            .toList()
            );
            super.initRegistryPagination();

            if(instance.getData().contains("island_id")) {
                final Island island = IslandManager.getInstance().getIslandByUUID(instance.getData().getUUID("island_id"));
                if(island == null) return;
                super.getItemStackHolder().putData("island_biome", island.getBiome());
            }
        }
    }

    @Cancelable
    public static class IslandRegistry extends RegistryEvents {
        public IslandRegistry(final ServerInterface instance, final GUIFiller filler, final int slots) {
            super(instance, filler, slots);

            instance.getOwner().getLevel().getCapability(SkyblockAddonWorldProvider.SKYBLOCKADDON_WORLD_CAPABILITY).ifPresent(cap -> {
                super.registry = new yorickbm.skyblockaddon.core.registries.IslandRegistry(IslandManager.getInstance().getIslands().stream().toList());
                super.initRegistryPagination();
            });
        }
    }


    @Cancelable
    public static class GroupsRegistry extends RegistryEvents {
        public GroupsRegistry(final ServerInterface instance, final GUIFiller filler, final int slots) {
            super(instance, filler, slots);
            if(!instance.getData().contains("island_id"))  this.setCanceled(true); //No island ID found

            final Island island = IslandManager.getInstance().getIslandByUUID(instance.getData().getUUID("island_id"));
            if(island == null) return;

            super.registry = new yorickbm.skyblockaddon.core.registries.GroupsRegistry(island);
            super.initRegistryPagination();
        }
    }

        @Cancelable
    public static class PermissionsRegistry extends RegistryEvents {
        public PermissionsRegistry(final ServerInterface instance, final GUIFiller filler, final int slots) {
            super(instance, filler, slots);
            if(!instance.getData().contains("island_id"))  this.setCanceled(true); //No island ID found

            final Island island = IslandManager.getInstance().getIslandByUUID(instance.getData().getUUID("island_id"));
            if(island == null) return;

            super.registry = new yorickbm.skyblockaddon.core.registries.PermissionRegistry(island, instance.getData().getCompound(GUILibraryRegistry.MOD_ID).getString("category_id"), instance.getData().getUUID("group_id"));
            super.initRegistryPagination();
        }
    }

    @Cancelable
    public static class GroupMembersRegistry extends RegistryEvents {
        public GroupMembersRegistry(final ServerInterface instance, final GUIFiller filler, final int slots) {
            super(instance, filler, slots);
            if(!instance.getData().contains("island_id"))  this.setCanceled(true); //No island ID found

            final Island island = IslandManager.getInstance().getIslandByUUID(instance.getData().getUUID("island_id"));
            if(island == null) return;

            super.registry = new yorickbm.skyblockaddon.core.registries.GroupMemberRegistry(island, instance.getData().getUUID("group_id"));
            super.initRegistryPagination();
        }
    }

    @Cancelable
    public static class MembersRegistry extends RegistryEvents {
        public MembersRegistry(final ServerInterface instance, final GUIFiller filler, final int slots) {
            super(instance, filler, slots);
            if(!instance.getData().contains("island_id"))  this.setCanceled(true); //No island ID found

            final Island island = IslandManager.getInstance().getIslandByUUID(instance.getData().getUUID("island_id"));
            if(island == null) return;

            super.registry = new yorickbm.skyblockaddon.core.registries.MemberRegistry(island);
            super.initRegistryPagination();
        }
    }
}
