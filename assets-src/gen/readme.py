"""Generate assets-src/README.md from the real out/ tree (so the manifest never drifts).

python gen/readme.py
"""
from __future__ import annotations

import fnmatch
import json
import sys
from collections import OrderedDict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from common import OUT, ROOT  # noqa: E402

# id: (properties, render layer the quads land in (automatic in 26.3), luminance, shape, notes)
BLOCK_CONTRACT = OrderedDict([
    ("wordmark", ("facing=north|east|south|west, part=0..17", "solid", "0", "thin plaque (back 2 px), nonOpaque",
                  "Static 9 × 2 entrance wordmark. Parts run left to right, top to bottom; baked faces sample one shared texture."))
    ,
    ("monitor", ("facing=north|east|south|west, lit=bool, up|down|left|right=bool", "solid", "lit ? 7 : 0",
                 "thin panel (back 4 px), nonOpaque",
                 "Connectable screen. up/down/left/right = true when the neighbour on that side (as seen by a viewer "
                 "looking at the screen) is a monitor with the same facing; the bezel on that side is then omitted, so "
                 "NxM monitors read as one screen. left = facing.getClockWise(), right = facing.getCounterClockWise(). "
                 "Screen surface is the plane z = 12/16 from the front of a north-facing model (recessed 1 px behind "
                 "the 2 px bezel); draw text there in the BER, inside the bezel (2 px inset on unconnected sides)."))
    ,
    ("task_board", ("facing, up|down|left|right=bool", "solid", "0", "thin panel (back 2 px), nonOpaque",
                    "Task Wall surface (walnut pinboard); same connection rules as monitor. Cards are drawn by the BER "
                    "on the plane z = 14/16 (just proud of the board)."))
    ,
    ("gallery_frame", ("facing, up|down|left|right=bool", "solid", "0", "thin panel (back 2 px), nonOpaque",
                       "Public image frame; same geometry and connections as the task board. The client supplies its image texture."))
    ,
    ("request_podium", ("facing, open=bool", "solid + cutout (auto)", "open ? 9 : 0", "lectern-like, nonOpaque",
                         "Front (emblem) faces the player who placed it (vanilla lectern rule). open=true lights the "
                         "desk paper, the clay lens and the desk bell, and emits redstone strength 15."))
    ,
    ("artifact_shelf", ("facing", "solid", "0", "full cube", "Library shelf: books + archive boxes."))
    ,
    ("artifact_catalog", ("facing", "solid", "0", "full cube", "Card-index drawers (memory index)."))
    ,
    ("preview_bench", ("facing, active=bool", "solid + cutout (auto)", "active ? 6 : 0", "full cube",
                       "Worktop with brass branch-merge inlay; active=true when a merge review is waiting."))
    ,
    ("status_lamp", ("status=off|idle|thinking|working|waiting|error|done", "solid + cutout (auto)", "off ? 0 : 12", "full cube",
                     "CI / agent status lamp; colours are the status palette. Animate 'waiting' in-world with particles, "
                     "not by flipping states."))
    ,
    ("channel_terminal", ("facing", "solid", "6", "cabinet + leaning screen, nonOpaque",
                          "Command console; use (Enter) opens the console screen."))
    ,
    ("glow_panel", ("(none)", "solid + cutout (auto)", "15", "full cube", "Ceiling light: plaster frame, brass mullions, glowing panes."))
    ,
    ("glow_strip", ("facing=up|down|north|south|east|west, axis=x|z", "solid", "12", "2 px strip, nonOpaque, no collision",
                    "facing = direction the lit face points (vanilla end_rod rotation table); it attaches to the "
                    "opposite neighbour. axis = the run direction on a floor/ceiling (set from the placer's horizontal "
                    "facing axis); on walls it always runs horizontally and axis is ignored."))
    ,
    ("plaster_panel", ("(none)", "solid", "0", "full cube", "Warm plaster wall (calcite / white concrete companion)."))
    ,
    ("plaster_frame", ("(none)", "solid", "0", "full cube", "Raised-molding plaster panel for wall articulation."))
    ,
    ("walnut_panel", ("(none)", "solid", "0", "full cube", "Vertical walnut boards (dark oak companion)."))
    ,
    ("walnut_trim", ("(none)", "solid", "0", "full cube (column)", "Walnut with a brass cap rail: wainscot top course."))
    ,
    ("terracotta_tile", ("(none)", "solid", "0", "full cube", "2x2 clay tiles with cream grout."))
    ,
    ("oak_parquet", ("(none)", "solid", "0", "full cube", "Stripped-oak basketweave parquet floor."))
    ,
])

