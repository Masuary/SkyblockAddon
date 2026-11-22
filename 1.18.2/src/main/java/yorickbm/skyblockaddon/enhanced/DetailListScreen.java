package yorickbm.skyblockaddon.enhanced;

import com.masuary.masugui.api.MasuGui;
import com.masuary.masugui.element.Button;
import com.masuary.masugui.element.ButtonStyle;
import com.masuary.masugui.element.ItemDisplay;
import com.masuary.masugui.element.KeyValueList;
import com.masuary.masugui.element.Label;
import com.masuary.masugui.element.ListView;
import com.masuary.masugui.element.Panel;
import com.masuary.masugui.element.Section;
import com.masuary.masugui.element.StatusBar;
import com.masuary.masugui.element.Window;
import com.masuary.masugui.element.data.Chip;
import com.masuary.masugui.element.data.KeyHint;
import com.masuary.masugui.element.data.KeyValueRow;
import com.masuary.masugui.element.data.ListColumn;
import com.masuary.masugui.element.data.ListRow;
import com.masuary.masugui.fallback.FallbackType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The list screens' shared layout: a table card (rows select) beside a detail card for the selected row (head or icon,
 * name, label/value rows, buttons with the primary one at the bottom). The selection lives in the screen's gui data and
 * the screen reopens with it.
 */
final class DetailListScreen {

    static final int ROW_HEIGHT = 19;
    private static final int[] SECONDARY_FALLBACK_SLOTS = {51, 47};
    private static final int PRIMARY_FALLBACK_SLOT = 52;
    static final int TOOLBAR_FALLBACK_SLOT = 46;

    /** {@code contentTop} is 21 without a toolbar row and 43 with one. */
    record Geometry(int width, int height, int contentTop, int tableCardWidth) {
        int contentBottom() { return height - 15; }
        int cardHeight() { return contentBottom() - contentTop; }
        int detailLeft() { return tableCardWidth + 5; }
        int detailWidth() { return width - tableCardWidth - 6; }
    }

    static final Geometry PLAYERS = new Geometry(398, 214, 21, 262);
    static final Geometry PLAYERS_WITH_TOOLBAR = new Geometry(398, 214, 43, 262);
    static final Geometry TRAVEL = new Geometry(438, 234, 43, 302);

    record DetailButton(String id, String label, ButtonStyle style, boolean enabled, Consumer<ServerPlayer> onClick) {
        static DetailButton of(String id, String label, ButtonStyle style, Consumer<ServerPlayer> onClick) {
            return new DetailButton(id, label, style, true, onClick);
        }
    }

    /** Secondary rows hold one or two buttons and stack upwards from the primary button. */
    record Detail(ItemStack icon, List<Component> iconTooltip, String title, List<KeyValueRow> facts,
                  List<List<DetailButton>> secondaryRows, DetailButton primary) {
    }

    private DetailListScreen() {
    }

    static MasuGui frame(String guiId, Geometry geometry, String title, String chip, ItemStack fallbackIcon,
                         Consumer<ServerPlayer> onBack, List<KeyHint> hints, String statusRight) {
        MasuGui gui = MasuGui.create(guiId)
                .title(new TextComponent(title))
                .size(geometry.width(), geometry.height())
                .fallbackType(FallbackType.CHEST_6);
        Window window = new Window("window", geometry.width(), geometry.height()).title(new TextComponent(title))
                .accent(EnhancedDialog.ACCENT).fallbackIcon(fallbackIcon);
        if (chip != null) window.chip(Chip.of("", chip, EnhancedDialog.VALUE_WHITE));
        if (onBack != null) window.onBack(onBack);
        gui.add(window);
        gui.add(new StatusBar("status", 1, geometry.height() - 13, geometry.width() - 2).hints(hints)
                .right(new TextComponent(statusRight)));
        return gui;
    }

