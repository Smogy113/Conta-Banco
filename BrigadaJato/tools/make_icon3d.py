#!/usr/bin/env python3
"""Icone 3D do Brigada Jato: um raymarcher de campos de distancia (SDF) em numpy.
O soldado e montado com formas (esferas, capsulas, caixas arredondadas, cilindros), iluminado
com luz principal, luz de contorno, sombra suave e oclusao ambiente, com sombreamento de
desenho (faixas) e contorno escuro. Gera os icones do Android e do PC."""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), "..")
RES = int(sys.argv[1]) if len(sys.argv) > 1 else 640


def norm(v):
    return v / np.linalg.norm(v)


def rot(axis, deg):
    a = math.radians(deg)
    x, y, z = norm(np.array(axis, float))
    c, s = math.cos(a), math.sin(a)
    return np.array([[c + x * x * (1 - c), x * y * (1 - c) - z * s, x * z * (1 - c) + y * s],
                     [y * x * (1 - c) + z * s, c + y * y * (1 - c), y * z * (1 - c) - x * s],
                     [z * x * (1 - c) - y * s, z * y * (1 - c) + x * s, c + z * z * (1 - c)]])


def look_rot(d, up=(0, 1, 0)):
    """matriz cujo eixo x aponta para d"""
    x = norm(np.array(d, float))
    z = norm(np.cross(x, up))
    y = np.cross(z, x)
    return np.stack([x, y, z], axis=1)  # colunas


# ---------------------------------------------------------------- primitivas (p: Nx3)
def sd_sphere(p, c, r):
    return np.linalg.norm(p - c, axis=1) - r


def sd_capsule(p, a, b, r):
    a, b = np.array(a, float), np.array(b, float)
    pa, ba = p - a, b - a
    h = np.clip((pa @ ba) / (ba @ ba), 0, 1)
    return np.linalg.norm(pa - np.outer(h, ba), axis=1) - r


def sd_rbox(p, c, half, r, R=None):
    q = p - c
    if R is not None:
        q = q @ R  # para o espaco local
    q = np.abs(q) - (np.array(half) - r)
    return np.linalg.norm(np.maximum(q, 0), axis=1) + np.minimum(np.max(q, axis=1), 0) - r


def sd_cyl(p, a, b, r, rr=0.0):
    a, b = np.array(a, float), np.array(b, float)
    ba = b - a
    L = np.linalg.norm(ba)
    u = ba / L
    pa = p - a
    y = pa @ u
    radial = np.linalg.norm(pa - np.outer(y, u), axis=1)
    dx = radial - (r - rr)
    dy = np.abs(y - L / 2) - (L / 2 - rr)
    return np.linalg.norm(np.stack([np.maximum(dx, 0), np.maximum(dy, 0)], 1), axis=1) + np.minimum(np.maximum(dx, dy), 0) - rr


def smin(a, b, k):
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0, 1)
    return b * (1 - h) + a * h - k * h * (1 - h)


# ---------------------------------------------------------------- cena
C = {
    "uni": np.array([0.24, 0.29, 0.16]), "pants": np.array([0.15, 0.16, 0.14]), "vest": np.array([0.09, 0.10, 0.09]),
    "helmet": np.array([0.25, 0.30, 0.17]), "skin": np.array([0.62, 0.44, 0.32]), "glove": np.array([0.13, 0.12, 0.11]),
    "boot": np.array([0.12, 0.10, 0.09]), "gun": np.array([0.15, 0.17, 0.18]), "gunL": np.array([0.32, 0.35, 0.37]),
    "lens": np.array([1.0, 0.62, 0.10]), "jet": np.array([0.24, 0.26, 0.28]), "orange": np.array([1.0, 0.55, 0.08]),
    "pad": np.array([0.16, 0.17, 0.14]), "strap": np.array([0.10, 0.10, 0.09]),
}
MATS = list(C.keys())

