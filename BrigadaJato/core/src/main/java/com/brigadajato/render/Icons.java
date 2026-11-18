package com.brigadajato.render;

import com.badlogic.gdx.math.MathUtils;

/** Icones desenhados com formas (sem simbolos de fonte). Todos centrados em (x, y), tamanho s. */
public final class Icons {
    private Icons() {
    }

    private static final int INK = 0x15151c;

    public static void heart(Draw d, float x, float y, float s, int col) {
        for (int pass = 0; pass < 2; pass++) {
            float o = pass == 0 ? s * 0.09f : 0;
            d.rgb(pass == 0 ? INK : col, 1);
            d.circle(x - s * 0.22f, y + s * 0.12f, s * 0.26f + o);
            d.circle(x + s * 0.22f, y + s * 0.12f, s * 0.26f + o);
            d.region(d.a.tri, x, y - s * 0.16f, s * 0.95f + o * 2.2f, s * 0.62f + o * 2, 180);
        }
        d.rgb(0xffffff, 0.55f);
        d.circle(x - s * 0.26f, y + s * 0.2f, s * 0.08f);
    }

    public static void flame(Draw d, float x, float y, float s, int col) {
        d.rgb(INK, 1);
        d.circle(x, y - s * 0.12f, s * 0.34f);
        d.region(d.a.tri, x, y + s * 0.2f, s * 0.6f, s * 0.7f, 0);
        d.rgb(col, 1);
        d.circle(x, y - s * 0.12f, s * 0.26f);
        d.region(d.a.tri, x, y + s * 0.18f, s * 0.44f, s * 0.58f, 0);
        d.rgb(0xfff59d, 1);
        d.circle(x, y - s * 0.18f, s * 0.12f);
    }

    public static void grenade(Draw d, float x, float y, float s) {
        d.rgb(INK, 1);
        d.circle(x, y - s * 0.08f, s * 0.38f);
        d.rect(x - s * 0.16f, y + s * 0.18f, s * 0.32f, s * 0.26f);
        d.rgb(0x558b2f, 1);
        d.circle(x, y - s * 0.08f, s * 0.3f);
        d.rgb(0x9e9e9e, 1);
        d.rect(x - s * 0.1f, y + s * 0.22f, s * 0.2f, s * 0.16f);
        d.rgb(0xffffff, 0.4f);
        d.circle(x - s * 0.1f, y + s * 0.02f, s * 0.08f);
    }

    public static void mine(Draw d, float x, float y, float s, boolean light) {
        d.rgb(INK, 1);
        d.batch.draw(d.a.half, x - s * 0.46f, y - s * 0.26f, s * 0.92f, s * 0.5f);
        d.rgb(0x546e7a, 1);
        d.batch.draw(d.a.half, x - s * 0.38f, y - s * 0.2f, s * 0.76f, s * 0.4f);
        d.rgb(light ? 0xff1744 : 0x5d0000, 1);
        d.circle(x, y + s * 0.08f, s * 0.08f);
    }

    public static void skull(Draw d, float x, float y, float s, int col) {
        d.rgb(INK, 1);
        d.circle(x, y + s * 0.06f, s * 0.42f);
        d.rect(x - s * 0.26f, y - s * 0.42f, s * 0.52f, s * 0.32f);
        d.rgb(col, 1);
        d.circle(x, y + s * 0.06f, s * 0.34f);
        d.rect(x - s * 0.19f, y - s * 0.36f, s * 0.38f, s * 0.3f);
        d.rgb(INK, 1);
        d.circle(x - s * 0.14f, y + s * 0.04f, s * 0.1f);
        d.circle(x + s * 0.14f, y + s * 0.04f, s * 0.1f);
        d.rect(x - s * 0.03f, y - s * 0.32f, s * 0.06f, s * 0.14f);
    }

    public static void fist(Draw d, float x, float y, float s, int skin) {
        d.rgb(INK, 1);
        d.rrect(x - s * 0.42f, y - s * 0.32f, s * 0.84f, s * 0.64f);
        d.rgb(skin, 1);
        d.rrect(x - s * 0.34f, y - s * 0.24f, s * 0.68f, s * 0.48f);
        d.rgb(INK, 0.6f);
        for (int i = 1; i < 4; i++) d.rect(x - s * 0.34f + i * s * 0.17f, y, s * 0.03f, s * 0.24f);
    }

    public static void bomb(Draw d, float x, float y, float s, boolean blink) {
        d.rgb(INK, 1);
        d.rect(x - s * 0.44f, y - s * 0.3f, s * 0.88f, s * 0.6f);
        d.rgb(0x8b0000, 1);
        d.rect(x - s * 0.36f, y - s * 0.22f, s * 0.72f, s * 0.44f);
        d.rgb(0x212121, 1);
        d.rect(x - s * 0.22f, y - s * 0.1f, s * 0.36f, s * 0.22f);
        d.rgb(blink ? 0xff1744 : 0x4a0000, 1);
        d.circle(x + s * 0.24f, y + s * 0.1f, s * 0.07f);
    }

    public static void spikes(Draw d, float x, float y, float s) {
        d.rgb(0x90a4ae, 1);
        for (int i = -1; i <= 1; i++) d.region(d.a.tri, x + i * s * 0.3f, y, s * 0.3f, s * 0.6f, 0);
    }

