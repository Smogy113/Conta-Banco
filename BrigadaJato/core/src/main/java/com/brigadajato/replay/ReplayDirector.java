package com.brigadajato.replay;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.brigadajato.Assets;
import com.brigadajato.I18n;
import com.brigadajato.Settings;
import com.brigadajato.render.Draw;
import com.brigadajato.render.Hud;
import com.brigadajato.render.Icons;
import com.brigadajato.render.SoldierPainter;
import com.brigadajato.world.C;
import com.brigadajato.world.Player;
import com.brigadajato.world.WeaponType;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;
import com.brigadajato.world.modes.BombMode;
import com.brigadajato.world.modes.CtfMode;

/**
 * Diretor de replays: grava a partida, detecta as melhores jogadas, mostra a killcam quando o
 * jogador morre e o "melhores momentos" no fim, com camera lenta e zoom.
 */
public class ReplayDirector implements Disposable {
    public enum Mode {NONE, KILLCAM, REEL}

    private final Assets assets;
    private final Settings settings;
    public final ReplayRecorder recorder = new ReplayRecorder();
    private final ReplayPlayer player;
    private final World live;
    public final OrthographicCamera cam = new OrthographicCamera();
    private final SoldierPainter painter = new SoldierPainter();

    public Mode mode = Mode.NONE;
    private float speed = 1, zoom = 0.85f;
    private float camX, camY;
    private float modeTime;
    private float flash;

    // killcam
    private float killcamAt = -1, deathTime;
    private int killerId = -1, killerWeapon = -1;
    private float killerHp;
    private int victimId = -1;

    // melhores jogadas
    private static class Pending {
        int focus;
        float start, end, lastKey;
        final FloatArray keys = new FloatArray();
        int kills;
        float bonus;
        boolean head, melee, explosive, longRange, air;
        String special;
        boolean local;
    }

    private final Array<Pending> pendings = new Array<>();
    public final Array<Clip> best = new Array<>();
    private final Array<Clip> reel = new Array<>();
    private int reelIndex;
    private float cardTime;
    public boolean reelDone;

    public ReplayDirector(World live, Assets assets, Settings settings) {
        this.live = live;
        this.assets = assets;
        this.settings = settings;
        player = new ReplayPlayer(live, assets, settings);
    }

    public void resize(float vw, float vh) {
        cam.viewportWidth = vw;
        cam.viewportHeight = vh;
    }

    /** Killcam ainda passando (segura o renascimento do jogador local). */
    public boolean holdRespawn() {
        return mode == Mode.KILLCAM || killcamAt > 0;
    }

    public boolean active() {
        return mode != Mode.NONE;
    }

    // ------------------------------------------------------------------ eventos

    public void onEvent(WorldEvent e, Player local) {
        recorder.addEvent(e);
        float now = live.time;
        int lid = local != null ? local.id : -999;
        switch (e.type) {
            case KILL: {
                Player k = live.player(e.a);
                if (e.b == lid && k != null && e.a != lid && settings.killcam && !live.mode.over) {
                    killcamAt = now + 0.8f;
                    deathTime = now;
                    killerId = e.a;
                    victimId = e.b;
                    killerWeapon = e.c;
                    killerHp = k.health;
                }
                if (k == null || e.a == e.b) break;
                Pending p = pendingFor(k.id, now);
                p.kills++;
                p.keys.add(now);
                p.lastKey = now;
                p.end = now + 1.3f;
                if (e.f > 0) p.head = true;
                if (e.c == C.KILL_MELEE) p.melee = true;
                WeaponType wt = WeaponType.of(e.c);
                if (e.c == C.KILL_FRAG || e.c == C.KILL_MINE || (wt != null && wt.isExplosive())) p.explosive = true;
                float dx = k.x - e.x, dy = k.centerY() - e.y;
                if (dx * dx + dy * dy > 650 * 650) p.longRange = true;
                if (!k.onGround) p.air = true;
                if (k.id == lid) p.local = true;
                if (k.streak >= 5) p.bonus += 2;
                break;
            }
            case FLAG:
                if (e.b == CtfMode.EV_CAPTURED && e.c >= 0) {
                    Pending p = pendingFor(e.c, now);
                    p.special = I18n.t("hl_flag");
                    p.bonus += 7;
                    p.keys.add(now);
                    p.lastKey = now;
                    p.end = now + 1.3f;
                    if (e.c == lid) p.local = true;
                }
                break;
            case BOMB:
                if ((e.b == BombMode.EV_PLANTED || e.b == BombMode.EV_DEFUSED) && e.c >= 0) {
                    Pending p = pendingFor(e.c, now);
                    p.special = I18n.t(e.b == BombMode.EV_PLANTED ? "hl_plant" : "hl_defuse");
                    p.bonus += e.b == BombMode.EV_PLANTED ? 4 : 6;
                    p.keys.add(now);
                    p.lastKey = now;
                    p.end = now + 1.3f;
                    if (e.c == lid) p.local = true;
                }
                break;
            default:
                break;
        }
    }

