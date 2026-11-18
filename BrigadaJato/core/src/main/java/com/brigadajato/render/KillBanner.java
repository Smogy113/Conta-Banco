package com.brigadajato.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.brigadajato.Assets;
import com.brigadajato.I18n;
import com.brigadajato.world.C;
import com.brigadajato.world.WeaponType;

/**
 * Emblema de abates em sequencia: a cada abate seguido aparece um emblema maior, com mais
 * "asas", cor mais quente e um som mais agudo (estilo dos jogos taticos).
 */
public class KillBanner {
    private static final int[] COLORS = {0x4dd0e1, 0x40c4ff, 0xffd54f, 0xff9100, 0xff1744};
    private static final int INK = 0x101018;

    private final Assets a;
    private float t = 99, life;
    private int level;
    private int code;
    private boolean head;
    private float spin;

    public KillBanner(Assets a) {
        this.a = a;
    }

    public void trigger(int level, int code, boolean head) {
        this.level = Math.max(1, level);
        this.code = code;
        this.head = head;
        t = 0;
        life = 1.8f + Math.min(5, this.level) * 0.1f;
    }

    public boolean active() {
        return t < life;
    }

    public void update(float dt) {
        t += dt;
        spin += dt * 25;
    }

    private int color() {
        return COLORS[Math.min(level, COLORS.length) - 1];
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

    /** Brilho nas bordas da tela no momento do abate. */
    public void drawEdgeGlow(Draw d, float W, float H) {
        if (t > 0.45f) return;
        float k = 1 - t / 0.45f;
        int c = color();
        d.rgb(c, 0.22f * k);
        float e = 26 + level * 6;
        d.rect(0, 0, W, e * k);
        d.rect(0, H - e * k, W, e * k);
        d.rect(0, 0, e * k, H);
        d.rect(W - e * k, 0, e * k, H);
    }

    public void draw(Draw d, float cx, float cy) {
        if (!active()) return;
        int c = color();
        float fade = MathUtils.clamp((life - t) / 0.35f, 0, 1);
        float scale = 1 + 0.65f * (float) Math.exp(-t * 9) * MathUtils.cos(t * 18);
        cy += t * 6;
        float S = 46 * scale;
        int lv = Math.min(level, 5);

        // brilho e raios de luz
        d.additive(true);
        d.rgb(c, 0.45f * fade);
        d.soft(cx, cy, S * 2.2f);
        int rays = 6 + lv * 2;
        for (int i = 0; i < rays; i++) {
            float ang = spin * (i % 2 == 0 ? 1 : -0.6f) + i * 360f / rays;
            float len = S * (1.5f + lv * 0.25f);
            d.rgb(c, 0.22f * fade);
            d.batch.draw(a.tri, cx - S * 0.12f, cy, S * 0.12f, 0, S * 0.24f, len, 1, 1, ang);
        }
        if (t < 0.35f) { // anel que explode para fora
            float k = t / 0.35f;
            d.rgb(0xffffff, (1 - k) * 0.9f);
            d.ring(cx, cy, S * (0.6f + k * 2.2f));
        }
        if (t < 0.12f) {
            d.rgb(0xffffff, (1 - t / 0.12f) * 0.8f);
            d.soft(cx, cy, S * 1.6f);
        }
        d.additive(false);

        // asas: uma "lamina" a mais de cada lado por nivel
        for (int i = 0; i < lv; i++) {
            float off = S * 0.62f + i * S * 0.26f;
            float h = S * (0.62f - i * 0.06f);
            for (int side = -1; side <= 1; side += 2) {
                float x0 = cx + side * off;
                d.rgb(INK, fade);
                d.line(x0, cy + h, x0 + side * S * 0.32f, cy, S * 0.2f);
                d.line(x0 + side * S * 0.32f, cy, x0, cy - h, S * 0.2f);
                d.rgb(i == lv - 1 ? 0xffffff : c, fade);
                d.line(x0, cy + h, x0 + side * S * 0.32f, cy, S * 0.11f);
                d.line(x0 + side * S * 0.32f, cy, x0, cy - h, S * 0.11f);
            }
        }

        // losango central
        d.rgb(INK, fade);
        d.rect(cx - S * 0.62f, cy - S * 0.62f, S * 1.24f, S * 1.24f, S * 0.62f, S * 0.62f, 45);
        d.rgb(SoldierPainter.darker(c, 0.55f), fade);
        d.rect(cx - S * 0.52f, cy - S * 0.52f, S * 1.04f, S * 1.04f, S * 0.52f, S * 0.52f, 45);
        d.rgb(c, fade);
        d.rect(cx - S * 0.44f, cy - S * 0.44f, S * 0.88f, S * 0.88f, S * 0.44f, S * 0.44f, 45);
        d.color(1, 1, 1, 0.35f * fade);
        d.rect(cx - S * 0.3f, cy, S * 0.6f, S * 0.3f, S * 0.3f, 0, 0);

        // icone do tipo de abate
        float is = S * 0.62f;
        if (code == C.KILL_MELEE) Icons.fist(d, cx, cy, is, 0xf1c27d);
        else if (code == C.KILL_FRAG || code == C.KILL_MINE || code == C.KILL_BOMB
                || (WeaponType.of(code) != null && WeaponType.of(code).isExplosive())) Icons.bomb(d, cx, cy, is, true);
        else Icons.skull(d, cx, cy, is, 0xffffff);
        if (head) {
            d.rgb(0xff1744, fade);
            d.ring(cx, cy + S * 0.05f, S * 0.42f);
            d.rect(cx - S * 0.02f, cy + S * 0.3f, S * 0.04f, S * 0.22f);
            d.rect(cx - S * 0.02f, cy - S * 0.42f, S * 0.04f, S * 0.22f);
        }

        // contadores (1 a 5)
        for (int i = 0; i < 5; i++) {
            float px = cx + (i - 2) * 20, py = cy - S * 0.95f - 14;
            d.rgb(INK, fade);
            d.rect(px - 7, py - 7, 14, 14, 7, 7, 45);
            d.rgb(i < lv ? (i == lv - 1 ? 0xffffff : c) : 0x37474f, fade);
            d.rect(px - 5, py - 5, 10, 10, 5, 5, 45);
        }

        String text = label(level);
        if (text != null) {
            BitmapFont f = a.big;
            float s = 0.9f + 0.4f * (float) Math.exp(-t * 10);
            f.getData().setScale(s / a.fontScale);
            f.setColor(((c >> 16) & 0xff) / 255f, ((c >> 8) & 0xff) / 255f, (c & 0xff) / 255f, fade);
            f.draw(d.batch, text, cx, cy - S * 0.95f - 34, 0, Align.center, false);
            f.getData().setScale(1f / a.fontScale);
            f.setColor(1, 1, 1, 1);
        }
    }
}
