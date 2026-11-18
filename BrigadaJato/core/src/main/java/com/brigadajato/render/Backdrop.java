package com.brigadajato.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.brigadajato.world.Theme;

import java.util.Random;

/** Fundo com paralaxe de um tema: ceu (ou parede), astros, nuvens e tres camadas de silhuetas. */
public class Backdrop implements Disposable {
    public final Theme theme;
    private final Texture sky;
    private final Array<float[]> far = new Array<>(), mid = new Array<>(), near = new Array<>(), clouds = new Array<>(), stars = new Array<>();
    private static final float PERIOD = 2400;
    private final float worldH;
    public final boolean indoor;
    /** 0 baixa, 1 media, 2+ alta (quantas camadas desenhar) */
    public static int quality = 2;
    private final BackdropArt art;
    /** camada atual tem textura pintada (pula as silhuetas simples) */
    private boolean tex;

    public Backdrop(Theme t, String seedKey, float worldH) {
        this.theme = t;
        this.worldH = worldH;
        Theme.Bg bg = t.bg;
        indoor = bg == Theme.Bg.INDOOR_WOOD || bg == Theme.Bg.INDOOR_LAB || bg == Theme.Bg.INDOOR_SCHOOL || bg == Theme.Bg.CAVE
                || bg == Theme.Bg.UNDERWATER;
        Pixmap pm = new Pixmap(1, 128, Pixmap.Format.RGBA8888);
        for (int y = 0; y < 128; y++) pm.drawPixel(0, y, TileArt.rgba(TileArt.mix(t.skyTop, t.skyBottom, y / 127f), 1));
        sky = new Texture(pm);
        sky.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        Random r = new Random(seedKey.hashCode() * 31L + t.ordinal());
        art = ArtCache.acquire(t, seedKey);
        for (int i = 0; i < 6; i++) far.add(new float[]{i * PERIOD / 6 + r.nextFloat() * 150, r.nextFloat(), 520 + r.nextFloat() * 380, 230 + r.nextFloat() * 220});
        for (int i = 0; i < 8; i++) mid.add(new float[]{i * PERIOD / 8 + r.nextFloat() * 120, r.nextFloat(), 300 + r.nextFloat() * 220, 90 + r.nextFloat() * 140});
        for (int i = 0; i < 12; i++) near.add(new float[]{i * PERIOD / 12 + r.nextFloat() * 80, r.nextFloat(), 40 + r.nextFloat() * 50, 70 + r.nextFloat() * 110});
        for (int i = 0; i < 8; i++) clouds.add(new float[]{r.nextFloat() * PERIOD, 120 + r.nextFloat() * 260, 34 + r.nextFloat() * 30, 8 + r.nextFloat() * 14});
        for (int i = 0; i < 140; i++) stars.add(new float[]{r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 1.6f, r.nextFloat() * 6});
    }

    private static float wrap(float v, float p) {
        v %= p;
        if (v < 0) v += p;
        return v;
    }