# juntas da pose (o soldado olha para +x; camera na frente-direita, um pouco de baixo)
HIP = np.array([0.0, 0.98, 0.0])
AIM = norm(np.array([1.0, 0.03, 0.85]))
RIFLE_R = look_rot(AIM)
BUTT = np.array([0.02, 1.37, -0.10])
SH_R, SH_L = np.array([0.02, 1.43, -0.19]), np.array([0.07, 1.44, 0.19])
GRIP = BUTT + AIM * 0.26 + np.array([0, -0.085, 0])
FORE = BUTT + AIM * 0.68 + np.array([0, -0.055, 0])
EL_R = np.array([-0.06, 1.2, -0.2])
EL_L = np.array([0.20, 1.2, 0.33])
TORSO_R = rot((0, 0, 1), -10) @ rot((0, 1, 0), -14)


GUN_S = 1.3


def rifle(p):
    return rifle_raw(p) * 1.0


def rifle_raw(p):
    """o fuzil em coordenadas locais (x ao longo do cano), engrossado GUN_S vezes"""
    q = (p - BUTT) @ RIFLE_R / GUN_S
    d = sd_rbox(q, np.array([0.30, 0.0, 0.0]), (0.17, 0.045, 0.028), 0.012)               # corpo
    d = np.minimum(d, sd_rbox(q, np.array([0.03, -0.02, 0.0]), (0.08, 0.04, 0.024), 0.012))  # coronha
    d = np.minimum(d, sd_rbox(q, np.array([0.58, 0.0, 0.0]), (0.13, 0.034, 0.03), 0.01))    # guarda-mao
    d = np.minimum(d, sd_cyl(q, [0.66, 0.01, 0], [0.92, 0.01, 0], 0.013, 0.004))             # cano
    d = np.minimum(d, sd_cyl(q, [0.90, 0.01, 0], [0.96, 0.01, 0], 0.022, 0.006))             # quebra-chama
    mag_r = rot((0, 0, 1), -12)
    d = np.minimum(d, sd_rbox(q, np.array([0.30, -0.11, 0.0]), (0.03, 0.08, 0.02), 0.01, mag_r))  # carregador
    d = np.minimum(d, sd_rbox(q, np.array([0.16, -0.08, 0.0]), (0.022, 0.05, 0.02), 0.008, rot((0, 0, 1), 18)))  # cabo
    d = np.minimum(d, sd_cyl(q, [0.22, 0.085, 0], [0.42, 0.085, 0], 0.028, 0.008))           # luneta
    d = np.minimum(d, sd_rbox(q, np.array([0.32, 0.055, 0.0]), (0.05, 0.02, 0.012), 0.005))
    return d * GUN_S


def lens(p):
    q = (p - BUTT) @ RIFLE_R / GUN_S
    return sd_cyl(q, [0.41, 0.085, 0], [0.43, 0.085, 0], 0.022, 0.003) * GUN_S


