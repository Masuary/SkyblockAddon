package yorickbm.skyblockaddon.mixins;

import de.maxhenkel.easypiglins.blocks.BartererBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.islands.InteractionHandler;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = BartererBlock.class, remap = false)
public class EasyPiglinsBlockMixinConfig {

    @Inject(method = "m_6227_", at = @At("HEAD"), cancellable = true, remap = false)
    private void use(BlockState state, Level level, BlockPos pos, Player player,
                     InteractionHand hand, BlockHitResult hit,
                     CallbackInfoReturnable<InteractionResult> cir) {
        if (!(player instanceof final ServerPlayer serverPlayer)) return;

        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (InteractionHandler.verifyEntity(serverPlayer, standingOn).asBoolean()) return;

        if (InteractionHandler.checkPlayerInteraction(standingOn, serverPlayer,
                (ServerLevel) level, pos, player.getItemInHand(hand), "onEPBlockInteract")) {
            // Immediately resync block entity to client to undo client-side visual prediction
            final BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                final var packet = be.getUpdatePacket();
                if (packet != null) serverPlayer.connection.send(packet);
            }
            serverPlayer.displayClientMessage(
                    new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere"))
                            .withStyle(ChatFormatting.DARK_RED), true);
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
