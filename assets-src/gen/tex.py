"""Tiny pixel-texture toolkit for 16x16 block textures and GUI sprites."""
from __future__ import annotations

from PIL import Image

from common import PALETTE, hex2rgba, paint_grid

CLEAR = (0, 0, 0, 0)


def tones(name):
    if name in PALETTE["ramps"]:
        return [hex2rgba(t) for t in PALETTE["ramps"][name]["tones"]]
    if name in PALETTE["status_ramps"]:
        return [hex2rgba(t) for t in PALETTE["status_ramps"][name]]
    raise KeyError(name)


def new(w=16, h=16, fill=CLEAR):
    return Image.new("RGBA", (w, h), fill)


def grid(img, rows, legend, x=0, y=0):
    return paint_grid(img, x, y, rows, legend)


def from_grid(rows, legend):
    img = new(len(rows[0]), len(rows))
    for r in rows:
        assert len(r) == len(rows[0]), f"ragged grid row {r!r}"
    return grid(img, rows, legend)


def rect(img, x0, y0, x1, y1, c):
    """Fill inclusive rect."""
    px = img.load()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = c


def hsh(*vals) -> int:
    """Deterministic integer hash (no global RNG state)."""
    h = 2166136261
    for v in vals:
        h ^= (int(v) & 0xFFFFFFFF)
        h = (h * 16777619) & 0xFFFFFFFF
        h ^= h >> 13
        h = (h * 0x5BD1E995) & 0xFFFFFFFF
        h ^= h >> 15
    return h


def alpha_mask(img, keep):
    """Copy of img where pixels for which keep(x, y, rgba) is False become transparent."""
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            if not keep(x, y, px[x, y]):
                px[x, y] = CLEAR
    return out
