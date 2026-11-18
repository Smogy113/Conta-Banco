package com.brigadajato.render;

import com.badlogic.gdx.math.MathUtils;
import com.brigadajato.Assets;
import com.brigadajato.world.Medals;

/** Desenho de medalha (fita + disco sombreado + simbolo). Usado nas medalhas e nas conquistas. */
public final class MedalArt {
    private MedalArt() {
    }

    private static final int INK = 0x15151c;

    /** sym = um dos simbolos de {@link Medals} (MVP, ACCURACY...). */
    public static void draw(Draw d, Assets a, int sym, int col, float cx, float cy, float s, float alpha, boolean locked, float time) {
        ShadeKit.load();
        if (locked) col = 0x546e7a;
        int dark = SoldierPainter.mix(col, 0x000000, 0.35f), light = SoldierPainter.mix(col, 0xffffff, 0.35f);
        // fita
        for (int i = -1; i <= 1; i += 2) {
            d.rgb(INK, alpha);
            d.rect(cx + i * s * 0.16f - s * 0.13f, cy + s * 0.08f, s * 0.26f, s * 0.6f, s * 0.13f, 0, i * 18);
            d.rgb(locked ? 0x37474f : (i < 0 ? 0xc62828 : 0x1565c0), alpha);
            d.rect(cx + i * s * 0.16f - s * 0.1f, cy + s * 0.1f, s * 0.2f, s * 0.56f, s * 0.1f, 0, i * 18);
        }
        // disco
        d.rgb(INK, alpha);
        d.circle(cx, cy, s * 0.44f);
        d.rgb(dark, alpha);
        d.circle(cx, cy, s * 0.4f);
        d.rgb(col, alpha);
        d.region(ShadeKit.ball, cx, cy, s * 0.72f, s * 0.72f, 0);
        d.rgb(light, alpha * 0.8f);
        d.ring(cx, cy, s * 0.3f);
        // simbolo
        float is = s * 0.42f;
        int ink = locked ? 0x90a4ae : 0xffffff;
        switch (sym) {
            case Medals.MVP: Icons.star(d, cx, cy, is, locked ? 0x90a4ae : 0xfff8e1); break;
            case Medals.ACCURACY: Icons.crosshair(d, cx, cy, is, 0, ink, alpha); break;
            case Medals.DAMAGE: Icons.flame(d, cx, cy, is, locked ? 0x90a4ae : 0xffab40); break;
            case Medals.STREAK: Icons.bolt(d, cx, cy, is, locked ? 0x90a4ae : 0xfff59d); break;
            case Medals.HEADSHOT: Icons.skull(d, cx, cy, is, ink); break;
            case Medals.LONG: Icons.magnifier(d, cx, cy, is, ink, alpha); break;
            case Medals.MELEE: Icons.fist(d, cx, cy, is, 0xe0ac69); break;
            case Medals.BOOM: Icons.grenade(d, cx, cy, is); break;
            case Medals.SURVIVOR: Icons.heart(d, cx, cy, is, locked ? 0x90a4ae : 0xef5350); break;
            case Medals.OBJECTIVE: Icons.flag(d, cx, cy, is, ink, time); break;
            case Medals.KAMIKAZE: Icons.bomb(d, cx, cy, is, ((int) (time * 2)) % 2 == 0); break;
            case Medals.COMBO: {
                a.normal.setColor(1, 1, 1, alpha);
                a.normal.draw(d.batch, "x2", cx, cy + a.normal.getCapHeight() / 2, 0, com.badlogic.gdx.utils.Align.center, false);
                a.normal.setColor(1, 1, 1, 1);
                break;
            }
            case Medals.TANK: Icons.shield(d, cx, cy, is, ink); break;
            default: Icons.star(d, cx, cy, is, ink);
        }
        if (locked) { // cadeado
            d.rgb(INK, alpha);
            d.rect(cx + s * 0.12f, cy - s * 0.42f, s * 0.3f, s * 0.24f);
            d.rgb(0xb0bec5, alpha);
            d.rect(cx + s * 0.145f, cy - s * 0.395f, s * 0.25f, s * 0.19f);
            d.rgb(INK, alpha);
            d.ring(cx + s * 0.27f, cy - s * 0.15f, s * 0.09f);
        } else {
            // brilho passando
            float k = (time * 0.5f + cx * 0.001f) % 1.6f;
            if (k < 1) {
                d.rgb(0xffffff, 0.35f * MathUtils.sin(k * MathUtils.PI) * alpha);
                d.rect(cx - s * 0.36f + k * s * 0.6f, cy - s * 0.3f, s * 0.07f, s * 0.6f, s * 0.035f, s * 0.3f, -25);
            }
        }
        d.batch.setColor(1, 1, 1, 1);
    }
}