    /**
     * @param night 0 dia .. 1 noite (so muda temas ao ar livre)
     * @param sunPos 0..1 posicao do sol/lua no arco do ceu
     */
    public void draw(Draw d, float camX, float camY, float minX, float minY, float vw, float vh, float time, float night, float sunPos) {
        Theme t = theme;
        // ate onde cada camada pintada e totalmente opaca: o que fica atras dela nao precisa ser desenhado
        float coverAll = minY;
        for (int i = 2; i >= 0; i--) {
            coverAbove[i] = coverAll;
            if (!drawsLayer(i)) continue;
            BackdropArt.Layer l = art.layers[i];
            if (l.tex == null || l.solidFrac <= 0) continue;
            float baseY = (camY - worldH * 0.5f) * (1 - KS[i] * 0.7f) + worldH * (0.18f - i * 0.06f);
            float y0 = baseY - l.h * 0.12f;
            coverAll = Math.max(coverAll, y0 + l.h * l.solidFrac);
        }
        coverAll = Math.min(coverAll, minY + vh);
        d.color(1, 1, 1, 1);
        if (coverAll < minY + vh) {
            float f = (coverAll - minY) / vh;
            d.batch.disableBlending();
            d.batch.draw(sky, minX, coverAll, vw, vh * (1 - f), 0, 1 - f, 1, 0);
            d.batch.enableBlending();
        }
        com.brigadajato.Perf.mark(d.batch, "b.sky");
        if (!indoor && night > 0.01f && t.bg != Theme.Bg.MOON && t.bg != Theme.Bg.CITY_NIGHT) {
            d.color(0.03f, 0.05f, 0.14f, night * 0.82f);
            if (coverAll < minY + vh) d.rect(minX, coverAll, vw, minY + vh - coverAll);
        }
        // estrelas
        float starA = t.bg == Theme.Bg.MOON || t.bg == Theme.Bg.CITY_NIGHT ? 1 : (indoor ? 0 : night);
        if (t.bg == Theme.Bg.ICE_PEAKS) starA = Math.max(starA, 0.6f);
        if (starA > 0.05f) {
            for (int i = 0; i < stars.size; i++) {
                float[] st = stars.get(i);
                float sx = minX + st[0] * vw, sy = minY + vh * 0.25f + st[1] * vh * 0.75f;
                if (sy + st[2] < coverAll) continue;
                d.color(1, 1, 1, (0.45f + 0.35f * MathUtils.sin(time * 2 + st[3])) * starA);
                d.circle(sx, sy, st[2]);
            }
        }
        com.brigadajato.Perf.mark(d.batch, "b.stars");
        if (!indoor) drawSky(d, t, minX, minY, vw, vh, time, night, sunPos, camX);
        else drawIndoorWall(d, t, camX, camY, minX, minY, vw, vh, time);
        com.brigadajato.Perf.mark(d.batch, "b.sun");
        lists[0] = far;
        lists[1] = mid;
        lists[2] = near;
        cols[0] = t.far;
        cols[1] = t.mid;
        cols[2] = t.near;
        for (int i = 0; i < 3; i++) {
            if (!drawsLayer(i)) continue;
            BackdropArt.Layer l = art.layers[i];
            tex = l.tex != null;
            if (tex) texLayer(d, l, KS[i], i, camX, camY, minX, minY, vw, vh, night, coverAbove[i]);
            if (com.brigadajato.Perf.ON) com.brigadajato.Perf.mark(d.batch, "b.tex" + i);
            if (tex && i < 2 && !indoor) { // neblina entre as camadas: da profundidade
                float k2 = KS[i];
                float baseY = (camY - worldH * 0.5f) * (1 - k2 * 0.7f) + worldH * (0.18f - i * 0.06f);
                int hz = t.skyBottom;
                float fade = 1 - night * 0.6f;
                float clip = coverAbove[i];
                for (int j = 0; j < 4; j++) {
                    float hy = baseY - 40 + j * 22, hh = 60 - j * 12;
                    if (hy + hh <= clip) continue;
                    if (hy < clip) {
                        hh -= clip - hy;
                        hy = clip;
                    }
                    d.color(((hz >> 16) & 0xff) / 255f * fade, ((hz >> 8) & 0xff) / 255f * fade, (hz & 0xff) / 255f * fade, 0.1f);
                    d.rect(minX, hy, vw, hh);
                }
            }
            if (com.brigadajato.Perf.ON) com.brigadajato.Perf.mark(d.batch, "b.haze" + i);
            layer(d, lists[i], KS[i], cols[i], i == 0 ? 0.9f : (i == 1 ? 0.95f : 1f), camX, camY, i, time, night);
            if (com.brigadajato.Perf.ON) com.brigadajato.Perf.mark(d.batch, "b.shapes" + i);
        }
        if (t.bg == Theme.Bg.UNDERWATER) {
            // raios de luz vindos da superficie
            d.additive(true);
            for (int i = 0; i < 5; i++) {
                float x = minX + wrap(i * 330 - camX * 0.25f + time * 8, vw + 400) - 200;
                d.color(0.6f, 0.9f, 1f, 0.07f + 0.03f * MathUtils.sin(time + i));
                d.batch.draw(d.a.tri, x, minY + vh, 80, 40, 160, vh * 1.2f, 1, 1, 180 + 8 * MathUtils.sin(time * 0.3f + i));
            }
            d.additive(false);
        }
    }

    private static final float[] KS = {0.18f, 0.38f, 0.6f};
    private final float[] coverAbove = new float[3];
    @SuppressWarnings("unchecked")
    private final Array<float[]>[] lists = new Array[3];
    private final int[] cols = new int[3];

    private boolean drawsLayer(int i) {
        if (quality == 0 && i != 1) return false; // celular fraco: so a camada do meio
        return !(quality == 1 && i == 2);
    }

    /** Pedaco da textura entre as alturas ya e yb (mundo), repetido na horizontal. */
    private void texPart(Draw d, BackdropArt.Layer l, float start, float minX, float vw, float y0, float ya, float yb) {
        if (yb <= ya) return;
        float fa = (ya - y0) / l.h, fb = (yb - y0) / l.h;
        for (float x = start; x < minX + vw; x += l.w) d.batch.draw(l.tex, x, ya, l.w, yb - ya, 0, 1 - fa, 1, 1 - fb);
    }

