package yorickbm.skyblockaddon.mixins;

import de.maxhenkel.easypiglins.events.PiglinEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.InteractionValidator;
import yorickbm.skyblockaddon.core.islands.Island;
import yorickbm.skyblockaddon.core.islands.IslandGroup;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;
import yorickbm.skyblockaddon.islands.InteractionHandler;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(value = PiglinEvents.class, remap = false)
public class EasyPiglinsPickupMixinConfig {

    @Inject(method = "onClick", at = @At("HEAD"), cancellable = true, remap = false)
    private void onClick(PlayerInteractEvent.EntityInteract event, CallbackInfo ci) {
        // Mirror EP's own filter: only process actual Piglin entities
        if (!(event.getTarget() instanceof Piglin)) return;
        if (!(event.getPlayer() instanceof final ServerPlayer player)) return;

        final AtomicReference<Island> standingOn = new AtomicReference<>();
        if (InteractionHandler.verifyEntity(player, standingOn).asBoolean()) return;

        final Optional<IslandGroup> group = standingOn.get().getGroupForEntityUUID(player.getUUID());
        if (group.isEmpty()) {
            player.displayClientMessage(
                    new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere"))
                            .withStyle(ChatFormatting.DARK_RED), true);
            ci.cancel();
            return;
        }

        final boolean blocked = InteractionValidator.checkPermission(
                group.get(),
                PermissionManager.getInstance().getPermissionsForTrigger("onPickupPiglin"),
                Map.of("entity", "minecraft:piglin"));

        if (blocked) {
            player.displayClientMessage(
                    new TextComponent(SkyBlockAddonLanguage.getLocalizedString("toolbar.overlay.nothere"))
                            .withStyle(ChatFormatting.DARK_RED), true);
            ci.cancel();
        }
    }
}
