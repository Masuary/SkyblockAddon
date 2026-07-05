package yorickbm.skyblockaddon.core.JSON;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class LoreLineDeserializer implements JsonDeserializer<LoreLineJson> {

    @Override
    public LoreLineJson deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        LoreLineJson line = new LoreLineJson();

        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            List<LoreSegmentJson> segments = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                segments.add(context.deserialize(parseSegment(element), LoreSegmentJson.class));
            }
            line.setSegments(segments);
        } else if (json.isJsonObject()) {
            JsonObject obj = json.getAsJsonObject();
            if (obj.has("join")) {
                LoreJoinJson join = context.deserialize(obj.get("join"), LoreJoinJson.class);
                line.setJoin(join);
            } else {
                throw new JsonParseException("Lore line object must have a \"join\" key: " + obj);
            }
        } else {
            throw new JsonParseException("Lore line must be either an array of segments or a {\"join\": ...} object, got: " + json);
        }

        return line;
    }

    private JsonElement parseSegment(final JsonElement element) {
        if (element.isJsonObject()) return element;
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Lore segment must be an object or a legacy JSON string, got: " + element);
        }

        final String rawComponent = escapeControlCharacters(element.getAsString());
        final JsonElement parsed;
        try {
            parsed = JsonParser.parseString(rawComponent);
        } catch (JsonParseException exception) {
            throw new JsonParseException("Invalid legacy lore component: " + element, exception);
        }

        if (!parsed.isJsonObject()) {
            throw new JsonParseException("Legacy lore component must contain a JSON object, got: " + element);
        }
        return parsed;
    }

    private String escapeControlCharacters(final String value) {
        return value
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
