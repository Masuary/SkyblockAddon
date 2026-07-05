#!/usr/bin/env python3
"""Convert a legacy SkyblockAddon configuration into the 10.0 directory format."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
REGISTRY_ROOT = REPOSITORY_ROOT / "core/src/main/resources/assets/skyblockaddon/registries"
BUNDLED_PERMISSION_DIRECTORY = REGISTRY_ROOT / "permissions"
BUNDLED_GROUP_DIRECTORY = REGISTRY_ROOT / "groups"
BUNDLED_GUI_DIRECTORY = REPOSITORY_ROOT / "core/src/main/resources/assets/skyblockaddon/guis"
MIGRATION_RULES_FILE = REGISTRY_ROOT / "permission_migrations.json"
LEGACY_SALVAGER_ID = "woldsvaults:vault_salager"
CURRENT_SALVAGER_ID = "woldsvaults:vault_salvager"


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Legacy config/skyblockaddon directory")
    parser.add_argument("output", type=Path, help="New directory to create")
    return parser.parse_args()


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise RuntimeError(f"Failed to load JSON file {path}: {exception}") from exception


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = path.with_name(path.name + ".tmp")
    temporary_path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary_path, path)


def convert_lore(value: Any, inside_lore: bool = False) -> tuple[Any, int]:
    if isinstance(value, dict):
        converted: dict[str, Any] = {}
        conversion_count = 0
        for key, child in value.items():
            converted_child, child_count = convert_lore(child, inside_lore or key == "lore")
            converted[key] = converted_child
            conversion_count += child_count
        return converted, conversion_count

    if isinstance(value, list):
        converted_items = []
        conversion_count = 0
        for child in value:
            converted_child, child_count = convert_lore(child, inside_lore)
            converted_items.append(converted_child)
            conversion_count += child_count
        return converted_items, conversion_count

    if inside_lore and isinstance(value, str):
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError:
            return value, 0
        if isinstance(parsed, (dict, list)):
            return parsed, 1

    return value, 0


def load_bundled_permissions() -> tuple[dict[str, dict[str, Any]], dict[str, str]]:
    documents: dict[str, dict[str, Any]] = {}
    permission_locations: dict[str, str] = {}
    for path in sorted(BUNDLED_PERMISSION_DIRECTORY.glob("*.json")):
        document = load_json(path)
        if not isinstance(document, dict) or not isinstance(document.get("permissions"), list):
            raise RuntimeError(f"Invalid bundled permission document {path}")
        documents[path.name] = document
        for permission in document["permissions"]:
            permission_id = str(permission.get("id", "")).lower()
            if not permission_id:
                raise RuntimeError(f"Permission without ID in {path}")
            if permission_id in permission_locations:
                raise RuntimeError(
                    f"Duplicate bundled permission ID {permission_id!r} in "
                    f"{permission_locations[permission_id]} and {path.name}"
                )
            permission_locations[permission_id] = path.name
    return documents, permission_locations


def replace_permission(
    document: dict[str, Any], permission_id: str, replacement: dict[str, Any]
) -> None:
    for index, permission in enumerate(document["permissions"]):
        if str(permission.get("id", "")).lower() == permission_id:
            document["permissions"][index] = replacement
            return
    raise RuntimeError(f"Permission {permission_id!r} disappeared during migration")


def preserve_existing_directory(directory: Path) -> None:
    if not directory.exists() or not any(directory.iterdir()):
        return
    backup = directory.with_name(directory.name + ".pre-10.0")
    if backup.exists():
        raise RuntimeError(f"Cannot preserve {directory}: backup already exists at {backup}")
    directory.rename(backup)


def route_spawn_action_through_confirmation(settings_document: Any) -> bool:
    if not isinstance(settings_document, dict):
        return False
    changed = False
    for item in settings_document.get("items", []):
        if not isinstance(item, dict) or not isinstance(item.get("action"), dict):
            continue
        action = item["action"]
        if action.get("onClick") != "yorickbm.skyblockaddon.events.IslandEvents$SetSpawnPoint":
            continue
        item["action"] = {
            "onClick": "yorickbm.guilibrary.events.ItemOpenMenuEvent",
            "data": {"gui": "skyblockaddon:confirm_setspawn"},
        }
        changed = True
    return changed


def migrate(source: Path, output: Path) -> dict[str, Any]:
    source = source.resolve()
    output = output.resolve()
    if not source.is_dir():
        raise RuntimeError(f"Source configuration directory does not exist: {source}")
    if output.exists():
        raise RuntimeError(f"Output path already exists: {output}")
    temporary_output = output.with_name(output.name + ".tmp")
    if temporary_output.exists():
        raise RuntimeError(f"Temporary output path already exists: {temporary_output}")

    legacy_registry = source / "registries/PermissionRegistry.json"
    if not legacy_registry.is_file():
        raise RuntimeError(f"Legacy permission registry not found: {legacy_registry}")

    migration_rules = load_json(MIGRATION_RULES_FILE)
    schema_version = int(migration_rules["schema_version"])
    retired_ids = {str(value).lower() for value in migration_rules["retired"]}
    replacements = {
        str(source_id).lower(): [str(target) for target in targets]
        for source_id, targets in migration_rules["replacements"].items()
    }
    missing_replacements = sorted(retired_ids - replacements.keys())
    if missing_replacements:
        raise RuntimeError(f"Retired permissions lack replacement rules: {missing_replacements}")

    try:
        shutil.copytree(source, temporary_output, copy_function=shutil.copy2)
        output_registry_directory = temporary_output / "registries"
        output_permission_directory = output_registry_directory / "permissions"
        output_group_directory = output_registry_directory / "groups"
        preserve_existing_directory(output_permission_directory)
        preserve_existing_directory(output_group_directory)
        output_permission_directory.mkdir(parents=True, exist_ok=True)
        output_group_directory.mkdir(parents=True, exist_ok=True)

        bundled_documents, permission_locations = load_bundled_permissions()
        legacy_text = legacy_registry.read_text(encoding="utf-8")
        salvager_correction_count = legacy_text.count(LEGACY_SALVAGER_ID)
        legacy_document = json.loads(legacy_text.replace(LEGACY_SALVAGER_ID, CURRENT_SALVAGER_ID))
        legacy_permissions = legacy_document.get("permissions")
        if not isinstance(legacy_permissions, list):
            raise RuntimeError(f"Legacy permission registry has no permissions array: {legacy_registry}")

        retained_ids: list[str] = []
        retired_report: dict[str, list[str]] = {}
        custom_permissions: list[dict[str, Any]] = []
        seen_legacy_ids: set[str] = set()
        lore_conversion_count = 0

        for permission in legacy_permissions:
            permission_id = str(permission.get("id", "")).lower()
            if not permission_id:
                raise RuntimeError("Legacy permission without an ID")
            if permission_id in seen_legacy_ids:
                raise RuntimeError(f"Duplicate legacy permission ID: {permission_id}")
            seen_legacy_ids.add(permission_id)

            converted_permission, converted_count = convert_lore(permission)
            lore_conversion_count += converted_count
            if permission_id in retired_ids:
                retired_report[permission_id] = replacements[permission_id]
                continue

            destination_name = permission_locations.get(permission_id)
            if destination_name is None:
                custom_permissions.append(converted_permission)
                continue
            replace_permission(bundled_documents[destination_name], permission_id, converted_permission)
            retained_ids.append(permission_id)

        for file_name, document in bundled_documents.items():
            write_json(output_permission_directory / file_name, document)
        if custom_permissions:
            write_json(
                output_permission_directory / "custom_legacy.json",
                {"permissions": custom_permissions},
            )
        for group_file in sorted(BUNDLED_GROUP_DIRECTORY.glob("*.json")):
            shutil.copy2(group_file, output_group_directory / group_file.name)

        converted_gui_files: list[str] = []
        spawn_confirmation_routed = False
        gui_directory = temporary_output / "guis"
        if gui_directory.is_dir():
            for gui_file in sorted(gui_directory.glob("*.json")):
                gui_document = load_json(gui_file)
                if gui_file.name == "settings.json":
                    spawn_confirmation_routed = route_spawn_action_through_confirmation(gui_document)
                converted_gui, converted_count = convert_lore(gui_document)
                if converted_count or spawn_confirmation_routed and gui_file.name == "settings.json":
                    write_json(gui_file, converted_gui)
                if converted_count:
                    converted_gui_files.append(gui_file.name)
                    lore_conversion_count += converted_count
            confirmation_gui = gui_directory / "confirm_setspawn.json"
            if not confirmation_gui.exists():
                shutil.copy2(BUNDLED_GUI_DIRECTORY / confirmation_gui.name, confirmation_gui)

        copied_legacy_registry = output_registry_directory / "PermissionRegistry.json"
        legacy_backup = output_registry_directory / "PermissionRegistry.pre-10.0.json"
        copied_legacy_registry.rename(legacy_backup)

        final_permission_ids: list[str] = []
        for document in bundled_documents.values():
            final_permission_ids.extend(str(permission["id"]) for permission in document["permissions"])
        final_permission_ids.extend(str(permission["id"]) for permission in custom_permissions)
        normalized_final_ids = [permission_id.lower() for permission_id in final_permission_ids]
        if len(normalized_final_ids) != len(set(normalized_final_ids)):
            raise RuntimeError("Migrated permissions contain duplicate IDs")

        added_ids = sorted(set(permission_locations) - set(retained_ids) - retired_ids)
        report = {
            "schema_version": schema_version,
            "source": str(source),
            "counts": {
                "legacy_permissions": len(legacy_permissions),
                "retained_legacy_overrides": len(retained_ids),
                "retired_legacy_permissions": len(retired_report),
                "custom_legacy_permissions": len(custom_permissions),
                "new_bundled_permissions": len(added_ids),
                "final_permissions": len(final_permission_ids),
                "lore_segments_converted": lore_conversion_count,
                "salvager_typos_corrected": salvager_correction_count,
            },
            "retained": sorted(retained_ids),
            "retired": dict(sorted(retired_report.items())),
            "custom": sorted(str(permission["id"]) for permission in custom_permissions),
            "added": added_ids,
            "converted_gui_files": converted_gui_files,
            "spawn_confirmation_routed": spawn_confirmation_routed,
            "normalizations": [
                {
                    "from": LEGACY_SALVAGER_ID,
                    "to": CURRENT_SALVAGER_ID,
                    "occurrences": salvager_correction_count,
                }
            ] if salvager_correction_count else [],
            "legacy_registry_backup": "registries/PermissionRegistry.pre-10.0.json",
        }
        write_json(temporary_output / "migration-report.json", report)
        os.replace(temporary_output, output)
        return report
    except Exception:
        shutil.rmtree(temporary_output, ignore_errors=True)
        raise


def main() -> int:
    arguments = parse_arguments()
    try:
        report = migrate(arguments.source, arguments.output)
    except Exception as exception:
        print(f"Migration failed: {exception}", file=sys.stderr)
        return 1

    counts = report["counts"]
    print(
        "Migration complete: "
        f"{counts['legacy_permissions']} legacy -> {counts['final_permissions']} final permissions; "
        f"{counts['lore_segments_converted']} lore segments converted."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
