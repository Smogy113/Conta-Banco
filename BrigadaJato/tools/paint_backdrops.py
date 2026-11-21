#!/usr/bin/env python3
"""Pintor de fundos em pixel art (paralaxe) inspirado nas referencias do projeto.

Gera, para cada tema, um ceu (sky) e tres camadas (far, mid, near) com transparencia, que o jogo
desenha com paralaxe. Tudo e pintado em baixa resolucao (1 pixel = 2 unidades do mundo) e
ampliado sem suavizar, para ficar com cara de pixel art pintada a mao:
  - degrades com pontilhado (dithering Bayer) em vez de degrade liso
  - silhuetas com luz de contorno do lado do sol e sombra do outro
  - nevoa de profundidade: quanto mais longe, mais a cor puxa para a do horizonte
  - as camadas repetem na horizontal sem emenda

Uso: python3 tools/paint_backdrops.py [tema ...]   (sem argumentos: todos)
Saida: assets/backdrops/<tema>_{sky,far,mid,near}.png e docs/fundos/<tema>.png (previa)
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..")
OUT = os.path.join(ROOT, "assets", "backdrops")
PREVIEW = os.path.join(ROOT, "docs", "fundos")

# tamanho das camadas em pixels (o mundo usa o dobro: far 1900x950, mid 1500x750, near 1200x600)
SIZES = {"far": (950, 475), "mid": (750, 375), "near": (600, 300)}
SKY = (480, 270)

BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], float) / 16.0


def rgb(h):
    if isinstance(h, tuple):
        return h
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def mix(a, b, t):
    a, b = rgb(a), rgb(b)
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, k):
    """k < 1 escurece, k > 1 clareia (em direcao ao branco)"""
    c = rgb(c)
    if k <= 1:
        return tuple(int(v * k) for v in c)
    return mix(c, (255, 255, 255), k - 1)


class Layer:
    """Imagem RGBA que repete na horizontal; coordenadas com y = 0 embaixo (como no mundo)."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)

    # ---- conversao: y de baixo para cima
    def Y(self, y):
        return self.h - 1 - y

    def _wrap(self, xs):
        lo, hi = min(xs), max(xs)
        offs = [0]
        if lo < 0:
            offs.append(self.w)
        if hi >= self.w:
            offs.append(-self.w)
        return offs

    def poly(self, pts, col, a=255):
        c = rgb(col) + (a,)
        for o in self._wrap([p[0] for p in pts]):
            self.d.polygon([(x + o, self.Y(y)) for x, y in pts], fill=c)

    def rect(self, x0, y0, x1, y1, col, a=255):
        if x1 < x0:
            x0, x1 = x1, x0
        if y1 < y0:
            y0, y1 = y1, y0
        c = rgb(col) + (a,)
        for o in self._wrap([x0, x1]):
            self.d.rectangle([x0 + o, self.Y(y1), x1 + o, self.Y(y0)], fill=c)

    def ellipse(self, cx, cy, rx, ry, col, a=255):
        c = rgb(col) + (a,)
        for o in self._wrap([cx - rx, cx + rx]):
            self.d.ellipse([cx - rx + o, self.Y(cy + ry), cx + rx + o, self.Y(cy - ry)], fill=c)

    def line(self, pts, col, width=1, a=255):
        c = rgb(col) + (a,)
        for o in self._wrap([p[0] for p in pts]):
            self.d.line([(x + o, self.Y(y)) for x, y in pts], fill=c, width=width)

    def px(self, x, y, col, a=255):
        x = int(x) % self.w
        y = int(self.Y(y))
        if 0 <= y < self.h:
            self.im.putpixel((x, y), rgb(col) + (a,))

    def paste(self, other):
        self.im.alpha_composite(other.im)

    def arr(self):
        return np.array(self.im).astype(np.float32)

    def set_arr(self, a):
        self.im = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")
        self.d = ImageDraw.Draw(self.im)


