package yorickbm.skyblockaddon.mixins;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yorickbm.skyblockaddon.islands.MixinPermissionChecks;

/**
 * Seats keep the mount rule; every other contraption block is judged like the same block placed in the world.
 */
@Mixin(value = AbstractContraptionEntity.class, remap = false)
public abstract class CreateContraptionMixinConfig {

    @Inject(method = "handlePlayerInteraction", at = @At("HEAD"), cancellable = true)
    public void denyContraptionBlockClick(final Player player, final BlockPos localPosition, final Direction side, final InteractionHand hand, final CallbackInfoReturnable<Boolean> callback) {
        final AbstractContraptionEntity contraptionEntity = (AbstractContraptionEntity) (Object) this;
        if (contraptionEntity.level.isClientSide) return;

        final Contraption contraption = contraptionEntity.getContraption();
        if (contraption == null || contraption.getSeats().contains(localPosition)) return;
        final StructureTemplate.StructureBlockInfo contraptionBlock = contraption.getBlocks().get(localPosition);
        if (contraptionBlock == null) return;

        final BlockPos worldPosition = new BlockPos(contraptionEntity.toGlobalVector(Vec3.atCenterOf(localPosition), 1.0F));
        if (MixinPermissionChecks.denyContraptionBlockClick(player, contraptionEntity.level, worldPosition, contraptionBlock.state, player.getItemInHand(hand))) {
            callback.setReturnValue(false);
        }
    }
}
