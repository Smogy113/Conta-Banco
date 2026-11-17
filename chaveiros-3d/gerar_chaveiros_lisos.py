"""Chaveiros originais "Gato Tedio" e "Gato Sorriso" - SEM relevo.

O desenho fica embutido (inlay) na face de cima: superficie 100% lisa, as cores
ocupam as ultimas camadas (0,6 mm). Para impressora multicor (AMS/MMU) use os
arquivos por cor; para uma cor so, use *_completo.stl.
"""
import os
import numpy as np
import trimesh
from shapely.geometry import Point, Polygon, box
from shapely.ops import unary_union
from shapely import affinity
from gerar_chaveiros import ellipse, stroke, arc, head_with_ring, preview

ESP = 4.0       # espessura total (mm)
INLAY = 0.6     # profundidade das cores embutidas (mm)
OUT = os.path.dirname(os.path.abspath(__file__))


def rounded_rect(cx, cy, w, h, r):
    return box(cx - w / 2 + r, cy - h / 2 + r, cx + w / 2 - r, cy + h / 2 - r).buffer(r, 32)


# ---------------------------------------------------------------- Gato Tedio
def gato_tedio():
    head = rounded_rect(0, -1, 46, 34, 13)            # cabeca "pao de forma"
    ears = [Polygon([(-21, 8), (-19, 22), (-9, 14)]).buffer(2.5),
            Polygon([(21, 8), (19, 22), (9, 14)]).buffer(2.5)]
    base = head_with_ring(head, ears, 19)

    orelhas = unary_union([Polygon([(-18, 11), (-17.5, 18), (-12, 14)]),
                           Polygon([(18, 11), (17.5, 18), (12, 14)])])
    # olhos: circulo branco cortado por palpebra reta (cara de tedio)
    olho_branco, palpebra, pupila = [], [], []
    for cx in (-9, 9):
        o = Point(cx, 1).buffer(5.5, 64)
        lid = o.intersection(box(cx - 6, 1.5, cx + 6, 7))
        olho_branco.append(o.difference(lid))
        palpebra.append(lid)
        pupila.append(Point(cx + 1.5, -0.8).buffer(2.0, 32).intersection(o.difference(lid)))
    palpebra = unary_union(palpebra)
    pupila = unary_union(pupila)
    olho_branco = unary_union(olho_branco).difference(pupila)
    contorno_palpebra = unary_union([stroke([(cx - 5.8, 1.5), (cx + 5.8, 1.5)], 0.9) for cx in (-9, 9)])
    boca = stroke([(-4, -9), (0, -9.5), (4.5, -8)], 1.1)   # boquinha torta
    nariz = Point(0, -5.5).buffer(1.3, 32)
    pintas = unary_union([Point(x, y).buffer(0.8, 16) for x, y in
                          [(-15, -7), (-17, -9.5), (-13, -10), (15, -7), (17, -9.5), (13, -10)]])
    escuro = unary_union([pupila, contorno_palpebra, boca, pintas])
    cores = {
        "verde_escuro": palpebra.difference(escuro.buffer(0.2)),
        "branco": olho_branco.difference(escuro.buffer(0.2)),
        "preto": escuro,
        "rosa": unary_union([orelhas, nariz]),
    }
    return base, cores, "verde"


