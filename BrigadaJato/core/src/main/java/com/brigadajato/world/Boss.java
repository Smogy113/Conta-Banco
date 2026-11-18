package com.brigadajato.world;

import com.badlogic.gdx.math.MathUtils;

/** Chefoes do modo Sobrevivencia: helicoptero de ataque e robo gigante. */
public class Boss {
    public static final int HELI = 0, ROBOT = 1;

    public int id, type;
    public float x, y, vx, vy, hp, maxHp, aim;
    public int facing = 1;
    public boolean alive = true, onGround;
    public float fireCd = 1.5f, rocketCd = 3f, laserCd = 4f, laserCharge, stompCd = 2.5f, jetTime, anim, hurtFlash;
    public float laserAim;
    private int burst;

    public float halfW() {
        return type == HELI ? 66 : 38;
    }

    public float height() {
        return type == HELI ? 56 : 118;
    }

    public float centerY() {
        return y + height() / 2;
    }

    private Player target(World w) {
        Player best = null;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < w.players.size; i++) {
            Player p = w.players.get(i);
            if (!p.alive || p.team != C.TEAM_RED) continue;
            float dx = p.x - x, dy = p.centerY() - centerY();
            float d = dx * dx + dy * dy;
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private static float approach(float v, float t, float d) {
        return v < t ? Math.min(t, v + d) : Math.max(t, v - d);
    }

    public void update(World w, float dt) {
        if (!alive) return;
        hurtFlash = Math.max(0, hurtFlash - dt);
        Player t = target(w);
        float tx = t != null ? t.x : w.map.worldW() / 2, ty = t != null ? t.centerY() : w.map.worldH() / 2;
        aim = MathUtils.atan2(ty - centerY(), tx - x);
        facing = tx >= x ? 1 : -1;
        if (type == HELI) updateHeli(w, dt, t, tx, ty);
        else updateRobot(w, dt, t, tx, ty);
    }

    private void updateHeli(World w, float dt, Player t, float tx, float ty) {
        anim += dt;
        float wantX = tx + MathUtils.sin(w.time * 0.7f) * 240;
        float wantY = MathUtils.clamp(ty + 210 + MathUtils.sin(w.time * 1.3f) * 30, 120, w.map.worldH() - 140);
        vx = approach(vx, MathUtils.clamp((wantX - x) * 1.6f, -260, 260), 520 * dt);
        vy = approach(vy, MathUtils.clamp((wantY - y) * 1.6f, -200, 200), 520 * dt);
        float nx = x + vx * dt;
        if (w.overlapsSolid(nx, y, halfW(), height())) {
            vx = 0;
            vy = Math.max(vy, 140);
        } else x = nx;
        float ny = y + vy * dt;
        if (w.overlapsSolid(x, ny, halfW(), height())) vy = 0;
        else y = ny;
        x = MathUtils.clamp(x, 90, w.map.worldW() - 90);
        y = MathUtils.clamp(y, 60, w.map.worldH() - 100);
        if (t == null) return;
        boolean los = w.lineOfSight(x + facing * 50, y + 14, t.x, t.centerY());
        fireCd -= dt;
        if (fireCd <= 0) {
            if (los && burst < 14) {
                float a = aim + (w.rng.nextFloat() - 0.5f) * 0.14f;
                w.bossShot(this, WeaponType.LMG, x + facing * 52, y + 14, a, 7);
                burst++;
                fireCd = 0.085f;
            } else {
                burst = 0;
                fireCd = 1.8f;
            }
        }
        rocketCd -= dt;
        if (rocketCd <= 0 && los) {
            rocketCd = 3.6f;
            w.bossRocket(this, x - facing * 10, y + 4, aim - 0.08f, 55);
            w.bossRocket(this, x - facing * 10, y + 4, aim + 0.08f, 55);
        }
    }

    private void updateRobot(World w, float dt, Player t, float tx, float ty) {
        float hw = halfW(), h = height();
        vy -= w.gravity * dt;
        if (vy < -C.MAX_FALL) vy = -C.MAX_FALL;
        float dx = tx - x;
        float want = Math.abs(dx) > 130 ? Math.signum(dx) * 95 : 0;
        if (laserCharge > 0) want = 0;
        vx = approach(vx, want, 420 * dt);
        jetTime -= dt;
        boolean wallAhead = w.overlapsSolid(x + Math.signum(vx) * 6, y + 4, hw, h - 8);
        if (onGround && jetTime <= 0 && (wallAhead || ty - centerY() > 150)) {
            vy = 820;
            jetTime = 1.2f;
            w.sound("rocket", x, y);
        }
        float nx = x + vx * dt;
        if (w.overlapsSolid(nx, y, hw, h)) vx = 0;
        else x = nx;
        float ny = y + vy * dt;
        onGround = false;
        if (w.overlapsSolid(x, ny, hw, h)) {
            if (vy < 0) {
                ny = (float) (Math.floor(ny / C.TILE) + 1) * C.TILE;
                if (w.overlapsSolid(x, ny, hw, h)) ny = y;
                if (vy < -500) w.robotLanding(this);
                onGround = true;
            } else ny = y;
            vy = 0;
        }
        y = ny;
        anim += Math.abs(vx) * dt / 30f;
        if (t == null) return;
        // pisao
        stompCd -= dt;
        if (onGround && stompCd <= 0 && Math.abs(dx) < 150 && Math.abs(ty - centerY()) < 110) {
            stompCd = 3f;
            w.bossStomp(this);
        }
        boolean los = w.lineOfSight(x, y + 96, t.x, t.centerY());
        // canhao do braco
        fireCd -= dt;
        if (fireCd <= 0 && los && laserCharge <= 0) {
            fireCd = 1.4f;
            for (int i = -1; i <= 1; i++) w.bossShot(this, WeaponType.SHOTGUN, x + facing * 40, y + 70, aim + i * 0.12f, 9);
        }
        // laser com aviso
        laserCd -= dt;
        if (laserCharge > 0) {
            laserCharge += dt;
            if (laserCharge >= 0.9f) {
                laserCharge = 0;
                laserCd = 4.5f;
                w.laserBeam(x + facing * 16, y + 100, laserAim, 46, 1300, C.BOSS_OWNER, C.TEAM_BLUE, WeaponType.LASER.ordinal());
            }
        } else if (laserCd <= 0 && los) {
            laserCharge = 0.01f;
            laserAim = MathUtils.atan2(ty - (y + 100), tx - (x + facing * 16));
            WorldEvent e = w.emit(WorldEvent.Type.BOSS);
            e.a = id;
            e.b = 2;
            e.x = x;
            e.y = y + 100;
        }
    }
}
