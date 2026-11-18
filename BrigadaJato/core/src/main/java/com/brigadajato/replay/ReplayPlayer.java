package com.brigadajato.replay;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;
import com.brigadajato.Assets;
import com.brigadajato.Audio;
import com.brigadajato.Settings;
import com.brigadajato.render.Draw;
import com.brigadajato.render.WorldRenderer;
import com.brigadajato.world.Gun;
import com.brigadajato.world.Pickup;
import com.brigadajato.world.Player;
import com.brigadajato.world.Projectile;
import com.brigadajato.world.WeaponType;
import com.brigadajato.world.World;
import com.brigadajato.world.modes.BombMode;
import com.brigadajato.world.modes.CtfMode;

/** Reproduz um clipe num "mundo fantasma" so para desenhar. */
public class ReplayPlayer implements Disposable {
    public final World view;
    public final WorldRenderer renderer;
    public final Audio audio;
    private Clip clip;
    public float t;
    private int lastEventFrame = -1;
    private final Array<Projectile> projPool = new Array<>();
    private final Array<Pickup> pickPool = new Array<>();
    private final IntMap<Player> players = new IntMap<>();

    public ReplayPlayer(World live, Assets assets, Settings settings) {
        view = new World(live.map, live.config, false, 1);
        audio = new Audio(assets, settings);
        audio.volumeScale = 0.75f;
        renderer = new WorldRenderer(assets, audio, settings);
        renderer.replayMode = true;
        renderer.setWorld(view);
    }

    public Clip clip() {
        return clip;
    }

    public void start(Clip c) {
        clip = c;
        t = c.start();
        lastEventFrame = -1;
        renderer.fx.clear();
        view.players.clear();
        for (IntMap.Entry<Clip.Who> e : c.roster.entries()) {
            Player p = players.get(e.key);
            if (p == null) {
                p = new Player(e.key, e.value.name, e.value.team, e.value.look, false);
                players.put(e.key, p);
            }
            p.name = e.value.name;
            p.look = e.value.look;
            p.team = e.value.team;
            p.dummy = e.value.dummy;
            p.alive = false;
            view.players.add(p);
        }
        apply(0);
    }

    public boolean finished() {
        return clip == null || t >= clip.end();
    }

    public Player focus() {
        return clip == null ? null : view.player(clip.focusId);
    }

    /** Avanca o tempo do replay (dt ja multiplicado pela velocidade). */
    public void advance(float dt, float speed) {
        if (clip == null) return;
        t = Math.min(clip.end(), t + dt);
        audio.pitchScale = MathUtils.clamp(0.55f + speed * 0.45f, 0.5f, 1f);
        apply(dt);
        renderer.update(dt);
    }

