#!/usr/bin/env python3
"""Synthetic acceptance/denial controls for static receipt validator."""

import copy
import importlib.util
from pathlib import Path
import json
import unittest

SCRIPT = Path(__file__).with_name("check-phone-receipt.py")
SPEC = importlib.util.spec_from_file_location("receipt_validator", SCRIPT)
assert SPEC and SPEC.loader
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)


class ReceiptValidationTests(unittest.TestCase):
    def setUp(self):
        self.document = json.loads(CHECK.TEMPLATE.read_text())

    def test_default_all_not_run_is_valid_without_fabricated_device_data(self):
        self.assertEqual(CHECK.validate(self.document), [])
        self.assertTrue(all(item["result"] == "NOT_RUN" for item in self.document["cases"]))
        self.assertIsNone(self.document["provenance"]["source_sha"])

    def test_blocked_explained_does_not_require_apk(self):
        case = self.document["cases"][0]
        case.update(result="BLOCKED", observed_state="Owner has not approved an install")
        self.assertEqual(CHECK.validate(self.document), [])

    def accepted(self):
        self.document["provenance"] = {
            "source_sha": "a" * 40, "ci_source_sha": "a" * 40,
            "ci_run_url": "https://github.com/synthetic/repo/actions/runs/123",
            "ci_artifact": "synthetic_apk",
            "apk_sha256": "b" * 64, "apk_package": "app.synthetic.debug",
            "apk_version": "0.0.0", "signing_cert_sha256": "c" * 64,
        }
        self.document["device"] = {
            "model": "synthetic phone", "android_version": "synthetic OS",
            "oneui_version": "synthetic OneUI", "home_fallback_confirmed": True,
        }
        self.document["consent"]["home"] = True
        case = self.document["cases"][1]
        case.update(result="PASS", exact_command="synthetic-only preflight",
                    observed_state="simulated holder check", evidence_class="device-observation",
                    evidence_ref="LOCAL-SYNTHETIC-RECEIPT",
                    rollback_result="NOT_APPLICABLE",
                    rollback_observation="No system setting was changed")
        return case

    def test_pass_requires_exact_provenance_and_consent(self):
        self.accepted()
        self.assertEqual(CHECK.validate(self.document), [])
        for field, value in [
            ("source_sha", None), ("ci_source_sha", "d" * 40),
            ("apk_sha256", None), ("signing_cert_sha256", "bad"),
            ("ci_run_url", "not a URL"), ("apk_package", None),
        ]:
            with self.subTest(field=field):
                clone = copy.deepcopy(self.document)
                clone["provenance"][field] = value
                self.assertTrue(any("provenance" in err for err in CHECK.validate(clone)))
        self.document["consent"]["home"] = False
        self.assertTrue(any("consent" in err for err in CHECK.validate(self.document)))

    def test_pass_with_missing_observation_is_rejected(self):
        case = self.accepted()
        del case["evidence_ref"]
        self.assertTrue(any("evidence" in err for err in CHECK.validate(self.document)))

    def test_fake_latency_and_evidence_on_not_run_rejected(self):
        self.document["cases"][0]["latency_samples_ms"] = [12]
        self.assertTrue(CHECK.validate(self.document))

    def test_duplicate_id_rejected(self):
        self.document["cases"][1]["id"] = self.document["cases"][0]["id"]
        self.assertTrue(any("duplicate" in err for err in CHECK.validate(self.document)))


if __name__ == "__main__":
    unittest.main()
