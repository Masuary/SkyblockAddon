package yorickbm.skyblockaddon.mixins;

import com.hollingsworth.arsnouveau.common.block.tile.PortalTile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.islands.InteractionHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = PortalTile.class, remap = false)
public abstract class ArsNouveauPortalMixinConfig {
    // PortalTile teleports players directly from its entity query, bypassing Block.use
    // and the normal portal block hooks. Filtering this query is the narrowest safe hook.
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",
                    remap = false
            )
    )
    private List<Entity> skyblockaddon$filterDeniedPlayers(
            final Level level,
            final Class<Entity> entityClass,
            final AABB bounds
    ) {
        final List<Entity> entities = level.getEntitiesOfClass(entityClass, bounds);
        if (!(level instanceof ServerLevel serverLevel)) return entities;

        final BlockPos portalPosition = ((PortalTile) (Object) this).getBlockPos();
        final List<Entity> allowedEntities = new ArrayList<>(entities.size());
        for (final Entity entity : entities) {
            if (!(entity instanceof ServerPlayer player)) {
                allowedEntities.add(entity);
                continue;
            }

            final AtomicReference<Island> island = new AtomicReference<>();
            if (InteractionHandler.verifyEntity(player, island).asBoolean()
                    || !InteractionHandler.checkPlayerInteraction(
                            island,
                            player,
                            serverLevel,
                            portalPosition,
                            ItemStack.EMPTY,
                            "onEnterPortal"
                    )) {
                allowedEntities.add(entity);
            }
        }
        return allowedEntities;
    }
}
