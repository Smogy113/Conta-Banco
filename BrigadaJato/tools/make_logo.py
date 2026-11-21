"""Gera o logo em pixel art (assets/ui/logo.png) a partir da fonte Pixelify Sans (OFL).

Letras sem suavizacao, degrade em faixas, brilho em cima, contorno escuro de 1 pixel,
sombra e uma chama de jato saindo do JATO. Uso: python3 tools/make_logo.py
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
FONT = os.path.join(HERE, "fonts", "PixelifySans_700Bold.ttf")
OUT = os.path.join(HERE, "..", "assets", "ui", "logo.png")
INK = (26, 18, 32)


# letras desenhadas a mao (traco de 2 pixels)
GLYPHS = {
    "B": ["#######.", "##....##", "##....##", "##...##.", "######..", "##...##.", "##....##", "##....##", "#######."],
    "R": ["#######.", "##....##", "##....##", "##...##.", "######..", "##..##..", "##...##.", "##....##", "##....##"],
    "I": ["######", "..##..", "..##..", "..##..", "..##..", "..##..", "..##..", "..##..", "######"],
    "G": [".######.", "##....##", "##......", "##......", "##..####", "##....##", "##....##", "##....##", ".######."],
    "A": ["..####..", ".##..##.", "##....##", "##....##", "########", "##....##", "##....##", "##....##", "##....##"],
    "D": ["######..", "##...##.", "##....##", "##....##", "##....##", "##....##", "##....##", "##...##.", "######.."],
    "J": ["....####", "......##", "......##", "......##", "......##", "##....##", "##....##", "##....##", ".######."],
    "T": ["########", "...##...", "...##...", "...##...", "...##...", "...##...", "...##...", "...##...", "...##..."],
    "O": [".######.", "##....##", "##....##", "##....##", "##....##", "##....##", "##....##", "##....##", ".######."],
}


def text_mask(txt, scale_y=1, track=2):
    rows = 9 * scale_y
    w = sum(len(GLYPHS[c][0]) + track for c in txt) - track
    m = np.zeros((rows, w), bool)
    x = 0
    for c in txt:
        g = GLYPHS[c]
        for y, line in enumerate(g):
            for k in range(scale_y):
                for i, ch in enumerate(line):
                    if ch == "#":
                        m[y * scale_y + k, x + i] = True
        x += len(g[0]) + track
    return m


def paint(mask, bands):
    h, w = mask.shape
    out = np.zeros((h, w, 4), np.uint8)
    for y in range(h):
        t = y / max(1, h - 1)
        col = bands[min(len(bands) - 1, int(t * len(bands)))]
        out[y][mask[y]] = (*col, 255)
    # brilho: pixel de cima de cada coluna
    top = mask & ~np.vstack([np.zeros((1, w), bool), mask[:-1]])
    out[top] = (255, 250, 220, 255)
    # pontilhado na faixa do meio (textura de pixel art)
    for y in range(h):
        for x in range(w):
            if mask[y, x] and (x + y) % 2 == 0 and abs(y - h * 0.55) < 0.6:
                out[y, x, :3] = (out[y, x, :3] * 0.88).astype(np.uint8)
    # sombra interna: pixel de baixo
    bot = mask & ~np.vstack([mask[1:], np.zeros((1, w), bool)])
    dark = tuple(int(c * 0.72) for c in bands[-1])
    out[bot] = (*dark, 255)
    return out


def extrude(img, depth, col):
    """volume 3D: copias escuras deslocadas para baixo"""
    h, w = img.shape[:2]
    out = np.zeros((h + depth, w + 1, 4), np.uint8)
    a = img[..., 3] > 0
    for k in range(depth, 0, -1):
        c = tuple(int(v * (0.75 + 0.25 * (depth - k) / depth)) for v in col)
        sub = out[k:k + h, :w]
        sub[a] = (*c, 255)
    out[:h, :w][a] = img[a]
    return out


def outline(img, col=INK, n=1):
    a = img[..., 3] > 0
    h, w = a.shape
    pad = np.zeros((h + 2 * n, w + 2 * n, 4), np.uint8)
    pad[n:n + h, n:n + w] = img
    A = np.zeros((h + 2 * n, w + 2 * n), bool)
    A[n:n + h, n:n + w] = a
    grown = A.copy()
    for dy in range(-n, n + 1):
        for dx in range(-n, n + 1):
            grown |= np.roll(np.roll(A, dy, 0), dx, 1)
    ring = grown & ~A
    pad[ring] = (*col, 255)
    return pad


def stack(base, top, ox, oy):
    h, w = top.shape[:2]
    H, W = base.shape[:2]
    for y in range(h):
        for x in range(w):
            if top[y, x, 3] and 0 <= oy + y < H and 0 <= ox + x < W:
                base[oy + y, ox + x] = top[y, x]


def main():
    brig = outline(extrude(paint(text_mask("BRIGADA", 1, 2), [(255, 230, 120), (255, 200, 40), (248, 164, 22), (230, 118, 16)]), 3, (150, 62, 20)))
    jato = outline(extrude(paint(text_mask("JATO", 1, 2), [(255, 176, 96), (248, 116, 44), (220, 70, 32), (176, 44, 30)]), 2, (110, 30, 28)))
    W = max(brig.shape[1], jato.shape[1] + 46) + 6
    H = brig.shape[0] + jato.shape[0] + 6
    img = np.zeros((H, W, 4), np.uint8)
    bx = (W - brig.shape[1]) // 2
    jx = W - jato.shape[1] - 4
    jy = brig.shape[0] - 1
    # sombra (2 px para baixo/direita)
    for part, x, y in ((brig, bx, 0), (jato, jx, jy)):
        sh = part.copy()
        sh[sh[..., 3] > 0] = (12, 8, 18, 170)
        stack(img, sh, x + 1, y + 2)
    # chama do jato saindo da esquerda do JATO
    fy = jy + jato.shape[0] // 2
    cols = [(255, 255, 230), (255, 236, 120), (255, 190, 50), (246, 120, 30), (200, 60, 30)]
    for i in range(40):
        x = jx - 2 - i
        th = max(1, int(5 - i * 0.11))
        c = cols[min(len(cols) - 1, i // 8)]
        for dy in range(-th // 2, th - th // 2):
            if (i > 30 and (i + dy) % 3 == 0):
                continue
            img[fy + dy, x] = (*c, 255)
    stack(img, brig, bx, 0)
    stack(img, jato, jx, jy)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    Image.fromarray(img, "RGBA").save(OUT)
    big = Image.fromarray(img, "RGBA").resize((W * 5, H * 5), Image.NEAREST)
    bg = Image.new("RGBA", big.size, (40, 52, 70, 255))
    bg.alpha_composite(big)
    bg.save(os.path.join(HERE, "..", "docs", "logo_preview.png"))
    print("logo", W, H, "chama_ponta_x", jx - 2, "chama_y_de_baixo", H - 1 - fy)


if __name__ == "__main__":
    main()
