package yorickbm.skyblockaddon.core.islands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.util.geometry.Square;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandMembershipTest {
    private TestIsland island;
    private TestIslandGroup membersGroup;
    private TestIslandGroup visitorsGroup;
    private TestIslandGroup customGroup;

    @BeforeEach
    void setUp() {
        island = new TestIsland();
        membersGroup = new TestIslandGroup(SkyblockAddonCore.MOD_UUID, "Members");
        visitorsGroup = new TestIslandGroup(SkyblockAddonCore.MOD_UUID2, "Visitors");
        customGroup = new TestIslandGroup(UUID.randomUUID(), "Builders");

        island.addGroup(membersGroup);
        island.addGroup(visitorsGroup);
        island.addGroup(customGroup);
        island.setOwner(UUID.randomUUID());
    }

    @Test
    void addingMemberToCustomGroupMaintainsCanonicalMembership() {
        final UUID member = UUID.randomUUID();

        assertTrue(island.addMember(member, customGroup.getId()));

        assertTrue(island.isPartOf(member));
        assertTrue(island.getMembers().contains(member));
        assertSame(customGroup, island.getGroupForEntityUUID(member).orElseThrow());
        assertEquals(1, countExplicitGroupAssignments(member));
    }

    @Test
    void assigningNonMemberDoesNotGrantIslandMembership() {
        final UUID visitor = UUID.randomUUID();

        assertTrue(island.assignGroup(visitor, customGroup.getId()));

        assertFalse(island.isPartOf(visitor));
        assertFalse(island.getMembers().contains(visitor));
        assertSame(customGroup, island.getGroupForEntityUUID(visitor).orElseThrow());
        assertEquals(1, countExplicitGroupAssignments(visitor));
    }

    @Test
    void reassigningMemberKeepsCanonicalMembershipAndSingleGroup() {
        final UUID member = UUID.randomUUID();
        assertTrue(island.addMember(member, membersGroup.getId()));

        assertTrue(island.assignGroup(member, customGroup.getId()));

        assertTrue(island.isPartOf(member));
        assertSame(customGroup, island.getGroupForEntityUUID(member).orElseThrow());
        assertEquals(1, countExplicitGroupAssignments(member));
    }

    @Test
    void invalidTargetDoesNotRemoveExistingAssignment() {
        final UUID member = UUID.randomUUID();
        assertTrue(island.addMember(member, customGroup.getId()));

        assertFalse(island.assignGroup(member, UUID.randomUUID()));

        assertTrue(island.isPartOf(member));
        assertSame(customGroup, island.getGroupForEntityUUID(member).orElseThrow());
        assertEquals(1, countExplicitGroupAssignments(member));
    }

    @Test
    void removingCustomGroupMovesMembersToMembersGroup() {
        final UUID member = UUID.randomUUID();
        assertTrue(island.addMember(member, customGroup.getId()));

        assertTrue(island.removeGroup(customGroup.getId()));

        assertTrue(island.isPartOf(member));
        assertSame(membersGroup, island.getGroupForEntityUUID(member).orElseThrow());
        assertEquals(1, countExplicitGroupAssignments(member));
    }

    @Test
    void removingMemberClearsEveryGroupAssignment() {
        final UUID member = UUID.randomUUID();
        assertTrue(island.addMember(member, customGroup.getId()));
        membersGroup.addMember(member);

        island.removeMember(member, customGroup.getId());

        assertFalse(island.isPartOf(member));
        assertEquals(0, countExplicitGroupAssignments(member));
        assertSame(visitorsGroup, island.getGroupForEntityUUID(member).orElseThrow());
    }

    @Test
    void ownerCannotBeAssignedToPermissionGroup() {
        assertFalse(island.assignGroup(island.getOwner(), customGroup.getId()));
        assertEquals(0, countExplicitGroupAssignments(island.getOwner()));
    }

    @Test
    void integrityRepairRecoversCustomGroupMemberMissingFromCanonicalList() {
        final UUID member = UUID.randomUUID();
        customGroup.addMember(member);

        final Island.MembershipRepairReport report = island.repairMembershipIntegrity();

        assertTrue(report.changed());
        assertEquals(1, report.recoveredMembers());
        assertTrue(island.isPartOf(member));
        assertSame(customGroup, island.getGroupForEntityUUID(member).orElseThrow());
    }

    @Test
    void integrityRepairKeepsCustomAssignmentAndRemovesDuplicates() {
        final UUID member = UUID.randomUUID();
        island.addCorruptCanonicalMember(member);
        island.addCorruptCanonicalMember(member);
        membersGroup.addMember(member);
        customGroup.addMember(member);

        final Island.MembershipRepairReport report = island.repairMembershipIntegrity();

        assertEquals(1, report.removedDuplicateMembers());
        assertEquals(1, report.repairedAssignments());
        assertEquals(1, island.getMembers().stream().filter(member::equals).count());
        assertSame(customGroup, island.getGroupForEntityUUID(member).orElseThrow());
        assertEquals(1, countExplicitGroupAssignments(member));
    }

    @Test
    void integrityRepairRemovesOwnerAndVisitorAssignments() {
        final UUID owner = island.getOwner();
        island.addCorruptCanonicalMember(owner);
        membersGroup.addMember(owner);
        visitorsGroup.addMember(owner);

        final Island.MembershipRepairReport report = island.repairMembershipIntegrity();

        assertTrue(report.removedOwnerReferences() >= 2);
        assertFalse(island.getMembers().contains(owner));
        assertEquals(0, countExplicitGroupAssignments(owner));
    }

    private long countExplicitGroupAssignments(final UUID entity) {
        return island.getGroups().stream().filter(group -> group.hasMember(entity)).count();
    }

    private static final class TestIsland extends Island {
        private void addCorruptCanonicalMember(final UUID member) {
            members.add(member);
        }

        @Override
        public Square getIslandBoundingBoxAsSquare() {
            return new Square() {};
        }

        @Override
        public Optional<UUID> getGroupByName(final String groupName) {
            return getGroups().stream()
                    .filter(group -> group.getName().equalsIgnoreCase(groupName))
                    .map(IslandGroup::getId)
                    .findFirst();
        }
    }

    private static final class TestIslandGroup extends IslandGroup {
        private final String name;

        private TestIslandGroup(final UUID id, final String name) {
            super(id, false);
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
