package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.ButtonStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;
import yorickbm.skyblockaddon.islands.ForgeIsland;

import java.util.List;

public final class ConfirmSetSpawnGui {

    private ConfirmSetSpawnGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains("island_id")) return;

        ForgeIsland island = (ForgeIsland) IslandManager.getInstance()
                .getIslandByUUID(data.getUUID("island_id"));
        if (island == null) return;

        int x = player.blockPosition().getX();
        int y = player.blockPosition().getY();
        int z = player.blockPosition().getZ();
        EnhancedDialog.open(player, "confirm_setspawn", "Set island spawn?", new ItemStack(Items.LODESTONE),
                List.of(new TextComponent("Move the island spawn here?"),
                        new TextComponent(x + ", " + y + ", " + z).withStyle(ChatFormatting.AQUA),
                        new TextComponent("Visitors arrive here too.").withStyle(ChatFormatting.GRAY)),
                "Set spawn", ButtonStyle.PRIMARY,
                p -> {
                    Vec3i position = new Vec3i(p.blockPosition().getX(), p.blockPosition().getY(), p.blockPosition().getZ());
                    if (!island.getIslandBoundingBox().isInside(position)) {
                        p.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("island.setspawn.outside"))
                                .withStyle(ChatFormatting.RED), p.getUUID());
                        MasuGui.closeFor(p);
                        return;
                    }
                    island.setSpawnPoint(position);
                    p.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("island.setspawn.success"))
                            .withStyle(ChatFormatting.GREEN), p.getUUID());
                    IslandHubGui.open(p, data);
                },
                p -> IslandHubGui.open(p, data));
    }
}