    /** Desenha uma camada pintada repetindo na horizontal, com paralaxe. */
    private void texLayer(Draw d, BackdropArt.Layer l, float k, int depth, float camX, float camY, float minX, float minY, float vw, float vh, float night,
                          float clipY) {
        float baseY = (camY - worldH * 0.5f) * (1 - k * 0.7f) + worldH * (0.18f - depth * 0.06f);
        float y0 = baseY - l.h * 0.12f;
        float dim = indoor ? 1 : 1 - night * 0.55f;
        float br = dim * (1 - night * 0.12f), bb = Math.min(1, dim * (1 + night * 0.25f));
        float xs = camX * (1 - k);
        float start = xs + (float) Math.floor((minX - xs) / l.w) * l.w;
        d.color(br, br, bb, 1);
        // so a parte visivel: a de baixo (opaca) sem mistura, a de cima com transparencia; o topo vazio nem desenha
        float bottom = Math.max(y0, clipY), solidTop = y0 + l.h * l.solidFrac, top = Math.min(y0 + l.h * l.topFrac, minY + vh);
        if (solidTop > bottom) {
            d.batch.disableBlending();
            texPart(d, l, start, minX, vw, y0, bottom, Math.min(solidTop, top));
            d.batch.enableBlending();
        }
        texPart(d, l, start, minX, vw, y0, Math.max(bottom, solidTop), top);
        int c = l.bottom;
        d.color(((c >> 16) & 0xff) / 255f * br, ((c >> 8) & 0xff) / 255f * br, (c & 0xff) / 255f * bb, 1);
        if (y0 > minY && y0 > clipY) { // preenchimento em degrade (escurece para baixo) em vez de cor chapada
            float hgt = y0 - minY + 1;
            float cr = ((c >> 16) & 0xff) / 255f * br, cg = ((c >> 8) & 0xff) / 255f * br, cb = (c & 0xff) / 255f * bb;
            int steps = 6;
            d.batch.disableBlending();
            for (int i = 0; i < steps; i++) {
                float kk = 1 - 0.32f * (i + 1) / steps;
                float yA = y0 + 1 - hgt * (i + 1) / steps, hA = hgt / steps + 1;
                if (yA + hA <= clipY) continue;
                if (yA < clipY) {
                    hA -= clipY - yA;
                    yA = clipY;
                }
                d.color(cr * kk, cg * kk, cb * kk, 1);
                d.rect(minX, yA, vw, hA);
            }
            d.batch.enableBlending();
            d.color(cr, cg, cb, 1);
        }
        if (l.ceiling && y0 + l.h < minY + vh) d.rect(minX, y0 + l.h - 1, vw, minY + vh - (y0 + l.h) + 2);
    }

    private void drawSky(Draw d, Theme t, float minX, float minY, float vw, float vh, float time, float night, float sunPos, float camX) {
        float ang = MathUtils.PI * (0.12f + 0.76f * sunPos);
        float sunX = minX + vw * (0.5f - 0.38f * MathUtils.cos(ang)), sunY = minY + vh * (0.42f + 0.42f * MathUtils.sin(ang));
        if (t.bg == Theme.Bg.MOON) {
            sunX = minX + vw * 0.78f;
            sunY = minY + vh * 0.78f;
            d.additive(true);
            d.color(0.3f, 0.6f, 1f, 0.35f);
            d.soft(sunX, sunY, 120);
            d.additive(false);
            d.rgb(0x1e88e5, 1);
            d.circle(sunX, sunY, 54);
            d.rgb(0x43a047, 1);
            d.ellipse(sunX - 14, sunY + 10, 20, 14, 20);
            d.ellipse(sunX + 18, sunY - 16, 14, 18, -10);
            d.rgb(0xffffff, 0.6f);
            d.ellipse(sunX + 4, sunY + 30, 26, 6, 0);
            d.rgb(0x000000, 0.35f);
            d.batch.draw(d.a.half, sunX - 54, sunY, 54, 0, 108, 54, 1, 1, -70);
        } else if (t.bg == Theme.Bg.CITY_NIGHT || night > 0.5f) {
            // lua cheia
            d.additive(true);
            d.color(0.8f, 0.85f, 1f, 0.3f);
            d.soft(sunX, sunY, 110);
            d.additive(false);
            d.color(0.95f, 0.95f, 0.88f, 1);
            d.circle(sunX, sunY, 26);
            d.color(0.75f, 0.75f, 0.7f, 0.6f);
            d.circle(sunX - 8, sunY + 6, 5);
            d.circle(sunX + 7, sunY - 7, 4);
        } else {
            boolean pale = t.bg == Theme.Bg.ICE_PEAKS || t.bg == Theme.Bg.BUILDINGS;
            boolean volcano = t.bg == Theme.Bg.VOLCANO;
            d.color(1, volcano ? 0.6f : 1, volcano ? 0.4f : 0.9f, pale ? 0.5f : 0.85f);
            d.additive(true);
            d.soft(sunX, sunY, 140);
            d.additive(false);
            d.color(1, volcano ? 0.75f : 1, volcano ? 0.5f : 0.92f, 1);
            d.circle(sunX, sunY, t.bg == Theme.Bg.ICE_PEAKS ? 28 : 40);
        }
        // nuvens
        boolean cloudy = t.bg != Theme.Bg.BUILDINGS && t.bg != Theme.Bg.ICE_PEAKS && t.bg != Theme.Bg.MOON && t.bg != Theme.Bg.CITY_NIGHT;
        if (cloudy) {
            boolean smoke = t.bg == Theme.Bg.VOLCANO;
            for (int i = 0; i < clouds.size; i++) {
                float[] c = clouds.get(i);
                float cx = wrap(c[0] + time * c[3] - camX * 0.1f, PERIOD) + camX - PERIOD / 2;
                float cy = minY + vh * 0.55f + c[1] * 0.6f;
                if (smoke) d.color(0.25f, 0.2f, 0.2f, 0.55f);
                else d.color(1 - night * 0.7f, 1 - night * 0.65f, 1 - night * 0.5f, 0.85f);
                d.circle(cx, cy, c[2]);
                d.circle(cx + c[2] * 0.9f, cy - c[2] * 0.25f, c[2] * 0.75f);
                d.circle(cx - c[2] * 0.9f, cy - c[2] * 0.3f, c[2] * 0.65f);
            }
        }
    }

