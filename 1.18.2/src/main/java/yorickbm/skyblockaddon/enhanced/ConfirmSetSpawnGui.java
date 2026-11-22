package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.ButtonStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import yorickbm.skyblockaddon.core.GuiDataKeys;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.islands.SpawnPointPlacement;
import yorickbm.skyblockaddon.islands.ForgeIsland;
import yorickbm.skyblockaddon.islands.IslandActionService;
import yorickbm.skyblockaddon.util.ForgeConverter;

import java.util.List;

public final class ConfirmSetSpawnGui {

    private ConfirmSetSpawnGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains(GuiDataKeys.ISLAND_ID)) return;

        ForgeIsland island = (ForgeIsland) IslandManager.getInstance()
                .getIslandByUUID(data.getUUID(GuiDataKeys.ISLAND_ID));
        if (island == null) return;
        if (closeUnlessAllowedToSetSpawn(player, island)) return;

        int x = player.blockPosition().getX();
        int y = player.blockPosition().getY();
        int z = player.blockPosition().getZ();
        EnhancedDialog.open(player, "confirm_setspawn", "Set island spawn?", new ItemStack(Items.LODESTONE),
                List.of(new TextComponent("Move the island spawn here?"),
                        new TextComponent(x + ", " + y + ", " + z).withStyle(ChatFormatting.AQUA),
                        new TextComponent("Visitors arrive here too.").withStyle(ChatFormatting.GRAY)),
                "Set spawn", ButtonStyle.PRIMARY,
                p -> {
                    if (closeUnlessAllowedToSetSpawn(p, island)) return;
                    SpawnPointPlacement placement = IslandActionService.checkSpawnPlacement(island, p);
                    if (!placement.allowed()) {
                        p.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString(placement.languageKey()))
                                .withStyle(ChatFormatting.RED), p.getUUID());
                        MasuGui.closeFor(p);
                        return;
                    }
                    island.setSpawnPoint(ForgeConverter.forgeToInternalVec3i(p.blockPosition()));
                    p.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("island.setspawn.success"))
                            .withStyle(ChatFormatting.GREEN), p.getUUID());
                    IslandHubGui.open(p, data);
                },
                p -> IslandHubGui.open(p, data));
    }

    private static boolean closeUnlessAllowedToSetSpawn(ServerPlayer player, ForgeIsland island) {
        if (IslandActionService.canSetSpawn(island, player)) return false;
        MasuGui.closeFor(player);
        player.sendMessage(new TextComponent(SkyBlockAddonLanguage.getLocalizedString("island.setspawn.not.allowed"))
                .withStyle(ChatFormatting.RED), player.getUUID());
        return true;
    }
}
