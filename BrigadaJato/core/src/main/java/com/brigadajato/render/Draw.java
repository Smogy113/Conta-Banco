package com.brigadajato.render;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.brigadajato.Assets;

/** Atalhos para desenhar formas simples com o SpriteBatch (tudo num unico atlas). */
public class Draw {
    public final SpriteBatch batch;
    public final Assets a;
    private boolean additive;

    public Draw(SpriteBatch batch, Assets a) {
        this.batch = batch;
        this.a = a;
    }

    public Draw rgb(int rgb, float alpha) {
        batch.setColor(((rgb >> 16) & 0xff) / 255f, ((rgb >> 8) & 0xff) / 255f, (rgb & 0xff) / 255f, alpha);
        return this;
    }

    public Draw color(float r, float g, float b, float alpha) {
        batch.setColor(r, g, b, alpha);
        return this;
    }

    public void additive(boolean on) {
        if (on == additive) return;
        additive = on;
        if (on) batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        else batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    public void rect(float x, float y, float w, float h) {
        batch.draw(a.white, x, y, w, h);
    }

    /** Retangulo girado em torno de (ox, oy) relativo ao canto inferior esquerdo. */
    public void rect(float x, float y, float w, float h, float ox, float oy, float deg) {
        batch.draw(a.white, x, y, ox, oy, w, h, 1, 1, deg);
    }

    public void region(TextureRegion r, float cx, float cy, float w, float h, float deg) {
        batch.draw(r, cx - w / 2, cy - h / 2, w / 2, h / 2, w, h, 1, 1, deg);
    }

    public void line(float x1, float y1, float x2, float y2, float thick) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;
        float deg = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        batch.draw(a.white, x1, y1 - thick / 2, 0, thick / 2, len, thick, 1, 1, deg);
    }

    public void circle(float cx, float cy, float r) {
        batch.draw(a.circle, cx - r, cy - r, r * 2, r * 2);
    }

    public void soft(float cx, float cy, float r) {
        batch.draw(a.soft, cx - r, cy - r, r * 2, r * 2);
    }

    public void ring(float cx, float cy, float r) {
        batch.draw(a.ring, cx - r, cy - r, r * 2, r * 2);
    }

    public void ellipse(float cx, float cy, float rx, float ry, float deg) {
        batch.draw(a.circle, cx - rx, cy - ry, rx, ry, rx * 2, ry * 2, 1, 1, deg);
    }

    public void rrect(float x, float y, float w, float h) {
        batch.draw(a.rrect, x, y, w, h);
    }

    /** Barra com fundo (0..1). */
    public void bar(float x, float y, float w, float h, float frac, int fill, int back) {
        rgb(0x101018, 0.85f);
        rect(x - 2, y - 2, w + 4, h + 4);
        rgb(back, 0.9f);
        rect(x, y, w, h);
        rgb(fill, 1f);
        rect(x, y, w * MathUtils.clamp(frac, 0, 1), h);
        color(1, 1, 1, 0.25f);
        rect(x, y + h * 0.55f, w * MathUtils.clamp(frac, 0, 1), h * 0.3f);
    }

    // ------------------------------------------------------------------ pecas de interface

    private static final ObjectMap<TextureRegion, NinePatch> PATCHES = new ObjectMap<>();

    /** Desenha uma regiao como 9-patch (bordas nao esticam). */
    public static void patch(Draw d, TextureRegion r, float x, float y, float w, float h, int border) {
        NinePatch np = PATCHES.get(r);
        if (np == null) {
            np = new NinePatch(r, border, border, border, border);
            PATCHES.put(r, np);
        }
        float scale = Math.min(1f, Math.min(w, h) / (border * 2f));
        if (scale < 1f) {
            np.scale(scale, scale);
            np.draw(d.batch, x, y, w, h);
            np.scale(1 / scale, 1 / scale);
        } else np.draw(d.batch, x, y, w, h);
    }

    /** Repete uma textura (faixa de alerta) na horizontal. */
    public static void tiled(Draw d, TextureRegion r, float x, float y, float w, float h) {
        float tileW = h * r.getRegionWidth() / (float) r.getRegionHeight();
        float u = r.getU(), v = r.getV(), u2 = r.getU2(), v2 = r.getV2();
        for (float px = 0; px < w; px += tileW) {
            float pw = Math.min(tileW, w - px);
            float uu = u + (u2 - u) * (pw / tileW);
            d.batch.draw(r.getTexture(), x + px, y, pw, h, u, v2, uu, v);
        }
    }

    /** Barra com contorno e divisoes (segs = 0 sem divisoes). */
    public static void segBar(Draw d, float x, float y, float w, float h, float frac, int col, int segs) {
        frac = MathUtils.clamp(frac, 0, 1);
        d.rgb(0x0e0e14, 0.95f);
        d.rect(x - 2.5f, y - 2.5f, w + 5, h + 5);
        d.rgb(0x2a2f36, 1);
        d.rect(x, y, w, h);
        d.rgb(col, 1);
        d.rect(x, y, w * frac, h);
        d.color(1, 1, 1, 0.28f);
        d.rect(x, y + h * 0.6f, w * frac, h * 0.25f);
        d.color(0, 0, 0, 0.2f);
        d.rect(x, y, w * frac, h * 0.25f);
        if (segs > 1) {
            d.rgb(0x0e0e14, 0.9f);
            for (int i = 1; i < segs; i++) d.rect(x + w * i / segs - 1, y, 2, h);
        }
    }

    /** Caixa colorida com contorno (placar dos times). */
    public static void box(Draw d, float x, float y, float w, float h, int col, boolean highlight) {
        d.rgb(highlight ? 0xffd54f : 0x0e0e14, 1);
        d.rect(x - 3, y - 3, w + 6, h + 6);
        d.rgb(col, 1);
        d.rect(x, y, w, h);
        d.color(1, 1, 1, 0.22f);
        d.rect(x, y + h * 0.62f, w, h * 0.38f);
        d.color(0, 0, 0, 0.18f);
        d.rect(x, y, w, h * 0.2f);
    }
}