# ---------------------------------------------------------------- Gato Sorriso
def gato_sorriso():
    head = affinity.scale(Point(0, 0).buffer(1, 64), 22, 20).union(ellipse(0, -6, 23, 13))
    ears = [Polygon([(-20, 6), (-14, 25), (-5, 14)]).buffer(1.8),
            Polygon([(20, 6), (14, 25), (5, 14)]).buffer(1.8)]
    base = head_with_ring(head, ears, 22)

    orelhas = unary_union([Polygon([(-17, 9), (-14, 20), (-8, 14)]),
                           Polygon([(17, 9), (14, 20), (8, 14)])])
    listras = unary_union([stroke([(x, 15), (x * 0.8, 9)], 1.8) for x in (-4, 0, 4)])
    olhos = unary_union([stroke(arc(cx, 5, 4, 200, 340), 1.4) for cx in (-9, 9)])  # olhinhos felizes
    sorriso = Polygon(arc(0, -3, 9, 195, 345, 48)).buffer(0.4)
    lingua = ellipse(0, -11, 3.5, 1.8).intersection(sorriso.buffer(-0.8))
    dente = box(-1.8, -7.6, 1.8, -5.0).intersection(sorriso.buffer(-0.5))
    sardas = unary_union([Point(x, y).buffer(0.7, 16) for x, y in
                          [(-14, 0), (-16, -2), (-13, -3), (14, 0), (16, -2), (13, -3)]])
    preto = unary_union([olhos, sorriso.difference(unary_union([lingua, dente]).buffer(0.3))])
    cores = {
        "laranja_escuro": unary_union([listras, sardas]),
        "preto": preto,
        "rosa": unary_union([orelhas, lingua]),
        "branco": dente,
    }
    return base, cores, "laranja"


def extrude(geom, h, z0):
    ms = []
    for g in getattr(geom, "geoms", [geom]):
        if not g.is_empty and g.area > 1e-3:
            m = trimesh.creation.extrude_polygon(g, h)
            m.apply_translation([0, 0, z0])
            ms.append(m)
    return trimesh.util.concatenate(ms)


def build(nome, base, cores, cor_base):
    inner = base.buffer(-0.8)
    # regioes de cor disjuntas e validas (uma cor nao invade a outra)
    usado, limpas = None, {}
    for k, v in cores.items():
        v = v.buffer(0).intersection(inner)
        if usado is not None:
            v = v.difference(usado.buffer(0.05))
        v = v.buffer(-0.01).buffer(0.01)
        limpas[k] = v
        usado = v if usado is None else usado.union(v)
    cores = limpas
    tudo = unary_union(list(cores.values()))
    corpo = trimesh.boolean.union([extrude(base, ESP - INLAY, 0),
                                   extrude(base.difference(tudo), INLAY, ESP - INLAY)],
                                  engine="manifold")
    corpo.export(os.path.join(OUT, f"{nome}_base_{cor_base}.stl"))
    partes = [corpo]
    for cor, g in cores.items():
        m = extrude(g, INLAY, ESP - INLAY)
        m.export(os.path.join(OUT, f"{nome}_cor_{cor}.stl"))
        partes.append(m)
    completo = trimesh.boolean.union(partes, engine="manifold")
    completo.export(os.path.join(OUT, f"{nome}_completo.stl"))
    b = completo.bounds
    print(f"{nome}: {b[1][0]-b[0][0]:.1f} x {b[1][1]-b[0][1]:.1f} x {b[1][2]-b[0][2]:.1f} mm,"
          f" estanque={completo.is_watertight}")
    return base, cores


if __name__ == "__main__":
    import gerar_chaveiros as g
    g_cores = {"verde": "#8fc9a8", "verde_escuro": "#3f7d5c", "laranja": "#f39a45",
               "laranja_escuro": "#c4621c", "branco": "#ffffff", "preto": "#222222",
               "rosa": "#f4a6b8"}
    b1, c1 = build("gato_tedio", *gato_tedio())
    b2, c2 = build("gato_sorriso", *gato_sorriso())
    import matplotlib; matplotlib.use("Agg"); import matplotlib.pyplot as plt
    fig, axs = plt.subplots(1, 2, figsize=(10, 5))
    for ax, t, b, c, cb in [(axs[0], "Gato Tedio", b1, c1, "verde"), (axs[1], "Gato Sorriso", b2, c2, "laranja")]:
        for geom, cor in [(b, cb)] + [(v, k) for k, v in c.items()]:
            for p in getattr(geom, "geoms", [geom]):
                if p.is_empty: continue
                ax.fill(*p.exterior.xy, color=g_cores[cor], ec="#555", lw=0.3)
                for h in p.interiors: ax.fill(*h.xy, color="white", ec="#555", lw=0.3)
        ax.set_title(t); ax.set_aspect("equal"); ax.axis("off")
    fig.savefig(os.path.join(OUT, "preview_lisos.png"), dpi=150, bbox_inches="tight")
