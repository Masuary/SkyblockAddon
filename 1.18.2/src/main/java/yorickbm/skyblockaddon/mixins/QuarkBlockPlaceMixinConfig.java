package yorickbm.skyblockaddon.mixins;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.quark.content.tweaks.module.ReacharoundPlacingModule;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.islands.InteractionHandler;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = {ReacharoundPlacingModule.class}, remap= false)
public abstract class QuarkBlockPlaceMixinConfig {
    /**
     * Inject at the start of Quark's reacharound target calculation.
     * You can cancel or modify placement here.
     */
    @Inject(method = "getPlayerReacharoundTarget", at = @At("HEAD"), cancellable = true, remap = false)
    public void onGetReacharoundTarget(final Player player, final CallbackInfoReturnable<BlockHitResult> cir) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if(InteractionHandler.verifyEntity(player, standingOn).asBoolean()) return;

        if(InteractionHandler.checkPlayerInteraction(standingOn, (ServerPlayer) player, (ServerLevel) player.getLevel(), player.getOnPos(), player.getMainHandItem(),  "onPlaceBlock")) {
            player.displayClientMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere")).withStyle(ChatFormatting.DARK_RED), true);
            cir.setReturnValue(null); //Force success
        }
    }
}
