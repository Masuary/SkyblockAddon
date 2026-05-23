package yorickbm.skyblockaddon.events.Gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.guilibrary.GUIItemStackHolder;
import yorickbm.skyblockaddon.components.ItemStackComponent;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.permissions.Permission;
import yorickbm.skyblockaddon.core.registries.GroupsRegistry;
import yorickbm.skyblockaddon.core.registries.IslandRegistry;
import yorickbm.skyblockaddon.core.registries.PermissionRegistry;
import yorickbm.skyblockaddon.events.RegistryEvents;
import yorickbm.skyblockaddon.islands.ForgeIslandGroup;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.Optional;
import java.util.UUID;

public class RegistryGuiEvents {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public void onBiomeRegistryFiller(final RegistryEvents.BiomeRegistry event) {
        if(event.isCanceled()) return; //Event is canceled
        event.setCanceled(true);

        for (int slot = 0; slot < event.getSlots(); slot++) {
            if((slot < 10 || slot > event.getSlots() - 10)  || (slot%9 == 0 || slot%9 == 8)) continue;
            if(!event.getRegistry().hasNext()) break;

            ItemStackComponent component = new ItemStackComponent();
            event.getRegistry().getNextData(component);

            final GUIItemStackHolder holder = event.processHolder(event.getItemStackHolder().clone(), component.getCompound());
            String minecraftItem = ((yorickbm.skyblockaddon.core.registries.BiomeRegistry) event.getRegistry()).getDataForComponent(component).get();
            holder.setItem(ForgeRegistries.ITEMS.getValue(new ResourceLocation(minecraftItem)));

            final ItemStack stack = holder.getItemStack();
            stack.getOrCreateTag().put(SkyblockAddonCore.MOD_ID, component.getCompound());

            event.drawItem(slot, stack);
        }
    }

    @SubscribeEvent
    public void onGroupRegistryFiller(final RegistryEvents.GroupsRegistry event) {
        if(event.isCanceled()) return; //Event is canceled
        event.setCanceled(true);

        for (int slot = 0; slot < event.getSlots(); slot++) {
            if((slot < 10 || slot > event.getSlots() - 10)  || (slot%9 == 0 || slot%9 == 8)) continue;
            if(!event.getRegistry().hasNext()) break;

            ItemStackComponent component = new ItemStackComponent();
            event.getRegistry().getNextData(component);

            Optional<IslandGroup> group = ((GroupsRegistry)event.getRegistry()).getDataForComponent(component);
            ForgeIslandGroup forgeGroup = (ForgeIslandGroup)group.get();

            ItemStack stack = forgeGroup.getItem();
            if(stack == null) stack = event.processHolder(event.getItemStackHolder().clone(), component.getCompound()).getItemStack();

            event.drawItem(slot, stack);
        }
    }

    @SubscribeEvent
    public void onPermissionRegistryFiller(final RegistryEvents.PermissionsRegistry event) {
        if(event.isCanceled()) return; //Event is canceled
        event.setCanceled(true);

        PermissionRegistry registry = (PermissionRegistry) event.getRegistry();


        for (int slot = 0; slot < event.getSlots(); slot++) {
            if((slot < 10 || slot > event.getSlots() - 10)  || (slot%9 == 0 || slot%9 == 8)) continue;
            if(!event.getRegistry().hasNext()) break;

            ItemStackComponent component = new ItemStackComponent();
            registry.getNextData(component);

            GUIItemStackHolder stack = event.getItemStackHolder().clone();
            Optional<Permission> permission = registry.getDataForComponent(component);
            CompoundTag data = component.getCompound();

            if(permission.isPresent()) {
                data.putString("permission_id", permission.get().getId());
                data.putString("group_name", registry.getGroup().getName());
                data.putString("status",
                        registry.getGroup().canDo(permission.get().getId()) ?
                                SkyBlockAddonLanguage.getLocalizedString("island.permission.enabled")
                        :
                                SkyBlockAddonLanguage.getLocalizedString("island.permission.disabled"));
                stack = ForgeConverter.JSONItemToGUIItemStackHolder(permission.get().getItem());
            }

            ItemStack item = event.processHolder(stack, data.copy()).getItemStack();
            item.getOrCreateTag().put(SkyblockAddonCore.MOD_ID, data.copy());

            event.drawItem(slot, item);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onIslandRegistryFiller(final RegistryEvents.IslandRegistry event) {
        if(event.isCanceled()) return; //Event is canceled
        event.setCanceled(true);

        IslandRegistry registry = (IslandRegistry) event.getRegistry();

        for (int slot = 0; slot < event.getSlots(); slot++) {
            if((slot < 10 || slot > event.getSlots() - 10)  || (slot%9 == 0 || slot%9 == 8)) continue;
            if(!event.getRegistry().hasNext()) break;

            ItemStackComponent component = new ItemStackComponent();
            registry.getNextData(component);

            final GUIItemStackHolder holder = event.processHolder(event.getItemStackHolder().clone(), component.getCompound());
            final ItemStack stack = holder.getItemStack();
            stack.getOrCreateTag().put(SkyblockAddonCore.MOD_ID, component.getCompound());

            String skullTexture = component.getObject("skull_texture", String.class);
            ServerHelper.applySkullTexture(stack, skullTexture);

            event.drawItem(slot, stack);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDefaultRegistryFiller(final RegistryEvents event) {
        if(event.isCanceled()) return; //Event is canceled
        event.setCanceled(true);

        for (int slot = 0; slot < event.getSlots(); slot++) {
            if((slot < 10 || slot > event.getSlots() - 10)  || (slot%9 == 0 || slot%9 == 8)) continue;
            if(!event.getRegistry().hasNext()) break;

            ItemStackComponent component = new ItemStackComponent();
            event.getRegistry().getNextData(component);

            final GUIItemStackHolder holder = event.processHolder(event.getItemStackHolder().clone(), component.getCompound());
            final ItemStack stack = holder.getItemStack();
            stack.getOrCreateTag().put(SkyblockAddonCore.MOD_ID, component.getCompound());

            event.drawItem(slot, stack);
        }
    }
}
