package com.brigadajato.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.brigadajato.Assets;
import com.brigadajato.Audio;
import com.brigadajato.Settings;
import com.brigadajato.world.C;
import com.brigadajato.world.GameMap;
import com.brigadajato.world.Pickup;
import com.brigadajato.world.Player;
import com.brigadajato.world.Projectile;
import com.brigadajato.world.Theme;
import com.brigadajato.world.WeaponType;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;
import com.brigadajato.world.modes.BombMode;
import com.brigadajato.world.modes.CtfMode;

import java.util.Random;

/** Desenha o mundo: cenario, blocos, soldados, projeteis, itens e efeitos. */
public class WorldRenderer implements Disposable {
    private final Assets a;
    private final Audio audio;
    private final Settings settings;
    public final Particles fx = new Particles(2600);
    private final SoldierPainter painter = new SoldierPainter();
    private final SoldierPainter.Pose pose = new SoldierPainter.Pose();
    private final GlyphLayout layout = new GlyphLayout();

    private World world;
    private Texture tileTex, skyTex;
    private TextureRegion[] solid = new TextureRegion[4];
    private TextureRegion topRegion, platRegion, spikeRegion;
    private final Array<float[]> bgFar = new Array<>(), bgMid = new Array<>(), bgNear = new Array<>(), clouds = new Array<>();

    public float shake;
    public float time;

    private static class Beam {
        float x1, y1, x2, y2, life;
    }

    private final Array<Beam> beams = new Array<>();

    private static class FloatText {
        float x, y, life;
        String text;
        float r, g, b;
        float scale;
    }

    private final Array<FloatText> texts = new Array<>();

    public WorldRenderer(Assets a, Audio audio, Settings settings) {
        this.a = a;
        this.audio = audio;
        this.settings = settings;
    }

    // ------------------------------------------------------------------ preparacao

    public void setWorld(World w) {
        this.world = w;
        fx.clear();
        beams.clear();
        texts.clear();
        fx.quality = settings.highQuality ? 1f : 0.45f;
        if (tileTex != null) tileTex.dispose();
        if (skyTex != null) skyTex.dispose();
        Theme t = w.map.theme;
        buildTiles(t);
        buildSky(t);
        buildBackground(t, w.map);
    }

    private static int rgba(int rgb, float alpha) {
        return Color.rgba8888(((rgb >> 16) & 0xff) / 255f, ((rgb >> 8) & 0xff) / 255f, (rgb & 0xff) / 255f, alpha);
    }

    private static int mix(int c1, int c2, float t) {
        t = MathUtils.clamp(t, 0, 1);
        int r = (int) (((c1 >> 16) & 0xff) * (1 - t) + ((c2 >> 16) & 0xff) * t);
        int g = (int) (((c1 >> 8) & 0xff) * (1 - t) + ((c2 >> 8) & 0xff) * t);
        int b = (int) ((c1 & 0xff) * (1 - t) + (c2 & 0xff) * t);
        return (r << 16) | (g << 8) | b;
    }

    private void buildSky(Theme t) {
        Pixmap pm = new Pixmap(1, 128, Pixmap.Format.RGBA8888);
        for (int y = 0; y < 128; y++) pm.drawPixel(0, y, rgba(mix(t.skyTop, t.skyBottom, y / 127f), 1));
        skyTex = new Texture(pm);
        skyTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
    }

