package yorickbm.skyblockaddon.mixins;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.vsngamer.elevatorid.network.TeleportHandler;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.islands.InteractionHandler;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Mixin(value = TeleportHandler.class, remap = false)
public class ElevatoridMixinConfig {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onHandleTeleport(@Coerce Object request,
                                         Supplier<NetworkEvent.Context> contextSupplier,
                                         CallbackInfo ci) {
        final NetworkEvent.Context ctx = contextSupplier.get();
        final ServerPlayer player = ctx.getSender();
        if (player == null) return;

        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (InteractionHandler.verifyEntity(player, standingOn).asBoolean()) return;

        if (InteractionHandler.checkPlayerInteraction(standingOn, player,
                (ServerLevel) player.level, player.getOnPos(), ItemStack.EMPTY, "onElevatorUse")) {
            player.displayClientMessage(
                    new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere"))
                            .withStyle(ChatFormatting.DARK_RED), true);
            ci.cancel();
        }
    }
}
