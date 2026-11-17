"""Gera dois chaveiros de gatinhos ORIGINAIS para impressao 3D.

Cada chaveiro tem:
  - base (cabeca + argola) com 4 mm de espessura
  - detalhes em relevo (+1 mm) separados por cor, para impressao multicor (AMS/MMU)
  - um arquivo *_completo.stl de peca unica para impressora de uma cor

Uso: python3 gerar_chaveiros.py   (requer: trimesh manifold3d shapely mapbox_earcut matplotlib)
"""
import os
import numpy as np
import trimesh
from shapely.geometry import Point, Polygon, LineString, box
from shapely.ops import unary_union
from shapely import affinity

BASE_H = 4.0      # espessura da base (mm)
RELEVO_H = 1.0    # altura dos detalhes (mm)
OUT = os.path.dirname(os.path.abspath(__file__))


def ellipse(cx, cy, rx, ry):
    return affinity.scale(Point(cx, cy).buffer(1, 64), rx, ry)


def stroke(pts, w):
    return LineString(pts).buffer(w / 2, cap_style=1, join_style=1)


def arc(cx, cy, r, a0, a1, n=24):
    t = np.radians(np.linspace(a0, a1, n))
    return list(zip(cx + r * np.cos(t), cy + r * np.sin(t)))


def ear(x_base0, x_base1, tip, y_base):
    return Polygon([(x_base0, y_base), tip, (x_base1, y_base)])


def head_with_ring(head, ears, ring_y):
    """Silhueta + argola no topo, com furo de 5 mm."""
    body = unary_union([head] + ears).buffer(1.5).buffer(-1.5)  # arredonda cantos
    tab = Point(0, ring_y).buffer(6, 64)
    bridge = box(-4, ring_y - 6, 4, ring_y)
    shape = unary_union([body, tab, bridge]).buffer(1).buffer(-1)
    return shape.difference(Point(0, ring_y).buffer(2.6, 64))


# ---------------------------------------------------------------- Gato 1: "Soneca"
def gato_soneca():
    head = ellipse(0, 0, 24, 18)
    ears = [ear(-22, -6, (-17, 24), 8), ear(6, 22, (17, 24), 8)]
    base = head_with_ring(head, ears, 21)

    inner_ears = unary_union([
        Polygon([(-18.5, 10), (-16.5, 19.5), (-10, 12)]),
        Polygon([(18.5, 10), (16.5, 19.5), (10, 12)]),
    ])
    olhos = unary_union([  # olhos fechados, arcos "felizes"
        stroke(arc(-9, 1, 4, 20, 160), 1.6),
        stroke(arc(9, 1, 4, 20, 160), 1.6),
    ])
    nariz = Polygon([(-2, -3), (2, -3), (0, -5.2)]).buffer(0.4)
    boca = unary_union([
        stroke(arc(-1.6, -5.4, 1.6, 180, 360), 1.0),
        stroke(arc(1.6, -5.4, 1.6, 180, 360), 1.0),
    ])
    bigodes = unary_union([
        stroke([(-14, -3), (-21, -1.5)], 0.9),
        stroke([(-14, -5.5), (-21, -6.5)], 0.9),
        stroke([(14, -3), (21, -1.5)], 0.9),
        stroke([(14, -5.5), (21, -6.5)], 0.9),
    ])
    bochechas = unary_union([ellipse(-11.5, -6.5, 3, 2), ellipse(11.5, -6.5, 3, 2)])
    preto = unary_union([olhos, boca, bigodes])
    rosa = unary_union([inner_ears, nariz, bochechas]).difference(preto.buffer(0.3))
    return base, {"preto": preto, "rosa": rosa}


