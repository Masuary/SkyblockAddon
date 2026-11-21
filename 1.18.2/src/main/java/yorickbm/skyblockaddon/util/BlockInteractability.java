package yorickbm.skyblockaddon.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yorickbm.skyblockaddon.configs.ConfiguredIdListValue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * Decides whether a right-click on a block is an interaction with the block (checked as onRightClickBlock) or a
 * placement against it (checked as onPlaceBlock), without running any of the block's code.
 */
public final class BlockInteractability {
    public static final String INTERACTABLE_BLOCKS_CONFIG_KEY = "permissions.interactable_blocks";
    public static final String NON_INTERACTABLE_BLOCKS_CONFIG_KEY = "permissions.non_interactable_blocks";

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Set<String> USE_METHOD_NAMES = Set.of("use", "m_6227_");
    private static final Class<?>[] USE_PARAMETER_TYPES = {
            BlockState.class, Level.class, BlockPos.class, Player.class, InteractionHand.class, BlockHitResult.class
    };

    private static final ClassValue<Boolean> DECLARES_OWN_USE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(final Class<?> blockClass) {
            return classOrModdedSuperclassDeclaresUse(blockClass);
        }
    };

    private static final ConfiguredIdListValue INTERACTABLE_BLOCKS = new ConfiguredIdListValue(INTERACTABLE_BLOCKS_CONFIG_KEY);
    private static final ConfiguredIdListValue NON_INTERACTABLE_BLOCKS = new ConfiguredIdListValue(NON_INTERACTABLE_BLOCKS_CONFIG_KEY);

    private BlockInteractability() {
    }

    /**
     * Right-click by a player: the configured block lists decide first, then a sneaking player holding an item against a
     * block without a block entity counts as placing (vanilla skips use() then; blocks with a block entity often have
     * their own sneak interaction, such as the Vault Wardrobe swap), then {@link #isInteractable(Level, BlockPos)}.
     */
    public static boolean isRightClickInteraction(final Level level, final BlockPos position, final Player player, final InteractionHand hand) {
        final BlockState state = level.getBlockState(position);
        final String blockId = blockId(state.getBlock());
        if (INTERACTABLE_BLOCKS.ids().contains(blockId)) return true;
        if (NON_INTERACTABLE_BLOCKS.ids().contains(blockId)) return false;

        final BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity == null && player.isShiftKeyDown() && !player.getItemInHand(hand).isEmpty()) return false;

        return hasInteractableTraits(level, position, state, blockEntity);
    }

    public static boolean isInteractable(final Level level, final BlockPos position) {
        final BlockState state = level.getBlockState(position);
        final String blockId = blockId(state.getBlock());
        if (INTERACTABLE_BLOCKS.ids().contains(blockId)) return true;
        if (NON_INTERACTABLE_BLOCKS.ids().contains(blockId)) return false;

        return hasInteractableTraits(level, position, state, level.getBlockEntity(position));
    }

    private static boolean hasInteractableTraits(final Level level, final BlockPos position, final BlockState state, final BlockEntity blockEntity) {
        if (blockEntity != null && exposesStorageCapability(blockEntity)) return true;
        if (state.getMenuProvider(level, position) != null) return true;
        return DECLARES_OWN_USE.get(state.getBlock().getClass());
    }

    private static boolean exposesStorageCapability(final BlockEntity blockEntity) {
        return blockEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null).isPresent()
                || blockEntity.getCapability(CapabilityEnergy.ENERGY, null).isPresent()
                || blockEntity.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null).isPresent();
    }

    private static boolean classOrModdedSuperclassDeclaresUse(final Class<?> blockClass) {
        for (Class<?> current = blockClass; current != null && current != Block.class && current != BlockBehaviour.class; current = current.getSuperclass()) {
            if (declaresUse(current)) return true;
        }
        return false;
    }

    private static boolean declaresUse(final Class<?> type) {
        final Method[] declaredMethods;
        try {
            declaredMethods = type.getDeclaredMethods();
        } catch (final LinkageError unresolvableSignature) {
            LOGGER.warn("Cannot inspect block class {} ({}); treating its blocks as interactable", type.getName(), unresolvableSignature.toString());
            return true;
        }
        for (final Method method : declaredMethods) {
            if (USE_METHOD_NAMES.contains(method.getName()) && Arrays.equals(method.getParameterTypes(), USE_PARAMETER_TYPES)) {
                return true;
            }
        }
        return false;
    }

    private static String blockId(final Block block) {
        return Objects.requireNonNull(block.getRegistryName(), "Unregistered block " + block).toString();
    }

}
