package yorickbm.skyblockaddon.core.islands;

/**
 * Group names come from the group's item. Before P8 they were shown and typed with the "[...]" that Minecraft puts
 * around an item's display name, so a typed or stored name is compared without one surrounding pair of brackets.
 */
public final class GroupNames {

    private GroupNames() {
    }

    public static String normalise(final String name) {
        final String trimmedName = name.trim();
        if (trimmedName.length() <= 2 || !trimmedName.startsWith("[") || !trimmedName.endsWith("]")) return trimmedName;
        return trimmedName.substring(1, trimmedName.length() - 1).trim();
    }

    public static boolean matches(final String groupName, final String typedName) {
        return normalise(groupName).equalsIgnoreCase(normalise(typedName));
    }
}