    /** Parede de fundo para ambientes fechados. */
    private void drawIndoorWall(Draw d, Theme t, float camX, float camY, float minX, float minY, float vw, float vh, float time) {
        float k = 0.3f;
        switch (t.bg) {
            case INDOOR_WOOD: {
                float pw = 46;
                float off = wrap(-camX * k, pw);
                for (float x = minX - pw + off; x < minX + vw + pw; x += pw) {
                    int i = (int) Math.floor((x + camX * k) / pw);
                    d.rgb(SoldierPainter.mix(t.far, 0x000000, (i & 3) * 0.05f), 1);
                    d.rect(x, minY, pw - 2, vh);
                    d.rgb(0x000000, 0.25f);
                    d.rect(x + pw - 2, minY, 2, vh);
                }
                break;
            }
            case INDOOR_LAB: {
                float g = 90;
                float ox = wrap(-camX * k, g), oy = wrap(-camY * k, g);
                d.rgb(0x000000, 0.25f);
                for (float x = minX - g + ox; x < minX + vw + g; x += g) d.rect(x, minY, 3, vh);
                for (float y = minY - g + oy; y < minY + vh + g; y += g) d.rect(minX, y, vw, 3);
                break;
            }
            case INDOOR_SCHOOL: {
                // metade de baixo pintada (rodape) e faixa
                float wainY = minY + vh * 0.35f - (camY - worldH * 0.5f) * k * 0.3f;
                d.rgb(t.mid, 1);
                d.rect(minX, minY, vw, Math.max(0, wainY - minY));
                d.rgb(SoldierPainter.darker(t.mid, 0.7f), 1);
                d.rect(minX, wainY, vw, 8);
                break;
            }
            case CAVE: {
                d.rgb(SoldierPainter.darker(t.far, 0.8f), 0.6f);
                for (int i = 0; i < far.size; i++) {
                    float[] s = far.get(i);
                    float x = camX + wrap(s[0] - camX * 0.12f + PERIOD / 2, PERIOD) - PERIOD / 2;
                    d.ellipse(x, minY + vh * (0.3f + s[1] * 0.4f), s[2] * 0.4f, s[3] * 0.5f, s[1] * 40);
                }
                break;
            }
            default:
                break;
        }
    }

    private void layer(Draw d, Array<float[]> shapes, float k, int color, float alpha, float camX, float camY, int depth, float time, float night) {
        Theme t = theme;
        float baseY = (camY - worldH * 0.5f) * (1 - k * 0.7f) + worldH * (0.18f - depth * 0.06f);
        float half = PERIOD / 2;
        float dim = indoor ? 1 : 1 - night * 0.55f;
        int col = SoldierPainter.mix(0x000000, color, dim);
        if (!indoor && night > 0) col = SoldierPainter.mix(col, 0x10183a, night * 0.3f);
        for (int i = 0; i < shapes.size; i++) {
            float[] s = shapes.get(i);
            float sx = wrap(s[0] - camX * k + half, PERIOD) - half;
            float x = camX + sx;
            float w = s[2], h = s[3];
            d.rgb(col, alpha);
            switch (t.bg) {
                case BUILDINGS:
                    buildings(d, x, baseY, w, h, depth, s[1], col, time, false);
                    break;
                case CITY_NIGHT:
                    buildings(d, x, baseY, w, h, depth, s[1], col, time, true);
                    break;
                case ICE_PEAKS:
                    if (tex) break;
                    if (depth == 2) d.batch.draw(d.a.tri, x - w * 0.3f, baseY, w * 0.6f, h);
                    else {
                        d.batch.draw(d.a.tri, x - w * (depth == 0 ? 0.5f : 0.25f), baseY, w * (depth == 0 ? 1 : 0.5f), h * (depth == 0 ? 1 : 1.3f));
                        d.rect(x - w * (depth == 0 ? 0.5f : 0.25f), baseY - 900, w * (depth == 0 ? 1 : 0.5f), 900);
                        if (depth == 0) {
                            d.rgb(0xffffff, 0.55f * dim);
                            d.batch.draw(d.a.tri, x - w * 0.16f, baseY + h * 0.68f, w * 0.32f, h * 0.32f);
                        }
                    }
                    break;
                case MOON:
                    if (depth == 2) {
                        if (tex) {
                            d.rgb(col, alpha);
                            d.rect(x - 2, baseY + 60, 4, h * 0.6f);
                            d.rgb(0xff5252, 0.8f + 0.2f * MathUtils.sin(time * 4 + x));
                            d.circle(x, baseY + 60 + h * 0.6f, 3);
                            break;
                        }
                        d.batch.draw(d.a.half, x - w, baseY, w * 2, w);
                        d.rect(x - 2, baseY + w * 0.9f, 4, h * 0.6f);
                        d.rgb(0xff5252, 0.8f + 0.2f * MathUtils.sin(time * 4 + x));
                        d.circle(x, baseY + w * 0.9f + h * 0.6f, 3);
                    } else if (!tex) hills(d, x, baseY, w, h, depth, t);
                    break;
                case OCEAN:
                    ocean(d, x, baseY, w, h, depth, s[1], col, time);
                    break;
                case INDOOR_WOOD:
                    woodInterior(d, x, baseY, w, h, depth, s[1], col, time);
                    break;
                case CAVE:
                    cave(d, x, baseY, w, h, depth, s[1], col);
                    break;
                case VOLCANO:
                    volcano(d, x, baseY, w, h, depth, s[1], col, time);
                    break;
                case UNDERWATER:
                    underwater(d, x, baseY, w, h, depth, s[1], col, time);
                    break;
                case INDOOR_LAB:
                    lab(d, x, baseY, w, h, depth, s[1], col, time);
                    break;
                case INDOOR_SCHOOL:
                    school(d, x, baseY, w, h, depth, s[1], col);
                    break;
                case MARKET:
                    market(d, x, baseY, w, h, depth, s[1], col, time);
                    break;
                default: // MOUNTAINS, ISLANDS, FOREST
                    if (tex) break;
                    if (depth == 2) {
                        if (t.ambient == Theme.Amb.DUST) cactus(d, x, baseY, h);
                        else tree(d, x, baseY, w, h);
                    } else hills(d, x, baseY, w, h, depth, t);
                    break;
            }
        }
    }

