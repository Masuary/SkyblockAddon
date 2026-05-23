package yorickbm.guilibrary;

import net.minecraft.network.chat.TextComponent;

import java.util.List;

public class GUIType {

    private final List<TextComponent> title;
    private final int rows;
    private final List<GUIItem> items;
    private final List<GUIFiller> fillers;

    // Private constructor to enforce Builder usage
    private GUIType(final Builder builder) {
        this.title = builder.title;
        this.rows = builder.rows;
        this.items = builder.items;
        this.fillers = builder.fillers;
    }

    // Getters
    public List<TextComponent> getTitle() {
        return title;
    }

    public int getRows() {
        return rows;
    }

    public List<GUIItem> getItems() {
        return items;
    }

    public List<GUIFiller> getFillers() {
        return fillers;
    }

    // Builder class
    public static class Builder {
        private List<TextComponent> title;
        private int rows;
        private List<GUIItem> items;
        private List<GUIFiller> fillers;

        // Default constructor
        public Builder() {
            // Set default values (optional)
            this.rows = 3;  // Default to 1 row if not specified
            this.items = List.of();  // Default to empty list if not specified
            this.fillers = List.of();  // Default to empty list if not specified
        }

        // Setter methods for each field
        public Builder setTitle(final List<TextComponent> title) {
            this.title = title;
            return this;
        }

        public Builder setRows(final int rows) {
            this.rows = rows;
            return this;
        }

        public Builder setItems(final List<GUIItem> items) {
            this.items = items;
            return this;
        }

        public Builder setFillers(final List<GUIFiller> fillers) {
            this.fillers = fillers;
            return this;
        }

        // Build method to return the fully constructed GUIType
        public GUIType build() {
            if (title == null) {
                throw new IllegalArgumentException("A GUI title must be provided.");
            }
            return new GUIType(this);
        }
    }
}
