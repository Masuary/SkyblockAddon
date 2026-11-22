package yorickbm.skyblockaddon.core.islands;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yorickbm.skyblockaddon.core.SkyblockAddonCore;
import yorickbm.skyblockaddon.core.permissions.PermissionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissingPermissionDefaultTest {
    private static final String ADDED_LATER = "w2b_added_later";
    private static final String ADDED_LATER_DEFAULT_ON = "w2b_added_later_default_on";

    @TempDir
    static Path temporaryFolder;

    @BeforeAll
    static void loadRegistryWithLaterPermissions() throws IOException {
        final Path registry = temporaryFolder.resolve("PermissionRegistry.json");
        Files.writeString(registry, """
                {"permissions": [
                  {"id": "%s", "item": {"item": "minecraft:stone"}, "category": "general", "triggers": ["onRightClickEntity"], "data": {}},
                  {"id": "%s", "item": {"item": "minecraft:stone"}, "category": "general", "triggers": ["OnHostileMobSpawn"], "data": {"defaults": true}}
                ]}
                """.formatted(ADDED_LATER, ADDED_LATER_DEFAULT_ON));
        PermissionManager.getInstance().loadPermissions(registry);
    }

    private static TestIsland.TestGroup savedBeforeTheRegistryGrew(final UUID groupId) {
        final TestIsland.TestGroup group = new TestIsland.TestGroup(groupId, "group", false);
        group.permissions.clear();
        return group;
    }

    @Test
    void membersAreAllowedAPermissionTheirGroupHasNoEntryFor() {
        assertTrue(savedBeforeTheRegistryGrew(SkyblockAddonCore.MEMBERS_GROUP_ID).canDo(ADDED_LATER));
    }

    @Test
    void visitorsAndCustomGroupsTakeTheRegistryDefault() {
        assertFalse(savedBeforeTheRegistryGrew(SkyblockAddonCore.VISITORS_GROUP_ID).canDo(ADDED_LATER));
        assertFalse(savedBeforeTheRegistryGrew(UUID.randomUUID()).canDo(ADDED_LATER));
        assertTrue(savedBeforeTheRegistryGrew(SkyblockAddonCore.VISITORS_GROUP_ID).canDo(ADDED_LATER_DEFAULT_ON));
    }

    @Test
    void aStoredChoiceWins() {
        final TestIsland.TestGroup members = savedBeforeTheRegistryGrew(SkyblockAddonCore.MEMBERS_GROUP_ID);
        members.setPermission(ADDED_LATER, false);
        assertFalse(members.canDo(ADDED_LATER));
    }

    @Test
    void unknownPermissionsStayDenied() {
        assertFalse(savedBeforeTheRegistryGrew(SkyblockAddonCore.MEMBERS_GROUP_ID).canDo("not_in_the_registry"));
    }
}
