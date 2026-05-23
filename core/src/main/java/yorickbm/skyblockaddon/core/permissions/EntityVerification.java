package yorickbm.skyblockaddon.core.permissions;

public enum EntityVerification {
    NOT_A_PLAYER(true),
    NOT_IN_NETHER(true),
    NOT_IN_OVERWORLD(true),
    IS_ADMIN(true),
    CAP_NOT_FOUND(true),
    NOT_ON_ISLAND(true),
    IS_ISLAND_OWNER(true),
    IS_ISLAND_MEMBER(false);


    private final boolean value;

    EntityVerification(final boolean value) {
        this.value = value;
    }

    public boolean asBoolean() {
        return value;
    }
}