def scene(p, want_mat=False):
    parts = []
    # pernas (quadril -> joelho -> pe), joelho para a frente
    kneeF, footF = np.array([0.27, 0.55, 0.17]), np.array([0.36, 0.10, 0.20])
    kneeB, footB = np.array([-0.06, 0.52, -0.16]), np.array([-0.30, 0.10, -0.18])
    hipF, hipB = HIP + [0.02, 0, 0.11], HIP + [-0.02, 0, -0.11]
    legs = np.minimum(np.minimum(sd_capsule(p, hipF, kneeF, 0.085), sd_capsule(p, kneeF, footF, 0.07)),
                      np.minimum(sd_capsule(p, hipB, kneeB, 0.085), sd_capsule(p, kneeB, footB, 0.07)))
    parts.append(("pants", legs))
    pads = np.minimum(sd_rbox(p, kneeF + [0.06, 0, 0.02], (0.04, 0.06, 0.06), 0.03), sd_rbox(p, kneeB + [0.07, 0, 0], (0.04, 0.06, 0.06), 0.03))
    parts.append(("pad", pads))
    boots = np.minimum(sd_rbox(p, footF + [0.05, -0.03, 0], (0.12, 0.06, 0.065), 0.04), sd_rbox(p, footB + [0.05, -0.03, 0], (0.12, 0.06, 0.065), 0.04))
    parts.append(("boot", boots))
    # tronco
    torso = sd_rbox(p, np.array([0.03, 1.22, 0.0]), (0.13, 0.24, 0.2), 0.08, TORSO_R)
    torso = smin(torso, sd_rbox(p, HIP + [0, 0.02, 0], (0.11, 0.1, 0.16), 0.06), 0.06)
    parts.append(("uni", torso))
    vest = sd_rbox(p, np.array([0.06, 1.24, 0.0]), (0.13, 0.19, 0.205), 0.06, TORSO_R)
    parts.append(("vest", vest))
    # bolsos do colete e cinto
    pouches = np.full(len(p), 9.0)
    for z in (-0.11, 0.0, 0.11):
        pouches = np.minimum(pouches, sd_rbox(p, np.array([0.19, 1.13, z]), (0.035, 0.055, 0.045), 0.015, TORSO_R))
    parts.append(("pad", pouches))
    belt = sd_cyl(p, HIP + [0, 0.07, 0] - [0, 0.025, 0], HIP + [0, 0.07, 0] + [0, 0.025, 0], 0.175, 0.01)
    parts.append(("strap", belt))
    # mochila / jetpack (dois tanques + bocais)
    tanks = np.minimum(sd_cyl(p, [-0.24, 1.05, -0.09], [-0.22, 1.42, -0.08], 0.075, 0.03),
                       sd_cyl(p, [-0.24, 1.05, 0.09], [-0.22, 1.42, 0.10], 0.075, 0.03))
    tanks = np.minimum(tanks, sd_rbox(p, np.array([-0.17, 1.25, 0.0]), (0.05, 0.16, 0.14), 0.03))
    parts.append(("jet", tanks))
    noz = np.minimum(sd_cyl(p, [-0.245, 0.96, -0.09], [-0.245, 1.06, -0.09], 0.05, 0.01), sd_cyl(p, [-0.245, 0.96, 0.09], [-0.245, 1.06, 0.09], 0.05, 0.01))
    parts.append(("gun", noz))
    stripe = np.minimum(sd_cyl(p, [-0.237, 1.30, -0.085], [-0.235, 1.34, -0.083], 0.078, 0.005),
                        sd_cyl(p, [-0.237, 1.30, 0.095], [-0.235, 1.34, 0.097], 0.078, 0.005))
    parts.append(("orange", stripe))
    # cabeca, capacete e oculos
    head_c = np.array([0.10, 1.63, 0.0])
    head = sd_sphere(p, head_c, 0.125)
    head = smin(head, sd_sphere(p, head_c + [0.04, -0.07, 0], 0.085), 0.04)  # queixo
    head = smin(head, sd_capsule(p, head_c + [-0.03, -0.12, 0], head_c + [-0.04, -0.2, 0], 0.06), 0.03)  # pescoco
    parts.append(("skin", head))
    helmet = sd_sphere(p, head_c + [-0.01, 0.025, 0], 0.15)
    helmet = np.maximum(helmet, -(p[:, 1] - (head_c[1] - 0.005) - 0.25 * (p[:, 0] - head_c[0])))
    helmet = np.minimum(helmet, sd_cyl(p, head_c + [-0.01, -0.005, 0], head_c + [-0.01, 0.005, 0], 0.16, 0.004))  # aba
    parts.append(("helmet", helmet))
    strap = sd_cyl(p, head_c + [-0.005, 0.035, 0] - [0, 0.018, 0], head_c + [-0.005, 0.035, 0] + [0, 0.018, 0], 0.152, 0.004)
    parts.append(("strap", np.maximum(strap, -helmet - 0.004)))
    # balaclava cobrindo do nariz para baixo
    bal = sd_sphere(p, head_c + [0.012, -0.025, 0], 0.13)
    bal = smin(bal, sd_sphere(p, head_c + [0.05, -0.08, 0], 0.09), 0.04)
    bal = np.maximum(bal, p[:, 1] - (head_c[1] - 0.012))
    parts.append(("vest", bal))
    # oculos taticos na altura dos olhos, com tira em volta da cabeca
    gog = np.minimum(sd_sphere(p, head_c + [0.1, 0.03, 0.05], 0.045), sd_sphere(p, head_c + [0.1, 0.03, -0.05], 0.045))
    gog = np.maximum(gog, -(p[:, 0] - (head_c[0] + 0.095)))
    parts.append(("lens", gog))
    gogframe = np.maximum(sd_capsule(p, head_c + [0.095, 0.03, 0.09], head_c + [0.095, 0.03, -0.09], 0.05), -gog + 0.004)
    gogframe = np.maximum(gogframe, -(p[:, 0] - (head_c[0] + 0.06)))
    band = np.maximum(sd_cyl(p, head_c + [0, 0.02, 0], head_c + [0, 0.04, 0], 0.13, 0.005), p[:, 0] - (head_c[0] + 0.05))
    parts.append(("strap", np.minimum(gogframe, band)))
    # suporte de visao noturna na frente do capacete
    nvg = sd_rbox(p, head_c + [0.13, 0.12, 0], (0.03, 0.025, 0.035), 0.01)
    parts.append(("gun", nvg))
    # detalhes do colete: tiras horizontais (molle) e placa
    molle = np.full(len(p), 9.0)
    for yy in (1.27, 1.33, 1.39):
        molle = np.minimum(molle, sd_rbox(p, np.array([0.185, yy, 0.0]), (0.012, 0.008, 0.16), 0.004, TORSO_R))
    parts.append(("pad", molle))
    patch = sd_rbox(p, SH_L + [0.0, -0.06, 0.07], (0.03, 0.03, 0.012), 0.008)
    parts.append(("orange", patch))
    # bracos: ombro -> cotovelo -> mao
    arms = np.minimum(np.minimum(sd_capsule(p, SH_R, EL_R, 0.065), sd_capsule(p, EL_R, GRIP, 0.055)),
                      np.minimum(sd_capsule(p, SH_L, EL_L, 0.065), sd_capsule(p, EL_L, FORE, 0.055)))
    arms = np.minimum(arms, np.minimum(sd_sphere(p, SH_R, 0.085), sd_sphere(p, SH_L, 0.085)))
    parts.append(("uni", arms))
    gloves = np.minimum(sd_sphere(p, GRIP, 0.058), sd_sphere(p, FORE, 0.058))
    parts.append(("glove", gloves))
    parts.append(("gun", rifle(p)))
    parts.append(("lens", lens(p)))
    d = parts[0][1]
    mat = np.full(len(p), MATS.index(parts[0][0]))
    for name, dd in parts[1:]:
        better = dd < d
        d = np.where(better, dd, d)
        if want_mat:
            mat = np.where(better, MATS.index(name), mat)
    if want_mat:
        return d, mat
    return d


