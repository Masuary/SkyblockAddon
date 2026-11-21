package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.Button;
import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.ItemDisplay;
import com.masuary.masugui.element.Label;
import com.masuary.masugui.element.Window;
import com.masuary.masugui.fallback.FallbackType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** The confirm dialog shared by the island screens: icon, consequence lines, Cancel and a confirm button. */
final class EnhancedDialog {

    static final int ACCENT = 0xFF55DD99;
    private static final int WIDTH = 240;
    private static final int LINE_HEIGHT = 11;
    private static final int ACTION_BUTTON_WIDTH = 60;

    private EnhancedDialog() {
    }

    /** One button of a dialog. */
    record Action(String label, ButtonStyle style, Consumer<ServerPlayer> run) {
    }

    /** {@code onConfirm} is null when the action is not possible right now; the confirm button is then disabled. */
    static void open(ServerPlayer player, String guiId, String title, ItemStack icon, List<Component> lines,
                     String confirmLabel, ButtonStyle confirmStyle, Consumer<ServerPlayer> onConfirm, Consumer<ServerPlayer> onCancel) {
        int height = heightFor(lines);
        MasuGui gui = frame(guiId, title, icon, lines, height);
        gui.add(new Button("cancel", WIDTH - 156, height - 24, 54, 15).style(ButtonStyle.SECONDARY)
                .label(new TextComponent("Cancel")).onClick(onCancel::accept).fallbackSlot(15));
        Button confirm = new Button("confirm", WIDTH - 96, height - 24, 88, 15).style(confirmStyle)
                .label(new TextComponent(confirmLabel)).enabled(onConfirm != null).fallbackSlot(11);
        if (onConfirm != null) confirm.onClick(onConfirm::accept);
        gui.add(confirm);
        gui.openFor(player);
    }

    /** A dialog whose actions sit left to right in list order, after Cancel. */
    static void openWithActions(ServerPlayer player, String guiId, String title, ItemStack icon, List<Component> lines,
                                List<Action> actions, Consumer<ServerPlayer> onCancel) {
        int height = heightFor(lines);
        MasuGui gui = frame(guiId, title, icon, lines, height);
        int right = WIDTH - 8;
        for (int index = actions.size() - 1; index >= 0; index--) {
            Action action = actions.get(index);
            gui.add(new Button("action_" + index, right - ACTION_BUTTON_WIDTH, height - 24, ACTION_BUTTON_WIDTH, 15).style(action.style())
                    .label(new TextComponent(action.label())).onClick(action.run()::accept).fallbackSlot(11 + index * 2));
            right -= ACTION_BUTTON_WIDTH + 4;
        }
        gui.add(new Button("cancel", right - 54, height - 24, 54, 15).style(ButtonStyle.SECONDARY)
                .label(new TextComponent("Cancel")).onClick(onCancel::accept).fallbackSlot(17));
        gui.openFor(player);
    }

    private static int heightFor(List<Component> lines) {
        return Math.max(96, 58 + lines.size() * LINE_HEIGHT + 22);
    }

    private static MasuGui frame(String guiId, String title, ItemStack icon, List<Component> lines, int height) {
        MasuGui gui = MasuGui.create(guiId)
                .title(new TextComponent(title))
                .size(WIDTH, height)
                .fallbackType(FallbackType.CHEST_3);
        gui.add(new Window("window", WIDTH, height).title(new TextComponent(title)).accent(ACCENT).fallbackIcon(icon.copy()));
        gui.add(new ItemDisplay("icon", 14, 32, 24).item(icon.copy()).framed(true).enhancedOnly());
        for (int i = 0; i < lines.size(); i++) {
            gui.add(new Label("line_" + i, 48, 32 + i * LINE_HEIGHT).text(lines.get(i))
                    .color(i == 0 ? 0xFFFFFFFF : 0xFFAAAAAA).shadow(false).maxWidth(WIDTH - 56).enhancedOnly());
        }
        return gui;
    }
}
