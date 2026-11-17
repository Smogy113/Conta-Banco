#!/usr/bin/env python3
"""Gera o icone do Brigada Jato (soldado com jetpack) em varios tamanhos."""
import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..")


def draw_icon(size):
    s = size * 4  # supersample
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    u = s / 100.0
    # fundo: circulo laranja com borda escura
    d.ellipse([2 * u, 2 * u, 98 * u, 98 * u], fill=(21, 21, 28))
    d.ellipse([6 * u, 6 * u, 94 * u, 94 * u], fill=(255, 179, 0))
    d.ellipse([6 * u, 6 * u, 94 * u, 60 * u], fill=(255, 202, 40))
    # chama do jetpack
    d.polygon([(30 * u, 62 * u), (42 * u, 62 * u), (36 * u, 92 * u)], fill=(255, 87, 34))
    d.polygon([(32 * u, 62 * u), (40 * u, 62 * u), (36 * u, 82 * u)], fill=(255, 235, 59))
    # jetpack
    d.rounded_rectangle([28 * u, 38 * u, 44 * u, 64 * u], radius=4 * u, fill=(21, 21, 28))
    d.rounded_rectangle([30 * u, 40 * u, 42 * u, 62 * u], radius=3 * u, fill=(127, 140, 141))
    # corpo
    d.rounded_rectangle([38 * u, 42 * u, 62 * u, 70 * u], radius=6 * u, fill=(21, 21, 28))
    d.rounded_rectangle([41 * u, 45 * u, 59 * u, 67 * u], radius=4 * u, fill=(76, 175, 80))
    # arma
    d.rectangle([52 * u, 50 * u, 86 * u, 58 * u], fill=(21, 21, 28))
    d.rectangle([54 * u, 52 * u, 84 * u, 56 * u], fill=(93, 109, 126))
    # cabeca
    d.ellipse([39 * u, 18 * u, 63 * u, 42 * u], fill=(21, 21, 28))
    d.ellipse([42 * u, 21 * u, 60 * u, 39 * u], fill=(241, 194, 125))
    # capacete
    d.pieslice([38 * u, 14 * u, 64 * u, 40 * u], 180, 360, fill=(21, 21, 28))
    d.pieslice([41 * u, 17 * u, 61 * u, 37 * u], 180, 360, fill=(46, 125, 50))
    # oculos
    d.rectangle([51 * u, 27 * u, 61 * u, 32 * u], fill=(0, 188, 212))
    return im.resize((size, size), Image.LANCZOS)


def main():
    draw_icon(128).save(os.path.join(ROOT, "assets", "icon.png"))
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for k, v in sizes.items():
        d = os.path.join(ROOT, "android", "src", "main", "res", "mipmap-" + k)
        os.makedirs(d, exist_ok=True)
        draw_icon(v).save(os.path.join(d, "ic_launcher.png"))
    draw_icon(512).save(os.path.join(ROOT, "tools", "icon-512.png"))
    print("icones gerados")


if __name__ == "__main__":
    main()
