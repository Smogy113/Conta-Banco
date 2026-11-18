package com.brigadajato.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.brigadajato.Assets;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.WeaponType;

/**
 * Icones grandes e animados dos modos de jogo (tela de escolha de modo). Cada icone e montado com
 * pecas sombreadas e contorno escuro, num "quadro" local (posicao, tamanho e giro), como um adesivo.
 */
public final class ModeIcons {
    private static final int INK = 0x15151c;
    private static final int MAX = 96;

    private final Draw d;
    private final Assets a;
    private final SoldierPainter painter;
    private final TextureRegion CIRC, BALL, RECT, BOX, TRI, HALF, SOFT, RING, STAR, PILL;

    // quadro local
    private float ox, oy, s, fc = 1, fs, fdeg, o, al = 1;
    // pecas com contorno (desenhadas em duas passadas: contorno de todas, depois o preenchimento)
    private final TextureRegion[] shp = new TextureRegion[MAX], fil = new TextureRegion[MAX];
    private final float[] px = new float[MAX], py = new float[MAX], pw = new float[MAX], ph = new float[MAX], pd = new float[MAX];
    private final int[] pc = new int[MAX];
    private final boolean[] shade = new boolean[MAX];
    private int n;

    public ModeIcons(Draw d, Assets a, SoldierPainter painter) {
        this.d = d;
        this.a = a;
        this.painter = painter;
        ShadeKit.load();
        CIRC = a.circle;
        BALL = ShadeKit.ball;
        RECT = a.white;
        BOX = ShadeKit.box;
        TRI = a.tri;
        HALF = a.half;
        SOFT = a.soft;
        RING = a.ring;
        STAR = a.star;
        PILL = ShadeKit.pill;
    }

    // ------------------------------------------------------------------ quadro e pecas

    private void frame(float x, float y, float size, float deg) {
        ox = x;
        oy = y;
        s = size;
        fdeg = deg;
        fc = MathUtils.cosDeg(deg);
        fs = MathUtils.sinDeg(deg);
        o = Math.max(1.4f, size * 0.032f);
    }

    private float wx(float lx, float ly) {
        return ox + (lx * fc - ly * fs) * s;
    }

    private float wy(float lx, float ly) {
        return oy + (lx * fs + ly * fc) * s;
    }

    /** peca com contorno; tamanhos em unidades do quadro */
    private void part(TextureRegion shape, TextureRegion fill, float lx, float ly, float w, float h, float deg, int col) {
        if (n >= MAX) return;
        shp[n] = shape;
        fil[n] = fill;
        px[n] = lx;
        py[n] = ly;
        pw[n] = w;
        ph[n] = h;
        pd[n] = deg;
        pc[n] = col;
        shade[n] = false;
        n++;
    }

    /** retangulo reto com sombra embaixo e brilho em cima */
    private void slab(float lx, float ly, float w, float h, float deg, int col) {
        part(RECT, RECT, lx, ly, w, h, deg, col);
        shade[n - 1] = true;
    }

    private void flush() {
        for (int i = 0; i < n; i++) {
            d.rgb(INK, al);
            d.region(shp[i], wx(px[i], py[i]), wy(px[i], py[i]), pw[i] * s + o * 2, ph[i] * s + o * 2, pd[i] + fdeg);
        }
        for (int i = 0; i < n; i++) {
            float cx = wx(px[i], py[i]), cy = wy(px[i], py[i]), w = pw[i] * s, h = ph[i] * s, deg = pd[i] + fdeg;
            d.rgb(pc[i], al);
            d.region(fil[i], cx, cy, w, h, deg);
            if (shade[i]) {
                // faixa de sombra (base) e de brilho (topo) no proprio eixo da peca
                float c = MathUtils.cosDeg(deg), sn = MathUtils.sinDeg(deg);
                float off = -h * 0.36f;
                d.rgb(SoldierPainter.mix(pc[i], 0x000000, 0.3f), al);
                d.region(RECT, cx - sn * off, cy + c * off, w, h * 0.28f, deg);
                float on = h * 0.38f;
                d.rgb(SoldierPainter.mix(pc[i], 0xffffff, 0.25f), al);
                d.region(RECT, cx - sn * on, cy + c * on, w, h * 0.12f, deg);
            }
        }
        n = 0;
    }

    /** forma sem contorno (detalhes) */
    private void flat(TextureRegion r, float lx, float ly, float w, float h, float deg, int col, float alpha) {
        d.rgb(col, al * alpha);
        d.region(r, wx(lx, ly), wy(lx, ly), w * s, h * s, deg + fdeg);
    }

    private void line(float x1, float y1, float x2, float y2, float thick, int col, float alpha) {
        d.rgb(col, al * alpha);
        d.line(wx(x1, y1), wy(x1, y1), wx(x2, y2), wy(x2, y2), Math.max(1f, thick * s));
    }

    private void glow(float lx, float ly, float r, int col, float alpha) {
        d.additive(true);
        flat(SOFT, lx, ly, r * 2, r * 2, 0, col, alpha);
        d.additive(false);
    }

