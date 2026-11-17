package com.brigadajato.render;

import com.badlogic.gdx.graphics.GL20;
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
}
