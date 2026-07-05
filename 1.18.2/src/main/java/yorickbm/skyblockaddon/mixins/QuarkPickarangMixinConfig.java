package yorickbm.skyblockaddon.mixins;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.quark.content.tools.item.PickarangItem;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.islands.InteractionHandler;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = {PickarangItem.class}, remap = false)
public abstract class QuarkPickarangMixinConfig {

    @Inject(method = {"m_7203_"}, at = {@At("HEAD")}, cancellable = true)
    private void use(final Level worldIn, final Player playerIn, @NotNull final InteractionHand handIn, final CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!(playerIn instanceof ServerPlayer serverPlayer) || !(worldIn instanceof ServerLevel serverLevel)) {
            return;
        }

        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if(InteractionHandler.verifyEntity(playerIn, standingOn).asBoolean()) return;

        final ItemStack handItem = playerIn.getItemInHand(handIn);
        if(InteractionHandler.checkPlayerInteraction(standingOn, serverPlayer, serverLevel, playerIn.getOnPos(), handItem, "onQuarkPickarangMixin")) {
            playerIn.displayClientMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere")).withStyle(ChatFormatting.DARK_RED), true);
            serverPlayer.containerMenu.broadcastFullState();
            cir.setReturnValue(InteractionResultHolder.success(handItem));
        }
    }
}
