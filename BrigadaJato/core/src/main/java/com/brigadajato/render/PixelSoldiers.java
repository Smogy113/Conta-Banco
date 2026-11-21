package com.brigadajato.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;

import java.nio.IntBuffer;

/**
 * Bonecos em pixel art de verdade: cada soldado e pintado numa celula pequena (1 pixel = TEXEL
 * unidades do mundo), depois um passo corta as bordas (sem meio-transparente) e poe um contorno de
 * 1 pixel em volta. Na hora de desenhar, a celula e ampliada sem filtro (pixels nitidos).
 * <p>
 * No jogo todos os bonecos visiveis sao pintados de uma vez no comeco do quadro (uma troca de
 * framebuffer so); nas telas de menu cada boneco e pintado na hora.
 */
public final class PixelSoldiers implements Disposable {
    /** unidades do mundo por pixel do boneco */
    public static final float TEXEL = 1.3f;
    /** tamanho da celula em pixels e altura do pe dentro dela */
    public static final int CW = 96, CH = 100, FOOT = 30;
    private final int cols, rows, W, H;
    private final FrameBuffer raw, out;
    private final TextureRegion[] cells;
    private int used;
    private static ShaderProgram cut;

    private final Matrix4 proj = new Matrix4(), saveProj = new Matrix4(), saveTrans = new Matrix4(), trans = new Matrix4();
    private final IntBuffer ib = BufferUtils.newIntBuffer(16);
    private int prevFbo;
    private final int[] prevView = new int[4];
    private ShaderProgram prevShader;
    private Texture.TextureFilter[] savedMin = new Texture.TextureFilter[2], savedMag = new Texture.TextureFilter[2];
    private Texture[] shapeTex = new Texture[2];

    public PixelSoldiers(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        W = cols * CW;
        H = rows * CH;
        raw = new FrameBuffer(Pixmap.Format.RGBA8888, W, H, false);
        out = new FrameBuffer(Pixmap.Format.RGBA8888, W, H, false);
        raw.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        out.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        cells = new TextureRegion[cols * rows];
        Texture t = out.getColorBufferTexture();
        for (int i = 0; i < cells.length; i++) {
            int x0 = (i % cols) * CW, y0 = (i / cols) * CH;
            cells[i] = new TextureRegion(t);
            cells[i].setRegion(x0 / (float) W, (y0 + CH) / (float) H, (x0 + CW) / (float) W, y0 / (float) H);
        }
        if (cut == null) {
            ShaderProgram.pedantic = false;
            cut = new ShaderProgram(VERT, FRAG);
            if (!cut.isCompiled()) Gdx.app.error("PixelSoldiers", cut.getLog());
        }
    }

    /** arredonda para a grade de pixels do boneco */
    public static float snap(float v) {
        return Math.round(v / TEXEL) * TEXEL;
    }

    public int capacity() {
        return cells.length;
    }

    public boolean full() {
        return used >= cells.length;
    }

    /** depois de perder o contexto (Android) o framebuffer ganha texturas novas: refaz as celulas */
    private void refresh() {
        Texture t = out.getColorBufferTexture();
        if (cells[0].getTexture() == t) return;
        raw.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        for (TextureRegion c : cells) c.setTexture(t);
    }

