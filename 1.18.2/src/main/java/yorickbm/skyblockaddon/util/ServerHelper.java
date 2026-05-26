package yorickbm.skyblockaddon.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class ServerHelper {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final HashMap<UUID, UUID> spawnerTracker = new HashMap<>();
    private static final HashMap<UUID, UUID> terminatorTracker = new HashMap<>();

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
        final BlockState state = world.getBlockState(pos);
        final BlockEntity be = world.getBlockEntity(pos);

        // Blocks with no BlockEntity are usually pure placement targets (dirt, stone, etc.).
        // Treat sneak + held item against such a block as a placement attempt.
        // Mod blocks with a BE frequently implement sneak-interactions (e.g. Vault Wardrobe
        // swap-on-sneak), so we deliberately do NOT short-circuit when a BE is present.
        if (be == null && player.isShiftKeyDown() && !player.getItemInHand(hand).isEmpty()) {
            return false;
        }

        // Check common capabilities (inventory, energy, fluid)
        if (be != null) {
            if (be.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null).isPresent()) return true;
            if (be.getCapability(CapabilityEnergy.ENERGY, null).isPresent()) return true;
            if (be.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null).isPresent()) return true;
        }

        // Use a FakePlayer to test the block's use() method.
        // FakePlayer.die() is a no-op in Forge, so we must manually clean up any side effects
        // (e.g. Create's SeatBlock.use() spawns a SeatEntity and mounts the FakePlayer on it).
        final FakePlayer fakePlayer = FakePlayerFactory.getMinecraft((ServerLevel) world);
        final ServerLevel serverLevel = (ServerLevel) world;
        final AABB cleanupArea = new AABB(pos).inflate(1.0);
        final List<Entity> entitiesBefore = serverLevel.getEntities((Entity) null, cleanupArea, e -> !(e instanceof Player));
        try {
            final InteractionResult result = state.use(world, fakePlayer, hand, vector);
            return result != InteractionResult.PASS;
        } catch (final Exception ignored) {
            return true;
        } finally {
            if (fakePlayer.getVehicle() != null) {
                fakePlayer.stopRiding();
            }
            fakePlayer.closeContainer();
            fakePlayer.kill();
            final List<Entity> entitiesAfter = serverLevel.getEntities((Entity) null, cleanupArea, e -> !(e instanceof Player));
            for (final Entity created : entitiesAfter) {
                if (!entitiesBefore.contains(created)) {
                    created.discard();
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
            final Item mcItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(item));
            return mcItem != null ? mcItem : basic;
        } catch (final Exception ex) {
            LOGGER.error("Failure to find item '{}';", item);
            LOGGER.error(ex);
            return basic;
        }
    }

    public static void registerIslandBorder(final ServerPlayer player, final List<Vec3i> points, final Vec3i location) {
        //Cleanup old threads
        if (spawnerTracker.containsKey(player.getUUID())) {
            final UUID oldSpawner = spawnerTracker.get(player.getUUID());
            final UUID oldTerminator = terminatorTracker.get(oldSpawner);

            ThreadManager.terminateThread(oldSpawner);
            ThreadManager.terminateThread(oldTerminator);
        }

        //Setup threads for spawner and tracker
        final UUID particleSpawner = ThreadManager.startLoopingThread((id) -> {
            ServerHelper.showParticleToPlayer(player, location, ParticleTypes.CLOUD, 3);
            for (final Vec3i pos : points) {
                ServerHelper.showParticleToPlayer(player, pos, ParticleTypes.CLOUD, 3);
            }
        }, 500);
        final UUID terminator = ThreadManager.startThread((id) -> {
            try {
                Thread.sleep(1000 * 60 * 5);

                //Terminate spawner thread
                ThreadManager.terminateThread(particleSpawner);

                //Cleanup trackers
                spawnerTracker.remove(player.getUUID());
                terminatorTracker.remove(id);
            } catch (final InterruptedException e) {
                //Nothing here
            }
        });

        //Register threads to trackers
        spawnerTracker.put(player.getUUID(), particleSpawner);
        terminatorTracker.put(particleSpawner, terminator);
    }

    /**
     * Forces a button to unpower, or toggles a lever at the given position.
     * Fully updates redstone in all directions.
     */
    public static void forceUnpowerOrTogglePoweredBlock(final ServerLevel level, final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        final Block block = state.getBlock();

        final BooleanProperty poweredProp = BlockStateProperties.POWERED;

        if (state.hasProperty(poweredProp)) {
            final boolean powered = state.getValue(poweredProp);

            if (block.getClass().getSimpleName().toLowerCase().contains("button")) {
                // Always force OFF for buttons
                final BlockState newState = state.setValue(poweredProp, false);
                level.setBlock(pos, newState, 3);
                updateAllNeighbors(level, pos, block);
                level.scheduleTick(pos, block, 1);
            } else {
                final BlockState newState = state.setValue(poweredProp, !powered);
                level.setBlock(pos, newState, 3);
                updateAllNeighbors(level, pos, block);
            }
        }
    }

    /**
     * Updates redstone and neighbor signals in all directions around a block.
     */
    private static void updateAllNeighbors(final ServerLevel level, final BlockPos pos, final Block block) {
        for (final Direction dir : Direction.values()) {
            final BlockPos neighborPos = pos.relative(dir);
            level.updateNeighborsAt(neighborPos, block);
            level.updateNeighbourForOutputSignal(neighborPos, block);
        }
    }

}