    private void buildTiles(Theme t) {
        Pixmap pm = new Pixmap(256, 128, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        Random r = new Random(t.ordinal() * 31L + 7);
        for (int v = 0; v < 4; v++) {
            int ox = v * 64;
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    float shade = 1f - y / 64f * 0.25f;
                    int base = t.tile;
                    boolean line = false;
                    switch (t) {
                        case DESERT: { // tijolos de arenito
                            int row = y / 16;
                            int bx = (x + (row % 2) * 16 + v * 8) % 32;
                            line = y % 16 == 0 || bx == 0;
                            break;
                        }
                        case FACTORY: { // placas de metal com rebites
                            line = x == 0 || y == 0 || x == 63 || y == 63;
                            int dx = x % 32 - 6, dy = y % 32 - 6;
                            if (dx * dx + dy * dy < 5) base = mix(base, 0xffffff, 0.35f);
                            if ((x + y + v * 5) % 23 == 0) base = mix(base, 0x000000, 0.15f);
                            break;
                        }
                        case ICE: { // blocos de gelo com brilho
                            line = (x + y) % 61 == 0;
                            if ((x - y + 64) % 37 < 3) base = mix(base, 0xffffff, 0.35f);
                            break;
                        }
                        case TRAINING: {
                            line = x % 32 == 0 || y % 32 == 0;
                            break;
                        }
                        default: { // terra com pedrinhas
                            if (r.nextFloat() < 0.012f) base = mix(base, 0x5d4037, 0.6f);
                            break;
                        }
                    }
                    float noise = 0.92f + r.nextFloat() * 0.12f;
                    int c = mix(0x000000, base, shade * noise);
                    if (line) c = mix(c, t.tileDark, 0.85f);
                    pm.drawPixel(ox + x, y, rgba(c, 1));
                }
            }
            // pedras grandes
            if (t == Theme.SKY || t == Theme.DESERT) {
                for (int k = 0; k < 3; k++) {
                    int cx = ox + 8 + r.nextInt(48), cy = 10 + r.nextInt(44), rr = 3 + r.nextInt(4);
                    pm.setColor(new Color(rgba(mix(t.tileDark, t.tile, 0.4f), 1)));
                    pm.fillCircle(cx, cy, rr);
                }
            }
        }
        // camada de cima (grama / areia / neve)
        for (int y = 0; y < 22; y++)
            for (int x = 0; x < 64; x++) {
                float edge = 15 + 4 * MathUtils.sin(x * 0.45f) + 2 * MathUtils.sin(x * 1.3f + 1);
                if (y > edge) continue;
                int c = mix(t.tileTop, 0xffffff, y < 4 ? 0.25f : 0);
                c = mix(c, 0x000000, y / 40f + (r.nextFloat() * 0.08f));
                pm.drawPixel(x, 64 + y, rgba(c, 1));
            }
        // plataforma
        for (int y = 0; y < 22; y++)
            for (int x = 0; x < 64; x++) {
                int c = t.platform;
                if (y < 3 || y > 18) c = mix(c, 0x000000, 0.45f);
                else if (x % 32 == 0) c = mix(c, 0x000000, 0.35f);
                else if (y % 7 == 0) c = mix(c, 0x000000, 0.12f);
                c = mix(c, 0xffffff, y >= 3 && y < 6 ? 0.2f : 0);
                pm.drawPixel(64 + x, 64 + y, rgba(c, 1));
            }
        // espinhos
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                int sx = x % 16;
                float hgt = 40 - Math.abs(sx - 7.5f) * 5;
                int yy = 63 - y;
                if (yy < hgt) {
                    int c = sx < 8 ? 0xb0bec5 : 0x78909c;
                    if (yy < 10) c = 0x546e7a;
                    pm.drawPixel(128 + x, 64 + y - 0, rgba(c, 1));
                } else if (yy < 6) pm.drawPixel(128 + x, 64 + y, rgba(0x37474f, 1));
            }
        tileTex = new Texture(pm, true);
        tileTex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        pm.dispose();
        for (int v = 0; v < 4; v++) solid[v] = new TextureRegion(tileTex, v * 64, 0, 64, 64);
        topRegion = new TextureRegion(tileTex, 0, 64, 64, 22);
        platRegion = new TextureRegion(tileTex, 64, 64, 64, 22);
        spikeRegion = new TextureRegion(tileTex, 128, 64, 64, 64);
    }

    private void buildBackground(Theme t, GameMap map) {
        bgFar.clear();
        bgMid.clear();
        bgNear.clear();
        clouds.clear();
        Random r = new Random(map.id.hashCode());
        float period = 2400;
        for (int i = 0; i < 6; i++) bgFar.add(new float[]{i * period / 6 + r.nextFloat() * 150, 0, 520 + r.nextFloat() * 380, 230 + r.nextFloat() * 220});
        for (int i = 0; i < 8; i++) bgMid.add(new float[]{i * period / 8 + r.nextFloat() * 120, 0, 300 + r.nextFloat() * 220, 90 + r.nextFloat() * 140});
        for (int i = 0; i < 12; i++) bgNear.add(new float[]{i * period / 12 + r.nextFloat() * 80, 0, 40 + r.nextFloat() * 50, 70 + r.nextFloat() * 110});
        for (int i = 0; i < 8; i++) clouds.add(new float[]{r.nextFloat() * period, 120 + r.nextFloat() * 260, 34 + r.nextFloat() * 30, 8 + r.nextFloat() * 14});
    }

    // ------------------------------------------------------------------ eventos -> efeitos

    public void handle(WorldEvent e, Player local) {
        World w = world;
        int localId = local != null ? local.id : -999;
        switch (e.type) {
            case SHOT: {
                WeaponType wt = WeaponType.of(e.b);
                if (wt == null) break;
                Player p = w.player(e.a);
                float ang = e.angles != null && e.angles.length > 0 ? e.angles[0] : 0;
                float len = wt.gunLen + 8;
                float mx = e.x + MathUtils.cos(ang) * len, my = e.y + MathUtils.sin(ang) * len;
                if (wt.kind != Projectile.Kind.FLAME) {
                    Particles.P f = fx.spawn(Particles.SOFT, mx, my, 0, 0, 0.07f, wt.gunH * 1.6f, wt.gunH * 0.8f, 0xffe082, 0.95f);
                    f.additive = true;
                    if (wt.kind == Projectile.Kind.BULLET && !fx.skip()) {
                        Particles.P c = fx.spawn(Particles.RECT, e.x, e.y, -MathUtils.cos(ang) * 60 + MathUtils.random(-40, 40), 140 + MathUtils.random(80), 0.8f, 4, 4, 0xd4ac0d, 1);
                        c.grav = 900;
                        c.vrot = 720;
                        c.a2 = 1;
                    }
                    for (int i = 0; i < 2 && !fx.skip(); i++) {
                        Particles.P s = fx.spawn(Particles.SOFT, mx, my, MathUtils.cos(ang) * 40 + MathUtils.random(-15, 15), 20 + MathUtils.random(20), 0.5f, 4, 12, 0xcfcfcf, 0.35f);
                        s.drag = 2;
                    }
                }
                if (wt.kind == Projectile.Kind.ROCKET || wt.kind == Projectile.Kind.SHELL) {
                    for (int i = 0; i < 6; i++) {
                        Particles.P s = fx.spawn(Particles.SOFT, e.x, e.y, -MathUtils.cos(ang) * 120 + MathUtils.random(-50, 50), MathUtils.random(-40, 60), 0.6f, 6, 18, 0xbdbdbd, 0.5f);
                        s.drag = 3;
                    }
                }
                if (!w.authority) w.spawnVisualShot(e);
                float vol = e.a == localId ? 0.75f : 1f;
                audio.play(wt.sound, e.x, e.y, vol, 1f);
                if (e.a == localId) {
                    shake = Math.max(shake, wt.recoil > 60 ? 6 : (wt.kind == Projectile.Kind.FLAME ? 0.5f : 1.8f));
                }
                if (p != null && p.netClient >= -1) p.lastShotTime = w.time;
                break;
            }
            case LASER: {
                Beam b = new Beam();
                b.x1 = e.x;
                b.y1 = e.y;
                b.x2 = e.x2;
                b.y2 = e.y2;
                b.life = 0.28f;
                beams.add(b);
                for (int i = 0; i < 10; i++) {
                    Particles.P s = fx.spawn(Particles.LINE, e.x2, e.y2, MathUtils.random(-250, 250), MathUtils.random(-250, 250), 0.3f, 4, 1, 0x7fffd4, 1);
                    s.additive = true;
                    s.drag = 4;
                }
                audio.at("laser", e.x, e.y);
                if (e.a == localId) shake = Math.max(shake, 4);
                break;
            }
            case EXPLOSION:
                explosion(e.x, e.y, e.f, local);
                break;
            case HIT: {
                int n = settings.highQuality ? 6 : 3;
                for (int i = 0; i < n; i++) {
                    Particles.P b = fx.spawn(Particles.CIRCLE, e.x, e.y, MathUtils.random(-160, 160), MathUtils.random(-30, 220), 0.6f, MathUtils.random(1.8f, 3.6f), 0.8f, 0xb71c1c, 1);
                    b.grav = 900;
                    b.a2 = 0.8f;
                }
                if (e.b == localId) {
                    audio.ui("hitmarker");
                    if (settings.damageNumbers) floatText("" + Math.max(1, Math.round(e.f)), e.x + MathUtils.random(-10, 10), e.y + 26,
                            e.c == 1 ? 1f : 1f, e.c == 1 ? 0.3f : 0.92f, e.c == 1 ? 0.2f : 0.3f, e.c == 1 ? 1.2f : 0.85f);
                }
                Player v = w.player(e.a);
                if (v != null) v.hurtFlash = 0.12f;
                if (e.a == localId) {
                    audio.ui("hit");
                    audio.vibrate(35);
                    shake = Math.max(shake, 3);
                } else audio.play("hit", e.x, e.y, 0.5f, 1f);
                break;
            }
            case KILL: {
                Player v = w.player(e.b);
                int col = v != null ? (v.team == C.TEAM_RED ? SoldierPainter.RED : (v.team == C.TEAM_BLUE ? SoldierPainter.BLUE : v.look.body)) : 0x888888;
                for (int i = 0; i < 9; i++) {
                    Particles.P g = fx.spawn(Particles.RECT, e.x, e.y, e.x2 * 0.3f + MathUtils.random(-220, 220), e.y2 * 0.3f + MathUtils.random(80, 380), 1.4f, MathUtils.random(6, 11), 5, i % 3 == 0 ? 0x2b2b2b : col, 1);
                    g.grav = 1100;
                    g.vrot = MathUtils.random(-800, 800);
                    g.a2 = 0.9f;
                }
                if (v != null) {
                    Particles.P h = fx.spawn(Particles.CIRCLE, e.x, e.y + 14, MathUtils.random(-140, 140), 420, 1.4f, 8, 8, v.look.skin, 1);
                    h.grav = 1100;
                }
                for (int i = 0; i < 12; i++) {
                    Particles.P b = fx.spawn(Particles.CIRCLE, e.x, e.y, MathUtils.random(-260, 260), MathUtils.random(-50, 320), 0.8f, MathUtils.random(2, 4.5f), 1, 0xb71c1c, 1);
                    b.grav = 900;
                }
                audio.at("death", e.x, e.y);
                if (e.a == localId && e.b != localId) audio.vibrate(60);
                break;
            }
            case SPARK: {
                for (int i = 0; i < 4 && !fx.skip(); i++) {
                    float ang = e.f + MathUtils.PI + MathUtils.random(-1.1f, 1.1f);
                    Particles.P s = fx.spawn(Particles.LINE, e.x, e.y, MathUtils.cos(ang) * 260, MathUtils.sin(ang) * 260, 0.18f, 2.5f, 0.5f, 0xffd54f, 1);
                    s.additive = true;
                    s.grav = 600;
                }
                Particles.P dust = fx.spawn(Particles.SOFT, e.x, e.y, 0, 15, 0.45f, 4, 13, world.map.theme.tile, 0.6f);
                dust.drag = 1;
                break;
            }
            case SOUND:
                if (e.x < 0) audio.ui(e.text);
                else audio.at(e.text, e.x, e.y);
                break;
            case MELEE: {
                Player p = w.player(e.a);
                if (p != null) p.meleeAnim = 0.25f;
                audio.at("melee", e.x, e.y);
                break;
            }
            case PICKUP: {
                String snd = "pickup";
                if (e.b == Pickup.Type.HEALTH.ordinal()) snd = "health";
                else if (e.b >= Pickup.Type.ARMOR.ordinal()) snd = "powerup";
                audio.at(snd, e.x, e.y);
                for (int i = 0; i < 8; i++) {
                    Particles.P s = fx.spawn(Particles.SOFT, e.x, e.y + 12, MathUtils.random(-80, 80), MathUtils.random(40, 160), 0.5f, 5, 1, 0xfff59d, 0.9f);
                    s.additive = true;
                }
                break;
            }
            case RESPAWN: {
                for (int i = 0; i < 14; i++) {
                    float ang = i / 14f * MathUtils.PI2;
                    Particles.P s = fx.spawn(Particles.SOFT, e.x + MathUtils.cos(ang) * 20, e.y + 22 + MathUtils.sin(ang) * 20, MathUtils.cos(ang) * 60, MathUtils.sin(ang) * 60 + 40, 0.6f, 6, 1, 0x80d8ff, 0.8f);
                    s.additive = true;
                }
                if (e.a == localId) audio.ui("spawn");
                break;
            }
            case THROW:
                audio.at("throw", e.x, e.y);
                break;
            case RELOAD:
                if (e.a == localId) audio.ui("reload");
                else audio.play("reload", e.x, e.y, 0.5f, 1f);
                break;
            case BOUNCE:
                audio.play(e.a == 1 ? "disc" : "bounce", e.x, e.y, e.a == 1 ? 0.4f : 0.7f, e.a == 1 ? 1.6f : 1f);
                if (e.a == 1) for (int i = 0; i < 4; i++) {
                    Particles.P s = fx.spawn(Particles.LINE, e.x, e.y, MathUtils.random(-200, 200), MathUtils.random(-200, 200), 0.2f, 2.5f, 0.5f, 0xffffff, 1);
                    s.additive = true;
                }
                break;
            case EMPTY:
                if (e.a == localId) audio.ui("empty");
                break;
            case FLAG:
                audio.ui("flag");
                break;
            case BOMB:
                if (e.b == BombMode.EV_BEEP) audio.at("beep", e.x, e.y);
                else if (e.b == BombMode.EV_PLANTED) audio.ui("planted");
                else if (e.b == BombMode.EV_DEFUSED) audio.ui("defused");
                break;
            case WAVE:
                audio.ui("wave");
                break;
            case STREAK:
                if (e.a == localId) audio.ui("streak");
                break;
            case ROUND:
                if (e.a > 0 && local != null) audio.ui(local.team == e.a ? "win" : "lose");
                break;
            default:
                break;
        }
    }

    private void explosion(float x, float y, float radius, Player local) {
        float k = radius / 110f;
        Particles.P flash = fx.spawn(Particles.SOFT, x, y, 0, 0, 0.18f, radius * 1.3f, radius * 0.6f, 0xfff8e1, 1);
        flash.additive = true;
        Particles.P ring = fx.spawn(Particles.RING, x, y, 0, 0, 0.35f, radius * 0.25f, radius * 1.05f, 0xffffff, 0.8f);
        ring.a2 = 0;
        int fire = (int) (14 * k * (settings.highQuality ? 1 : 0.5f));
        for (int i = 0; i < fire; i++) {
            float ang = MathUtils.random(MathUtils.PI2), sp = MathUtils.random(40, 260) * k;
            Particles.P f = fx.spawn(Particles.SOFT, x, y, MathUtils.cos(ang) * sp, MathUtils.sin(ang) * sp + 60, MathUtils.random(0.35f, 0.7f), MathUtils.random(18, 34) * k, 6, i % 2 == 0 ? 0xffb300 : 0xff6d00, 1);
            f.additive = true;
            f.drag = 3;
        }
        for (int i = 0; i < fire; i++) {
            float ang = MathUtils.random(MathUtils.PI2), sp = MathUtils.random(30, 160) * k;
            Particles.P s = fx.spawn(Particles.SOFT, x, y, MathUtils.cos(ang) * sp, MathUtils.sin(ang) * sp + 70, MathUtils.random(0.9f, 1.8f), 14 * k, 42 * k, 0x424242, 0.65f);
            s.drag = 2;
            s.grav = -40;
        }
        for (int i = 0; i < 10; i++) {
            Particles.P d = fx.spawn(Particles.RECT, x, y, MathUtils.random(-380, 380), MathUtils.random(80, 520), 1.2f, MathUtils.random(4, 9), 4, world.map.theme.tileDark, 1);
            d.grav = 1200;
            d.vrot = MathUtils.random(-900, 900);
            d.a2 = 1;
        }
        for (int i = 0; i < 12; i++) {
            float ang = MathUtils.random(MathUtils.PI2);
            Particles.P s = fx.spawn(Particles.LINE, x, y, MathUtils.cos(ang) * 600, MathUtils.sin(ang) * 600, 0.3f, 4, 1, 0xffe082, 1);
            s.additive = true;
            s.drag = 3;
        }
        audio.at(radius > 200 ? "explosion" : (radius > 100 ? "explosion" : "explosion_small"), x, y);
        if (local != null) {
            float dx = local.x - x, dy = local.centerY() - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float s = MathUtils.clamp(1 - d / 900f, 0, 1) * 16 * k;
            shake = Math.max(shake, s);
            if (d < 300) audio.vibrate(90);
        }
    }

    public void floatText(String text, float x, float y, float r, float g, float b, float scale) {
        FloatText ft = new FloatText();
        ft.text = text;
        ft.x = x;
        ft.y = y;
        ft.life = 0.9f;
        ft.r = r;
        ft.g = g;
        ft.b = b;
        ft.scale = scale;
        texts.add(ft);
    }

    // ------------------------------------------------------------------ desenho

    public void update(float dt) {
        time += dt;
        fx.update(dt);
        for (int i = beams.size - 1; i >= 0; i--) {
            beams.get(i).life -= dt;
            if (beams.get(i).life <= 0) beams.removeIndex(i);
        }
        for (int i = texts.size - 1; i >= 0; i--) {
            FloatText t = texts.get(i);
            t.life -= dt;
            t.y += 40 * dt;
            if (t.life <= 0) texts.removeIndex(i);
        }
        shake = Math.max(0, shake - dt * 30);
        // efeitos continuos (jetpack, foguetes, chamas)
        if (world == null) return;
        for (Player p : world.players) {
            if (!p.alive || !p.jetting) continue;
            if (fx.skip()) continue;
            float nx = p.x - p.facing * 10, ny = p.y + (p.crouching ? 4 : 9);
            boolean boost = p.jetBoost > 0;
            Particles.P f = fx.spawn(Particles.SOFT, nx + MathUtils.random(-2, 2), ny, MathUtils.random(-20, 20) + p.vx * 0.2f, -260 + p.vy * 0.2f, 0.16f, 7, 2, boost ? 0x40c4ff : 0xffab00, 1);
            f.additive = true;
            if (MathUtils.randomBoolean(0.5f)) {
                Particles.P s = fx.spawn(Particles.SOFT, nx, ny - 8, MathUtils.random(-30, 30), -80, 0.6f, 5, 16, 0xbdbdbd, 0.35f);
                s.drag = 2;
            }
        }
        for (Projectile pr : world.projectiles) {
            if (pr.kind == Projectile.Kind.ROCKET && !fx.skip()) {
                Particles.P s = fx.spawn(Particles.SOFT, pr.x, pr.y, MathUtils.random(-20, 20), MathUtils.random(-20, 20), 0.7f, 5, 16, 0xcfcfcf, 0.5f);
                s.drag = 2;
                Particles.P f = fx.spawn(Particles.SOFT, pr.x, pr.y, 0, 0, 0.08f, 9, 3, 0xffab00, 1);
                f.additive = true;
            } else if (pr.kind == Projectile.Kind.FRAG && settings.highQuality && MathUtils.randomBoolean(0.3f)) {
                fx.spawn(Particles.SOFT, pr.x, pr.y + 4, 0, 30, 0.3f, 2, 5, 0x9e9e9e, 0.4f);
            }
        }
    }

    public void render(Draw d, OrthographicCamera cam, World w, Player local, boolean showCrosshair, float crossX, float crossY) {
        float vw = cam.viewportWidth * cam.zoom, vh = cam.viewportHeight * cam.zoom;
        float minX = cam.position.x - vw / 2, minY = cam.position.y - vh / 2;
        float maxX = minX + vw, maxY = minY + vh;
        drawBackground(d, cam, minX, minY, vw, vh);
        drawZones(d, w);
        drawTiles(d, w.map, minX, minY, maxX, maxY);
        drawPickups(d, w, minX, minY, maxX, maxY);
        drawModeObjects(d, w);
        drawProjectilesSolid(d, w, minX, minY, maxX, maxY);
        for (Player p : w.players) {
            if (!p.alive) continue;
            if (p.x < minX - 60 || p.x > maxX + 60 || p.y < minY - 80 || p.y > maxY + 80) continue;
            drawPlayer(d, p, w);
        }
        fx.draw(d, false, minX, minY, maxX, maxY);
        d.additive(true);
        drawProjectilesGlow(d, w, minX, minY, maxX, maxY);
        fx.draw(d, true, minX, minY, maxX, maxY);
        for (Beam b : beams) {
            float t = b.life / 0.28f;
            d.color(0.2f, 1f, 0.85f, 0.6f * t);
            d.line(b.x1, b.y1, b.x2, b.y2, 9 * t + 2);
            d.color(1, 1, 1, 0.9f * t);
            d.line(b.x1, b.y1, b.x2, b.y2, 3 * t + 1);
        }
        d.additive(false);
        drawNames(d, w, local, minX, minY, maxX, maxY);
        for (FloatText t : texts) {
            BitmapFont f = a.normal;
            f.getData().setScale(t.scale / a.fontScale * 0.9f);
            f.setColor(t.r, t.g, t.b, MathUtils.clamp(t.life * 2, 0, 1));
            f.draw(d.batch, t.text, t.x, t.y, 0, Align.center, false);
            f.getData().setScale(1f / a.fontScale);
        }
        a.normal.setColor(Color.WHITE);
        if (showCrosshair && local != null && local.alive) drawCrosshair(d, local, crossX, crossY);
    }

    private void drawBackground(Draw d, OrthographicCamera cam, float minX, float minY, float vw, float vh) {
        d.color(1, 1, 1, 1);
        d.batch.draw(skyTex, minX, minY, vw, vh);
        Theme t = world.map.theme;
        float camX = cam.position.x, camY = cam.position.y;
        float period = 2400;
        // sol / lua
        d.color(1, 1, 0.9f, t == Theme.ICE || t == Theme.FACTORY ? 0.5f : 0.85f);
        float sunX = minX + vw * 0.78f, sunY = minY + vh * 0.78f;
        d.additive(true);
        d.soft(sunX, sunY, 140);
        d.additive(false);
        d.color(1, 1, 0.92f, 1);
        d.circle(sunX, sunY, t == Theme.ICE ? 28 : 40);
        // nuvens
        if (t != Theme.FACTORY && t != Theme.ICE) {
            for (float[] c : clouds) {
                float k = 0.1f;
                float cx = wrap(c[0] + time * c[3] - camX * k, period) + camX - period / 2;
                float cy = minY + vh * 0.55f + c[1] * 0.6f;
                d.color(1, 1, 1, 0.85f);
                d.circle(cx, cy, c[2]);
                d.circle(cx + c[2] * 0.9f, cy - c[2] * 0.25f, c[2] * 0.75f);
                d.circle(cx - c[2] * 0.9f, cy - c[2] * 0.3f, c[2] * 0.65f);
            }
        }
        layer(d, bgFar, 0.18f, t.far, 0.9f, camX, camY, period, 0, t);
        layer(d, bgMid, 0.38f, t.mid, 0.95f, camX, camY, period, 1, t);
        layer(d, bgNear, 0.6f, t.near, 1f, camX, camY, period, 2, t);
    }

    private static float wrap(float v, float p) {
        v %= p;
        if (v < 0) v += p;
        return v;
    }

    private void layer(Draw d, Array<float[]> shapes, float k, int color, float alpha, float camX, float camY, float period, int depth, Theme t) {
        float baseY = (camY - world.map.worldH() * 0.5f) * (1 - k * 0.7f) + world.map.worldH() * (0.18f - depth * 0.06f);
        float half = period / 2;
        for (float[] s : shapes) {
            float sx = wrap(s[0] - camX * k + half, period) - half;
            float x = camX + sx;
            float w = s[2], h = s[3];
            d.rgb(color, alpha);
            if (depth == 0) {
                // montanhas / predios ao longe
                if (t == Theme.FACTORY) {
                    d.rect(x - w * 0.3f, baseY - 900, w * 0.45f, h + 900);
                    d.rect(x + w * 0.2f, baseY - 900, w * 0.12f, h * 1.4f + 900);
                } else {
                    d.batch.draw(a.tri, x - w / 2, baseY, w, h);
                    d.rect(x - w / 2, baseY - 900, w, 900);
                    if (t == Theme.ICE || t == Theme.TRAINING || t == Theme.SKY) {
                        d.rgb(0xffffff, 0.55f);
                        d.batch.draw(a.tri, x - w * 0.16f, baseY + h * 0.68f, w * 0.32f, h * 0.32f);
                    }
                }
            } else if (depth == 1) {
                if (t == Theme.FACTORY) {
                    d.rect(x - w * 0.25f, baseY - 900, w * 0.5f, h + 900);
                    d.rgb(0xffeb3b, 0.3f);
                    for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++)
                        d.rect(x - w * 0.2f + i * w * 0.15f, baseY + h - 30 - j * 34, 10, 14);
                } else if (t == Theme.ICE) {
                    d.batch.draw(a.tri, x - w * 0.25f, baseY, w * 0.5f, h * 1.3f);
                    d.rect(x - w * 0.25f, baseY - 900, w * 0.5f, 900);
                } else {
                    // morros / dunas arredondados
                    d.ellipse(x, baseY, w * 0.6f, h, 0);
                    d.rect(x - w * 0.6f, baseY - 900, w * 1.2f, 900);
                }
            } else {
                switch (t) {
                    case DESERT:
                        d.rect(x - 6, baseY, 12, h);
                        d.circle(x, baseY + h, 6);
                        d.rect(x - 20, baseY + h * 0.45f, 8, h * 0.3f);
                        d.rect(x + 12, baseY + h * 0.55f, 8, h * 0.28f);
                        d.rect(x - 20, baseY + h * 0.45f, 20, 7);
                        d.rect(x, baseY + h * 0.55f, 20, 7);
                        break;
                    case FACTORY:
                        d.rect(x - 5, baseY - 900, 10, h + 900);
                        d.rect(x - 14, baseY + h, 28, 8);
                        break;
                    case ICE:
                        d.batch.draw(a.tri, x - w * 0.3f, baseY, w * 0.6f, h);
                        break;
                    default:
                        d.rect(x - 4, baseY - 900, 8, h * 0.6f + 900);
                        d.circle(x, baseY + h * 0.7f, w * 0.55f);
                        d.circle(x - w * 0.3f, baseY + h * 0.5f, w * 0.4f);
                        d.circle(x + w * 0.3f, baseY + h * 0.5f, w * 0.4f);
                        break;
                }
            }
        }
    }

    private void drawTiles(Draw d, GameMap m, float minX, float minY, float maxX, float maxY) {
        int x0 = Math.max(-12, (int) Math.floor(minX / C.TILE) - 1), x1 = Math.min(m.w + 11, (int) (maxX / C.TILE) + 1);
        int y0 = Math.max(-12, (int) Math.floor(minY / C.TILE) - 1), y1 = Math.min(m.h + 11, (int) (maxY / C.TILE) + 1);
        float T = C.TILE;
        Theme th = m.theme;
        d.color(1, 1, 1, 1);
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                int t = m.get(tx, ty);
                float x = tx * T, y = ty * T;
                if (t == GameMap.SOLID) {
                    int v = (((tx + 64) * 7 + (ty + 64) * 13) & 0xff) % 4;
                    d.batch.draw(solid[v], x, y, T, T);
                } else if (t == GameMap.PLATFORM) {
                    d.batch.draw(platRegion, x, y + T - 11, T, 11);
                } else if (t == GameMap.SPIKES) {
                    d.batch.draw(spikeRegion, x, y, T, T);
                }
            }
        }
        // bordas e topo
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                if (m.get(tx, ty) != GameMap.SOLID) continue;
                float x = tx * T, y = ty * T;
                boolean up = m.solid(tx, ty + 1), down = m.solid(tx, ty - 1), left = m.solid(tx - 1, ty), right = m.solid(tx + 1, ty);
                d.rgb(SoldierPainter.OUTLINE, 0.9f);
                if (!left) d.rect(x - 1.5f, y, 3, T);
                if (!right) d.rect(x + T - 1.5f, y, 3, T);
                if (!down) d.rect(x, y - 1.5f, T, 3);
                if (!up) {
                    d.rect(x - (left ? 0 : 1.5f), y + T - 1.5f, T + (left ? 0 : 1.5f) + (right ? 0 : 1.5f), 3);
                    d.color(1, 1, 1, 1);
                    d.batch.draw(topRegion, x - 1, y + T - 9, T + 2, 11);
                }
                if (!down && th != Theme.FACTORY) {
                    d.color(0, 0, 0, 0.18f);
                    d.rect(x, y, T, 6);
                }
            }
        }
    }

    private void drawZones(Draw d, World w) {
        if (w.mode instanceof BombMode) {
            BombMode bm = (BombMode) w.mode;
            for (int i = 0; i < 2; i++) {
                Vector2 s = bm.sitePos(i);
                d.color(1f, 0.3f, 0.2f, 0.12f + 0.05f * MathUtils.sin(time * 3));
                d.circle(s.x, s.y + 20, BombMode.SITE_RADIUS);
                d.color(1f, 0.4f, 0.3f, 0.5f);
                d.ring(s.x, s.y + 20, BombMode.SITE_RADIUS);
                a.huge.setColor(1, 0.4f, 0.3f, 0.55f);
                a.huge.draw(d.batch, i == 0 ? "A" : "B", s.x, s.y + 60, 0, Align.center, false);
                a.huge.setColor(Color.WHITE);
            }
        }
        if (w.mode instanceof CtfMode && w.map.hasFlags) {
            for (int t = 1; t <= 2; t++) {
                Vector2 b = t == 1 ? w.map.flagRed : w.map.flagBlue;
                d.rgb(t == 1 ? SoldierPainter.RED : SoldierPainter.BLUE, 0.25f);
                d.ellipse(b.x, b.y + 2, 46, 10, 0);
                d.rgb(t == 1 ? SoldierPainter.RED : SoldierPainter.BLUE, 0.15f);
                d.soft(b.x, b.y + 40, 70);
            }
        }
    }

    private void drawModeObjects(Draw d, World w) {
        if (w.mode instanceof CtfMode) {
            CtfMode m = (CtfMode) w.mode;
            for (int t = 1; t <= 2; t++) {
                if (m.state[t] == CtfMode.CARRIED) continue;
                drawFlag(d, m.fx[t], m.fy[t], t == 1 ? SoldierPainter.RED : SoldierPainter.BLUE, m.state[t] == CtfMode.DROPPED);
            }
        }
        if (w.mode instanceof BombMode) {
            BombMode bm = (BombMode) w.mode;
            if (bm.bombState == BombMode.B_DROPPED || bm.bombState == BombMode.B_PLANTED || bm.bombState == BombMode.B_DEFUSED) {
                float x = bm.bombX, y = bm.bombY;
                d.rgb(SoldierPainter.OUTLINE, 1);
                d.rect(x - 11, y - 1, 22, 14);
                d.rgb(0x8b0000, 1);
                d.rect(x - 9.5f, y, 19, 11.5f);
                d.rgb(0x222222, 1);
                d.rect(x - 6, y + 3, 12, 6);
                boolean blink = bm.bombState == BombMode.B_PLANTED && (time * (bm.bombTimer < 10 ? 6 : 2)) % 1 < 0.5f;
                d.rgb(blink ? 0xff1744 : 0x550000, 1);
                d.circle(x + 6, y + 9, 2.5f);
                if (blink) {
                    d.additive(true);
                    d.color(1, 0.1f, 0.1f, 0.6f);
                    d.soft(x + 6, y + 9, 22);
                    d.additive(false);
                }
            }
        }
    }

    private void drawFlag(Draw d, float x, float y, int color, boolean dropped) {
        d.rgb(SoldierPainter.OUTLINE, 1);
        d.rect(x - 2.5f, y, 5, 52);
        d.rgb(0xeeeeee, 1);
        d.rect(x - 1.2f, y, 2.4f, 51);
        for (int i = 0; i < 6; i++) {
            float wave = MathUtils.sin(time * 6 + i * 0.7f) * 2.5f;
            d.rgb(SoldierPainter.OUTLINE, 1);
            d.rect(x + 1 + i * 5, y + 31 + wave - 1, 6, 20);
            d.rgb(color, 1);
            d.rect(x + 1 + i * 5, y + 31 + wave, 5.5f, 18);
        }
        if (dropped) {
            d.additive(true);
            d.rgb(color, 0.3f + 0.2f * MathUtils.sin(time * 8));
            d.soft(x + 12, y + 30, 40);
            d.additive(false);
        }
    }

    private void drawPickups(Draw d, World w, float minX, float minY, float maxX, float maxY) {
        for (Pickup pk : w.pickups) {
            if (!pk.active) continue;
            if (pk.x < minX - 40 || pk.x > maxX + 40 || pk.y < minY - 40 || pk.y > maxY + 40) continue;
            float bob = MathUtils.sin(pk.bob * 3) * 3;
            float cx = pk.x, cy = pk.y + 16 + bob;
            if (pk.dropped && pk.life < 4 && (time * 6) % 1 < 0.4f) continue;
            // brilho
            d.additive(true);
            int glow = 0xfff59d;
            switch (pk.type) {
                case HEALTH: glow = 0xff8a80; break;
                case ARMOR: glow = 0x82b1ff; break;
                case DAMAGE: glow = 0xff5252; break;
                case JETPACK: glow = 0x40c4ff; break;
                case SPEED: glow = 0xffff00; break;
                default: break;
            }
            d.rgb(glow, 0.35f + 0.1f * MathUtils.sin(pk.bob * 4));
            d.soft(cx, cy, 26);
            d.additive(false);
            switch (pk.type) {
                case WEAPON:
                    if (pk.weapon != null) painter.drawGun(d, pk.weapon, cx, cy, -8 + MathUtils.sin(pk.bob * 2) * 6, 1.05f, 1);
                    break;
                case HEALTH:
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.rect(cx - 11, cy - 9, 22, 18);
                    d.rgb(0xffffff, 1);
                    d.rect(cx - 9.5f, cy - 7.5f, 19, 15);
                    d.rgb(0xe53935, 1);
                    d.rect(cx - 2.5f, cy - 6, 5, 12);
                    d.rect(cx - 6, cy - 2.5f, 12, 5);
                    break;
                case GRENADES:
                    for (int i = 0; i < 2; i++) {
                        float gx = cx - 6 + i * 12;
                        d.rgb(SoldierPainter.OUTLINE, 1);
                        d.circle(gx, cy - 2, 7);
                        d.rgb(0x33691e, 1);
                        d.circle(gx, cy - 2, 5.5f);
                        d.rgb(0x9e9e9e, 1);
                        d.rect(gx - 2, cy + 3, 4, 4);
                    }
                    break;
                case ARMOR:
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.batch.draw(a.half, cx - 12, cy - 2, 24, 13);
                    d.batch.draw(a.half, cx - 12, cy - 2, 12, 6.5f, 24, 13, 1, 1, 180);
                    d.rgb(0x1e88e5, 1);
                    d.rect(cx - 9, cy - 2, 18, 12);
                    d.rgb(0x90caf9, 1);
                    d.rect(cx - 2, cy - 1, 4, 10);
                    break;
                case DAMAGE:
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.region(a.star, cx, cy, 34, 34, pk.bob * 40);
                    d.rgb(0xff1744, 1);
                    d.region(a.star, cx, cy, 28, 28, pk.bob * 40);
                    break;
                case JETPACK:
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.rect(cx - 8, cy - 9, 16, 20);
                    d.rgb(0x607d8b, 1);
                    d.rect(cx - 6.5f, cy - 7.5f, 13, 17);
                    d.rgb(0x40c4ff, 1);
                    d.region(a.tri, cx, cy - 14, 10, 10, 180);
                    break;
                case SPEED:
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.region(a.arrow, cx, cy, 30, 26, 0);
                    d.rgb(0xffea00, 1);
                    d.region(a.arrow, cx, cy, 24, 20, 0);
                    break;
            }
        }
    }

    private void drawProjectilesSolid(Draw d, World w, float minX, float minY, float maxX, float maxY) {
        for (Projectile pr : w.projectiles) {
            if (pr.x < minX - 60 || pr.x > maxX + 60 || pr.y < minY - 60 || pr.y > maxY + 60) continue;
            switch (pr.kind) {
                case ROCKET: {
                    float deg = MathUtils.atan2(pr.vy, pr.vx) * MathUtils.radiansToDegrees;
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.rect(pr.x - 11, pr.y - 4.5f, 22, 9, 11, 4.5f, deg);
                    d.rgb(0x556b2f, 1);
                    d.rect(pr.x - 9.5f, pr.y - 3, 19, 6, 9.5f, 3, deg);
                    d.rgb(0xd32f2f, 1);
                    d.rect(pr.x + MathUtils.cosDeg(deg) * 7 - 3, pr.y + MathUtils.sinDeg(deg) * 7 - 3, 6, 6, 3, 3, deg);
                    break;
                }
                case SHELL:
                case FRAG: {
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.circle(pr.x, pr.y + 5, 6.5f);
                    d.rgb(pr.kind == Projectile.Kind.FRAG ? 0x33691e : 0x4e342e, 1);
                    d.circle(pr.x, pr.y + 5, 5);
                    d.rgb(0xffffff, 0.4f);
                    d.circle(pr.x - 1.5f, pr.y + 6.5f, 1.6f);
                    if (pr.kind == Projectile.Kind.FRAG && (time * (pr.fuse < 1 ? 10 : 4)) % 1 < 0.5f) {
                        d.rgb(0xff1744, 1);
                        d.circle(pr.x, pr.y + 10, 1.8f);
                    }
                    break;
                }
                case MINE: {
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.batch.draw(a.half, pr.x - 9, pr.y - 1, 9, 1, 18, 9, 1, 1, pr.rot);
                    d.rgb(0x455a64, 1);
                    d.batch.draw(a.half, pr.x - 7.5f, pr.y, 7.5f, 0, 15, 7.5f, 1, 1, pr.rot);
                    boolean on = pr.armed && (time * 2) % 1 < 0.5f;
                    d.rgb(on ? 0xff1744 : (pr.armed ? 0x550000 : 0xffeb3b), 1);
                    d.circle(pr.x - MathUtils.sinDeg(pr.rot) * 5, pr.y + MathUtils.cosDeg(pr.rot) * 5, 1.8f);
                    break;
                }
                case DISC: {
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.region(a.star, pr.x, pr.y, 24, 24, pr.rot);
                    d.rgb(0xcfd8dc, 1);
                    d.region(a.star, pr.x, pr.y, 20, 20, pr.rot);
                    d.rgb(0x607d8b, 1);
                    d.circle(pr.x, pr.y, 3);
                    break;
                }
                default:
                    break;
            }
        }
    }

    private void drawProjectilesGlow(Draw d, World w, float minX, float minY, float maxX, float maxY) {
        for (Projectile pr : w.projectiles) {
            if (pr.x < minX - 60 || pr.x > maxX + 60 || pr.y < minY - 60 || pr.y > maxY + 60) continue;
            if (pr.kind == Projectile.Kind.BULLET) {
                float sp = (float) Math.sqrt(pr.vx * pr.vx + pr.vy * pr.vy) + 1;
                float dx = pr.x - pr.startX, dy = pr.y - pr.startY;
                float travelled = (float) Math.sqrt(dx * dx + dy * dy);
                float len = Math.min(travelled, pr.weapon == WeaponType.SNIPER ? 90 : (pr.weapon == WeaponType.SHOTGUN ? 16 : 30));
                if (travelled < 14) continue;
                float tx = pr.x - pr.vx / sp * len, ty = pr.y - pr.vy / sp * len;
                d.color(1f, 0.85f, 0.4f, 0.55f);
                d.line(tx, ty, pr.x, pr.y, pr.weapon == WeaponType.SNIPER ? 4.5f : 3.2f);
                d.color(1f, 1f, 0.9f, 0.95f);
                d.line(tx + (pr.x - tx) * 0.4f, ty + (pr.y - ty) * 0.4f, pr.x, pr.y, 1.6f);
            } else if (pr.kind == Projectile.Kind.FLAME) {
                float t = 1 - MathUtils.clamp(pr.life / Math.max(0.01f, pr.weapon.lifetime()), 0, 1);
                float size = 6 + t * 22;
                d.color(1f, 0.75f - t * 0.5f, 0.2f - t * 0.2f, 0.7f * (1 - t * 0.6f));
                d.soft(pr.x, pr.y, size);
                d.color(1f, 1f, 0.6f, 0.5f * (1 - t));
                d.soft(pr.x, pr.y, size * 0.4f);
            } else if (pr.kind == Projectile.Kind.ROCKET) {
                d.color(1f, 0.6f, 0.1f, 0.6f);
                d.soft(pr.x - pr.vx * 0.012f, pr.y - pr.vy * 0.012f, 14);
            } else if (pr.kind == Projectile.Kind.DISC) {
                d.color(1f, 1f, 1f, 0.25f);
                d.soft(pr.x, pr.y, 18);
            }
        }
    }

    private void drawPlayer(Draw d, Player p, World w) {
        pose.x = p.x;
        pose.y = p.y;
        pose.aim = p.aim;
        pose.facing = p.facing;
        pose.anim = p.anim;
        pose.onGround = p.onGround;
        pose.crouch = p.crouching;
        pose.jetting = p.jetting;
        pose.look = p.look;
        pose.team = w.config.mode.teams ? p.team : 0;
        if (p.dummy) pose.team = 0;
        pose.weapon = p.gun() != null ? p.gun().type : null;
        pose.meleeAnim = p.meleeAnim;
        pose.hurtFlash = p.hurtFlash;
        pose.time = time + p.id;
        pose.carryingFlag = p.carryingFlag != 0;
        pose.flagColor = p.carryingFlag == C.TEAM_RED ? SoldierPainter.RED : SoldierPainter.BLUE;
        pose.hasBomb = p.hasBomb;
        pose.shield = p.spawnShield > 0;
        pose.alpha = 1;
        if (p.dmgBoost > 0) {
            d.additive(true);
            d.color(1, 0.2f, 0.2f, 0.35f + 0.1f * MathUtils.sin(time * 12));
            d.soft(p.x, p.y + 22, 40);
            d.additive(false);
        }
        if (p.speedBoost > 0 && Math.abs(p.vx) > 50) {
            d.color(1, 1, 0.3f, 0.25f);
            d.rect(p.x - Math.signum(p.vx) * 30 - 10, p.y + 10, 20, 3);
            d.rect(p.x - Math.signum(p.vx) * 40 - 10, p.y + 22, 24, 3);
        }
        painter.draw(d, pose);
    }

    private void drawNames(Draw d, World w, Player local, float minX, float minY, float maxX, float maxY) {
        BitmapFont f = a.small;
        for (Player p : w.players) {
            if (!p.alive || p == local) continue;
            if (p.x < minX - 60 || p.x > maxX + 60 || p.y < minY - 80 || p.y > maxY + 80) continue;
            boolean ally = local != null && w.config.mode.teams && p.team == local.team;
            float ny = p.y + p.height() + 24;
            if (ally) f.setColor(0.6f, 1f, 0.6f, 1);
            else if (w.config.mode.teams) f.setColor(p.team == C.TEAM_RED ? 1f : 0.55f, p.team == C.TEAM_RED ? 0.55f : 0.75f, p.team == C.TEAM_RED ? 0.5f : 1f, 1);
            else f.setColor(1, 1, 1, 0.95f);
            f.draw(d.batch, p.name, p.x, ny, 0, Align.center, false);
            if (p.health < C.HEALTH_MAX || p.armor > 0) {
                float bw = 34;
                d.bar(p.x - bw / 2, ny - 22, bw, 3.5f, p.health / C.HEALTH_MAX, p.health > 50 ? 0x66bb6a : (p.health > 25 ? 0xffca28 : 0xef5350), 0x3a1010);
                if (p.armor > 0) {
                    d.rgb(0x42a5f5, 1);
                    d.rect(p.x - bw / 2, ny - 26, bw * p.armor / C.ARMOR_MAX, 2.5f);
                }
            }
        }
        f.setColor(Color.WHITE);
    }

    private void drawCrosshair(Draw d, Player local, float cx, float cy) {
        Projectile.Kind k = local.gun() != null ? local.gun().type.kind : Projectile.Kind.BULLET;
        float r = 9;
        d.rgb(0x000000, 0.5f);
        d.ring(cx, cy, r + 1.5f);
        d.rgb(0xffffff, 0.9f);
        d.ring(cx, cy, r);
        d.rect(cx - 1, cy + r - 2, 2, 7);
        d.rect(cx - 1, cy - r - 5, 2, 7);
        d.rect(cx + r - 2, cy - 1, 7, 2);
        d.rect(cx - r - 5, cy - 1, 7, 2);
        if (k == Projectile.Kind.FRAG) d.circle(cx, cy, 2);
    }

    @Override
    public void dispose() {
        if (tileTex != null) tileTex.dispose();
        if (skyTex != null) skyTex.dispose();
    }
}
