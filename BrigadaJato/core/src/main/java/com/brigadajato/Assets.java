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

    /** Texturas da interface: placas militares com rebites, botoes com relevo, faixa de alerta. */
    public TextureRegion uiStripes;

    private void buildUi() {
        Pixmap pm = new Pixmap(512, 64, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        java.util.Random r = new java.util.Random(7);
        // painel: placa de metal escura com rebites nos cantos
        roundRect(pm, 0, 0, 64, 64, 10, 0.06f, 0.07f, 0.09f, 1f);
        roundRect(pm, 3, 3, 58, 58, 8, 0.17f, 0.21f, 0.23f, 0.95f);
        for (int y = 6; y < 58; y++)
            for (int x = 6; x < 58; x++) {
                int c = pm.getPixel(x, y);
                float a = (c & 0xff) / 255f;
                float n = r.nextFloat() * 0.03f;
                float top = y < 9 ? 0.06f : 0;
                pm.drawPixel(x, y, rgba(0.17f + n + top, 0.21f + n + top, 0.23f + n + top, a));
            }
        int[][] rivets = {{9, 9}, {54, 9}, {9, 54}, {54, 54}};
        for (int[] rv : rivets) {
            for (int y = -3; y <= 3; y++)
                for (int x = -3; x <= 3; x++) {
                    float dd = (float) Math.sqrt(x * x + y * y);
                    if (dd > 3.2f) continue;
                    float light = y < 0 ? 0.62f : 0.42f;
                    pm.drawPixel(rv[0] + x, rv[1] + y, rgba(light, light, light + 0.03f, 1));
                }
        }
        // botao: contorno grosso + corpo claro com brilho em cima e sombra embaixo
        roundRect(pm, 64, 0, 64, 64, 12, 0.06f, 0.06f, 0.08f, 1f);
        roundRect(pm, 68, 4, 56, 52, 9, 1f, 1f, 1f, 1f);
        for (int y = 4; y < 56; y++)
            for (int x = 68; x < 124; x++) {
                int c = pm.getPixel(x, y);
                float a = (c & 0xff) / 255f;
                if (a <= 0) continue;
                float k;
                if (y < 10) k = 1f;            // brilho
                else if (y < 30) k = 0.93f;
                else if (y < 44) k = 0.86f;
                else k = 0.66f;                // sombra de baixo
                pm.drawPixel(x, y, rgba(k, k, k, a));
            }
        // botao pressionado
        roundRect(pm, 128, 0, 64, 64, 12, 0.06f, 0.06f, 0.08f, 1f);
        roundRect(pm, 132, 8, 56, 52, 9, 0.8f, 0.8f, 0.8f, 1f);
        // campo de texto
        roundRect(pm, 192, 0, 64, 64, 10, 0.06f, 0.06f, 0.08f, 1f);
        roundRect(pm, 195, 3, 58, 58, 8, 0.95f, 0.95f, 0.9f, 1f);
        // faixa de alerta (amarelo/preto diagonal)
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 64; x++) {
                boolean yellow = ((x + y) / 8) % 2 == 0;
                pm.drawPixel(256 + x, y, yellow ? rgba(1f, 0.76f, 0.03f, 1) : rgba(0.08f, 0.08f, 0.1f, 1));
            }
        // aba / chip arredondado escuro (para HUD e listas)
        roundRect(pm, 320, 0, 64, 64, 14, 0.04f, 0.05f, 0.07f, 0.78f);
        uiTex = new Texture(pm);
        uiTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        uiTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge);
        pm.dispose();
        uiPanel = new TextureRegion(uiTex, 0, 0, 64, 64);
        uiButton = new TextureRegion(uiTex, 64, 0, 64, 64);
        uiButtonDown = new TextureRegion(uiTex, 128, 0, 64, 64);
        uiField = new TextureRegion(uiTex, 192, 0, 64, 64);
        uiStripes = new TextureRegion(uiTex, 256, 0, 64, 32);
        uiChip = new TextureRegion(uiTex, 320, 0, 64, 64);
        uiKnob = circle;
    }

    public TextureRegion uiChip;

    // ------------------------------------------------------------------ fontes

    private void loadFonts() {
        fontScale = MathUtils.clamp(Gdx.graphics.getHeight() / 720f, 0.5f, 3f);
        FreeTypeFontGenerator ui = new FreeTypeFontGenerator(Gdx.files.internal("fonts/ui.ttf"));
        FreeTypeFontGenerator title = new FreeTypeFontGenerator(Gdx.files.internal("fonts/title.ttf"));
        small = font(ui, 20, 1.8f, 1.5f);
        normal = font(ui, 27, 2.2f, 2f);
        big = font(title, 40, 2.8f, 3f);
        huge = font(title, 78, 4.5f, 5f);
        ui.dispose();
        title.dispose();
    }

    private BitmapFont font(FreeTypeFontGenerator gen, int size, float border, float shadow) {
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = Math.max(8, Math.round(size * fontScale));
        p.borderWidth = border * fontScale;
        p.borderColor = new Color(0.06f, 0.06f, 0.09f, 1f);
        p.shadowOffsetX = 0;
        p.shadowOffsetY = Math.round(shadow * fontScale);
        p.shadowColor = new Color(0, 0, 0, 0.45f);
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        p.characters = FreeTypeFontGenerator.DEFAULT_CHARS;
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
            "throw", "arm", "streak", "spawn", "step", "kill1", "kill2", "kill3", "kill4", "kill5", "headshot",
            "bow", "boomerang", "catch", "frost", "freeze", "tesla", "gravity", "harpoon", "harpoon_pull", "hook", "hook_hit",
            "block", "crate_break", "lever", "door", "boss", "boss_down", "charge", "stomp", "levelup", "infect", "emote",
            "dance", "round", "roll", "smoke", "stick", "flashbang"};

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
