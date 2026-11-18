#!/usr/bin/env python3
"""Mapas competitivos (modo bomba, estilo CS/Valorant) e o mapa grande do Battle Royale.
Desenhados com retangulos (nao espelhados): atacantes (r) a esquerda, defensores (b) a direita.
Rotas: longo do A (cima), meio com portas, tuneis do B (baixo) e rotacao dos defensores."""
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "maps")


class Grid:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.g = [["." for _ in range(w)] for _ in range(h)]

    def fill(self, x0, y0, x1, y1, ch="#"):
        for y in range(max(0, y0), min(self.h, y1 + 1)):
            for x in range(max(0, x0), min(self.w, x1 + 1)):
                self.g[y][x] = ch

    def clear(self, x0, y0, x1, y1):
        self.fill(x0, y0, x1, y1, ".")

    def put(self, x, y, ch):
        self.g[y][x] = ch

    def rows(self):
        out = []
        for y, r in enumerate(self.g):
            r = list(r)
            if y == 0 or y == self.h - 1:
                r = ["#"] * self.w
            r[0] = r[-1] = "#"
            out.append("".join(r))
        return out


def save(fid, name_pt, name_en, theme, g, extra=""):
    with open(os.path.join(OUT, fid + ".txt"), "w", encoding="utf-8") as f:
        f.write(f"@name {name_pt}\n@name_en {name_en}\n@theme {theme}\n{extra}")
        for r in g.rows():
            f.write(r + "\n")
    print(fid, g.w, "x", g.h)
    for r in g.rows():
        print("  " + r)


def poeira():
    """Deserto, estilo 'dust': A em cima, B embaixo."""
    g = Grid(100, 36)
    # pisos que separam as 3 camadas (cima 1-10, meio 13-22, baixo 25-34)
    g.fill(0, 11, 99, 12)
    g.fill(0, 23, 99, 24)
    # --- base dos atacantes (esquerda): poço que liga as 3 camadas
    g.clear(6, 11, 10, 12)
    g.clear(6, 23, 10, 24)
    g.fill(1, 22, 5, 22, "=")
    g.fill(11, 17, 14, 17, "=")
    for x in (2, 4, 12, 14):
        g.put(x, 21, "r")
    g.put(3, 9, "r"); g.put(13, 33, "r")
    # --- LONGO DO A (cima): corredor comprido com as "portas do longo"
    g.fill(36, 1, 38, 6)          # portas do longo (passa por baixo)
    g.fill(24, 9, 26, 10)          # caixa de cobertura
    g.fill(48, 8, 49, 10, "D")     # caixas quebraveis
    g.put(20, 10, "W"); g.put(44, 10, "H")
    # --- BOMB A (cima a direita): plataforma com caixotes e muretas
    g.fill(64, 9, 66, 10)          # caixote "default"
    g.fill(74, 8, 75, 10)          # mureta
    g.fill(70, 5, 78, 5, "=")      # varanda do A
    g.fill(80, 1, 81, 6)           # pilar ao lado do site
    for x in (67, 68, 69, 70, 71, 72):
        g.put(x, 10, "a")
    g.put(60, 10, "X")
    g.put(77, 4, "M")
    # escada curta do meio para o A ("short"): abertura no piso
    g.clear(56, 11, 58, 12)
    g.fill(54, 12, 55, 12, "=")
    # --- MEIO: portas do meio (parede com vao embaixo) e passarela
    g.fill(40, 13, 42, 18)         # portas do meio
    g.fill(30, 17, 36, 17, "=")    # passarela
    g.fill(22, 20, 24, 22)         # caixote do meio
    g.fill(60, 19, 62, 22)         # caixote perto do conector
    g.put(28, 22, "W"); g.put(46, 22, "G"); g.put(66, 22, "P")
    g.put(52, 16, "M")
    g.put(34, 22, "X")
    # conector do meio para o B ("janela")
    g.clear(50, 23, 52, 24)
    # --- TUNEIS DO B (baixo): teto baixo, passagem estreita e porta com alavanca
    g.fill(14, 25, 44, 29)         # teto dos tuneis
    g.fill(30, 33, 32, 34, "#")    # degrau
    g.fill(45, 25, 46, 34)         # parede da saida do tunel...
    g.fill(45, 30, 46, 34, "O")    # ...com porta de correr
    g.put(43, 34, "L"); g.put(48, 34, "L")
    g.put(18, 34, "W"); g.put(38, 34, "H")
    g.put(25, 30, "M")
    # --- BOMB B (baixo a direita): pilares e caixas
    g.fill(60, 31, 62, 34)
    g.fill(72, 30, 73, 34)
    g.fill(66, 27, 70, 27, "=")
    g.fill(76, 33, 77, 34, "D")
    for x in (63, 64, 65, 66, 67, 68):
        g.put(x, 34, "c")
    g.put(58, 34, "X")
    g.put(70, 26, "M")
    # --- BASE DOS DEFENSORES (direita): rotacao entre A e B
    g.clear(88, 11, 91, 12)
    g.clear(88, 23, 91, 24)
    g.fill(92, 17, 98, 17, "=")
    g.fill(84, 13, 85, 18)          # parede que esconde a base
    for x in (90, 93, 95, 97):
        g.put(x, 22, "b")
    g.put(94, 16, "b"); g.put(96, 34, "b")
    g.put(86, 22, "H"); g.put(95, 10, "G")
    g.fill(97, 1, 98, 1, ".")
    save("poeira", "Poeira (competitivo)", "Dust Yard (competitive)", "desert", g)


