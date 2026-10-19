#!/usr/bin/env python3
"""Offline schema and negative controls. No Android tests are performed."""

import copy
import importlib.util
import json
import unittest
from pathlib import Path

source = Path(__file__).with_name("check-promotion-cases.py")
spec = importlib.util.spec_from_file_location("promotion_cases", source)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
cases = Path(__file__).resolve().parents[2] / "docs/zer0/qualification/search-acceptance-cases.json"


class PromotionCasesTests(unittest.TestCase):
    def setUp(self):
        self.doc = json.loads(cases.read_text(encoding="utf-8"))

    def test_catalog_covers_required_cases(self):
        self.assertEqual([], gate.validate(self.doc))

    def reject(self, fn, expected):
        mutated = copy.deepcopy(self.doc)
        fn(mutated)
        self.assertTrue(any(expected in err for err in gate.validate(mutated)))

    def test_missing_case(self):
        self.reject(lambda d: d["cases"].pop(0), "missing required")

    def test_duplicate_id(self):
        self.reject(lambda d: d["cases"].append(d["cases"][0].copy()), "duplicate")

    def test_invalid_source_head(self):
        self.reject(lambda d: d["cases"][0]["carrier"].update(head="current"), "exact source head")

    def test_missing_credit(self):
        self.reject(lambda d: d["cases"][0].update(credits=[]), "provenance")

    def test_wrong_queue(self):
        self.reject(lambda d: d.update(queue="https://github.com/example/other/issues/1"), "canonical")

    def test_unknown_evidence_state(self):
        self.reject(lambda d: d["cases"][0]["evidence"].update(android="PENDING"), "android evidence state")

    def test_false_ready_without_android(self):
        self.reject(lambda d: d["cases"][0].update(status="READY_FOR_HUMAN_PROMOTION_REVIEW"), "gate not passed")

    def test_false_ready_even_with_synthetic_native_pass(self):
        def change(d):
            item = d["cases"][0]
            item["status"] = "READY_FOR_HUMAN_PROMOTION_REVIEW"
            item["evidence"].update(android="PASS", device="PASS", review="PASS")
        self.reject(change, "validated head differs")

    def test_negative_control_cannot_be_ready(self):
        self.reject(lambda d: d["cases"][7].update(status="READY_FOR_HUMAN_PROMOTION_REVIEW"), "negative control")

    def test_missing_test_target(self):
        self.reject(lambda d: d["cases"][0].update(test_target=""), "test target")

    def test_duplicate_not_origin_ready(self):
        self.reject(lambda d: d["cases"][0].update(status="UNREVIEWED"), "unknown promotion status")


if __name__ == "__main__":
    unittest.main()