def first_mat_fix(d_parts):
    pass


# ---------------------------------------------------------------- render
def render(res):
    cam = np.array([1.3, 1.22, 1.72])
    target = np.array([0.24, 1.36, 0.14])
    fwd = norm(target - cam)
    right = norm(np.cross(fwd, [0, 1, 0]))
    up = np.cross(right, fwd)
    fov = 0.34
    ys, xs = np.mgrid[0:res, 0:res]
    u = (xs + 0.5) / res * 2 - 1
    v = 1 - (ys + 0.5) / res * 2
    dirs = fwd + (u.reshape(-1, 1) * right + v.reshape(-1, 1) * up) * fov
    dirs = dirs / np.linalg.norm(dirs, axis=1, keepdims=True)
    n = len(dirs)
    t = np.full(n, 0.5)
    hit = np.zeros(n, bool)
    alive = np.ones(n, bool)
    for it in range(140):
        idx = np.nonzero(alive)[0]
        if len(idx) == 0:
            break
        pts = cam + dirs[idx] * t[idx, None]
        d = scene(pts)
        t[idx] += d * 0.9
        done = d < 0.0012
        hit[idx[done]] = True
        far = t[idx] > 8
        alive[idx[done | far]] = False
    hidx = np.nonzero(hit)[0]
    P = cam + dirs[hidx] * t[hidx, None]
    _, mat = scene(P, True)
    e = 0.0015
    nx = scene(P + [e, 0, 0]) - scene(P - [e, 0, 0])
    ny = scene(P + [0, e, 0]) - scene(P - [0, e, 0])
    nz = scene(P + [0, 0, e]) - scene(P - [0, 0, e])
    N = np.stack([nx, ny, nz], 1)
    N /= np.linalg.norm(N, axis=1, keepdims=True) + 1e-9
    key = norm(np.array([0.5, 0.9, 0.75]))
    rim = norm(np.array([-0.9, 0.3, -0.6]))
    # sombra suave
    sh = np.ones(len(P))
    ts = np.full(len(P), 0.02)
    for it in range(36):
        q = P + N * 0.003 + key * ts[:, None]
        dd = scene(q)
        sh = np.minimum(sh, np.clip(12 * dd / ts, 0, 1))
        ts += np.clip(dd, 0.01, 0.12)
    # oclusao ambiente
    ao = np.zeros(len(P))
    for k in range(1, 6):
        h = 0.02 * k
        ao += (h - scene(P + N * h)) / (2 ** k)
    ao = np.clip(1 - ao * 9, 0, 1)
    base = np.array([C[MATS[m]] for m in range(len(MATS))])[mat]
    lam = np.clip(N @ key, 0, 1) * sh
    toon = np.where(lam > 0.55, 1.0, np.where(lam > 0.18, 0.72, 0.46))
    toon = toon * 0.85 + lam * 0.15
    V = -dirs[hidx]
    H = norm_rows(key + V)
    spec = np.clip((N * H).sum(1), 0, 1) ** 40 * sh
    shiny = np.isin(mat, [MATS.index("gun"), MATS.index("gunL"), MATS.index("helmet"), MATS.index("jet"), MATS.index("lens")])
    rimv = np.clip(1 - (N * V).sum(1), 0, 1) ** 2.5 * np.clip(N @ rim + 0.5, 0, 1)
    boost = np.where(mat == MATS.index("skin"), 1.15, 1.45)
    base = np.clip(base * boost[:, None], 0, 1)
    col = base * (0.38 + 1.0 * toon[:, None]) * (0.6 + 0.4 * ao[:, None])
    col += spec[:, None] * np.where(shiny, 0.9, 0.25)[:, None]
    col += rimv[:, None] * np.array([0.35, 0.85, 1.0]) * 1.1
    is_lens = mat == MATS.index("lens")
    col[is_lens] = np.clip(C["lens"] * 1.25 + spec[is_lens, None] * 1.2, 0, 1)
    img = np.zeros((n, 4))
    img[hidx, :3] = np.clip(col, 0, 1)
    img[hidx, 3] = 1
    depth = np.full(n, 99.0)
    depth[hidx] = t[hidx]
    matmap = np.full(n, -1)
    matmap[hidx] = mat
    return img.reshape(res, res, 4), depth.reshape(res, res), matmap.reshape(res, res), (cam, fwd, right, up, fov)


