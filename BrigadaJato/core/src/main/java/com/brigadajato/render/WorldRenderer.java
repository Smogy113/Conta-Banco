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
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.brigadajato.I18n;
import com.brigadajato.world.Boss;
import com.brigadajato.world.C;
import com.brigadajato.world.GameMap;
import com.brigadajato.world.Prop;
import com.brigadajato.world.Pickup;
import com.brigadajato.world.Player;
import com.brigadajato.world.Projectile;
import com.brigadajato.world.Theme;
import com.brigadajato.world.WeaponType;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;
import com.brigadajato.world.modes.BombMode;
import com.brigadajato.world.modes.BattleRoyaleMode;
import com.brigadajato.world.modes.CtfMode;
import com.brigadajato.world.modes.KingHillMode;

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
    private TileArt art1, art2;
    private Backdrop bg1, bg2;
    public final Lighting lighting = new Lighting();
    public final Debris debris = new Debris();
    /** 0 dia .. 1 noite; posicao do sol; cor do ambiente para a luz */
    public float night, sunPos;
    private float ambR = 1, ambG = 1, ambB = 1;
    private final Array<float[]> lavaSeen = new Array<>();
    private int lavaCount;

    public float shake;
    public float time;
    /** desenhando um replay (nao cria balas visuais a partir dos tiros) */
    public boolean replayMode;

    private static class Beam {
        float x1, y1, x2, y2, life;
    }

    private final Array<Beam> beams = new Array<>();

    /** raio em zigue-zague (arma tesla) */
    private static class Bolt {
        float[] pts;
        float life;
    }

    private final Array<Bolt> bolts = new Array<>();

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
        bolts.clear();
        texts.clear();
        lighting.clear();
        debris.clear();
        applySettings();
        disposeArt();
        art1 = new TileArt(w.map.theme);
        bg1 = new Backdrop(w.map.theme, w.map.id, w.map.worldH());
        if (w.map.theme2 != null) {
            art2 = new TileArt(w.map.theme2);
            bg2 = new Backdrop(w.map.theme2, w.map.id + "2", w.map.worldH());
        }
        for (int i = 0; i < 60; i++) lavaSeen.add(new float[2]);
    }

    private void disposeArt() {
        if (art1 != null) art1.dispose();
        if (art2 != null) art2.dispose();
        if (bg1 != null) bg1.dispose();
        if (bg2 != null) bg2.dispose();
        art1 = art2 = null;
        bg1 = bg2 = null;
    }

    private boolean nearSolid(float x, float y, float r) {
        GameMap m = world.map;
        for (int k = 0; k < 8; k++) {
            float a = k * MathUtils.PI2 / 8;
            if (m.solidAt(x + MathUtils.cos(a) * r, y + MathUtils.sin(a) * r)) return true;
        }
        return m.solidAt(x, y - r);
    }

    private TileArt artAt(int ty) {
        return art2 != null && ty <= world.map.lowerTop ? art2 : art1;
    }

    private Theme themeAtY(float y) {
        return world.map.themeAt((int) Math.floor(y / C.TILE));
    }

    /** Reaplica as opcoes visuais (qualidade, particulas, contorno). */
    public void applySettings() {
        float q = settings.quality == 0 ? 0.35f : (settings.quality == 1 ? 0.65f : (settings.quality == 2 ? 1f : 1.25f));
        fx.quality = Math.min(1f, q * settings.particles);
        SoldierPainter.outlineWidth = settings.outline == 0 ? 0.6f : (settings.outline == 1 ? 1.2f : (settings.outline == 2 ? 1.7f : 2.4f));
    }

    private int bloodColor() {
        return settings.blood == 1 ? 0x76c442 : 0xb71c1c;
    }

    /** Marcador de acerto na mira (0..1). */
    public float hitMarker;

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
                    if (wt.kind == Projectile.Kind.BULLET && !wt.instant()) {
                        float side = MathUtils.cos(ang) >= 0 ? -1 : 1;
                        debris.casing(e.x, e.y, side * MathUtils.random(60, 140), MathUtils.random(160, 260),
                                wt == WeaponType.SHOTGUN ? 0xc62828 : 0xd4ac0d);
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
                if (!w.authority && !replayMode) w.spawnVisualShot(e);
                if (wt.kind != Projectile.Kind.FLAME && wt.kind != Projectile.Kind.FROST) lighting.flash(mx, my, 120, 0xffd08a, 1f, 0.07f);
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
                if (nearSolid(e.x, e.y, e.f * 0.45f)) debris.scorch(e.x, e.y, e.f * 0.75f);
                lighting.flash(e.x, e.y, e.f * 3.2f, 0xffa040, 1.3f, 0.6f);
                break;
            case HIT: {
                int n = settings.blood == 2 ? 0 : (settings.highQuality ? 6 : 3);
                if (e.b == localId) hitMarker = 1f;
                if (settings.blood == 2 && !fx.skip()) {
                    Particles.P sp = fx.spawn(Particles.LINE, e.x, e.y, MathUtils.random(-200, 200), MathUtils.random(-50, 200), 0.18f, 2.5f, 0.5f, 0xffffff, 1);
                    sp.additive = true;
                }
                for (int i = 0; i < n; i++) {
                    Particles.P b = fx.spawn(Particles.CIRCLE, e.x, e.y, MathUtils.random(-160, 160), MathUtils.random(-30, 220), 0.6f, MathUtils.random(1.8f, 3.6f), 0.8f, bloodColor(), 1);
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
                for (int i = 0; i < (settings.blood == 2 ? 0 : 12); i++) {
                    Particles.P b = fx.spawn(Particles.CIRCLE, e.x, e.y, MathUtils.random(-260, 260), MathUtils.random(-50, 320), 0.8f, MathUtils.random(2, 4.5f), 1, bloodColor(), 1);
                    b.grav = 900;
                }
                audio.at("death", e.x, e.y);
                if (e.a == localId && e.b != localId) {
                    int lvl = MathUtils.clamp(e.d > 0 ? e.d : 1, 1, 5);
                    if (settings.killBanner && !replayMode) {
                        audio.ui("kill" + lvl);
                        if (e.f > 0) audio.ui("headshot");
                    }
                    audio.vibrate(40 + lvl * 25);
                }
                break;
            }
            case SPARK: {
                for (int i = 0; i < 4 && !fx.skip(); i++) {
                    float ang = e.f + MathUtils.PI + MathUtils.random(-1.1f, 1.1f);
                    Particles.P s = fx.spawn(Particles.LINE, e.x, e.y, MathUtils.cos(ang) * 260, MathUtils.sin(ang) * 260, 0.18f, 2.5f, 0.5f, 0xffd54f, 1);
                    s.additive = true;
                    s.grav = 600;
                }
                debris.hole(e.x, e.y, e.x + MathUtils.cos(e.f) * 4, e.y + MathUtils.sin(e.f) * 4);
                Particles.P dust = fx.spawn(Particles.SOFT, e.x, e.y, 0, 15, 0.45f, 4, 13, themeAtY(e.y).tile, 0.6f);
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
            case TILE: {
                if (!w.authority) w.applyTile(e.a + e.b * w.map.w, e.c);
                if (e.c == GameMap.EMPTY) { // caixa quebrou
                    for (int i = 0; i < 10; i++) {
                        Particles.P pl = fx.spawn(Particles.RECT, e.x + MathUtils.random(-12, 12), e.y + MathUtils.random(-12, 12), MathUtils.random(-260, 260),
                                MathUtils.random(60, 380), 1.3f, MathUtils.random(5, 12), 3.5f, i % 3 == 0 ? 0x6d4422 : 0xb07a3c, 1);
                        pl.grav = 1100;
                        pl.vrot = MathUtils.random(-700, 700);
                        pl.a2 = 1;
                    }
                    for (int i = 0; i < 4; i++) {
                        Particles.P sm = fx.spawn(Particles.SOFT, e.x, e.y, MathUtils.random(-50, 50), MathUtils.random(10, 60), 0.8f, 8, 26, 0xbcaaa4, 0.5f);
                        sm.drag = 2;
                    }
                    audio.at("crate_break", e.x, e.y);
                } else if (e.c == GameMap.DOOR || e.c == GameMap.DOOR_OPEN) {
                    Particles.P sm = fx.spawn(Particles.SOFT, e.x, e.y - 12, MathUtils.random(-30, 30), 10, 0.5f, 6, 16, 0x9e9e9e, 0.35f);
                    sm.drag = 2;
                }
                break;
            }
            case PROP: {
                for (int i = 0; i < 8; i++) {
                    Particles.P pl = fx.spawn(Particles.RECT, e.x, e.y + 16, MathUtils.random(-300, 300), MathUtils.random(150, 520), 1.6f, MathUtils.random(6, 12), 5, i % 2 == 0 ? 0xc62828 : 0x37474f, 1);
                    pl.grav = 1100;
                    pl.vrot = MathUtils.random(-800, 800);
                    pl.a2 = 1;
                }
                break;
            }
            case LEVER:
                audio.at("lever", e.x, e.y);
                audio.play("door", e.x, e.y, 0.8f, e.b == 1 ? 1f : 0.85f);
                if (!w.authority && e.a >= 0 && e.a < w.leverOn.length) w.leverOn[e.a] = e.b == 1;
                break;
            case HOOK:
                if (e.b == 0) audio.at("hook", e.x, e.y);
                else {
                    audio.at("hook_hit", e.x, e.y);
                    for (int i = 0; i < 5; i++) {
                        Particles.P sp = fx.spawn(Particles.LINE, e.x, e.y, MathUtils.random(-200, 200), MathUtils.random(-200, 200), 0.2f, 2.5f, 0.5f, 0xffe082, 1);
                        sp.additive = true;
                    }
                }
                break;
            case BLOCK: {
                for (int i = 0; i < 7; i++) {
                    Particles.P sp = fx.spawn(Particles.LINE, e.x, e.y, MathUtils.random(-280, 280), MathUtils.random(-120, 260), 0.22f, 3, 0.5f, 0xfff59d, 1);
                    sp.additive = true;
                    sp.grav = 700;
                }
                lighting.flash(e.x, e.y, 70, 0xfff2b0, 1f, 0.08f);
                audio.at("block", e.x, e.y);
                break;
            }
            case LIGHTNING: {
                if (e.angles != null && e.angles.length >= 4) {
                    Bolt b = new Bolt();
                    b.pts = e.angles;
                    b.life = 0.22f;
                    bolts.add(b);
                    for (int k = 2; k + 1 < e.angles.length; k += 2) {
                        lighting.flash(e.angles[k], e.angles[k + 1], 200, 0x9fd4ff, 1.2f, 0.15f);
                        for (int i = 0; i < 6; i++) {
                            Particles.P sp = fx.spawn(Particles.LINE, e.angles[k], e.angles[k + 1], MathUtils.random(-260, 260), MathUtils.random(-260, 260), 0.2f, 3, 0.5f, 0xb3e5fc, 1);
                            sp.additive = true;
                        }
                    }
                }
                audio.at("tesla", e.x, e.y);
                if (e.a == localId) shake = Math.max(shake, 3);
                break;
            }
            case EMOTE: {
                Player p = w.player(e.a);
                if (p != null) {
                    p.emote = e.b;
                    p.emoteTimer = 2.4f;
                }
                audio.play(e.b >= C.EMOTE_DANCE_FIRST ? "dance" : "emote", e.x, e.y, 0.8f, 1f);
                break;
            }
            case FREEZE: {
                for (int i = 0; i < 12; i++) {
                    Particles.P ic = fx.spawn(Particles.RECT, e.x, e.y, MathUtils.random(-200, 200), MathUtils.random(-50, 260), 0.8f, MathUtils.random(3, 7), 3, 0xe1f5fe, 1);
                    ic.grav = 800;
                    ic.vrot = MathUtils.random(-600, 600);
                }
                audio.at("freeze", e.x, e.y);
                break;
            }
            case BOSS: {
                if (e.b == 0) {
                    audio.ui("boss");
                    shake = Math.max(shake, 6);
                } else if (e.b == 1) {
                    audio.ui("boss_down");
                    shake = Math.max(shake, 18);
                    lighting.flash(e.x, e.y, 600, 0xffa040, 1.5f, 1.2f);
                } else if (e.b == 2) {
                    audio.at("charge", e.x, e.y);
                } else if (e.b == 3 || e.b == 4) {
                    shake = Math.max(shake, e.b == 3 ? 12 : 8);
                    audio.at("stomp", e.x, e.y);
                    for (int i = 0; i < 14; i++) {
                        Particles.P dd = fx.spawn(Particles.SOFT, e.x + MathUtils.random(-60, 60), e.y + 4, MathUtils.random(-320, 320), MathUtils.random(20, 120), 0.9f, 10, 30, 0xa1887f, 0.55f);
                        dd.drag = 3;
                    }
                }
                break;
            }
            case LEVEL_UP: {
                Player p = w.player(e.a);
                if (p != null) floatText(I18n.t("lvl") + " " + (e.b + 1), p.x, p.y + 70, 1f, 0.85f, 0.2f, 1.1f);
                if (e.a == localId) audio.ui("levelup");
                for (int i = 0; i < 14; i++) {
                    float ang = i / 14f * MathUtils.PI2;
                    Particles.P sp = fx.spawn(Particles.SOFT, e.x, e.y + 24, MathUtils.cos(ang) * 120, MathUtils.sin(ang) * 120, 0.5f, 5, 1, 0xffd740, 1);
                    sp.additive = true;
                }
                break;
            }
            case INFECT: {
                for (int i = 0; i < 18; i++) {
                    Particles.P g = fx.spawn(Particles.SOFT, e.x, e.y + 22, MathUtils.random(-160, 160), MathUtils.random(-40, 200), 0.8f, 6, 14, 0x76ff03, 0.8f);
                    g.drag = 2;
                    g.additive = true;
                }
                audio.at("infect", e.x, e.y);
                break;
            }
            case FLASHBANG: {
                Particles.P fl = fx.spawn(Particles.SOFT, e.x, e.y, 0, 0, 0.35f, 260, 40, 0xffffff, 1);
                fl.additive = true;
                for (int i = 0; i < 12; i++) {
                    float ang = i / 12f * MathUtils.PI2;
                    Particles.P sp = fx.spawn(Particles.LINE, e.x, e.y, MathUtils.cos(ang) * 700, MathUtils.sin(ang) * 700, 0.25f, 6, 1, 0xfffde7, 1);
                    sp.additive = true;
                    sp.drag = 3;
                }
                lighting.flash(e.x, e.y, 700, 0xffffff, 2f, 0.5f);
                audio.at("flashbang", e.x, e.y);
                if (local != null) {
                    float dx = local.x - e.x, dy = local.y - e.y;
                    if (dx * dx + dy * dy < 700 * 700) shake = Math.max(shake, 6);
                }
                break;
            }
            case ROUND_END:
                audio.ui(local != null && e.a == local.id ? "win" : "round");
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
        int fire = (int) (12 * k * fx.quality) + 2;
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
        for (int i = 0; i < (int) (8 * fx.quality) + 2; i++) {
            Particles.P d = fx.spawn(Particles.RECT, x, y, MathUtils.random(-380, 380), MathUtils.random(80, 520), 1.2f, MathUtils.random(4, 9), 4, themeAtY(y).tileDark, 1);
            d.grav = 1200;
            d.vrot = MathUtils.random(-900, 900);
            d.a2 = 1;
        }
        for (int i = 0; i < (int) (10 * fx.quality) + 2; i++) {
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
        for (int i = bolts.size - 1; i >= 0; i--) {
            bolts.get(i).life -= dt;
            if (bolts.get(i).life <= 0) bolts.removeIndex(i);
        }
        shake = Math.max(0, shake - dt * 30);
        hitMarker = Math.max(0, hitMarker - dt * 5);
        lighting.update(dt);
        if (world != null) debris.update(dt, world.map);
        updateDayNight();
        updateAmbient(dt);
        // efeitos continuos (jetpack, foguetes, chamas)
        if (world == null) return;
        for (int i50_ = 0; i50_ < world.players.size; i50_++) {
            Player p = world.players.get(i50_);
            if (!p.alive || !p.jetting) continue;
            if (fx.skip()) continue;
            float nx = p.x - p.facing * 10, ny = p.y + (p.crouching ? 4 : 9);
            boolean boost = p.jetBoost > 0;
            int flameCol = boost ? 0x40c4ff : p.look.flameColor(time + p.id);
            int jets = p.look.jetpack == 1 ? 2 : 1;
            for (int k = 0; k < jets; k++) {
                float off = jets == 2 ? (k == 0 ? -2.7f : 2.7f) : 0;
                Particles.P f = fx.spawn(Particles.SOFT, nx + off + MathUtils.random(-2, 2), ny, MathUtils.random(-20, 20) + p.vx * 0.2f, -260 + p.vy * 0.2f, 0.16f, jets == 2 ? 5 : 7, 2, flameCol, 1);
                f.additive = true;
            }
            if (MathUtils.randomBoolean(0.6f)) { // fumaca que enrola
                float curl = MathUtils.sin(time * 9 + p.id * 1.7f) * 55;
                Particles.P s = fx.spawn(Particles.SOFT, nx, ny - 8, curl + p.vx * 0.15f, -70 + MathUtils.random(-15, 15), 0.9f, 4, 20, 0xd7d7d7, 0.32f);
                s.drag = 1.6f;
                s.grav = -50;
            }
            if (MathUtils.randomBoolean(0.25f)) { // faiscas
                Particles.P sp = fx.spawn(Particles.LINE, nx, ny - 4, MathUtils.random(-90, 90), MathUtils.random(-260, -120), 0.35f, 3.5f, 0.6f, boost ? 0x80d8ff : 0xffcc66, 1);
                sp.additive = true;
                sp.grav = 500;
            }
        }
        for (int i51_ = 0; i51_ < world.projectiles.size; i51_++) {
            Projectile pr = world.projectiles.get(i51_);
            if (pr.kind == Projectile.Kind.ROCKET && !fx.skip()) {
                Particles.P s = fx.spawn(Particles.SOFT, pr.x, pr.y, MathUtils.random(-20, 20), MathUtils.random(-20, 20), 0.7f, 5, 16, 0xcfcfcf, 0.5f);
                s.drag = 2;
                Particles.P f = fx.spawn(Particles.SOFT, pr.x, pr.y, 0, 0, 0.08f, 9, 3, 0xffab00, 1);
                f.additive = true;
            } else if (pr.kind == Projectile.Kind.FRAG && settings.highQuality && MathUtils.randomBoolean(0.3f)) {
                fx.spawn(Particles.SOFT, pr.x, pr.y + 4, 0, 30, 0.3f, 2, 5, 0x9e9e9e, 0.4f);
            } else if (pr.kind == Projectile.Kind.VORTEX && pr.stuck && !fx.skip()) {
                // poeira sendo sugada para o buraco negro
                float ang = MathUtils.random(MathUtils.PI2), rr = MathUtils.random(90, 240);
                float px = pr.x + MathUtils.cos(ang) * rr, py = pr.y + MathUtils.sin(ang) * rr;
                Particles.P sp = fx.spawn(Particles.SOFT, px, py, (pr.x - px) * 2.4f - MathUtils.sin(ang) * 120, (pr.y - py) * 2.4f + MathUtils.cos(ang) * 120, 0.4f, 4, 1, 0xb388ff, 0.9f);
                sp.additive = true;
            } else if (pr.kind == Projectile.Kind.BOOMERANG && settings.highQuality && MathUtils.randomBoolean(0.4f)) {
                Particles.P sp = fx.spawn(Particles.SOFT, pr.x, pr.y, 0, 0, 0.25f, 5, 2, 0xffe082, 0.35f);
                sp.additive = true;
            }
        }
    }

    public void render(Draw d, OrthographicCamera cam, World w, Player local, boolean showCrosshair, float crossX, float crossY) {
        float vw = cam.viewportWidth * cam.zoom, vh = cam.viewportHeight * cam.zoom;
        float minX = cam.position.x - vw / 2, minY = cam.position.y - vh / 2;
        float maxX = minX + vw, maxY = minY + vh;
        viewMinX = minX;
        viewMinY = minY;
        viewW = vw;
        viewH = vh;
        cam_zoom = cam.zoom;
        drawBackground(d, cam, minX, minY, vw, vh);
        drawZones(d, w);
        drawLamps(d, w, minX, minY, maxX, maxY, true);
        drawTiles(d, w.map, minX, minY, maxX, maxY);
        debris.drawMarks(d, w.map, minX, minY, maxX, maxY);
        drawLevers(d, w);
        drawProps(d, w, minX, minY, maxX, maxY);
        drawPickups(d, w, minX, minY, maxX, maxY);
        drawModeObjects(d, w);
        drawProjectilesSolid(d, w, minX, minY, maxX, maxY);
        drawHooks(d, w);
        for (int i = 0; i < w.bosses.size; i++) drawBoss(d, w.bosses.get(i));
        for (int i52_ = 0; i52_ < w.players.size; i52_++) {
            Player p = w.players.get(i52_);
            if (!p.alive) continue;
            if (p.x < minX - 60 || p.x > maxX + 60 || p.y < minY - 80 || p.y > maxY + 80) continue;
            drawPlayer(d, p, w);
        }
        debris.drawCasings(d, minX, minY, maxX, maxY);
        drawSmoke(d, w, minX, minY, maxX, maxY);
        fx.draw(d, false, minX, minY, maxX, maxY);
        d.additive(true);
        drawProjectilesGlow(d, w, minX, minY, maxX, maxY);
        fx.draw(d, true, minX, minY, maxX, maxY);
        for (int i53_ = 0; i53_ < beams.size; i53_++) {
            Beam b = beams.get(i53_);
            float t = b.life / 0.28f;
            d.color(0.2f, 1f, 0.85f, 0.6f * t);
            d.line(b.x1, b.y1, b.x2, b.y2, 9 * t + 2);
            d.color(1, 1, 1, 0.9f * t);
            d.line(b.x1, b.y1, b.x2, b.y2, 3 * t + 1);
        }
        drawBolts(d);
        drawLamps(d, w, minX, minY, maxX, maxY, false);
        d.additive(false);
        drawBossBars(d, w);
        if (settings.showNames) drawNames(d, w, local, minX, minY, maxX, maxY);
        drawEmotes(d, w, minX, minY, maxX, maxY);
        for (int i54_ = 0; i54_ < texts.size; i54_++) {
            FloatText t = texts.get(i54_);
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
        float camX = cam.position.x, camY = cam.position.y;
        if (bg2 == null) {
            bg1.draw(d, camX, camY, minX, minY, vw, vh, time, night, sunPos);
            return;
        }
        // duas regioes: o fundo de baixo aparece so abaixo da linha de divisao
        float boundary = (world.map.lowerTop + 1) * C.TILE;
        float f = (boundary - minY) / vh;
        if (f <= 0) {
            bg1.draw(d, camX, camY, minX, minY, vw, vh, time, night, sunPos);
            return;
        }
        if (f >= 1) {
            bg2.draw(d, camX, camY, minX, minY, vw, vh, time, night, sunPos);
            return;
        }
        bg1.draw(d, camX, camY, minX, minY, vw, vh, time, night, sunPos);
        d.batch.flush();
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        int bh = Gdx.graphics.getBackBufferHeight();
        Gdx.gl.glScissor(0, 0, Gdx.graphics.getBackBufferWidth(), Math.max(0, (int) (f * bh)));
        bg2.draw(d, camX, camY, minX, minY, vw, vh, time, night, sunPos);
        d.batch.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        // faixa escura na divisa (teto/piso entre os dois ambientes)
        d.color(0, 0, 0, 0.35f);
        d.rect(minX, boundary - 6, vw, 6);
    }

    private static int hashTile(int tx, int ty) {
        return (((tx + 64) * 7 + (ty + 64) * 13) & 0xff) % 4;
    }

    private void drawTiles(Draw d, GameMap m, float minX, float minY, float maxX, float maxY) {
        int x0 = Math.max(-12, (int) Math.floor(minX / C.TILE) - 1), x1 = Math.min(m.w + 11, (int) (maxX / C.TILE) + 1);
        int y0 = Math.max(-12, (int) Math.floor(minY / C.TILE) - 1), y1 = Math.min(m.h + 11, (int) (maxY / C.TILE) + 1);
        float T = C.TILE;
        lavaCount = 0;
        d.color(1, 1, 1, 1);
        for (int ty = y0; ty <= y1; ty++) {
            TileArt art = artAt(ty);
            for (int tx = x0; tx <= x1; tx++) {
                int t = m.get(tx, ty);
                float x = tx * T, y = ty * T;
                switch (t) {
                    case GameMap.SOLID:
                        d.batch.draw(art.solid[hashTile(tx, ty)], x, y, T, T);
                        break;
                    case GameMap.PLATFORM: {
                        boolean lp = m.get(tx - 1, ty) == GameMap.PLATFORM, rp = m.get(tx + 1, ty) == GameMap.PLATFORM;
                        // apoios em diagonal embaixo das pontas e a cada 3 blocos
                        if ((!lp || !rp || ((tx + 64) % 3 == 0)) && !m.solid(tx, ty - 1)) {
                            int sc = art.theme.platform;
                            d.rgb(SoldierPainter.OUTLINE, 1);
                            d.rect(x + T / 2 - 2.5f, y + T - 24, 5, 15);
                            d.rgb(SoldierPainter.darker(sc, 0.7f), 1);
                            d.rect(x + T / 2 - 1.5f, y + T - 23, 3, 13);
                            d.rgb(SoldierPainter.OUTLINE, 1);
                            d.rect(x + T / 2, y + T - 22, 14, 3, 0, 1.5f, 40);
                            d.rect(x + T / 2, y + T - 22, 14, 3, 0, 1.5f, 140);
                            d.color(1, 1, 1, 1);
                        }
                        d.batch.draw(art.plat, x - (lp ? 0 : 2), y + T - 12, T + (lp ? 0 : 2) + (rp ? 0 : 2), 12);
                        if (!lp || !rp) {
                            d.rgb(SoldierPainter.OUTLINE, 0.9f);
                            if (!lp) d.rect(x - 3, y + T - 12, 2, 12);
                            if (!rp) d.rect(x + T + 1, y + T - 12, 2, 12);
                            d.color(1, 1, 1, 1);
                        }
                        break;
                    }
                    case GameMap.SPIKES:
                        if (art.lava) {
                            float bob = MathUtils.sin(time * 2 + tx * 0.7f) * 1.5f;
                            d.batch.draw(art.spike, x, y + bob, T, T);
                            if (lavaCount < lavaSeen.size) {
                                lavaSeen.get(lavaCount)[0] = x + T / 2;
                                lavaSeen.get(lavaCount)[1] = y + T * 0.6f;
                                lavaCount++;
                            }
                        } else d.batch.draw(art.spike, x, y, T, T);
                        break;
                    case GameMap.BREAKABLE: {
                        d.batch.draw(art.crate, x, y, T, T);
                        int idx = tx + ty * m.w;
                        float frac = m.tileHp != null && idx >= 0 && idx < m.tileHp.length ? m.tileHp[idx] / (float) GameMap.BREAKABLE_HP : 1;
                        if (frac < 0.95f) cracks(d, x, y, T, 1 - frac, tx * 31 + ty * 17);
                        d.color(1, 1, 1, 1);
                        break;
                    }
                    case GameMap.DOOR:
                        d.batch.draw(art.door, x, y, T, T);
                        break;
                    case GameMap.DOOR_OPEN:
                        d.batch.draw(art.doorOpen, x, y, T, T);
                        break;
                    default:
                        break;
                }
            }
        }
        // bordas e topo (so blocos solidos normais)
        for (int ty = y0; ty <= y1; ty++) {
            TileArt art = artAt(ty);
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
                    d.batch.draw(art.top, x - 1, y + T - 9, T + 2, 11);
                }
                if (!down && art.theme.tiles != Theme.Tiles.METAL) {
                    d.color(0, 0, 0, 0.18f);
                    d.rect(x, y, T, 6);
                }
            }
        }
        d.color(1, 1, 1, 1);
    }

    /** Rachaduras na caixa conforme o dano. */
    private void cracks(Draw d, float x, float y, float T, float dmg, int seed) {
        int lines = 1 + (int) (dmg * 5);
        d.rgb(0x2b1a0c, 0.85f);
        for (int i = 0; i < lines; i++) {
            int h = (seed * 2654435761L + i * 40503L) > 0 ? (int) ((seed * 2654435761L + i * 40503L) & 0xffff) : i * 977;
            float cx = x + T * 0.5f + ((h & 31) - 16) * 0.4f, cy = y + T * 0.5f + (((h >> 5) & 31) - 16) * 0.4f;
            float ang = ((h >> 10) & 63) / 63f * 360;
            float len = T * (0.25f + 0.25f * dmg);
            d.rect(cx, cy - 1, len, 2, 0, 1, ang);
            d.rect(cx + MathUtils.cosDeg(ang) * len, cy + MathUtils.sinDeg(ang) * len - 1, len * 0.6f, 1.6f, 0, 0.8f, ang + 40);
        }
    }

    private void drawLevers(Draw d, World w) {
        for (int i = 0; i < w.map.levers.size; i++) {
            Vector2 l = w.map.levers.get(i);
            boolean on = i < w.leverOn.length && w.leverOn[i];
            float x = l.x, y = l.y + 14;
            d.rgb(SoldierPainter.OUTLINE, 1);
            d.rect(x - 10, y - 9, 20, 18);
            d.rgb(0x607d8b, 1);
            d.rect(x - 8.5f, y - 7.5f, 17, 15);
            float ang = on ? 50 : 130;
            d.rgb(SoldierPainter.OUTLINE, 1);
            d.rect(x, y - 2.5f, 20, 5, 0, 2.5f, ang);
            d.rgb(0xcfd8dc, 1);
            d.rect(x, y - 1.5f, 19, 3, 0, 1.5f, ang);
            d.rgb(on ? 0x66bb6a : 0xef5350, 1);
            d.circle(x + MathUtils.cosDeg(ang) * 20, y + MathUtils.sinDeg(ang) * 20, 4.5f);
            d.rgb(on ? 0x69f0ae : 0xff5252, 0.9f);
            d.circle(x + 6, y - 4, 2);
        }
    }

    private void drawProps(Draw d, World w, float minX, float minY, float maxX, float maxY) {
        for (int i = 0; i < w.props.size; i++) {
            Prop pr = w.props.get(i);
            if (!pr.alive || pr.x < minX - 40 || pr.x > maxX + 40 || pr.y < minY - 50 || pr.y > maxY + 50) continue;
            boolean flash = pr.hurtFlash > 0;
            float wob = flash ? MathUtils.sin(time * 80) * 1.5f : 0;
            float x = pr.x + wob, y = pr.y, hw = Prop.W / 2, h = Prop.H;
            d.rgb(SoldierPainter.OUTLINE, 1);
            d.rect(x - hw - 1.5f, y - 1, Prop.W + 3, h + 2);
            d.rgb(flash ? 0xffffff : 0xc62828, 1);
            d.rect(x - hw, y, Prop.W, h);
            d.rgb(0x8e0000, 1);
            d.rect(x - hw, y + 6, Prop.W, 2.5f);
            d.rect(x - hw, y + h - 8, Prop.W, 2.5f);
            d.rgb(0xff8a80, 0.7f);
            d.rect(x - hw + 3, y + 2, 3, h - 4);
            // simbolo de inflamavel
            d.rgb(0xffeb3b, 1);
            d.rect(x - 6, y + 11, 12, 10);
            d.rgb(0xd84315, 1);
            d.region(a.tri, x, y + 16, 7, 8, 0);
            if (pr.hp < Prop.HP * 0.5f && (time * 5) % 1 < 0.5f) {
                d.rgb(0xffab00, 0.9f);
                d.circle(x + 5, y + h + 1, 2);
            }
        }
    }

    /** Lampadas do mapa: o objeto (passo normal) e o brilho (passo aditivo). */
    private void drawLamps(Draw d, World w, float minX, float minY, float maxX, float maxY, boolean body) {
        GameMap m = w.map;
        for (int i = 0; i < m.lamps.size; i++) {
            Vector2 l = m.lamps.get(i);
            if (l.x < minX - 200 || l.x > maxX + 200 || l.y < minY - 200 || l.y > maxY + 200) continue;
            int tx = (int) Math.floor(l.x / C.TILE), ty = (int) Math.floor(l.y / C.TILE);
            boolean ceiling = m.solid(tx, ty + 1);
            float bx = l.x, by = ceiling ? l.y + C.TILE - 18 : l.y + 52;
            float flick = 0.85f + 0.15f * MathUtils.sin(time * 9 + i * 3.1f) * MathUtils.sin(time * 4.3f + i);
            if (body) {
                d.rgb(SoldierPainter.OUTLINE, 1);
                if (ceiling) {
                    d.rect(bx - 1, by + 4, 2, l.y + C.TILE - by - 4);
                    d.batch.draw(a.half, bx - 10, by - 1, 20, 9);
                    d.rgb(0x455a64, 1);
                    d.batch.draw(a.half, bx - 8.5f, by, 17, 7.5f);
                } else {
                    d.rect(bx - 2.5f, l.y, 5, by - l.y);
                    d.rect(bx - 7, l.y, 14, 4);
                    d.rgb(0x37474f, 1);
                    d.rect(bx - 1.5f, l.y, 3, by - l.y);
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.rect(bx - 7, by, 14, 9);
                }
                d.color(1, 0.93f, 0.7f, 1);
                d.circle(bx, by - (ceiling ? 1 : -5), 4);
            } else {
                d.color(1, 0.85f, 0.5f, 0.22f * flick);
                d.soft(bx, by, 60);
            }
        }
    }

    private void drawHooks(Draw d, World w) {
        for (int i = 0; i < w.players.size; i++) {
            Player p = w.players.get(i);
            if (!p.alive || p.hookState == 0) continue;
            float sx = p.x + MathUtils.cos(p.aim) * 6, sy = p.shoulderY() + MathUtils.sin(p.aim) * 6;
            d.rgb(SoldierPainter.OUTLINE, 0.9f);
            d.line(sx, sy, p.hookX, p.hookY, 3.5f);
            d.rgb(0xbcaaa4, 1);
            d.line(sx, sy, p.hookX, p.hookY, 1.8f);
            float ang = MathUtils.atan2(p.hookY - sy, p.hookX - sx) * MathUtils.radiansToDegrees;
            d.rgb(SoldierPainter.OUTLINE, 1);
            d.region(a.tri, p.hookX, p.hookY, 11, 11, ang - 90);
            d.rgb(0x90a4ae, 1);
            d.region(a.tri, p.hookX, p.hookY, 8, 8, ang - 90);
        }
    }

    /** Nuvens de fumaca: varias bolhas cinza que crescem e giram devagar. */
    private void drawSmoke(Draw d, World w, float minX, float minY, float maxX, float maxY) {
        for (int i = 0; i < w.projectiles.size; i++) {
            Projectile pr = w.projectiles.get(i);
            if (pr.kind != Projectile.Kind.SMOKE || !pr.armed) continue;
            float r = World.smokeRadius(pr);
            if (pr.x + r < minX || pr.x - r > maxX || pr.y + r < minY || pr.y - r > maxY) continue;
            float fade = Math.min(1, pr.fuse / 1.5f);
            for (int k = 0; k < 9; k++) {
                float ang = k * 0.698f + time * 0.15f * (k % 2 == 0 ? 1 : -1);
                float rr = r * (0.35f + 0.25f * ((k * 37) % 5) / 4f);
                float bx = pr.x + MathUtils.cos(ang) * rr * 0.8f, by = pr.y + 10 + MathUtils.sin(ang) * rr * 0.55f;
                float g = 0.78f + 0.08f * ((k * 13) % 3);
                d.color(g, g, g * 1.02f, 0.62f * fade);
                d.soft(bx, by, r * 0.75f);
            }
            d.color(0.85f, 0.85f, 0.86f, 0.75f * fade);
            d.soft(pr.x, pr.y + 10, r * 0.9f);
        }
    }

    private void drawBolts(Draw d) {
        for (int i = 0; i < bolts.size; i++) {
            Bolt b = bolts.get(i);
            float k = b.life / 0.22f;
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 0) d.color(0.45f, 0.75f, 1f, 0.55f * k);
                else d.color(1, 1, 1, 0.95f * k);
                float th = pass == 0 ? 7 * k + 2 : 2.2f;
                for (int p = 0; p + 3 < b.pts.length; p += 2) {
                    float x1 = b.pts[p], y1 = b.pts[p + 1], x2 = b.pts[p + 2], y2 = b.pts[p + 3];
                    float px = x1, py = y1;
                    int seg = 6;
                    for (int s2 = 1; s2 <= seg; s2++) {
                        float t = s2 / (float) seg;
                        float nx = x1 + (x2 - x1) * t, ny = y1 + (y2 - y1) * t;
                        if (s2 < seg) {
                            nx += MathUtils.random(-12, 12);
                            ny += MathUtils.random(-12, 12);
                        }
                        d.line(px, py, nx, ny, th);
                        px = nx;
                        py = ny;
                    }
                }
            }
        }
    }

    private void drawBoss(Draw d, Boss b) {
        boolean flash = b.hurtFlash > 0;
        int f = b.facing;
        if (b.type == Boss.HELI) {
            float x = b.x, y = b.y + 10, tilt = MathUtils.clamp(-b.vx * 0.05f, -14, 14);
            int body = flash ? 0xffffff : 0x4e5d3a, dark = 0x2f3a22;
            // cauda
            d.rgb(SoldierPainter.OUTLINE, 1);
            d.rect(x, y + 20, 80, 12, 0, 6, 180 * (f > 0 ? 1 : 0) + tilt);
            d.rgb(body, 1);
            d.rect(x, y + 21.5f, 78, 9, 0, 4.5f, 180 * (f > 0 ? 1 : 0) + tilt);
            float tx = x - f * 78, ty = y + 26;
            d.rgb(0x9e9e9e, 0.6f);
            d.ellipse(tx, ty + 6, 4, 18 * Math.abs(MathUtils.sin(time * 40)) + 4, 0);
            // corpo
            d.rgb(SoldierPainter.OUTLINE, 1);
            d.ellipse(x, y + 22, 52, 26, tilt);
            d.rgb(body, 1);
            d.ellipse(x, y + 22, 49.5f, 23.5f, tilt);
            d.rgb(dark, 1);
            d.rect(x - 40, y + 4, 80, 6, 40, 0, tilt);
            // cabine
            d.rgb(0x80deea, 0.9f);
            d.ellipse(x + f * 26, y + 28, 18, 12, tilt);
            d.rgb(0xffffff, 0.5f);
            d.ellipse(x + f * 30, y + 33, 7, 3, tilt);
            // esquis
            d.rgb(SoldierPainter.OUTLINE, 1);
            d.rect(x - 40, y - 8, 80, 4, 40, 0, tilt);
            d.rect(x - 24, y - 6, 3, 12);
            d.rect(x + 22, y - 6, 3, 12);
            // metralhadora no nariz
            float gx = x + f * 46, gy = y + 12;
            d.rgb(SoldierPainter.OUTLINE, 1);
            d.rect(gx, gy - 3, 26, 6, 0, 3, b.aim * MathUtils.radiansToDegrees);
            d.rgb(0x424242, 1);
            d.rect(gx, gy - 2, 25, 4, 0, 2, b.aim * MathUtils.radiansToDegrees);
            // helice principal (borrada)
            float blur = Math.abs(MathUtils.sin(time * 30));
            d.rgb(SoldierPainter.OUTLINE, 1);
            d.rect(x - 2, y + 44, 4, 10);
            d.rgb(0x212121, 0.55f);
            d.rect(x - 90 * blur, y + 54, 180 * blur, 4);
            d.rgb(0xbdbdbd, 0.18f);
            d.ellipse(x, y + 56, 92, 5, 0);
        } else {
            float x = b.x, y = b.y;
            int body = flash ? 0xffffff : 0x546e7a, dark = 0x263238, accent = 0xff6f00;
            float walk = MathUtils.sin(b.anim * 2.2f) * 18;
            // pernas
            for (int k = 0; k < 2; k++) {
                float ang = k == 0 ? walk : -walk;
                float hx = x + (k == 0 ? -14 : 14), hy = y + 52;
                d.rgb(SoldierPainter.OUTLINE, 1);
                d.rect(hx - 9, hy - 52, 18, 54, 9, 52, ang);
                d.rgb(k == 0 ? dark : body, 1);
                d.rect(hx - 7.5f, hy - 50.5f, 15, 51, 7.5f, 50.5f, ang);
                float fx = hx + MathUtils.sinDeg(ang) * 50, fy = hy - MathUtils.cosDeg(ang) * 50;
                d.rgb(SoldierPainter.OUTLINE, 1);
                d.rect(fx - 15, fy - 4, 30, 9);
                d.rgb(dark, 1);
                d.rect(fx - 13.5f, fy - 2.5f, 27, 6);
            }
            // tronco
            d.rgb(SoldierPainter.OUTLINE, 1);
            d.rect(x - 36, y + 48, 72, 52);
            d.rgb(body, 1);
            d.rect(x - 34, y + 50, 68, 48);
            d.rgb(0xffc107, 1);
            for (int k = 0; k < 4; k++) d.rect(x - 34 + k * 18, y + 50, 9, 5);
            d.rgb(dark, 1);
            d.rect(x - 34, y + 55, 68, 4);
            // nucleo (brilha ao carregar o laser)
            float ch = MathUtils.clamp(b.laserCharge / 0.9f, 0, 1);
            d.rgb(0x1a237e, 1);
            d.circle(x, y + 76, 11);
            d.color(1, 0.25f + 0.5f * (1 - ch), 0.2f, 1);
            d.circle(x, y + 76, 6 + ch * 4);
            // cabeca
            d.rgb(SoldierPainter.OUTLINE, 1);
            d.rect(x - 20, y + 98, 40, 22);
            d.rgb(body, 1);
            d.rect(x - 18.5f, y + 99.5f, 37, 19);
            d.rgb(0xff1744, 1);
            d.rect(x - 14 + f * 4, y + 106, 24, 6);
            // braco canhao
            float deg = b.aim * MathUtils.radiansToDegrees;
            float sx = x + f * 30, sy = y + 88;
            d.rgb(SoldierPainter.OUTLINE, 1);
            d.rect(sx, sy - 9, 50, 18, 0, 9, deg);
            d.rgb(dark, 1);
            d.rect(sx, sy - 7.5f, 48, 15, 0, 7.5f, deg);
            d.rgb(accent, 1);
            d.rect(sx + MathUtils.cosDeg(deg) * 38, sy + MathUtils.sinDeg(deg) * 38 - 4, 8, 8, 0, 4, deg);
            // aviso do laser
            if (b.laserCharge > 0) {
                float lx = x + f * 16, ly = y + 100;
                float ex = lx + MathUtils.cos(b.laserAim) * 1300, ey = ly + MathUtils.sin(b.laserAim) * 1300;
                float tt = world.rayTile(lx, ly, ex, ey);
                if (tt <= 1) {
                    ex = lx + (ex - lx) * tt;
                    ey = ly + (ey - ly) * tt;
                }
                d.color(1, 0.1f, 0.1f, 0.25f + 0.5f * ch * ((time * 12) % 1 < 0.5f ? 1 : 0.5f));
                d.line(lx, ly, ex, ey, 1.5f + ch * 3);
            }
        }
    }

    private void drawBossBars(Draw d, World w) {
        for (int i = 0; i < w.bosses.size; i++) {
            Boss b = w.bosses.get(i);
            float bw = 110, y = b.y + b.height() + 24;
            d.bar(b.x - bw / 2, y, bw, 7, MathUtils.clamp(b.hp / Math.max(1, b.maxHp), 0, 1), 0xff5252, 0x3a1010);
        }
    }

    private void drawEmotes(Draw d, World w, float minX, float minY, float maxX, float maxY) {
        BitmapFont f = a.small;
        for (int i = 0; i < w.players.size; i++) {
            Player p = w.players.get(i);
            if (!p.alive || p.emoteTimer <= 0 || p.emote < 0) continue;
            if (p.x < minX - 80 || p.x > maxX + 80 || p.y < minY - 100 || p.y > maxY + 120) continue;
            float k = MathUtils.clamp(p.emoteTimer * 3, 0, 1) * MathUtils.clamp((2.4f - p.emoteTimer) * 8, 0, 1);
            float bx = p.x, by = p.y + p.height() + 46 + (1 - k) * 8;
            if (p.emote < C.EMOTE_PHRASES) {
                String text = I18n.t("phrase_" + p.emote);
                layout.setText(f, text);
                float tw = layout.width + 18, th = 26;
                d.rgb(SoldierPainter.OUTLINE, 0.9f * k);
                d.rect(bx - tw / 2 - 1.5f, by - th / 2 - 1.5f, tw + 3, th + 3);
                d.rgb(0xffffff, 0.95f * k);
                d.rect(bx - tw / 2, by - th / 2, tw, th);
                d.region(a.tri, bx, by - th / 2 - 4, 10, 8, 180);
                f.setColor(0.08f, 0.08f, 0.1f, k);
                f.draw(d.batch, text, bx, by + layout.height / 2, 0, Align.center, false);
            } else if (p.emote < C.EMOTE_DANCE_FIRST) {
                int r = p.emote - C.EMOTE_PHRASES;
                float pop = 1 + 0.2f * MathUtils.sin(time * 10);
                switch (r) {
                    case 0: // joinha
                        d.rgb(SoldierPainter.OUTLINE, k);
                        d.circle(bx, by, 15 * pop);
                        d.rgb(0xffca28, k);
                        d.circle(bx, by, 13 * pop);
                        d.rgb(0xf57f17, k);
                        d.rect(bx - 6, by - 6, 11, 10);
                        d.rect(bx - 1, by + 2, 5, 9);
                        break;
                    case 1: // risada
                        d.rgb(SoldierPainter.OUTLINE, k);
                        d.circle(bx, by, 15 * pop);
                        d.rgb(0xffeb3b, k);
                        d.circle(bx, by, 13 * pop);
                        d.rgb(0x3e2723, k);
                        d.rect(bx - 7, by + 3, 4, 2, 2, 1, 20);
                        d.rect(bx + 3, by + 3, 4, 2, 2, 1, -20);
                        d.batch.draw(a.half, bx - 7, by - 1, 7, 0, 14, 7, 1, 1, 180);
                        break;
                    case 2: // bravo
                        d.rgb(SoldierPainter.OUTLINE, k);
                        d.circle(bx, by, 15 * pop);
                        d.rgb(0xff5252, k);
                        d.circle(bx, by, 13 * pop);
                        d.rgb(0x3e2723, k);
                        d.rect(bx - 8, by + 4, 6, 2, 3, 1, -25);
                        d.rect(bx + 2, by + 4, 6, 2, 3, 1, 25);
                        d.rect(bx - 5, by - 6, 10, 2);
                        break;
                    default: // coracao
                        d.rgb(0xff4081, k);
                        d.circle(bx - 5 * pop, by + 3, 7 * pop);
                        d.circle(bx + 5 * pop, by + 3, 7 * pop);
                        d.region(a.tri, bx, by - 5, 22 * pop, 14 * pop, 180);
                        break;
                }
            } else if ((int) (time * 3 + p.id) % 2 == 0) { // notas musicais da danca
                float nx = bx + MathUtils.sin(time * 2 + p.id) * 14, ny = by - 6 + ((time * 30 + p.id * 9) % 20);
                d.rgb(0xffffff, 0.85f * k);
                d.circle(nx, ny, 3.5f);
                d.rect(nx + 2.5f, ny, 1.6f, 12);
                d.rect(nx + 2.5f, ny + 10, 6, 2);
            }
        }
        f.setColor(Color.WHITE);
    }

    private void drawZones(Draw d, World w) {
        if (w.mode instanceof KingHillMode) {
            KingHillMode km = (KingHillMode) w.mode;
            Vector2 h = km.hill();
            int col = km.owner == C.TEAM_RED ? SoldierPainter.RED : (km.owner == C.TEAM_BLUE ? SoldierPainter.BLUE : (km.owner == -1 ? 0xffeb3b : 0xffffff));
            float pulse = 0.5f + 0.5f * MathUtils.sin(time * 3);
            d.rgb(col, 0.1f + 0.06f * pulse);
            d.circle(h.x, h.y + 30, KingHillMode.RADIUS);
            d.rgb(col, 0.6f);
            d.ring(h.x, h.y + 30, KingHillMode.RADIUS);
            // coroa
            float cy = h.y + 30 + KingHillMode.RADIUS + 16 + pulse * 4;
            d.rgb(SoldierPainter.OUTLINE, 0.9f);
            d.rect(h.x - 15, cy - 9, 30, 13);
            d.rgb(0xffd740, 1);
            d.rect(h.x - 13.5f, cy - 7.5f, 27, 10);
            for (int k = -1; k <= 1; k++) d.region(a.tri, h.x + k * 10, cy + 6, 9, 10, 0);
        }
        if (w.mode instanceof BattleRoyaleMode) {
            BattleRoyaleMode br = (BattleRoyaleMode) w.mode;
            // escurece fora da zona (anel bem grosso)
            float thick = 2600;
            int seg = 72;
            float rr = br.zoneR + thick / 2;
            d.color(0.85f, 0.1f, 0.35f, 0.22f);
            for (int i = 0; i < seg; i++) {
                float a1 = i / (float) seg * MathUtils.PI2, a2 = (i + 1) / (float) seg * MathUtils.PI2;
                d.line(br.zoneX + MathUtils.cos(a1) * rr, br.zoneY + MathUtils.sin(a1) * rr,
                        br.zoneX + MathUtils.cos(a2) * rr, br.zoneY + MathUtils.sin(a2) * rr, thick + 4);
            }
            d.color(1f, 0.3f, 0.5f, 0.85f);
            for (int i = 0; i < seg; i++) {
                float a1 = i / (float) seg * MathUtils.PI2, a2 = (i + 1) / (float) seg * MathUtils.PI2;
                d.line(br.zoneX + MathUtils.cos(a1) * br.zoneR, br.zoneY + MathUtils.sin(a1) * br.zoneR,
                        br.zoneX + MathUtils.cos(a2) * br.zoneR, br.zoneY + MathUtils.sin(a2) * br.zoneR, 4);
            }
            if (br.nextR > 0) {
                d.color(1, 1, 1, 0.5f);
                for (int i = 0; i < seg; i += 2) {
                    float a1 = i / (float) seg * MathUtils.PI2, a2 = (i + 1) / (float) seg * MathUtils.PI2;
                    d.line(br.nextX + MathUtils.cos(a1) * br.nextR, br.nextY + MathUtils.sin(a1) * br.nextR,
                            br.nextX + MathUtils.cos(a2) * br.nextR, br.nextY + MathUtils.sin(a2) * br.nextR, 2.5f);
                }
            }
        }
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

    private int pickupGlow(Pickup pk) {
        switch (pk.type) {
            case HEALTH: return 0xff8a80;
            case ARMOR: return 0x82b1ff;
            case DAMAGE: return 0xff5252;
            case JETPACK: return 0x40c4ff;
            case SPEED: return 0xffff00;
            default: return 0xfff59d;
        }
    }

    private void drawPickups(Draw d, World w, float minX, float minY, float maxX, float maxY) {
        // 1) todos os brilhos de uma vez (troca de modo de mistura so 2x por quadro)
        d.additive(true);
        for (int i = 0; i < w.pickups.size; i++) {
            Pickup pk = w.pickups.get(i);
            if (!pk.active) continue;
            if (pk.x < minX - 40 || pk.x > maxX + 40 || pk.y < minY - 40 || pk.y > maxY + 40) continue;
            if (pk.dropped && pk.life < 4 && (time * 6) % 1 < 0.4f) continue;
            float cy = pk.y + 16 + MathUtils.sin(pk.bob * 3) * 3;
            d.rgb(pickupGlow(pk), 0.35f + 0.1f * MathUtils.sin(pk.bob * 4));
            d.soft(pk.x, cy, 26);
        }
        d.additive(false);
        // 2) os itens
        for (int i58_ = 0; i58_ < w.pickups.size; i58_++) {
            Pickup pk = w.pickups.get(i58_);
            if (!pk.active) continue;
            if (pk.x < minX - 40 || pk.x > maxX + 40 || pk.y < minY - 40 || pk.y > maxY + 40) continue;
            float bob = MathUtils.sin(pk.bob * 3) * 3;
            float cx = pk.x, cy = pk.y + 16 + bob;
            if (pk.dropped && pk.life < 4 && (time * 6) % 1 < 0.4f) continue;
            // sombra no chao e anel
            d.color(0, 0, 0, 0.2f);
            d.ellipse(pk.x, pk.y + 2, 13 - bob, 3, 0);
            ShadeKit.load();
            switch (pk.type) {
                case WEAPON:
                    if (pk.weapon != null) painter.drawGun(d, pk.weapon, cx, cy, -8 + MathUtils.sin(pk.bob * 2) * 6, 1.15f, 1);
                    break;
                case HEALTH: { // maleta de primeiros socorros
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.batch.draw(ShadeKit.box, cx - 12.5f, cy - 10, 25, 20);
                    d.rgb(0xf5f5f5, 1);
                    d.batch.draw(ShadeKit.box, cx - 11, cy - 8.5f, 22, 17);
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.rect(cx - 5, cy + 8, 10, 3.5f);
                    d.rgb(0xe53935, 1);
                    d.rect(cx - 2.2f, cy - 5.5f, 4.4f, 11);
                    d.rect(cx - 5.5f, cy - 2.2f, 11, 4.4f);
                    d.color(1, 1, 1, 0.5f);
                    d.batch.draw(ShadeKit.gloss, cx - 9, cy + 3, 9, 4);
                    break;
                }
                case GRENADES:
                    for (int i = 0; i < 2; i++) {
                        float gx = cx - 6 + i * 12, gy = cy - 2 + i * 1.5f;
                        d.rgb(SoldierPainter.OUTLINE, 1);
                        d.batch.draw(ShadeKit.ball, gx - 7.5f, gy - 7.5f, 15, 15);
                        d.rgb(0x556b2f, 1);
                        d.batch.draw(ShadeKit.ball, gx - 6, gy - 6, 12, 12);
                        d.rgb(0x9e9e9e, 1);
                        d.rect(gx - 2, gy + 4.5f, 4, 3.5f);
                        d.rgb(0xffd54f, 1);
                        d.ring(gx + 3, gy + 7, 2);
                    }
                    break;
                case ARMOR: { // colete
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.batch.draw(ShadeKit.box, cx - 11.5f, cy - 11, 23, 21);
                    d.rgb(0x1e88e5, 1);
                    d.batch.draw(ShadeKit.box, cx - 10, cy - 9.5f, 20, 18);
                    d.rgb(0x0d47a1, 1);
                    d.rect(cx - 2, cy - 9, 4, 17);
                    d.rect(cx - 8, cy - 6, 5, 5);
                    d.rect(cx + 3, cy - 6, 5, 5);
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.batch.draw(a.half, cx - 6, cy + 6, 12, 5);
                    break;
                }
                default: { // power-ups: esfera brilhante com icone
                    int col = pickupGlow(pk);
                    float spin = pk.bob * 120;
                    d.rgb(col, 0.8f);
                    d.region(a.ring, cx, cy, 30, 30 * Math.abs(MathUtils.cos(pk.bob * 2)), spin * 0.2f);
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.batch.draw(ShadeKit.ball, cx - 12, cy - 12, 24, 24);
                    d.rgb(col, 1);
                    d.batch.draw(ShadeKit.ball, cx - 10.5f, cy - 10.5f, 21, 21);
                    d.color(1, 1, 1, 0.6f);
                    d.batch.draw(ShadeKit.gloss, cx - 7, cy + 2, 8, 5);
                    d.rgb(0x15151c, 0.85f);
                    if (pk.type == Pickup.Type.DAMAGE) d.region(a.star, cx, cy - 1, 13, 13, spin);
                    else if (pk.type == Pickup.Type.JETPACK) {
                        d.rect(cx - 3.5f, cy - 4, 7, 9);
                        d.region(a.tri, cx, cy - 7, 6, 5, 180);
                    } else d.region(a.arrow, cx, cy - 1, 14, 11, 0);
                    break;
                }
            }
        }
    }

    private void drawProjectilesSolid(Draw d, World w, float minX, float minY, float maxX, float maxY) {
        for (int i59_ = 0; i59_ < w.projectiles.size; i59_++) {
            Projectile pr = w.projectiles.get(i59_);
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
                case ARROW:
                case HARPOON: {
                    boolean harp = pr.kind == Projectile.Kind.HARPOON;
                    float sp = (float) Math.sqrt(pr.vx * pr.vx + pr.vy * pr.vy) + 0.0001f;
                    float dx = pr.vx / sp, dy = pr.vy / sp;
                    float len = harp ? 30 : 22;
                    float tx = pr.x - dx * len, ty = pr.y - dy * len;
                    if (harp && !pr.stuck) {
                        Player o = w.player(pr.owner);
                        if (o != null && o.alive) {
                            d.rgb(0x5d4037, 0.8f);
                            d.line(o.x, o.shoulderY(), tx, ty, 1.5f);
                        }
                    }
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.line(tx, ty, pr.x, pr.y, harp ? 5 : 3.6f);
                    d.rgb(harp ? 0x90a4ae : 0xa1887f, 1);
                    d.line(tx, ty, pr.x, pr.y, harp ? 3 : 2f);
                    float deg = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.region(a.tri, pr.x, pr.y, harp ? 11 : 8, harp ? 12 : 9, deg - 90);
                    d.rgb(harp ? 0xcfd8dc : 0x9e9e9e, 1);
                    d.region(a.tri, pr.x, pr.y, harp ? 8 : 5.5f, harp ? 9 : 6.5f, deg - 90);
                    if (!harp) {
                        d.rgb(0xef5350, 1);
                        d.rect(tx, ty - 3, 6, 6, 0, 3, deg + 25);
                        d.rect(tx, ty - 3, 6, 6, 0, 3, deg - 25);
                    }
                    break;
                }
                case SMOKE:
                case FLASH: {
                    if (pr.kind == Projectile.Kind.SMOKE && pr.armed) break;
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.rect(pr.x - 4.5f, pr.y - 1, 9, 13, 4.5f, 6.5f, pr.rot);
                    d.rgb(pr.kind == Projectile.Kind.SMOKE ? 0x78909c : 0xeceff1, 1);
                    d.rect(pr.x - 3.2f, pr.y, 6.4f, 11, 3.2f, 5.5f, pr.rot);
                    d.rgb(pr.kind == Projectile.Kind.SMOKE ? 0x37474f : 0xffd54f, 1);
                    d.rect(pr.x - 3.2f, pr.y + 4, 6.4f, 2.5f, 3.2f, 1.5f, pr.rot);
                    break;
                }
                case STICKY: {
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.circle(pr.x, pr.y + 4, 6.5f);
                    d.rgb(0x76ff03, 1);
                    d.circle(pr.x, pr.y + 4, 5);
                    d.rgb(0x33691e, 1);
                    d.circle(pr.x + 1.5f, pr.y + 5, 1.8f);
                    if ((time * (pr.fuse < 0.8f ? 12 : 5)) % 1 < 0.5f) {
                        d.rgb(0xff1744, 1);
                        d.circle(pr.x - 1.5f, pr.y + 7, 1.6f);
                    }
                    break;
                }
                case BOOMERANG: {
                    d.rgb(SoldierPainter.OUTLINE, 1);
                    d.rect(pr.x - 1, pr.y - 3.5f, 16, 7, 1, 3.5f, pr.rot);
                    d.rect(pr.x - 1, pr.y - 3.5f, 16, 7, 1, 3.5f, pr.rot + 100);
                    d.rgb(0xffb300, 1);
                    d.rect(pr.x, pr.y - 2.5f, 14, 5, 0, 2.5f, pr.rot);
                    d.rgb(0xff8f00, 1);
                    d.rect(pr.x, pr.y - 2.5f, 14, 5, 0, 2.5f, pr.rot + 100);
                    break;
                }
                case VORTEX: {
                    if (pr.stuck) {
                        float r = 20 + 3 * MathUtils.sin(time * 9);
                        d.rgb(0x000000, 0.9f);
                        d.circle(pr.x, pr.y, r);
                        d.rgb(0x7c4dff, 0.8f);
                        d.ring(pr.x, pr.y, r + 3);
                    } else {
                        d.rgb(SoldierPainter.OUTLINE, 1);
                        d.circle(pr.x, pr.y, 8);
                        d.rgb(0x311b92, 1);
                        d.circle(pr.x, pr.y, 6.5f);
                    }
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
        for (int i60_ = 0; i60_ < w.projectiles.size; i60_++) {
            Projectile pr = w.projectiles.get(i60_);
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
            } else if (pr.kind == Projectile.Kind.FROST) {
                float t = 1 - MathUtils.clamp(pr.life / Math.max(0.01f, pr.weapon != null ? pr.weapon.lifetime() : 0.4f), 0, 1);
                float size = 6 + t * 18;
                d.color(0.55f, 0.85f, 1f, 0.6f * (1 - t * 0.6f));
                d.soft(pr.x, pr.y, size);
                d.color(1f, 1f, 1f, 0.45f * (1 - t));
                d.soft(pr.x, pr.y, size * 0.35f);
            } else if (pr.kind == Projectile.Kind.VORTEX) {
                float r = pr.stuck ? 70 + 10 * MathUtils.sin(time * 6) : 22;
                d.color(0.5f, 0.3f, 1f, pr.stuck ? 0.45f : 0.6f);
                d.soft(pr.x, pr.y, r);
                if (pr.stuck) {
                    for (int k = 0; k < 3; k++) {
                        float ang = pr.rot * 0.05f + k * 2.1f;
                        d.color(0.75f, 0.55f, 1f, 0.5f);
                        d.region(a.ring, pr.x, pr.y, 60 + k * 18, 22 + k * 6, ang * MathUtils.radiansToDegrees);
                    }
                }
            } else if (pr.kind == Projectile.Kind.BOOMERANG) {
                d.color(1f, 0.9f, 0.5f, 0.2f);
                d.soft(pr.x, pr.y, 16);
            }
        }
    }

    /** Pique-esconde: o jogador aparece como objeto do cenario. */
    private void drawDisguise(Draw d, Player p) {
        ShadeKit.load();
        float x = p.x, y = p.y;
        float wob = Math.abs(p.vx) > 20 ? MathUtils.sin(time * 18) * 3 : 0;
        int O = SoldierPainter.OUTLINE;
        switch (p.disguise) {
            case 0: { // caixa
                d.rgb(O, 1);
                d.rect(x - 16.5f, y - 1, 33, 33, 16.5f, 0, wob);
                d.rgb(0xb07a3c, 1);
                d.batch.draw(ShadeKit.box, x - 15, y, 15, 0, 30, 30, 1, 1, wob);
                d.rgb(0x6d4422, 1);
                d.rect(x - 15, y + 13.5f, 30, 3, 15, -13.5f, wob + 45);
                d.rect(x - 15, y + 13.5f, 30, 3, 15, -13.5f, wob - 45);
                break;
            }
            case 1: { // barril
                d.rgb(O, 1);
                d.rect(x - 13.5f, y - 1, 27, 36, 13.5f, 0, wob);
                d.rgb(0x2e7d32, 1);
                d.batch.draw(ShadeKit.box, x - 12, y, 12, 0, 24, 34, 1, 1, wob);
                d.rgb(0x1b5e20, 1);
                d.rect(x - 12, y + 7, 24, 2.5f, 12, -7, wob);
                d.rect(x - 12, y + 25, 24, 2.5f, 12, -25, wob);
                break;
            }
            case 2: { // arbusto
                int[] g = {0x2e7d32, 0x388e3c, 0x43a047};
                float[][] b = {{-9, 9, 11}, {9, 10, 11}, {0, 19, 13}, {-3, 6, 10}};
                for (int k = 0; k < b.length; k++) {
                    d.rgb(O, 1);
                    d.batch.draw(ShadeKit.ball, x + b[k][0] - b[k][2] - 1.5f + wob * 0.3f, y + b[k][1] - b[k][2] - 1.5f, (b[k][2] + 1.5f) * 2, (b[k][2] + 1.5f) * 2);
                }
                for (int k = 0; k < b.length; k++) {
                    d.rgb(g[k % 3], 1);
                    d.batch.draw(ShadeKit.ball, x + b[k][0] - b[k][2] + wob * 0.3f, y + b[k][1] - b[k][2], b[k][2] * 2, b[k][2] * 2);
                }
                break;
            }
            default: { // vaso com planta
                d.rgb(O, 1);
                d.rect(x - 11.5f, y - 1, 23, 18);
                d.rgb(0xd84315, 1);
                d.batch.draw(ShadeKit.box, x - 10, y, 20, 16);
                for (int k = -1; k <= 1; k++) {
                    d.rgb(O, 1);
                    d.rect(x + k * 4, y + 14, 4, 22, 2, 0, k * 22 + wob);
                    d.rgb(0x66bb6a, 1);
                    d.rect(x + k * 4 + 0.8f, y + 15, 2.6f, 20, 1.3f, 0, k * 22 + wob);
                }
                break;
            }
        }
        d.color(1, 1, 1, 1);
    }

    private void drawPlayer(Draw d, Player p, World w) {
        if (p.disguise >= 0) {
            drawDisguise(d, p);
            return;
        }
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
        pose.vx = p.vx;
        pose.action = 0;
        pose.zombie = p.zombie;
        if (p.emoteTimer > 0 && p.emote >= C.EMOTE_DANCE_FIRST) pose.action = 2 + (p.emote - C.EMOTE_DANCE_FIRST);
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
        if (p.rollTimer > 0) { // rolamento: gira o boneco inteiro em volta do centro
            float spin = (1 - p.rollTimer / 0.34f) * 360 * -p.rollDir;
            float cx = p.x, cy = p.y + 14;
            com.badlogic.gdx.math.Matrix4 m = d.batch.getTransformMatrix();
            m.idt().translate(cx, cy, 0).rotate(0, 0, 1, spin).translate(-cx, -cy, 0);
            d.batch.setTransformMatrix(m);
            painter.draw(d, pose);
            d.batch.setTransformMatrix(m.idt());
            if (!fx.skip()) {
                Particles.P dust = fx.spawn(Particles.SOFT, p.x - p.rollDir * 10, p.y + 3, -p.rollDir * 40, 20, 0.5f, 5, 14, 0xbcaaa4, 0.4f);
                dust.drag = 3;
            }
        } else painter.draw(d, pose);
        if (p.frozen > 0) { // bloco de gelo
            float h = p.height() + 10;
            d.color(0.75f, 0.92f, 1f, 0.5f);
            d.rect(p.x - 16, p.y - 2, 32, h);
            d.color(1, 1, 1, 0.75f);
            d.rect(p.x - 13, p.y + h - 10, 3, 7);
            d.rect(p.x - 13, p.y + 4, 2, h * 0.5f);
            d.rect(p.x + 8, p.y + h * 0.4f, 2, h * 0.4f, 1, 0, 20);
            d.color(0.5f, 0.8f, 1f, 0.9f);
            d.ring(p.x, p.y + h / 2, 3);
        } else if (p.chill > 0.05f) {
            d.additive(true);
            d.color(0.4f, 0.75f, 1f, Math.min(0.4f, p.chill * 0.5f));
            d.soft(p.x, p.y + 22, 30);
            d.additive(false);
        }
        if (p.stun > 0 && p.frozen <= 0) { // estrelinhas de atordoado
            for (int k = 0; k < 3; k++) {
                float ang = time * 6 + k * 2.09f;
                d.rgb(0xfff176, 1);
                d.region(a.star, p.x + MathUtils.cos(ang) * 12, p.y + p.height() + 6 + MathUtils.sin(ang) * 3, 8, 8, time * 200);
            }
        }
    }

    /** Calcula a cor do ambiente: noite ao ar livre e ambientes fechados ficam escuros. */
    private void computeAmbient(float camY) {
        GameMap m = world.map;
        float boundary = m.theme2 != null ? (m.lowerTop + 1) * C.TILE : -1e9f;
        float k = m.theme2 != null ? MathUtils.clamp((boundary - camY) / 240f + 0.5f, 0, 1) : 0; // 1 = regiao de baixo
        float r1 = 1, g1 = 1, b1 = 1, r2 = 1, g2 = 1, b2 = 1;
        float[] c = new float[3];
        themeAmbient(m.theme, c);
        r1 = c[0];
        g1 = c[1];
        b1 = c[2];
        if (m.theme2 != null) {
            themeAmbient(m.theme2, c);
            r2 = c[0];
            g2 = c[1];
            b2 = c[2];
        }
        ambR = r1 + (r2 - r1) * k;
        ambG = g1 + (g2 - g1) * k;
        ambB = b1 + (b2 - b1) * k;
    }

    private void themeAmbient(Theme t, float[] out) {
        float n = night;
        if (t.dark) {
            boolean outdoor = t.bg == Theme.Bg.CITY_NIGHT;
            float base = outdoor ? 0.45f : 0.42f;
            out[0] = base + (outdoor ? -0.05f : 0.05f);
            out[1] = base;
            out[2] = base + (outdoor ? 0.15f : 0);
            if (outdoor) {
                out[0] -= 0.08f * n;
                out[1] -= 0.08f * n;
            }
        } else {
            float k = n * 0.72f;
            out[0] = 1 - k * 0.78f;
            out[1] = 1 - k * 0.72f;
            out[2] = 1 - k * 0.5f;
        }
    }

    /** Escuridao atual (0 = dia claro). */
    public float darkness() {
        return 1 - Math.min(ambR, Math.min(ambG, ambB));
    }

    /**
     * Passo de luz: chamar depois de render(), com o batch fechado. Nao faz nada de dia.
     */
    public void renderLights(com.badlogic.gdx.graphics.g2d.SpriteBatch batch, OrthographicCamera cam, Player local) {
        if (world == null) return;
        computeAmbient(cam.position.y);
        if (darkness() < 0.03f) {
            lighting.clear();
            return;
        }
        World w = world;
        float vw = cam.viewportWidth * cam.zoom, vh = cam.viewportHeight * cam.zoom;
        float minX = cam.position.x - vw / 2 - 300, minY = cam.position.y - vh / 2 - 300, maxX = minX + vw + 600, maxY = minY + vh + 600;
        if (!settings.dynamicLights || settings.quality == 0) {
            // simples: so escurece, com uma luz em volta do jogador
            if (local != null && local.alive) lighting.light(local.x, local.centerY(), 260, 0xffffff, 0.55f);
            lighting.render(batch, a.soft, cam, ambR, ambG, ambB);
            return;
        }
        GameMap m = w.map;
        for (int i = 0; i < m.lamps.size; i++) {
            Vector2 l = m.lamps.get(i);
            if (l.x < minX || l.x > maxX || l.y < minY || l.y > maxY) continue;
            int tx = (int) Math.floor(l.x / C.TILE), ty = (int) Math.floor(l.y / C.TILE);
            boolean ceiling = m.solid(tx, ty + 1);
            float by = ceiling ? l.y + C.TILE - 30 : l.y + 52;
            float flick = 0.9f + 0.1f * MathUtils.sin(time * 9 + i * 3.1f) * MathUtils.sin(time * 4.3f + i);
            lighting.light(l.x, by, 300, 0xffd59a, 0.95f * flick);
        }
        for (int i = 0; i < lavaCount; i++) lighting.light(lavaSeen.get(i)[0], lavaSeen.get(i)[1], 90, 0xff6a00, 0.7f + 0.1f * MathUtils.sin(time * 3 + i));
        for (int i = 0; i < w.players.size; i++) {
            Player p = w.players.get(i);
            if (!p.alive || p.x < minX || p.x > maxX || p.y < minY || p.y > maxY) continue;
            lighting.light(p.x, p.centerY(), p == local ? 230 : 120, 0xffffff, p == local ? 0.5f : 0.3f);
            if (p.jetting) lighting.light(p.x - p.facing * 10, p.y + 4, 110, p.jetBoost > 0 ? 0x40c4ff : 0xffa040, 0.85f);
            if (p.zombie) lighting.light(p.x, p.y + p.height() - 6, 50, 0x76ff03, 0.5f);
        }
        int bullets = 0;
        for (int i = 0; i < w.projectiles.size; i++) {
            Projectile pr = w.projectiles.get(i);
            if (pr.x < minX || pr.x > maxX || pr.y < minY || pr.y > maxY) continue;
            switch (pr.kind) {
                case ROCKET: lighting.light(pr.x, pr.y, 130, 0xffa040, 0.9f); break;
                case FLAME: if ((i & 1) == 0) lighting.light(pr.x, pr.y, 80, 0xff7a30, 0.55f); break;
                case FROST: if ((i & 1) == 0) lighting.light(pr.x, pr.y, 60, 0x80d8ff, 0.4f); break;
                case VORTEX: lighting.light(pr.x, pr.y, pr.stuck ? 220 : 80, 0xb388ff, 0.7f); break;
                case BULLET: if (bullets++ < 40) lighting.light(pr.x, pr.y, 46, 0xffe0a0, 0.45f); break;
                case DISC: lighting.light(pr.x, pr.y, 50, 0xffffff, 0.35f); break;
                default: break;
            }
        }
        for (int i = 0; i < w.pickups.size; i++) {
            Pickup pk = w.pickups.get(i);
            if (!pk.active || pk.x < minX || pk.x > maxX || pk.y < minY || pk.y > maxY) continue;
            lighting.light(pk.x, pk.y + 16, 60, pickupGlow(pk), 0.55f);
        }
        for (int i = 0; i < beams.size; i++) {
            Beam b = beams.get(i);
            for (int k = 0; k <= 3; k++) lighting.light(b.x1 + (b.x2 - b.x1) * k / 3f, b.y1 + (b.y2 - b.y1) * k / 3f, 110, 0x7fffd4, b.life / 0.28f);
        }
        for (int i = 0; i < w.bosses.size; i++) {
            Boss b = w.bosses.get(i);
            if (b.type == Boss.HELI) lighting.light(b.x + b.facing * 30, b.y - 60, 180, 0xfff8e1, 0.6f);
            else lighting.light(b.x, b.y + 100, 90 + b.laserCharge * 120, 0xff3d00, 0.7f);
        }
        if (w.mode instanceof KingHillMode) {
            Vector2 h = ((KingHillMode) w.mode).hill();
            lighting.light(h.x, h.y + 30, KingHillMode.RADIUS * 1.8f, 0xfff59d, 0.5f);
        }
        lighting.render(batch, a.soft, cam, ambR, ambG, ambB);
    }

    private void drawNames(Draw d, World w, Player local, float minX, float minY, float maxX, float maxY) {
        BitmapFont f = a.small;
        for (int i61_ = 0; i61_ < w.players.size; i61_++) {
            Player p = w.players.get(i61_);
            if (!p.alive || p == local) continue;
            if (p.x < minX - 60 || p.x > maxX + 60 || p.y < minY - 80 || p.y > maxY + 80) continue;
            if (p.disguise >= 0 && (local == null || local.team != p.team)) continue; // disfarce nao mostra o nome
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
        float size = 18 * cam_zoom;
        Icons.crosshair(d, cx, cy, size, settings.crosshairStyle, settings.crosshairRgb(), 0.95f);
        if (hitMarker > 0) {
            float o = size * 0.45f, l = size * 0.4f;
            d.rgb(0xffffff, hitMarker);
            d.line(cx - o, cy - o, cx - o - l, cy - o - l, 2.5f * cam_zoom);
            d.line(cx + o, cy - o, cx + o + l, cy - o - l, 2.5f * cam_zoom);
            d.line(cx - o, cy + o, cx - o - l, cy + o + l, 2.5f * cam_zoom);
            d.line(cx + o, cy + o, cx + o + l, cy + o + l, 2.5f * cam_zoom);
        }
    }

    private float cam_zoom = 1f;

    // ------------------------------------------------------------------ particulas de ambiente

    private float ambientTimer;
    private float viewMinX, viewMinY, viewW = 1000, viewH = 600;

    public static final float DAY_CYCLE = 240f;

    private void updateDayNight() {
        if (world == null) return;
        GameMap m = world.map;
        int mode = settings.dayNight;
        if (m.night || mode == 2) {
            night = 1;
            sunPos = 0.6f;
            return;
        }
        if (mode == 0) {
            night = 0;
            sunPos = 0.65f;
            return;
        }
        float p = (world.time / DAY_CYCLE + 0.18f + (m.id.hashCode() & 7) * 0.01f) % 1f;
        if (p < 0.5f) night = 0;
        else if (p < 0.6f) night = (p - 0.5f) / 0.1f;
        else if (p < 0.92f) night = 1;
        else night = 1 - (p - 0.92f) / 0.08f;
        night = night * night * (3 - 2 * night);
        sunPos = p < 0.55f ? p / 0.55f : (p - 0.55f) / 0.45f;
    }

    private void updateAmbient(float dt) {
        if (!settings.ambient || world == null || settings.quality == 0) return;
        ambientTimer -= dt;
        if (ambientTimer > 0) return;
        Theme t = themeAtY(viewMinY + viewH / 2);
        Theme.Amb amb = t.ambient;
        ambientTimer = amb == Theme.Amb.SNOW || amb == Theme.Amb.RAIN ? 0.025f : (amb == Theme.Amb.BUBBLES ? 0.12f : 0.09f);
        float x = viewMinX + MathUtils.random(viewW), y = viewMinY + viewH + 10;
        switch (amb) {
            case SNOW: {
                Particles.P p = fx.spawn(Particles.CIRCLE, x, y, MathUtils.random(-20, 20), -MathUtils.random(40, 80), 9f, MathUtils.random(1.2f, 2.8f), 1.5f, 0xffffff, 0.85f);
                p.a2 = 0.6f;
                break;
            }
            case RAIN: {
                Particles.P p = fx.spawn(Particles.LINE, x, y, -60, -900, 1.2f, 14, 1.2f, 0x9fb7e0, 0.45f);
                p.rot = 0;
                break;
            }
            case LEAVES: {
                Particles.P p = fx.spawn(Particles.RECT, x, y, MathUtils.random(-40, 20), -MathUtils.random(30, 60), 9f, 6, 6, MathUtils.randomBoolean() ? 0x7cb342 : 0x558b2f, 0.9f);
                p.vrot = MathUtils.random(-160, 160);
                p.a2 = 0.8f;
                break;
            }
            case DUST:
                fx.spawn(Particles.SOFT, viewMinX - 10, viewMinY + MathUtils.random(viewH), MathUtils.random(80, 160), MathUtils.random(-10, 10), 7f, MathUtils.random(3, 8), 10, 0xe9c46a, 0.25f);
                break;
            case STARS: {
                Particles.P p = fx.spawn(Particles.SOFT, x, viewMinY + MathUtils.random(viewH), 0, 6, 3f, 2, 0, 0xb3e5fc, 0.6f);
                p.additive = true;
                break;
            }
            case SOOT:
                fx.spawn(Particles.CIRCLE, x, y, MathUtils.random(-10, 10), -MathUtils.random(25, 45), 10f, 1.4f, 1f, 0x424242, 0.6f);
                break;
            case DRIPS: {
                Particles.P p = fx.spawn(Particles.CIRCLE, x, y, 0, -420, 1.8f, 1.6f, 1.2f, 0x90a4ae, 0.7f);
                p.grav = 300;
                break;
            }
            case EMBERS: {
                Particles.P p = fx.spawn(Particles.SOFT, x, viewMinY - 10, MathUtils.random(-30, 30), MathUtils.random(60, 140), 5f, MathUtils.random(1.5f, 3f), 0.5f, MathUtils.randomBoolean() ? 0xff9100 : 0xffd180, 0.9f);
                p.additive = true;
                break;
            }
            case BUBBLES: {
                Particles.P p = fx.spawn(Particles.RING, x, viewMinY - 10, MathUtils.random(-10, 10), MathUtils.random(50, 110), 7f, MathUtils.random(2, 5), MathUtils.random(2, 5), 0xe1f5fe, 0.6f);
                p.a2 = 0.6f;
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void dispose() {
        disposeArt();
        lighting.dispose();
    }
}