    private Pending pendingFor(int focus, float now) {
        for (int i = 0; i < pendings.size; i++) {
            Pending p = pendings.get(i);
            if (p.focus == focus && now - p.lastKey <= 4f) return p;
        }
        Pending p = new Pending();
        p.focus = focus;
        p.start = now - 3.2f;
        p.lastKey = now;
        p.end = now + 1.3f;
        pendings.add(p);
        return p;
    }

    private void finalizePending(Pending p) {
        Clip c = recorder.extract(Math.max(p.start, p.keys.size > 0 ? p.keys.first() - 3.2f : p.start), p.end);
        if (c.frames.size < 20) return;
        c.focusId = p.focus;
        c.keyTimes.addAll(p.keys);
        c.kills = p.kills;
        c.localPlay = p.local;
        float score = p.kills * 2 + Math.max(0, p.kills - 1) * 4 + p.bonus;
        if (p.head) score += 2;
        if (p.melee) score += 3;
        if (p.explosive) score += 1;
        if (p.longRange) score += 2;
        if (p.air) score += 1;
        if (p.local) score += 3;
        c.score = score;
        Clip.Who who = c.roster.get(p.focus);
        c.subtitle = who != null ? who.name : "?";
        if (p.special != null) c.title = p.special;
        else if (p.kills >= 4) c.title = I18n.t("hl_massacre");
        else if (p.kills == 3) c.title = I18n.t("hl_triple");
        else if (p.kills == 2) c.title = p.explosive ? I18n.t("hl_double_boom") : I18n.t("hl_double");
        else if (p.melee) c.title = I18n.t("hl_melee");
        else if (p.longRange && p.head) c.title = I18n.t("hl_snipe");
        else if (p.head) c.title = I18n.t("headshot");
        else if (p.air) c.title = I18n.t("hl_air");
        else if (p.explosive) c.title = I18n.t("hl_boom");
        else c.title = I18n.t("hl_kill");
        // mantem as 5 melhores
        best.add(c);
        best.sort((a, b) -> Float.compare(b.score, a.score));
        while (best.size > 5) best.pop();
    }

    // ------------------------------------------------------------------ atualizacao

    public void update(float dt, Player local) {
        recorder.update(live, dt);
        float now = live.time;
        for (int i = pendings.size - 1; i >= 0; i--) {
            Pending p = pendings.get(i);
            if (now >= p.end && now - p.lastKey > 1.2f) {
                pendings.removeIndex(i);
                finalizePending(p);
            }
        }
        if (mode == Mode.NONE && killcamAt > 0 && now >= killcamAt) {
            killcamAt = -1;
            Clip c = recorder.extract(deathTime - 2.2f, now);
            if (c.frames.size > 10 && local != null && !local.alive) {
                c.focusId = killerId;
                c.keyTimes.add(deathTime);
                begin(Mode.KILLCAM, c);
            }
        }
        if (mode == Mode.NONE) return;
        modeTime += dt;
        flash = Math.max(0, flash - dt * 3);
        if (mode == Mode.KILLCAM) {
            boolean respawned = local != null && local.alive;
            if (player.finished() || respawned || live.mode.over) {
                stop();
                return;
            }
        }
        if (mode == Mode.REEL && cardTime > 0) {
            cardTime -= dt;
            if (reelIndex < 0) {
                if (cardTime <= 0) nextClip();
                return;
            }
        }
        // velocidade e zoom: camera lenta perto dos momentos importantes
        Clip c = player.clip();
        float targetSpeed = 1f, targetZoom = mode == Mode.KILLCAM ? 0.82f : 0.92f;
        for (int i = 0; i < c.keyTimes.size; i++) {
            float dtKey = c.keyTimes.get(i) - player.t;
            if (mode == Mode.KILLCAM) {
                if (dtKey < 0.45f && dtKey > -0.5f) {
                    targetSpeed = 0.4f;
                    targetZoom = 0.68f;
                    if (dtKey < 0.03f && dtKey > -0.03f) flash = 0.8f;
                } else if (dtKey >= 0.45f) targetSpeed = 1.6f; // inicio mais rapido para chegar no abate
            } else if (dtKey < 0.55f && dtKey > -0.45f) {
                targetSpeed = 0.22f;
                targetZoom = 0.6f;
                if (dtKey < 0.03f && dtKey > -0.03f) flash = 1f;
            }
        }
        speed += (targetSpeed - speed) * Math.min(1, dt * 8);
        zoom += (targetZoom - zoom) * Math.min(1, dt * 4);
        player.advance(dt * speed, speed);
        if (mode == Mode.REEL && player.finished()) nextClip();
    }