    private void text(String t, float lx, float ly, float size, int col) {
        BitmapFont f = a.small;
        float keep = f.getScaleX();
        f.getData().setScale(keep * size * s / 30f);
        f.setColor(((col >> 16) & 0xff) / 255f, ((col >> 8) & 0xff) / 255f, (col & 0xff) / 255f, al);
        f.draw(d.batch, t, wx(lx, ly), wy(lx, ly) + f.getCapHeight() / 2, 0, Align.center, false);
        f.getData().setScale(keep);
        f.setColor(1, 1, 1, 1);
    }

    private static float hash(int i) {
        float v = MathUtils.sin(i * 12.9898f + 78.233f) * 43758.547f;
        return v - (float) Math.floor(v);
    }

    // ------------------------------------------------------------------ icones

    /** Desenha o icone do modo centrado em (x, y), tamanho s (~ largura do icone), tempo t. */
    public void draw(ModeType m, float x, float y, float size, float t, float alpha) {
        al = alpha;
        n = 0;
        switch (m) {
            case DEATHMATCH: deathmatch(x, y, size, t); break;
            case TEAM_DEATHMATCH: teams(x, y, size, t); break;
            case CTF: flag(x, y, size, t); break;
            case BOMB: bomb(x, y, size, t); break;
            case SURVIVAL: survival(x, y, size, t); break;
            case GUN_GAME: gunGame(x, y, size, t); break;
            case KING_HILL: king(x, y, size, t); break;
            case INFECTION: zombie(x, y, size, t); break;
            case BATTLE_ROYALE: royale(x, y, size, t); break;
            case DUEL: duel(x, y, size, t); break;
            case HIDE_SEEK: hide(x, y, size, t); break;
            default:
                frame(x, y, size, 0);
                part(STAR, STAR, 0, 0, 0.8f, 0.8f, 0, 0xffca28);
                flush();
        }
        d.additive(false);
        d.batch.setColor(1, 1, 1, 1);
    }

    private void deathmatch(float x, float y, float size, float t) {
        frame(x, y, size, 0);
        // mira girando atras
        flat(RING, 0, 0, 1.3f, 1.3f, 0, 0xffffff, 0.16f);
        for (int k = 0; k < 4; k++) {
            float ang = t * 30 + k * 90;
            float c = MathUtils.cosDeg(ang), sn = MathUtils.sinDeg(ang);
            line(c * 0.52f, sn * 0.52f, c * 0.7f, sn * 0.7f, 0.035f, 0xffffff, 0.45f);
        }
        // fuzis cruzados
        float gs = size * 0.034f;
        painter.drawGun(d, WeaponType.RIFLE, 0, x, y - size * 0.08f, 34, gs, al, 1);
        painter.drawGun(d, WeaponType.RIFLE, 0, x, y - size * 0.08f, 34, gs, al, -1);
        // caveira
        float bob = MathUtils.sin(t * 2.4f) * 0.03f;
        frame(x, y + bob * size, size * 0.92f, MathUtils.sin(t * 1.3f) * 3);
        int bone = 0xece5d2;
        part(CIRC, BALL, 0, 0.1f, 0.7f, 0.66f, 0, bone);
        part(RECT, BOX, 0, -0.2f, 0.42f, 0.28f, 0, bone);
        part(CIRC, BALL, -0.2f, -0.07f, 0.2f, 0.2f, 0, bone);
        part(CIRC, BALL, 0.2f, -0.07f, 0.2f, 0.2f, 0, bone);
        flush();
        int hole = 0x1d1416;
        flat(CIRC, -0.14f, 0.03f, 0.21f, 0.18f, -14, hole, 1);
        flat(CIRC, 0.14f, 0.03f, 0.21f, 0.18f, 14, hole, 1);
        float g = 0.6f + 0.4f * MathUtils.sin(t * 5);
        glow(-0.14f, 0.03f, 0.16f, 0xff1744, 0.7f * g);
        glow(0.14f, 0.03f, 0.16f, 0xff1744, 0.7f * g);
        flat(CIRC, -0.14f, 0.025f, 0.065f, 0.065f, 0, 0xff8a80, 1);
        flat(CIRC, 0.14f, 0.025f, 0.065f, 0.065f, 0, 0xff8a80, 1);
        flat(TRI, 0, -0.1f, 0.09f, 0.1f, 180, hole, 1);
        // dentes
        line(-0.17f, -0.2f, 0.17f, -0.2f, 0.022f, hole, 0.9f);
        for (int i = -1; i <= 1; i++) line(i * 0.1f, -0.3f, i * 0.1f, -0.12f, 0.02f, hole, 0.85f);
        // rachadura
        line(-0.1f, 0.42f, -0.04f, 0.32f, 0.022f, hole, 0.8f);
        line(-0.04f, 0.32f, -0.1f, 0.24f, 0.02f, hole, 0.8f);
        line(-0.04f, 0.32f, 0.02f, 0.28f, 0.016f, hole, 0.7f);
        flat(CIRC, -0.17f, 0.3f, 0.16f, 0.09f, 30, 0xffffff, 0.55f);
    }

