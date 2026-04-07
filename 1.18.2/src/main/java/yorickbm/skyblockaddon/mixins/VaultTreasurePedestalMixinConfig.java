package yorickbm.skyblockaddon.mixins;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import iskallia.vault.block.TreasurePedestalBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.islands.InteractionHandler;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = {TreasurePedestalBlock.class}, remap = false)
public abstract class VaultTreasurePedestalMixinConfig {

    @Inject(method = "m_6227_", at = @At("HEAD"), cancellable = true)
    public void onUse(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                      BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof FakePlayer) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (InteractionHandler.verifyEntity(player, standingOn).asBoolean()) {
            return;
        }

        if (InteractionHandler.checkPlayerInteraction(standingOn, serverPlayer, serverLevel, pos,
                player.getItemInHand(hand), "onRightClickBlock")) {
            player.displayClientMessage(
                    new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere"))
                            .withStyle(ChatFormatting.DARK_RED), true);
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