    public static void swap(Draw d, float x, float y, float s, float alpha) {
        d.rgb(0xffffff, alpha);
        d.rect(x - s * 0.36f, y + s * 0.1f, s * 0.5f, s * 0.12f);
        d.region(d.a.tri, x + s * 0.24f, y + s * 0.16f, s * 0.3f, s * 0.3f, -90);
        d.rect(x - s * 0.14f, y - s * 0.22f, s * 0.5f, s * 0.12f);
        d.region(d.a.tri, x - s * 0.24f, y - s * 0.16f, s * 0.3f, s * 0.3f, 90);
    }

    public static void reload(Draw d, float x, float y, float s, float alpha) {
        d.rgb(0x15151c, alpha);
        d.ring(x, y, s * 0.36f);
        d.rgb(0x15151c, alpha);
        d.region(d.a.tri, x + s * 0.34f, y + s * 0.06f, s * 0.3f, s * 0.26f, 180);
    }

    public static void magnifier(Draw d, float x, float y, float s, int col, float alpha) {
        d.rgb(col, alpha);
        d.ring(x - s * 0.08f, y + s * 0.08f, s * 0.3f);
        d.line(x + s * 0.12f, y - s * 0.12f, x + s * 0.4f, y - s * 0.4f, s * 0.13f);
    }

    public static void bolt(Draw d, float x, float y, float s, int col) {
        d.rgb(col, 1);
        d.region(d.a.tri, x - s * 0.08f, y + s * 0.12f, s * 0.36f, s * 0.6f, -20);
        d.region(d.a.tri, x + s * 0.08f, y - s * 0.12f, s * 0.36f, s * 0.6f, 160);
    }

    public static void shield(Draw d, float x, float y, float s, int col) {
        d.rgb(INK, 1);
        d.rect(x - s * 0.36f, y - s * 0.05f, s * 0.72f, s * 0.42f);
        d.region(d.a.tri, x, y - s * 0.2f, s * 0.72f, s * 0.4f, 180);
        d.rgb(col, 1);
        d.rect(x - s * 0.28f, y - s * 0.02f, s * 0.56f, s * 0.32f);
        d.region(d.a.tri, x, y - s * 0.16f, s * 0.56f, s * 0.3f, 180);
    }

    public static void star(Draw d, float x, float y, float s, int col) {
        d.rgb(INK, 1);
        d.region(d.a.star, x, y, s * 1.1f, s * 1.1f, 0);
        d.rgb(col, 1);
        d.region(d.a.star, x, y, s * 0.85f, s * 0.85f, 0);
    }

    public static void person(Draw d, float x, float y, float s, int col) {
        d.rgb(col, 1);
        d.circle(x, y + s * 0.22f, s * 0.2f);
        d.batch.draw(d.a.half, x - s * 0.34f, y - s * 0.38f, s * 0.68f, s * 0.42f);
    }

    public static void flag(Draw d, float x, float y, float s, int col, float time) {
        d.rgb(INK, 1);
        d.rect(x - s * 0.3f, y - s * 0.45f, s * 0.08f, s * 0.9f);
        float w = MathUtils.sin(time * 6) * s * 0.04f;
        d.rgb(col, 1);
        d.rect(x - s * 0.22f, y + s * 0.05f + w, s * 0.55f, s * 0.36f);
    }

    public static void crosshair(Draw d, float x, float y, float s, int style, int col, float alpha) {
        d.rgb(0x000000, alpha * 0.55f);
        switch (style) {
            case 1: // ponto
                d.circle(x, y, s * 0.16f);
                d.rgb(col, alpha);
                d.circle(x, y, s * 0.11f);
                break;
            case 2: // cruz aberta
                for (int k = 0; k < 2; k++) {
                    float t = k == 0 ? s * 0.12f : s * 0.07f;
                    if (k == 1) d.rgb(col, alpha);
                    d.rect(x - t / 2, y + s * 0.18f, t, s * 0.4f);
                    d.rect(x - t / 2, y - s * 0.58f, t, s * 0.4f);
                    d.rect(x + s * 0.18f, y - t / 2, s * 0.4f, t);
                    d.rect(x - s * 0.58f, y - t / 2, s * 0.4f, t);
                }
                break;
            case 3: // circulo
                d.ring(x, y, s * 0.52f);
                d.rgb(col, alpha);
                d.ring(x, y, s * 0.48f);
                d.circle(x, y, s * 0.06f);
                break;
            case 4: // chevron (V)
                d.rgb(col, alpha);
                d.line(x - s * 0.35f, y - s * 0.3f, x, y, s * 0.08f);
                d.line(x + s * 0.35f, y - s * 0.3f, x, y, s * 0.08f);
                break;
            default: // classico: anel com tracos
                d.ring(x, y, s * 0.5f);
                d.rgb(col, alpha);
                d.ring(x, y, s * 0.45f);
                d.rect(x - s * 0.05f, y + s * 0.35f, s * 0.1f, s * 0.35f);
                d.rect(x - s * 0.05f, y - s * 0.7f, s * 0.1f, s * 0.35f);
                d.rect(x + s * 0.35f, y - s * 0.05f, s * 0.35f, s * 0.1f);
                d.rect(x - s * 0.7f, y - s * 0.05f, s * 0.35f, s * 0.1f);
                break;
        }
    }
}
