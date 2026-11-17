package com.brigadajato;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Todos os recursos graficos sao desenhados por codigo (sem imagens externas): formas basicas
 * num atlas unico, fontes geradas com FreeType e sons sintetizados (assets/sfx).
 */
public class Assets implements Disposable {
    public Texture atlas;
    public TextureRegion white, circle, soft, half, ring, tri, rrect, star, arrow;
    public Texture uiTex;
    public TextureRegion uiPanel, uiButton, uiButtonDown, uiField, uiKnob;

    public BitmapFont small, normal, big, huge;
    public float fontScale = 1f;

    private final ObjectMap<String, Sound> sounds = new ObjectMap<>();
    public Music music;

    public void load() {
        buildAtlas();
        buildUi();
        loadFonts();
        loadSounds();
    }

    // ------------------------------------------------------------------ texturas

    private static int rgba(float r, float g, float b, float a) {
        return Color.rgba8888(r, g, b, MathUtils.clamp(a, 0, 1));
    }

    private void buildAtlas() {
        int size = 512;
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        // branco
        pm.setColor(Color.WHITE);
        pm.fillRectangle(0, 0, 8, 8);
        // circulo com borda suave
        disc(pm, 8, 0, 128, false);
        // gradiente radial (fumaca, brilho)
        for (int y = 0; y < 128; y++)
            for (int x = 0; x < 128; x++) {
                float dx = (x + 0.5f - 64) / 64f, dy = (y + 0.5f - 64) / 64f;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = MathUtils.clamp(1 - d, 0, 1);
                a = a * a * (3 - 2 * a);
                pm.drawPixel(136 + x, y, rgba(1, 1, 1, a));
            }
        // meio circulo (de cima)
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 128; x++) {
                float dx = (x + 0.5f - 64) / 64f, dy = (64 - (y + 0.5f)) / 64f;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = MathUtils.clamp((1 - d) * 64, 0, 1);
                pm.drawPixel(264 + x, y, rgba(1, 1, 1, a));
            }
        // anel
        for (int y = 0; y < 120; y++)
            for (int x = 0; x < 120; x++) {
                float dx = (x + 0.5f - 60) / 60f, dy = (y + 0.5f - 60) / 60f;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = MathUtils.clamp((1 - d) * 60, 0, 1) * MathUtils.clamp((d - 0.8f) * 60, 0, 1);
                pm.drawPixel(392 + x, y, rgba(1, 1, 1, a));
            }
        // triangulo (ponta para cima)
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float yy = 63 - y; // de baixo para cima
                float half = (64 - yy) / 2f;
                float dx = Math.abs(x + 0.5f - 32);
                float a = MathUtils.clamp(half - dx, 0, 1);
                pm.drawPixel(x, 136 + y, rgba(1, 1, 1, a));
            }
        // estrela
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float dx = x + 0.5f - 32, dy = y + 0.5f - 32;
                float ang = MathUtils.atan2(dy, dx);
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                float lim = 18 + 12 * MathUtils.cos(ang * 5 + MathUtils.PI / 2);
                pm.drawPixel(64 + x, 136 + y, rgba(1, 1, 1, MathUtils.clamp(lim - r, 0, 1)));
            }
        // seta (para a direita)
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float dy = Math.abs(y + 0.5f - 32);
                float a;
                if (x < 32) a = MathUtils.clamp(10 - dy, 0, 1);
                else a = MathUtils.clamp((64 - x) * 0.9f - dy, 0, 1);
                pm.drawPixel(128 + x, 136 + y, rgba(1, 1, 1, a));
            }
        // retangulo arredondado
        roundRect(pm, 200, 136, 64, 64, 18, 1, 1, 1, 1);
        atlas = new Texture(pm, true);
        atlas.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        pm.dispose();
        white = new TextureRegion(atlas, 2, 2, 4, 4);
        circle = new TextureRegion(atlas, 8, 0, 128, 128);
        soft = new TextureRegion(atlas, 136, 0, 128, 128);
        half = new TextureRegion(atlas, 264, 0, 128, 64);
        ring = new TextureRegion(atlas, 392, 0, 120, 120);
        tri = new TextureRegion(atlas, 0, 136, 64, 64);
        star = new TextureRegion(atlas, 64, 136, 64, 64);
        arrow = new TextureRegion(atlas, 128, 136, 64, 64);
        rrect = new TextureRegion(atlas, 200, 136, 64, 64);
    }

    private static void disc(Pixmap pm, int ox, int oy, int s, boolean unused) {
        float r = s / 2f;
        for (int y = 0; y < s; y++)
            for (int x = 0; x < s; x++) {
                float dx = x + 0.5f - r, dy = y + 0.5f - r;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = MathUtils.clamp(r - 1 - d + 1, 0, 1);
                pm.drawPixel(ox + x, oy + y, rgba(1, 1, 1, a));
            }
    }

    private static void roundRect(Pixmap pm, int ox, int oy, int w, int h, float rad, float r, float g, float b, float alpha) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                float cx = MathUtils.clamp(x + 0.5f, rad, w - rad), cy = MathUtils.clamp(y + 0.5f, rad, h - rad);
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = MathUtils.clamp(rad - d, 0, 1) * alpha;
                pm.drawPixel(ox + x, oy + y, rgba(r, g, b, a));
            }
    }

    /** Texturas da interface (9-patch com borda escura estilo cartoon). */
    private void buildUi() {
        Pixmap pm = new Pixmap(256, 64, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        // painel: fundo escuro translucido
        roundRect(pm, 0, 0, 64, 64, 14, 0.08f, 0.09f, 0.12f, 1f);
        roundRect(pm, 3, 3, 58, 58, 11, 0.16f, 0.18f, 0.24f, 0.92f);
        // botao: contorno + corpo branco (tingido depois) + sombra inferior
        roundRect(pm, 64, 0, 64, 64, 14, 0.05f, 0.05f, 0.07f, 1f);
        roundRect(pm, 67, 3, 58, 54, 11, 1f, 1f, 1f, 1f);
        roundRect(pm, 67, 3, 58, 30, 11, 1f, 1f, 1f, 1f);
        for (int y = 40; y < 57; y++)
            for (int x = 67; x < 125; x++) {
                int c = pm.getPixel(x, y);
                if ((c & 0xff) > 0) pm.drawPixel(x, y, Color.rgba8888(0.82f, 0.82f, 0.82f, (c & 0xff) / 255f));
            }
        // botao pressionado (sem sombra, deslocado)
        roundRect(pm, 128, 0, 64, 64, 14, 0.05f, 0.05f, 0.07f, 1f);
        roundRect(pm, 131, 7, 58, 54, 11, 0.85f, 0.85f, 0.85f, 1f);
        // campo de texto
        roundRect(pm, 192, 0, 64, 64, 10, 0.05f, 0.05f, 0.07f, 1f);
        roundRect(pm, 195, 3, 58, 58, 8, 0.95f, 0.95f, 0.92f, 1f);
        uiTex = new Texture(pm);
        uiTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        uiPanel = new TextureRegion(uiTex, 0, 0, 64, 64);
        uiButton = new TextureRegion(uiTex, 64, 0, 64, 64);
        uiButtonDown = new TextureRegion(uiTex, 128, 0, 64, 64);
        uiField = new TextureRegion(uiTex, 192, 0, 64, 64);
        uiKnob = circle;
    }

    // ------------------------------------------------------------------ fontes

    private void loadFonts() {
        fontScale = MathUtils.clamp(Gdx.graphics.getHeight() / 720f, 0.5f, 3f);
        FileHandle fh = Gdx.files.internal("fonts/game.ttf");
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(fh);
        small = font(gen, 18, 1.5f);
        normal = font(gen, 25, 2f);
        big = font(gen, 38, 2.5f);
        huge = font(gen, 70, 4f);
        gen.dispose();
    }

    private BitmapFont font(FreeTypeFontGenerator gen, int size, float border) {
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = Math.max(8, Math.round(size * fontScale));
        p.borderWidth = border * fontScale;
        p.borderColor = new Color(0.05f, 0.05f, 0.08f, 1f);
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        p.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "•→←↑↓★☠⇄∞»✝✚♥";
        p.incremental = false;
        BitmapFont f = gen.generateFont(p);
        f.getData().setScale(1f / fontScale);
        f.setUseIntegerPositions(false);
        f.getData().markupEnabled = true;
        return f;
    }

    // ------------------------------------------------------------------ sons

    private static final String[] SOUND_NAMES = {
            "pistol", "magnum", "smg", "rifle", "shotgun", "sniper", "lmg", "rocket", "explosion", "explosion_small",
            "bounce", "laser", "flame", "disc", "reload", "empty", "pickup", "health", "powerup", "jetpack", "hit",
            "hitmarker", "death", "melee", "ui_click", "beep", "planted", "defused", "flag", "win", "lose", "wave",
            "throw", "arm", "streak", "spawn", "step"};

    private void loadSounds() {
        for (String n : SOUND_NAMES) {
            try {
                sounds.put(n, Gdx.audio.newSound(Gdx.files.internal("sfx/" + n + ".wav")));
            } catch (Exception e) {
                Gdx.app.error("Assets", "som nao carregou: " + n, e);
            }
        }
        try {
            music = Gdx.audio.newMusic(Gdx.files.internal("sfx/music.wav"));
            music.setLooping(true);
        } catch (Exception e) {
            Gdx.app.error("Assets", "musica nao carregou", e);
        }
    }

    public Sound sound(String name) {
        return sounds.get(name);
    }

    @Override
    public void dispose() {
        atlas.dispose();
        uiTex.dispose();
        small.dispose();
        normal.dispose();
        big.dispose();
        huge.dispose();
        for (Sound s : sounds.values()) s.dispose();
        if (music != null) music.dispose();
    }
}
