#!/usr/bin/env python3
"""Run the shipping checker against isolated protocol/JAR fixtures."""
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
C_BUS = "hook/framework/bus_protocol.h"
JAVA_BUS = "java_patch/com/luka/carplay/bus/CarplayBus.java"
SOURCES = (
    "scripts/check_local_protocols.py", C_BUS, JAVA_BUS,
    "hook/framework/bus.c", "maneuver_render/protocol.h",
    "java_patch/com/luka/carplay/rgd/RendererServer.java",
    "altscreen_render/protocol.h",
    "java_patch/com/luka/carplay/core/AltscreenControlServer.java",
    "java_patch/com/luka/carplay/cluster/MostPresentation.java",
    "build/carplay_hook.jar",
)


class LocalProtocolChecks(unittest.TestCase):
    def setUp(self):
        self.scratch = tempfile.TemporaryDirectory(prefix="local-protocols-")
        self.addCleanup(self.scratch.cleanup)
        self.root = Path(self.scratch.name)
        for relative in SOURCES:
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, target)

    def set_value(self, relative, name, value):
        path = self.root / relative
        source = path.read_text(encoding="utf-8")
        if relative.endswith(".java"):
            pattern = r"(\b(?:int|byte|long)\s+" + name + r"\s*=\s*)[^;]+"
        else:
            pattern = r"(^#define[ \t]+" + name + r"[ \t]+)[^\n]+"
        source, count = re.subn(pattern, lambda match: match[1] + value, source, flags=re.M)
        self.assertEqual(count, 1, name)
        path.write_text(source, encoding="utf-8")

    def remove_symbol(self, relative, name):
        path = self.root / relative
        source = path.read_text(encoding="utf-8")
        source, count = re.subn(r"^.*\b" + name + r"\b.*(?:\n|$)", "", source, count=1, flags=re.M)
        self.assertEqual(count, 1, name)
        path.write_text(source, encoding="utf-8")

    def check(self, expected=None):
        run = subprocess.run(
            [sys.executable, "-B", str(self.root / "scripts/check_local_protocols.py")],
            capture_output=True, text=True, check=False,
        )
        output = run.stdout + run.stderr
        if expected is None:
            self.assertEqual(run.returncode, 0, output)
            self.assertIn("Shipping JAR:", output)
        else:
            self.assertNotEqual(run.returncode, 0, output)
            self.assertIn(expected, output)

    def test_current_protocols_and_shipping_jar_pass(self):
        self.check()

    def test_matching_duplicate_commands_fail(self):
        for relative in (C_BUS, JAVA_BUS):
            self.set_value(relative, "CMD_MOST_MAP_APPEARANCE", "0x011C")
        self.check("Duplicate bus opcode")

    def test_matching_command_event_collision_fails(self):
        for relative in (C_BUS, JAVA_BUS):
            self.set_value(relative, "CMD_MOST_MAP_APPEARANCE", "0x0050")
        self.check("Duplicate bus opcode")

    def test_matching_duplicate_events_fail(self):
        for relative in (C_BUS, JAVA_BUS):
            self.set_value(relative, "EVT_MOST_MAP_VIDEO", "0x0041")
        self.check("Duplicate bus opcode")

    def test_missing_java_peer_fails(self):
        self.remove_symbol(JAVA_BUS, "CMD_MOST_MAP_APPEARANCE")
        self.check("missing Java peers: CMD_MOST_MAP_APPEARANCE")

    def test_missing_c_peer_fails(self):
        self.remove_symbol(C_BUS, "CMD_MOST_MAP_APPEARANCE")
        self.check("missing C peers: CMD_MOST_MAP_APPEARANCE")

    def test_missing_renderer_flag_peer_fails(self):
        self.remove_symbol("java_patch/com/luka/carplay/rgd/RendererServer.java", "MAN_FLAG_SNAP_TO_ROAD")
        self.check("missing Java peers: MAN_FLAG_SNAP_TO_ROAD")

    def test_one_sided_value_drift_fails(self):
        self.set_value(JAVA_BUS, "CMD_MOST_MAP_APPEARANCE", "0x011E")
        self.check("Protocol mismatch:")

    def test_matching_out_of_range_opcodes_fail(self):
        for value in ("-1", "0x0120", "0xFFFF"):
            with self.subTest(value=value):
                for relative in (C_BUS, JAVA_BUS):
                    self.set_value(relative, "CMD_MOST_MAP_APPEARANCE", value)
                self.check("Bus dispatch table cannot register CMD_MOST_MAP_APPEARANCE=")

    def test_limit_drift_fails(self):
        self.set_value("hook/framework/bus.c", "MAX_TYPES", "0x011D")
        self.check("Protocol mismatch:")

    def test_matching_limit_reduction_fails(self):
        for relative in ("hook/framework/bus.c", JAVA_BUS):
            self.set_value(relative, "MAX_TYPES", "0x011D")
        self.check("Bus dispatch table cannot register CMD_MOST_MAP_APPEARANCE=")

    def test_missing_shipping_jar_fails(self):
        (self.root / "build/carplay_hook.jar").unlink()
        self.check("Build Java first")


if __name__ == "__main__":
    unittest.main()