    private void shield(float x, float y, float size, float deg, int col, boolean star) {
        frame(x, y, size, deg);
        int dark = SoldierPainter.mix(col, 0x000000, 0.3f), light = SoldierPainter.mix(col, 0xffffff, 0.3f);
        part(RECT, RECT, 0, 0.12f, 0.5f, 0.36f, 0, 0x90a4ae);
        part(TRI, TRI, 0, -0.17f, 0.5f, 0.36f, 180, 0x90a4ae);
        flush();
        // borda de metal e miolo pintado
        flat(RECT, 0, 0.11f, 0.42f, 0.3f, 0, col, 1);
        flat(TRI, 0, -0.15f, 0.42f, 0.3f, 180, col, 1);
        flat(RECT, 0, -0.02f, 0.42f, 0.04f, 0, dark, 0.6f);
        flat(RECT, -0.1f, 0.06f, 0.07f, 0.5f, 0, light, 0.5f);
        if (star) {
            flat(STAR, 0, 0.06f, 0.3f, 0.3f, 0, INK, 1);
            flat(STAR, 0, 0.06f, 0.24f, 0.24f, 0, 0xffffff, 1);
        } else {
            for (int k = 0; k < 2; k++) {
                float yy = 0.12f - k * 0.12f;
                flat(RECT, -0.07f, yy, 0.18f, 0.06f, -38, 0xffffff, 1);
                flat(RECT, 0.07f, yy, 0.18f, 0.06f, 38, 0xffffff, 1);
            }
        }
        for (int i = -1; i <= 1; i += 2) flat(CIRC, i * 0.2f, 0.26f, 0.05f, 0.05f, 0, 0xcfd8dc, 1);
        flat(SOFT, -0.1f, 0.22f, 0.3f, 0.14f, 20, 0xffffff, 0.35f);
    }

    private void teams(float x, float y, float size, float t) {
        float k = Math.abs(MathUtils.sin(t * 2.2f));
        float off = 0.22f + 0.07f * k;
        shield(x - off * size, y, size * 0.95f, 10 + 6 * k, 0xd32f2f, true);
        shield(x + off * size, y, size * 0.95f, -10 - 6 * k, 0x1976d2, false);
        frame(x, y, size, 0);
        if (k < 0.25f) {
            float p = 1 - k / 0.25f;
            d.additive(true);
            flat(SOFT, 0, 0.05f, 0.5f * p, 0.5f * p, 0, 0xffd54f, 0.8f * p);
            for (int i = 0; i < 7; i++) {
                float ang = i * 51 + t * 10;
                float c = MathUtils.cosDeg(ang), sn = MathUtils.sinDeg(ang);
                line(c * 0.06f, 0.05f + sn * 0.06f, c * (0.12f + 0.2f * p), 0.05f + sn * (0.12f + 0.2f * p), 0.025f, 0xffeb3b, p);
            }
            d.additive(false);
        }
    }