    private void begin(Mode m, Clip c) {
        mode = m;
        modeTime = 0;
        speed = 1;
        zoom = m == Mode.KILLCAM ? 0.9f : 1f;
        player.start(c);
        player.victimId = m == Mode.KILLCAM ? victimId : -1;
        Player f = player.focus();
        if (f != null) {
            camX = f.x;
            camY = f.centerY();
        }
    }

    public void stop() {
        mode = Mode.NONE;
        player.audio.jetpack(false);
    }

    /** Fim de partida: monta o "melhores momentos". Retorna false se nao ha jogadas. */
    public boolean startReel() {
        float now = live.time;
        for (int i = pendings.size - 1; i >= 0; i--) {
            Pending p = pendings.get(i);
            p.end = Math.min(p.end, now);
            finalizePending(p);
        }
        pendings.clear();
        reelDone = true;
        if (best.size == 0) return false;
        reel.clear();
        for (int i = Math.min(3, best.size) - 1; i >= 0; i--) reel.add(best.get(i)); // do menor para o melhor
        reelIndex = -1;
        mode = Mode.REEL;
        modeTime = 0;
        cardTime = 1.6f;
        return true;
    }

    private void nextClip() {
        reelIndex++;
        if (reelIndex >= reel.size) {
            stop();
            return;
        }
        begin(Mode.REEL, reel.get(reelIndex));
        cardTime = 2.0f;
    }

    /** Toque na tela: pula (killcam) ou vai para o proximo clipe. */
    public void skip() {
        if (mode == Mode.KILLCAM) stop();
        else if (mode == Mode.REEL) {
            if (reelIndex < 0) nextClip();
            else nextClip();
        }
    }

    public void skipAll() {
        stop();
    }

    // ------------------------------------------------------------------ desenho

    public void renderWorld(Draw d, com.brigadajato.render.ColorFilter filter, int filterMode) {
        if (mode == Mode.REEL && reelIndex < 0) return;
        Player f = player.focus();
        if (f != null && f.alive) {
            float k = Math.min(1, 0.08f + speed * 0.12f);
            float tx = f.x + MathUtils.cos(f.aim) * 60, ty = f.centerY() + 20;
            Player v = mode == Mode.KILLCAM ? player.victim() : null;
            if (v != null && Math.abs(v.x - f.x) < 1000 && Math.abs(v.y - f.y) < 560) { // enquadra os dois
                tx = (f.x + v.x) / 2;
                ty = (f.centerY() + v.centerY()) / 2 + 20;
            }
            camX += (tx - camX) * k;
            camY += (ty - camY) * k;
        }
        cam.zoom = zoom;
        float hw = cam.viewportWidth * zoom / 2, hh = cam.viewportHeight * zoom / 2;
        float mw = live.map.worldW(), mh = live.map.worldH();
        cam.position.set(MathUtils.clamp(camX, hw - 120, Math.max(hw - 120, mw - hw + 120)),
                MathUtils.clamp(camY, hh - 120, Math.max(hh - 120, mh - hh + 120)), 0);
        cam.update();
        boolean filtered = filter != null && filter.begin(d.batch, filterMode);
        d.batch.setProjectionMatrix(cam.combined);
        d.batch.begin();
        if (filtered) filter.uniforms(filterMode);
        player.render(d, cam);
        d.batch.end();
        if (filtered) filter.end(d.batch);
    }

