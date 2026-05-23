package yorickbm.skyblockaddon.mixins;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.quark.content.building.entity.GlassItemFrame;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.islands.InteractionHandler;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = {GlassItemFrame.class}, remap= false)
public abstract class QuarkGlassFrameMixinConfig {
    @Shadow public abstract BlockPos getBehindPos();

    @Inject(method = {"m_6096_"}, at = {@At("HEAD")}, cancellable = true)
    public void interact(final Player player, @Nonnull final InteractionHand hand, final CallbackInfoReturnable<InteractionResult> cir) {
        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if(InteractionHandler.verifyEntity(player, standingOn).asBoolean()) return;

        final ItemStack item = ((GlassItemFrame)(Object)this).getItem();
        if (!player.isShiftKeyDown() && !item.isEmpty() && !(item.getItem() instanceof BannerItem)) {

            BlockState state = player.getLevel().getBlockState(this.getBehindPos());
            final FakePlayer p = FakePlayerFactory.getMinecraft((ServerLevel) player.getLevel());

            if(state.use(
                    player.getLevel(),
                    p,
                    hand,
                    new BlockHitResult(
                        new Vec3(((GlassItemFrame)(Object)this).getX(), ((GlassItemFrame)(Object)this).getY(), ((GlassItemFrame)(Object)this).getZ()),
                            ((GlassItemFrame)(Object)this).getDirection(),
                            this.getBehindPos(),
                            true
                    )
            ) == InteractionResult.PASS) {
                return; //Not interactable container
            }
            p.closeContainer();
            p.kill();

            if(InteractionHandler.checkPlayerInteraction(standingOn, (ServerPlayer) player, (ServerLevel) player.getLevel(), this.getBehindPos(), item,  "onRightClickBlock")) {
                player.displayClientMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere")).withStyle(ChatFormatting.DARK_RED), true);
                cir.setReturnValue(InteractionResult.FAIL); //Force success
            }
        }
    }
}