    private void flag(float x, float y, float size, float t) {
        frame(x, y, size, 0);
        // vento
        for (int i = 0; i < 3; i++) {
            float u = (t * 0.8f + i * 0.33f) % 1f;
            float yy = 0.3f - i * 0.18f;
            line(-0.5f + u * 1.1f, yy, -0.36f + u * 1.1f, yy, 0.018f, 0xffffff, 0.35f * MathUtils.sin(u * MathUtils.PI));
        }
        // base de pedra e mastro
        part(HALF, HALF, -0.27f, -0.42f, 0.4f, 0.16f, 0, 0x78909c);
        slab(-0.27f, 0.0f, 0.055f, 0.86f, 0, 0x8d6e63);
        part(CIRC, BALL, -0.27f, 0.45f, 0.11f, 0.11f, 0, 0xffca28);
        flush();
        // pano ondulando em tiras
        int N = 14;
        float W = 0.7f, H = 0.4f, left = -0.245f, top = 0.41f;
        int base = 0xd32f2f;
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i < N; i++) {
                float u = i / (float) N, uc = (i + 0.5f) / N;
                float wave = MathUtils.sin(t * 6 - uc * 5) * 0.06f * uc;
                float slope = MathUtils.cos(t * 6 - uc * 5);
                float cx = left + uc * W, cy = top - H / 2 + wave;
                float sw = W / N + 0.003f;
                if (pass == 0) {
                    flat(RECT, cx, cy, sw + (i == N - 1 ? o * 2 / s : 0), H + o * 2 / s, 0, INK, 1);
                    continue;
                }
                float sh = 0.25f * Math.max(0, slope) * uc;
                flat(RECT, cx, cy, sw, H, 0, SoldierPainter.mix(base, 0x000000, sh), 1);
                flat(RECT, cx, cy, sw, H * 0.26f, 0, SoldierPainter.mix(0xffffff, 0x000000, sh), 1);
                flat(RECT, cx, cy + H * 0.42f, sw, H * 0.08f, 0, 0xffffff, 0.18f);
            }
        float ec = 0.55f, wave = MathUtils.sin(t * 6 - ec * 5) * 0.06f * ec;
        flat(STAR, left + ec * W, top - H / 2 + wave, 0.2f, 0.2f, 0, INK, 1);
        flat(STAR, left + ec * W, top - H / 2 + wave, 0.15f, 0.15f, 0, 0xffca28, 1);
    }

    private void bomb(float x, float y, float size, float t) {
        float ph = t % 4.2f;
        int sec = Math.max(0, 3 - (int) ph);
        boolean boom = ph > 3.4f;
        float shake = ph > 2.9f && !boom ? MathUtils.sin(t * 90) * 0.012f : 0;
        frame(x + shake * size, y, size, 0);
        // tres blocos de explosivo plastico
        int c4 = 0xc9a76b;
        for (int i = -1; i <= 1; i++) slab(i * 0.19f, -0.06f, 0.18f, 0.6f, 0, c4);
        flush();
        for (int i = -1; i <= 1; i++) {
            line(i * 0.19f - 0.05f, -0.3f, i * 0.19f - 0.05f, 0.16f, 0.012f, 0x8d6e3a, 0.6f);
            text("C4", i * 0.19f, -0.26f, 0.32f, 0x6d4c22);
        }
        // fitas
        for (int k = 0; k < 2; k++) {
            float yy = k == 0 ? 0.13f : -0.27f;
            flat(RECT, 0, yy, 0.62f, 0.065f, 0, 0x263238, 1);
            flat(RECT, 0, yy + 0.02f, 0.62f, 0.012f, 0, 0x546e7a, 1);
        }
        // fios
        for (int w = 0; w < 2; w++) {
            int col = w == 0 ? 0xe53935 : 0x1e88e5;
            float sx = w == 0 ? -0.15f : 0.15f, ex = w == 0 ? -0.28f : 0.3f;
            float px0 = sx, py0 = 0.05f;
            for (int i = 1; i <= 8; i++) {
                float u = i / 8f;
                float bx = MathUtils.lerp(sx, ex, u), by = 0.05f + MathUtils.sin(u * MathUtils.PI) * 0.22f + u * 0.1f;
                line(px0, py0, bx, by, 0.05f, INK, 1);
                px0 = bx;
                py0 = by;
            }
            px0 = sx;
            py0 = 0.05f;
            for (int i = 1; i <= 8; i++) {
                float u = i / 8f;
                float bx = MathUtils.lerp(sx, ex, u), by = 0.05f + MathUtils.sin(u * MathUtils.PI) * 0.22f + u * 0.1f;
                line(px0, py0, bx, by, 0.028f, col, 1);
                px0 = bx;
                py0 = by;
            }
        }
        // relogio
        part(RECT, BOX, 0, -0.02f, 0.44f, 0.24f, 0, 0x37474f);
        flush();
        flat(RECT, -0.03f, -0.02f, 0.3f, 0.14f, 0, 0x0a140c, 1);
        int led = sec <= 1 ? 0xff1744 : 0x76ff03;
        text("0:0" + sec, -0.03f, -0.02f, 0.5f, led);
        boolean blink = ((int) (t * (sec <= 1 ? 8 : 3))) % 2 == 0;
        flat(CIRC, 0.17f, -0.02f, 0.06f, 0.06f, 0, blink ? 0xff1744 : 0x5d1010, 1);
        if (blink) glow(0.17f, -0.02f, 0.14f, 0xff1744, 0.9f);
        // explosao
        if (boom) {
            float k = (ph - 3.4f) / 0.8f;
            d.additive(true);
            flat(SOFT, 0, 0, 1.6f * Interpolation.pow2Out.apply(k), 1.6f * Interpolation.pow2Out.apply(k), 0, 0xff9100, 1 - k);
            flat(SOFT, 0, 0, 0.8f, 0.8f, 0, 0xffffff, (1 - k) * (1 - k));
            d.additive(false);
            flat(RING, 0, 0, 0.3f + k * 1.2f, 0.3f + k * 1.2f, 0, 0xffe082, 1 - k);
        }
    }

    private void survival(float x, float y, float size, float t) {
        frame(x, y, size, 0);
        // ondas
        for (int i = 0; i < 3; i++) {
            float k = (t * 0.5f + i / 3f) % 1f;
            flat(RING, 0, 0.02f, 0.5f + k * 0.9f, 0.5f + k * 0.9f, 0, 0xffffff, (1 - k) * 0.35f);
        }
        // eletrocardiograma rolando
        float head = (t * 0.45f) % 1f;
        float prevX = 0, prevY = 0;
        int seg = 40;
        for (int i = 0; i <= seg; i++) {
            float u = i / (float) seg;
            float lx = -0.62f + u * 1.24f;
            float p = (u * 2.2f - t * 1.0f) % 1f;
            if (p < 0) p += 1;
            float yy = 0;
            if (p < 0.04f) yy = p / 0.04f * 0.32f;
            else if (p < 0.08f) yy = 0.32f - (p - 0.04f) / 0.04f * 0.5f;
            else if (p < 0.11f) yy = -0.18f + (p - 0.08f) / 0.03f * 0.18f;
            else if (p > 0.25f && p < 0.33f) yy = MathUtils.sin((p - 0.25f) / 0.08f * MathUtils.PI) * 0.06f;
            yy -= 0.04f;
            if (i > 0) line(prevX, prevY, lx, yy, 0.03f, 0x69f0ae, 0.85f);
            prevX = lx;
            prevY = yy;
        }
        // coracao batendo
        float beat = (t * 1.3f) % 1f;
        float sc = 1 + 0.12f * Math.max(0, MathUtils.sin(beat * MathUtils.PI2 * 2)) * (beat < 0.5f ? 1 : 0);
        frame(x, y + size * 0.03f, size * 0.85f * sc, 0);
        int red = 0xe53935;
        part(CIRC, BALL, -0.17f, 0.08f, 0.42f, 0.42f, 0, red);
        part(CIRC, BALL, 0.17f, 0.08f, 0.42f, 0.42f, 0, red);
        part(TRI, TRI, 0, -0.15f, 0.76f, 0.52f, 180, red);
        flush();
        flat(TRI, 0.05f, -0.2f, 0.5f, 0.3f, 180, 0xb71c1c, 0.6f);
        flat(CIRC, -0.22f, 0.17f, 0.14f, 0.09f, 35, 0xffffff, 0.6f);
        // curativo
        part(PILL, PILL, 0.13f, -0.04f, 0.46f, 0.15f, -35, 0xffcc80);
        flush();
        flat(RECT, 0.13f, -0.04f, 0.13f, 0.11f, -35, 0xffe0b2, 1);
        for (int i = -1; i <= 1; i += 2)
            for (int j = -1; j <= 1; j += 2) {
                float lx = 0.13f + i * 0.03f * MathUtils.cosDeg(-35) - j * 0.02f * MathUtils.sinDeg(-35);
                float ly = -0.04f + i * 0.03f * MathUtils.sinDeg(-35) + j * 0.02f * MathUtils.cosDeg(-35);
                flat(CIRC, lx, ly, 0.022f, 0.022f, 0, 0xbf8f5a, 1);
            }
    }

    private void gunGame(float x, float y, float size, float t) {
        WeaponType[] ws = {WeaponType.PISTOL, WeaponType.SMG, WeaponType.SHOTGUN, WeaponType.RIFLE, WeaponType.SNIPER, WeaponType.ROCKET};
        int i = ((int) (t / 1.0f)) % ws.length;
        float k = (t / 1.0f) % 1f;
        frame(x, y, size, 0);
        // raios dourados girando
        d.additive(true);
        for (int r = 0; r < 8; r++) {
            float ang = t * 25 + r * 45;
            float c = MathUtils.cosDeg(ang), sn = MathUtils.sinDeg(ang);
            flat(TRI, c * 0.32f, 0.04f + sn * 0.32f, 0.14f, 0.62f, ang - 90, 0xffd54f, 0.16f);
        }
        flat(SOFT, 0, 0.04f, 0.9f, 0.9f, 0, 0xffe082, 0.35f);
        d.additive(false);
        // arma atual com "pop"
        float pop = k < 0.2f ? Interpolation.swingOut.apply(k / 0.2f) : 1;
        float scl = ws[i] == WeaponType.SNIPER ? 0.03f : ws[i] == WeaponType.PISTOL ? 0.05f : 0.038f;
        painter.drawGun(d, ws[i], 0, x, y + size * 0.05f, 8 * MathUtils.sin(t * 2), size * scl * pop, al, 1);
        if (k < 0.3f) {
            float p = 1 - k / 0.3f;
            d.additive(true);
            flat(SOFT, 0, 0.05f, 0.7f, 0.7f, 0, 0xffffff, 0.8f * p);
            for (int j = 0; j < 8; j++) {
                float ang = j * 45 + 20;
                float c = MathUtils.cosDeg(ang), sn = MathUtils.sinDeg(ang);
                float r0 = 0.2f + (1 - p) * 0.3f;
                line(c * r0, 0.05f + sn * r0, c * (r0 + 0.12f * p), 0.05f + sn * (r0 + 0.12f * p), 0.03f, 0xfff59d, p);
            }
            d.additive(false);
        }
        // barra de progresso
        for (int j = 0; j < ws.length; j++) {
            boolean on = j <= i;
            float lx = -0.4f + j * 0.16f;
            part(RECT, on ? BOX : RECT, lx, -0.42f, 0.13f, 0.08f, 0, on ? (j == i ? 0xffeb3b : 0xffb300) : 0x37474f);
        }
        flush();
        text((i + 1) + "/" + ws.length, 0.44f, 0.42f, 0.38f, 0xffffff);
    }

    private void crown(float x, float y, float size, float deg, float t) {
        frame(x, y, size, deg);
        int gold = 0xffc107, goldL = 0xffe082;
        part(TRI, TRI, -0.24f, 0.12f, 0.22f, 0.34f, 8, gold);
        part(TRI, TRI, 0.24f, 0.12f, 0.22f, 0.34f, -8, gold);
        part(TRI, TRI, 0, 0.16f, 0.26f, 0.42f, 0, gold);
        part(CIRC, BALL, -0.27f, 0.3f, 0.09f, 0.09f, 0, goldL);
        part(CIRC, BALL, 0.27f, 0.3f, 0.09f, 0.09f, 0, goldL);
        part(CIRC, BALL, 0, 0.38f, 0.1f, 0.1f, 0, goldL);
        slab(0, -0.06f, 0.66f, 0.22f, 0, gold);
        flush();
        flat(CIRC, 0, -0.06f, 0.12f, 0.12f, 0, INK, 1);
        flat(CIRC, 0, -0.06f, 0.09f, 0.09f, 0, 0xe53935, 1);
        for (int i = -1; i <= 1; i += 2) {
            flat(CIRC, i * 0.2f, -0.06f, 0.09f, 0.09f, 0, INK, 1);
            flat(CIRC, i * 0.2f, -0.06f, 0.065f, 0.065f, 0, 0x29b6f6, 1);
        }
        flat(CIRC, -0.02f, -0.03f, 0.03f, 0.03f, 0, 0xffffff, 0.8f);
        // reflexo correndo
        float u = (t * 0.6f) % 1.6f - 0.3f;
        if (u > -0.3f && u < 1f) flat(RECT, -0.33f + u * 0.66f, 0.0f, 0.04f, 0.3f, 20, 0xffffff, 0.5f);
    }

    private void king(float x, float y, float size, float t) {
        frame(x, y, size, 0);
        float pulse = 0.5f + 0.5f * MathUtils.sin(t * 3);
        // coluna de luz
        d.additive(true);
        flat(RECT, 0, 0.12f, 0.34f, 1.0f, 0, 0xffeb3b, 0.12f + 0.08f * pulse);
        flat(RECT, 0, 0.12f, 0.16f, 1.0f, 0, 0xffffff, 0.08f + 0.06f * pulse);
        flat(SOFT, 0, -0.18f, 0.9f, 0.4f, 0, 0xffeb3b, 0.4f);
        // fagulhas subindo
        for (int i = 0; i < 6; i++) {
            float u = (t * 0.5f + hash(i)) % 1f;
            flat(CIRC, (hash(i + 9) - 0.5f) * 0.3f, -0.2f + u * 0.7f, 0.035f, 0.035f, 0, 0xfff59d, 1 - u);
        }
        d.additive(false);
        // morro
        part(HALF, HALF, 0, -0.38f, 1.2f, 0.44f, 0, 0x689f38);
        flush();
        flat(HALF, 0.12f, -0.4f, 0.8f, 0.34f, 0, 0x558b2f, 0.8f);
        flat(CIRC, -0.3f, -0.42f, 0.1f, 0.07f, 0, 0x8d8d8d, 1);
        flat(CIRC, 0.34f, -0.45f, 0.08f, 0.06f, 0, 0x8d8d8d, 1);
        for (int i = 0; i < 5; i++) {
            float lx = -0.36f + i * 0.18f;
            float ly = -0.38f + 0.2f * (1 - (lx * lx) / 0.36f);
            flat(TRI, lx, ly, 0.05f, 0.07f, 10 - i * 5, 0x33691e, 1);
        }
        // zona no topo
        flat(RING, 0, -0.17f, 0.62f + 0.06f * pulse, 0.14f + 0.02f * pulse, 0, 0xffeb3b, 0.5f + 0.4f * pulse);
        crown(x, y + size * (0.12f + MathUtils.sin(t * 2) * 0.035f), size * 0.62f, MathUtils.sin(t * 1.4f) * 5, t);
    }

    private void zombie(float x, float y, float size, float t) {
        frame(x, y, size, 0);
        float g = 0.5f + 0.5f * MathUtils.sin(t * 2.5f);
        glow(0, 0, 0.6f, 0x76ff03, 0.25f + 0.2f * g);
        // bolhas
        for (int i = 0; i < 6; i++) {
            float u = (t * 0.35f + hash(i)) % 1f;
            float bx = (hash(i + 3) - 0.5f) * 0.9f + MathUtils.sin(t * 2 + i) * 0.03f;
            flat(RING, bx, -0.4f + u * 0.9f, 0.06f + hash(i + 7) * 0.05f, 0.06f + hash(i + 7) * 0.05f, 0, 0xb2ff59, (1 - u) * 0.8f);
        }
        float sway = MathUtils.sin(t * 1.7f) * 7;
        frame(x, y, size * 0.95f, sway);
        int skin = 0x8bc34a, skinD = 0x689f38;
        part(CIRC, BALL, 0, 0.06f, 0.74f, 0.74f, 0, skin);
        part(RECT, BOX, 0.02f, -0.26f, 0.44f, 0.2f, 0, skinD);
        flush();
        flat(CIRC, 0.2f, 0.28f, 0.14f, 0.1f, 20, skinD, 0.9f);
        flat(CIRC, -0.26f, -0.06f, 0.09f, 0.12f, 0, skinD, 0.9f);
        // pontos de costura
        line(-0.28f, 0.28f, 0.08f, 0.38f, 0.022f, 0x33402a, 1);
        for (int i = 0; i < 5; i++) {
            float u = i / 4f;
            float lx = MathUtils.lerp(-0.26f, 0.06f, u), ly = MathUtils.lerp(0.285f, 0.375f, u);
            line(lx - 0.012f, ly - 0.04f, lx + 0.012f, ly + 0.04f, 0.018f, 0x33402a, 1);
        }
        // olho esbugalhado
        part(CIRC, BALL, -0.15f, 0.07f, 0.23f, 0.23f, 0, 0xfff9c4);
        // olho caido
        part(CIRC, BALL, 0.16f, 0.04f, 0.17f, 0.15f, 0, 0xffee58);
        flush();
        float look = MathUtils.sin(t * 1.3f) * 0.03f;
        flat(CIRC, -0.15f + look, 0.06f, 0.08f, 0.08f, 0, 0xc62828, 1);
        flat(CIRC, -0.15f + look, 0.06f, 0.035f, 0.035f, 0, INK, 1);
        flat(CIRC, 0.16f, 0.025f, 0.05f, 0.05f, 0, INK, 1);
        flat(RECT, 0.16f, 0.1f, 0.2f, 0.07f, -12, skinD, 1);
        line(0.06f, 0.08f, 0.26f, 0.12f, 0.02f, INK, 0.9f);
        // boca
        flat(RECT, 0.02f, -0.22f, 0.38f, 0.13f, -4, 0x2b0d0d, 1);
        for (int i = 0; i < 4; i++) {
            float lx = -0.12f + i * 0.09f;
            flat(TRI, lx, -0.175f, 0.06f, 0.07f, 180, 0xf5f0dc, 1);
            if (i < 3) flat(TRI, lx + 0.045f, -0.265f, 0.05f, 0.06f, 0, 0xf5f0dc, 1);
        }
        // baba
        float dp = (t * 0.7f) % 1f;
        flat(CIRC, 0.14f, -0.3f - dp * 0.25f, 0.05f, 0.07f, 0, 0x9ccc65, 1 - dp);
        flat(RECT, 0.14f, -0.29f - dp * 0.12f, 0.02f, dp * 0.22f, 0, 0x9ccc65, (1 - dp) * 0.8f);
    }

    private void royale(float x, float y, float size, float t) {
        frame(x, y, size, 0);
        // zona fechando
        float k = (t * 0.25f) % 1f;
        float r = 1.25f - 0.5f * Interpolation.pow2Out.apply(k);
        d.additive(true);
        flat(RING, 0, 0, r, r, 0, 0x7c4dff, 0.6f);
        flat(RING, 0, 0, r + 0.08f, r + 0.08f, 0, 0x40c4ff, 0.35f);
        d.additive(false);
        for (int i = 0; i < 12; i++) {
            float ang = i * 30 + t * 40;
            float rr = r / 2 + 0.05f + hash(i) * 0.08f;
            flat(CIRC, MathUtils.cosDeg(ang) * rr, MathUtils.sinDeg(ang) * rr, 0.03f, 0.03f, 0, 0xb388ff, 0.7f);
        }
        // paraquedas com caixa balancando
        float swing = MathUtils.sin(t * 1.6f) * 12;
        float fall = MathUtils.sin(t * 0.9f) * 0.03f;
        frame(x, y + size * (0.24f + fall), size, swing);
        // cordas
        for (int i = -1; i <= 1; i += 2) {
            line(i * 0.37f, -0.01f, i * 0.1f, -0.42f, 0.014f, INK, 1);
            line(i * 0.13f, -0.01f, i * 0.06f, -0.42f, 0.012f, INK, 0.9f);
        }
        part(HALF, HALF, 0, 0.08f, 0.8f, 0.36f, 0, 0xff7043);
        flush();
        flat(HALF, 0, 0.08f, 0.32f, 0.36f, 0, 0xffffff, 1);
        flat(HALF, 0, 0.08f, 0.1f, 0.36f, 0, 0xff7043, 1);
        flat(RECT, 0, -0.09f, 0.8f, 0.03f, 0, 0xbf360c, 1);
        flat(SOFT, -0.18f, 0.18f, 0.24f, 0.1f, 25, 0xffffff, 0.35f);
        // caixa de suprimentos
        slab(0, -0.56f, 0.32f, 0.26f, 0, 0x8d6e63);
        flush();
        line(-0.15f, -0.68f, 0.15f, -0.44f, 0.025f, 0x5d4037, 1);
        flat(RECT, 0, -0.56f, 0.32f, 0.03f, 0, 0x5d4037, 1);
        flat(RECT, 0, -0.56f, 0.1f, 0.1f, 0, 0xffffff, 1);
        flat(RECT, 0, -0.56f, 0.03f, 0.08f, 0, 0xe53935, 1);
        flat(RECT, 0, -0.56f, 0.08f, 0.03f, 0, 0xe53935, 1);
    }

    private void duel(float x, float y, float size, float t) {
        frame(x, y, size, 0);
        // por do sol do faroeste: sol meio escondido no horizonte, montanhas e cactos
        glow(0, -0.08f, 0.55f, 0xff6d00, 0.5f);
        part(HALF, HALF, 0, 0.06f, 0.62f, 0.31f, 0, 0xffa726);
        flush();
        flat(HALF, 0, 0.04f, 0.44f, 0.24f, 0, 0xffcc80, 0.5f);
        for (int i = 0; i < 3; i++) flat(RECT, 0, -0.05f + i * 0.055f, 0.66f, 0.02f, 0, 0xff7043, 0.55f);
        flat(TRI, -0.36f, -0.0f, 0.42f, 0.2f, 0, 0x4e342e, 1);
        flat(TRI, 0.4f, -0.02f, 0.36f, 0.16f, 0, 0x4e342e, 1);
        flat(RECT, 0, -0.12f, 1.24f, 0.05f, 0, 0x6d4c41, 1);
        for (int i = -1; i <= 1; i += 2) {
            float cx = i * 0.52f;
            part(PILL, PILL, cx, 0.06f, 0.36f, 0.09f, 90, 0x43a047);
            part(PILL, PILL, cx - 0.07f, 0.1f, 0.14f, 0.06f, 90, 0x43a047);
            part(PILL, PILL, cx + 0.07f, 0.14f, 0.12f, 0.06f, 90, 0x43a047);
            part(RECT, RECT, cx - 0.045f, 0.05f, 0.06f, 0.04f, 0, 0x43a047);
            part(RECT, RECT, cx + 0.045f, 0.09f, 0.06f, 0.04f, 0, 0x43a047);
        }
        flush();
        for (int i = -1; i <= 1; i += 2) flat(RECT, i * 0.52f - 0.015f, 0.07f, 0.015f, 0.26f, 0, 0x2e7d32, 1);
        // revolveres
        float k = Math.abs(MathUtils.sin(t * 2.5f));
        float off = 0.36f - 0.04f * k;
        float gs = size * 0.027f;
        painter.drawGun(d, WeaponType.MAGNUM, 0, x - off * size, y - size * 0.22f, 8, gs, al, 1);
        painter.drawGun(d, WeaponType.MAGNUM, 0, x + off * size, y - size * 0.22f, 8, gs, al, -1);
        // balas se chocando
        frame(x, y, size, 0);
        float p = (t * 0.8f) % 1f;
        if (p < 0.35f) {
            float u = p / 0.35f;
            float bx = MathUtils.lerp(0.14f, 0.01f, u);
            for (int i = -1; i <= 1; i += 2) {
                flat(PILL, i * bx, -0.17f, 0.07f, 0.03f, 0, 0xffd54f, 1);
                line(i * (bx + 0.03f), -0.17f, i * (bx + 0.14f), -0.17f, 0.012f, 0xffffff, 0.5f);
            }
        } else if (p < 0.6f) {
            float u = (p - 0.35f) / 0.25f;
            d.additive(true);
            flat(SOFT, 0, -0.17f, 0.5f * (1 - u) + 0.1f, 0.5f * (1 - u) + 0.1f, 0, 0xffeb3b, 1 - u);
            for (int i = 0; i < 8; i++) {
                float ang = i * 45 + 22;
                float r0 = 0.04f + u * 0.2f;
                float c = MathUtils.cosDeg(ang), sn = MathUtils.sinDeg(ang);
                line(c * r0, -0.17f + sn * r0, c * (r0 + 0.06f), -0.17f + sn * (r0 + 0.06f), 0.02f, 0xfff59d, 1 - u);
            }
            d.additive(false);
        }
    }

    private void hide(float x, float y, float size, float t) {
        frame(x, y, size, 0);
        // interrogacoes subindo
        for (int i = 0; i < 3; i++) {
            float u = (t * 0.4f + i / 3f) % 1f;
            float lx = -0.35f + i * 0.35f + MathUtils.sin(t * 2 + i) * 0.04f;
            float keep = al;
            al = keep * MathUtils.sin(u * MathUtils.PI) * 0.8f;
            text("?", lx, 0.05f + u * 0.4f, 0.8f, 0xffffff);
            al = keep;
        }
        // caixa
        int wood = 0xb07a3c, woodD = 0x7a4f22;
        slab(0, -0.24f, 0.62f, 0.4f, 0, wood);
        flush();
        flat(RECT, 0, -0.24f, 0.62f, 0.035f, 0, woodD, 1);
        flat(RECT, -0.27f, -0.24f, 0.05f, 0.4f, 0, woodD, 1);
        flat(RECT, 0.27f, -0.24f, 0.05f, 0.4f, 0, woodD, 1);
        line(-0.24f, -0.42f, 0.24f, -0.06f, 0.04f, woodD, 1);
        // tampa levantando com olhos
        float lift = Math.max(0, MathUtils.sin(t * 1.3f)) * 0.08f;
        flat(RECT, 0, -0.03f + lift / 2, 0.6f, lift + 0.02f, 0, 0x120c08, 1);
        if (lift > 0.025f) {
            boolean blink = (t % 2.3f) < 0.12f;
            float look = MathUtils.sin(t * 1.7f) * 0.025f;
            for (int i = -1; i <= 1; i += 2) {
                flat(CIRC, i * 0.08f, -0.03f + lift * 0.55f, 0.07f, blink ? 0.015f : Math.min(0.06f, lift * 0.8f), 0, 0xffffff, 1);
                if (!blink) flat(CIRC, i * 0.08f + look, -0.03f + lift * 0.5f, 0.03f, 0.03f, 0, INK, 1);
            }
        }
        part(RECT, RECT, -0.01f, 0.01f + lift, 0.68f, 0.09f, lift * 40, 0x9c6b33);
        flush();
        flat(RECT, -0.01f, 0.03f + lift, 0.66f, 0.02f, lift * 40, 0xc8935a, 1);
        // lupa passando
        float mx = MathUtils.sin(t * 1.5f) * 0.32f, my = 0.2f + MathUtils.cos(t * 2.1f) * 0.1f;
        frame(x + mx * size, y + my * size, size * 0.55f, -35 + MathUtils.sin(t * 1.5f) * 8);
        slab(0, -0.52f, 0.13f, 0.42f, 0, 0x5d4037);
        part(CIRC, BALL, 0, 0, 0.66f, 0.66f, 0, 0x90a4ae);
        flush();
        flat(CIRC, 0, 0, 0.5f, 0.5f, 0, 0xb3e5fc, 0.75f);
        flat(CIRC, 0.05f, -0.05f, 0.4f, 0.4f, 0, 0x81d4fa, 0.5f);
        flat(RECT, -0.1f, 0.1f, 0.06f, 0.24f, 45, 0xffffff, 0.8f);
        flat(CIRC, 0.12f, -0.12f, 0.05f, 0.05f, 0, 0xffffff, 0.7f);
    }
}