    public void drawOverlay(Draw d, float W, float H) {
        float bars = Math.min(1, modeTime * 4) * 64;
        d.color(0, 0, 0, 1);
        d.rect(0, H - bars, W, bars);
        d.rect(0, 0, W, bars);
        if (flash > 0) {
            d.color(1, 1, 1, flash * 0.35f);
            d.rect(0, 0, W, H);
        }
        // "REC" piscando
        if ((modeTime * 2) % 1 < 0.6f) {
            d.rgb(0xff1744, 1);
            d.circle(36, H - 32, 9);
        }
        assets.small.setColor(1, 1, 1, 1);
        assets.small.draw(d.batch, mode == Mode.KILLCAM ? I18n.t("killcam") : I18n.t("replay"), 54, H - 20);
        if (speed < 0.6f) {
            assets.small.setColor(1, 0.85f, 0.3f, 1);
            assets.small.draw(d.batch, I18n.t("slowmo"), W - 30, H - 20, 0, Align.right, false);
        }
        if (mode == Mode.KILLCAM) {
            Player k = player.focus();
            String name = k != null ? k.name : "?";
            assets.big.setColor(1, 0.55f, 0.5f, 1);
            assets.big.draw(d.batch, I18n.f("killcam_by", name), W / 2, 50, 0, Align.center, false);
            WeaponType wt = WeaponType.of(killerWeapon);
            if (wt != null) painter.drawGun(d, wt, W / 2 - 250, 30, 0, 1.3f, 1);
            assets.small.setColor(0.85f, 0.85f, 0.85f, 1);
            assets.small.draw(d.batch, Hud.weaponName(killerWeapon) + "   ·   " + I18n.f("hp_left", Math.max(1, Math.round(killerHp))), W / 2, 22, 0, Align.center, false);
            assets.small.setColor(1, 1, 1, 0.7f);
            assets.small.draw(d.batch, I18n.t("tap_skip"), W - 30, 22, 0, Align.right, false);
        } else if (mode == Mode.REEL) {
            if (reelIndex < 0) {
                d.color(0, 0, 0, 0.6f);
                d.rect(0, 0, W, H);
                d.color(1, 1, 1, 1);
                Draw.tiled(d, assets.uiStripes, 0, H / 2 + 70, W, 10);
                Draw.tiled(d, assets.uiStripes, 0, H / 2 - 80, W, 10);
                float s = 0.8f + Math.min(1, modeTime * 3) * 0.2f;
                assets.huge.getData().setScale(s / assets.fontScale);
                assets.huge.setColor(1, 0.78f, 0.1f, 1);
                assets.huge.draw(d.batch, I18n.t("best_plays"), W / 2, H / 2 + 40, 0, Align.center, false);
                assets.huge.getData().setScale(1 / assets.fontScale);
            } else {
                Clip c = reel.get(reelIndex);
                float show = Math.min(1, cardTime * 2);
                if (show > 0) {
                    float slide = (1 - Math.min(1, (2.0f - cardTime) * 4)) * -300;
                    d.color(1, 1, 1, show);
                    Draw.patch(d, assets.uiChip, 30 + slide, H - 190, 520, 104, 14);
                    assets.big.setColor(1, 0.78f, 0.1f, show);
                    assets.big.draw(d.batch, "#" + (reel.size - reelIndex) + "  " + c.title, 50 + slide, H - 104);
                    assets.normal.setColor(1, 1, 1, show);
                    assets.normal.draw(d.batch, c.subtitle, 52 + slide, H - 150);
                }
                assets.small.setColor(1, 1, 1, 0.7f);
                assets.small.draw(d.batch, I18n.t("tap_skip"), W - 30, 22, 0, Align.right, false);
                // pontinhos de progresso
                for (int i = 0; i < reel.size; i++) {
                    d.rgb(i == reelIndex ? 0xffc61a : 0x757575, 1);
                    d.circle(W / 2 - (reel.size - 1) * 12 + i * 24, 30, 6);
                }
            }
        }
        assets.small.setColor(1, 1, 1, 1);
        assets.normal.setColor(1, 1, 1, 1);
        assets.big.setColor(1, 1, 1, 1);
    }

    @Override
    public void dispose() {
        player.dispose();
    }
}
