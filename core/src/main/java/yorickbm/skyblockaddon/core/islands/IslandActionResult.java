package yorickbm.skyblockaddon.core.islands;

public enum IslandActionResult {
    SUCCESS("island.action.success"),
    NOT_ALLOWED("island.action.not.allowed"),
    OWNER_ONLY("island.action.owner.only"),
    TARGET_IS_SELF("island.action.target.self"),
    TARGET_IS_OWNER("island.action.target.owner"),
    TARGET_NOT_MEMBER("island.action.target.not.member"),
    TARGET_IS_MEMBER("island.action.target.member"),
    TARGET_IS_BANNED("island.action.target.banned"),
    TARGET_NOT_BANNED("island.action.target.not.banned"),
    TARGET_NOT_ON_ISLAND("island.action.target.not.on.island"),
    GROUP_NOT_FOUND("island.action.group.not.found"),
    DEFAULT_GROUP_PROTECTED("island.action.group.default"),
    GROUP_NAME_EXISTS("commands.group.name.exists"),
    HELD_ITEM_MISSING("commands.group.failure"),
    NO_CHANGE("island.action.no.change");

    private final String languageKey;

    IslandActionResult(final String languageKey) {
        this.languageKey = languageKey;
    }

    public String languageKey() {
        return languageKey;
    }

    public boolean succeeded() {
        return this == SUCCESS;
    }
}