def norm_rows(v):
    return v / (np.linalg.norm(v, axis=1, keepdims=True) + 1e-9)


def project(pt, cam, fwd, right, up, fov, res):
    d = np.array(pt) - cam
    z = d @ fwd
    u = (d @ right) / z / fov
    v = (d @ up) / z / fov
    return ((u + 1) / 2 * res, (1 - v) / 2 * res)


def compose(res):
    img, depth, matmap, camdata = render(res)
    cam, fwd, right, up, fov = camdata
    # contorno: onde a profundidade ou o material mudam
    dz = np.zeros_like(depth)
    dz[1:-1, 1:-1] = np.maximum.reduce([np.abs(depth[1:-1, 1:-1] - depth[:-2, 1:-1]), np.abs(depth[1:-1, 1:-1] - depth[2:, 1:-1]),
                                        np.abs(depth[1:-1, 1:-1] - depth[1:-1, :-2]), np.abs(depth[1:-1, 1:-1] - depth[1:-1, 2:])])
    edge = (dz > 0.09).astype(float)
    mm = matmap
    medge = np.zeros_like(depth)
    medge[1:-1, 1:-1] = ((mm[1:-1, 1:-1] != mm[:-2, 1:-1]) | (mm[1:-1, 1:-1] != mm[1:-1, :-2])).astype(float) * 0.55
    edge = np.maximum(edge, medge)
    char = Image.fromarray((img * 255).astype(np.uint8), "RGBA")
    edge_im = Image.fromarray((edge * 255).astype(np.uint8))
    # fundo: gradiente quente com raios e brilho atras do soldado
    S = res
    bg = Image.new("RGBA", (S, S))
    yy, xx = np.mgrid[0:S, 0:S]
    cx, cy = S * 0.42, S * 0.42
    r = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / S
    ang = np.arctan2(yy - cy, xx - cx)
    rays = (np.sin(ang * 14) > 0.55).astype(float) * np.clip(1 - r * 1.5, 0, 1) * 0.18
    top = np.array([255, 170, 40]) / 255
    bot = np.array([150, 30, 20]) / 255
    k = np.clip(r * 1.6, 0, 1)[..., None]
    colbg = top * (1 - k) + bot * k
    colbg = colbg * (1 - 0.35 * (yy / S))[..., None] + rays[..., None]
    bgarr = np.dstack([np.clip(colbg, 0, 1), np.ones((S, S))])
    bg = Image.fromarray((bgarr * 255).astype(np.uint8), "RGBA")
    # fumaca/fogo do jetpack (atras do personagem)
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for z in (-0.09, 0.09):
        fx, fy = project([-0.245, 0.95, z], cam, fwd, right, up, fov, S)
        for i in range(14):
            rr = S * (0.012 + i * 0.006)
            oy = fy + i * S * 0.022
            a = int(255 * (1 - i / 14) * 0.9)
            c = (255, 220 - i * 10, 120 - i * 8, a) if i < 6 else (255, 120 - i * 4, 30, a)
            gd.ellipse([fx - rr, oy - rr, fx + rr, oy + rr], fill=c)
    glow = glow.filter(ImageFilter.GaussianBlur(S * 0.008))
    bg = Image.alpha_composite(bg, glow)
    # sombra no chao
    sh = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    sx, sy = project([0.03, 0.02, 0.0], cam, fwd, right, up, fov, S)
    ImageDraw.Draw(sh).ellipse([sx - S * 0.26, sy - S * 0.045, sx + S * 0.26, sy + S * 0.045], fill=(0, 0, 0, 120))
    bg = Image.alpha_composite(bg, sh.filter(ImageFilter.GaussianBlur(S * 0.015)))
    # contorno grosso do personagem (silhueta) + personagem + contorno interno
    alpha = char.split()[3]
    sil = alpha.filter(ImageFilter.MaxFilter(max(3, (S // 150) | 1)))
    outline = Image.new("RGBA", (S, S), (20, 18, 24, 255))
    bg.paste(outline, (0, 0), sil)
    bg = Image.alpha_composite(bg, char)
    ink = Image.new("RGBA", (S, S), (20, 18, 24, 255))
    bg.paste(ink, (0, 0), Image.fromarray((np.array(edge_im) * (np.array(alpha) > 0)).astype(np.uint8)))
    # brilho das lentes dos oculos
    lg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ld = ImageDraw.Draw(lg)
    hc = np.array([0.10, 1.63, 0.0])
    for z in (0.05, -0.05):
        gx, gy = project(hc + [0.13, 0.03, z], cam, fwd, right, up, fov, S)
        rr = S * 0.035
        ld.ellipse([gx - rr, gy - rr, gx + rr, gy + rr], fill=(255, 150, 30, 150))
    bg = Image.alpha_composite(bg, lg.filter(ImageFilter.GaussianBlur(S * 0.012)))
    # clarao no cano
    mx, my = project(BUTT + AIM * 0.99 * GUN_S + np.array([0, 0.013, 0]), cam, fwd, right, up, fov, S)
    fl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fl)
    adir = np.array(project(BUTT + AIM * 1.2, cam, fwd, right, up, fov, S)) - np.array([mx, my])
    adir = adir / (np.linalg.norm(adir) + 1e-6)
    perp = np.array([-adir[1], adir[0]])
    for scale, col in ((1.0, (255, 140, 30, 200)), (0.65, (255, 220, 120, 230)), (0.35, (255, 255, 230, 255))):
        L, W = S * 0.2 * scale, S * 0.06 * scale
        tip = np.array([mx, my]) + adir * L
        pts = [tuple(np.array([mx, my]) + perp * W), tuple(tip), tuple(np.array([mx, my]) - perp * W), tuple(np.array([mx, my]) - adir * W * 0.6)]
        fd.polygon(pts, fill=col)
        for k in (-1, 1):
            side = np.array([mx, my]) + (adir * 0.35 + perp * 0.55 * k) * L
            fd.polygon([tuple(np.array([mx, my]) + perp * W * 0.4), tuple(side), tuple(np.array([mx, my]) - perp * W * 0.4)], fill=col)
    rng2 = np.random.default_rng(3)
    for i in range(18):
        a = rng2.uniform(-0.9, 0.9)
        dvec = adir * np.cos(a) + perp * np.sin(a)
        r0, r1 = S * rng2.uniform(0.05, 0.12), S * rng2.uniform(0.14, 0.26)
        p0, p1 = np.array([mx, my]) + dvec * r0, np.array([mx, my]) + dvec * r1
        fd.line([tuple(p0), tuple(p1)], fill=(255, 230, 140, 220), width=max(1, S // 220))
    bg = Image.alpha_composite(bg, fl.filter(ImageFilter.GaussianBlur(S * 0.003)))
    glow2 = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(glow2).ellipse([mx - S * 0.16, my - S * 0.16, mx + S * 0.16, my + S * 0.16], fill=(255, 180, 60, 90))
    bg = Image.alpha_composite(bg, glow2.filter(ImageFilter.GaussianBlur(S * 0.05)))
    return bg


def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return m


def main():
    art = compose(RES)
    out = os.path.join(ROOT, "docs", "icone_3d.png")
    art.save(out)
    print("render ok", out)
    if RES < 400:
        return
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for k, s in sizes.items():
        im = art.resize((s, s), Image.LANCZOS)
        masked = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        masked.paste(im, (0, 0), rounded_mask(s, int(s * 0.22)))
        d = os.path.join(ROOT, "android", "src", "main", "res", "mipmap-" + k)
        os.makedirs(d, exist_ok=True)
        masked.save(os.path.join(d, "ic_launcher.png"))
    for s in (16, 32, 64, 128):
        im = art.resize((s, s), Image.LANCZOS)
        masked = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        masked.paste(im, (0, 0), rounded_mask(s, int(s * 0.22)))
        masked.save(os.path.join(ROOT, "desktop", "src", "main", "resources", "icon%d.png" % s) if os.path.isdir(os.path.join(ROOT, "desktop", "src", "main", "resources")) else os.path.join(ROOT, "assets", "icon%d.png" % s))
    print("icones gerados")


if __name__ == "__main__":
    main()
