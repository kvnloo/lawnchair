#!/usr/bin/env python3
"""Synthetic XML/JSON negative-control tests. No Android build or device."""

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("check-zer0-string-catalog.py")
SPEC = importlib.util.spec_from_file_location("catalog_validator", SCRIPT)
assert SPEC and SPEC.loader
CHECK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECK)


class CatalogValidatorTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.path = "lawnchair/res/values/synthetic.xml"
        dest = self.root / self.path
        dest.parent.mkdir(parents=True)
        self.words = (
            '<resources>'
            "<string name=\"greeting\">Don't skip %1$s and %2$d — 你好</string>"
            '<string name="brand" translatable="false">z0live</string>'
            '</resources>'
        )
        dest.write_text(self.words, encoding="utf-8")
        self.catalog = {
            "catalog_version": 1,
            "entries": [
                self.entry("greeting", "Don't skip %1$s and %2$d — 你好",
                           [{"position": 1, "type": "s"}, {"position": 2, "type": "d"}]),
                self.entry("brand", "z0live", [], False),
            ],
        }

    def entry(self, key, value, placeholders, translatable=True):
        return {
            "key": key, "source": self.path, "source_text": value,
            "source_translatable": translatable,
            "recommend_translatable": translatable,
            "placeholders": placeholders, "usage": "visible-label",
            "context": "Synthetic translator context",
        }

    def check(self):
        return CHECK.validate(self.catalog, self.root)

    def test_valid_positional_unicode_apostrophe_and_nontranslatable_brand(self):
        self.assertEqual(self.check(), [])
        self.assertEqual(CHECK.markers("%% ignored %1$s %2$d"), [
            {"position": 1, "type": "s"}, {"position": 2, "type": "d"},
        ])

    def test_duplicate_ids_rejected(self):
        self.catalog["entries"].append(dict(self.catalog["entries"][0]))
        self.assertTrue(any("duplicate" in item for item in self.check()))

    def test_missing_source_key_rejected(self):
        self.catalog["entries"].pop()
        self.assertTrue(any("missing catalog" in item for item in self.check()))

    def test_changed_placeholder_type_rejected(self):
        self.catalog["entries"][0]["placeholders"][1]["type"] = "s"
        self.assertTrue(any("placeholder" in item for item in self.check()))

    def test_unsafe_bidirectional_control_rejected(self):
        self.catalog["entries"][1]["context"] = "untrusted\u202eoverride"
        self.assertTrue(any("unsafe" in item for item in self.check()))

    def test_renamed_xml_key_rejected(self):
        target = self.root / self.path
        target.write_text(self.words.replace('name="brand"', 'name="changed"'),
                          encoding="utf-8")
        self.assertTrue(any("missing catalog" in item for item in self.check()))

    def test_missing_mandatory_evidence_field_rejected(self):
        del self.catalog["entries"][0]["source_translatable"]
        self.assertTrue(any("missing" in item for item in self.check()))


if __name__ == "__main__":
    unittest.main()