    private void hills(Draw d, float x, float baseY, float w, float h, int depth, Theme t) {
        if (depth == 0) {
            d.batch.draw(d.a.tri, x - w / 2, baseY, w, h);
            d.rect(x - w / 2, baseY - 900, w, 900);
            if (t.bg == Theme.Bg.ISLANDS || t == Theme.TRAINING) {
                d.rgb(0xffffff, 0.55f);
                d.batch.draw(d.a.tri, x - w * 0.16f, baseY + h * 0.68f, w * 0.32f, h * 0.32f);
            }
        } else {
            d.ellipse(x, baseY, w * 0.6f, h, 0);
            d.rect(x - w * 0.6f, baseY - 900, w * 1.2f, 900);
        }
    }

    private void cactus(Draw d, float x, float baseY, float h) {
        d.rect(x - 6, baseY, 12, h);
        d.circle(x, baseY + h, 6);
        d.rect(x - 20, baseY + h * 0.45f, 8, h * 0.3f);
        d.rect(x + 12, baseY + h * 0.55f, 8, h * 0.28f);
        d.rect(x - 20, baseY + h * 0.45f, 20, 7);
        d.rect(x, baseY + h * 0.55f, 20, 7);
    }

    private void tree(Draw d, float x, float baseY, float w, float h) {
        d.rect(x - 4, baseY - 900, 8, h * 0.6f + 900);
        d.circle(x, baseY + h * 0.7f, w * 0.55f);
        d.circle(x - w * 0.3f, baseY + h * 0.5f, w * 0.4f);
        d.circle(x + w * 0.3f, baseY + h * 0.5f, w * 0.4f);
    }

    private void buildings(Draw d, float x, float baseY, float w, float h, int depth, float rnd, int col, float time, boolean night) {
        if (tex && depth < 2) {
            if (night && depth == 1 && rnd > 0.55f) { // letreiro neon na frente dos predios
                int neon = rnd > 0.78f ? 0xff4081 : 0x18ffff;
                float glow = 0.75f + 0.25f * MathUtils.sin(time * (3 + rnd * 5));
                d.additive(true);
                d.rgb(neon, 0.3f * glow);
                d.soft(x, baseY + h * 1.3f, w * 0.25f);
                d.additive(false);
                d.rgb(neon, glow);
                d.rect(x - w * 0.12f, baseY + h * 1.3f - 4, w * 0.24f, 7);
            }
            return;
        }
        if (depth == 0) {
            d.rect(x - w * 0.3f, baseY - 900, w * 0.45f, h + 900);
            d.rect(x + w * 0.2f, baseY - 900, w * 0.12f, h * 1.4f + 900);
            if (night) windows(d, x - w * 0.3f, baseY, w * 0.45f, h, rnd, 0xfff59d, 0.35f);
        } else if (depth == 1) {
            d.rect(x - w * 0.25f, baseY - 900, w * 0.5f, h + 900);
            windows(d, x - w * 0.25f, baseY, w * 0.5f, h, rnd + 0.3f, night ? 0xffe082 : 0xffeb3b, night ? 0.75f : 0.3f);
            if (night && rnd > 0.5f) { // letreiro neon
                int neon = rnd > 0.75f ? 0xff4081 : 0x18ffff;
                float glow = 0.75f + 0.25f * MathUtils.sin(time * (3 + rnd * 5));
                d.rgb(neon, glow);
                d.rect(x - w * 0.18f, baseY + h - 26, w * 0.36f, 8);
                d.additive(true);
                d.rgb(neon, 0.25f * glow);
                d.soft(x, baseY + h - 22, w * 0.3f);
                d.additive(false);
            }
        } else {
            if (night) { // poste
                d.rect(x - 2.5f, baseY - 900, 5, h + 900);
                d.rect(x - 2.5f, baseY + h, 22, 4);
                d.color(1, 0.9f, 0.6f, 1);
                d.circle(x + 18, baseY + h - 2, 4);
                d.additive(true);
                d.color(1, 0.85f, 0.5f, 0.25f);
                d.soft(x + 18, baseY + h - 6, 40);
                d.additive(false);
            } else {
                d.rect(x - 5, baseY - 900, 10, h + 900);
                d.rect(x - 14, baseY + h, 28, 8);
            }
        }
    }