def gradient(w, h, stops, steps=10, dither=True):
    """degrade vertical (stops: [(pos 0..1 de cima para baixo, cor), ...]) com pontilhado"""
    ys = np.arange(h)[:, None] / max(1, h - 1)
    out = np.zeros((h, w, 3), np.float32)
    pos = [s[0] for s in stops]
    cols = [np.array(rgb(s[1]), np.float32) for s in stops]
    t = np.repeat(ys, w, axis=1)
    if dither:
        b = np.tile(BAYER, (h // 4 + 1, w // 4 + 1))[:h, :w]
        t = np.floor(t * steps + b) / steps
    for i in range(len(stops) - 1):
        a, bpos = pos[i], pos[i + 1]
        m = (t >= a) & (t <= bpos)
        k = ((t - a) / max(1e-6, bpos - a))[..., None]
        seg = cols[i] * (1 - k) + cols[i + 1] * k
        out[m] = seg[m]
    out[t < pos[0]] = cols[0]
    out[t > pos[-1]] = cols[-1]
    return out


def edge_light(layer, light=(-1, 1), lit=None, dark=None, width=1, dark_width=1):
    """luz de contorno: pixels opacos cuja vizinhanca do lado da luz e transparente clareiam;
    os do lado oposto escurecem (da volume as silhuetas chapadas)"""
    a = layer.arr()
    al = a[..., 3] > 127
    lx, ly = light  # ly: +1 = luz vem de cima

    def shifted(m, dx, dy):
        r = np.roll(m, -dy, axis=0) if dy else m
        r = np.roll(r, dx, axis=1) if dx else r
        if dy > 0:
            r[:dy, :] = False
        elif dy < 0:
            r[dy:, :] = False
        return r

    if lit is not None:
        for k in range(1, width + 1):
            nb = shifted(al, -lx * k, ly * k)
            e = al & ~nb
            for c in range(3):
                a[..., c][e] = a[..., c][e] * 0.45 + lit[c] * 0.55
    if dark is not None:
        for k in range(1, dark_width + 1):
            nb = shifted(al, lx * k, -ly * k)
            e = al & ~nb
            for c in range(3):
                a[..., c][e] = a[..., c][e] * 0.5 + dark[c] * 0.5
    layer.set_arr(a)


def fog(layer, col, amount, bottom=None, bottom_h=0.25, top_fade=None):
    """puxa as cores para a nevoa; 'bottom' adiciona nevoa mais forte embaixo (pontilhada)"""
    a = layer.arr()
    c = np.array(rgb(col), np.float32)
    a[..., :3] = a[..., :3] * (1 - amount) + c * amount
    h, w = a.shape[:2]
    if bottom is not None:
        bc = np.array(rgb(bottom[0]), np.float32)
        strength = bottom[1]
        ys = np.arange(h)[:, None] / h
        k = np.clip((ys - (1 - bottom_h)) / bottom_h, 0, 1) * strength
        b = np.tile(BAYER, (h // 4 + 1, w // 4 + 1))[:h, :w]
        k = np.floor(k * 8 + b) / 8
        k = np.clip(k, 0, strength)[..., None]
        a[..., :3] = a[..., :3] * (1 - k) + bc * k
    layer.set_arr(a)


def finish(layer):
    """fundo opaco ate a base (o jogo completa abaixo da textura com a cor da ultima linha)"""
    a = layer.arr()
    a[-1, :, 3] = 255
    # garante que a ultima linha e opaca e uniforme o bastante
    layer.set_arr(a)


# ------------------------------------------------------------------ elementos

def ridge(layer, rnd, base, amp, col, rough=0.5, freq=3, peaks=False):
    """silhueta de morros/serra que repete na horizontal"""
    w = layer.w
    n = w
    h = np.zeros(n)
    for o in range(1, 6):
        f = freq * (2 ** (o - 1))
        ph = rnd.random() * math.tau
        h += np.sin(np.arange(n) / n * math.tau * f + ph) * (rough ** (o - 1))
        ph2 = rnd.random() * math.tau
        h += np.cos(np.arange(n) / n * math.tau * (f + 1) + ph2) * 0.5 * (rough ** (o - 1))
    h = (h - h.min()) / (h.max() - h.min() + 1e-6)
    if peaks:
        h = h ** 1.6
    ys = base + h * amp
    pts = [(0, -2)] + [(x, ys[x]) for x in range(n)] + [(n - 1, -2)]
    layer.poly(pts, col)
    return ys


def sun(layer, cx, cy, r, core, halo, rings=4):
    for i in range(rings, 0, -1):
        rr = r * (1 + i * 0.45)
        layer.ellipse(cx, cy, rr, rr, mix(halo, core, 1 - i / (rings + 1)), a=int(40 + 30 * (rings - i) / rings))
    layer.ellipse(cx, cy, r, r, core)
    layer.ellipse(cx - r * 0.25, cy + r * 0.25, r * 0.55, r * 0.55, shade(core, 1.15))


def moon(layer, cx, cy, r, col, rnd):
    for i in range(3, 0, -1):
        layer.ellipse(cx, cy, r * (1 + i * 0.35), r * (1 + i * 0.35), col, a=18 + 10 * (3 - i))
    layer.ellipse(cx, cy, r, r, col)
    for _ in range(7):
        a = rnd.random() * math.tau
        d = rnd.random() * r * 0.7
        cr = r * (0.06 + rnd.random() * 0.12)
        layer.ellipse(cx + math.cos(a) * d, cy + math.sin(a) * d, cr, cr, shade(col, 0.86))
    # sombra do lado oposto
    layer.ellipse(cx + r * 0.35, cy - r * 0.3, r * 0.75, r * 0.75, shade(col, 0.93), a=90)


def _vnoise(rnd, w, h, cells):
    """ruido suave (valor) com interpolacao bicubica simples, 0..1"""
    g = np.array([[rnd.random() for _ in range(cells + 1)] for _ in range(cells + 1)], np.float32)
    ys = np.linspace(0, cells, h, endpoint=False)
    xs = np.linspace(0, cells, w, endpoint=False)
    y0 = ys.astype(int); x0 = xs.astype(int)
    fy = ys - y0; fx = xs - x0
    fy = fy * fy * (3 - 2 * fy); fx = fx * fx * (3 - 2 * fx)
    a = g[y0][:, x0]; b = g[y0][:, x0 + 1]; c = g[y0 + 1][:, x0]; d = g[y0 + 1][:, x0 + 1]
    top = a + (b - a) * fx[None, :]
    bot = c + (d - c) * fx[None, :]
    return top + (bot - top) * fy[:, None]


def _fbm(rnd, w, h, octaves=4, base=3):
    n = np.zeros((h, w), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        n += _vnoise(rnd, w, h, base * 2 ** o) * amp
        tot += amp
        amp *= 0.5
    return n / tot


def planet(layer, cx, cy, r, rnd, light=(-0.6, 0.55)):
    """planeta em pixel art: oceano, continentes, nuvens, terminador com pontilhado e brilho da atmosfera"""
    A = layer.arr()
    H, W = A.shape[:2]
    R = int(r)
    # brilho da atmosfera (misturado de verdade, em degraus)
    G = R + 14
    gy, gx = np.mgrid[-G:G + 1, -G:G + 1].astype(np.float32)
    dist = np.sqrt(gx * gx + gy * gy) / r
    glow = np.clip(1 - (dist - 1) / 0.3, 0, 1) * (dist > 1)
    glow = np.floor(glow * 4) / 4 * 0.55
    X0, Y0 = int(cx) - G, layer.Y(int(cy)) - G
    for j in range(2 * G + 1):
        Yy = Y0 + j
        if 0 <= Yy < H:
            xs_ = np.arange(2 * G + 1) + X0
            ok = (xs_ >= 0) & (xs_ < W) & (glow[j] > 0)
            k_ = glow[j, ok][:, None]
            A[Yy, xs_[ok], :3] = A[Yy, xs_[ok], :3] * (1 - k_) + np.array([80, 150, 255]) * k_
    size = 2 * R + 1
    land = _fbm(rnd, size, size, 5, 3)
    clouds = _fbm(rnd, size, size, 4, 4)
    yy, xx = np.mgrid[-R:R + 1, -R:R + 1].astype(np.float32)
    yy = -yy  # y para cima
    d2 = (xx * xx + yy * yy) / (r * r)
    inside = d2 <= 1
    z = np.sqrt(np.clip(1 - d2, 0, 1))
    lx, ly = light
    lz = math.sqrt(max(0.0, 1 - lx * lx - ly * ly))
    lam = (xx / r * lx + yy / r * ly + z * lz)
    ocean = np.array([30, 70, 150], np.float32); ocean2 = np.array([50, 110, 190], np.float32)
    green = np.array([70, 140, 70], np.float32); sand = np.array([170, 150, 90], np.float32)
    ice = np.array([225, 235, 245], np.float32)
    col = ocean[None, None] + (ocean2 - ocean)[None, None] * np.clip(land * 1.6 - 0.3, 0, 1)[..., None]
    isl = land > 0.5
    col[isl] = green
    col[(land > 0.62)] = sand
    col[(land > 0.52) & (land < 0.56)] = green * 0.8
    lat = np.abs(yy / r)
    col[lat + (clouds - 0.5) * 0.35 > 0.86] = ice
    cm = clouds > 0.6
    col[cm] = col[cm] * 0.25 + np.array([240, 245, 250]) * 0.75
    # iluminacao em faixas (pixel art) + pontilhado no terminador
    by = BAYER[(np.arange(size)[:, None] % 4), (np.arange(size)[None, :] % 4)]
    li = np.clip(lam, 0, 1)
    bands = np.floor(li * 4 + by * 0.9) / 4
    shadeK = 0.28 + 0.72 * np.clip(bands, 0, 1)
    shaded = col * shadeK[..., None]
    shaded = shaded * np.array([0.92, 0.95, 1.1])
    # borda iluminada pela atmosfera
    rim = (d2 > 0.86) & inside & (lam > -0.1)
    shaded[rim] = shaded[rim] * 0.5 + np.array([120, 190, 255]) * 0.5
    x0, y0 = int(cx) - R, layer.Y(int(cy)) - R
    for j in range(size):
        Y = y0 + j
        if not 0 <= Y < H:
            continue
        for_x = np.arange(size) + x0
        ok = inside[j] & (for_x >= 0) & (for_x < W)
        A[Y, for_x[ok], :3] = np.clip(shaded[j, ok], 0, 255)
        A[Y, for_x[ok], 3] = 255
    layer.set_arr(A)


def moon_base(t, rnd, x, gy, col, lit):
    """base lunar pequena: cupula com vidro, modulo com janelas acesas, tubo e antena"""
    dark, mid, hi = shade(col, 0.62), shade(col, 0.82), shade(col, 1.18)
    t.rect(x - 22, gy - 2, x + 26, gy + 4, dark)
    t.ellipse(x - 10, gy + 4, 11, 10, mid)
    t.ellipse(x - 11, gy + 5, 10, 9, col)
    t.ellipse(x - 13, gy + 8, 5, 4, lit)
    t.px(x - 15, gy + 10, (255, 255, 255))
    t.rect(x + 3, gy + 4, x + 21, gy + 12, mid)
    t.rect(x + 3, gy + 11, x + 21, gy + 12, hi)
    for k in range(3):
        t.rect(x + 6 + k * 5, gy + 7, x + 8 + k * 5, gy + 9, (255, 214, 120))
    t.rect(x - 1, gy + 4, x + 3, gy + 7, dark)
    t.line([(x + 17, gy + 12), (x + 17, gy + 30)], dark, 1)
    t.line([(x + 14, gy + 26), (x + 20, gy + 28)], dark, 1)
    t.px(x + 17, gy + 31, (255, 90, 80))
    t.ellipse(x + 17, gy + 31, 3, 3, (255, 90, 80), a=70)


def stars(layer, rnd, n, y_min, col=(255, 255, 255)):
    for _ in range(n):
        x = rnd.randrange(layer.w)
        y = rnd.uniform(y_min, layer.h - 1)
        a = rnd.choice([90, 140, 200, 255])
        layer.px(x, y, col, a)
        if a == 255 and rnd.random() < 0.15:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                layer.px(x + dx, y + dy, col, 110)


def cloud(layer, rnd, cx, cy, w, h, lit, mid, dark, a=255):
    """nuvem de pixel art: bolas empilhadas com topo iluminado e base sombreada"""
    blobs = []
    n = max(3, int(w / (h * 0.8)))
    for i in range(n):
        bx = cx - w / 2 + (i + 0.5) * w / n + rnd.uniform(-3, 3)
        top = h * (0.55 + 0.45 * math.sin(math.pi * (i + 0.5) / n)) * rnd.uniform(0.75, 1.1)
        blobs.append((bx, cy, w / n * 0.85, top))
    for bx, by, rx, ry in blobs:  # sombra
        layer.ellipse(bx, by - 1, rx, ry, dark, a)
    for bx, by, rx, ry in blobs:
        layer.ellipse(bx - 1, by + 1, rx * 0.95, ry * 0.92, mid, a)
    for bx, by, rx, ry in blobs:  # luz no topo
        layer.ellipse(bx - rx * 0.2, by + ry * 0.35, rx * 0.6, ry * 0.5, lit, a)
    layer.rect(cx - w / 2 - 2, cy - h * 0.25, cx + w / 2 + 2, cy, dark, a)


def cloud_band(layer, rnd, y, thick, col, a=255, gaps=0.35):
    """faixa longa de nuvem (por do sol)"""
    x = rnd.uniform(0, layer.w)
    total = 0
    while total < layer.w:
        seg = rnd.uniform(30, 120)
        if rnd.random() > gaps:
            th = thick * rnd.uniform(0.5, 1.2)
            layer.ellipse(x + seg / 2, y + rnd.uniform(-thick, thick), seg / 2, th, col, a)
        x += seg
        total += seg


def birds(layer, rnd, n, y0, y1, col):
    for _ in range(n):
        x = rnd.uniform(0, layer.w)
        y = rnd.uniform(y0, y1)
        s = rnd.choice([1, 1, 2])
        layer.line([(x - 2 * s, y + s), (x, y), (x + 2 * s, y + s)], col)


def windows_grid(layer, rnd, x0, y0, x1, y1, lit, dark, chance=0.35, ww=2, wh=3, gx=5, gy=7):
    y = y0 + 4
    while y + wh < y1 - 2:
        x = x0 + 3
        while x + ww < x1 - 2:
            if rnd.random() < chance:
                layer.rect(x, y, x + ww - 1, y + wh - 1, lit)
            elif dark is not None and rnd.random() < 0.3:
                layer.rect(x, y, x + ww - 1, y + wh - 1, dark)
            x += gx
        y += gy


def tower(layer, rnd, x, base, w, h, wall, roof, kind="cone", win=None, trim=None):
    layer.rect(x - w / 2, base, x + w / 2, base + h, wall)
    top = base + h
    if kind == "cone":
        layer.poly([(x - w / 2 - 2, top), (x + w / 2 + 2, top), (x, top + w * 1.3)], roof)
    elif kind == "battlement":
        layer.rect(x - w / 2 - 1, top, x + w / 2 + 1, top + 3, wall)
        k = x - w / 2 - 1
        while k < x + w / 2:
            layer.rect(k, top + 3, k + 2, top + 6, wall)
            k += 4
    elif kind == "dome":
        layer.ellipse(x, top, w / 2 + 1, w / 2, roof)
        layer.rect(x - 0.5, top + w / 2, x + 0.5, top + w / 2 + 6, roof)
    elif kind == "spire":
        layer.poly([(x - w / 2, top), (x + w / 2, top), (x, top + w * 2.6)], roof)
    if trim is not None:
        layer.rect(x - w / 2, top - 2, x + w / 2, top, trim)
    if win is not None:
        for yy in range(int(base + 6), int(top - 4), 9):
            if rnd.random() < 0.6:
                layer.rect(x - 1, yy, x, yy + 3, win)


def house(layer, rnd, x, base, w, h, wall, beam, roof, win, roof_kind="gable"):
    layer.rect(x, base, x + w, base + h, wall)
    # enxaimel (vigas)
    layer.rect(x, base + h - 2, x + w, base + h, beam)
    layer.rect(x, base + h * 0.5, x + w, base + h * 0.5 + 1, beam)
    for k in range(0, int(w) + 1, max(4, int(w / 4))):
        layer.rect(x + k, base, x + k + 1, base + h, beam)
    if rnd.random() < 0.6:
        layer.line([(x, base + h * 0.5), (x + w / 4, base + h)], beam)
    # telhado
    if roof_kind == "gable":
        layer.poly([(x - 3, base + h), (x + w + 3, base + h), (x + w / 2, base + h + w * 0.55)], roof)
        layer.line([(x - 3, base + h), (x + w / 2, base + h + w * 0.55)], shade(roof, 0.7))
    else:
        layer.poly([(x - 2, base + h), (x + w + 2, base + h), (x + w - 2, base + h + 6), (x + 2, base + h + 6)], roof)
    # janelas acesas
    for k in range(2, int(w) - 3, 6):
        if rnd.random() < 0.7:
            wy = base + h * 0.55 + 2
            layer.rect(x + k, wy, x + k + 2, wy + 3, win)
            layer.rect(x + k, wy - 1, x + k + 2, wy - 1, shade(beam, 0.8))
    # chamine
    if rnd.random() < 0.5:
        cx = x + w * rnd.uniform(0.2, 0.7)
        layer.rect(cx, base + h + 2, cx + 3, base + h + w * 0.45, shade(wall, 0.7))


def column(layer, rnd, x, base, w, h, col, dark, broken=False):
    layer.rect(x - w / 2 - 2, base, x + w / 2 + 2, base + 3, col)  # base
    top = base + h
    if broken:
        pts = [(x - w / 2, base + 3)]
        steps = 4
        for i in range(steps + 1):
            pts.append((x - w / 2 + w * i / steps, top - rnd.uniform(0, h * 0.25)))
        pts.append((x + w / 2, base + 3))
        layer.poly(pts, col)
    else:
        layer.rect(x - w / 2, base + 3, x + w / 2, top, col)
        layer.rect(x - w / 2 - 2, top, x + w / 2 + 2, top + 3, col)  # capitel
        layer.rect(x - w / 2 - 1, top + 3, x + w / 2 + 1, top + 4, dark)
    for k in range(1, int(w), 3):  # caneluras
        layer.rect(x - w / 2 + k, base + 4, x - w / 2 + k, top - (h * 0.2 if broken else 1), dark)


def arch(layer, x, base, w, h, thick, col, broken_side=0):
    """arco romano: dois pilares + volta de cima"""
    layer.rect(x - w / 2, base, x - w / 2 + thick, base + h, col)
    if broken_side != 2:
        layer.rect(x + w / 2 - thick, base, x + w / 2, base + h, col)
    r = w / 2
    pts_out, pts_in = [], []
    for i in range(0, 181, 10):
        a = math.radians(180 - i)
        if broken_side == 1 and i > 120:
            break
        pts_out.append((x + math.cos(a) * r, base + h + math.sin(a) * r * 0.75))
        pts_in.append((x + math.cos(a) * (r - thick), base + h + math.sin(a) * (r - thick) * 0.75))
    if len(pts_out) > 1:
        layer.poly(pts_out + pts_in[::-1], col)


def dead_tree(layer, rnd, x, base, h, col, width=3):
    def branch(x0, y0, ang, length, wdt, depth):
        x1 = x0 + math.cos(ang) * length
        y1 = y0 + math.sin(ang) * length
        layer.line([(x0, y0), (x1, y1)], col, max(1, int(wdt)))
        if depth <= 0 or length < 4:
            return
        for s in (-1, 1):
            if rnd.random() < 0.85:
                branch(x1, y1, ang + s * rnd.uniform(0.3, 0.8), length * rnd.uniform(0.55, 0.75), wdt * 0.65, depth - 1)

    branch(x, base, math.pi / 2 + rnd.uniform(-0.15, 0.15), h * 0.45, width, 5)


def grass(layer, rnd, y_of, col, density=0.6, hmax=5):
    for x in range(0, layer.w):
        if rnd.random() < density:
            y = y_of(x)
            hh = rnd.randint(1, hmax)
            layer.line([(x, y), (x + rnd.choice([-1, 0, 1]), y + hh)], col)


def stall(layer, rnd, x, base, w, h, post, awning_a, awning_b, goods):
    layer.rect(x, base, x + 1, base + h, post)
    layer.rect(x + w - 1, base, x + w, base + h, post)
    layer.rect(x, base, x + w, base + 5, shade(post, 1.1))  # balcao
    for k in range(0, int(w), 4):  # mercadorias
        layer.ellipse(x + k + 2, base + 6, 1.5, 1.5, rnd.choice(goods))
    # toldo listrado
    top = base + h
    stripes = max(3, int(w / 4))
    for i in range(stripes):
        c = awning_a if i % 2 == 0 else awning_b
        x0 = x - 2 + i * (w + 4) / stripes
        x1 = x - 2 + (i + 1) * (w + 4) / stripes
        layer.poly([(x0, top), (x1, top), (x1 - 1, top + 7), (x0 + 1, top + 7)], c)
        layer.poly([(x0, top), (x1, top), ((x0 + x1) / 2, top - 3)], c)


def bunting(layer, rnd, x0, y0, x1, y1, cols, line_col):
    pts = []
    n = 14
    for i in range(n + 1):
        t = i / n
        pts.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t - math.sin(t * math.pi) * 6))
    layer.line(pts, line_col)
    for i in range(1, n, 1):
        x, y = pts[i]
        layer.poly([(x - 1.5, y), (x + 1.5, y), (x, y - 4)], cols[i % len(cols)])


def ship(layer, rnd, x, base, w, hull, wood, sail=None):
    layer.poly([(x - w / 2, base + 8), (x + w / 2 + 6, base + 10), (x + w / 2, base), (x - w / 2 + 6, base)], hull)
    layer.rect(x - w / 2, base + 8, x + w / 2, base + 9, shade(hull, 1.3))
    masts = 2 if w > 40 else 1
    for m in range(masts):
        mx = x - w * 0.2 + m * w * 0.4
        mh = 34 + 10 * (m == 0)
        layer.rect(mx, base + 9, mx + 1, base + 9 + mh, wood)
        for yy in (0.45, 0.75):
            ly = base + 9 + mh * yy
            layer.rect(mx - 9, ly, mx + 10, ly + 1, wood)
            if sail is not None:
                layer.poly([(mx - 8, ly), (mx + 9, ly), (mx + 8, ly - 8), (mx - 7, ly - 8)], sail)
        layer.line([(mx, base + 9 + mh), (x + w / 2 + 6, base + 10)], shade(wood, 0.8))
        layer.line([(mx, base + 9 + mh), (x - w / 2, base + 8)], shade(wood, 0.8))


def crane(layer, x, base, h, col):
    layer.rect(x, base, x + 2, base + h, col)
    layer.line([(x - 14, base + h), (x + 24, base + h)], col, 2)
    layer.line([(x + 1, base + h + 6), (x - 14, base + h)], col)
    layer.line([(x + 1, base + h + 6), (x + 24, base + h)], col)
    layer.line([(x + 20, base + h), (x + 20, base + h - 18)], col)


def mushroom(layer, rnd, x, base, s, stem, cap, spot, glow):
    layer.ellipse(x, base + s * 1.6, s * 2.6, s * 1.6, glow, a=45)
    layer.rect(x - s * 0.25, base, x + s * 0.25, base + s * 1.4, stem)
    layer.ellipse(x, base + s * 1.4, s, s * 0.6, cap)
    layer.ellipse(x - s * 0.3, base + s * 1.6, s * 0.35, s * 0.2, shade(cap, 1.25))
    if s > 3:
        layer.px(x + s * 0.3, base + s * 1.5, spot)


def waterfall(layer, rnd, x, top, bottom, w, col, light):
    layer.rect(x, bottom, x + w, top, col, a=200)
    for k in range(int(w)):
        if rnd.random() < 0.5:
            yy = rnd.uniform(bottom, top)
            layer.rect(x + k, yy, x + k, yy + rnd.uniform(4, 14), light)
    layer.ellipse(x + w / 2, bottom + 1, w * 0.9, 3, light, a=220)


def leaf(layer, cx, cy, length, width, ang, col, rib):
    pts = []
    for i in range(0, 21):
        t = i / 20
        r = math.sin(t * math.pi) * width
        px_ = cx + math.cos(ang) * length * t
        py_ = cy + math.sin(ang) * length * t
        pts.append((px_ - math.sin(ang) * r, py_ + math.cos(ang) * r))
    for i in range(20, -1, -1):
        t = i / 20
        r = math.sin(t * math.pi) * width
        px_ = cx + math.cos(ang) * length * t
        py_ = cy + math.sin(ang) * length * t
        pts.append((px_ + math.sin(ang) * r, py_ - math.cos(ang) * r))
    layer.poly(pts, col)
    layer.line([(cx, cy), (cx + math.cos(ang) * length * 0.9, cy + math.sin(ang) * length * 0.9)], rib)


def orb_tree(layer, rnd, x, base, h, trunk, orb, glow, dark):
    # tronco retorcido em varias raizes/galhos
    for _ in range(rnd.randint(3, 5)):
        xs = x + rnd.uniform(-h * 0.35, h * 0.35)
        pts = [(xs, base)]
        cx, cy = xs, base
        for i in range(6):
            cx += (x - cx) * 0.3 + rnd.uniform(-4, 4)
            cy += h / 6
            pts.append((cx, cy))
        layer.line(pts, trunk, rnd.choice([2, 3]))
    for _ in range(rnd.randint(2, 4)):
        ox = x + rnd.uniform(-h * 0.25, h * 0.25)
        oy = base + h * rnd.uniform(0.45, 1.0)
        r = h * rnd.uniform(0.07, 0.13)
        layer.ellipse(ox, oy, r * 1.9, r * 1.9, glow, a=40)
        layer.ellipse(ox, oy, r + 1, r + 1, dark)
        layer.ellipse(ox, oy, r, r, orb)
        layer.ellipse(ox - r * 0.3, oy + r * 0.3, r * 0.45, r * 0.45, shade(orb, 1.25))
        for k in range(3):  # galhos segurando a orbe
            a = rnd.uniform(0, math.tau)
            layer.line([(ox, oy), (ox + math.cos(a) * r * 1.3, oy + math.sin(a) * r * 1.3)], dark)


# ------------------------------------------------------------------ acabamento (textura, volume, musgo)

def _mask(layer):
    return np.array(layer.im)[..., 3] > 127


def _shift(m, dx, dy):
    """desloca a mascara (dy > 0 = para cima no desenho)"""
    r = np.roll(m, -dy, axis=0) if dy else m.copy()
    if dx:
        r = np.roll(r, dx, axis=1)
    if dy > 0:
        r[-dy:, :] = False
    elif dy < 0:
        r[:-dy, :] = False
    return r


def volume(layer, light_dx=-1, depth=6, lit=1.12, dark=0.72):
    """da volume: perto da borda do lado da luz clareia, do outro lado escurece (em degraus)"""
    a = layer.arr()
    m = a[..., 3] > 127
    for k in range(1, depth + 1):
        far_side = m & ~_shift(m, -light_dx * k, 0)  # borda do lado oposto a luz
        near_side = m & ~_shift(m, light_dx * k, 0)
        f = dark + (1 - dark) * (k - 1) / depth
        g = lit - (lit - 1) * (k - 1) / depth
        for c in range(3):
            ch = a[..., c]
            ch[far_side] = np.minimum(ch[far_side], ch[far_side] * f + 0)
        if k <= 2:
            for c in range(3):
                ch = a[..., c]
                ch[near_side] = np.clip(ch[near_side] * g, 0, 255)
    layer.set_arr(a)


def bricks(layer, rnd, bw=6, bh=3, mortar=0.82, vary=0.08, region=None):
    """textura de pedra/tijolo nos pixels opacos (fiadas desencontradas, cada bloco com um tom)"""
    a = layer.arr()
    h, w = a.shape[:2]
    m = a[..., 3] > 127
    yy, xx = np.mgrid[0:h, 0:w]
    row = yy // bh
    off = (row % 2) * (bw // 2)
    col = (xx + off) // bw
    seed = (row * 7919 + col * 104729) % 9973
    tone = 1 + ((seed * 2654435761 % 1000) / 1000.0 - 0.5) * 2 * vary
    line = ((yy % bh) == 0) | (((xx + off) % bw) == 0)
    k = np.where(line, mortar, tone)
    for c in range(3):
        ch = a[..., c]
        ch[m] = np.clip(ch[m] * k[m], 0, 255)
    layer.set_arr(a)


def speckle(layer, rnd, amount=0.08, strength=0.12):
    a = layer.arr()
    h, w = a.shape[:2]
    m = a[..., 3] > 127
    r = np.random.default_rng(rnd.randrange(1 << 30)).random((h, w))
    dk = m & (r < amount)
    lt = m & (r > 1 - amount * 0.6)
    for c in range(3):
        ch = a[..., c]
        ch[dk] *= 1 - strength
        ch[lt] = np.clip(ch[lt] * (1 + strength * 0.8), 0, 255)
    layer.set_arr(a)


def moss(layer, rnd, col, chance=0.5, drip=4):
    """musgo/hera nas bordas de cima das silhuetas, escorrendo um pouco"""
    a = layer.arr()
    m = a[..., 3] > 127
    top = m & ~_shift(m, 0, -1)  # pixel opaco com transparente em cima
    h, w = m.shape
    ys, xs = np.nonzero(top)
    c = np.array(rgb(col), np.float32)
    g = np.random.default_rng(rnd.randrange(1 << 30))
    for y, x in zip(ys, xs):
        if g.random() > chance:
            continue
        n = int(g.integers(1, drip + 1))
        for k in range(n):
            yy = y + k
            if yy < h and m[yy, x]:
                t = 0.85 - k * 0.12
                a[yy, x, :3] = a[yy, x, :3] * (1 - t) + c * t
        if y - 1 >= 0 and g.random() < 0.4:
            a[y - 1, x, :3] = c * 1.1
            a[y - 1, x, 3] = 255
    layer.set_arr(a)


def bush(layer, rnd, x, base, r, col, lit, dark):
    for _ in range(int(r * 0.9) + 2):
        bx = x + rnd.uniform(-r, r)
        by = base + rnd.uniform(0, r * 0.7)
        rr = rnd.uniform(r * 0.35, r * 0.6)
        layer.ellipse(bx, by, rr, rr * 0.85, dark)
    for _ in range(int(r * 0.9) + 2):
        bx = x + rnd.uniform(-r * 0.8, r * 0.8)
        by = base + rnd.uniform(r * 0.15, r * 0.8)
        rr = rnd.uniform(r * 0.3, r * 0.5)
        layer.ellipse(bx, by, rr, rr * 0.85, col)
    for _ in range(int(r * 0.6) + 1):
        bx = x + rnd.uniform(-r * 0.7, r * 0.5)
        by = base + rnd.uniform(r * 0.5, r * 0.95)
        layer.ellipse(bx, by, r * 0.22, r * 0.16, lit)


def sub(layer):
    """camada temporaria do mesmo tamanho (para pintar, dar acabamento e depois juntar)"""
    return Layer(layer.w, layer.h)


def cloud2(layer, rnd, cx, cy, w, h, lit, mid, dark, a=255):
    """nuvem de pixel art com base reta, sombra embaixo e topo iluminado"""
    t = sub(layer)
    n = max(3, int(w / (h * 0.9)))
    blobs = []
    for i in range(n):
        bx = cx - w / 2 + (i + 0.5) * w / n + rnd.uniform(-2, 2)
        top = h * (0.45 + 0.55 * math.sin(math.pi * (i + 0.5) / n)) * rnd.uniform(0.8, 1.15)
        blobs.append((bx, cy, w / n * 0.8, top))
    for bx, by, rx, ry in blobs:
        t.ellipse(bx, by, rx, ry, dark)
    for bx, by, rx, ry in blobs:
        t.ellipse(bx - 1, by + 2, rx * 0.95, ry * 0.9, mid)
    for bx, by, rx, ry in blobs:
        t.ellipse(bx - rx * 0.25, by + ry * 0.4, rx * 0.55, ry * 0.45, lit)
    arr = t.arr()
    yb = int(t.Y(cy - h * 0.18))
    arr[yb:, :, 3] = 0  # base reta
    arr[..., 3] = np.minimum(arr[..., 3], a)
    t.set_arr(arr)
    layer.paste(t)


# ------------------------------------------------------------------ montagem / previa

def save_layers(name, sky, layers):
    os.makedirs(OUT, exist_ok=True)
    Image.fromarray(np.clip(sky, 0, 255).astype(np.uint8), "RGB").save(os.path.join(OUT, name + "_sky.png"))
    for k, L in layers.items():
        finish(L)
        L.im.save(os.path.join(OUT, name + "_" + k + ".png"))


def preview(name, sky, layers, cam_y=0.5, world_h=960, cam_x=900):
    """monta a cena como o jogo desenha (camera no meio do mapa) para conferir"""
    VW, VH = 925, 520
    scale = 1280 / VW
    out = Image.fromarray(np.clip(sky, 0, 255).astype(np.uint8), "RGB").resize((1280, 720), Image.NEAREST).convert("RGBA")
    camY = world_h * cam_y
    minY = camY - VH / 2
    ks = {"far": 0.18, "mid": 0.38, "near": 0.6}
    wh = {"far": (1900, 950), "mid": (1500, 750), "near": (1200, 600)}
    depth = {"far": 0, "mid": 1, "near": 2}
    for k in ("far", "mid", "near"):
        if k not in layers:
            continue
        L = layers[k]
        lw, lh = wh[k]
        base_y = (camY - world_h * 0.5) * (1 - ks[k] * 0.7) + world_h * (0.18 - depth[k] * 0.06)
        y0 = base_y - lh * 0.12
        img = L.im.resize((int(lw * scale), int(lh * scale)), Image.NEAREST)
        xs = cam_x * (1 - ks[k])
        minX = cam_x - VW / 2
        start = xs + math.floor((minX - xs) / lw) * lw
        top_screen = 720 - (y0 + lh - minY) * scale
        x = start
        while x < minX + VW:
            out.alpha_composite(img, (int((x - minX) * scale), int(top_screen)))
            x += lw
        # preenchimento abaixo da textura com a cor da ultima linha
        bottom_screen = 720 - (y0 - minY) * scale
        if bottom_screen < 720:
            row = np.array(L.im)[-1]
            c = tuple(int(v) for v in row[:, :3].mean(axis=0))
            ImageDraw.Draw(out).rectangle([0, max(0, bottom_screen), 1280, 720], fill=c + (255,))
    os.makedirs(PREVIEW, exist_ok=True)
    out.convert("RGB").save(os.path.join(PREVIEW, name + ".png"))
    return out


THEMES = {}


def theme(name):
    def reg(fn):
        THEMES[name] = fn
        return fn

    return reg


def new_layers():
    return {k: Layer(*v) for k, v in SIZES.items()}


# ------------------------------------------------------------------ temas

def sky_layer(stops):
    W, H = SKY
    S = Layer(W, H)
    S.set_arr(np.dstack([gradient(W, H, stops), np.full((H, W), 255, np.float32)]))
    return S


def gothic_arch(layer, x, base, w, h, col, a=255):
    """abertura em arco ogival (ponta em cima)"""
    pts = [(x - w / 2, base), (x - w / 2, base + h)]
    for i in range(1, 9):
        t = i / 9
        ang = math.pi * t / 2
        pts.append((x - w / 2 + (w / 2) * (1 - math.cos(ang)) * 1.0, base + h + math.sin(ang) * w * 0.62))
    pts.append((x, base + h + w * 0.75))
    for i in range(8, 0, -1):
        t = i / 9
        ang = math.pi * t / 2
        pts.append((x + w / 2 - (w / 2) * (1 - math.cos(ang)), base + h + math.sin(ang) * w * 0.62))
    pts += [(x + w / 2, base + h), (x + w / 2, base)]
    layer.poly(pts, col, a)


def ruin_skyline(layer, rnd, base, hmin, hmax, col, dense=1.0):
    x = 0
    while x < layer.w:
        r = rnd.random()
        bw = rnd.randint(10, 26)
        bh = rnd.randint(hmin, hmax)
        if r < 0.25:
            tower(layer, rnd, x, base, bw, bh, col, col, rnd.choice(["dome", "cone", "battlement", "spire"]))
        elif r < 0.45:
            for k in range(rnd.randint(2, 4)):
                column(layer, rnd, x + k * 9, base, 5, bh * rnd.uniform(0.4, 0.8), col, col, broken=rnd.random() < 0.6)
        elif r < 0.65:
            arch(layer, x + bw, base, bw * 1.6, bh * 0.45, 4, col, broken_side=rnd.choice([0, 1, 2]))
        else:
            layer.rect(x, base, x + bw * 1.5, base + bh * 0.55, col)
            layer.ellipse(x + bw * 0.75, base + bh * 0.55, bw * 0.6, bw * 0.45, col)
        x += int((bw + rnd.randint(4, 26)) / dense)


@theme("desert")
def desert(rnd):
    """ruinas de pedra-areia sob ceu claro (fortaleza, poeira)"""
    W, H = SKY
    S = sky_layer([(0, 0x3f86b0), (0.4, 0x8cc6d6), (0.72, 0xeedcb0), (1, 0xf7e7c0)])
    sun(S, W * 0.74, H * 0.5, 13, (255, 250, 228), (255, 226, 160), rings=5)
    for _ in range(10):
        cloud2(S, rnd, rnd.uniform(0, W), rnd.uniform(H * 0.42, H * 0.9), rnd.uniform(40, 120), rnd.uniform(10, 22),
               (255, 250, 236), (232, 226, 214), (168, 190, 200))
    birds(S, rnd, 10, H * 0.5, H * 0.85, (64, 54, 56))
    L = new_layers()
    f = L["far"]
    a1 = sub(f)
    ruin_skyline(a1, rnd, 150, 40, 95, (176, 190, 196))
    bricks(a1, rnd, 3, 2, 0.93, 0.03)
    volume(a1, -1, 2, lit=1.08, dark=0.88)
    fog(a1, (222, 224, 210), 0.42)
    f.paste(a1)
    a2 = sub(f)
    ridge(a2, rnd, 120, 30, (214, 196, 160), rough=0.45, freq=2)
    ruin_skyline(a2, rnd, 125, 40, 100, (196, 176, 140), dense=0.8)
    bricks(a2, rnd, 4, 2, 0.9, 0.05)
    volume(a2, -1, 3)
    fog(a2, (228, 218, 190), 0.3, bottom=((240, 226, 190), 0.55), bottom_h=0.65)
    f.paste(a2)
    # ---- meio
    m = L["mid"]
    sand, sand_d, roof = (204, 162, 108), (140, 104, 68), (176, 82, 56)
    st = sub(m)
    ys = ridge(st, rnd, 92, 26, (190, 150, 98), rough=0.5, freq=3)
    x = rnd.randint(0, 30)
    while x < m.w:
        r = rnd.random()
        gy = ys[int(x) % m.w] - 4
        if r < 0.28:
            n = rnd.randint(3, 5)
            for k in range(n):
                arch(st, x + k * 28 + 14, gy, 28, rnd.randint(46, 62), 6, sand, broken_side=2 if k == n - 1 and rnd.random() < 0.6 else 0)
            st.rect(x, gy + 70, x + n * 28 - (14 if rnd.random() < 0.5 else 0), gy + 78, sand)
            x += n * 28 + rnd.randint(20, 40)
        elif r < 0.52:
            tw, th = rnd.randint(24, 34), rnd.randint(85, 135)
            tower(st, rnd, x + tw / 2, gy, tw, th, sand, roof, rnd.choice(["cone", "battlement", "cone"]), win=(54, 36, 28), trim=sand_d)
            # parte desmoronada
            st.poly([(x + tw * 0.55, gy + th + 3), (x + tw + 3, gy + th + 3), (x + tw + 3, gy + th * 0.62), (x + tw * 0.8, gy + th * 0.78)], (0, 0, 0), a=0)
            x += tw + rnd.randint(30, 60)
        elif r < 0.76:
            wl = rnd.randint(40, 70)
            wh = rnd.randint(40, 70)
            pts = [(x, gy)]
            for i in range(9):
                pts.append((x + wl * i / 8, gy + wh * rnd.uniform(0.55, 1.0)))
            pts.append((x + wl, gy))
            st.poly(pts, sand)
            for _ in range(2):  # janelas/buracos
                hx = x + rnd.uniform(6, wl - 12)
                gothic_arch(st, hx, gy + 10, 7, 12, (0, 0, 0), a=0)
            x += wl + rnd.randint(20, 50)
        else:
            for k in range(rnd.randint(2, 3)):
                column(st, rnd, x + k * 18, gy, 9, rnd.randint(55, 100), sand, sand_d, broken=rnd.random() < 0.55)
            x += 70
    bricks(st, rnd, 6, 3, 0.84, 0.08)
    volume(st, -1, 5)
    speckle(st, rnd, 0.06, 0.1)
    moss(st, rnd, (104, 140, 64), chance=0.35, drip=3)
    edge_light(st, (-1, 1), lit=(255, 232, 180), dark=None)
    m.paste(st)
    veg = sub(m)
    for _ in range(16):
        x = rnd.uniform(0, m.w)
        bush(veg, rnd, x, ys[int(x) % m.w] - 4, rnd.uniform(5, 10), (108, 138, 66), (150, 176, 86), (70, 96, 48))
    for _ in range(4):
        x = rnd.uniform(0, m.w)
        dead_tree(veg, rnd, x, ys[int(x) % m.w] - 4, rnd.randint(55, 85), (92, 66, 46), 3)
    m.paste(veg)
    grass(m, rnd, lambda xx: ys[xx % m.w] - 2, (112, 140, 70), density=0.4, hmax=4)
    fog(m, (236, 222, 186), 0.1, bottom=((206, 172, 120), 0.6), bottom_h=0.55)
    # ---- perto
    n = L["near"]
    gr = sub(n)
    ys2 = ridge(gr, rnd, 80, 26, (128, 92, 58), rough=0.55, freq=4)
    for _ in range(12):
        x = rnd.uniform(0, n.w)
        gy = ys2[int(x) % n.w]
        if rnd.random() < 0.5:
            gr.rect(x, gy - 3, x + rnd.randint(10, 20), gy + rnd.randint(5, 10), (136, 100, 66))
        else:
            column(gr, rnd, x, gy - 3, 10, rnd.randint(22, 48), (140, 104, 68), (98, 70, 46), broken=True)
    bricks(gr, rnd, 7, 4, 0.86, 0.06)
    volume(gr, -1, 5)
    edge_light(gr, (-1, 1), lit=(220, 176, 116), dark=(66, 46, 30))
    n.paste(gr)
    v2 = sub(n)
    for _ in range(10):
        x = rnd.uniform(0, n.w)
        bush(v2, rnd, x, ys2[int(x) % n.w] - 3, rnd.uniform(6, 12), (74, 96, 48), (108, 132, 62), (50, 66, 34))
    n.paste(v2)
    grass(n, rnd, lambda xx: ys2[xx % n.w] - 1, (82, 102, 50), density=0.7, hmax=8)
    fog(n, (0, 0, 0), 0.0, bottom=((96, 68, 44), 0.7), bottom_h=0.55)
    return S.arr()[..., :3], L


@theme("city")
def city(rnd):
    """cidade gotica a noite, lua enorme e torre do relogio"""
    W, H = SKY
    S = sky_layer([(0, 0x100d26), (0.5, 0x2a2150), (0.8, 0x4c3a74), (1, 0x5e4680)])
    stars(S, rnd, 180, H * 0.3)
    moon(S, W * 0.68, H * 0.66, 34, (244, 236, 190), rnd)
    for _ in range(7):
        cloud_band(S, rnd, rnd.uniform(H * 0.4, H * 0.88), rnd.uniform(2.5, 5), rnd.choice([(66, 52, 108), (84, 66, 126)]), a=220)
    for _ in range(4):
        cloud2(S, rnd, rnd.uniform(0, W), rnd.uniform(H * 0.45, H * 0.8), rnd.uniform(40, 90), rnd.uniform(8, 14),
               (168, 150, 200), (108, 92, 150), (64, 50, 100), a=235)
    birds(S, rnd, 7, H * 0.5, H * 0.8, (28, 22, 46))
    L = new_layers()
    f = L["far"]
    for depth_col, base, hmin, hmax, fa in (((74, 76, 132), 150, 40, 120, 0.35), ((50, 52, 100), 120, 50, 150, 0.12)):
        t = sub(f)
        x = 0
        while x < t.w:
            bw = rnd.randint(9, 22)
            bh = rnd.randint(hmin, hmax)
            t.rect(x, 0, x + bw, base + bh, depth_col)
            k = rnd.random()
            if k < 0.45:
                t.poly([(x - 1, base + bh), (x + bw + 1, base + bh), (x + bw / 2, base + bh + bw * rnd.uniform(1.0, 2.6))], depth_col)
            elif k < 0.6:
                t.ellipse(x + bw / 2, base + bh, bw / 2, bw / 2.4, depth_col)
                t.rect(x + bw / 2, base + bh, x + bw / 2, base + bh + bw * 0.7, depth_col)
            windows_grid(t, rnd, x, base, x + bw, base + bh, (180, 190, 240), None, chance=0.1, ww=1, wh=2, gx=3, gy=5)
            windows_grid(t, rnd, x, base, x + bw, base + bh, (250, 200, 120), None, chance=0.05, ww=1, wh=2, gx=3, gy=5)
            x += bw + rnd.randint(-3, 4)
        volume(t, 1, 3, lit=1.15, dark=0.8)
        fog(t, (86, 70, 130), fa, bottom=((96, 78, 140), 0.5), bottom_h=0.45)
        f.paste(t)
    m = L["mid"]
    stone = (36, 32, 58)
    br = sub(m)
    br.rect(0, 0, m.w, 186, stone)
    glow = sub(m)
    for k in range(0, m.w, 32):  # arcos ogivais acesos (o brilho da cidade la dentro)
        gothic_arch(glow, k + 16, 122, 22, 30, (236, 120, 50))
        gothic_arch(glow, k + 16, 122, 18, 27, (250, 170, 70))
        gothic_arch(glow, k + 16, 128, 12, 20, (255, 214, 120))
    br.paste(glow)
    for k in range(0, m.w, 32):
        br.rect(k + 15, 122, k + 17, 168, stone)  # mainel
        br.rect(k + 6, 146, k + 26, 147, stone)
        br.rect(k, 112, k + 3, 190, (28, 24, 46))  # contraforte
    br.rect(0, 186, m.w, 192, stone)
    for k in range(0, m.w, 8):
        br.rect(k, 192, k + 4, 196, stone)  # ameias
    for cx in (m.w * 0.27, m.w * 0.77):  # torres do relogio
        tw = 44
        br.rect(cx - tw / 2, 196, cx + tw / 2, 300, stone)
        br.rect(cx - tw / 2 - 5, 300, cx + tw / 2 + 5, 307, stone)
        br.rect(cx - tw / 2 + 5, 307, cx + tw / 2 - 5, 334, stone)
        gothic_arch(br, cx, 312, 12, 10, (250, 190, 90))
        br.ellipse(cx, 334, tw / 2 - 5, 14, stone)
        br.rect(cx - 1, 346, cx + 1, 362, stone)
        br.ellipse(cx, 260, 16, 16, (26, 20, 30))
        br.ellipse(cx, 260, 14, 14, (240, 186, 96))
        br.ellipse(cx, 260, 11, 11, (255, 226, 160))
        for i in range(12):
            ang = i / 12 * math.tau
            br.px(cx + math.cos(ang) * 9, 260 + math.sin(ang) * 9, (90, 60, 40))
        br.line([(cx, 260), (cx, 268)], (60, 40, 30), 2)
        br.line([(cx, 260), (cx + 6, 257)], (60, 40, 30), 2)
        for yy in (212, 234):
            gothic_arch(br, cx - 8, yy, 5, 8, (250, 180, 80))
            gothic_arch(br, cx + 8, yy, 5, 8, (250, 180, 80))
    for _ in range(9):
        cx = rnd.uniform(0, m.w)
        tower(br, rnd, cx, 196, rnd.randint(8, 14), rnd.randint(25, 70), stone, stone, "spire", win=(250, 180, 80))
    bricks(br, rnd, 6, 3, 0.8, 0.07)
    volume(br, 1, 4, lit=1.25, dark=0.75)
    edge_light(br, (1, 1), lit=(130, 116, 176), dark=None)
    m.paste(br)
    fog(m, (40, 34, 70), 0.04, bottom=((24, 20, 40), 0.65), bottom_h=0.5)
    n = L["near"]
    rf = sub(n)
    roof = (18, 16, 30)
    x = 0
    while x < n.w:
        bw = rnd.randint(32, 64)
        bh = rnd.randint(70, 125)
        rf.rect(x, 0, x + bw, bh, roof)
        rf.poly([(x - 4, bh), (x + bw + 4, bh), (x + bw / 2, bh + bw * 0.48)], roof)
        if rnd.random() < 0.6:
            cxh = x + bw * 0.72
            rf.rect(cxh, bh, cxh + 5, bh + bw * 0.42, roof)
            rf.rect(cxh - 1, bh + bw * 0.42, cxh + 6, bh + bw * 0.42 + 2, roof)
        if rnd.random() < 0.8:
            wx = x + bw * rnd.uniform(0.25, 0.6)
            rf.rect(wx - 1, bh - 20, wx + 7, bh - 8, roof)
            rf.rect(wx, bh - 19, wx + 6, bh - 9, (250, 186, 90))
            rf.rect(wx + 3, bh - 19, wx + 3, bh - 9, roof)
            rf.rect(wx, bh - 14, wx + 6, bh - 14, roof)
        x += bw + rnd.randint(0, 6)
    volume(rf, 1, 3, lit=1.6, dark=0.8)
    edge_light(rf, (1, 1), lit=(84, 72, 128))
    n.paste(rf)
    return S.arr()[..., :3], L


@theme("ship")
def harbor(rnd):
    """porto ao por do sol: sol enorme no mar, barcos, guindastes e cais"""
    W, H = SKY
    S = sky_layer([(0, 0x1e3a4c), (0.28, 0x6b3a55), (0.52, 0xd0533c), (0.7, 0xf08f48), (0.8, 0xffd27a), (1, 0xffe2a0)])
    sun(S, W * 0.5, H * 0.26, 30, (255, 246, 206), (255, 196, 112), rings=5)
    for _ in range(10):  # faixas de nuvem iluminadas por baixo
        y = rnd.uniform(H * 0.35, H * 0.95)
        th = rnd.uniform(2.5, 6)
        c = rnd.choice([(120, 40, 52), (96, 34, 54), (150, 56, 58)])
        t = Layer(W, H)
        cloud_band(t, rnd, y, th, c)
        cloud_band(t, rnd, y - th * 0.6, th * 0.4, (250, 150, 90), a=200)
        S.paste(t)
    birds(S, rnd, 12, H * 0.45, H * 0.9, (40, 16, 26))
    L = new_layers()
    f = L["far"]
    sea_y = 175
    sea = sub(f)
    sea.set_arr(np.dstack([gradient(f.w, f.h, [(0, 0x3a1e30), (0.55, 0x4a2236), (0.6, 0x6b2c3c), (1, 0x2a1424)], steps=6),
                           np.zeros((f.h, f.w), np.float32)]))
    a = sea.arr()
    a[f.h - sea_y:, :, 3] = 255
    sea.set_arr(a)
    for k in range(80):  # brilhos do sol na agua
        y = sea_y - 3 - k * 2.0
        if y < 4:
            break
        for cx in np.arange(f.w * 0.5, f.w * 3, f.w):
            for _ in range(2):
                wd = (34 - k * 0.35) * rnd.uniform(0.4, 1.1)
                xx = (cx % f.w) + rnd.uniform(-12, 12)
                sea.rect(xx - wd, y, xx + wd, y, (255, 196, 120), a=max(40, 220 - k * 3))
    f.paste(sea)
    sh = sub(f)
    ridge(sh, rnd, sea_y, 22, (120, 54, 66), rough=0.45, freq=2)
    arr = sh.arr()
    arr[f.h - sea_y + 1:, :, 3] = 0  # a costa fica so acima do mar
    sh.set_arr(arr)
    for _ in range(5):
        ship(sh, rnd, rnd.uniform(0, f.w), sea_y - 4, rnd.randint(30, 50), (104, 46, 60), (104, 46, 60), sail=None)
    lx = rnd.uniform(0, f.w)
    sh.rect(lx, sea_y + 18, lx + 6, sea_y + 60, (104, 46, 60))  # farol
    sh.ellipse(lx + 3, sea_y + 63, 6, 6, (255, 220, 140), a=110)
    fog(sh, (210, 110, 90), 0.3)
    f.paste(sh)
    m = L["mid"]
    dk, wood = (58, 26, 36), (78, 38, 42)
    dock = sub(m)
    for k in range(0, m.w, 18):  # estacas dentro da agua (o mar do fundo aparece entre elas)
        dock.rect(k, 0, k + 3, 150, dk)
        dock.line([(k + 1, 100), (k + 19, 138)], dk)
    dock.rect(0, 140, m.w, 150, dk)
    x = 0
    while x < m.w:
        bw = rnd.randint(34, 62)
        bh = rnd.randint(34, 72)
        dock.rect(x, 150, x + bw, 150 + bh, dk)
        dock.poly([(x - 3, 150 + bh), (x + bw + 3, 150 + bh), (x + bw / 2, 150 + bh + 16)], dk)
        for k in range(2, bw - 4, 7):
            if rnd.random() < 0.5:
                dock.rect(x + k, 150 + bh * 0.45, x + k + 3, 150 + bh * 0.45 + 5, (255, 176, 86))
        x += bw + rnd.randint(50, 120)
    for _ in range(4):
        ship(dock, rnd, rnd.uniform(0, m.w), 128, rnd.randint(55, 85), dk, wood, sail=None)
    for _ in range(3):
        crane(dock, rnd.uniform(0, m.w), 150, rnd.randint(55, 85), dk)
    bricks(dock, rnd, 8, 2, 0.85, 0.05)
    volume(dock, 0, 3, lit=1.2, dark=0.8)
    edge_light(dock, (0, 1), lit=(246, 128, 84))
    m.paste(dock)
    n = L["near"]
    pier = sub(n)
    blk = (30, 14, 22)
    pier.rect(0, 0, n.w, 92, blk)
    for k in range(0, n.w, 26):
        pier.rect(k, 92, k + 5, 116, blk)
        pier.line([(k + 3, 114), (k + 29, 104)], blk)
    for _ in range(12):
        x = rnd.uniform(0, n.w)
        r = rnd.random()
        if r < 0.4:
            pier.rect(x, 92, x + 14, 106, blk)
            pier.rect(x, 99, x + 14, 99, (60, 30, 36))
        elif r < 0.75:
            pier.ellipse(x, 99, 6, 7, blk)
            pier.rect(x - 6, 96, x + 6, 96, (60, 30, 36))
        else:  # rolo de corda
            pier.ellipse(x, 95, 7, 3, (70, 40, 36))
    for _ in range(3):
        x = rnd.uniform(0, n.w)
        pier.rect(x, 92, x + 2, 140, blk)
        pier.rect(x - 3, 140, x + 5, 142, blk)
        pier.ellipse(x + 1, 146, 9, 9, (255, 180, 90), a=60)
        pier.rect(x - 2, 142, x + 4, 150, (255, 206, 120))
        pier.rect(x - 3, 150, x + 5, 152, blk)
    bricks(pier, rnd, 10, 3, 0.8, 0.05)
    edge_light(pier, (0, 1), lit=(210, 96, 72))
    n.paste(pier)
    return S.arr()[..., :3], L


def rock_pillar(layer, rnd, x, base, w, h, col, top_round=True):
    """coluna de rocha irregular (bordas tortas, topo arredondado)"""
    pts = []
    n = 10
    for i in range(n + 1):
        t = i / n
        pts.append((x - w / 2 + rnd.uniform(-3, 3) + math.sin(t * 3 + x) * 3, base + h * t))
    if top_round:
        for i in range(1, 8):
            a = math.pi - i / 8 * math.pi
            pts.append((x + math.cos(a) * w / 2, base + h + math.sin(a) * w * 0.3 + rnd.uniform(-1.5, 1.5)))
    for i in range(n, -1, -1):
        t = i / n
        pts.append((x + w / 2 + rnd.uniform(-3, 3) + math.sin(t * 2.5 + x * 0.7) * 3, base + h * t))
    layer.poly(pts, col)


def stalactites(layer, rnd, top, col, count, lmin=10, lmax=60):
    for _ in range(count):
        x = rnd.uniform(0, layer.w)
        w = rnd.uniform(5, 16)
        layer.poly([(x - w / 2, top), (x + w / 2, top), (x + rnd.uniform(-2, 2), top - rnd.uniform(lmin, lmax))], col)


def interior(rnd, wall, wall_d, accent, window_sky=None, kind="stone", lamp=(255, 196, 110), style="hall"):
    """salao interno: parede de fundo (no lugar do ceu), elementos do lugar no meio e objetos na frente"""
    W, H = SKY
    S = sky_layer([(0, shade(wall, 0.62)), (0.55, wall), (1, shade(wall, 0.8))])
    if kind in ("stone", "brick"):
        bricks(S, rnd, 10 if kind == "stone" else 7, 5 if kind == "stone" else 3, 0.84, 0.07)
    elif kind == "wood":
        a = S.arr()
        for x in range(0, W, 11):
            a[:, x, :3] *= 0.72
            a[:, x + 1, :3] *= 0.88
        S.set_arr(a)
        speckle(S, rnd, 0.08, 0.07)
    elif kind == "metal":
        bricks(S, rnd, 30, 20, 0.72, 0.05)
        for x in range(4, W, 30):
            for y in range(4, H, 20):
                S.px(x, y, shade(wall, 1.35))
    elif kind == "rock":
        ridge(S, rnd, H * 0.1, H * 0.5, shade(wall, 0.85), rough=0.6, freq=3)
        speckle(S, rnd, 0.1, 0.12)
    if window_sky is not None:  # janelas em arco mostrando o ceu la fora
        for k in range(4):
            cx = W * (0.12 + k * 0.25)
            sky_in = Layer(W, H)
            sky_in.set_arr(np.dstack([gradient(W, H, window_sky), np.zeros((H, W), np.float32)]))
            for _ in range(2):
                cloud2(sky_in, rnd, cx + rnd.uniform(-14, 14), H * rnd.uniform(0.62, 0.78), 26, 6, (255, 252, 240), (234, 228, 220), (196, 200, 210))
            mask = Layer(W, H)
            gothic_arch(mask, cx, H * 0.38, 40, 62, (255, 255, 255))
            sa = sky_in.arr()
            sa[..., 3] = mask.arr()[..., 3]
            sky_in.set_arr(sa)
            gothic_arch(S, cx, H * 0.38 - 3, 48, 66, wall_d)
            S.paste(sky_in)
            S.rect(cx - 1, H * 0.38, cx + 1, H * 0.38 + 86, wall_d)
            S.rect(cx - 20, H * 0.38 + 34, cx + 20, H * 0.38 + 35, wall_d)
            S.rect(cx - 26, H * 0.38 - 5, cx + 26, H * 0.38 - 2, shade(wall_d, 0.8))
    S.rect(0, 0, W, H * 0.14, shade(wall, 0.55))
    L = new_layers()
    f, m, n = L["far"], L["mid"], L["near"]
    dark = shade(wall_d, 0.8)
    st = sub(m)
    if style == "hall":  # salao de pedra: abobadas no alto e poucos pilares grossos
        fa = sub(f)
        for x in range(0, f.w, 316):
            fa.rect(x, 0, x + 22, f.h, wall_d)
            arch(fa, x + 11 + 158, 330, 316, 30, 14, wall_d)
        bricks(fa, rnd, 7, 3, 0.85, 0.06)
        volume(fa, -1, 6)
        fog(fa, shade(wall, 0.85), 0.3)
        f.paste(fa)
        for x in range(40, m.w, 250):
            st.rect(x, 0, x + 22, m.h, dark)
            st.rect(x - 4, 60, x + 26, 68, wall_d)
            st.rect(x - 4, 300, x + 26, 308, wall_d)
            st.rect(x + 6, 150, x + 16, 190, accent)  # estandarte no pilar
            st.poly([(x + 6, 150), (x + 16, 150), (x + 11, 143)], accent)
        for x in range(160, m.w, 250):  # lampadario pendurado
            st.line([(x, m.h), (x, 250)], dark)
            st.rect(x - 12, 246, x + 12, 250, dark)
            for dx in (-10, 0, 10):
                st.ellipse(x + dx, 254, 5, 5, lamp, a=80)
                st.rect(x + dx - 1, 250, x + dx + 1, 255, lamp)
    elif style == "ship":  # porao: cavernas do casco curvas, lanternas, redes
        fa = sub(f)
        for x in range(0, f.w, 60):
            pts = [(x + math.sin(t / 10 * math.pi) * 14, t / 10 * f.h) for t in range(11)]
            fa.line(pts, wall_d, 8)
        fa.rect(0, f.h - 30, f.w, f.h - 20, wall_d)
        volume(fa, -1, 3)
        fog(fa, shade(wall, 0.85), 0.25)
        f.paste(fa)
        for x in range(30, m.w, 140):
            st.line([(x, m.h), (x, 220)], dark)
            st.rect(x - 5, 206, x + 5, 220, dark)
            st.ellipse(x, 213, 12, 12, lamp, a=70)
            st.rect(x - 3, 208, x + 3, 218, lamp)
        for x in range(80, m.w, 200):  # rede pendurada
            for k in range(6):
                st.line([(x + k * 6, 300), (x + k * 6 + 10, 260)], shade(wall, 1.2))
                st.line([(x + k * 6 + 10, 300), (x + k * 6, 260)], shade(wall, 1.2))
    elif style == "cellar":  # adega: arcos baixos e prateleiras de barris
        fa = sub(f)
        for x in range(0, f.w, 160):
            arch(fa, x + 80, 150, 160, 70, 16, wall_d)
            fa.rect(x, 0, x + 16, 220, wall_d)
        bricks(fa, rnd, 7, 3, 0.85, 0.06)
        volume(fa, -1, 5)
        fog(fa, shade(wall, 0.85), 0.25)
        f.paste(fa)
        for x in range(10, m.w, 120):
            st.rect(x, 70, x + 90, 74, dark)
            st.rect(x, 74, x + 3, 160, dark)
            st.rect(x + 87, 74, x + 90, 160, dark)
            for row in range(3):
                for k in range(4):
                    bx, by = x + 12 + k * 22, 86 + row * 22
                    st.ellipse(bx, by, 10, 10, (110, 70, 40))
                    st.ellipse(bx, by, 7, 7, (130, 86, 50))
                    st.ellipse(bx, by, 2, 2, (60, 36, 20))
    elif style == "sewer":  # esgoto: tunel de tijolo, canos e canal de agua verde
        fa = sub(f)
        for x in range(0, f.w, 240):
            arch(fa, x + 120, 120, 240, 120, 22, wall_d)
            fa.rect(x, 0, x + 22, 240, wall_d)
        bricks(fa, rnd, 6, 3, 0.82, 0.07)
        volume(fa, -1, 5)
        fog(fa, shade(wall, 0.85), 0.25)
        f.paste(fa)
        st.rect(0, 0, m.w, 46, (40, 90, 60))
        for k in range(0, m.w, 7):
            if rnd.random() < 0.5:
                st.rect(k, 40, k + 4, 41, (120, 220, 140))
        for y in (200, 236):
            st.rect(0, y, m.w, y + 8, (70, 80, 74))
            for x in range(0, m.w, 90):
                st.rect(x, y - 2, x + 6, y + 10, (54, 62, 58))
        for x in range(60, m.w, 180):  # cano jorrando agua
            st.rect(x, 120, x + 14, 132, (70, 80, 74))
            st.rect(x + 3, 46, x + 11, 122, (120, 200, 150), a=160)
    elif style == "magma":  # caverna de lava: rochas com rachaduras brilhando
        fa = sub(f)
        for _ in range(10):
            rock_pillar(fa, rnd, rnd.uniform(0, f.w), 0, rnd.uniform(40, 90), rnd.uniform(200, 380), wall_d)
        stalactites(fa, rnd, f.h, wall_d, 30, 20, 90)
        volume(fa, -1, 5)
        fog(fa, shade(wall, 0.9), 0.3)
        f.paste(fa)
        for _ in range(8):
            rock_pillar(st, rnd, rnd.uniform(0, m.w), 0, rnd.uniform(30, 60), rnd.uniform(100, 200), dark)
        stalactites(st, rnd, m.h, dark, 18, 20, 70)
        for _ in range(30):  # rachaduras de lava
            x, y = rnd.uniform(0, m.w), rnd.uniform(20, 180)
            pts = [(x, y)]
            for k in range(5):
                x += rnd.uniform(-6, 6)
                y += rnd.uniform(-8, 2)
                pts.append((x, y))
            st.line(pts, (255, 120, 50))
    elif style == "reactor":  # reator: tubos brilhando, canos e paineis
        fa = sub(f)
        for x in range(0, f.w, 120):
            fa.rect(x, 0, x + 60, f.h, wall_d)
            for y in range(20, f.h, 40):
                fa.rect(x + 6, y, x + 54, y + 2, shade(wall_d, 1.3))
        volume(fa, -1, 4)
        fog(fa, shade(wall, 0.85), 0.3)
        f.paste(fa)
        for x in range(30, m.w, 150):  # tubo de energia
            st.rect(x - 14, 60, x + 14, 66, dark)
            st.rect(x - 14, 280, x + 14, 286, dark)
            st.ellipse(x, 170, 24, 120, lamp, a=40)
            st.rect(x - 9, 66, x + 9, 280, shade(lamp, 0.7))
            st.rect(x - 4, 66, x + 4, 280, lamp)
            st.rect(x - 1, 66, x + 1, 280, (240, 255, 255))
        for y in (220, 250):
            st.rect(0, y, m.w, y + 6, dark)
    elif style == "classroom":  # escola: lousa, estantes de livros e mesas
        for x in range(20, m.w, 260):
            st.rect(x, 120, x + 110, 190, (110, 80, 50))
            st.rect(x + 4, 124, x + 106, 186, (40, 76, 60))
            for k in range(4):
                st.line([(x + 14, 170 - k * 12), (x + rnd.randint(40, 90), 170 - k * 12)], (220, 230, 220))
            sx = x + 140
            st.rect(sx, 70, sx + 70, 200, (120, 86, 56))
            for row in range(4):
                y = 80 + row * 30
                st.rect(sx + 3, y + 24, sx + 67, y + 26, (90, 62, 40))
                k = sx + 5
                while k < sx + 64:
                    bw = rnd.randint(3, 6)
                    st.rect(k, y, k + bw - 1, y + rnd.randint(16, 23), rnd.choice([(190, 60, 50), (60, 110, 170), (220, 180, 60), (80, 140, 80), (150, 90, 160)]))
                    k += bw + 1
    elif style == "gym":  # ginasio: arquibancada e cesta
        for x in range(0, m.w, 300):
            for row in range(5):
                st.rect(x, 70 + row * 16, x + 220 - row * 20, 78 + row * 16, (120, 84, 56))
            st.rect(x + 250, 70, x + 254, 250, (200, 200, 210))
            st.rect(x + 236, 230, x + 276, 262, (240, 240, 240))
            st.rect(x + 248, 236, x + 264, 248, (220, 80, 40))
            st.ellipse(x + 256, 226, 9, 3, (230, 90, 40))
    volume(st, -1, 3)
    edge_light(st, (-1, 1), lit=shade(wall, 1.35))
    m.paste(st)
    # objetos na frente (caixas, barris, mesas)
    pr = sub(n)
    pr.rect(0, 0, n.w, 66, shade(wall_d, 0.5))
    for _ in range(12):
        x = rnd.uniform(0, n.w)
        r = rnd.random()
        c = shade(wall_d, 0.6)
        if r < 0.4:
            pr.rect(x, 66, x + 16, 82, c)
            pr.line([(x, 66), (x + 16, 82)], shade(c, 0.75))
            pr.line([(x, 82), (x + 16, 66)], shade(c, 0.75))
        elif r < 0.75:
            pr.ellipse(x, 75, 7, 9, c)
            pr.rect(x - 7, 72, x + 7, 72, shade(c, 0.7))
            pr.rect(x - 7, 79, x + 7, 79, shade(c, 0.7))
        else:
            pr.rect(x, 66, x + 24, 69, c)
            pr.rect(x + 2, 69, x + 4, 82, c)
            pr.rect(x + 20, 69, x + 22, 82, c)
    volume(pr, -1, 3)
    edge_light(pr, (-1, 1), lit=shade(wall, 1.1))
    n.paste(pr)
    return S.arr()[..., :3], L


@theme("market")
def market(rnd):
    """feira medieval: barracas coloridas, casas de enxaimel, torres e bandeirinhas"""
    W, H = SKY
    S = sky_layer([(0, 0x4fa6e0), (0.5, 0x9cd4f2), (0.85, 0xe8f3f0), (1, 0xf6f1df)])
    for _ in range(11):
        cloud2(S, rnd, rnd.uniform(0, W), rnd.uniform(H * 0.4, H * 0.92), rnd.uniform(40, 120), rnd.uniform(10, 22),
               (255, 255, 255), (232, 240, 246), (176, 200, 220))
    birds(S, rnd, 8, H * 0.55, H * 0.9, (60, 60, 70))
    L = new_layers()
    f = L["far"]
    c1 = sub(f)
    ridge(c1, rnd, 120, 30, (150, 176, 190), rough=0.4, freq=2)
    for x in np.linspace(0, f.w, 7)[:-1] + rnd.uniform(0, 60):  # castelo ao longe
        tower(c1, rnd, x, 140, rnd.randint(16, 26), rnd.randint(70, 140), (150, 168, 186), (120, 150, 190), rnd.choice(["cone", "battlement", "spire"]))
        c1.rect(x, 140, x + rnd.randint(30, 60), 140 + rnd.randint(30, 50), (150, 168, 186))
    for _ in range(30):  # pinheiros
        x = rnd.uniform(0, f.w)
        hgt = rnd.uniform(16, 30)
        c1.poly([(x - 6, 128), (x + 6, 128), (x, 128 + hgt)], (120, 150, 150))
    bricks(c1, rnd, 4, 2, 0.92, 0.04)
    volume(c1, -1, 3, lit=1.1, dark=0.86)
    fog(c1, (200, 222, 232), 0.35, bottom=((220, 232, 230), 0.5), bottom_h=0.6)
    f.paste(c1)
    m = L["mid"]
    hs = sub(m)
    hs.rect(0, 0, m.w, 110, (150, 120, 90))
    x = 0
    walls = [(232, 214, 180), (220, 196, 160), (236, 224, 196), (206, 186, 150)]
    roofs = [(176, 82, 56), (160, 70, 50), (96, 110, 140), (190, 110, 60)]
    while x < m.w:
        if rnd.random() < 0.22:
            tw = rnd.randint(22, 30)
            tower(hs, rnd, x + tw / 2, 110, tw, rnd.randint(110, 160), (184, 176, 166), rnd.choice(roofs), "cone", win=(70, 50, 40), trim=(140, 132, 124))
            x += tw + rnd.randint(4, 12)
        else:
            bw = rnd.randint(34, 52)
            house(hs, rnd, x, 110, bw, rnd.randint(50, 90), rnd.choice(walls), (96, 62, 40), rnd.choice(roofs), (255, 212, 120))
            x += bw + rnd.randint(4, 10)
    bricks(hs, rnd, 5, 3, 0.9, 0.05)
    volume(hs, -1, 4)
    edge_light(hs, (-1, 1), lit=(255, 246, 220), dark=None)
    m.paste(hs)
    st = sub(m)
    aw = [((214, 70, 60), (240, 230, 210)), ((60, 120, 190), (240, 230, 210)), ((236, 170, 50), (150, 70, 40)), ((90, 150, 80), (240, 230, 210))]
    x = rnd.uniform(0, 20)
    while x < m.w:
        a1, a2 = rnd.choice(aw)
        stall(st, rnd, x, 110, rnd.randint(26, 38), rnd.randint(24, 32), (110, 72, 44), a1, a2,
              [(220, 80, 60), (240, 180, 60), (120, 170, 70), (200, 140, 90)])
        x += rnd.randint(50, 90)
    for _ in range(5):
        x0 = rnd.uniform(0, m.w)
        bunting(st, rnd, x0, rnd.uniform(170, 200), x0 + rnd.uniform(60, 110), rnd.uniform(170, 200),
                [(214, 70, 60), (236, 190, 60), (70, 130, 200), (90, 160, 90)], (90, 60, 40))
    volume(st, -1, 2)
    m.paste(st)
    fog(m, (0, 0, 0), 0.0, bottom=((140, 112, 84), 0.6), bottom_h=0.4)
    n = L["near"]
    pr = sub(n)
    pr.rect(0, 0, n.w, 84, (110, 84, 62))
    for _ in range(16):
        x = rnd.uniform(0, n.w)
        r = rnd.random()
        if r < 0.35:
            pr.ellipse(x, 93, 7, 9, (120, 76, 44))
            pr.rect(x - 7, 89, x + 7, 90, (80, 50, 30))
            pr.rect(x - 7, 97, x + 7, 98, (80, 50, 30))
        elif r < 0.65:
            pr.rect(x, 84, x + 15, 98, (150, 104, 62))
            pr.line([(x, 84), (x + 15, 98)], (100, 66, 38))
            pr.rect(x + 2, 98, x + 13, 101, rnd.choice([(220, 80, 60), (240, 180, 60), (120, 170, 70)]))
        else:  # carrinho de mao / sacos
            pr.ellipse(x, 90, 6, 6, (210, 190, 150))
            pr.ellipse(x + 8, 89, 5, 5, (196, 176, 136))
    bricks(pr, rnd, 7, 3, 0.85, 0.06)
    volume(pr, -1, 3)
    edge_light(pr, (-1, 1), lit=(230, 200, 150))
    n.paste(pr)
    return S.arr()[..., :3], L


@theme("jungle")
def jungle(rnd):
    """selva verde-azulada: folhas enormes, cipos, nevoa e uma base de pesquisa no alto"""
    W, H = SKY
    S = sky_layer([(0, 0x7fd6c8), (0.5, 0xb6ecd8), (1, 0xe6f8e4)])
    for _ in range(5):
        cloud2(S, rnd, rnd.uniform(0, W), rnd.uniform(H * 0.6, H * 0.95), rnd.uniform(60, 120), rnd.uniform(10, 18),
               (246, 255, 246), (214, 242, 230), (180, 222, 210), a=200)
    L = new_layers()
    f = L["far"]
    for col, base, fa in (((120, 190, 170), 200, 0.5), ((80, 160, 140), 150, 0.3)):
        t = sub(f)
        for _ in range(16):  # arvores gigantes
            x = rnd.uniform(0, f.w)
            hgt = rnd.uniform(140, 260)
            t.rect(x - 4, 0, x + 4, base + hgt * 0.5, col)
            for _ in range(6):
                t.ellipse(x + rnd.uniform(-34, 34), base + hgt * rnd.uniform(0.4, 0.75), rnd.uniform(18, 34), rnd.uniform(10, 18), col)
        ridge(t, rnd, base - 40, 30, col, rough=0.5, freq=3)
        volume(t, -1, 3, lit=1.12, dark=0.86)
        fog(t, (190, 236, 220), fa, bottom=((200, 240, 226), 0.6), bottom_h=0.5)
        f.paste(t)
    m = L["mid"]
    tr = sub(m)
    dg = (34, 104, 92)
    tr.rect(0, 0, m.w, 90, dg)
    for _ in range(6):  # troncos com raizes
        x = rnd.uniform(0, m.w)
        tr.rect(x - 7, 80, x + 7, 340, (44, 86, 78))
        tr.poly([(x - 18, 80), (x - 7, 110), (x - 7, 80)], (44, 86, 78))
        tr.poly([(x + 18, 80), (x + 7, 110), (x + 7, 80)], (44, 86, 78))
    for _ in range(40):  # folhas grandes
        x = rnd.uniform(0, m.w)
        y = rnd.uniform(90, 320)
        ang = rnd.uniform(-0.6, 0.6) + (math.pi if rnd.random() < 0.5 else 0)
        leaf(tr, x, y, rnd.uniform(24, 44), rnd.uniform(7, 12), ang, rnd.choice([(46, 132, 106), (58, 150, 118), (40, 118, 100)]), (30, 90, 76))
    # base de pesquisa: plataforma com escada, janelas acesas
    for bx in (m.w * 0.2, m.w * 0.68):
        tr.rect(bx, 210, bx + 90, 214, (30, 60, 66))
        tr.rect(bx + 4, 90, bx + 7, 210, (30, 60, 66))
        tr.rect(bx + 80, 90, bx + 83, 210, (30, 60, 66))
        for yy in range(96, 208, 8):
            tr.rect(bx + 40, yy, bx + 50, yy + 1, (30, 60, 66))
        tr.rect(bx + 40, 90, bx + 41, 210, (30, 60, 66))
        tr.rect(bx + 49, 90, bx + 50, 210, (30, 60, 66))
        tr.rect(bx + 10, 214, bx + 46, 240, (30, 60, 66))
        tr.poly([(bx + 6, 240), (bx + 50, 240), (bx + 28, 252)], (30, 60, 66))
        tr.rect(bx + 16, 222, bx + 24, 230, (255, 230, 120))
        tr.rect(bx + 30, 222, bx + 38, 230, (230, 240, 250))
        tr.line([(bx + 90, 214), (bx + 140, 160)], (30, 60, 66), 2)
    for _ in range(25):  # cipos
        x = rnd.uniform(0, m.w)
        y0 = rnd.uniform(260, 360)
        pts = [(x + math.sin(k * 0.4) * 3, y0 - k * 6) for k in range(rnd.randint(6, 18))]
        tr.line(pts, (60, 120, 70))
    volume(tr, -1, 4, lit=1.18, dark=0.8)
    edge_light(tr, (-1, 1), lit=(150, 220, 180))
    fog(tr, (150, 214, 196), 0.12, bottom=((60, 130, 110), 0.5), bottom_h=0.5)
    m.paste(tr)
    n = L["near"]
    fr = sub(n)
    fr.rect(0, 0, n.w, 70, (20, 62, 56))
    for _ in range(45):
        x = rnd.uniform(0, n.w)
        y = rnd.uniform(60, 150)
        ang = math.pi / 2 + rnd.uniform(-1.1, 1.1)
        leaf(fr, x, y - 20, rnd.uniform(26, 50), rnd.uniform(8, 14), ang, rnd.choice([(22, 76, 64), (28, 90, 72), (18, 66, 58)]), (14, 50, 44))
    volume(fr, -1, 3, lit=1.25, dark=0.8)
    edge_light(fr, (-1, 1), lit=(90, 170, 130))
    n.paste(fr)
    return S.arr()[..., :3], L


@theme("volcano")
def volcano(rnd):
    """ceu vermelho-magenta, vulcoes soltando fumaca e arvores retorcidas com orbes brilhando"""
    W, H = SKY
    S = sky_layer([(0, 0x2e1230), (0.45, 0x6e2446), (0.8, 0xbe4446), (1, 0xe0663e)])
    for _ in range(50):
        S.px(rnd.uniform(0, W), rnd.uniform(H * 0.2, H * 0.95), (255, 150, 140), rnd.choice([120, 200]))
    for _ in range(6):
        cloud_band(S, rnd, rnd.uniform(H * 0.45, H * 0.9), rnd.uniform(3, 6), rnd.choice([(84, 28, 52), (112, 38, 58)]), a=210)
    L = new_layers()
    f = L["far"]
    v = sub(f)
    for k in range(3):
        cx = f.w * (0.15 + k * 0.33) + rnd.uniform(-40, 40)
        hgt = rnd.uniform(150, 220)
        pts = [(cx - 190, 100)]
        for i in range(1, 20):  # encosta concava
            t = i / 20
            pts.append((cx - 190 + 170 * t, 100 + hgt * (t ** 2.2)))
        pts += [(cx - 20, 100 + hgt), (cx + 20, 100 + hgt)]
        for i in range(1, 20):
            t = 1 - i / 20
            pts.append((cx + 190 - 170 * t, 100 + hgt * (t ** 2.2)))
        pts.append((cx + 190, 100))
        v.poly(pts, (96, 34, 58))
        v.ellipse(cx, 100 + hgt + 3, 22, 6, (255, 140, 70))
        for j in range(4):  # lava escorrendo
            x0 = cx + rnd.uniform(-14, 14)
            pts = [(x0, 100 + hgt)]
            y = 100 + hgt
            x = x0
            while y > 110:
                y -= 8
                x += rnd.uniform(-4, 4) + (x - cx) * 0.06
                pts.append((x, y))
            v.line(pts, (240, 90, 50), 2)
        for i in range(7):  # fumaca
            v.ellipse(cx + i * 9 + rnd.uniform(-4, 4), 100 + hgt + 14 + i * 14, 10 + i * 4, 7 + i * 2, (120, 60, 80), a=200 - i * 20)
    ridge(v, rnd, 80, 40, (84, 28, 50), rough=0.5, freq=3)
    volume(v, -1, 4)
    fog(v, (170, 64, 76), 0.3, bottom=((190, 76, 80), 0.6), bottom_h=0.5)
    f.paste(v)
    m = L["mid"]
    tr = sub(m)
    ys = ridge(tr, rnd, 80, 24, (44, 16, 30), rough=0.5, freq=3)
    for _ in range(6):
        x = rnd.uniform(0, m.w)
        orb_tree(tr, rnd, x, ys[int(x) % m.w] - 4, rnd.uniform(70, 110), (36, 12, 26), (232, 56, 84), (255, 80, 100), (24, 8, 18))
    edge_light(tr, (0, 1), lit=(170, 50, 80))
    m.paste(tr)
    for _ in range(60):
        m.px(rnd.uniform(0, m.w), rnd.uniform(80, 340), (255, 120, 130), 200)
    n = L["near"]
    rk = sub(n)
    ys2 = ridge(rk, rnd, 70, 30, (30, 10, 20), rough=0.6, freq=4, peaks=True)
    for k in range(0, n.w, 3):
        if rnd.random() < 0.3:
            rk.rect(k, 0, k + 2, rnd.uniform(4, 14), (255, 110, 50))
    edge_light(rk, (0, 1), lit=(200, 60, 70))
    n.paste(rk)
    return S.arr()[..., :3], L


@theme("ice")
def cave(rnd):
    """caverna azul: rochas, cogumelos roxos brilhantes, musgo e cachoeiras"""
    W, H = SKY
    S = sky_layer([(0, 0x141f48), (0.5, 0x23397a), (1, 0x3d62a8)])
    ridge(S, rnd, H * 0.05, H * 0.35, (44, 66, 130), rough=0.6, freq=2)
    stalactites(S, rnd, H, (20, 30, 70), 40, 10, 60)
    for _ in range(90):
        S.px(rnd.uniform(0, W), rnd.uniform(0, H), (255, 236, 160), rnd.choice([90, 160, 230]))
    L = new_layers()
    f = L["far"]
    for col, fa, cnt in (((58, 80, 150), 0.28, 9), ((42, 60, 126), 0.1, 8)):
        t = sub(f)
        for _ in range(cnt):
            x = rnd.uniform(0, f.w)
            w = rnd.uniform(40, 90)
            h = rnd.uniform(160, 330)
            rock_pillar(t, rnd, x, 0, w, h, col)
            t.ellipse(x, h + 2, w / 2 + 6, 7, (160, 110, 220))
            if rnd.random() < 0.55:
                waterfall(t, rnd, x - 3, h, 0, 6, (160, 205, 250), (232, 246, 255))
        stalactites(t, rnd, f.h, col, 25, 20, 80)
        speckle(t, rnd, 0.1, 0.12)
        volume(t, -1, 5)
        moss(t, rnd, (170, 110, 230), chance=0.6, drip=4)
        fog(t, (100, 140, 210), fa, bottom=((110, 150, 220), 0.6), bottom_h=0.45)
        f.paste(t)
    m = L["mid"]
    rk = sub(m)
    rk.rect(0, 0, m.w, 60, (38, 50, 108))
    for _ in range(7):
        x = rnd.uniform(0, m.w)
        w = rnd.uniform(50, 100)
        h = rnd.uniform(90, 190)
        rock_pillar(rk, rnd, x, 40, w, h, (46, 60, 124))
        rk.ellipse(x, 40 + h + 2, w / 2 + 8, 8, (150, 90, 210))
        if rnd.random() < 0.6:
            waterfall(rk, rnd, x + rnd.uniform(-10, 6), 40 + h, 50, rnd.randint(6, 10), (150, 200, 250), (232, 246, 255))
    speckle(rk, rnd, 0.12, 0.14)
    volume(rk, -1, 6)
    moss(rk, rnd, (176, 110, 230), chance=0.75, drip=5)
    edge_light(rk, (-1, 1), lit=(130, 160, 240))
    m.paste(rk)
    ms = sub(m)
    for _ in range(24):
        x = rnd.uniform(0, m.w)
        mushroom(ms, rnd, x, 60, rnd.uniform(3, 7), (220, 210, 240), (160, 90, 210), (255, 255, 200), (230, 180, 255))
    m.paste(ms)
    for _ in range(60):
        m.px(rnd.uniform(0, m.w), rnd.uniform(60, 330), (255, 240, 160), 230)
    n = L["near"]
    fr = sub(n)
    ys = ridge(fr, rnd, 60, 22, (24, 30, 72), rough=0.6, freq=4)
    for _ in range(16):
        x = rnd.uniform(0, n.w)
        mushroom(fr, rnd, x, ys[int(x) % n.w] - 3, rnd.uniform(5, 11), (200, 190, 230), (130, 70, 190), (255, 240, 200), (220, 160, 255))
    edge_light(fr, (-1, 1), lit=(110, 120, 210))
    n.paste(fr)
    return S.arr()[..., :3], L


@theme("sky")
def islands(rnd):
    """ruinas azuis sob ceu claro (ilhas no ceu)"""
    W, H = SKY
    S = sky_layer([(0, 0x2f7fd0), (0.6, 0x84c4f0), (1, 0xd8eefa)])
    for _ in range(12):
        cloud2(S, rnd, rnd.uniform(0, W), rnd.uniform(H * 0.25, H * 0.9), rnd.uniform(50, 130), rnd.uniform(12, 24),
               (255, 255, 255), (226, 238, 250), (170, 196, 230))
    birds(S, rnd, 10, H * 0.5, H * 0.9, (40, 50, 80))
    L = new_layers()
    f = L["far"]
    t = sub(f)
    ruin_skyline(t, rnd, 150, 50, 160, (120, 160, 210))
    volume(t, -1, 3, lit=1.1, dark=0.85)
    fog(t, (190, 220, 245), 0.45)
    f.paste(t)
    for _ in range(5):  # ilhas flutuantes
        isl = sub(f)
        x = rnd.uniform(0, f.w)
        y = rnd.uniform(250, 380)
        w = rnd.uniform(40, 90)
        isl.poly([(x - w / 2, y), (x + w / 2, y), (x + w * 0.15, y - w * 0.6), (x - w * 0.1, y - w * 0.7)], (120, 140, 170))
        isl.ellipse(x, y + 2, w / 2 + 2, 5, (120, 190, 110))
        bush(isl, rnd, x, y + 2, w * 0.15, (110, 180, 110), (160, 220, 140), (80, 140, 90))
        volume(isl, -1, 3)
        fog(isl, (200, 226, 248), 0.3)
        f.paste(isl)
    m = L["mid"]
    st = sub(m)
    ys = ridge(st, rnd, 90, 30, (64, 96, 140), rough=0.5, freq=3)
    x = 0
    while x < m.w:
        gy = ys[int(x) % m.w] - 4
        r = rnd.random()
        if r < 0.4:
            for k in range(rnd.randint(2, 4)):
                column(st, rnd, x + k * 16, gy, 9, rnd.randint(60, 120), (76, 108, 160), (54, 80, 124), broken=rnd.random() < 0.5)
            x += 70
        elif r < 0.7:
            arch(st, x + 30, gy, 60, rnd.randint(60, 90), 9, (76, 108, 160), broken_side=rnd.choice([0, 1]))
            x += 80
        else:
            st.poly([(x, gy), (x + 6, gy + rnd.uniform(40, 80)), (x + 20, gy + rnd.uniform(50, 100)), (x + 30, gy)], (70, 100, 150))
            x += 50
    bricks(st, rnd, 6, 3, 0.86, 0.06)
    volume(st, -1, 5)
    moss(st, rnd, (110, 170, 120), chance=0.4)
    edge_light(st, (-1, 1), lit=(200, 226, 255))
    fog(st, (180, 210, 240), 0.1, bottom=((90, 120, 160), 0.5), bottom_h=0.5)
    m.paste(st)
    n = L["near"]
    gr = sub(n)
    ys2 = ridge(gr, rnd, 70, 30, (34, 60, 70), rough=0.55, freq=4)
    for _ in range(14):
        x = rnd.uniform(0, n.w)
        bush(gr, rnd, x, ys2[int(x) % n.w] - 3, rnd.uniform(6, 12), (40, 96, 70), (70, 130, 90), (26, 66, 50))
    edge_light(gr, (-1, 1), lit=(110, 170, 150))
    n.paste(gr)
    grass(n, rnd, lambda xx: ys2[xx % n.w] - 1, (60, 120, 80), density=0.6, hmax=7)
    return S.arr()[..., :3], L


@theme("factory")
def factory(rnd):
    """cidade industrial gotica ao entardecer: chamines, torre do relogio e galpoes com arcos"""
    W, H = SKY
    S = sky_layer([(0, 0x2b2a44), (0.4, 0x7a4a52), (0.75, 0xe08a4a), (1, 0xf6c070)])
    sun(S, W * 0.3, H * 0.32, 22, (255, 236, 180), (255, 170, 90), rings=4)
    for _ in range(8):
        cloud_band(S, rnd, rnd.uniform(H * 0.4, H * 0.95), rnd.uniform(3, 6), rnd.choice([(110, 60, 70), (150, 80, 70)]), a=220)
    birds(S, rnd, 8, H * 0.5, H * 0.9, (40, 24, 30))
    L = new_layers()
    f = L["far"]
    t = sub(f)
    x = 0
    while x < f.w:
        bw = rnd.randint(16, 40)
        bh = rnd.randint(40, 140)
        t.rect(x, 0, x + bw, 130 + bh, (96, 70, 86))
        if rnd.random() < 0.4:  # chamine soltando fumaca
            cx = x + bw * 0.5
            t.rect(cx - 3, 130 + bh, cx + 3, 130 + bh + 50, (96, 70, 86))
            for k in range(6):
                t.ellipse(cx + k * 6, 130 + bh + 54 + k * 7, 6 + k * 2, 5 + k, (150, 110, 110), a=160 - k * 20)
        windows_grid(t, rnd, x, 130, x + bw, 130 + bh, (255, 190, 100), None, chance=0.12, ww=1, wh=2, gx=4, gy=6)
        x += bw + rnd.randint(-2, 10)
    volume(t, -1, 3)
    fog(t, (200, 120, 100), 0.35, bottom=((220, 140, 100), 0.5), bottom_h=0.5)
    f.paste(t)
    m = L["mid"]
    hall = sub(m)
    stone = (70, 52, 58)
    hall.rect(0, 0, m.w, 200, stone)
    glow = sub(m)
    for k in range(0, m.w, 46):  # janelas em arco enormes com o por do sol dentro
        gothic_arch(glow, k + 23, 100, 34, 56, (230, 120, 60))
        gothic_arch(glow, k + 23, 104, 28, 50, (250, 170, 80))
        gothic_arch(glow, k + 23, 116, 18, 30, (255, 214, 140))
    hall.paste(glow)
    for k in range(0, m.w, 46):
        hall.rect(k + 22, 100, k + 24, 176, stone)
        for yy in (120, 140, 160):
            hall.rect(k + 7, yy, k + 39, yy, stone)
        hall.rect(k, 90, k + 4, 210, shade(stone, 0.8))
    hall.rect(0, 200, m.w, 206, shade(stone, 0.85))
    for cx in (m.w * 0.5,):  # torre do relogio
        hall.rect(cx - 22, 206, cx + 22, 320, stone)
        hall.poly([(cx - 26, 320), (cx + 26, 320), (cx, 350)], stone)
        hall.ellipse(cx, 280, 15, 15, (40, 24, 24))
        hall.ellipse(cx, 280, 13, 13, (250, 196, 110))
        hall.line([(cx, 280), (cx, 289)], (60, 40, 30), 2)
        hall.line([(cx, 280), (cx - 6, 277)], (60, 40, 30), 2)
    for _ in range(4):  # engrenagens
        cx, cy, r = rnd.uniform(0, m.w), rnd.uniform(220, 260), rnd.uniform(10, 16)
        hall.ellipse(cx, cy, r, r, shade(stone, 0.8))
        for i in range(8):
            a = i / 8 * math.tau
            hall.rect(cx + math.cos(a) * r - 2, cy + math.sin(a) * r - 2, cx + math.cos(a) * r + 2, cy + math.sin(a) * r + 2, shade(stone, 0.8))
        hall.ellipse(cx, cy, r * 0.35, r * 0.35, (230, 150, 70))
    bricks(hall, rnd, 6, 3, 0.82, 0.07)
    volume(hall, -1, 4)
    edge_light(hall, (-1, 1), lit=(240, 150, 100))
    m.paste(hall)
    n = L["near"]
    pp = sub(n)
    dk = (36, 26, 32)
    pp.rect(0, 0, n.w, 80, dk)
    for k in range(0, n.w, 70):  # canos e tanques
        pp.rect(k, 80, k + 6, 150, dk)
        pp.rect(k, 150, k + 40, 156, dk)
        pp.ellipse(k + 30, 100, 14, 20, dk)
        pp.rect(k + 44, 80, k + 60, 110, dk)
    edge_light(pp, (-1, 1), lit=(200, 110, 80))
    n.paste(pp)
    return S.arr()[..., :3], L


@theme("space")
def space(rnd):
    """lua: ceu estrelado, planeta azul e montanhas de crateras"""
    W, H = SKY
    S = sky_layer([(0, 0x05060f), (0.7, 0x141a3a), (1, 0x2a3060)])
    # nebulosa: faixas suaves de cor com pontilhado
    neb = _fbm(rnd, W, H, 4, 3)
    A = S.arr()
    by = BAYER[(np.arange(H)[:, None] % 4), (np.arange(W)[None, :] % 4)]
    band = np.exp(-((np.arange(H)[:, None] - H * 0.35 - np.arange(W)[None, :] * 0.18) / (H * 0.18)) ** 2)
    k = np.clip((neb - 0.45) * 2.2 * band + (by - 0.5) * 0.12, 0, 0.55)
    k = np.floor(k * 6) / 6
    tint = np.where((neb > 0.55)[..., None], np.array([150, 70, 170]), np.array([60, 90, 190]))
    A[..., :3] = A[..., :3] * (1 - k[..., None]) + tint * k[..., None]
    S.set_arr(A)
    stars(S, rnd, 340, 0)
    planet(S, W * 0.27, H * 0.68, 44, rnd)
    for _ in range(5):
        x, y = rnd.uniform(0, W), rnd.uniform(H * 0.4, H)
        S.px(x, y, (255, 255, 255)); S.px(x + 1, y, (200, 220, 255), 150); S.px(x - 1, y, (200, 220, 255), 150)
        S.px(x, y + 1, (200, 220, 255), 150); S.px(x, y - 1, (200, 220, 255), 150)
    L = new_layers()
    for k, (col, base, amp, fa) in {"far": ((90, 96, 120), 150, 140, 0.35), "mid": ((110, 114, 130), 100, 90, 0.1), "near": ((60, 62, 74), 70, 50, 0)}.items():
        Lk = L[k]
        t = sub(Lk)
        ys = ridge(t, rnd, base, amp, col, rough=0.55, freq=2 + (k != "far"), peaks=True)
        for _ in range(12):
            x = rnd.uniform(0, Lk.w)
            y = ys[int(x) % Lk.w] - rnd.uniform(10, 60)
            r = rnd.uniform(6, 18)
            t.ellipse(x, y, r, r * 0.45, shade(col, 0.8))
            t.ellipse(x, y + 1, r * 0.8, r * 0.3, shade(col, 0.7))
        volume(t, 1, 5)
        speckle(t, rnd, 0.06, 0.1)
        if k == "mid":
            for bx in (rnd.uniform(0.1, 0.3) * Lk.w, rnd.uniform(0.6, 0.8) * Lk.w):
                moon_base(t, rnd, bx, ys[int(bx) % Lk.w] - 6, (150, 156, 172), (190, 230, 255))
        edge_light(t, (1, 1), lit=shade(col, 1.4))
        if fa:
            fog(t, (40, 46, 80), fa)
        Lk.paste(t)
    return S.arr()[..., :3], L


@theme("undersea")
def undersea(rnd):
    """fundo do mar: raios de luz, ruinas afundadas e algas"""
    W, H = SKY
    S = sky_layer([(0, 0x3aa0d0), (0.4, 0x1a6aa0), (1, 0x08304e)])
    for _ in range(8):
        x = rnd.uniform(0, W)
        S.poly([(x, H), (x + 14, H), (x + 60, 0), (x + 30, 0)], (160, 220, 250), a=30)
    L = new_layers()
    f = L["far"]
    t = sub(f)
    ruin_skyline(t, rnd, 120, 50, 140, (40, 100, 140))
    volume(t, -1, 3)
    fog(t, (30, 90, 130), 0.4, bottom=((20, 70, 110), 0.6), bottom_h=0.6)
    f.paste(t)
    m = L["mid"]
    st = sub(m)
    ys = ridge(st, rnd, 80, 30, (24, 70, 100), rough=0.5, freq=3)
    for _ in range(10):
        x = rnd.uniform(0, m.w)
        gy = ys[int(x) % m.w] - 4
        if rnd.random() < 0.5:
            column(st, rnd, x, gy, 9, rnd.randint(50, 110), (40, 90, 120), (28, 66, 94), broken=True)
        else:
            arch(st, x, gy, 50, rnd.randint(40, 70), 8, (40, 90, 120), broken_side=1)
    bricks(st, rnd, 6, 3, 0.86, 0.06)
    volume(st, -1, 4)
    moss(st, rnd, (60, 140, 110), chance=0.6, drip=6)
    m.paste(st)
    alg = sub(m)
    for _ in range(40):
        x = rnd.uniform(0, m.w)
        y0 = ys[int(x) % m.w] - 4
        pts = [(x + math.sin(k * 0.5 + x) * 4, y0 + k * 6) for k in range(rnd.randint(6, 20))]
        alg.line(pts, rnd.choice([(40, 130, 90), (60, 150, 100), (30, 110, 80)]), 2)
    m.paste(alg)
    for _ in range(40):
        m.ellipse(rnd.uniform(0, m.w), rnd.uniform(80, 340), 1.5, 1.5, (180, 230, 250), a=160)
    n = L["near"]
    gr = sub(n)
    ys2 = ridge(gr, rnd, 70, 26, (10, 40, 60), rough=0.55, freq=4)
    for _ in range(30):
        x = rnd.uniform(0, n.w)
        y0 = ys2[int(x) % n.w] - 3
        pts = [(x + math.sin(k * 0.5 + x) * 5, y0 + k * 7) for k in range(rnd.randint(4, 14))]
        gr.line(pts, rnd.choice([(20, 90, 60), (30, 100, 70)]), 3)
    edge_light(gr, (-1, 1), lit=(60, 140, 160))
    n.paste(gr)
    return S.arr()[..., :3], L


@theme("school")
def school(rnd):
    return interior(rnd, (226, 210, 176), (170, 140, 104), (190, 50, 50),
                    window_sky=[(0, 0x6fb6e8), (0.7, 0xbfe4f6), (1, 0xf0f6f0)], kind="brick", style="classroom")


@theme("gym")
def gym(rnd):
    return interior(rnd, (176, 124, 82), (110, 74, 48), (60, 110, 190), window_sky=[(0, 0x6fb6e8), (1, 0xd0ecf6)], kind="wood", style="gym")


@theme("cellar")
def cellar(rnd):
    return interior(rnd, (96, 78, 62), (62, 48, 38), (140, 40, 40), kind="stone", style="cellar")


@theme("hold")
def hold(rnd):
    return interior(rnd, (104, 70, 44), (66, 42, 26), (170, 60, 40), kind="wood", style="ship")


@theme("sewer")
def sewer(rnd):
    return interior(rnd, (60, 78, 66), (38, 52, 44), (80, 130, 90), kind="brick", lamp=(180, 255, 160), style="sewer")


@theme("magma")
def magma(rnd):
    return interior(rnd, (86, 32, 26), (54, 20, 16), (255, 110, 50), kind="rock", lamp=(255, 140, 60), style="magma")


@theme("reactor")
def reactor(rnd):
    return interior(rnd, (44, 70, 86), (26, 44, 56), (60, 210, 230), kind="metal", lamp=(120, 240, 255), style="reactor")


def main():
    names = sys.argv[1:] or list(THEMES)
    for name in names:
        rnd = random.Random(hash(name) & 0xffff if False else sum(map(ord, name)) * 7919)
        sky, layers = THEMES[name](rnd)
        save_layers(name, sky, layers)
        preview(name, sky, layers)
        print("ok", name)


if __name__ == "__main__":
    main()
