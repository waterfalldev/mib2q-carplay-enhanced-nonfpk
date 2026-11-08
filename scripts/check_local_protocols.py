#!/usr/bin/env python3
"""Check duplicated constants at shipping Java/C transport boundaries (no HU)."""
import ast
import re
import struct
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def numeric(expr):
    expr = re.sub(r"\b(0x[0-9a-fA-F]+|[0-9]+)[uUlL]+\b", r"\1", expr)
    expr = re.sub(r"\((?:byte|int)\)", "", expr).strip()
    tree = ast.parse(expr, mode="eval")
    def value(node):
        if isinstance(node, ast.Constant) and type(node.value) is int:
            return node.value
        if isinstance(node, ast.UnaryOp):
            if isinstance(node.op, ast.USub): return -value(node.operand)
            if isinstance(node.op, ast.UAdd): return value(node.operand)
        if isinstance(node, ast.BinOp):
            a, b = value(node.left), value(node.right)
            if isinstance(node.op, ast.Mult): return a * b
            if isinstance(node.op, ast.LShift): return a << b
            if isinstance(node.op, ast.BitOr): return a | b
        raise ValueError(f"unsupported integer constant: {expr}")
    return value(tree.body)


def constants(path, java=False):
    text = (ROOT / path).read_text(encoding="utf-8")
    text = re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.S)
    # A valueless header guard must not consume the next #define as its value.
    pattern = r"\b(?:int|byte|long)\s+(\w+)\s*=\s*([^;]+);" if java else r"^#define[ \t]+(\w+)[ \t]+([^\n]+)"
    return dict(re.findall(pattern, text, re.M))


checks = 0

def compare(c_path, java_path, mapping, shared=(), c_only=()):
    global checks
    c, j = constants(c_path), constants(java_path, True)
    pairs = list(mapping.items())
    cnames = {key for key in c if key.startswith(shared)}
    jnames = {key for key in j if key.startswith(shared)}
    missing_java = cnames - jnames - set(c_only)
    missing_c = jnames - cnames
    if missing_java:
        raise SystemExit(f"Protocol names mismatch: {java_path} missing Java peers: {', '.join(sorted(missing_java))}")
    if missing_c:
        raise SystemExit(f"Protocol names mismatch: {c_path} missing C peers: {', '.join(sorted(missing_c))}")
    pairs += [(key, key) for key in sorted(cnames & jnames)]
    for cn, jn in pairs:
        cv, jv = numeric(c[cn]), numeric(j[jn])
        if cv != jv:
            raise SystemExit(f"Protocol mismatch: {c_path}:{cn}={cv}, {java_path}:{jn}={jv}")
        checks += 1


compare("hook/framework/bus_protocol.h", "java_patch/com/luka/carplay/bus/CarplayBus.java", {
    "BUS_TCP_PORT": "PORT", "BUS_MAGIC": "MAGIC", "BUS_HEADER_SIZE": "HEADER_SIZE",
    "BUS_MAX_PAYLOAD": "MAX_PAYLOAD", "BUS_FLAG_STICKY": "FLAG_STICKY",
    "BUS_FLAG_BINARY": "FLAG_BINARY",
}, ("CMD_", "EVT_"))
compare("hook/framework/bus.c", "java_patch/com/luka/carplay/bus/CarplayBus.java", {"MAX_TYPES": "MAX_TYPES"})
bus_limit = numeric(constants("hook/framework/bus.c")["MAX_TYPES"])
# Commands and events share one dispatch table in each peer. Matching peers can
# still be broken if two distinct symbols claim the same table slot.
for path, java in (("hook/framework/bus_protocol.h", False),
                   ("java_patch/com/luka/carplay/bus/CarplayBus.java", True)):
    slots = {}
    for name, value in constants(path, java).items():
        if not name.startswith(("CMD_", "EVT_")):
            continue
        opcode = numeric(value)
        if not 0 <= opcode < bus_limit:
            raise SystemExit(f"Bus dispatch table cannot register {name}={opcode} in {path}; expected 0 <= opcode < {bus_limit}")
        if opcode in slots:
            raise SystemExit(f"Duplicate bus opcode {opcode} in {path}: {slots[opcode]} and {name}")
        slots[opcode] = name
compare("maneuver_render/protocol.h", "java_patch/com/luka/carplay/rgd/RendererServer.java", {
    "CR_TCP_PORT": "PORT", "CR_PKT_SIZE": "PKT_SIZE",
}, ("CMD_", "EVT_", "MAN_FLAG_"),
    # The Java client deliberately omits the renderer's diagnostic/lifecycle
    # commands. Every other renderer opcode and maneuver flag has a peer.
    c_only=("CMD_SCREENSHOT", "CMD_SHUTDOWN", "CMD_PERSPECTIVE", "CMD_DEBUG"))
compare("altscreen_render/protocol.h", "java_patch/com/luka/carplay/core/AltscreenControlServer.java", {
    "ALTCTL_PORT": "PORT", "ALTEVT_VIDEO_ON": "EVT_VIDEO_ON", "ALTEVT_VIDEO_OFF": "EVT_VIDEO_OFF",
    "ALTEVT_LOADER_READY": "EVT_LOADER_READY",
    "ALTCMD_VIEW_AREA": "CMD_VIEW_AREA", "ALTCMD_READINESS": "CMD_READINESS",
})

MOST = "java_patch/com/luka/carplay/cluster/MostPresentation.java"
compare("maneuver_render/protocol.h", MOST, {
    "CR_MAP_DISPLAYABLE_ID": "MAP_DISPLAYABLE", "CR_OUTPUT_MIN": "OUTPUT_MIN",
    "CR_OUTPUT_MAX": "OUTPUT_MAX",
})

def same_path(c_path, c_name, java_path):
    """Java names these files as string literals (tests point them at scratch files)."""
    global checks
    value = constants(c_path)[c_name].strip()
    if not value.startswith('"') or value not in (ROOT / java_path).read_text(encoding="utf-8"):
        raise SystemExit(f"Path mismatch: {c_path}:{c_name} not named by {java_path}")
    checks += 1

# The renderer's request/ready files.
for name in ("CR_MOST_OUTPUT_PATH", "CR_MOST_OUTPUT_READY_PATH",
             "CR_MOST_MAP_OUTPUT_PATH", "CR_MOST_MAP_OUTPUT_READY_PATH"):
    same_path("maneuver_render/protocol.h", name, MOST)
print(f"Local transport constants: {checks} matching Java/C ports, opcodes, flags, sizes and limits")
jar = ROOT / "build/carplay_hook.jar"
if not jar.exists():
    raise SystemExit("Build Java first to check the shipping class-file target")
with zipfile.ZipFile(jar) as archive:
    classes = [name for name in archive.namelist() if name.endswith(".class")]
    if not classes:
        raise SystemExit("Empty patch JAR")
    for name in classes:
        magic, minor, major = struct.unpack(">IHH", archive.read(name)[:8])
        if magic != 0xCAFEBABE or (minor, major) != (0, 48):
            raise SystemExit(f"Unexpected Java 1.4 target: {name} version={major}.{minor}")
    print(f"Shipping JAR: {len(classes)} classes at Java 1.4 class-file version 48.0")
