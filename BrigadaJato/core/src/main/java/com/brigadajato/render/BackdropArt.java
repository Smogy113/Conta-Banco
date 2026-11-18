package com.brigadajato.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.brigadajato.world.Theme;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Pinta as camadas do fundo em texturas de alta resolucao, pixel a pixel: relevo com luz do sol
 * vindo de um lado, brilho na crista, neblina de profundidade, granulado leve e detalhes (neve,
 * arvores, cactos, predios com janelas, crateras, estalactites). As texturas repetem na horizontal.
 */
public class BackdropArt implements Disposable {
    public static final int TW = 1024, TH = 512;

    /** uma camada pronta */
    public static class Layer {
        public Texture tex;
        /** cor da ultima linha (preenche o que fica abaixo da textura) */
        public int bottom;
        /** tamanho no mundo */
        public float w, h;
        /** fator de paralaxe e altura da base (fracao da altura do mapa) */
        public float k, base;
        /** a textura tambem tem teto (caverna) */
        public boolean ceiling;
        public int top;
        /** fracao da altura (a partir de baixo) totalmente opaca, e onde comeca o conteudo (acima e transparente) */
        public float solidFrac = 0, topFrac = 1;
        /** imagem pronta esperando virar textura (gerada fora da thread do OpenGL) */
        Pixmap pending;
    }

    /** gerar so as imagens (sem OpenGL): as texturas sao criadas depois em {@link #upload()} */
    private final boolean defer;
    public String cacheKey;

    public final Layer[] layers;
    private final Random r;
    private final Theme t;
    private final int haze;

    // ------------------------------------------------------------------ ruido periodico

    private float[] table(int n) {
        float[] v = new float[n];
        for (int i = 0; i < n; i++) v[i] = r.nextFloat();
        return v;
    }

    private static float smooth(float[] tab, float x) {
        int n = tab.length;
        float fx = x - (float) Math.floor(x);
        int i0 = ((int) Math.floor(x) % n + n) % n, i1 = (i0 + 1) % n;
        float tt = fx * fx * (3 - 2 * fx);
        return tab[i0] + (tab[i1] - tab[i0]) * tt;
    }

    /** fbm periodico em [0,1): soma de oitavas com periodos inteiros. */
    private float[] fbm(int width, int baseCells, int octaves, float gain, boolean ridged) {
        float[] out = new float[width];
        float amp = 1, total = 0;
        for (int o = 0; o < octaves; o++) {
            int cells = baseCells << o;
            float[] tab = table(cells);
            for (int x = 0; x < width; x++) {
                float v = smooth(tab, x / (float) width * cells);
                if (ridged) v = 1 - Math.abs(v * 2 - 1);
                out[x] += v * amp;
            }
            total += amp;
            amp *= gain;
        }
        for (int x = 0; x < width; x++) out[x] /= total;
        return out;
    }

    // ------------------------------------------------------------------ pixels

    private int[] px;

    private static int mix(int a, int b, float k) {
        return TileArt.mix(a, b, k);
    }

    private static int scale(int c, float k) {
        int rr = Math.min(255, Math.max(0, (int) (((c >> 16) & 0xff) * k)));
        int gg = Math.min(255, Math.max(0, (int) (((c >> 8) & 0xff) * k)));
        int bb = Math.min(255, Math.max(0, (int) ((c & 0xff) * k)));
        return (rr << 16) | (gg << 8) | bb;
    }

    /** escreve (y medido de baixo para cima), com alfa para suavizar bordas */
    private void put(int x, int y, int rgb, float alpha) {
        if (y < 0 || y >= TH) return;
        x = ((x % TW) + TW) % TW;
        int i = (TH - 1 - y) * TW + x;
        if (alpha >= 0.999f) {
            px[i] = (rgb << 8) | 0xff;
            return;
        }
        if (alpha <= 0) return;
        int old = px[i];
        int oa = old & 0xff;
        if (oa == 0) {
            px[i] = (rgb << 8) | (int) (alpha * 255);
            return;
        }
        int oc = old >>> 8;
        float na = Math.min(1, oa / 255f + alpha * (1 - oa / 255f));
        px[i] = (mix(oc, rgb, alpha) << 8) | (int) (na * 255);
    }

