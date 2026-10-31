"""Toy Box GUI kit: 9-slice-friendly sprites for the vanilla GUI sprite atlas.

Output: out/assets/toybox_minecraft/textures/gui/sprites/kit/<name>.png (+ .png.mcmeta with
vanilla "gui.scaling" nine_slice metadata for the stretchable ones) so the mod can draw them with
the 26.3 (Mojang-named) API
    graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        Identifier.fromNamespaceAndPath("toybox_minecraft", "kit/<name>"), x, y, w, h)
(graphics = net.minecraft.client.gui.GuiGraphicsExtractor) and get correct 9-slice scaling for free. A full manifest (sizes, insets, content padding,
text colours) is written to out/assets/toybox_minecraft/gui/kit.json.

python gen/gui.py
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from common import OUT, PALETTE, hex2rgba, mix, pal_hex, pc, save_png, write_json  # noqa: E402
from tex import CLEAR, from_grid, new  # noqa: E402

H = hex2rgba
SPRITES = {}   # name -> (img, meta)

# ---- UI tones
CREAM, CREAM_HI, PAPER, PAPER_SH, PAPER_DK, PAPER_DEEP = (pc("ui.panel"), pc("ui.panel_hi"), pc("ui.inset"),
                                                          pc("ui.panel_shade"), pc("ui.panel_edge"), pc("ui.paper_deep"))
EDGE = pc("ui.edge")
RIM = pc("ui.control_rim")
WHITE = pc("colors.white")
INK, INK2, INK3 = pc("ramps.ink[2]"), pc("ramps.ink[1]"), pc("ramps.ink[0]")
# text colours recorded in the manifest (hex strings, all from the palette)
T_INK, T_INK2, T_MUTED = pal_hex("ui.text"), pal_hex("ramps.ink[1]"), pal_hex("ui.text_muted")
T_CREAM, T_HI, T_DISABLED = pal_hex("ui.panel"), pal_hex("ui.panel_hi"), pal_hex("ramps.stone[2]")
BR = [H(c) for c in PALETTE["ramps"]["brass"]["tones"]]   # 0 hi .. 4 deep
CL = [H(c) for c in PALETTE["ramps"]["clay"]["tones"]]
WAL = pc("colors.walnut")


def shadow(a):
    return (31, 30, 29, a)


def reg(name, img, slice_=None, padding=None, note="", scaling="nine_slice", text=None):
    meta = {"size": list(img.size), "note": note}
    if slice_:
        l, t, r, b = slice_
        meta["slice"] = {"left": l, "top": t, "right": r, "bottom": b}
        meta["scaling"] = scaling
    if padding:
        meta["content_padding"] = {"left": padding[0], "top": padding[1], "right": padding[2], "bottom": padding[3]}
    if text:
        meta["text_color"] = text
    SPRITES[name] = (img, meta)
    return img


def rounded_box(w, h, *, border, fill, hi=None, lo=None, radius=1, corner_fill=None):
    """Box with 1px border, rounded corners (radius 1 or 2), top highlight row, bottom shade row."""
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            edge = x in (0, w - 1) or y in (0, h - 1)
            c = border if edge else fill
            if not edge:
                if hi is not None and y == 1:
                    c = hi
                if lo is not None and y == h - 2:
                    c = lo
            px[x, y] = c
    # round corners
    for (cx, cy, dx, dy) in ((0, 0, 1, 1), (w - 1, 0, -1, 1), (0, h - 1, 1, -1), (w - 1, h - 1, -1, -1)):
        px[cx, cy] = CLEAR
        if radius >= 2:
            px[cx + dx, cy] = CLEAR
            px[cx, cy + dy] = CLEAR
            px[cx + dx, cy + dy] = border
    return img


def with_drop_shadow(img, rows=2, alphas=(70, 30)):
    """Extend the image downward by `rows` px of soft ink shadow (inside the sprite bounds)."""
    w, h = img.size
    out = new(w, h + rows)
    out.paste(img, (0, 0))
    px = out.load()
    for i in range(rows):
        y = h + i
        for x in range(1 + i, w - 1 - i):
            px[x, y] = shadow(alphas[i])
    return out


# ============================================================== panels & frames
def panels():
    body = rounded_box(32, 30, border=EDGE, fill=CREAM, hi=CREAM_HI, lo=PAPER, radius=2)
    reg("panel_paper", with_drop_shadow(body), (6, 6, 6, 8), (8, 8, 8, 9),
        "Matte paper panel, soft drop shadow. Main window background.", text=T_INK)

    # header strip for frames/panels (title bar), clay accent underline
    hd = new(16, 14)
    px = hd.load()
    for y in range(14):
        for x in range(16):
            c = PAPER if y < 11 else (CL[1] if y == 11 else (CL[2] if y == 12 else CLEAR))
            if y == 0:
                c = PAPER_SH
            px[x, y] = c
    reg("header", hd, (2, 2, 2, 3), (6, 2, 6, 4), "Title strip with clay underline; place at the top inside a panel.", text=T_INK)


# ============================================================== buttons
def button_family(prefix, face, hi, sh, lip1, lip2, border, hover_border, text, text_disabled, disabled):
    w, h = 32, 20

    def make(face_c, hi_c, sh_c, lip_a, lip_b, border_c):
        img = new(w, h)
        px = img.load()
        for y in range(h):
            for x in range(w):
                edge_x = x in (0, w - 1)
                if y == 0 or y == h - 1 or edge_x:
                    c = border_c
                else:
                    if y == 1:
                        c = hi_c
                    elif y <= 15:
                        c = face_c
                    elif y == 16:
                        c = sh_c
                    elif y == 17:
                        c = lip_a
                    else:
                        c = lip_b
                px[x, y] = c
        for (cx, cy) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
            px[cx, cy] = CLEAR
        return img

    reg(f"{prefix}", make(face, hi, sh, lip1, lip2, border), (4, 3, 4, 5), (6, 5, 6, 6),
        f"{prefix}: normal", text=text)
    reg(f"{prefix}_hover", make(mix(face, WHITE, 0.25), WHITE, sh, lip1, lip2, hover_border),
        (4, 3, 4, 5), (6, 5, 6, 6), f"{prefix}: hover (brass outline)", text=text)
    dface, dborder = disabled
    reg(f"{prefix}_disabled", make(dface, dface, dface, mix(dface, dborder, 0.4), mix(dface, dborder, 0.6), dborder),
        (4, 3, 4, 5), (6, 5, 6, 6), f"{prefix}: disabled", text=text_disabled)


def buttons():
    button_family("button", CREAM, CREAM_HI, PAPER_SH, PAPER_DK, EDGE, RIM, BR[2],
                  T_INK, T_DISABLED, (PAPER, PAPER_DK))
    button_family("button_primary", CL[1], CL[0], pc("ui.primary_shade"), CL[2], CL[3], pc("ui.primary_rim"), BR[2],
                  T_HI, T_CREAM, (pc("ui.primary_disabled"), pc("ui.primary_disabled_rim")))


# ============================================================== fields, tooltip, nameplate
def fields():
    for name, border, inner in (("text_field", PAPER_DEEP, PAPER_SH), ("text_field_focused", CL[1], pc("ui.field_focus"))):
        img = new(16, 16)
        px = img.load()
        for y in range(16):
            for x in range(16):
                if x in (0, 15) or y in (0, 15):
                    c = border
                elif y == 1 or x == 1 or (name.endswith("focused") and (x == 14 or y == 14)):
                    c = inner
                else:
                    c = CREAM_HI
                px[x, y] = c
        for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)):
            px[x, y] = CLEAR
        reg(name, img, (3, 3, 3, 3), (5, 4, 5, 3), "Single-line input (console). Caret: ink 1px, blink 500ms.", text=T_INK)

    tip = new(16, 16)
    px = tip.load()
    for y in range(16):
        for x in range(16):
            c = pc("ui.tooltip", 244)
            if x in (0, 15) or y in (0, 15):
                c = INK3
            elif y == 1:
                c = BR[2]
            px[x, y] = c
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)):
        px[x, y] = CLEAR
    reg("tooltip", tip, (4, 4, 4, 4), (5, 5, 5, 4), "Dark ink tooltip with a brass top rule. Text cream (ui.panel).", text=T_CREAM)

    plate = new(16, 12)
    px = plate.load()
    for y in range(12):
        for x in range(16):
            px[x, y] = pc("ui.nameplate")
    for (x, y) in ((0, 0), (15, 0), (0, 11), (15, 11)):
        px[x, y] = CLEAR
    reg("nameplate", plate, (3, 3, 3, 3), (4, 2, 4, 1), "In-world nameplate pill (translucent ink, alpha 220 so names keep >= 4.5:1 even over a bright wall).", text=T_CREAM)


# ============================================================== status dots
DOT = ["..xxx..", ".xxxxx.", "xxxxxxx", "xxxxxxx", "xxxxxxx", ".xxxxx.", "..xxx.."]
STATUS_KEYS = ["idle", "working", "waiting", "done"]


def dots():
    for s in STATUS_KEYS:
        r = [H(c) for c in PALETTE["status_ramps"][s]]
        img = new(7, 7)
        px = img.load()
        for y, row in enumerate(DOT):
            for x, ch in enumerate(row):
                if ch != "x":
                    continue
                edge = (y in (0, 6)) or (x in (0, 6)) or (y in (1, 5) and x in (1, 5))
                c = r[2] if edge else r[1]
                px[x, y] = c
        px[2, 2] = r[0]
        px[3, 2] = mix(r[0], r[1], 0.5)
        px[2, 3] = mix(r[0], r[1], 0.5)
        reg(f"dot_{s}", img, None, None, f"Status dot: {s} {PALETTE['status'][s]}")
        if s != "waiting":
            continue
        # soft halo for the one pulsing state: 11x11
        halo = new(11, 11)
        hp = halo.load()
        for y in range(11):
            for x in range(11):
                d = math.hypot(x - 5, y - 5)
                if 3.6 < d <= 5.2:
                    a = int(max(0, 1 - (d - 3.6) / 1.6) * 110)
                    hp[x, y] = (r[1][0], r[1][1], r[1][2], a)
        halo.alpha_composite(img, (2, 2))
        reg(f"dot_{s}_halo", halo, None, None, f"Status dot with halo (animate alpha for pulsing): {s}")


# ============================================================== tool icons (12x12)
IC = {
    "i": INK2, "I": INK3, "c": CREAM_HI, "C": CREAM, "p": PAPER_DK, "P": PAPER_DEEP,
    "y": BR[1], "Y": BR[2], "k": BR[3], "o": CL[1], "O": CL[2], "t": pc("ramps.teal[1]"), "T": pc("ramps.teal[0]"),
    "s": pc("ramps.sage[1]"), "S": pc("ramps.sage[2]"), "w": WAL, "W": pc("ramps.walnut[1]"), "r": pc("status.error"),
}
ICONS = {
    "team": [
        "............",
        "..iii..iii..",
        "..ici..iti..",
        "..iii..iii..",
        "............",
        ".iiiii.iiii.",
        ".iccci.ittti",
        ".iccci.ittti",
        ".iiiii.iiii.",
        "............",
        "............",
        "............",
    ],
    "tasks": [
        "............",
        ".iiiiiiiiii.",
        ".icccccccci.",
        ".icyciiiici.",
        ".icccccccci.",
        ".icyciiiici.",
        ".icccccccci.",
        ".icyciiiici.",
        ".icccccccci.",
        ".iiiiiiiiii.",
        "............",
        "............",
    ],
    "routines": [
        "............",
        "...iiiiii...",
        "..icccccci..",
        ".icccicccci.",
        ".icccicccci.",
        ".iccciyyyci.",
        ".icccccccci.",
        ".icccccccci.",
        "..iccccci...",
        "...iiiiii...",
        "............",
        "............",
    ],
    "open": [
        "............",
        "......iiiii.",
        ".......iyyi.",
        "......iyiyi.",
        ".iiiiiyii.i.",
        ".icciyi...i.",
        ".iciii......",
        ".iccccci....",
        ".iccccci....",
        ".iiiiiii....",
        "............",
        "............",
    ],
    "preview": [
        "............",
        ".iiiiiiiiii.",
        ".icccccccci.",
        ".ictTTTTtci.",
        ".ictTttTtci.",
        ".ictTTTTtci.",
        ".icccccccci.",
        ".iiiiiiiiii.",
        ".....ii.....",
        "...iiiiii...",
        "............",
        "............",
    ],
    "channels": [
        "............",
        "...ii..ii...",
        "...ii..ii...",
        ".iiiiiiiiii.",
        ".iiiiiiiiii.",
        "...ii..ii...",
        "...ii..ii...",
        ".iiiiiiiiii.",
        ".iiiiiiiiii.",
        "...ii..ii...",
        "...ii..ii...",
        "............",
    ],
    "read": [
        "............",
        ".iii....iii.",
        "iccciiiicCCi",
        "icppcicpppCi",
        "icccciccCCCi",
        "icppcicpppCi",
        "icccciccCCCi",
        "icppciYpppCi",
        "icccciYcCCCi",
        "iiiiiiYiiiii",
        "......k.....",
        "............",
    ],
    "edit": [
        "............",
        "........ii..",
        ".......icPi.",
        "......iyYi..",
        ".....ioOi...",
        "....ioOi....",
        "...ioOi.....",
        "..ioOi......",
        "..iWi.......",
        "..ii........",
        ".i..........",
        "............",
    ],
    "message": [
        "............",
        ".iiiiiiiiii.",
        "icccccccccci",
        "icccccccccCi",
        "icoccoccocCi",
        "icccccccccCi",
        "iCCCCCCCCCCi",
        ".iiiiCiiiii.",
        "....iCi.....",
        "....ii......",
        "............",
        "............",
    ],
    "decision": [
        "............",
        ".....ii.....",
        ".....yi.....",
        "....iiii....",
        "...iyYYki...",
        "..iyYYYYki..",
        "..iYYYYYki..",
        ".iyYYYYYYki.",
        ".iiiiiiiiii.",
        "iWWWWWWWWWWi",
        "iwwwwwwwwwwi",
        ".iiiiiiiiii.",
    ],
}


def icons():
    for name, rows in ICONS.items():
        rows = [r.replace(" ", ".") for r in rows]
        img = from_grid(rows, {**IC, ".": None})
        reg(f"icon_{name}", img, None, None, f"Tool icon '{name}' (12x12, ink + one accent).")


# ============================================================== progress
def progress():
    track = new(16, 6)
    px = track.load()
    for y in range(6):
        for x in range(16):
            c = PAPER_SH
            if y == 0:
                c = PAPER_DEEP
            elif y == 5:
                c = CREAM_HI
            px[x, y] = c
    for (x, y) in ((0, 0), (15, 0), (0, 5), (15, 5)):
        px[x, y] = CLEAR
    reg("progress_track", track, (2, 2, 2, 2), None, "Progress bar track (6px tall).")
    fills = {"brass": PALETTE["ramps"]["brass"]["tones"][1:4]}
    for n, (hi, base, sh) in fills.items():
        f = new(16, 6)
        px = f.load()
        for y in range(6):
            for x in range(16):
                c = H(base)
                if y in (0, 5):
                    c = H(sh)
                elif y == 1:
                    c = H(hi)
                px[x, y] = c
        for (x, y) in ((0, 0), (15, 0), (0, 5), (15, 5)):
            px[x, y] = CLEAR
        reg(f"progress_fill_{n}", f, (2, 2, 2, 2), None, f"Progress bar fill ({n}); draw over the track, width = p * w.")


# ============================================================== task cards
CARD_STATUS = {"todo": "idle", "doing": "working", "done": "done", "blocked": "error"}


def task_cards():
    w, h = 24, 24
    for status, ramp_key in CARD_STATUS.items():
        r = [H(c) for c in PALETTE["status_ramps"][ramp_key]]
        img = new(w, h)
        px = img.load()
        for y in range(h - 1):
            for x in range(w):
                if y == 0 or y == h - 2 or x == w - 1:
                    c = EDGE
                elif x == 0:
                    c = r[2]
                elif x in (1, 2):
                    c = r[1] if x == 1 else r[2]
                elif x == 3:
                    c = CREAM_HI if status != "done" else PAPER
                elif y == 1:
                    c = CREAM_HI
                elif y == h - 3:
                    c = PAPER
                else:
                    c = CREAM if status != "done" else PAPER
                px[x, y] = c
        px[1, 1] = r[0]
        for x in range(1, w - 1):
            px[x, h - 1] = shadow(60)
        for (x, y) in ((0, 0), (w - 1, 0), (0, h - 2), (w - 1, h - 2)):
            px[x, y] = CLEAR
        reg(f"card_{status}", img, (5, 4, 4, 5), (7, 5, 5, 6),
            f"Task card ({status}): status stripe uses the {ramp_key} ramp.", text=T_INK if status != "done" else T_MUTED)


# ============================================================== speech bubble + small widgets
def bubble_and_widgets():
    b = rounded_box(16, 16, border=INK3, fill=CREAM_HI, hi=None, lo=CREAM, radius=2)
    reg("bubble", b, (5, 5, 5, 5), (6, 4, 6, 5), "Speech bubble body (billboard). Draw bubble_tail centred under it.", text=T_INK)
    tail = from_grid([
        "iCCCCCCCi",
        ".iCCCCCi.",
        "..iCCCi..",
        "...iCi...",
        "....i....",
    ], {"i": INK3, "C": CREAM, ".": None})
    reg("bubble_tail", tail, None, None, "Speech bubble tail; overlap its first row with the bubble's bottom border.")
    pill = rounded_box(12, 11, border=BR[2], fill=CREAM_HI, hi=None, lo=CREAM, radius=2)
    reg("pill", pill, (4, 4, 4, 4), (5, 2, 5, 1), "Badge/pill for tags (@agent, branch, file).", text=T_INK)
    div = new(16, 3)
    px = div.load()
    for x in range(16):
        px[x, 0] = PAPER_DK
        px[x, 1] = CREAM_HI
    px[7, 0] = BR[2]
    px[8, 0] = BR[2]
    reg("divider", div, (4, 1, 4, 1), None, "Horizontal rule; stretch horizontally.", scaling="nine_slice")
    for name, on in (("checkbox", False), ("checkbox_checked", True)):
        img = new(10, 10)
        px = img.load()
        for y in range(10):
            for x in range(10):
                c = CREAM_HI
                if x in (0, 9) or y in (0, 9):
                    c = PAPER_DEEP
                elif y == 1 or x == 1:
                    c = PAPER_SH
                px[x, y] = c
        for (x, y) in ((0, 0), (9, 0), (0, 9), (9, 9)):
            px[x, y] = CLEAR
        if on:
            for (x, y) in ((2, 5), (3, 6), (4, 7), (5, 6), (6, 5), (7, 4), (7, 3), (3, 5), (4, 6)):
                px[x, y] = CL[2] if (x, y) in ((4, 7), (7, 3)) else CL[1]
        reg(name, img, None, None, "Checkbox.")


def build():
    panels()
    buttons()
    fields()
    dots()
    icons()
    progress()
    task_cards()
    bubble_and_widgets()
    base = OUT / "textures" / "gui" / "sprites" / "kit"
    manifest = {"version": 1, "atlas_prefix": "toybox_minecraft:kit/",
                "how_to_draw": "graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath(\"toybox_minecraft\", "
                               "\"kit/<name>\"), x, y, w, h) with graphics = net.minecraft.client.gui.GuiGraphicsExtractor "
                               "(Minecraft 26.3, Mojang names). Sprites with a 'slice' entry ship <name>.png.mcmeta "
                               "(gui.scaling nine_slice) so vanilla nine-slices them; the rest (icons, dots, "
                               "bubble tail, checkboxes) are fixed-size and drawn at their own size.",
                "gui_scale_note": "Sprites are authored at 1 texel = 1 GUI pixel; never scale by non-integers.",
                "sprites": {}}
    for name, (img, meta) in sorted(SPRITES.items()):
        save_png(img, base / f"{name}.png")
        if "slice" in meta:
            s = meta["slice"]
            mc = {"gui": {"scaling": {"type": meta["scaling"], "width": img.width, "height": img.height,
                                      "border": {"left": s["left"], "top": s["top"], "right": s["right"], "bottom": s["bottom"]}}}}
            write_json(base / f"{name}.png.mcmeta", mc)
        manifest["sprites"][name] = {"id": f"toybox_minecraft:kit/{name}", **meta}
    write_json(OUT / "gui" / "kit.json", manifest)
    print(f"{len(SPRITES)} gui sprites ->", base)
    return SPRITES


if __name__ == "__main__":
    build()