    private void windows(Draw d, float x0, float baseY, float w, float h, float rnd, int col, float alpha) {
        int cols = Math.max(1, (int) (w / 26)), rows = Math.max(1, (int) (h / 34));
        for (int i = 0; i < cols; i++)
            for (int j = 0; j < rows; j++) {
                int hash = (int) (rnd * 997) + i * 31 + j * 17;
                if ((hash * 2654435761L >>> 28 & 3) == 0) continue;
                d.rgb(col, alpha);
                d.rect(x0 + 8 + i * 26, baseY + h - 30 - j * 34, 10, 14);
            }
    }

    private void ocean(Draw d, float x, float baseY, float w, float h, int depth, float rnd, int col, float time) {
        if (depth == 0 && tex) return;
        if (depth == 0) { // ilhas e navios ao longe
            if (rnd < 0.45f) {
                d.ellipse(x, baseY + 10, w * 0.25f, h * 0.18f, 0);
            } else if (rnd < 0.7f) {
                d.rect(x - 30, baseY + 6, 60, 10);
                d.rect(x - 4, baseY + 16, 4, 34);
                d.batch.draw(d.a.tri, x, baseY + 20, 26, 30);
            }
            d.rect(x - w / 2, baseY - 900, w, 906);
        } else if (depth == 1) { // agua com ondas
            d.rect(x - w * 0.6f, baseY - 900, w * 1.2f, 900 + h * 0.4f);
            d.rgb(0xffffff, 0.35f);
            for (int k = 0; k < 4; k++) {
                float wx = x - w * 0.5f + wrap(k * w * 0.3f + time * 22, w);
                d.ellipse(wx, baseY + h * 0.4f - 2 + MathUtils.sin(time * 2 + k) * 2, 16, 2.5f, 0);
            }
        } else { // gaivotas
            if (rnd > 0.6f) {
                float gy = baseY + h * 2.2f + MathUtils.sin(time * 1.5f + rnd * 9) * 10;
                float gx = x + MathUtils.sin(time * 0.4f + rnd * 5) * 40;
                float flap = MathUtils.sin(time * 9 + rnd * 7) * 18;
                d.rgb(0x37474f, 0.8f);
                d.rect(gx, gy, 10, 2, 0, 1, 160 + flap);
                d.rect(gx, gy, 10, 2, 0, 1, 20 - flap);
            }
        }
    }

    private void woodInterior(Draw d, float x, float baseY, float w, float h, int depth, float rnd, int col, float time) {
        if (depth == 0) { // vigas
            d.rect(x - 10, baseY - 900, 20, 2000);
        } else if (depth == 1) {
            d.rect(x - w * 0.6f, baseY + h * 1.6f, w * 1.2f, 16);
            if (rnd > 0.4f) { // barris e caixas empilhados
                d.rect(x - 30, baseY - 10, 34, 40);
                d.ellipse(x + 26, baseY + 12, 18, 22, 0);
            }
        } else if (rnd > 0.55f) { // lampiao pendurado
            float ly = baseY + h * 2.4f;
            d.rect(x - 1, ly, 2, 200);
            float sw = MathUtils.sin(time * 1.3f + rnd * 6) * 4;
            d.color(1, 0.8f, 0.4f, 1);
            d.circle(x + sw, ly - 4, 6);
            d.additive(true);
            d.color(1, 0.7f, 0.3f, 0.25f);
            d.soft(x + sw, ly - 4, 50);
            d.additive(false);
        }
    }

    private void cave(Draw d, float x, float baseY, float w, float h, int depth, float rnd, int col) {
        if (tex) return;
        if (depth == 2) {
            if (rnd > 0.6f) {
                d.additive(true);
                d.color(0.3f, 1f, 0.8f, 0.25f);
                d.soft(x, baseY + 20, 26);
                d.additive(false);
                d.color(0.5f, 1f, 0.9f, 0.8f);
                d.batch.draw(d.a.tri, x - 5, baseY, 10, 22);
            }
            return;
        }
        float top = baseY + 700 - depth * 120;
        // estalactites de cima
        d.batch.draw(d.a.tri, x - w * 0.08f, top, w * 0.04f + 10, w * 0.04f + 10, w * 0.16f, h * (0.8f + rnd), 1, 1, 180);
        d.rect(x - w * 0.6f, top, w * 1.2f, 900);
        if (depth >= 1) { // estalagmites
            d.batch.draw(d.a.tri, x - w * 0.1f, baseY, w * 0.2f, h * 0.7f);
            d.rect(x - w * 0.6f, baseY - 900, w * 1.2f, 900);
        }
        if (depth == 2 && rnd > 0.6f) { // cristal brilhante
            d.additive(true);
            d.color(0.3f, 1f, 0.8f, 0.25f);
            d.soft(x, baseY + 20, 26);
            d.additive(false);
            d.color(0.5f, 1f, 0.9f, 0.8f);
            d.batch.draw(d.a.tri, x - 5, baseY, 10, 22);
        }
    }

