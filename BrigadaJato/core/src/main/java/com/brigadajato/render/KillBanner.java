package com.brigadajato.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.brigadajato.Assets;
import com.brigadajato.I18n;
import com.brigadajato.world.C;
import com.brigadajato.world.WeaponType;

/**
 * Emblema animado de abates em sequencia. Linha do tempo: o emblema cai girando e bate
 * (tremor + faiscas + ondas de choque), as asas entram uma a uma, os contadores acendem em
 * sequencia, um brilho atravessa o emblema e o texto entra com tranco (e "glitch" nos niveis
 * altos). No nivel 5 aparece uma moldura hexagonal girando e brasas subindo.
 */
public class KillBanner {
    private static final int[] COLORS = {0x4dd0e1, 0x40c4ff, 0xffd54f, 0xff9100, 0xff1744};
    private static final int INK = 0x101018;
    private static final float SLAM = 0.09f;
    private static final int MAXP = 90;

    private final Assets a;
    private float t = 99, life;
    private int level = 1;
    private int code;
    private boolean head;
    private float spin;
    private boolean impactDone = true;
    private float pendingImpact;

    // faiscas (coordenadas relativas ao centro do emblema)
    private final float[] px = new float[MAXP], py = new float[MAXP], pvx = new float[MAXP], pvy = new float[MAXP];
    private final float[] plife = new float[MAXP], pmax = new float[MAXP], psize = new float[MAXP];
    private final int[] pcol = new int[MAXP];
    private int pnext;

    public KillBanner(Assets a) {
        this.a = a;
    }

    public void trigger(int level, int code, boolean head) {
        this.level = Math.max(1, level);
        this.code = code;
        this.head = head;
        t = 0;
        impactDone = false;
        life = 2.1f + Math.min(5, this.level) * 0.12f;
    }

    public boolean active() {
        return t < life;
    }

    /** Forca do impacto (para tremer a camera) uma unica vez. */
    public float consumeImpact() {
        float v = pendingImpact;
        pendingImpact = 0;
        return v;
    }

    private int color() {
        return COLORS[MathUtils.clamp(level, 1, COLORS.length) - 1];
    }

    private static String label(int level) {
        switch (level) {
            case 1: return null;
            case 2: return I18n.t("kb_2");
            case 3: return I18n.t("kb_3");
            case 4: return I18n.t("kb_4");
            case 5: return I18n.t("kb_5");
            default: return I18n.t("kb_6");
        }
    }

    private void spark(float x, float y, float vx, float vy, float lifeS, float size, int col) {
        int i = pnext;
        pnext = (pnext + 1) % MAXP;
        px[i] = x;
        py[i] = y;
        pvx[i] = vx;
        pvy[i] = vy;
        plife[i] = pmax[i] = lifeS;
        psize[i] = size;
        pcol[i] = col;
    }

    public void update(float dt) {
        t += dt;
        spin += dt * 30;
        if (!impactDone && t >= SLAM) {
            impactDone = true;
            pendingImpact = 0.5f + Math.min(5, level) * 0.25f;
            int n = 14 + Math.min(5, level) * 7;
            for (int i = 0; i < n; i++) {
                float ang = MathUtils.random(MathUtils.PI2);
                float sp = MathUtils.random(160, 420 + level * 40);
                spark(0, 0, MathUtils.cos(ang) * sp, MathUtils.sin(ang) * sp + 80, MathUtils.random(0.35f, 0.8f),
                        MathUtils.random(2.5f, 5.5f), i % 3 == 0 ? 0xffffff : color());
            }
        }
        if (level >= 5 && t < life - 0.3f && MathUtils.randomBoolean(0.5f)) // brasas
            spark(MathUtils.random(-60, 60), MathUtils.random(-30, 10), MathUtils.random(-20, 20), MathUtils.random(60, 140),
                    MathUtils.random(0.6f, 1.2f), MathUtils.random(1.5f, 3.5f), MathUtils.randomBoolean() ? 0xffab00 : 0xff1744);
        for (int i = 0; i < MAXP; i++) {
            if (plife[i] <= 0) continue;
            plife[i] -= dt;
            pvy[i] -= 420 * dt;
            pvx[i] *= (1 - 2.2f * dt);
            px[i] += pvx[i] * dt;
            py[i] += pvy[i] * dt;
        }
    }

    /** Brilho nas bordas da tela no momento do impacto. */
    public void drawEdgeGlow(Draw d, float W, float H) {
        float tt = t - SLAM;
        if (tt < 0 || tt > 0.5f) return;
        float k = 1 - tt / 0.5f;
        int c = color();
        d.rgb(c, 0.26f * k);
        float e = (30 + level * 8) * k;
        d.rect(0, 0, W, e);
        d.rect(0, H - e, W, e);
        d.rect(0, 0, e, H);
        d.rect(W - e, 0, e, H);
    }

    private static float easeOutBack(float x) {
        x = MathUtils.clamp(x, 0, 1);
        float c1 = 1.9f, c3 = c1 + 1;
        return 1 + c3 * (x - 1) * (x - 1) * (x - 1) + c1 * (x - 1) * (x - 1);
    }