    private int frameIndexAt(float time) {
        Array<ReplayFrame> fr = clip.frames;
        int lo = 0, hi = fr.size - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >> 1;
            if (fr.get(mid).time <= time) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    private void apply(float dt) {
        Array<ReplayFrame> fr = clip.frames;
        if (fr.size == 0) return;
        int i = frameIndexAt(t);
        // eventos dos quadros que passaram
        for (int k = lastEventFrame + 1; k <= i; k++) {
            ReplayFrame f = fr.get(k);
            Player focus = focus();
            for (int e = 0; e < f.events.size; e++) renderer.handle(f.events.get(e), focus);
        }
        lastEventFrame = Math.max(lastEventFrame, i);
        ReplayFrame a = fr.get(i), b = i + 1 < fr.size ? fr.get(i + 1) : a;
        float span = b.time - a.time;
        float k = span > 0 ? MathUtils.clamp((t - a.time) / span, 0, 1) : 0;
        view.time = t;
        // jogadores
        for (int p = 0; p < view.players.size; p++) view.players.get(p).alive = false;
        for (int n = 0; n < a.np; n++) {
            Player p = view.player(a.pid[n]);
            if (p == null) continue;
            int o = n * ReplayFrame.PF, q = n * ReplayFrame.PI;
            int bn = b.indexOf(a.pid[n]);
            float x = a.pf[o], y = a.pf[o + 1], aim = a.pf[o + 4];
            if (bn >= 0) {
                int ob = bn * ReplayFrame.PF;
                x += (b.pf[ob] - x) * k;
                y += (b.pf[ob + 1] - y) * k;
                float da = b.pf[ob + 4] - aim;
                while (da > MathUtils.PI) da -= MathUtils.PI2;
                while (da < -MathUtils.PI) da += MathUtils.PI2;
                aim += da * k;
            }
            p.alive = true;
            p.x = x;
            p.y = y;
            p.vx = a.pf[o + 2];
            p.vy = a.pf[o + 3];
            p.aim = aim;
            p.anim = a.pf[o + 5] + (bn >= 0 ? (b.pf[bn * ReplayFrame.PF + 5] - a.pf[o + 5]) * k : 0);
            p.meleeAnim = Math.max(p.meleeAnim - dt, a.pf[o + 6]);
            p.hurtFlash = a.pf[o + 7];
            p.health = a.pf[o + 8];
            p.spawnShield = a.pf[o + 9];
            p.dmgBoost = a.pf[o + 10];
            p.actionProgress = a.pf[o + 11];
            int fl = a.pi[q];
            p.onGround = (fl & 1) != 0;
            p.crouching = (fl & 2) != 0;
            p.jetting = (fl & 4) != 0;
            p.facing = (fl & 8) != 0 ? 1 : -1;
            p.hasBomb = (fl & 16) != 0;
            WeaponType wt = WeaponType.of(a.pi[q + 1]);
            if (wt == null) p.guns[0] = null;
            else if (p.guns[0] == null || p.guns[0].type != wt) p.guns[0] = new Gun(wt);
            p.guns[1] = null;
            p.slot = 0;
            p.carryingFlag = a.pi[q + 2];
        }
        // projeteis (extrapolados pela velocidade)
        float since = t - a.time;
        view.projectiles.clear();
        for (int n = 0; n < a.nr; n++) {
            Projectile pr = n < projPool.size ? projPool.get(n) : null;
            if (pr == null) {
                pr = new Projectile();
                projPool.add(pr);
            }
            int o = n * ReplayFrame.RF, q = n * ReplayFrame.RI;
            pr.kind = Projectile.Kind.values()[a.ri[q]];
            pr.weapon = WeaponType.of(a.ri[q + 1]);
            if (pr.weapon == null) pr.weapon = WeaponType.PISTOL;
            boolean moving = !(pr.kind == Projectile.Kind.MINE && (a.ri[q + 2] & 2) != 0);
            pr.x = a.rf[o] + (moving ? a.rf[o + 2] * since : 0);
            pr.y = a.rf[o + 1] + (moving ? a.rf[o + 3] * since : 0);
            pr.vx = a.rf[o + 2];
            pr.vy = a.rf[o + 3];
            pr.rot = a.rf[o + 4];
            pr.startX = a.rf[o + 5];
            pr.startY = a.rf[o + 6];
            pr.life = a.rf[o + 7];
            pr.fuse = a.rf[o + 8];
            pr.armed = (a.ri[q + 2] & 1) != 0;
            pr.stuck = (a.ri[q + 2] & 2) != 0;
            pr.team = a.ri[q + 3];
            pr.dead = false;
            pr.visual = true;
            view.projectiles.add(pr);
        }
        // itens
        view.pickups.clear();
        for (int n = 0; n < a.nk; n++) {
            Pickup pk = n < pickPool.size ? pickPool.get(n) : null;
            if (pk == null) {
                pk = new Pickup();
                pickPool.add(pk);
            }
            int o = n * ReplayFrame.KF, q = n * ReplayFrame.KI;
            pk.x = a.kf[o];
            pk.y = a.kf[o + 1];
            pk.bob = a.kf[o + 2] + since;
            pk.life = a.kf[o + 3];
            pk.type = Pickup.Type.values()[a.ki[q]];
            pk.weapon = WeaponType.of(a.ki[q + 1]);
            pk.dropped = a.ki[q + 2] != 0;
            pk.active = true;
            view.pickups.add(pk);
        }
        // objetos do modo
        if (view.mode instanceof CtfMode) {
            CtfMode m = (CtfMode) view.mode;
            m.state[1] = (int) a.mode[0];
            m.fx[1] = a.mode[1];
            m.fy[1] = a.mode[2];
            m.state[2] = (int) a.mode[3];
            m.fx[2] = a.mode[4];
            m.fy[2] = a.mode[5];
        } else if (view.mode instanceof BombMode) {
            BombMode m = (BombMode) view.mode;
            m.bombState = (int) a.mode[0];
            m.bombX = a.mode[1];
            m.bombY = a.mode[2];
            m.bombTimer = a.mode[3];
            m.phase = (int) a.mode[4];
        }
    }

    public void render(Draw d, OrthographicCamera cam) {
        renderer.render(d, cam, view, focus(), false, 0, 0);
    }

    @Override
    public void dispose() {
        renderer.dispose();
    }
}
