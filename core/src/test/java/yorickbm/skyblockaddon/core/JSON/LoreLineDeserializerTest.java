package yorickbm.skyblockaddon.core.JSON;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoreLineDeserializerTest {
    @Test
    void parsesStructuredLoreSegments() {
        final ItemStackJson item = parseItem("""
                [[{"text":"Structured","color":"green","bold":true}]]
                """);

        final LoreSegmentJson segment = item.getLore().get(0).getSegments().get(0);
        assertEquals("Structured", segment.text);
        assertEquals("green", segment.color);
        assertEquals(true, segment.bold);
    }

    @Test
    void parsesLegacyJsonStringSegments() {
        final ItemStackJson item = parseItem("""
                [["{\\\"text\\\":\\\"Legacy\\\",\\\"color\\\":\\\"dark_gray\\\"}"]]
                """);

        final LoreSegmentJson segment = item.getLore().get(0).getSegments().get(0);
        assertEquals("Legacy", segment.text);
        assertEquals("dark_gray", segment.color);
    }

    @Test
    void parsesLegacyComponentContainingDecodedNewline() {
        final ItemStackJson item = parseItem("""
                [["{\\\"text\\\":\\\"First line\\nSecond line\\\",\\\"color\\\":\\\"gray\\\"}"]]
                """);

        final LoreSegmentJson segment = item.getLore().get(0).getSegments().get(0);
        assertEquals("First line\nSecond line", segment.text);
    }

    @Test
    void rejectsMalformedLegacyComponent() {
        assertThrows(JsonParseException.class, () -> parseItem("""
                [["not json"]]
                """));
    }

    @Test
    void parsesJoinLoreWithoutTreatingItAsLegacy() {
        final ItemStackJson item = parseItem("""
                [{"join":{"prefix":{"text":"Contains: "},"separator":{"text":", "},"entries":[]}}]
                """);

        assertFalse(item.getLore().isEmpty());
        assertEquals(true, item.getLore().get(0).isJoin());
    }

    private ItemStackJson parseItem(final String lore) {
        final ItemStackJson item = new ItemStackJson();
        item.fromJSON("""
                {
                  "display_name": ["{\\\"text\\\":\\\"Test\\\"}"],
                  "item": "minecraft:paper",
                  "lore": %s
                }
                """.formatted(lore));
        return item;
    }
}