    public void draw(Draw d, float cx, float cy) {
        if (!active() && !anySparks()) return;
        int c = color();
        int lv = Math.min(level, 5);
        float fade = MathUtils.clamp((life - t) / 0.35f, 0, 1);
        boolean showEmblem = active();

        // ---- entrada: cai girando e bate
        float slamK = MathUtils.clamp(t / SLAM, 0, 1);
        float dropY = (1 - slamK) * (1 - slamK) * 110;
        float rot = (1 - slamK) * -220;
        float sinceImpact = Math.max(0, t - SLAM);
        float squashX = 1 + 0.35f * (float) Math.exp(-sinceImpact * 10) * MathUtils.cos(sinceImpact * 26);
        float squashY = 1 - 0.3f * (float) Math.exp(-sinceImpact * 10) * MathUtils.cos(sinceImpact * 26);
        float enterScale = slamK < 1 ? 2.1f - slamK * 1.1f : 1f;
        float exitScale = 1 + (1 - fade) * 0.35f;
        float bob = MathUtils.sin(t * 3) * 3;
        float ey = cy + dropY + bob;
        float S = 46 * enterScale * exitScale;
        float alpha = fade;

        if (showEmblem) {
            // brilho de fundo e raios
            d.additive(true);
            float pulse = 0.4f + 0.1f * MathUtils.sin(t * 9);
            d.rgb(c, pulse * alpha);
            d.soft(cx, ey, S * 2.4f);
            int rays = 6 + lv * 2;
            for (int i = 0; i < rays; i++) {
                float ang = spin * (i % 2 == 0 ? 1 : -0.6f) + i * 360f / rays;
                float len = S * (1.6f + lv * 0.3f) * (0.85f + 0.15f * MathUtils.sin(t * 7 + i));
                d.rgb(c, 0.22f * alpha);
                d.batch.draw(a.tri, cx - S * 0.12f, ey, S * 0.12f, 0, S * 0.24f, len, 1, 1, ang);
            }
            // ondas de choque no impacto
            for (int r = 0; r < (lv >= 3 ? 2 : 1); r++) {
                float k = (sinceImpact - r * 0.08f) / 0.45f;
                if (k > 0 && k < 1) {
                    d.rgb(r == 0 ? 0xffffff : c, (1 - k) * 0.9f);
                    d.ring(cx, ey, S * (0.5f + k * (2.4f + r)));
                }
            }
            if (sinceImpact > 0 && sinceImpact < 0.12f) {
                d.rgb(0xffffff, (1 - sinceImpact / 0.12f) * 0.85f);
                d.soft(cx, ey, S * 1.8f);
            }
            d.additive(false);

            // nivel 5: moldura hexagonal girando
            if (lv >= 5) {
                float hr = S * 1.25f;
                for (int i = 0; i < 6; i++) {
                    float a0 = spin * 2 + i * 60, a1 = a0 + 60;
                    float x0 = cx + MathUtils.cosDeg(a0) * hr, y0 = ey + MathUtils.sinDeg(a0) * hr;
                    float x1 = cx + MathUtils.cosDeg(a1) * hr, y1 = ey + MathUtils.sinDeg(a1) * hr;
                    d.rgb(INK, alpha);
                    d.line(x0, y0, x1, y1, 7);
                    d.rgb(0xffd54f, alpha);
                    d.line(x0, y0, x1, y1, 3.5f);
                }
            }

            // asas: entram uma a uma com tranco
            for (int i = 0; i < lv; i++) {
                float appear = (sinceImpact - i * 0.05f) / 0.18f;
                if (appear <= 0) continue;
                float k = easeOutBack(appear) * (0.55f + 0.45f * fade);
                float off = (S * 0.62f + i * S * 0.27f) * k;
                float h = S * (0.62f - i * 0.06f);
                boolean fresh = appear < 1.4f;
                for (int side = -1; side <= 1; side += 2) {
                    float x0 = cx + side * off;
                    d.rgb(INK, alpha);
                    d.line(x0, ey + h, x0 + side * S * 0.33f, ey, S * 0.22f);
                    d.line(x0 + side * S * 0.33f, ey, x0, ey - h, S * 0.22f);
                    d.rgb(fresh || i == lv - 1 ? 0xffffff : c, alpha);
                    d.line(x0, ey + h, x0 + side * S * 0.33f, ey, S * 0.12f);
                    d.line(x0 + side * S * 0.33f, ey, x0, ey - h, S * 0.12f);
                }
            }

            // losango central (com amassado do impacto)
            float sx = S * squashX, sy = S * squashY;
            float rr = rot + 45;
            diamond(d, cx, ey, sx * 1.24f, sy * 1.24f, rr, INK, alpha);
            diamond(d, cx, ey, sx * 1.04f, sy * 1.04f, rr, SoldierPainter.darker(c, 0.55f), alpha);
            diamond(d, cx, ey, sx * 0.88f, sy * 0.88f, rr, c, alpha);
            // brilho atravessando
            float g = ((t - 0.3f) % 1.3f) / 0.35f;
            if (t > 0.3f && g >= 0 && g <= 1) {
                d.additive(true);
                d.rgb(0xffffff, 0.55f * alpha * (1 - Math.abs(g - 0.5f) * 2));
                float gx = cx - S * 0.6f + g * S * 1.2f;
                d.rect(gx - S * 0.08f, ey - S * 0.55f, S * 0.16f, S * 1.1f, S * 0.08f, S * 0.55f, 25);
                d.additive(false);
            }
            // icone
            float is = S * 0.62f * Math.min(squashX, 1.15f);
            if (Math.abs(rot) < 1) {
                if (code == C.KILL_MELEE) Icons.fist(d, cx, ey, is, 0xf1c27d);
                else if (code == C.KILL_FRAG || code == C.KILL_MINE || code == C.KILL_BOMB
                        || (WeaponType.of(code) != null && WeaponType.of(code).isExplosive())) Icons.bomb(d, cx, ey, is, (t * 6) % 1 < 0.5f);
                else Icons.skull(d, cx, ey, is, 0xffffff);
                if (head) {
                    float hs = 1 + 0.6f * (float) Math.exp(-sinceImpact * 8);
                    d.rgb(0xff1744, alpha);
                    d.ring(cx, ey + S * 0.05f, S * 0.42f * hs);
                    d.rect(cx - S * 0.02f, ey + S * 0.3f, S * 0.04f, S * 0.22f);
                    d.rect(cx - S * 0.02f, ey - S * 0.42f, S * 0.04f, S * 0.22f);
                }
            }

            // contadores acendendo em sequencia
            for (int i = 0; i < 5; i++) {
                float pxp = cx + (i - 2) * 22, pyp = cy - 46 * 0.95f - 16;
                float lit = sinceImpact - 0.1f - i * 0.05f;
                boolean on = i < lv && lit > 0;
                float pop = on ? 1 + 0.8f * (float) Math.exp(-lit * 12) : 1;
                float s = 7 * pop;
                diamond(d, pxp, pyp, s * 2.1f, s * 2.1f, 45, INK, alpha);
                diamond(d, pxp, pyp, s * 1.5f, s * 1.5f, 45, on ? (i == lv - 1 ? 0xffffff : c) : 0x37474f, alpha);
                if (on && lit < 0.2f) {
                    d.additive(true);
                    d.rgb(c, (1 - lit / 0.2f) * alpha);
                    d.soft(pxp, pyp, 18);
                    d.additive(false);
                }
            }

            // texto com tranco (e glitch nos niveis altos)
            String text = label(level);
            float tt = sinceImpact - 0.12f;
            if (text != null && tt > 0) {
                BitmapFont f = a.big;
                float s = 1 + 1.4f * (1 - easeOutBack(tt / 0.25f)) + (1 - fade) * 0.3f;
                float shake = tt < 0.18f ? MathUtils.random(-3f, 3f) : 0;
                float ty = cy - 46 * 0.95f - 40;
                f.getData().setScale(Math.max(0.6f, s) / a.fontScale);
                if (lv >= 4 && tt < 0.6f) {
                    float j = MathUtils.random(2f, 5f);
                    f.setColor(1, 0.1f, 0.2f, 0.55f * alpha);
                    f.draw(d.batch, text, cx - j + shake, ty, 0, Align.center, false);
                    f.setColor(0.1f, 0.9f, 1f, 0.55f * alpha);
                    f.draw(d.batch, text, cx + j + shake, ty, 0, Align.center, false);
                }
                f.setColor(((c >> 16) & 0xff) / 255f, ((c >> 8) & 0xff) / 255f, (c & 0xff) / 255f, alpha);
                f.draw(d.batch, text, cx + shake, ty + shake * 0.5f, 0, Align.center, false);
                f.getData().setScale(1f / a.fontScale);
                f.setColor(1, 1, 1, 1);
            }
        }

        // faiscas
        d.additive(true);
        for (int i = 0; i < MAXP; i++) {
            if (plife[i] <= 0) continue;
            float k = plife[i] / pmax[i];
            d.rgb(pcol[i], k);
            float len = (float) Math.sqrt(pvx[i] * pvx[i] + pvy[i] * pvy[i]) * 0.03f + psize[i];
            d.line(cx + px[i] - pvx[i] * 0.02f, cy + py[i] - pvy[i] * 0.02f, cx + px[i], cy + py[i], psize[i] * k + 1);
            if (len > 0) d.soft(cx + px[i], cy + py[i], psize[i] * 1.6f * k);
        }
        d.additive(false);
    }

    private boolean anySparks() {
        for (int i = 0; i < MAXP; i++) if (plife[i] > 0) return true;
        return false;
    }

    private static void diamond(Draw d, float cx, float cy, float w, float h, float rot, int col, float alpha) {
        d.rgb(col, alpha);
        d.rect(cx - w / 2, cy - h / 2, w, h, w / 2, h / 2, rot);
    }
}
