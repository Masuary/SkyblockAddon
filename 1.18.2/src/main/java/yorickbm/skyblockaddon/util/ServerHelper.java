package yorickbm.skyblockaddon.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

public class ServerHelper {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void playSongToPlayer(final ServerPlayer player, final SoundEvent event, final float vol, final float pitch) {
        ServerHelper.sendPacket(player, new ClientboundSoundPacket(event, SoundSource.PLAYERS, player.position().x, player.position().y, player.position().z, vol, pitch));
    }

    public static void sendPacket(final ServerPlayer player, final Packet<?> packet) {
        player.connection.send(packet);
    }

    public static void showParticleToPlayer(final ServerPlayer player, final Vec3i location, final ParticleOptions particle, final int count) {
        ServerHelper.sendPacket(player, new ClientboundLevelParticlesPacket(particle, false, location.getX() + 0.5f, location.getY() + 0.5f, location.getZ() + 0.5f, 0.1f, 0f, 0.1f, 0f, count));
    }

    /**
     * Applies custom skull texture to an ItemStack
     * @param itemStack The ItemStack (should be a player head)
     * @param skullTexture The base64 encoded texture string
     * @return The ItemStack with the applied texture
     */
    public static ItemStack applySkullTexture(ItemStack itemStack, String skullTexture) {
        if (itemStack.getItem() != Items.PLAYER_HEAD || skullTexture.length() < 10) {
            return itemStack;
        }

        // Create a GameProfile with random UUID
        GameProfile profile = new GameProfile(UUID.randomUUID(), "");

        // Add the texture property
        profile.getProperties().put("textures", new Property("textures", skullTexture));

        // Convert GameProfile to NBT
        CompoundTag profileTag = NbtUtils.writeGameProfile(new CompoundTag(), profile);

        // Add to ItemStack NBT
        itemStack.getOrCreateTag().put("SkullOwner", profileTag);

        return itemStack;
    }

    /**
     * Get item from ForgeRegistries.
     * If not found returns @param basic
     *
     * @return - Minecraft Registry Item
     */
    public static Item getItem(final String item, final Item basic) {
        try {
            final Item mcItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(item));
            return mcItem != null ? mcItem : basic;
        } catch (final Exception ex) {
            LOGGER.error("Failure to find item '{}';", item);
            LOGGER.error(ex);
            return basic;
        }
    }

}