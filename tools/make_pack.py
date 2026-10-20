"""Builds the NullCraft rank-badge resource pack (no third-party libraries).

Each rank gets a small pixel badge drawn with a built-in 5-pixel-high font and mapped to a
private-use character, so a LuckPerms prefix can show it with that one character.
Output: src/main/resources/prefix-pack.zip and a mapping printed for the README.

The same zip is the network's only resource pack: it also carries NullCraft's custom item
models (tools/pack-assets) for every version from 1.21 to 1.21.11. 1.21.4+ clients pick a
model through assets/minecraft/items/*.json and the item's custom_model_data string; older
clients only know model "overrides" keyed by a custom_model_data number, so each custom model
also gets a number (LEGACY_MODEL_IDS) that NullCraftPrefixes adds to the item for them.
"""
import json
import os
import struct
import zipfile
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_ZIP = os.path.join(ROOT, "src", "main", "resources", "prefix-pack.zip")

FONT = {
    "A": [".#.", "#.#", "###", "#.#", "#.#"],
    "D": ["##.", "#.#", "#.#", "#.#", "##."],
    "E": ["###", "#..", "##.", "#..", "###"],
    "G": [".##", "#..", "#.#", "#.#", ".##"],
    "H": ["#.#", "#.#", "###", "#.#", "#.#"],
    "I": ["###", ".#.", ".#.", ".#.", "###"],
    "L": ["#..", "#..", "#..", "#..", "###"],
    "M": ["#...#", "##.##", "#.#.#", "#...#", "#...#"],
    "N": ["#..#", "##.#", "#.##", "#..#", "#..#"],
    "O": [".#.", "#.#", "#.#", "#.#", ".#."],
    "P": ["##.", "#.#", "##.", "#..", "#.."],
    "R": ["##.", "#.#", "##.", "#.#", "#.#"],
    "S": [".##", "#..", ".#.", "..#", "##."],
    "T": ["###", ".#.", ".#.", ".#.", ".#."],
    "V": ["#.#", "#.#", "#.#", "#.#", ".#."],
    "W": ["#...#", "#...#", "#.#.#", "##.##", "#...#"],
    " ": ["..", "..", "..", "..", ".."],
}

# custom_model_data string -> number for 1.21-1.21.3 clients (ViaBackwards passes the number on).
# Keep in step with legacy-item-models in src/main/resources/config.yml.
LEGACY_MODEL_IDS = {"god_blade": 1001}
# Vanilla item -> custom models drawn on it, as (custom_model_data string, model).
CUSTOM_ITEMS = {"netherite_sword": [("god_blade", "nullcraft:item/god_blade")]}
VANILLA_ITEM_MODELS = {"netherite_sword": {"parent": "minecraft:item/handheld",
                                           "textures": {"layer0": "minecraft:item/netherite_sword"}}}

GOLD = (255, 215, 64)
WHITE = (255, 255, 255)
# group, label, background, border, text
BADGES = [
    ("godtier1", "GOD I", (150, 115, 10), GOLD, WHITE),
    ("godtier2", "GOD II", (190, 100, 15), (255, 170, 60), WHITE),
    ("godtier3", "GOD III", (160, 25, 25), (255, 90, 90), WHITE),
    ("godtier4", "GOD IV", (100, 35, 140), (200, 110, 255), WHITE),
    ("godtier5", "GOD V", (55, 0, 0), GOLD, GOLD),
]
STAFF = [
    ("helper", (40, 38, 8), (230, 200, 40), (255, 230, 90)),
    ("mod", (10, 25, 60), (80, 140, 255), (160, 195, 255)),
    ("admin", (60, 10, 10), (255, 80, 80), (255, 160, 160)),
]
for role, bg, border, text in STAFF:
    name = role.upper()
    BADGES += [
        ("trial-" + role, "TRIAL " + name, bg, border, text),
        (role, name, bg, border, text),
        ("senior-" + role, "SR " + name, bg, border, text),
        ("head-" + role, "HEAD " + name, bg, border, text),
    ]
