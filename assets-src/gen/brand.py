"""Reproducible Toy Box connector-mark icon; no external artwork required."""
from PIL import Image, ImageDraw
from common import OUT, pc, save_png


def build():
    image = Image.new("RGBA", (64, 64), pc("toy.shell"))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((3, 3, 60, 60), radius=12, fill=pc("toy.visor"))
    draw.polygon([(14, 23), (31, 14), (49, 23), (32, 32)], fill=pc("toy.shell"))
    draw.polygon([(14, 27), (29, 35), (29, 51), (14, 43)], fill=pc("toy.coral"))
    draw.polygon([(34, 35), (49, 27), (49, 43), (34, 51)], fill=pc("toy.mint"))
    draw.rectangle((28, 18, 34, 22), fill=pc("toy.visor"))
    save_png(image, OUT / "icon.png")

    # One atlas for the 9 × 2 canopy plaque; baked block faces select its 16px tiles.
    plaque = Image.new("RGBA", (144, 32), pc("toy.shell"))
    draw = ImageDraw.Draw(plaque)
    draw.rectangle((0, 0, 143, 31), outline=pc("toy.coral"), width=2)
    letters = ["111/010/010/010/010", "111/101/101/101/111", "101/101/010/010/010",
               "110/101/110/101/110", "111/101/101/101/111", "101/101/010/101/101"]
    x = 24
    for i, letter in enumerate(letters):
        for y, row in enumerate(letter.split("/")):
            for dx, pixel in enumerate(row):
                if pixel == "1":
                    draw.rectangle((x + dx * 4, 6 + y * 4, x + dx * 4 + 3, 9 + y * 4), fill=pc("toy.visor"))
        x += 20 if i == 2 else 16
    save_png(plaque, OUT / "textures" / "block" / "wordmark.png")


if __name__ == "__main__":
    build()