    /** Mede a parte opaca e a parte vazia da textura (para desenhar so o necessario). */
    private void measure(Layer l) {
        final int margin = 16; // folga por causa do mipmap e do filtro linear
        int topRow = TH;
        for (int y = 0; y < TH && topRow == TH; y++)
            for (int x = 0; x < TW; x++)
                if ((px[y * TW + x] & 0xff) != 0) {
                    topRow = y;
                    break;
                }
        int solidRow = TH;
        for (int y = TH - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < TW; x++)
                if ((px[y * TW + x] & 0xff) != 0xff) {
                    full = false;
                    break;
                }
            if (!full) break;
            solidRow = y;
        }
        l.topFrac = Math.min(1f, (TH - Math.max(0, topRow - margin)) / (float) TH);
        l.solidFrac = Math.max(0f, (TH - Math.min(TH, solidRow + margin)) / (float) TH);
        if (l.solidFrac > l.topFrac) l.solidFrac = l.topFrac;
    }

    private Texture build(Layer l) {
        measure(l);
        Pixmap pm = pixmap();
        if (defer) {
            l.pending = pm;
            return null;
        }
        return texture(pm);
    }

    /** Cria as texturas das imagens geradas em segundo plano (chamar na thread do OpenGL). */
    public void upload() {
        for (Layer l : layers)
            if (l.pending != null) {
                l.tex = texture(l.pending);
                l.pending = null;
            }
    }

    private static Texture texture(Pixmap pm) {
        Texture tex = new Texture(pm, true);
        tex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        tex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge);
        pm.dispose();
        return tex;
    }

    private Pixmap pixmap() {
        Pixmap pm = new Pixmap(TW, TH, Pixmap.Format.RGBA8888);
        ByteBuffer buf = pm.getPixels();
        byte[] bytes = new byte[TW * TH * 4];
        for (int i = 0; i < px.length; i++) {
            int v = px[i];
            bytes[i * 4] = (byte) (v >>> 24);
            bytes[i * 4 + 1] = (byte) (v >>> 16);
            bytes[i * 4 + 2] = (byte) (v >>> 8);
            bytes[i * 4 + 3] = (byte) v;
        }
        buf.position(0);
        buf.put(bytes);
        buf.position(0);
        return pm;
    }

    /**
     * Terreno: preenche abaixo de h[x] com sombreamento por inclinacao (sol vindo da esquerda),
     * brilho na crista, escurecimento para baixo e neblina.
     */
    private void terrain(float[] h, int color, float fog, float rim, int snowAbove, int grainSeed) {
        Random g = new Random(grainSeed);
        for (int x = 0; x < TW; x++) {
            float hx = h[x];
            // inclinacao media numa janela larga: luz suave, sem listras por coluna
            float slope = (h[(x + 8) % TW] - h[(x - 8 + TW) % TW]) / 16f;
            float fine = (h[(x + 2) % TW] - h[(x - 2 + TW) % TW]) / 4f;
            float light = Math.max(-1, Math.min(1, -slope * 0.45f));
            float lightTop = Math.max(-1, Math.min(1, -fine * 0.3f));
            int top = (int) Math.ceil(hx);
            for (int y = 0; y < Math.min(TH, top + 1); y++) {
                float depth = hx - y; // distancia da crista
                float a = Math.min(1, Math.max(0, depth + 0.5f));
                if (a <= 0) continue;
                float near = Math.max(0, 1 - depth / 60f);
                float shade = 0.84f + 0.16f * light * Math.max(0, 1 - depth / 220f) + 0.08f * lightTop * near
                        - 0.16f * Math.min(1, depth / (TH * 0.9f));
                shade += (g.nextFloat() - 0.5f) * 0.035f;
                int c = scale(color, shade);
                if (depth < rim) c = mix(c, mix(color, 0xffffff, 0.45f), (1 - depth / rim) * (0.35f + 0.4f * Math.max(0, light)));
                if (snowAbove > 0 && hx > snowAbove + 15) {
                    float snowLine = snowAbove + 18 * (float) Math.sin(x * 0.031) + 8 * (float) Math.sin(x * 0.083);
                    float cover = Math.min(1, (y - snowLine) / 30f);
                    if (cover > 0) {
                        int snow = scale(0xf2f6ff, 0.78f + 0.3f * light);
                        c = mix(c, snow, 0.85f * cover);
                    }
                }
                float f = fog * (0.55f + 0.45f * (1 - Math.min(1, y / Math.max(1f, hx))));
                c = mix(c, haze, Math.min(0.85f, f));
                put(x, y, c, a);
            }
        }
    }

    private void blob(float cx, float cy, float rad, int color, float fog) {
        int x0 = (int) (cx - rad - 1), x1 = (int) (cx + rad + 1), y0 = (int) (cy - rad - 1), y1 = (int) (cy + rad + 1);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                float dx = x - cx, dy = y - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = Math.min(1, Math.max(0, rad - d + 0.5f));
                if (a <= 0) continue;
                float light = (-dx * 0.6f + dy * 0.8f) / rad; // luz de cima-esquerda
                int c = scale(color, 0.8f + 0.3f * light);
                c = mix(c, haze, fog);
                put(x, y, c, a);
            }
    }

    private void rect(float x0, float y0, float w, float h, int color, float fog) {
        for (int y = (int) y0; y < (int) (y0 + h); y++)
            for (int x = (int) x0; x < (int) (x0 + w); x++) put(x, y, mix(color, haze, fog), 1);
    }

    private void tree(float x, float ground, float size, int color, float fog, boolean pine) {
        int trunk = scale(mix(color, 0x3e2723, 0.6f), 0.7f);
        rect(x - size * 0.08f, ground - 2, size * 0.16f, size * 0.7f, trunk, fog);
        if (pine) {
            for (int k = 0; k < 3; k++) {
                float w = size * (0.75f - k * 0.18f), base = ground + size * (0.3f + k * 0.32f);
                for (int y = 0; y < size * 0.55f; y++) {
                    float half = w * (1 - y / (size * 0.55f));
                    for (int xx = (int) (x - half); xx <= (int) (x + half); xx++) {
                        float light = (x - xx) / Math.max(1, w);
                        put(xx, (int) (base + y), mix(scale(color, 0.85f + 0.3f * light), haze, fog), 1);
                    }
                }
            }
        } else {
            float cy = ground + size * 0.8f;
            blob(x, cy, size * 0.42f, color, fog);
            blob(x - size * 0.3f, cy - size * 0.14f, size * 0.3f, scale(color, 0.92f), fog);
            blob(x + size * 0.3f, cy - size * 0.1f, size * 0.32f, scale(color, 0.96f), fog);
            blob(x - size * 0.05f, cy + size * 0.2f, size * 0.26f, scale(color, 1.06f), fog);
        }
    }

    private void cactus(float x, float ground, float size, int color, float fog) {
        float w = size * 0.16f;
        for (int y = 0; y < size; y++)
            for (int xx = (int) (x - w); xx <= (int) (x + w); xx++) {
                float l = (x - xx) / w;
                put(xx, (int) (ground + y), mix(scale(color, 0.85f + 0.25f * l), haze, fog), 1);
            }
        blob(x, ground + size, w, color, fog);
        for (int s = -1; s <= 1; s += 2) {
            float ay = ground + size * (s < 0 ? 0.45f : 0.55f);
            float ax = x + s * size * 0.32f;
            rect(Math.min(x, ax), ay, Math.abs(ax - x), w * 1.2f, scale(color, 0.9f), fog);
            rect(ax - w * 0.8f, ay, w * 1.6f, size * 0.3f, scale(color, 0.95f), fog);
            blob(ax, ay + size * 0.3f, w * 0.8f, color, fog);
        }
    }

    /** linha de predios com janelas */
    private void skyline(int color, float fog, float minH, float maxH, boolean lit, float windowChance, int windowColor) {
        int x = 0;
        while (x < TW) {
            int w = 50 + r.nextInt(90);
            if (x + w > TW) w = TW - x;
            float h = minH + r.nextFloat() * (maxH - minH);
            int c = scale(color, 0.9f + r.nextFloat() * 0.2f);
            for (int y = 0; y < h; y++)
                for (int xx = x; xx < x + w; xx++) {
                    float side = xx < x + 5 ? 1.12f : (xx > x + w - 6 ? 0.82f : 1f);
                    float grad = 1 - 0.12f * (1 - y / h);
                    put(xx, y, mix(scale(c, side * grad), haze, fog * (0.6f + 0.4f * (1 - y / h))), 1);
                }
            // telhado: antena ou caixa d'agua
            if (r.nextFloat() < 0.4f) rect(x + w * 0.6f, h, 2, 18 + r.nextInt(30), scale(c, 0.8f), fog);
            if (r.nextFloat() < 0.25f) rect(x + w * 0.2f, h, w * 0.25f, 10, scale(c, 0.85f), fog);
            rect(x, h - 4, w, 4, scale(c, 1.15f), fog);
            // janelas
            for (int wy = 14; wy < h - 16; wy += 22)
                for (int wx = x + 10; wx < x + w - 14; wx += 18) {
                    if (r.nextFloat() > windowChance) continue;
                    int wc = lit ? mix(windowColor, 0xffffff, r.nextFloat() * 0.3f) : scale(c, 0.7f);
                    rect(wx, wy, 8, 12, wc, lit ? fog * 0.3f : fog);
                }
            x += w + r.nextInt(8);
        }
    }

    private void craters(float[] h, int color, float fog) {
        for (int k = 0; k < 14; k++) {
            float cx = r.nextFloat() * TW;
            float top = h[(int) cx % TW];
            float cy = top * (0.25f + r.nextFloat() * 0.6f);
            float rw = 18 + r.nextFloat() * 40, rh = rw * 0.35f;
            for (int y = (int) (cy - rh); y <= cy + rh; y++)
                for (int x = (int) (cx - rw); x <= cx + rw; x++) {
                    float dx = (x - cx) / rw, dy = (y - cy) / rh;
                    float d = dx * dx + dy * dy;
                    if (d > 1 || y > h[((x % TW) + TW) % TW] - 3) continue;
                    int c = d > 0.7f ? scale(color, 1.12f) : scale(color, 0.72f + 0.2f * dy);
                    put(x, y, mix(c, haze, fog), 1);
                }
        }
    }

    private void stalactites(int color, float fog, float maxLen) {
        // teto da textura (de cima para baixo)
        float[] ceil = fbm(TW, 6, 4, 0.5f, false);
        for (int x = 0; x < TW; x++) {
            float len = 20 + ceil[x] * 40;
            for (int y = TH - 1; y > TH - 1 - len; y--) put(x, y, mix(scale(color, 0.85f), haze, fog), 1);
        }
        for (int k = 0; k < 38; k++) {
            float cx = r.nextFloat() * TW, len = 30 + r.nextFloat() * maxLen, w = 6 + r.nextFloat() * 16;
            for (int y = 0; y < len; y++) {
                float half = w * (1 - y / len);
                for (int x = (int) (cx - half); x <= cx + half; x++) {
                    float l = (cx - x) / Math.max(1, w);
                    put(x, TH - 40 - y, mix(scale(color, 0.85f + 0.25f * l), haze, fog), 1);
                }
            }
        }
    }

    // ------------------------------------------------------------------ montagem por tema

    public BackdropArt(Theme t, String seedKey) {
        this(t, seedKey, false);
    }

    public BackdropArt(Theme t, String seedKey, boolean defer) {
        this.defer = defer;
        this.t = t;
        this.r = new Random(seedKey.hashCode() * 7919L + t.ordinal() * 31L);
        this.haze = t.skyBottom;
        Theme.Bg bg = t.bg;
        boolean snowy = bg == Theme.Bg.ICE_PEAKS || bg == Theme.Bg.ISLANDS || t == Theme.TRAINING;
        Layer far = new Layer(), mid = new Layer(), near = new Layer();
        far.k = 0.15f;
        mid.k = 0.32f;
        near.k = 0.55f;
        far.base = 0.06f;
        mid.base = 0.0f;
        near.base = -0.06f;
        far.w = 1900;
        far.h = 950;
        mid.w = 1500;
        mid.h = 750;
        near.w = 1200;
        near.h = 600;

        // ---- camada de longe
        px = new int[TW * TH];
        switch (bg) {
            case BUILDINGS:
                skyline(t.far, 0.45f, 120, 380, false, 0.5f, 0);
                break;
            case CITY_NIGHT:
                skyline(t.far, 0.25f, 160, 440, true, 0.35f, 0xffe9a8);
                break;
            case MARKET:
                skyline(mix(t.far, 0xffffff, 0.2f), 0.4f, 90, 220, false, 0.6f, 0);
                break;
            case OCEAN: {
                float[] h = fbm(TW, 5, 4, 0.5f, false);
                for (int x = 0; x < TW; x++) h[x] = Math.max(0, (h[x] - 0.55f) * 260);
                terrain(h, t.far, 0.45f, 2, 0, 1);
                break;
            }
            case VOLCANO: {
                float[] h = fbm(TW, 4, 5, 0.5f, true);
                for (int x = 0; x < TW; x++) {
                    float dx = Math.abs(x - TW * 0.5f) / (TW * 0.5f);
                    float cone = Math.max(0, 1 - dx * 1.7f);
                    h[x] = 120 + h[x] * 90 + cone * 300;
                    if (cone > 0.82f) h[x] -= (cone - 0.82f) * 300; // cratera
                }
                terrain(h, t.far, 0.25f, 3, 0, 2);
                // rios de lava descendo
                for (int k = 0; k < 4; k++) {
                    float x = TW * 0.5f + (k - 1.5f) * 30;
                    for (int y = 0; y < 330; y++) {
                        float xx = x + (float) Math.sin(y * 0.05 + k) * 8 + (k - 1.5f) * y * 0.25f;
                        if (y < h[((int) xx % TW + TW) % TW] - 6) put((int) xx, 420 - y, mix(0xff6d00, 0xffd180, r.nextFloat() * 0.4f), 0.85f);
                    }
                }
                break;
            }
            case UNDERWATER: {
                float[] h = fbm(TW, 6, 5, 0.55f, true);
                for (int x = 0; x < TW; x++) h[x] = 80 + h[x] * 300;
                terrain(h, t.far, 0.55f, 2, 0, 3);
                break;
            }
            case CAVE:
                stalactites(t.far, 0.4f, 160);
                far.ceiling = true;
                break;
            case MOON: {
                float[] h = fbm(TW, 5, 5, 0.5f, false);
                for (int x = 0; x < TW; x++) h[x] = 90 + h[x] * 200;
                terrain(h, t.far, 0.2f, 2, 0, 4);
                craters(h, t.far, 0.2f);
                break;
            }
            default: {
                float[] h = fbm(TW, 5, 5, 0.45f, true);
                for (int x = 0; x < TW; x++) h[x] = 140 + h[x] * 300;
                terrain(h, t.far, 0.42f, 3, snowy || bg == Theme.Bg.MOUNTAINS ? 370 : 0, 5);
                break;
            }
        }
        if (bg != Theme.Bg.INDOOR_WOOD && bg != Theme.Bg.INDOOR_LAB && bg != Theme.Bg.INDOOR_SCHOOL) {
            far.tex = build(far);
            far.bottom = mix(t.far, haze, 0.4f);
        }

        // ---- camada do meio
        px = new int[TW * TH];
        switch (bg) {
            case BUILDINGS:
                skyline(t.mid, 0.2f, 100, 300, true, 0.35f, 0xffeb3b);
                break;
            case CITY_NIGHT:
                skyline(t.mid, 0.12f, 140, 360, true, 0.55f, 0xffcc66);
                break;
            case MARKET:
                skyline(mix(t.mid, 0xfff3e0, 0.35f), 0.15f, 110, 240, false, 0.7f, 0);
                break;
            case OCEAN:
                break;
            case CAVE: {
                float[] h = fbm(TW, 6, 5, 0.5f, true);
                for (int x = 0; x < TW; x++) h[x] = 60 + h[x] * 160;
                terrain(h, t.mid, 0.25f, 2, 0, 6);
                stalactites(t.mid, 0.25f, 120);
                mid.ceiling = true;
                break;
            }
            default: {
                float[] h = fbm(TW, 4, 5, 0.5f, false);
                for (int x = 0; x < TW; x++) h[x] = 90 + h[x] * 210;
                terrain(h, t.mid, 0.22f, 3, bg == Theme.Bg.ICE_PEAKS ? 230 : 0, 7);
                if (bg == Theme.Bg.MOON) craters(h, t.mid, 0.1f);
                if (bg == Theme.Bg.FOREST || bg == Theme.Bg.ISLANDS || (bg == Theme.Bg.MOUNTAINS && t.ambient != Theme.Amb.DUST)) {
                    for (int x = 0; x < TW; x += 9 + r.nextInt(14)) tree(x, h[x] - 4, 26 + r.nextFloat() * 26, mix(t.mid, 0x2e7d32, 0.4f), 0.2f, bg == Theme.Bg.ISLANDS);
                }
                if (bg == Theme.Bg.ICE_PEAKS)
                    for (int x = 0; x < TW; x += 14 + r.nextInt(30)) tree(x, h[x] - 4, 30 + r.nextFloat() * 30, mix(t.mid, 0x1b5e20, 0.45f), 0.25f, true);
                if (bg == Theme.Bg.UNDERWATER)
                    for (int k = 0; k < 20; k++) {
                        float x = r.nextFloat() * TW;
                        blob(x, h[(int) x] + 6, 8 + r.nextFloat() * 12, r.nextBoolean() ? 0xff8a65 : 0xf06292, 0.35f);
                    }
                break;
            }
        }
        if (bg != Theme.Bg.OCEAN && bg != Theme.Bg.INDOOR_WOOD && bg != Theme.Bg.INDOOR_LAB && bg != Theme.Bg.INDOOR_SCHOOL) {
            mid.tex = build(mid);
            mid.bottom = mix(t.mid, haze, 0.18f);
        }

        // ---- camada de perto
        px = new int[TW * TH];
        boolean nearTex = true;
        switch (bg) {
            case MOUNTAINS:
            case FOREST:
            case ISLANDS:
            case ICE_PEAKS:
            case VOLCANO:
            case MOON: {
                float[] h = fbm(TW, 3, 4, 0.5f, false);
                for (int x = 0; x < TW; x++) h[x] = 50 + h[x] * 110;
                terrain(h, t.near, 0.08f, 3, bg == Theme.Bg.ICE_PEAKS ? 1 : 0, 8);
                if (bg == Theme.Bg.MOUNTAINS && t.ambient == Theme.Amb.DUST) {
                    for (int x = 30; x < TW; x += 90 + r.nextInt(120)) cactus(x, h[x] - 6, 60 + r.nextFloat() * 50, mix(t.near, 0x558b2f, 0.55f), 0.05f);
                } else if (bg == Theme.Bg.VOLCANO) {
                    for (int x = 30; x < TW; x += 120 + r.nextInt(160)) {
                        int c = scale(t.near, 0.7f);
                        rect(x, h[x] - 4, 6, 70, c, 0.05f);
                        rect(x + 3, h[x] + 40, 26, 4, c, 0.05f);
                        rect(x - 18, h[x] + 30, 20, 4, c, 0.05f);
                    }
                } else if (bg != Theme.Bg.MOON) {
                    boolean pine = bg == Theme.Bg.ICE_PEAKS;
                    for (int x = 20; x < TW; x += 40 + r.nextInt(70)) tree(x, h[x] - 6, 60 + r.nextFloat() * 50, mix(t.near, pine ? 0x1b5e20 : 0x33691e, 0.45f), 0.05f, pine);
                } else craters(h, t.near, 0.05f);
                break;
            }
            case UNDERWATER: {
                float[] h = fbm(TW, 5, 4, 0.5f, true);
                for (int x = 0; x < TW; x++) h[x] = 40 + h[x] * 100;
                terrain(h, t.near, 0.15f, 2, 0, 9);
                break;
            }
            default:
                nearTex = false;
                break;
        }
        if (nearTex) {
            near.tex = build(near);
            near.bottom = t.near;
        }
        px = null;
        layers = new Layer[]{far, mid, near};
    }

    @Override
    public void dispose() {
        for (Layer l : layers) {
            if (l.tex != null) l.tex.dispose();
            if (l.pending != null) l.pending.dispose();
            l.tex = null;
            l.pending = null;
        }
    }
}
