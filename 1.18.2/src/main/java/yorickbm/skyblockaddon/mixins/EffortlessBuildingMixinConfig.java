package yorickbm.skyblockaddon.mixins;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import nl.requios.effortlessbuilding.helper.SurvivalHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.islands.InteractionHandler;
import yorickbm.skyblockaddon.util.ForgeConverter;
import yorickbm.skyblockaddon.util.ServerHelper;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = SurvivalHelper.class, remap = false)
public abstract class EffortlessBuildingMixinConfig {
    private static final Logger LOGGER = LogManager.getLogger("SkyblockAddon-EB");

    @Inject(method = "placeBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onPlaceBlock(Level world, Player player, BlockPos pos, BlockState blockState,
                                     ItemStack origstack, Direction facing, Vec3 hitVec,
                                     boolean skipPlaceCheck, boolean skipCollisionCheck, boolean playSound,
                                     CallbackInfoReturnable<Boolean> cir) {
        LOGGER.info("EB SurvivalHelper.placeBlock mixin fired at {} by {}", pos, player.getName().getString());
        if (player instanceof FakePlayer) return;
        if (!(player instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel serverLevel)) return;
        if (serverPlayer.hasPermissions(Commands.LEVEL_ADMINS)) return;

        final Island targetIsland = IslandManager.getInstance().getIslandByPos(ForgeConverter.ForgeToInternalVec3i(pos));
        if (targetIsland == null) return;
        if (targetIsland.isOwner(serverPlayer.getUUID())) return;

        final AtomicReference<Island> standingOn = new AtomicReference<>(targetIsland);
        if (InteractionHandler.checkPlayerInteraction(standingOn, serverPlayer, serverLevel, pos, origstack, "onPlaceBlock")) {
            serverPlayer.displayClientMessage(
                    new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere"))
                            .withStyle(ChatFormatting.DARK_RED), true);
            ServerHelper.SendPacket(serverPlayer, new ClientboundBlockUpdatePacket(pos, serverLevel.getBlockState(pos)));
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "breakBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onBreakBlock(Level world, Player player, BlockPos pos, boolean skipChecks,
                                     CallbackInfoReturnable<Boolean> cir) {
        LOGGER.info("EB SurvivalHelper.breakBlock mixin fired at {} by {}", pos, player.getName().getString());
        if (player instanceof FakePlayer) return;
        if (!(player instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel serverLevel)) return;
        if (serverPlayer.hasPermissions(Commands.LEVEL_ADMINS)) return;

        final Island targetIsland = IslandManager.getInstance().getIslandByPos(ForgeConverter.ForgeToInternalVec3i(pos));
        if (targetIsland == null) return;
        if (targetIsland.isOwner(serverPlayer.getUUID())) return;

        final AtomicReference<Island> standingOn = new AtomicReference<>(targetIsland);
        if (InteractionHandler.checkPlayerInteraction(standingOn, serverPlayer, serverLevel, pos, serverPlayer.getMainHandItem(), "onLeftClickBlock")) {
            serverPlayer.displayClientMessage(
                    new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere"))
                            .withStyle(ChatFormatting.DARK_RED), true);
            ServerHelper.SendPacket(serverPlayer, new ClientboundBlockUpdatePacket(pos, serverLevel.getBlockState(pos)));
            cir.setReturnValue(false);
        }
    }
}
