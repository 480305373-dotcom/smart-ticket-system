import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from distributed_backup import DATABASES, quote, validate_bundle


class BackupBundleTests(unittest.TestCase):
    def test_identifier_quoting_keeps_a_table_name_inside_one_identifier(self):
        self.assertEqual(quote('a";DROP TABLE ticket;--'), '"a"";DROP TABLE ticket;--"')

    def test_missing_service_database_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / "manifest.json").write_text(json.dumps({"format": 1, "databases": {}}))
            with self.assertRaises(ValueError):
                validate_bundle(folder)

    def test_tampering_is_rejected_before_restore(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            records = {}
            for database in DATABASES:
                data = database.encode()
                (folder / (database + ".dump")).write_bytes(data)
                records[database] = {"sha256": hashlib.sha256(data).hexdigest(),
                                     "snapshot": {"extensions": ["plpgsql"]}}
            (folder / "manifest.json").write_text(json.dumps({"format": 1, "databases": records}))
            self.assertEqual(set(validate_bundle(folder)["databases"]), set(DATABASES))
            (folder / "smartticket_knowledge.dump").write_bytes(b"modified")
            with self.assertRaisesRegex(ValueError, "checksum"):
                validate_bundle(folder)

    def test_unknown_extensions_require_an_explicit_restore_implementation(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            records = {}
            for database in DATABASES:
                (folder / (database + ".dump")).write_bytes(b"archive")
                records[database] = {"sha256": hashlib.sha256(b"archive").hexdigest(),
                                     "snapshot": {"extensions": ["plpgsql", "unknown_extension"]}}
            (folder / "manifest.json").write_text(json.dumps({"format": 1, "databases": records}))
            with self.assertRaisesRegex(ValueError, "extensions"):
                validate_bundle(folder)


if __name__ == "__main__":
    unittest.main()
