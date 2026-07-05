package yorickbm.skyblockaddon.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.core.util.ThreadManager;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerHelper {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final GameProfile INTERACTION_PROBE_PROFILE = new GameProfile(
            UUID.fromString("c7fb8f7d-370d-46df-8fb0-d5f561a82ef2"),
            "[SkyblockAddon]"
    );
    private static final ConcurrentHashMap<UUID, UUID> spawnerTracker = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, UUID> terminatorTracker = new ConcurrentHashMap<>();

    public static void playSongToPlayer(final ServerPlayer player, final SoundEvent event, final float vol, final float pitch) {
        ServerHelper.SendPacket(player, new ClientboundSoundPacket(event, SoundSource.PLAYERS, player.position().x, player.position().y, player.position().z, vol, pitch));
    }

    public static void SendPacket(final ServerPlayer player, final Packet<?> packet) {
        player.connection.send(packet);
    }

    public static void showParticleToPlayer(final ServerPlayer player, final Vec3i location, final ParticleOptions particle, final int count) {
        ServerHelper.SendPacket(player, new ClientboundLevelParticlesPacket(particle, false, location.getX() + 0.5f, location.getY() + 0.5f, location.getZ() + 0.5f, 0.1f, 0f, 0.1f, 0f, count));
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
     * Checks if the block at the given position in the world is interactable.
     *
     * @param world the level/world
     * @param pos the block position clicked
     * @param player the player interacting (used for FakePlayer context)
     * @param hand the hand used for interaction (can be player.getUsedItemHand())
     * @return true if the block is interactable (has GUI/capabilities or overrides use)
     */
    public static boolean isBlockInteractable(final Level world, final BlockPos pos, final Player player, final InteractionHand hand, final BlockHitResult vector) {
        if (!(world instanceof ServerLevel serverLevel)) {
            return false;
        }
        final BlockState state = world.getBlockState(pos);
        final BlockEntity be = world.getBlockEntity(pos);

        // Blocks with no BlockEntity are usually pure placement targets (dirt, stone, etc.).
        // Treat sneak + held item against such a block as a placement attempt.
        // Mod blocks with a BE frequently implement sneak-interactions
        if (be == null && player.isShiftKeyDown() && !player.getItemInHand(hand).isEmpty()) {
            return false;
        }

        // Check common capabilities (inventory, energy, fluid)
        if (be != null) {
            if (be.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null).isPresent()) return true;
            if (be.getCapability(CapabilityEnergy.ENERGY, null).isPresent()) return true;
            if (be.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null).isPresent()) return true;
        }

        // Probe use() with a FakePlayer and remove side effects created by the probe.
        final FakePlayer fakePlayer = FakePlayerFactory.get(serverLevel, INTERACTION_PROBE_PROFILE);
        final AABB cleanupArea = new AABB(pos).inflate(1.0);
        final Set<UUID> entityIdsBefore = serverLevel.getEntities(
                (Entity) null,
                cleanupArea,
                entity -> !(entity instanceof Player)
        ).stream().map(Entity::getUUID).collect(java.util.stream.Collectors.toUnmodifiableSet());
        fakePlayer.getInventory().clearContent();
        fakePlayer.setItemInHand(hand, player.getItemInHand(hand).copy());
        fakePlayer.setShiftKeyDown(player.isShiftKeyDown());
        try {
            final InteractionResult result = state.use(world, fakePlayer, hand, vector);
            return result != InteractionResult.PASS;
        } catch (final RuntimeException exception) {
            LOGGER.warn(
                    "Block interaction probe failed for {} at {}; treating the block as interactable",
                    state.getBlock().getRegistryName(),
                    pos,
                    exception
            );
            return true;
        } finally {
            if (fakePlayer.getVehicle() != null) {
                fakePlayer.stopRiding();
            }
            fakePlayer.closeContainer();
            fakePlayer.getInventory().clearContent();
            fakePlayer.setShiftKeyDown(false);

            final List<Entity> entitiesAfter = serverLevel.getEntities(
                    (Entity) null,
                    cleanupArea,
                    entity -> !(entity instanceof Player)
            );
            for (final Entity entity : entitiesAfter) {
                if (!entityIdsBefore.contains(entity.getUUID())) {
                    entity.discard();
                }
            }
        }
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

    public static void registerIslandBorder(final ServerPlayer player, final List<Vec3i> points, final Vec3i location) {
        final UUID oldSpawner = spawnerTracker.remove(player.getUUID());
        if (oldSpawner != null) {
            final UUID oldTerminator = terminatorTracker.remove(oldSpawner);
            ThreadManager.terminateThread(oldSpawner);
            ThreadManager.terminateThread(oldTerminator);
        }

        //Setup threads for spawner and tracker
        final UUID particleSpawner = ThreadManager.startLoopingThread((id) -> {
            final var server = player.getServer();
            if (server == null) return;
            server.execute(() -> {
                if (player.isRemoved()) return;
                ServerHelper.showParticleToPlayer(player, location, ParticleTypes.CLOUD, 3);
                for (final Vec3i pos : points) {
                    ServerHelper.showParticleToPlayer(player, pos, ParticleTypes.CLOUD, 3);
                }
            });
        }, 500);
        final UUID terminator = ThreadManager.startThread((id) -> {
            try {
                Thread.sleep(1000 * 60 * 5);
                ThreadManager.terminateThread(particleSpawner);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                spawnerTracker.remove(player.getUUID(), particleSpawner);
                terminatorTracker.remove(particleSpawner, id);
            }
        });

        //Register threads to trackers
        spawnerTracker.put(player.getUUID(), particleSpawner);
        terminatorTracker.put(particleSpawner, terminator);
    }

}