    private void volcano(Draw d, float x, float baseY, float w, float h, int depth, float rnd, int col, float time) {
        if (tex) return;
        if (depth == 0) {
            float vh = h * 1.3f;
            d.batch.draw(d.a.tri, x - w * 0.6f, baseY, w * 1.2f, vh);
            d.rect(x - w * 0.6f, baseY - 900, w * 1.2f, 900);
            if (rnd > 0.4f) { // cratera com lava e fumaca
                float cy = baseY + vh * 0.86f;
                d.rgb(0xff6d00, 0.9f);
                d.ellipse(x, cy, w * 0.07f, 6, 0);
                d.additive(true);
                d.color(1, 0.4f, 0.1f, 0.35f + 0.1f * MathUtils.sin(time * 3 + rnd * 5));
                d.soft(x, cy, w * 0.25f);
                d.additive(false);
                for (int k = 0; k < 4; k++) {
                    float t2 = wrap(time * 0.15f + k * 0.25f + rnd, 1);
                    d.color(0.2f, 0.17f, 0.17f, 0.5f * (1 - t2));
                    d.circle(x + t2 * 80 + MathUtils.sin(t2 * 6) * 10, cy + t2 * 260, 20 + t2 * 60);
                }
            }
        } else if (depth == 1) {
            d.ellipse(x, baseY, w * 0.6f, h, 0);
            d.rect(x - w * 0.6f, baseY - 900, w * 1.2f, 900);
            d.rgb(0xff5722, 0.5f + 0.2f * MathUtils.sin(time * 2 + rnd * 9));
            d.rect(x - w * 0.2f, baseY + h * 0.2f, 3, h * 0.5f, 1.5f, 0, 30 * (rnd - 0.5f));
        } else { // arvore seca
            d.rect(x - 3, baseY - 900, 6, h * 0.7f + 900);
            d.rect(x, baseY + h * 0.5f, 3, h * 0.35f, 1.5f, 0, -40);
            d.rect(x, baseY + h * 0.4f, 3, h * 0.3f, 1.5f, 0, 35);
        }
    }

    private void underwater(Draw d, float x, float baseY, float w, float h, int depth, float rnd, int col, float time) {
        if (depth == 0 && tex) return;
        if (depth == 1 && tex) {
            kelp(d, x + w * 0.2f, baseY + 40, h * 1.4f, time, rnd, SoldierPainter.mix(col, 0x2e7d32, 0.5f));
            return;
        }
        if (depth == 2 && tex) {
            kelp(d, x, baseY + 30, h * 1.8f, time, rnd, SoldierPainter.mix(col, 0x43a047, 0.55f));
            if (rnd > 0.5f) {
                float fx = x + MathUtils.sin(time * 0.5f + rnd * 9) * 120, fy = baseY + h * 2 + MathUtils.sin(time + rnd * 4) * 30;
                int dir = MathUtils.cos(time * 0.5f + rnd * 9) > 0 ? 1 : -1;
                d.rgb(rnd > 0.75f ? 0xffb74d : 0x80deea, 0.85f);
                d.ellipse(fx, fy, 9, 4.5f, 0);
                d.batch.draw(d.a.tri, fx - dir * 9 - 4, fy - 4, 4, 4, 8, 8, 1, 1, dir > 0 ? 90 : -90);
            }
            return;
        }
        if (depth == 0) {
            d.ellipse(x, baseY, w * 0.5f, h * 0.7f, 0);
            d.rect(x - w * 0.5f, baseY - 900, w, 900);
        } else if (depth == 1) {
            d.ellipse(x, baseY, w * 0.35f, h * 0.6f, 0);
            d.rect(x - w * 0.35f, baseY - 900, w * 0.7f, 900);
            kelp(d, x + w * 0.2f, baseY + h * 0.3f, h * 1.4f, time, rnd, SoldierPainter.mix(col, 0x2e7d32, 0.5f));
        } else {
            kelp(d, x, baseY, h * 1.8f, time, rnd, SoldierPainter.mix(col, 0x43a047, 0.55f));
            if (rnd > 0.5f) { // peixinhos
                float fx = x + MathUtils.sin(time * 0.5f + rnd * 9) * 120, fy = baseY + h * 2 + MathUtils.sin(time + rnd * 4) * 30;
                int dir = MathUtils.cos(time * 0.5f + rnd * 9) > 0 ? 1 : -1;
                d.rgb(rnd > 0.75f ? 0xffb74d : 0x80deea, 0.85f);
                d.ellipse(fx, fy, 9, 4.5f, 0);
                d.batch.draw(d.a.tri, fx - dir * 9 - 4, fy - 4, 4, 4, 8, 8, 1, 1, dir > 0 ? 90 : -90);
            }
        }
    }

    private void kelp(Draw d, float x, float baseY, float h, float time, float rnd, int col) {
        d.rgb(col, 1);
        int seg = 7;
        float px = x, py = baseY;
        for (int i = 0; i < seg; i++) {
            float nx = x + MathUtils.sin(time * 1.2f + i * 0.6f + rnd * 9) * (4 + i * 2.5f), ny = baseY + (i + 1) * h / seg;
            d.line(px, py, nx, ny, 7 - i * 0.6f);
            if (i % 2 == 1) d.ellipse(nx + 6, ny, 7, 3, 30);
            px = nx;
            py = ny;
        }
    }

