import json
import tempfile
import unittest
from pathlib import Path

from tools import migrate_config


class ConfigurationMigrationTest(unittest.TestCase):
    def test_migration_preserves_source_and_writes_reported_new_format(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source = root / "source"
            output = root / "output"
            registry_directory = source / "registries"
            gui_directory = source / "guis"
            registry_directory.mkdir(parents=True)
            gui_directory.mkdir(parents=True)

            legacy_document = {
                "permissions": [
                    self.permission("place_blocks", "woldsvaults:vault_salager"),
                    self.permission("mod_create", "create:.*"),
                    self.permission("mod_ars_nouveau", "ars_nouveau:.*"),
                    self.permission("mod_industrialforegoing", "industrialforegoing:.*"),
                    self.permission("open_sophstorage", "sophisticatedstorage:.*"),
                    self.permission("server_custom", "custom:machine"),
                ]
            }
            legacy_path = registry_directory / "PermissionRegistry.json"
            original_legacy_text = json.dumps(legacy_document)
            legacy_path.write_text(original_legacy_text, encoding="utf-8")
            gui_path = gui_directory / "settings.json"
            gui_path.write_text(
                json.dumps({
                    "items": [{
                        "item": {"lore": [["{\"text\":\"Legacy\"}"]]},
                        "action": {
                            "onClick": "yorickbm.skyblockaddon.events.IslandEvents$SetSpawnPoint"
                        },
                    }]
                }),
                encoding="utf-8",
            )

            report = migrate_config.migrate(source, output)

            self.assertEqual(original_legacy_text, legacy_path.read_text(encoding="utf-8"))
            self.assertFalse((output / "registries/PermissionRegistry.json").exists())
            self.assertTrue((output / "registries/PermissionRegistry.pre-10.0.json").is_file())
            self.assertIn("mod_create", report["retired"])
            self.assertEqual(
                ["ars_nouveau_interact", "ars_nouveau_portal"],
                report["retired"]["mod_ars_nouveau"],
            )
            self.assertEqual(
                ["industrialforegoing_machines"],
                report["retired"]["mod_industrialforegoing"],
            )
            self.assertEqual(
                ["sophisticatedstorage_storage", "sophisticatedstorage_link"],
                report["retired"]["open_sophstorage"],
            )
            self.assertEqual(2, report["schema_version"])
            self.assertEqual(["server_custom"], report["custom"])
            self.assertEqual(1, report["counts"]["salvager_typos_corrected"])
            self.assertEqual("woldsvaults:vault_salvager", report["normalizations"][0]["to"])
            self.assertTrue(report["spawn_confirmation_routed"])
            migrated_settings = json.loads((output / "guis/settings.json").read_text(encoding="utf-8"))
            self.assertIsInstance(
                migrated_settings["items"][0]["item"]["lore"][0][0],
                dict,
            )
            self.assertEqual(
                "skyblockaddon:confirm_setspawn",
                migrated_settings["items"][0]["action"]["data"]["gui"],
            )
            self.assertTrue((output / "guis/confirm_setspawn.json").is_file())

            migrated_permissions = []
            for permission_file in (output / "registries/permissions").glob("*.json"):
                migrated_permissions.extend(json.loads(permission_file.read_text(encoding="utf-8"))["permissions"])
            permissions_by_id = {permission["id"]: permission for permission in migrated_permissions}
            self.assertNotIn("mod_create", permissions_by_id)
            self.assertNotIn("mod_ars_nouveau", permissions_by_id)
            self.assertNotIn("mod_industrialforegoing", permissions_by_id)
            self.assertNotIn("open_sophstorage", permissions_by_id)
            self.assertIn("ars_nouveau_interact", permissions_by_id)
            self.assertIn("ars_nouveau_portal", permissions_by_id)
            self.assertIn("industrialforegoing_machines", permissions_by_id)
            self.assertIn("sophisticatedstorage_storage", permissions_by_id)
            self.assertIn("sophisticatedstorage_link", permissions_by_id)
            self.assertIn("server_custom", permissions_by_id)
            self.assertEqual(
                ["woldsvaults:vault_salvager"],
                permissions_by_id["place_blocks"]["data"]["block"],
            )

    @staticmethod
    def permission(permission_id: str, block_pattern: str) -> dict:
        return {
            "id": permission_id,
            "triggers": ["onRightClickBlock"],
            "item": {"lore": [["{\"text\":\"Legacy\"}"]]},
            "data": {"block": [block_pattern]},
        }


if __name__ == "__main__":
    unittest.main()
