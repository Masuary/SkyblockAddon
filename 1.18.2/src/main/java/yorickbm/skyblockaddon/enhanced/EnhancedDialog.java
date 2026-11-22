package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.Button;
import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.ItemDisplay;
import com.masuary.masugui.element.KeyValueList;
import com.masuary.masugui.element.Label;
import com.masuary.masugui.element.Window;
import com.masuary.masugui.element.data.KeyValueRow;
import com.masuary.masugui.fallback.FallbackType;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** The confirm dialog shared by the island screens: framed icon, the question, what happens as label/value rows, Cancel and the action. */
final class EnhancedDialog {

    static final int ACCENT = 0xFF55DD99;
    static final int VALUE_WHITE = 0xFFFFFF;
    static final int VALUE_AQUA = 0x55FFFF;
    static final int VALUE_GREEN = 0x55FF55;
    static final int VALUE_RED = 0xFF5555;
    static final int VALUE_GRAY = 0xAAAAAA;

    private static final int WIDTH = 238;
    private static final int HEIGHT = 106;
    private static final int TEXT_LEFT = 54;
    private static final int TEXT_WIDTH = 180;

    private EnhancedDialog() {
    }

    /** {@code onConfirm} is null when the action is not possible right now; the action button is then disabled. */
    static void open(ServerPlayer player, String guiId, String title, ItemStack icon, String question, List<KeyValueRow> terms,
                     String confirmLabel, ButtonStyle confirmStyle, Consumer<ServerPlayer> onConfirm, Consumer<ServerPlayer> onCancel) {
        MasuGui gui = MasuGui.create(guiId)
                .title(new TextComponent(title))
                .size(WIDTH, HEIGHT)
                .fallbackType(FallbackType.CHEST_3);
        gui.add(new Window("window", WIDTH, HEIGHT).title(new TextComponent(title)).accent(ACCENT).fallbackIcon(icon.copy()));
        gui.add(new ItemDisplay("preview", 9, 28, 32).item(icon.copy()).framed(true).enhancedOnly());
        gui.add(new Label("question", TEXT_LEFT, 26).text(new TextComponent(question)).role(Label.Role.TITLE)
                .maxWidth(TEXT_WIDTH).enhancedOnly());
        gui.add(new KeyValueList("terms", TEXT_LEFT, 40, TEXT_WIDTH).rowHeight(11).rows(terms)
                .fallbackName(new TextComponent(question)).fallbackSlot(13));
        gui.add(new Button("cancel", TEXT_LEFT, 82, 80, 20).style(ButtonStyle.SECONDARY)
                .label(new TextComponent("Cancel")).onClick(onCancel::accept).fallbackSlot(15));
        Button confirm = new Button("confirm", 138, 82, 96, 20).style(confirmStyle)
                .label(new TextComponent(confirmLabel)).enabled(onConfirm != null).fallbackSlot(11);
        if (onConfirm != null) confirm.onClick(onConfirm::accept);
        gui.add(confirm);
        gui.openFor(player);
    }
}