# ---------------------------------------------------------------- Gato 2: "Faisca"
def gato_faisca():
    head = ellipse(0, 0, 21, 19)
    ears = [ear(-20, -5, (-19, 26), 7), ear(5, 20, (19, 26), 7)]
    base = head_with_ring(head, ears, 22)

    inner_ears = unary_union([
        Polygon([(-17, 10), (-17.5, 21), (-9, 13)]),
        Polygon([(17, 10), (17.5, 21), (9, 13)]),
    ])
    olho_e = ellipse(-8, 1, 4, 5)
    olho_d = ellipse(8, 1, 4, 5)
    brilho = unary_union([Point(-6.6, 3).buffer(1.3, 32), Point(9.4, 3).buffer(1.3, 32)])
    olhos = unary_union([olho_e, olho_d]).difference(brilho)  # brilho fica na cor da base
    nariz = ellipse(0, -5, 1.8, 1.2)
    sorriso = Polygon(arc(0, -6.5, 4, 200, 340, 32)).buffer(0.3)
    lingua = ellipse(0, -9.7, 1.6, 1.0).intersection(sorriso.buffer(-0.5))
    sorriso = sorriso.difference(lingua.buffer(0.3))
    estrela_pts = []
    for i in range(10):
        r = 3.2 if i % 2 == 0 else 1.4
        a = np.radians(90 + i * 36)
        estrela_pts.append((r * np.cos(a), 12 + r * np.sin(a)))
    estrela = Polygon(estrela_pts)
    preto = unary_union([olhos, sorriso])
    rosa = unary_union([inner_ears, nariz, lingua])
    amarelo = estrela
    return base, {"preto": preto, "rosa": rosa, "amarelo": amarelo}


def extrude(geom, h, z0):
    geoms = getattr(geom, "geoms", [geom])
    meshes = []
    for g in geoms:
        if g.area < 1e-3:
            continue
        m = trimesh.creation.extrude_polygon(g, h)
        m.apply_translation([0, 0, z0])
        meshes.append(m)
    return trimesh.util.concatenate(meshes)


def build(nome, base, partes, cor_base):
    base_mesh = extrude(base, BASE_H, 0)
    base_mesh.export(os.path.join(OUT, f"{nome}_base_{cor_base}.stl"))
    todos = [base_mesh]
    for cor, geom in partes.items():
        geom = geom.intersection(base.buffer(-0.5))
        m = extrude(geom, RELEVO_H, BASE_H)
        m.export(os.path.join(OUT, f"{nome}_relevo_{cor}.stl"))
        todos.append(m)
    completo = trimesh.boolean.union(todos, engine="manifold")
    completo.export(os.path.join(OUT, f"{nome}_completo.stl"))
    b = completo.bounds
    print(f"{nome}: {b[1][0]-b[0][0]:.1f} x {b[1][1]-b[0][1]:.1f} x {b[1][2]-b[0][2]:.1f} mm,"
          f" estanque={completo.is_watertight}")
    return base, partes


def preview(itens, path):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    cores = {"preto": "#222222", "rosa": "#f4a6b8", "amarelo": "#ffd23f",
             "branco": "#f5f0e6", "lilas": "#b79ce0"}
    fig, axs = plt.subplots(1, len(itens), figsize=(5 * len(itens), 5))
    for ax, (titulo, base, partes, cor_base) in zip(axs, itens):
        for g, c in [(base, cor_base)] + [(p, k) for k, p in partes.items()]:
            for poly in getattr(g, "geoms", [g]):
                x, y = poly.exterior.xy
                ax.fill(x, y, color=cores[c], ec="#555", lw=0.4)
                for hole in poly.interiors:
                    hx, hy = hole.xy
                    ax.fill(hx, hy, color="white", ec="#555", lw=0.4)
        ax.set_title(titulo)
        ax.set_aspect("equal")
        ax.axis("off")
    fig.savefig(path, dpi=150, bbox_inches="tight")


if __name__ == "__main__":
    b1, p1 = build("gato_soneca", *gato_soneca(), "branco")
    b2, p2 = build("gato_faisca", *gato_faisca(), "lilas")
    preview([("Gato Soneca", b1, p1, "branco"), ("Gato Faisca", b2, p2, "lilas")],
            os.path.join(OUT, "preview.png"))