    /** Adds the table card and returns its list, already added, for the caller's click handler and empty text. */
    static ListView table(MasuGui gui, Geometry geometry, String listId, String header, List<ListRow> rows,
                          List<ListColumn> columns, int selectedIndex) {
        int top = geometry.contentTop();
        gui.add(new Panel("list_card", 1, top, geometry.tableCardWidth(), geometry.cardHeight()).style(Panel.Style.CARD));
        gui.add(new Section("list_header", 3, top + 4, geometry.tableCardWidth() - 4).label(new TextComponent(header)));
        ListView list = new ListView(listId, 3, top + 17, geometry.tableCardWidth() - 5, geometry.cardHeight() - 22)
                .rowHeight(ROW_HEIGHT).rows(rows).columns(columns).selected(selectedIndex);
        gui.add(list);
        return list;
    }

    /** The detail card of the selected row, or a muted hint when nothing is selected. */
    static void detail(MasuGui gui, Geometry geometry, Detail detail, String emptyText) {
        int left = geometry.detailLeft();
        int width = geometry.detailWidth();
        int top = geometry.contentTop();
        int center = left + width / 2;
        gui.add(new Panel("detail_card", left, top, width, geometry.cardHeight()).style(Panel.Style.CARD));
        if (detail == null) {
            gui.add(new Label("detail_empty", center, top + geometry.cardHeight() / 2 - 4).text(new TextComponent(emptyText))
                    .role(Label.Role.MUTED).centered().maxWidth(width - 8).enhancedOnly());
            return;
        }
        ItemDisplay icon = new ItemDisplay("detail_icon", center - 16, top + 9, 32).item(detail.icon().copy()).framed(true);
        if (!detail.iconTooltip().isEmpty()) icon.tooltip(detail.iconTooltip());
        gui.add(icon.enhancedOnly());
        gui.add(new Label("detail_name", center, top + 50).text(new TextComponent(detail.title())).role(Label.Role.TITLE)
                .centered().maxWidth(width - 8).enhancedOnly());
        gui.add(new KeyValueList("detail_values", left + 7, top + 65, width - 14).rowHeight(11).rows(detail.facts()));

        int buttonLeft = left + 6;
        int buttonWidth = width - 12;
        int primaryTop = geometry.contentBottom() - 26;
        if (detail.primary() != null) addButton(gui, detail.primary(), buttonLeft, primaryTop, buttonWidth, 22, PRIMARY_FALLBACK_SLOT);
        int rowTop = primaryTop - 21;
        int fallbackIndex = 0;
        for (List<DetailButton> row : detail.secondaryRows()) {
            int gap = 4;
            int eachWidth = (buttonWidth - gap * (row.size() - 1)) / row.size();
            for (int index = 0; index < row.size(); index++) {
                int fallbackSlot = fallbackIndex < SECONDARY_FALLBACK_SLOTS.length ? SECONDARY_FALLBACK_SLOTS[fallbackIndex] : -2;
                addButton(gui, row.get(index), buttonLeft + index * (eachWidth + gap), rowTop, eachWidth, 17, fallbackSlot);
                fallbackIndex++;
            }
            rowTop -= 21;
        }
    }

    private static void addButton(MasuGui gui, DetailButton button, int x, int y, int width, int height, int fallbackSlot) {
        Button element = new Button(button.id(), x, y, width, height).style(button.style())
                .label(new TextComponent(button.label())).enabled(button.enabled()).fallbackSlot(fallbackSlot);
        if (button.enabled()) element.onClick(button.onClick()::accept);
        gui.add(element);
    }

    /** The index of the first entry matching the stored selection, else the first entry, or -1 for an empty list. */
    static <T> int selectedIndex(List<T> entries, Predicate<T> isSelected) {
        for (int index = 0; index < entries.size(); index++) {
            if (isSelected.test(entries.get(index))) return index;
        }
        return entries.isEmpty() ? -1 : 0;
    }
}