def estacao():
    """Fabrica/estacao: B em cima, A embaixo, com meio vertical."""
    g = Grid(100, 36)
    g.fill(0, 11, 99, 12)
    g.fill(0, 23, 99, 24)
    # atacantes
    g.clear(5, 11, 8, 12)
    g.clear(5, 23, 8, 24)
    for x in (2, 4, 10, 12):
        g.put(x, 22, "r")
    g.put(3, 10, "r"); g.put(11, 34, "r")
    g.fill(9, 18, 13, 18, "=")
    # cima: rota do B com galpao
    g.fill(28, 1, 30, 7)
    g.fill(40, 8, 44, 10)
    g.fill(52, 6, 58, 6, "=")
    g.put(22, 10, "W"); g.put(48, 10, "H"); g.put(35, 10, "X")
    # B em cima a direita
    g.fill(66, 7, 67, 10)
    g.fill(74, 9, 76, 10, "D")
    g.fill(78, 4, 84, 4, "=")
    for x in (68, 69, 70, 71, 72, 73):
        g.put(x, 10, "c")
    g.put(80, 3, "M"); g.put(64, 10, "X")
    # meio: conectores verticais
    g.clear(36, 11, 38, 12)
    g.clear(62, 23, 64, 24)
    g.fill(46, 13, 48, 19)
    g.fill(54, 21, 57, 22)
    g.fill(20, 16, 26, 16, "=")
    g.put(30, 22, "W"); g.put(52, 22, "G"); g.put(70, 22, "P"); g.put(40, 22, "X")
    g.put(60, 15, "M")
    # baixo: rota do A pelo esgoto
    g.fill(12, 25, 40, 28)
    g.fill(41, 25, 42, 34)
    g.fill(41, 30, 42, 34, "O")
    g.put(39, 34, "L"); g.put(44, 34, "L")
    g.put(20, 34, "W"); g.put(32, 34, "H"); g.put(26, 29, "M")
    # A embaixo a direita
    g.fill(58, 31, 60, 34)
    g.fill(70, 29, 71, 34)
    g.fill(64, 27, 68, 27, "=")
    for x in (61, 62, 63, 64, 65, 66):
        g.put(x, 34, "a")
    g.put(74, 34, "X"); g.put(66, 26, "M")
    g.fill(76, 33, 77, 34, "D")
    # defensores
    g.clear(88, 11, 91, 12)
    g.clear(88, 23, 91, 24)
    g.fill(84, 13, 85, 18)
    g.fill(92, 17, 98, 17, "=")
    for x in (90, 93, 95, 97):
        g.put(x, 22, "b")
    g.put(94, 16, "b"); g.put(96, 34, "b")
    g.put(86, 22, "H"); g.put(95, 10, "G")
    save("estacao", "Estação (competitivo)", "Station (competitive)", "factory", g)


def arena_br():
    """Mapa grande do Battle Royale: 4 regioes (praia, vila, floresta, montanha) com cavernas."""
    import random
    rnd = random.Random(7)
    W, H = 200, 60
    g = Grid(W, H)
    # terreno ondulado
    import math
    ground = []
    for x in range(W):
        y = int(H - 8 - 6 * math.sin(x * 0.045) - 4 * math.sin(x * 0.11 + 1) - (10 if 120 < x < 170 else 0) * math.sin((x - 120) / 50 * math.pi))
        ground.append(max(12, min(H - 4, y)))
    for x in range(W):
        g.fill(x, ground[x], x, H - 1)
    # agua/espinhos na praia
    for x in range(1, 14):
        g.fill(x, ground[x] - 1, x, ground[x] - 1, "^")
    # cavernas por baixo
    for cx, cy, cw in ((40, H - 6, 30), (100, H - 7, 40), (160, H - 6, 25)):
        g.clear(cx, cy - 2, cx + cw, cy)
        g.fill(cx + cw // 2, cy, cx + cw // 2 + 1, cy, "M")
    # vila: casas com telhado
    for hx in (60, 72, 84, 96):
        gy = ground[hx]
        g.fill(hx, gy - 7, hx + 9, gy - 7)          # telhado
        g.fill(hx, gy - 6, hx, gy - 3)
        g.fill(hx + 9, gy - 6, hx + 9, gy - 3)
        g.fill(hx + 2, gy - 4, hx + 7, gy - 4, "=")
        g.put(hx + 4, gy - 5, "W"); g.put(hx + 6, gy - 1, "H")
        g.put(hx + 1, gy - 6, "M")
    # plataformas flutuantes e torres
    for k in range(26):
        x = rnd.randint(8, W - 14)
        y = rnd.randint(8, max(9, ground[x] - 10))
        g.fill(x, y, x + rnd.randint(4, 8), y, "=")
        if rnd.random() < 0.5:
            g.put(x + 2, y - 1, rnd.choice("WWHGP"))
    for x in (30, 140, 185):
        gy = ground[x]
        g.fill(x, gy - 14, x + 1, gy - 1)
        g.fill(x - 3, gy - 14, x + 4, gy - 14, "=")
        g.put(x, gy - 15, "W")
    # caixas e barris
    for k in range(18):
        x = rnd.randint(16, W - 6)
        gy = ground[x]
        if g.g[gy - 1][x] == ".":
            g.put(x, gy - 1, rnd.choice("XDW"))
    # spawns espalhados
    for k in range(24):
        x = 10 + k * 8
        gy = ground[x]
        if g.g[gy - 1][x] == ".":
            g.put(x, gy - 1, "S")
    save("arena", "Arena Gigante (BR)", "Giant Arena (BR)", "jungle", g)


if __name__ == "__main__":
    poeira()
    estacao()
    arena_br()