    /** Comeca a pintar (o batch NAO pode estar desenhando). */
    public void begin(SpriteBatch b, Draw d) {
        refresh();
        used = 0;
        saveProj.set(b.getProjectionMatrix());
        saveTrans.set(b.getTransformMatrix());
        prevShader = b.getShader();
        ib.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, ib);
        prevFbo = ib.get(0);
        ib.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, ib);
        for (int i = 0; i < 4; i++) prevView[i] = ib.get(i);
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, raw.getFramebufferHandle());
        Gdx.gl.glViewport(0, 0, W, H);
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        // formas pintadas sem filtro: bordas secas, sombreado em 2 tons nitido
        ShadeKit.load();
        shapeTex[0] = d.a.white.getTexture();
        shapeTex[1] = ShadeKit.ball.getTexture();
        for (int i = 0; i < 2; i++) {
            savedMin[i] = shapeTex[i].getMinFilter();
            savedMag[i] = shapeTex[i].getMagFilter();
            shapeTex[i].setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        proj.setToOrtho2D(0, 0, W, H);
        b.setShader(null);
        b.setProjectionMatrix(proj);
        b.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        b.begin();
    }

    /** Pinta o boneco numa celula livre; devolve o indice (ou -1 se acabou o espaco). */
    public int paint(SoldierPainter p, Draw d, SoldierPainter.Pose s) {
        if (used >= cells.length) return -1;
        int i = used++;
        float cx = (i % cols) * CW + CW / 2f, cy = (i / cols) * CH + FOOT;
        trans.idt().translate(cx, cy, 0).scale(1 / TEXEL, 1 / TEXEL, 1).translate(-snap(s.x), -snap(s.y), 0);
        d.batch.setTransformMatrix(trans);
        p.drawBody(d, s);
        return i;
    }

    /** Termina: recorta/contorna e volta para onde estava. */
    public void end(SpriteBatch b) {
        b.end();
        b.setTransformMatrix(trans.idt());
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, out.getFramebufferHandle());
        Gdx.gl.glViewport(0, 0, W, H);
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        b.setShader(cut);
        b.disableBlending();
        b.begin();
        cut.setUniformf("u_px", 1f / W, 1f / H);
        int usedRows = (used + cols - 1) / cols;
        float hUsed = usedRows * CH;
        b.setColor(1, 1, 1, 1);
        b.draw(raw.getColorBufferTexture(), 0, 0, W, hUsed, 0, 0, 1, hUsed / H);
        b.end();
        b.enableBlending();
        b.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        b.setShader(prevShader);
        for (int i = 0; i < 2; i++) shapeTex[i].setFilter(savedMin[i], savedMag[i]);
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, prevFbo);
        Gdx.gl.glViewport(prevView[0], prevView[1], prevView[2], prevView[3]);
        b.setProjectionMatrix(saveProj);
        b.setTransformMatrix(saveTrans);
    }

    public TextureRegion cell(int i) {
        return cells[i];
    }

    /** Desenha a celula com o pe em (x, y); escala/giro em volta do pe ou de (ox, oy) relativo ao pe. */
    public void draw(SpriteBatch b, int i, float x, float y, float alpha, float scale, float rot, float ox, float oy) {
        float w = CW * TEXEL, h = CH * TEXEL;
        float fx = CW / 2f * TEXEL, fy = FOOT * TEXEL;
        b.setColor(1, 1, 1, alpha);
        b.draw(cells[i], snap(x) - fx, snap(y) - fy, fx + ox, fy + oy, w, h, scale, scale, rot);
    }

    // ---- modo "na hora" (telas de menu)
    private static PixelSoldiers single;

    public static boolean ready() {
        return Gdx.gl != null && Gdx.graphics != null;
    }

    /** Pinta e desenha um boneco agora mesmo, respeitando a matriz de transformacao atual do batch. */
    public static void drawNow(Draw d, SoldierPainter p, SoldierPainter.Pose s) {
        if (single == null) single = new PixelSoldiers(1, 1);
        SpriteBatch b = d.batch;
        boolean drawing = b.isDrawing();
        if (drawing) b.end();
        float alpha = s.alpha;
        s.alpha = 1;
        single.begin(b, d);
        single.paint(p, d, s);
        single.end(b);
        s.alpha = alpha;
        b.begin();
        p.drawShadow(d, s);
        single.draw(b, 0, s.x, s.y, alpha, 1, 0, 0, 0);
        p.drawFx(d, s);
        if (!drawing) b.end();
    }

    @Override
    public void dispose() {
        raw.dispose();
        out.dispose();
    }

    private static final String VERT = "attribute vec4 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n"
            + "attribute vec4 " + ShaderProgram.COLOR_ATTRIBUTE + ";\n"
            + "attribute vec2 " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n"
            + "uniform mat4 u_projTrans;\n"
            + "varying vec2 v_tc;\n"
            + "void main(){ v_tc = " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0; gl_Position = u_projTrans * " + ShaderProgram.POSITION_ATTRIBUTE + "; }\n";
    private static final String FRAG = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform vec2 u_px;\n"
            + "varying vec2 v_tc;\n"
            + "void main(){\n"
            + "  vec4 c = texture2D(u_texture, v_tc);\n"
            + "  if (c.a >= 0.5) { gl_FragColor = vec4(c.rgb / c.a, 1.0); return; }\n"
            + "  float n = max(max(texture2D(u_texture, v_tc + vec2(u_px.x, 0.0)).a, texture2D(u_texture, v_tc - vec2(u_px.x, 0.0)).a),\n"
            + "                max(texture2D(u_texture, v_tc + vec2(0.0, u_px.y)).a, texture2D(u_texture, v_tc - vec2(0.0, u_px.y)).a));\n"
            + "  gl_FragColor = n >= 0.5 ? vec4(0.075, 0.06, 0.09, 1.0) : vec4(0.0);\n"
            + "}\n";
}