# glob (relative to out/assets/toybox_minecraft) -> description; first match wins
DESCRIBE = [
    ("textures/gui/sprites/kit/*.png.mcmeta", "vanilla gui.scaling nine_slice metadata for the sprite of the same name"),
    ("textures/gui/sprites/kit/icon_*.png", "12x12 icon (page buttons, task cards)"),
    ("textures/gui/sprites/kit/dot_*_halo.png", "11x11 status dot with soft halo (pulse by animating alpha)"),
    ("textures/gui/sprites/kit/dot_*.png", "7x7 status dot"),
    ("textures/gui/sprites/kit/progress_*.png", "progress bar piece (9-slice)"),
    ("textures/gui/sprites/kit/card_*.png", "task card background per status (9-slice)"),
    ("textures/gui/sprites/kit/button*.png", "button state (9-slice, 20 px tall)"),
    ("textures/gui/sprites/kit/*.png", "GUI kit sprite (see gui/kit.json for insets and use)"),
    ("textures/block/*_emissive.png", "emissive overlay (cutout, used by a light_emission=15 element)"),
    ("textures/block/status_lamp_*.png", "status lamp face for one status"),
    ("textures/block/monitor_*.png", "monitor part texture"),
    ("textures/block/task_board_*.png", "task board part texture"),
    ("textures/block/request_podium_*.png", "request podium part texture"),
    ("textures/block/preview_bench_*.png", "preview bench texture"),
    ("textures/block/console_*.png", "channel terminal texture"),
    ("textures/block/*.png", "block texture"),
    ("models/block/monitor_corner_*.json", "monitor bezel corner (outer = both edges open, h/v = that edge continues)"),
    ("models/block/task_board_corner_*.json", "task board trim corner (outer/h/v as monitor)"),
    ("models/block/*_edge_*.json", "connectable bezel/trim edge (omitted when connected on that side)"),
    ("models/block/*_panel_*.json", "connectable panel body (screen/board surface + back + sides)"),
    ("models/block/*_inventory.json", "merged model for the item (panel + all bezels)"),
    ("models/block/status_lamp_*.json", "status lamp model for one status"),
    ("models/block/*.json", "block model"),
    ("blockstates/*.json", "blockstate (property contract below)"),
    ("items/*.json", "1.21.4+/26.x item model definition"),
    ("lang/en_us.json", "English names for every block and item + creative tab"),
    ("gui/kit.json", "GUI kit manifest: every sprite's id, size, 9-slice insets, content padding, text colour"),
    ("gui/ui-style.json", "display/UI colour tokens (monitor log kinds, paper GUI, status)"),
    ("palette.json", "runtime copy of the Toy Box palette"),
]


def describe(rel):
    for pat, d in DESCRIBE:
        if fnmatch.fnmatch(rel, pat):
            return d
    return ""


