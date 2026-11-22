package yorickbm.skyblockaddon.core.islands;

/**
 * Whether the island spawn may be set where the player stands: on the island, on the ground, with a solid block
 * under their feet (never over the void).
 */
public enum SpawnPointPlacement {
    ALLOWED("island.setspawn.success"),
    OUTSIDE_ISLAND("island.setspawn.outside"),
    NOT_ON_GROUND("island.setspawn.not.on.ground");

    private final String languageKey;

    SpawnPointPlacement(final String languageKey) {
        this.languageKey = languageKey;
    }

    public static SpawnPointPlacement evaluate(final boolean standingOnIsland, final boolean onGround, final boolean solidBlockUnderFeet) {
        if (!standingOnIsland) return OUTSIDE_ISLAND;
        if (!onGround || !solidBlockUnderFeet) return NOT_ON_GROUND;
        return ALLOWED;
    }

    public String languageKey() {
        return languageKey;
    }

    public boolean allowed() {
        return this == ALLOWED;
    }
}