    private void lab(Draw d, float x, float baseY, float w, float h, int depth, float rnd, int col, float time) {
        if (depth == 0) { // canos
            d.rect(x - w * 0.6f, baseY + h * 1.5f, w * 1.2f, 18);
            d.rect(x - 9, baseY - 900, 18, 2400);
        } else if (depth == 1) { // tanques com liquido brilhante
            d.rrect(x - 40, baseY, 80, h * 1.2f);
            d.color(0.3f, 1f, 0.85f, 0.35f + 0.1f * MathUtils.sin(time * 2 + rnd * 7));
            d.rect(x - 30, baseY + 10, 60, h * 1.2f - 30);
            d.color(1, 1, 1, 0.5f);
            for (int k = 0; k < 3; k++) {
                float by = baseY + 10 + wrap(time * 40 + k * 40 + rnd * 99, h * 1.2f - 40);
                d.circle(x - 15 + k * 14, by, 3);
            }
        } else if (rnd > 0.5f) { // painel com luzes
            d.rect(x - 22, baseY + h * 0.6f, 44, 30);
            for (int k = 0; k < 4; k++) {
                boolean on = ((int) (time * 2 + k + rnd * 9)) % 3 != 0;
                d.rgb(k % 2 == 0 ? 0x69f0ae : 0xff5252, on ? 1 : 0.25f);
                d.circle(x - 13 + k * 9, baseY + h * 0.6f + 15, 2.5f);
            }
        }
    }

    private void school(Draw d, float x, float baseY, float w, float h, int depth, float rnd, int col) {
        if (depth == 0) return;
        if (depth == 1) {
            if (rnd > 0.5f) { // janela
                d.rgb(0x5d4037, 1);
                d.rect(x - 44, baseY + 60, 88, 80);
                d.rgb(0xb3e5fc, 1);
                d.rect(x - 40, baseY + 64, 38, 72);
                d.rect(x + 2, baseY + 64, 38, 72);
            } else { // quadro negro com rabiscos
                d.rgb(0x6d4c41, 1);
                d.rect(x - 70, baseY + 56, 140, 84);
                d.rgb(0x2e7d32, 1);
                d.rect(x - 64, baseY + 62, 128, 72);
                d.rgb(0xffffff, 0.6f);
                d.rect(x - 52, baseY + 112, 40, 3);
                d.rect(x - 52, baseY + 100, 60, 3);
                d.rect(x + 14, baseY + 86, 30, 3);
                d.circle(x + 30, baseY + 112, 9);
            }
        } else if (rnd > 0.45f) { // armarios
            for (int k = 0; k < 3; k++) {
                d.rgb(k % 2 == 0 ? 0x1976d2 : 0x1565c0, 1);
                d.rect(x - 36 + k * 24, baseY - 10, 22, 92);
                d.rgb(0x0d47a1, 1);
                d.rect(x - 31 + k * 24, baseY + 60, 12, 2);
                d.rect(x - 31 + k * 24, baseY + 56, 12, 2);
            }
        }
    }

    private void market(Draw d, float x, float baseY, float w, float h, int depth, float rnd, int col, float time) {
        if (depth == 0 && tex) return;
        if (depth == 0) { // casarios coloridos
            int[] colors = {0xef9a9a, 0xfff59d, 0x80cbc4, 0xb39ddb, 0xffcc80};
            d.rgb(colors[(int) (rnd * colors.length) % colors.length], 0.75f);
            d.rect(x - w * 0.2f, baseY - 900, w * 0.4f, h * 0.8f + 900);
            d.rgb(0x8d6e63, 0.75f);
            d.batch.draw(d.a.tri, x - w * 0.23f, baseY + h * 0.8f, w * 0.46f, 40);
            d.rgb(0x5d4037, 0.4f);
            for (int k = 0; k < 3; k++) d.rect(x - w * 0.15f + k * w * 0.11f, baseY + h * 0.4f, 12, 18);
        } else if (depth == 1) { // barracas com toldo listrado
            d.rgb(0x6d4c41, 1);
            d.rect(x - 60, baseY, 6, 90);
            d.rect(x + 54, baseY, 6, 90);
            d.rect(x - 60, baseY, 120, 30);
            int a1 = rnd > 0.5f ? 0xe53935 : 0x1e88e5;
            for (int k = 0; k < 6; k++) {
                d.rgb(k % 2 == 0 ? a1 : 0xffffff, 1);
                d.rect(x - 66 + k * 22, baseY + 86, 22, 18);
                d.batch.draw(d.a.half, x - 66 + k * 22, baseY + 76, 11, 5, 22, 10, 1, 1, 180);
            }
            // frutas
            int[] fr = {0xff7043, 0xffee58, 0x9ccc65, 0xab47bc};
            for (int k = 0; k < 6; k++) {
                d.rgb(fr[(k + (int) (rnd * 10)) % fr.length], 1);
                d.circle(x - 45 + k * 17, baseY + 36, 6);
            }
        } else if (rnd > 0.3f) { // varal de bandeirinhas
            float y0 = baseY + h * 2.6f;
            for (int k = 0; k < 6; k++) {
                int[] fc = {0xf44336, 0xffeb3b, 0x4caf50, 0x2196f3};
                d.rgb(fc[k % fc.length], 0.9f);
                float sway = MathUtils.sin(time * 2 + k + rnd * 5) * 3;
                d.batch.draw(d.a.tri, x - 60 + k * 20 - 6, y0 - 14 + sway, 6, 7, 12, 14, 1, 1, 180);
            }
            d.rgb(0x5d4037, 0.8f);
            d.rect(x - 70, y0, 140, 1.5f);
        }
    }

    @Override
    public void dispose() {
        ArtCache.release(art);
        sky.dispose();
    }
}
