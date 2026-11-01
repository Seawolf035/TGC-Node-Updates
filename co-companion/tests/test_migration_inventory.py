"""Read-only legacy migration discovery tests. No Node keys are opened."""
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch

from tgc_cocompanion.migration_inventory import (
    inspect_staff_installation, REQUIRED_STAFF_FILES, RUNTIME_FILES,
)


class MigrationInventoryTests(unittest.TestCase):
    def test_missing_installation(self):
        with tempfile.TemporaryDirectory() as td:
            result = inspect_staff_installation(Path(td) / "missing")
            self.assertFalse(result.installation_found)
            self.assertFalse(result.migration_authorized)

    def test_complete_install_files_detected_without_reading(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            for name in REQUIRED_STAFF_FILES + RUNTIME_FILES:
                (root / name).write_bytes(b"synthetic private sentinel; never read")
            (root / "tgc_staff_node_queue.db-wal").write_bytes(b"synthetic")
            with patch.object(Path, "read_bytes", side_effect=AssertionError("No reading keys")):
                result = inspect_staff_installation(root)
            self.assertTrue(result.installation_found)
            self.assertEqual(set(result.source_files_found), set(REQUIRED_STAFF_FILES))
            self.assertEqual(set(result.runtime_files_found), set(RUNTIME_FILES))
            self.assertIn("tgc_staff_node_queue.db-wal", result.queue_sidecars_found)
            self.assertFalse(result.migration_authorized)
            self.assertNotIn("sentinel", repr(result))

    def test_missing_queue_is_reported_not_recreated(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            for name in REQUIRED_STAFF_FILES:
                (root / name).touch()
            result = inspect_staff_installation(root)
            self.assertIn("tgc_staff_node_queue.db", result.runtime_files_missing)
            self.assertFalse((root / "tgc_staff_node_queue.db").exists())
            self.assertFalse(result.migration_authorized)

    def test_symlink_not_treated_as_local_credential(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            (root / "other").write_bytes(b"private")
            (root / "tgc_staff_node_key.dpapi").symlink_to(root / "other")
            result = inspect_staff_installation(root)
            self.assertIn("tgc_staff_node_key.dpapi", result.runtime_files_missing)
            self.assertFalse(result.migration_authorized)


if __name__ == "__main__":
    unittest.main()
