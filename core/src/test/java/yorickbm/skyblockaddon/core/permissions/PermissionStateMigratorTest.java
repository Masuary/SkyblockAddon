package yorickbm.skyblockaddon.core.permissions;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionStateMigratorTest {
    @Test
    void copiesSplitPermissionValueToEveryReplacement() {
        final Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("vh_gear_tools", true);

        assertEquals(2, PermissionStateMigrator.migrate(permissions, Set.of("vh_gear_tools")));
        assertTrue(permissions.get("vh_gear"));
        assertTrue(permissions.get("vh_tools"));
    }

    @Test
    void preservesExplicitReplacementValue() {
        final Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("vh_gear_tools", true);
        permissions.put("vh_tools", false);

        assertEquals(1, PermissionStateMigrator.migrate(
                permissions,
                Set.of("vh_gear_tools", "vh_tools")
        ));
        assertTrue(permissions.get("vh_gear"));
        assertFalse(permissions.get("vh_tools"));
    }

    @Test
    void repeatedMigrationIsStableWhenMigratedIdsAreStored() {
        final Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("mod_mekanism", true);
        PermissionStateMigrator.migrate(permissions, Set.of("mod_mekanism"));

        assertEquals(0, PermissionStateMigrator.migrate(
                permissions,
                Set.of("mod_mekanism", "mekanism_machines", "mekanism_qio")
        ));
    }

    @Test
    void derivesNewGranularPermissionsFromSpecificLegacyControls() {
        final Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("open_chests", true);
        permissions.put("interact_generic", false);

        PermissionStateMigrator.migrate(permissions, Set.of("open_chests", "interact_generic"));

        assertTrue(permissions.get("chestmonster_interact"));
        assertFalse(permissions.get("cfm_cabinets"));
        assertFalse(permissions.get("interact_furnaces"));
    }

    @Test
    void migratesPhaseNinePermissionSplitsWithoutChangingValues() {
        final Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("mod_ars_nouveau", false);
        permissions.put("mod_industrialforegoing", true);
        permissions.put("open_sophstorage", false);

        assertEquals(5, PermissionStateMigrator.migrate(
                permissions,
                Set.of("mod_ars_nouveau", "mod_industrialforegoing", "open_sophstorage")
        ));

        assertFalse(permissions.get("ars_nouveau_machines"));
        assertFalse(permissions.get("ars_nouveau_portal"));
        assertTrue(permissions.get("industrialforegoing_machines"));
        assertFalse(permissions.get("sophisticatedstorage_storage"));
        assertFalse(permissions.get("sophisticatedstorage_link"));
        assertEquals(3, PermissionStateMigrator.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void migratesShadowingBroadPermissionsToLiveSafeSplitIds() {
        final Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("mod_occultism", false);
        permissions.put("open_ID", true);
        permissions.put("interact_functional_drawers", true);
        permissions.put("mod_immersiveengineering", false);
        permissions.put("open_Tom", true);

        assertEquals(8, PermissionStateMigrator.migrate(
                permissions,
                Set.of(
                        "mod_occultism",
                        "open_ID",
                        "interact_functional_drawers",
                        "mod_immersiveengineering",
                        "open_Tom"
                )
        ));

        assertFalse(permissions.get("occultism_rituals"));
        assertFalse(permissions.get("occultism_storage"));
        assertTrue(permissions.get("idyn_logic"));
        assertTrue(permissions.get("integratednbt_extractor"));
        assertTrue(permissions.get("integrated_addons"));
        assertTrue(permissions.get("functionalstorage_interact"));
        assertFalse(permissions.get("immersive_engineering"));
        assertTrue(permissions.get("toms_storage_terminals"));
    }
}
