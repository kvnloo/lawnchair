#!/usr/bin/env python3
"""Offline parser/path/import/generation negative controls."""

import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("audit-zer0-boundaries.py")
SPEC = importlib.util.spec_from_file_location("zer0_audit", SCRIPT)
assert SPEC and SPEC.loader
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)


class BoundaryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def add(self, relative, source):
        target = self.root / AUDIT.SOURCE / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source, encoding="utf-8")

    def test_pure_java_with_jdk_imports_is_safe(self):
        self.add("voice/PhoneActionGate.java", (
            "package app.lawnchair.zer0.voice;\n"
            "import java.util.Set;\nclass PhoneActionGate {}\n"))
        result = AUDIT.scan(self.root)
        self.assertEqual(result["violations"], [])
        self.assertEqual(result["source_file_count"], 1)

    def test_pure_classes_disallow_android_and_network(self):
        self.add("spaces/SpaceModel.java", (
            "import android.content.Context;\nimport java.net.URL;\n"
            "class SpaceModel { int p = R.string.test; }\n"))
        result = AUDIT.scan(self.root)
        self.assertEqual(len(result["violations"]), 1)
        item = result["files"][0]
        self.assertEqual(item["android_imports"], ["android.content.Context"])
        self.assertEqual(item["network_imports"], ["java.net.URL"])
        self.assertTrue(item["resource_reference"])

    def test_launcher_adapter_can_import_android_without_becoming_pure(self):
        self.add("spaces/HyprSpacesActivity.java", (
            "import android.app.Activity;\nclass HyprSpacesActivity {}\n"))
        result = AUDIT.scan(self.root)
        self.assertEqual(result["violations"], [])
        self.assertEqual(result["files"][0]["kind"], "Android/launcher adapter or other")

    def test_generated_source_and_dotfiles_are_excluded(self):
        self.add("spaces/generated/Pretend.java", "import android.app.Activity;\n")
        self.add("voice/VoiceCommand.java", "import java.util.List;\n")
        result = AUDIT.scan(self.root)
        self.assertEqual(result["source_file_count"], 1)
        self.assertEqual(result["violations"], [])

    def test_kotlin_alias_imports_and_integration_seams(self):
        self.add("voice/MyAdapter.kt", (
            "import androidx.compose.runtime.State as UiState\n"
            "import kotlinx.coroutines.flow.Flow\n"
            "class MyAdapter"))
        target = self.root / AUDIT.ENTRYPOINTS["qsb"]
        target.parent.mkdir(parents=True)
        target.write_text("render zer0 fast actions", encoding="utf-8")
        result = AUDIT.scan(self.root)
        self.assertEqual(result["files"][0]["android_imports"],
                         ["androidx.compose.runtime.State"])
        self.assertTrue(result["entrypoints"]["qsb"]["references_zer0"])

    def test_empty_checkout_fails_closed(self):
        self.assertTrue(AUDIT.scan(self.root)["violations"])


if __name__ == "__main__":
    unittest.main()
