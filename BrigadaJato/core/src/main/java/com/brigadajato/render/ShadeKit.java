package com.brigadajato.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

/**
 * Formas com sombreamento de desenho animado (luz de cima/esquerda, sombra chapada embaixo/direita)
 * em tons de cinza: o SpriteBatch tinge com a cor da peca. Bordas suavizadas.
 */
public final class ShadeKit {
    private ShadeKit() {
    }

    private static Texture tex;
    public static TextureRegion ball, pill, box, gloss;

    private static int px(float v, float a) {
        return Color.rgba8888(v, v, v, a);
    }

    public static void load() {
        if (tex != null) return;
        int W = 256, H = 256;
        Pixmap pm = new Pixmap(W, H, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        // bola 128x128 em (0,0)
        float R = 62;
        for (int y = 0; y < 128; y++)
            for (int x = 0; x < 128; x++) {
                float dx = x + 0.5f - 64, dy = y + 0.5f - 64; // y para baixo
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = MathUtils.clamp(R - d + 0.5f, 0, 1);
                if (a <= 0) continue;
                float nx = dx / R, ny = dy / R;
                // sombra: lado de baixo/direita (fora de um circulo deslocado para cima/esquerda)
                float sx = dx + 14, sy = dy + 16;
                boolean lit = sx * sx + sy * sy < (R + 2) * (R + 2);
                float v = lit ? 1f : 0.74f;
                // escurece levemente a borda
                v *= 1 - 0.08f * MathUtils.clamp((d - R * 0.8f) / (R * 0.2f), 0, 1);
                pm.drawPixel(x, y, px(v, a));
            }
        // pilula 128x48 em (128,0)
        for (int y = 0; y < 48; y++)
            for (int x = 0; x < 128; x++) {
                float rr = 23;
                float cx = MathUtils.clamp(x + 0.5f, rr + 1, 127 - rr), cy = 24;
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = MathUtils.clamp(rr - d + 0.5f, 0, 1);
                if (a <= 0) continue;
                float v = (y + 0.5f) > 31 ? 0.76f : 1f; // metade de baixo na sombra
                pm.drawPixel(128 + x, y, px(v, a));
            }
        // caixa arredondada 64x64 em (128,64)
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float r = 16;
                float cx = MathUtils.clamp(x + 0.5f, r, 64 - r), cy = MathUtils.clamp(y + 0.5f, r, 64 - r);
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = MathUtils.clamp(r - d + 0.5f, 0, 1);
                if (a <= 0) continue;
                boolean shade = y > 46 || x > 50;
                float v = shade ? 0.77f : 1f;
                pm.drawPixel(128 + x, 64 + y, px(v, a));
            }
        // brilho (oval branco suave) 64x64 em (192,64)
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float dx = (x + 0.5f - 32) / 30f, dy = (y + 0.5f - 32) / 30f;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = MathUtils.clamp((1 - d) * 3f, 0, 1);
                if (a <= 0) continue;
                pm.drawPixel(192 + x, 64 + y, Color.rgba8888(1, 1, 1, a));
            }
        tex = new Texture(pm, true);
        tex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        pm.dispose();
        ball = new TextureRegion(tex, 0, 0, 128, 128);
        pill = new TextureRegion(tex, 128, 0, 128, 48);
        box = new TextureRegion(tex, 128, 64, 64, 64);
        gloss = new TextureRegion(tex, 192, 64, 64, 64);
    }
}
