"""Chaveiros de corpo inteiro: Gato Tedio e Gato Sorriso (personagens originais).

Estilo adesivo/cartoon: contorno escuro, cores chapadas embutidas (sem relevo).
Peca lisa de 4 mm; as cores ocupam os ultimos 0,6 mm.
"""
import os
import numpy as np
import trimesh
from shapely.geometry import Point, Polygon, LineString, box
from shapely.ops import unary_union
from shapely import affinity

ESP, INLAY = 4.0, 0.6
OUT = os.path.dirname(os.path.abspath(__file__))
LINHA = 0.9  # espessura do contorno (mm)


def ell(cx, cy, rx, ry, rot=0):
    e = affinity.scale(Point(0, 0).buffer(1, 72), rx, ry)
    return affinity.translate(affinity.rotate(e, rot, origin=(0, 0)), cx, cy)


def cap(p0, p1, r):
    return LineString([p0, p1]).buffer(r, 48)


def stroke(pts, w):
    return LineString(pts).buffer(w / 2, 24)


def arc(cx, cy, rx, ry, a0, a1, n=40):
    t = np.radians(np.linspace(a0, a1, n))
    return list(zip(cx + rx * np.cos(t), cy + ry * np.sin(t)))


def bezier(p0, p1, p2, n=40):
    t = np.linspace(0, 1, n)[:, None]
    p0, p1, p2 = map(np.array, (p0, p1, p2))
    return [tuple(p) for p in (1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t ** 2 * p2]


def soft(g, r=0.8):
    return g.buffer(r, 32).buffer(-r, 32)


def corpo_base(tail_pts, ear_l, ear_r):
    """Partes do corpo (cada uma ganha contorno proprio)."""
    p = {}
    p["cauda"] = stroke(tail_pts, 4.2)
    p["perna_e"] = soft(box(-8.5, -33, -1.8, -20)).union(ell(-5.4, -33, 4.4, 2.6))
    p["perna_d"] = soft(box(1.8, -33, 8.5, -20)).union(ell(5.4, -33, 4.4, 2.6))
    p["tronco"] = soft(Polygon([(-9, -9), (9, -9), (11, -26), (-11, -26)]), 3)
    p["braco_e"] = cap((-8.5, -12), (-13.5, -21), 2.6)
    p["braco_d"] = cap((8.5, -12), (13.5, -21), 2.6)
    p["orelha_e"] = soft(Polygon(ear_l), 1.5)
    p["orelha_d"] = soft(Polygon(ear_r), 1.5)
    p["cabeca"] = ell(0, 6, 19, 15)
    return p


def montar(partes, cores, cor_base, ring_y):
    """partes: ordem de desenho (tras -> frente). cores: lista (nome_cor, geom) por cima."""
    sil = unary_union(list(partes.values()))
    anel = Point(0, ring_y).buffer(5.5, 64).union(box(-3.5, ring_y - 6, 3.5, ring_y))
    sil = soft(unary_union([sil, anel]), 1.0).buffer(0.6)  # 0.6 de borda escura extra
    sil = sil.difference(Point(0, ring_y).buffer(2.6, 64))

    # contornos: cada parte desenhada por cima das anteriores
    ordem = list(partes.items())
    linhas = []
    for i, (nome, g) in enumerate(ordem):
        frente = unary_union([h for _, h in ordem[i + 1:]]) if i + 1 < len(ordem) else None
        borda = g.exterior.buffer(LINHA / 2, 16)
        if frente is not None:
            borda = borda.difference(frente.buffer(-LINHA / 2))
        linhas.append(borda)
    contorno = unary_union(linhas + [sil.exterior.buffer(1.2, 16),
                                     Point(0, ring_y).buffer(2.6 + 0.9).exterior.buffer(0.45)])
    contorno = contorno.intersection(sil)

    # pintura em camadas: depois sobrescreve antes; contorno por ultimo
    camadas = cores + [("preto", contorno)]
    regioes, ocupado = {}, Point(0, 0).buffer(0)
    for nome, g in reversed(camadas):
        g = g.buffer(0).intersection(sil.buffer(-0.01)).difference(ocupado)
        g = g.buffer(-0.02).buffer(0.02)
        if g.is_empty:
            continue
        regioes[nome] = unary_union([regioes.get(nome, Point(0, 0).buffer(0)), g])
        ocupado = ocupado.union(g.buffer(0.03))
    return sil, regioes, cor_base


# ------------------------------------------------------------- Gato Tedio
def gato_tedio():
    tail = bezier((9, -22), (24, -24), (20, -6)) + bezier((20, -6), (18, 0), (22, 2))[1:]
    p = corpo_base(tail, [(-18, 12), (-16, 28), (-6, 19)], [(18, 12), (16, 28), (6, 19)])
    cores = []
    # moletom mostarda com capuz e cordinhas
    moletom = unary_union([p["tronco"], p["braco_e"], p["braco_d"]]).intersection(box(-30, -24, 30, -8))
    cores.append(("mostarda", moletom))
    cores.append(("mostarda_escuro", unary_union([box(-11, -26, 11, -23.5).intersection(p["tronco"]),
                                                 ell(-13.5, -21, 2.7, 1.3).intersection(p["braco_e"]),
                                                 ell(13.5, -21, 2.7, 1.3).intersection(p["braco_d"])])))
    cores.append(("branco", unary_union([stroke([(-2.5, -10), (-3, -16)], 0.8),
                                         stroke([(2.5, -10), (3, -16)], 0.8),
                                         Point(-3, -16.3).buffer(0.9), Point(3, -16.3).buffer(0.9)])))
    cores.append(("bolso", soft(box(-6, -21.5, 6, -17), 1)))
    # maos e pes claros
    cores.append(("cinza_claro", unary_union([Point(-13.8, -22).buffer(2.4), Point(13.8, -22).buffer(2.4),
                                              ell(-5.4, -33.5, 4.2, 2.2), ell(5.4, -33.5, 4.2, 2.2)])))
    # focinho claro, orelha rosa
    cores.append(("cinza_claro", ell(0, 0, 8, 5)))
    cores.append(("rosa", unary_union([soft(Polygon([(-15.5, 14), (-15, 24), (-9, 18)]), 1),
                                       soft(Polygon([(15.5, 14), (15, 24), (9, 18)]), 1)])))
    # olhos de tedio: branco + palpebra azul-escura reta + pupila olhando de lado
    for cx in (-7.5, 7.5):
        o = ell(cx, 6, 4.6, 4.2)
        tampa = o.intersection(box(cx - 6, 6.8, cx + 6, 12))
        cores.append(("branco", o))
        cores.append(("azul_escuro", tampa))
        cores.append(("preto", Point(cx + 1.6, 5.2).buffer(1.9, 32).intersection(o.difference(tampa))))
        cores.append(("branco", Point(cx + 2.2, 5.9).buffer(0.55, 16)))
        cores.append(("preto", stroke([(cx - 4.9, 6.8), (cx + 4.9, 6.8)], 0.8)))
        cores.append(("preto", o.exterior.buffer(0.35).difference(tampa)))
    # nariz, boca torta, bigodes
    cores.append(("rosa", ell(0, 1.6, 1.4, 1.0)))
    cores.append(("preto", stroke(bezier((-3.2, -2.2), (0, -1.6), (3.6, -0.8)), 0.8)))
    for s in (-1, 1):
        cores.append(("preto", stroke([(s * 8.5, 0.5), (s * 14, 1.5)], 0.6)))
        cores.append(("preto", stroke([(s * 8.5, -1.2), (s * 14, -2.2)], 0.6)))
    # listras na cauda
    for y in (-14, -8):
        cores.append(("azul_escuro", box(10, y, 30, y + 2).intersection(p["cauda"])))
    return montar(p, cores, "azul", 27)


# ------------------------------------------------------------- Gato Sorriso
def gato_sorriso():
    tail = bezier((-9, -22), (-25, -22), (-21, -4)) + bezier((-21, -4), (-19, 3), (-24, 4))[1:]
    p = corpo_base(tail, [(-19, 10), (-13, 28), (-4, 19)], [(19, 10), (13, 28), (4, 19)])
    cores = []
    # barriga creme + macacao jeans
    cores.append(("creme", ell(0, -17, 7, 7).intersection(p["tronco"])))
    macacao = p["tronco"].intersection(box(-30, -27, 30, -17)).union(
        soft(box(-5.5, -20, 5.5, -13), 1).intersection(p["tronco"]))
    macacao = macacao.union(p["perna_e"].intersection(box(-30, -31, 30, -18)))
    macacao = macacao.union(p["perna_d"].intersection(box(-30, -31, 30, -18)))
    cores.append(("jeans", macacao))
    cores.append(("jeans", unary_union([stroke([(-4.5, -13.5), (-7.5, -9)], 1.6),
                                        stroke([(4.5, -13.5), (7.5, -9)], 1.6)])))
    cores.append(("amarelo", unary_union([Point(-4.3, -14.2).buffer(1.0), Point(4.3, -14.2).buffer(1.0)])))
    cores.append(("jeans_escuro", soft(box(-3, -19, 3, -15.5), 0.6)))
    # lenco vermelho no pescoco
    cores.append(("vermelho", soft(Polygon([(-8, -8.5), (8, -8.5), (0, -14)]), 0.8)))
    # patas e focinho creme
    cores.append(("creme", unary_union([Point(-13.8, -22).buffer(2.4), Point(13.8, -22).buffer(2.4),
                                        ell(-5.4, -33.5, 4.2, 2.2), ell(5.4, -33.5, 4.2, 2.2),
                                        ell(0, -0.5, 9, 6)])))
    cores.append(("rosa", unary_union([soft(Polygon([(-16, 13), (-12.5, 24), (-7, 18)]), 1),
                                       soft(Polygon([(16, 13), (12.5, 24), (7, 18)]), 1)])))
    # listras de tigrado (cabeca e cauda)
    for x in (-4, 0, 4):
        cores.append(("laranja_escuro", stroke([(x, 20.5), (x * 0.8, 15.5)], 1.6)))
    for s in (-1, 1):
        cores.append(("laranja_escuro", stroke([(s * 19.5, 8), (s * 15.5, 7.5)], 1.4)))
        cores.append(("laranja_escuro", stroke([(s * 19.0, 4), (s * 15.5, 4.5)], 1.4)))
    for y in (-14, -7):
        cores.append(("laranja_escuro", box(-30, y, -10, y + 2).intersection(p["cauda"])))
    # olhos felizes, bochechas, sorrisao
    for cx in (-7.5, 7.5):
        cores.append(("preto", stroke(arc(cx, 5.2, 3.6, 3.2, 20, 160), 1.3)))
        cores.append(("rosa", ell(cx * 1.55, 1.2, 2.4, 1.5)))
    cores.append(("rosa", ell(0, 2, 1.5, 1.0)))
    boca = soft(Polygon(arc(0, 0.8, 5.5, 5.5, 200, 340, 48)), 0.5)
    cores.append(("preto", boca))
    cores.append(("rosa", ell(0, -4.2, 2.6, 1.2).intersection(boca.buffer(-0.7))))
    return montar(p, cores, "laranja", 27)


# ------------------------------------------------------------- saida
PALETA = {"azul": "#8aa9d6", "azul_escuro": "#4b5f9a", "cinza_claro": "#e8edf5",
          "mostarda": "#f2c14e", "mostarda_escuro": "#c99a2e", "bolso": "#e0ab38",
          "laranja": "#f6a04d", "laranja_escuro": "#d06a22", "creme": "#fff1d6",
          "jeans": "#5b8fd1", "jeans_escuro": "#3f6fae", "amarelo": "#ffd84a",
          "vermelho": "#e54b4b", "rosa": "#f7a1b5", "branco": "#ffffff", "preto": "#2a2230"}


def extrude(geom, h, z0):
    ms = []
    for g in getattr(geom, "geoms", [geom]):
        if not g.is_empty and g.area > 1e-3:
            m = trimesh.creation.extrude_polygon(g, h)
            m.apply_translation([0, 0, z0])
            ms.append(m)
    return trimesh.util.concatenate(ms)


def exportar(nome, sil, regioes, cor_base):
    tudo = unary_union(list(regioes.values()))
    corpo = trimesh.boolean.union([extrude(sil, ESP - INLAY, 0),
                                   extrude(sil.difference(tudo).buffer(0), INLAY, ESP - INLAY)],
                                  engine="manifold")
    corpo.export(os.path.join(OUT, f"{nome}_base_{cor_base}.stl"))
    pecas = [corpo]
    for cor, g in regioes.items():
        m = extrude(g, INLAY, ESP - INLAY)
        m.export(os.path.join(OUT, f"{nome}_cor_{cor}.stl"))
        pecas.append(m)
    completo = trimesh.boolean.union(pecas, engine="manifold")
    completo.export(os.path.join(OUT, f"{nome}_completo.stl"))
    b = completo.bounds
    print(f"{nome}: {b[1][0]-b[0][0]:.1f} x {b[1][1]-b[0][1]:.1f} x {b[1][2]-b[0][2]:.1f} mm,"
          f" estanque={completo.is_watertight}, cores={1 + len(regioes)}")


def preview(itens, path):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from matplotlib.path import Path
    from matplotlib.patches import PathPatch

    def patch(poly, cor):
        verts, codes = [], []
        for ring in [poly.exterior] + list(poly.interiors):
            c = list(ring.coords)
            verts += c
            codes += [Path.MOVETO] + [Path.LINETO] * (len(c) - 2) + [Path.CLOSEPOLY]
        return PathPatch(Path(verts, codes), fc=cor, lw=0)

    fig, axs = plt.subplots(1, len(itens), figsize=(6 * len(itens), 7))
    for ax, (titulo, sil, regioes, cor_base) in zip(axs, itens):
        for geom, cor in [(sil, cor_base)] + [(g, c) for c, g in regioes.items()]:
            for p in getattr(geom, "geoms", [geom]):
                if not p.is_empty:
                    ax.add_patch(patch(p, PALETA[cor]))
        ax.set_title(titulo, fontsize=14)
        ax.set_aspect("equal")
        ax.autoscale_view()
        ax.axis("off")
    fig.savefig(path, dpi=160, bbox_inches="tight")


if __name__ == "__main__":
    t = gato_tedio()
    s = gato_sorriso()
    exportar("corpo_gato_tedio", *t)
    exportar("corpo_gato_sorriso", *s)
    preview([("Gato Tedio", *t), ("Gato Sorriso", *s)], os.path.join(OUT, "preview_corpo.png"))
