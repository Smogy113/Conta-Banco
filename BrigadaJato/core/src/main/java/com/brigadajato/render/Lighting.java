package com.brigadajato.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

/**
 * Mapa de luz: escurece a cena (noite / lugares fechados) e acende pontos de luz - explosoes,
 * tiros, jetpacks, lampadas, lava. Desenha as luzes numa textura pequena e multiplica por cima.
 */
public class Lighting implements Disposable {
    private static final int MAX = 400;
    private final float[] lx = new float[MAX], ly = new float[MAX], lr = new float[MAX], lcr = new float[MAX], lcg = new float[MAX],
            lcb = new float[MAX], life = new float[MAX], maxLife = new float[MAX];
    private int count;
    // luzes de um quadro so
    private final float[] fx = new float[MAX], fy = new float[MAX], fr = new float[MAX], fcr = new float[MAX], fcg = new float[MAX], fcb = new float[MAX];
    private int fcount;
    private FrameBuffer fbo;
    private TextureRegion fboRegion;
    private int fw, fh;
    private final Matrix4 screen = new Matrix4();
    private boolean failed;

    /** Luz que some sozinha (explosao, clarao). */
    public void flash(float x, float y, float radius, int rgb, float intensity, float duration) {
        if (count >= MAX) return;
        int i = count++;
        lx[i] = x;
        ly[i] = y;
        lr[i] = radius;
        lcr[i] = ((rgb >> 16) & 0xff) / 255f * intensity;
        lcg[i] = ((rgb >> 8) & 0xff) / 255f * intensity;
        lcb[i] = (rgb & 0xff) / 255f * intensity;
        life[i] = maxLife[i] = duration;
    }

    /** Luz so deste quadro. */
    public void light(float x, float y, float radius, int rgb, float intensity) {
        if (fcount >= MAX) return;
        int i = fcount++;
        fx[i] = x;
        fy[i] = y;
        fr[i] = radius;
        fcr[i] = ((rgb >> 16) & 0xff) / 255f * intensity;
        fcg[i] = ((rgb >> 8) & 0xff) / 255f * intensity;
        fcb[i] = (rgb & 0xff) / 255f * intensity;
    }

    /**
     * Versao barata (sem textura extra): escurece a tela com um retangulo e soma brilhos por cima.
     * Usada em qualidade baixa/media ou sem luzes dinamicas.
     */
    public void renderCheap(SpriteBatch batch, TextureRegion soft, TextureRegion white, OrthographicCamera cam, float ar, float ag, float ab) {
        float dark = 1 - Math.min(ar, Math.min(ag, ab));
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        float vw = cam.viewportWidth * cam.zoom, vh = cam.viewportHeight * cam.zoom;
        float x0 = cam.position.x - vw / 2, y0 = cam.position.y - vh / 2;
        batch.setColor(0.02f * ar, 0.03f * ag, 0.08f * ab, dark * 0.85f);
        batch.draw(white, x0, y0, vw, vh);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (int i = 0; i < count; i++) {
            float k = life[i] / maxLife[i];
            batch.setColor(lcr[i], lcg[i], lcb[i], k * k * 0.5f * dark);
            float r = lr[i] * 0.6f;
            batch.draw(soft, lx[i] - r, ly[i] - r, r * 2, r * 2);
        }
        for (int i = 0; i < fcount; i++) {
            batch.setColor(fcr[i], fcg[i], fcb[i], 0.28f * dark);
            float r = fr[i] * 0.55f;
            batch.draw(soft, fx[i] - r, fy[i] - r, r * 2, r * 2);
        }
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setColor(1, 1, 1, 1);
        fcount = 0;
    }

    public void update(float dt) {
        for (int i = count - 1; i >= 0; i--) {
            life[i] -= dt;
            if (life[i] <= 0) {
                int last = --count;
                lx[i] = lx[last];
                ly[i] = ly[last];
                lr[i] = lr[last];
                lcr[i] = lcr[last];
                lcg[i] = lcg[last];
                lcb[i] = lcb[last];
                life[i] = life[last];
                maxLife[i] = maxLife[last];
            }
        }
    }

    public void clear() {
        count = 0;
        fcount = 0;
    }

    /**
     * Desenha o mapa de luz por cima do que ja esta na tela. Chamar com o batch fechado.
     * @param ar,ag,ab cor do ambiente (1,1,1 = sem escurecer)
     */
    public void render(SpriteBatch batch, TextureRegion soft, OrthographicCamera cam, float ar, float ag, float ab) {
        if (failed) {
            fcount = 0;
            return;
        }
        int w = Math.max(64, Gdx.graphics.getBackBufferWidth() / 6), h = Math.max(36, Gdx.graphics.getBackBufferHeight() / 6);
        if (fbo == null || fw != w || fh != h) {
            if (fbo != null) fbo.dispose();
            try {
                fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
            } catch (Exception e) {
                failed = true;
                fcount = 0;
                return;
            }
            fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            fboRegion = new TextureRegion(fbo.getColorBufferTexture());
            fboRegion.flip(false, true);
            fw = w;
            fh = h;
        }
        fbo.begin();
        Gdx.gl.glClearColor(ar, ag, ab, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(cam.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        for (int i = 0; i < count; i++) {
            float k = life[i] / maxLife[i];
            k = k * k;
            batch.setColor(lcr[i] * k, lcg[i] * k, lcb[i] * k, 1);
            float r = lr[i] * (0.7f + 0.3f * k);
            batch.draw(soft, lx[i] - r, ly[i] - r, r * 2, r * 2);
        }
        for (int i = 0; i < fcount; i++) {
            batch.setColor(fcr[i], fcg[i], fcb[i], 1);
            batch.draw(soft, fx[i] - fr[i], fy[i] - fr[i], fr[i] * 2, fr[i] * 2);
        }
        batch.end();
        fbo.end();
        fcount = 0;
        // multiplica a cena pela luz
        screen.setToOrtho2D(0, 0, 1, 1);
        batch.setProjectionMatrix(screen);
        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO);
        batch.begin();
        batch.setColor(1, 1, 1, 1);
        batch.draw(fboRegion, 0, 0, 1, 1);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void dispose() {
        if (fbo != null) fbo.dispose();
    }
}
