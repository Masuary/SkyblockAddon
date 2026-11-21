package yorickbm.skyblockaddon.core.islands;

import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.util.geometry.Square;
import yorickbm.skyblockaddon.core.util.geometry.Vec3i;

import java.util.Optional;
import java.util.UUID;

final class TestIsland extends Island {
    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    TestIsland() {
        addGroup(new TestGroup(SkyblockAddonCore.MEMBERS_GROUP_ID, "Members", true));
        addGroup(new TestGroup(SkyblockAddonCore.VISITORS_GROUP_ID, "Visitors", false));
        setOwner(OWNER);
        setCenter(new Vec3i(0, 110, 0));
    }

    UUID addCustomGroup(final String name) {
        final UUID groupId = UUID.randomUUID();
        addGroup(new TestGroup(groupId, name, false));
        return groupId;
    }

    @Override
    public Square getIslandBoundingBoxAsSquare() {
        throw new UnsupportedOperationException("not needed in tests");
    }

    @Override
    public Optional<UUID> getGroupByName(final String groupName) {
        return getGroups().stream().filter(group -> group.getName().equals(groupName)).map(IslandGroup::getId).findFirst();
    }

    static final class TestGroup extends IslandGroup {
        private final String name;

        TestGroup(final UUID groupId, final String name, final boolean allowAll) {
            super(groupId, allowAll);
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
