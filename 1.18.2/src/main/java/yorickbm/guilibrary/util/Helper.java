package yorickbm.guilibrary.util;


import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.Arrays;

public class Helper {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final float UI_SOUND_VOL = 0.5f;
    public static final float UI_SUCCESS_VOL = 3f;
    public static final float EFFECT_SOUND_VOL = 0.2f;

    /**
     * Get item from ForgeRegistries.
     * If not found returns @param basic
     *
     * @return - Minecraft Registry Item
     */
    public static Item getItem(final String item, final Item basic) {
        try {
            final Item mcItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(item));
            return mcItem != null ? mcItem : basic;
        } catch (final Exception ex) {
            LOGGER.error("Failure to find item '{}';", item);
            LOGGER.error(ex);
            return basic;
        }
    }

    /**
     * Add components as lore to stack
     * @param stack - Itemstack to add too
     * @param components - Text Components to add as lore
     */
    public static void addLore(final ItemStack stack, final TextComponent... components) {
        final ListTag lore = new ListTag();
        Arrays.stream(components).toList().forEach(text -> lore.add(StringTag.valueOf(Component.Serializer.toJson(text))));
        stack.getOrCreateTagElement("display").put("Lore", lore);
    }

    /**
     * Send sound packet to client, to play a specific sound for target
     * @param player - Target to play sound for
     * @param event - Type of sound
     * @param vol - Sound volume
     * @param pitch - Sound pitch
     */
    public static void playSongToPlayer(final ServerPlayer player, final SoundEvent event, final float vol, final float pitch) {
        ServerHelper.SendPacket(player, new ClientboundSoundPacket(event, SoundSource.PLAYERS, player.position().x, player.position().y, player.position().z, vol, pitch));
    }
}
