package yorickbm.skyblockaddon.core.util;

import java.util.HashMap;
import java.util.Map;

public abstract class DataComponent {

    // Internal storage map
    private final Map<String, Object> dataMap = new HashMap<>();

    /**
     * Stores a value associated with the given key.
     * @param key the key
     * @param data the value (any object)
     */
    public void put(String key, Object data) {
        dataMap.put(key, data);
    }

    /**
     * Retrieves an int associated with the key.
     * If the stored value is not a Number, returns 0.
     * @param key the key
     * @return the int value
     */
    public int get(String key) {
        Object value = dataMap.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0; // fallback if no value or not numeric
    }

    /**
     * Optional: generic get for any type.
     */
    @SuppressWarnings("unchecked")
    public <T> T getObject(String key, Class<T> type) {
        Object value = dataMap.get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Check if a key exists
     */
    public boolean contains(String key) {
        return dataMap.containsKey(key);
    }

    /**
     * Remove a key
     */
    public void remove(String key) {
        dataMap.remove(key);
    }

    /**
     * Clear all entries
     */
    public void clear() {
        dataMap.clear();
    }

    protected Map<String, Object> getDataMap() {
        return dataMap;
    }
}
