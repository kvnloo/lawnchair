#!/usr/bin/env python3
"""Synthetic negative controls; test fixtures are not real GitHub/APK/device evidence."""

import copy
import hashlib
import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path

script = Path(__file__).with_name("check-build-evidence.py")
spec = importlib.util.spec_from_file_location("build_evidence", script)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class BuildEvidenceTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.apk = Path(tmp.name) / "synthetic.apk"
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("AndroidManifest.xml", "dummy text, NOT an Android APK")
        h = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        self.record = {
            "schema_version": 1,
            "source": {"sha": "a" * 40, "ref": "chatgpt/test"},
            "ci": {
                "run_id": 7,
                "run_url": "https://github.com/example/lawnchair/actions/runs/7",
                "head_sha": "a" * 40, "conclusion": "success",
                "jobs": [
                    {"name": "build", "required": True, "state": "success"},
                    {"name": "release", "required": False, "state": "skipped"},
                ],
            },
            "artifact": {
                "source_sha": "a" * 40, "run_id": 7,
                "package_id": "app.lawnchair.play.debug", "variant": "PlayDebug",
                "sha256": h, "signer_sha256": "b" * 64,
                "url": "https://github.com/example/lawnchair/actions/runs/7",
            },
            "device": {
                "status": "PASS", "owner_consented": True,
                "source_sha": "a" * 40, "installed_apk_sha256": h,
                "test_target": "synthetic negative-control case",
                "observations": "dummy, no actual device",
            },
        }

    def reject(self, change, expected, claim="ci"):
        entry = copy.deepcopy(self.record)
        change(entry)
        errors = gate.validate(entry, claim, self.apk if claim != "ci" else None)
        self.assertTrue(any(expected in error for error in errors), errors)

    def test_success_with_optional_release_skip(self):
        self.assertEqual([], gate.validate(self.record, "ci"))

    def test_artifact_metadata_and_local_bytes(self):
        self.assertEqual([], gate.validate(self.record, "artifact", self.apk))

    def test_device_consistency_is_not_attestation(self):
        self.assertEqual([], gate.validate(self.record, "device", self.apk))

    def test_failed_required_job(self):
        self.reject(lambda x: x["ci"]["jobs"][0].update(state="failure"), "required job")

    def test_skipped_required_job(self):
        self.reject(lambda x: x["ci"]["jobs"][0].update(state="skipped"), "required job")

    def test_cancelled_required_job(self):
        self.reject(lambda x: x["ci"]["jobs"][0].update(state="cancelled"), "required job")

    def test_pending_required_job(self):
        self.reject(lambda x: x["ci"]["jobs"][0].update(state="pending"), "required job")

    def test_missing_jobs(self):
        self.reject(lambda x: x["ci"].pop("jobs"), "jobs missing")

    def test_no_required_job(self):
        self.reject(lambda x: x["ci"]["jobs"][0].update(required=False), "no required")

    def test_duplicate_job(self):
        self.reject(lambda x: x["ci"]["jobs"].append(x["ci"]["jobs"][0].copy()), "duplicate")

    def test_different_head(self):
        self.reject(lambda x: x["ci"].update(head_sha="c" * 40), "head_sha")

    def test_mismatched_run_id(self):
        self.reject(lambda x: x["artifact"].update(run_id=6), "run ID", "artifact")

    def test_mismatched_artifact_head(self):
        self.reject(lambda x: x["artifact"].update(source_sha="c" * 40), "source SHA", "artifact")

    def test_mismatched_apk_hash(self):
        self.reject(lambda x: x["artifact"].update(sha256="d" * 64), "APK SHA256", "artifact")

    def test_missing_signing_fingerprint(self):
        self.reject(lambda x: x["artifact"].pop("signer_sha256"), "signer", "artifact")

    def test_requires_local_apk(self):
        self.assertTrue(gate.validate(self.record, "artifact"))

    def test_missing_device_consent(self):
        self.reject(lambda x: x["device"].update(owner_consented=False), "consent", "device")

    def test_mismatched_installed_apk(self):
        self.reject(lambda x: x["device"].update(installed_apk_sha256="0" * 64), "installed APK", "device")

    def test_missing_device_observation(self):
        self.reject(lambda x: x["device"].update(observations=""), "observations", "device")


if __name__ == "__main__":
    unittest.main()