BADGES.append(("owner", "OWNER", (40, 0, 0), (200, 0, 0), (255, 70, 70)))


def badge_pixels(label, bg, border, text):
    glyphs = [FONT[c] for c in label]
    text_w = sum(len(g[0]) for g in glyphs) + len(glyphs) - 1
    w, h = text_w + 6, 9
    px = [[(0, 0, 0, 0)] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            edge = y in (0, h - 1) or x in (0, w - 1)
            corner = (x in (0, w - 1)) and (y in (0, h - 1))
            if not corner:
                px[y][x] = (*border, 255) if edge else (*bg, 255)
    x0 = 3
    for g in glyphs:
        for row, line in enumerate(g):
            for col, ch in enumerate(line):
                if ch == "#":
                    px[2 + row][x0 + col] = (*text, 255)
        x0 += len(g[0]) + 1
    return w, h, px


def png_bytes(w, h, px):
    raw = b"".join(b"\x00" + bytes(v for p in row for v in p) for row in px)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    providers, mapping = [], []
    files = {}
    for i, (group, label, bg, border, text) in enumerate(BADGES):
        char = chr(0xE100 + i)
        # HD art from tools/BadgeArt.java (18 px tall, drawn at twice GUI resolution) wins
        # over the built-in 9 px pixel art; "height": 9 keeps both on one chat line.
        hd = os.path.join(ROOT, "tools", "badges", f"badge_{group}.png")
        if os.path.exists(hd):
            with open(hd, "rb") as f:
                files[f"assets/nullcraft/textures/font/badge_{group}.png"] = f.read()
        else:
            w, h, px = badge_pixels(label, bg, border, text)
            files[f"assets/nullcraft/textures/font/badge_{group}.png"] = png_bytes(w, h, px)
        providers.append({"type": "bitmap", "file": f"nullcraft:font/badge_{group}.png",
                          "ascent": 8, "height": 9, "chars": [char]})
        mapping.append((group, label, f"\\u{0xE100 + i:04X}"))
    files["assets/minecraft/font/default.json"] = json.dumps({"providers": providers}, indent=1).encode()
    for item, models in CUSTOM_ITEMS.items():
        # 1.21.4+: item model definition, chosen by the custom_model_data string.
        files[f"assets/minecraft/items/{item}.json"] = json.dumps({"model": {
            "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
            "cases": [{"when": key, "model": {"type": "minecraft:model", "model": model}} for key, model in models],
            "fallback": {"type": "minecraft:model", "model": f"minecraft:item/{item}"}}}, indent=1).encode()
        # 1.21-1.21.3: the vanilla model plus overrides by number. Newer clients ignore
        # "overrides" and draw this model exactly like vanilla.
        legacy = dict(VANILLA_ITEM_MODELS[item])
        legacy["overrides"] = [{"predicate": {"custom_model_data": LEGACY_MODEL_IDS[key]}, "model": model}
                               for key, model in models]
        files[f"assets/minecraft/models/item/{item}.json"] = json.dumps(legacy, indent=1).encode()
    assets = os.path.join(ROOT, "tools", "pack-assets")
    for base, _, names in os.walk(assets):
        for name in names:
            path = os.path.join(base, name)
            files[os.path.relpath(path, assets).replace(os.sep, "/")] = open(path, "rb").read()
    files["pack.mcmeta"] = json.dumps({"pack": {
        # 22 is 1.20.3, the first version that keeps several server packs at once; older
        # declarations made 1.20.3-1.21.3 clients treat the pack as incompatible.
        "pack_format": 22, "supported_formats": {"min_inclusive": 22, "max_inclusive": 999},
        "min_format": 22, "max_format": 999, "description": "§5§lNULL§d§lCRAFT §7Rank badges + custom items (1.21 - 1.21.11)"}}, indent=1).encode()
    os.makedirs(os.path.dirname(OUT_ZIP), exist_ok=True)
    with zipfile.ZipFile(OUT_ZIP, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, files[name])
    for group, label, code in mapping:
        print(f"{group}\t{label}\t{code}")


if __name__ == "__main__":
    main()
