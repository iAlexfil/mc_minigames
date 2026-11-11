#!/usr/bin/env python3
"""Import pinned LesRaisins Dust II geometry and its radar, without executable world data.

Python 3 standard library only. See assets/maps/lesraisins-dust2/SOURCE-Dust2.md
and LICENSE-Dust2.txt for attribution, independent terms and the pinned download.
"""

import argparse
import gzip
import hashlib
from pathlib import Path
import struct
import zipfile
import zlib

SHA256 = "3debce04b6fb9183038b88a0a751927a7b431e23a3a52ee4d9fa570710085803"
MIN_X, MAX_X, MIN_Z, MAX_Z = -25, 130, -34, 119


class NBT:
    """Big-endian Java NBT reader for this archive's 1.16 Anvil sections."""

    def __init__(self, data):
        self.data, self.cursor = data, 0

    def take(self, count):
        if count < 0 or self.cursor + count > len(self.data):
            raise ValueError("Truncated NBT")
        result = self.data[self.cursor:self.cursor + count]
        self.cursor += count
        return result

    def number(self, code):
        return struct.unpack(">" + code, self.take(struct.calcsize(">" + code)))[0]

    def string(self):
        return self.take(self.number("H")).decode("utf-8")

    def tag(self, kind):
        if kind in (1, 2, 3, 4, 5, 6):
            return self.number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        if kind == 7:
            return self.take(self.number("i"))
        if kind == 8:
            return self.string()
        if kind == 9:
            child, count = self.number("B"), self.number("i")
            return [self.tag(child) for _ in range(count)]
        if kind == 10:
            result = {}
            while child := self.number("B"):
                key = self.string()
                result[key] = self.tag(child)
            return result
        if kind in (11, 12):
            return [self.number("i" if kind == 11 else "q") for _ in range(self.number("i"))]
        raise ValueError(f"Unsupported NBT tag {kind}")

    def root(self):
        kind = self.number("B")
        self.string()
        return self.tag(kind)


def decode(source):
    if hashlib.sha256(source.read_bytes()).hexdigest() != SHA256:
        raise ValueError("Expected the pinned dust2.zip; see SOURCE-Dust2.md")
    cells = {}
    with zipfile.ZipFile(source) as archive:
        for name in sorted(archive.namelist()):
            # Never mix Nether/End regions or load entities, functions or player records.
            if not name.startswith("dust2/region/") or not name.endswith(".mca"):
                continue
            data = archive.read(name)
            for chunk_index in range(1024):
                offset = int.from_bytes(data[chunk_index * 4:chunk_index * 4 + 3], "big") * 4096
                if not offset:
                    continue
                length = int.from_bytes(data[offset:offset + 4], "big")
                compression = data[offset + 4]
                raw = data[offset + 5:offset + 4 + length]
                if compression == 2:
                    raw = zlib.decompress(raw)
                elif compression == 1:
                    raw = gzip.decompress(raw)
                elif compression != 3:
                    raise ValueError(f"Unsupported region compression {compression}")
                chunk = NBT(raw).root()
                chunk = chunk.get("Level", chunk)
                cx, cz = chunk["xPos"], chunk["zPos"]
                for section in chunk.get("Sections", []):
                    palette, words = section.get("Palette", []), section.get("BlockStates", [])
                    if not palette:
                        continue
                    bits = max(4, (len(palette) - 1).bit_length())
                    per_word = 64 // bits
                    for index in range(4096):
                        palette_index = 0
                        if words:
                            word = words[index // per_word] & ((1 << 64) - 1)
                            palette_index = (word >> ((index % per_word) * bits)) & ((1 << bits) - 1)
                        state = palette[palette_index]
                        if state["Name"] in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air"):
                            continue
                        pos = (cx * 16 + (index & 15), int(section["Y"]) * 16 + (index >> 8),
                               cz * 16 + ((index >> 4) & 15))
                        if MIN_X <= pos[0] <= MAX_X and 9 <= pos[1] <= 45 and MIN_Z <= pos[2] <= MAX_Z:
                            cells[pos] = state
    return cells


def modern_state(state):
    name, props = state["Name"], dict(state.get("Properties", {}))
    name = {"minecraft:grass": "minecraft:short_grass", "minecraft:grass_path": "minecraft:dirt_path",
            "minecraft:chain": "minecraft:iron_chain"}.get(name, name)
    if name == "minecraft:cauldron" and "level" in props:
        if props["level"] != "0":
            name = "minecraft:water_cauldron"
        else:
            props = {}
    return name + ("[" + ",".join(k + "=" + str(v) for k, v in sorted(props.items())) + "]" if props else "")


def radar(cells, target):
    """Build a standard 128px PNG from actual upper surfaces, using only stdlib."""
    top = {}
    for (x, y, z), state in cells.items():
        if y <= 21 and ((x, z) not in top or y > top[x, z][0]):
            top[x, z] = (y, state["Name"])
    pixels = bytearray()
    for row in range(128):
        pixels.append(0)  # PNG scanline filter: none
        for col in range(128):
            x = round(MIN_X + col / 127 * (MAX_X - MIN_X))
            z = round(MIN_Z + row / 127 * (MAX_Z - MIN_Z))
            y, material = top.get((x, z), (9, "minecraft:air"))
            color = (137, 126, 103)
            if "sand" in material:
                color = (166, 146, 111)
            elif any(word in material for word in ("stone", "andesite", "slate")):
                color = (127, 126, 117)
            elif any(word in material for word in ("oak", "wood", "spruce")):
                color = (119, 91, 65)
            elif "terracotta" in material:
                color = (152, 115, 82)
            elif "red_concrete" in material:
                color = (182, 74, 57)
            elif "blue_concrete" in material:
                color = (60, 101, 151)
            elif material == "minecraft:air":
                color = (52, 55, 52)
            shade = (y - 14) * 1.5
            pixels.extend(int(max(0, min(255, channel + shade))) for channel in color)
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    target.write_bytes(b"\x89PNG\r\n\x1a\n"
                       + chunk(b"IHDR", struct.pack(">IIBBBBB", 128, 128, 8, 2, 0, 0, 0))
                       + chunk(b"IDAT", zlib.compress(bytes(pixels), 9)) + chunk(b"IEND", b""))


def encode(source, target):
    cells = decode(source)
    palette, indices, blocks = [], {}, []
    for (x, y, z), state in sorted(cells.items()):
        text = modern_state(state)
        if text not in indices:
            indices[text] = len(palette)
            palette.append(text)
        blocks.append((x, y + 64, z, indices[text]))
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("wb") as raw:
        with gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as out:
            out.write(b"CSMAP001" + struct.pack(">I", len(palette)))
            for text in palette:
                data = text.encode("utf-8")
                out.write(struct.pack(">H", len(data)) + data)
            out.write(struct.pack(">I", len(blocks)))
            for cell in blocks:
                out.write(struct.pack(">hhhH", *cell))
    radar(cells, target.with_name("radar.png"))
    print(f"Imported {len(blocks)} blocks / {len(palette)} states and radar from archive {SHA256}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    args = parser.parse_args()
    encode(args.source, args.target)
