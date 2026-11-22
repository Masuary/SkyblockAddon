package yorickbm.skyblockaddon.core.islands;

/**
 * Whether the island spawn may be set where the player stands: on the island, on the ground, on a block with a
 * full top face (never over the void, and never on a post the arriving player would slip past).
 */
public enum SpawnPointPlacement {
    ALLOWED("island.setspawn.success"),
    OUTSIDE_ISLAND("island.setspawn.outside"),
    NOT_ON_GROUND("island.setspawn.not.on.ground");

    private final String languageKey;

    SpawnPointPlacement(final String languageKey) {
        this.languageKey = languageKey;
    }

    public static SpawnPointPlacement evaluate(final boolean standingOnIsland, final boolean onGround, final boolean sturdyTopUnderFeet) {
        if (!standingOnIsland) return OUTSIDE_ISLAND;
        if (!onGround || !sturdyTopUnderFeet) return NOT_ON_GROUND;
        return ALLOWED;
    }

    public String languageKey() {
        return languageKey;
    }

    public boolean allowed() {
        return this == ALLOWED;
    }
}
