package yorickbm.guilibrary.events;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import yorickbm.guilibrary.interfaces.GuiClickItemEvent;
import yorickbm.guilibrary.util.Helper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultEventHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void itemRenderer(final GuiDrawItemEvent event) {
        if(event.isCanceled()) {
            event.setResult(Event.Result.DENY);
            return; //Skip if canceled
        }

        //SkullTexture
        if(event.getItemStackHolder().getData().contains("SkullTexture")) {
            CompoundTag tag = event.getItemStackHolder().getData();
            CompoundTag skullOwnerTag = new CompoundTag();
            skullOwnerTag.putString("Name", tag.contains("SkullOwner") ? tag.getString("SkullOwner") : "Piggy");

            // Create Properties tag
            CompoundTag propertiesTag = new CompoundTag();
            ListTag texturesList = new ListTag();

            // Texture tag with Base64
            CompoundTag textureTag = new CompoundTag();
            for(String texture : tag.getString("SkullTexture").split(",")) {
                textureTag.putString("Value", texture);
                texturesList.add(textureTag);
            }

            propertiesTag.put("textures", texturesList);
            skullOwnerTag.put("Properties", propertiesTag);

            tag.put("SkullOwner", skullOwnerTag);
        }

        //Default dynamic text
        final List<TextComponent> name = event.getItemStackHolder().getDisplayName();
        final List<List<TextComponent>> lore = event.getItemStackHolder().getLore();

        final Map<String, String> replacements = new HashMap<>() {{
            put("%pagenum%", event.getHolder().getCurrentPage()+"");
            put("%maxpage%", event.getHolder().getMaxPage()+"");
        }};

        //Server variables
        final MinecraftServer server = event.getHolder().getOwner().getServer();
        if(server != null) {
            replacements.put("%server_name%", server.getMotd().trim());
            replacements.put("%server_ip%", server.getLocalIp().trim());
            replacements.put("%server_port%", server.getPort() + "");
            replacements.put("%max_players%", server.getMaxPlayers()+"");
            replacements.put("%current_players%", server.getPlayerCount()+"");

            replacements.put("%server_tps%", String.format("%.2f", server.getAverageTickTime()));
            replacements.put("%server_difficulty%", server.getWorldData().getDifficulty().toString());
            replacements.put("%server_mod_name%", server.getServerModName().trim());

            // Server Uptime Calculation (Compact)
            final long seconds = server.getTickCount() / 20;
            final long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            final long sec = seconds % 60;
            final String serverUptime = (hours > 0) ? String.format("%02dh %02dm %02ds", hours, minutes, sec) :
                    (minutes > 0) ? String.format("%02dm %02ds", minutes, sec) :
                            String.format("%02ds", sec);

            replacements.put("%server_uptime%", serverUptime);
        }

        event.getItemStackHolder().setDisplayName(name.stream()
                .map(component -> {
                    final String modifiedText = replacements.entrySet().stream()
                            .reduce(component.getString(), (text, entry) -> text.replace(entry.getKey(), entry.getValue()), (a, b) -> b);
                    return new TextComponent(modifiedText).withStyle(component.getStyle());
                })
                .filter(c -> c instanceof TextComponent) // Ensure it's a TextComponent
                .map(c -> (TextComponent) c) // Cast to TextComponent
                .collect(Collectors.toList()));

        event.getItemStackHolder().setLore(
                lore.stream()
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
        //End dynamic text

        event.drawItem(); //Add item into inventory
        event.setResult(Event.Result.ALLOW);
    }
    @SubscribeEvent
    public void dynamicTitle(final OpenMenuEvent event) {
        if(event.isCanceled()) return; //Skip if canceled

        final List<TextComponent> title = event.getTitle();
        final Map<String, String> replacements = new HashMap<>();
        final MinecraftServer server = event.getTarget().getServer();

        if(server != null) {
            replacements.put("%server_name%", server.getMotd().trim());
            replacements.put("%server_ip%", server.getLocalIp().trim());
            replacements.put("%server_port%", server.getPort() + "");
            replacements.put("%max_players%", server.getMaxPlayers()+"");
            replacements.put("%current_players%", server.getPlayerCount()+"");

            replacements.put("%server_tps%", String.format("%.2f", server.getAverageTickTime()));
            replacements.put("%server_difficulty%", server.getWorldData().getDifficulty().toString());
            replacements.put("%server_mod_name%", server.getServerModName().trim());

            // Server Uptime Calculation (Compact)
            final long seconds = server.getTickCount() / 20;
            final long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            final long sec = seconds % 60;
            final String serverUptime = (hours > 0) ? String.format("%02dh %02dm %02ds", hours, minutes, sec) :
                    (minutes > 0) ? String.format("%02dm %02ds", minutes, sec) :
                            String.format("%02ds", sec);

            replacements.put("%server_uptime%", serverUptime);
        }

        event.setTitle(title.stream()
                .map(component -> {
                    final String modifiedText = replacements.entrySet().stream()
                            .reduce(component.getString(), (text, entry) -> text.replace(entry.getKey(), entry.getValue()), (a, b) -> b);
                    return new TextComponent(modifiedText).withStyle(component.getStyle());
                })
                .filter(c -> c instanceof TextComponent) // Ensure it's a TextComponent
                .map(c -> (TextComponent) c) // Cast to TextComponent
                .collect(Collectors.toList()));
    }


    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void fillerRenderer(final GuiDrawFillerEvent event) {
        if(event.isCanceled()) return; //Skip if canceled

        switch(event.getPattern()) {
            case EDGES -> {
                for (int slot = 0; slot < event.getSlots(); slot++) {
                    if(slot >= 10 && slot <= event.getSlots() - 10  && slot%9 != 0 && slot%9 != 8) continue;
                    if(event.slotIsEmpty(slot)) {
                        event.drawItem(slot, event.getItemStackHolder().getItemStack());
                    }
                }
            }
            case EMPTY -> {
                for (int slot = 0; slot < event.getSlots(); slot++) {
                    if(event.slotIsEmpty(slot)) {
                        event.drawItem(slot, event.getItemStackHolder().getItemStack());
                    }
                }
            }
            case INSIDE -> {
                for (int slot = 0; slot < event.getSlots(); slot++) {
                    if((slot < 10 || slot > event.getSlots() - 10)  || (slot%9 == 0 || slot%9 == 8)) continue;
                    if(event.slotIsEmpty(slot)) {
                        event.drawItem(slot, event.getItemStackHolder().getItemStack());
                    }
                }
            }
        }

        event.setResult(Event.Result.ALLOW);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void openGui(final OpenMenuEvent event) {
        if(event.isCanceled()) return; //Skip if canceled
        event.setResult(event.getTarget().openMenu(event.getProvider()).isPresent() ? Event.Result.ALLOW : Event.Result.DENY);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void closeGui(final ItemCloseMenuEvent event) {
        if(event.isCanceled()) return; //Skip if canceled

        event.getHolder().close();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onNextPage(final ItemNextPageEvent event)
    {
        if(event.getHolder().nextPage()) event.getHolder().update();
        else event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPrevPage(final ItemPreviousPageEvent event)
    {
        if(event.getHolder().prevPage()) event.getHolder().update();
        else event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemClick(final GuiClickItemEvent event)
    {
        if(!event.getResult().equals(Event.Result.DENY)) {
            Helper.playSongToPlayer(event.getTarget(), SoundEvents.NOTE_BLOCK_CHIME, Helper.UI_SUCCESS_VOL, 1f); //Send success sound notification of click
        }
        else {
            Helper.playSongToPlayer(event.getTarget(), SoundEvents.NOTE_BLOCK_BASS, Helper.UI_SUCCESS_VOL, 1f); //Send failure sound notification of click
        }

    }
}
