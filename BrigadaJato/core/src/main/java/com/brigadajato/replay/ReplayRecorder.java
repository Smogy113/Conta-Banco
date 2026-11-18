package com.brigadajato.replay;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.brigadajato.world.Gun;
import com.brigadajato.world.Pickup;
import com.brigadajato.world.Player;
import com.brigadajato.world.Projectile;
import com.brigadajato.world.World;
import com.brigadajato.world.WorldEvent;
import com.brigadajato.world.modes.BombMode;
import com.brigadajato.world.modes.CtfMode;

/** Grava continuamente os ultimos segundos da partida num buffer circular (30 quadros/s). */
public class ReplayRecorder {
    public static final float FPS = 30f;
    public static final float SECONDS = 10f;
    private static final int MAXP = 40, MAXR = 200, MAXK = 48;

    private final ReplayFrame[] ring;
    private int head, count;
    private float acc;
    private final Array<WorldEvent> pending = new Array<>(false, 32);
    public final IntMap<Clip.Who> roster = new IntMap<>();

    public ReplayRecorder() {
        ring = new ReplayFrame[(int) (FPS * SECONDS)];
        for (int i = 0; i < ring.length; i++) ring[i] = new ReplayFrame(MAXP, MAXR, MAXK);
    }

    public void addEvent(WorldEvent e) {
        if (pending.size < 400) pending.add(e);
    }

    public void update(World w, float dt) {
        acc += dt;
        if (acc < 1f / FPS) return;
        acc = Math.min(acc - 1f / FPS, 1f / FPS);
        capture(w);
    }

    public int count() {
        return count;
    }

    /** i = 0 e o mais antigo. */
    public ReplayFrame get(int i) {
        int idx = (head - count + i + ring.length) % ring.length;
        return ring[idx];
    }

    public float newestTime() {
        return count > 0 ? get(count - 1).time : 0;
    }

    private void capture(World w) {
        ReplayFrame f = ring[head];
        f.time = w.time;
        f.events.clear();
        f.events.addAll(pending);
        pending.clear();
        int np = 0;
        for (int i = 0; i < w.players.size && np < f.maxP(); i++) {
            Player p = w.players.get(i);
            if (!roster.containsKey(p.id) || roster.get(p.id).team != p.team) {
                Clip.Who who = new Clip.Who();
                who.name = p.name;
                who.look = p.look;
                who.team = p.team;
                who.dummy = p.dummy;
                roster.put(p.id, who);
            }
            if (!p.alive) continue;
            f.pid[np] = p.id;
            int o = np * ReplayFrame.PF;
            f.pf[o] = p.x;
            f.pf[o + 1] = p.y;
            f.pf[o + 2] = p.vx;
            f.pf[o + 3] = p.vy;
            f.pf[o + 4] = p.aim;
            f.pf[o + 5] = p.anim;
            f.pf[o + 6] = p.meleeAnim;
            f.pf[o + 7] = p.hurtFlash;
            f.pf[o + 8] = p.health;
            f.pf[o + 9] = p.spawnShield;
            f.pf[o + 10] = p.dmgBoost;
            f.pf[o + 11] = p.actionProgress;
            int k = np * ReplayFrame.PI;
            f.pi[k] = (p.onGround ? 1 : 0) | (p.crouching ? 2 : 0) | (p.jetting ? 4 : 0) | (p.facing > 0 ? 8 : 0)
                    | (p.hasBomb ? 16 : 0) | (p.dummy ? 32 : 0);
            Gun g = p.gun();
            f.pi[k + 1] = g != null ? g.type.ordinal() : -1;
            f.pi[k + 2] = p.carryingFlag;
            f.pi[k + 3] = p.team;
            np++;
        }
        f.np = np;
        int nr = 0;
        for (int i = 0; i < w.projectiles.size && nr < f.maxR(); i++) {
            Projectile pr = w.projectiles.get(i);
            if (pr.dead) continue;
            int o = nr * ReplayFrame.RF;
            f.rf[o] = pr.x;
            f.rf[o + 1] = pr.y;
            f.rf[o + 2] = pr.vx;
            f.rf[o + 3] = pr.vy;
            f.rf[o + 4] = pr.rot;
            f.rf[o + 5] = pr.startX;
            f.rf[o + 6] = pr.startY;
            f.rf[o + 7] = pr.life;
            f.rf[o + 8] = pr.fuse;
            int k = nr * ReplayFrame.RI;
            f.ri[k] = pr.kind.ordinal();
            f.ri[k + 1] = pr.weapon != null ? pr.weapon.ordinal() : -1;
            f.ri[k + 2] = (pr.armed ? 1 : 0) | (pr.stuck ? 2 : 0);
            f.ri[k + 3] = pr.team;
            nr++;
        }
        f.nr = nr;
        int nk = 0;
        for (int i = 0; i < w.pickups.size && nk < f.maxK(); i++) {
            Pickup pk = w.pickups.get(i);
            if (!pk.active) continue;
            int o = nk * ReplayFrame.KF;
            f.kf[o] = pk.x;
            f.kf[o + 1] = pk.y;
            f.kf[o + 2] = pk.bob;
            f.kf[o + 3] = pk.life;
            int k = nk * ReplayFrame.KI;
            f.ki[k] = pk.type.ordinal();
            f.ki[k + 1] = pk.weapon != null ? pk.weapon.ordinal() : -1;
            f.ki[k + 2] = pk.dropped ? 1 : 0;
            nk++;
        }
        f.nk = nk;
        if (w.mode instanceof CtfMode) {
            CtfMode m = (CtfMode) w.mode;
            f.mode[0] = m.state[1];
            f.mode[1] = m.fx[1];
            f.mode[2] = m.fy[1];
            f.mode[3] = m.state[2];
            f.mode[4] = m.fx[2];
            f.mode[5] = m.fy[2];
        } else if (w.mode instanceof BombMode) {
            BombMode m = (BombMode) w.mode;
            f.mode[0] = m.bombState;
            f.mode[1] = m.bombX;
            f.mode[2] = m.bombY;
            f.mode[3] = m.bombTimer;
            f.mode[4] = m.phase;
        }
        head = (head + 1) % ring.length;
        count = Math.min(count + 1, ring.length);
    }

    /** Quantos quadros gravados estao entre t0 e t1. */
    public int countBetween(float t0, float t1) {
        int n = 0;
        for (int i = 0; i < count; i++) {
            float t = get(i).time;
            if (t >= t0 && t <= t1) n++;
        }
        return n;
    }

    /** Copia os quadros entre t0 e t1 para um clipe novo. */
    public Clip extract(float t0, float t1) {
        Clip c = new Clip();
        for (int i = 0; i < count; i++) {
            ReplayFrame f = get(i);
            if (f.time < t0 || f.time > t1) continue;
            c.frames.add(f.compactCopy());
            for (int k = 0; k < f.np; k++) {
                int id = f.pid[k];
                if (!c.roster.containsKey(id) && roster.containsKey(id)) c.roster.put(id, roster.get(id));
            }
        }
        // inclui quem aparece so nos eventos (ex.: vitima que morreu no primeiro quadro)
        for (IntMap.Entry<Clip.Who> e : roster.entries()) if (!c.roster.containsKey(e.key)) c.roster.put(e.key, e.value);
        return c;
    }
}
