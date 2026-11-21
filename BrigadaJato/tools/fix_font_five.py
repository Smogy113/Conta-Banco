"""Redesenha o "5" da Pixelify Sans (OFL), que e igual ao "S", com topo reto e haste a esquerda.
Uso: python3 tools/fix_font_five.py  (altera assets/fonts/pixel_ui.ttf e pixel_title.ttf)"""
import os
from fontTools.ttLib import TTFont
from fontTools.pens.ttGlyphPen import TTGlyphPen

HERE = os.path.dirname(os.path.abspath(__file__))
# (arquivo, colunas e linhas do desenho de pixels de cada peso)
SPECS = {
    "pixel_ui.ttf": dict(x=(61, 150, 179, 417, 446, 536), y=(-11, 75, 113, 200, 337, 424, 548, 636)),
    "pixel_title.ttf": dict(x=(61, 149, 188, 414, 453, 542), y=(-11, 74, 125, 211, 331, 416, 552, 638)),
}


def five(spec):
    X0, X1, X2, X3, X4, X5 = spec["x"]
    Yb, Y1, Y2, Y3, Ym0, Ym1, Yt0, Yt1 = spec["y"]
    return [
        (X0, Yt0, X5, Yt1),  # barra de cima (reta)
        (X0, Ym0, X2, Yt1),  # haste da esquerda
        (X0, Ym0, X4, Ym1),  # barra do meio
        (X3, Y1, X5, Ym0),   # barriga da direita
        (X1, Yb, X4, Y2),    # barra de baixo
        (X0, Y1, X2, Y3),    # pontinha de baixo
    ]


for fn, spec in SPECS.items():
    path = os.path.join(HERE, "..", "assets", "fonts", fn)
    f = TTFont(path)
    name = f.getBestCmap()[ord("5")]
    pen = TTGlyphPen(None)
    for x0, y0, x1, y1 in five(spec):
        pen.moveTo((x0, y0))
        pen.lineTo((x0, y1))
        pen.lineTo((x1, y1))
        pen.lineTo((x1, y0))
        pen.closePath()
    g = pen.glyph()
    f["glyf"][name] = g
    g.recalcBounds(f["glyf"])
    f.save(path)
    print("ok", fn)