def build():
    files = sorted(p.relative_to(OUT).as_posix() for p in OUT.rglob("*") if p.is_file())
    groups = OrderedDict()
    for r in files:
        top = r.split("/")[0] if r.count("/") == 0 else "/".join(r.split("/")[:2 if not r.startswith("textures/gui/sprites") else 4])
        groups.setdefault(top, []).append(r)
    kit = json.load(open(OUT / "gui" / "kit.json", encoding="utf-8"))["sprites"]

    lines = []
    w = lines.append
    w("# assets-src: Toy Box art pipeline")
    w("")
    w("Deterministic generators for every Toy Box block texture, model and GUI sprite. Style: \"Toy Box\"")
    w("(docs/visual-bar.md). Agent skins and portraits are not generated here: the mod draws them at runtime")
    w("from each member's avatar (`AvatarArt`). This README is generated by `gen/readme.py`; the manifest")
    w("below is read from the real `out/` tree.")
    w("")
    w("## Regenerate")
    w("")
    w("```sh")
    w("# one-time: project venv (no global installs)")
    w("python3 -m venv assets-src/.venv")
    w("assets-src/.venv/bin/python -m pip install -r assets-src/requirements.txt")
    w("")
    w("assets-src/.venv/bin/python assets-src/build.py            # all shipped assets, a few seconds")
    w("assets-src/.venv/bin/python assets-src/build.py --verify   # rebuild in 2 fresh processes (PYTHONHASHSEED 1, 987654), assert byte-identical")
    w("```")
    w("")
    w("`requirements.txt` pins Pillow (11.3.0): PNG bytes depend on the Pillow/zlib build, and `sync.py --check` compares bytes.")
    w("`out/` is build output and is not committed; the synced copy in `mod/` is.")
    w("")
    w("Single steps: `python gen/blocks.py`, `gen/models.py`, `gen/gui.py`, `gen/ui_style.py`, `gen/brand.py`.")
    w("")
    w("## Sync into the mod")
    w("")
    w("```sh")
    w("python assets-src/sync.py --dry-run   # what would change")
    w("python assets-src/sync.py             # copy out/assets -> mod/src/main/resources/assets")
    w("python assets-src/sync.py --check     # exit 1 if the mod copy is stale (use before a build)")
    w("```")
    w("")
    w("`sync.py` is stdlib-only. It never deletes files it did not put there (ledger:")
    w("`mod/src/main/resources/assets/toybox_minecraft/.art-sync.json`), so mod-owned files such as `icon.png` are safe.")
    w("")
    w("**Lang files are merged, not copied.** Minecraft loads one `lang/<locale>.json` per namespace per pack, so the")
    w("mod's own keys (keybinds, screen titles, messages) and the generated block/item names share")
    w("`assets/toybox_minecraft/lang/en_us.json`. Edit that file in `mod/` as usual: sync owns only the keys it generates")
    w("(`block.toybox_minecraft.*`, `item.toybox_minecraft.*`, `itemGroup.toybox_minecraft`), updates them in place, removes ones")
    w("assets-src stopped generating, and keeps every other key verbatim in the file's order (a mod value for a")
    w("generated key is overwritten with a warning). `--check` compares the merged result by content, so mod-added keys")
    w("or CRLF line endings never count as stale.")
    w("")
    w("## Source files")
    w("")
    w("| file | role |")
    w("|---|---|")
    for f, d in [
        ("palette.json", "Toy Box palette: named colours, material ramps (light to dark, `base` index), status ramps, UI and toy colours"),
        ("ui-style.md / ui-style.json", "in-world monitor + GUI text style guide and tokens (generated by gen/ui_style.py)"),
        ("build.py", "regenerate everything (`--verify` determinism)"),
        ("sync.py", "copy out/assets into the mod (lang files merged with the mod's own keys)"),
        ("gen/common.py, gen/tex.py", "palette loading, grid painting, PNG/JSON writing"),
        ("gen/brand.py", "mod icon and the entrance wordmark plaque"),
        ("gen/blocks.py", "16x16 block textures (hand grids + structured procedural for tileable materials); asserts opacity"),
        ("gen/models.py", "block models, multipart/variant blockstates, item definitions, lang"),
        ("gen/gui.py", "GUI kit sprites + nine_slice .mcmeta + gui/kit.json"),
        ("gen/ui_style.py", "display tokens, WCAG contrast table, ui-style.md"),
        ("gen/validate.py", "resource checks + palette single-source check (no colour literals in shipping generators) + frame symmetry + 9-slice metadata + emissive rules (AO off, shade_direction_override, no obsolete `shade`) + z-fight check (near-coplanar faces) + one panel body per connectable state"),
        ("gen/readme.py", "this README"),
    ]:
        w(f"| `{f}` | {d} |")
    w("")
    w("## Palette")
    w("")
    w("`palette.json` holds every colour value the shipping generators use (named colours, material ramps, status")
    w("ramps, UI and toy colours). Generators reference entries by name (`pc(\"ramps.brass[2]\")`); `gen/validate.py`")
    w("fails the build if a quoted hex literal appears in any shipping generator. Generators may still blend two palette")
    w("entries (`mix`) for glints and falloffs, so not every shipped pixel colour is listed: the build prints the split.")
    w("The mod reads the shipped copy for UI colours (`UiStyle`).")
    w("")
    w("## Block contract (what the Java side must declare)")
    w("")
    w("`facing` = direction the front faces = towards the placing player (`ctx.getHorizontalPlayerFacing().getOpposite()`),")
    w("like vanilla lecterns/furnaces. Emissive parts follow vanilla `cross_emissive`: an exact duplicate element with")
    w("`\"light_emission\": 15` and `\"shade_direction_override\": \"up\"` (26.3 no longer parses the old `\"shade\": false`),")
    w("and every model with one sets `\"ambientocclusion\": false`. **Render layers are automatic in 26.3**: each quad")
    w("goes to SOLID / CUTOUT / TRANSLUCENT from the alpha of the texture region it samples")
    w("(`ChunkSectionLayer.byTransparency`), so no `BlockRenderLayerMap` call is needed; the column below says where")
    w("the quads end up. Non-cube shapes still need `noOcclusion()`. Suggested luminance is for the block's light level")
    w("(separate from the full-bright faces).")
    w("")
    w("| block id | properties | render layer (auto) | luminance | shape | notes |")
    w("|---|---|---|---|---|---|")
    for bid, (props, layer, lum, shape, notes) in BLOCK_CONTRACT.items():
        esc = lambda v: v.replace("|", "\\|")  # noqa: E731  (pipes inside cells break GFM tables)
        w(f"| `toybox_minecraft:{bid}` | `{esc(props)}` | {layer} | {esc(lum)} | {shape} | {esc(notes)} |")
    w("")
    w("## GUI kit")
    w("")
    n9 = sum(1 for m in kit.values() if "slice" in m)
    w("Sprites live in the vanilla GUI sprite atlas: `assets/toybox_minecraft/textures/gui/sprites/kit/<name>.png`. Draw them")
    w("with the Minecraft 26.3 (Mojang-named) API:")
    w("")
    w("```java")
    w("// graphics: net.minecraft.client.gui.GuiGraphicsExtractor")
    w("graphics.blitSprite(RenderPipelines.GUI_TEXTURED,")
    w("        Identifier.fromNamespaceAndPath(\"toybox_minecraft\", \"kit/panel_paper\"), x, y, w, h);")
    w("```")
    w("")
    w(f"{n9} of the {len(kit)} sprites are stretchable and ship a `.png.mcmeta` with `gui.scaling` nine_slice insets, so")
    w(f"vanilla nine-slices them; the other {len(kit) - n9} (icons, dots, bubble tail, checkboxes) are")
    w("fixed-size and drawn at their own size. 1 texel = 1 GUI px. Full manifest with content padding and text colours:")
    w("`assets/toybox_minecraft/gui/kit.json`. Colour/typography rules: `ui-style.md`.")
    w("")
    w("| sprite | size | 9-slice (l,t,r,b) | use |")
    w("|---|---|---|---|")
    for name, m in sorted(kit.items()):
        sl = m.get("slice")
        sls = f"{sl['left']},{sl['top']},{sl['right']},{sl['bottom']}" if sl else "-"
        w(f"| `{name}` | {m['size'][0]}x{m['size'][1]} | {sls} | {m['note']} |")
    w("")
    w("## Asset manifest (every shipped file)")
    w("")
    w(f"{len(files)} files under `out/assets/toybox_minecraft/`.")
    w("")
    for g, rels in groups.items():
        w(f"### {g}")
        w("")
        w("| file | use |")
        w("|---|---|")
        for r in rels:
            w(f"| `{r}` | {describe(r)} |")
        w("")
    with open(ROOT / "README.md", "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines))
    print("README.md:", len(files), "files listed")


if __name__ == "__main__":
    build()
