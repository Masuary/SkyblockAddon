package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.Button;
import com.masuary.masugui.element.Label;
import com.masuary.masugui.element.Panel;
import com.masuary.masugui.fallback.FallbackType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import yorickbm.skyblockaddon.core.configs.SkyBlockAddonLanguage;
import yorickbm.skyblockaddon.core.islands.IslandManager;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;
import yorickbm.skyblockaddon.islands.ForgeIsland;

public final class ConfirmSetSpawnGui {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 80;

    private ConfirmSetSpawnGui() {}

    public static void open(ServerPlayer player, CompoundTag data) {
        if (!data.contains("island_id")) return;

        ForgeIsland island = (ForgeIsland) IslandManager.getInstance()
                .getIslandByUUID(data.getUUID("island_id"));
        if (island == null) return;

        int x = player.blockPosition().getX();
        int y = player.blockPosition().getY();
        int z = player.blockPosition().getZ();

        MasuGui gui = MasuGui.create("confirm_setspawn")
                .title(new TextComponent("Set Spawn"))
                .size(WIDTH, HEIGHT)
                .fallbackType(FallbackType.CHEST_1);

        gui.add(new Panel("bg", 0, 0, WIDTH, HEIGHT)
                .color(0xE8181818).border(0xFF335533));

        gui.add(new Label("title", WIDTH / 2, 8)
                .text(new TextComponent("Set Island Spawn?").withStyle(ChatFormatting.GREEN))
                .centered().scale(1.0f).shadow(true));

        gui.add(new Label("message", WIDTH / 2, 24)
                .text(new TextComponent("Set spawn to your current location:"))
                .color(0xFFCCCCCC).centered().scale(0.7f));

        gui.add(new Label("coords", WIDTH / 2, 34)
                .text(new TextComponent(x + ", " + y + ", " + z).withStyle(ChatFormatting.AQUA))
                .centered().scale(0.7f).shadow(true));

        gui.add(new Button("confirm_btn", WIDTH / 2 - 72, HEIGHT - 26, 64, 16)
                .label(new TextComponent("Confirm")).backgroundColor(0xFF33AA33).flat()
                .onClick(p -> {
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
                    MasuGui.closeFor(p);
                    IslandHubGui.open(p, data);
                }));

        gui.add(new Button("cancel_btn", WIDTH / 2 + 8, HEIGHT - 26, 64, 16)
                .label(new TextComponent("Cancel")).backgroundColor(0xFF383838).flat()
                .onClick(p -> IslandHubGui.open(p, data)));

        gui.openFor(player);
    }
}
