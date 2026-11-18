package com.brigadajato.world.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import com.brigadajato.world.C;
import com.brigadajato.world.GameMap;
import com.brigadajato.world.Gun;
import com.brigadajato.world.Pickup;
import com.brigadajato.world.Player;
import com.brigadajato.world.PlayerInput;
import com.brigadajato.world.WeaponType;
import com.brigadajato.world.World;

import java.util.Random;

/** Inteligencia artificial de um bot: percebe, escolhe objetivo, navega, mira e atira. */
public class BotBrain {
    private final Player p;
    private final Random rng;
    private int difficulty;
    private float reaction, aimError, turnSpeed, lead, vision, burstRest;

    private Player target;
    private boolean targetVisible;
    private float lastSeenX, lastSeenY, lastSeenTime = -99;
    private float reactTimer;
    private float thinkTimer;

    private final IntArray path = new IntArray();
    private int pathIdx;
    private float repathTimer;
    private float pathGoalX, pathGoalY;

    private float wanderX, wanderY, wanderTimer;
    private float strafeTimer;
    private int strafeDir = 1;
    private float hopTimer;

    private float stuckCheck;
    private float stuckX, stuckY;
    private float unstuckTimer;
    private int unstuckDir = 1;

    private float aimNoise, aimNoiseTarget, noiseTimer;
    private float grenadeTimer = 4f;
    private float burstTimer;
    private boolean resting;
    private float dummyTimer;

    private final Vector2 goal = new Vector2();
    /** chefao na mira (Sobrevivencia) */
    private com.brigadajato.world.Boss boss;
    private float useCd, emoteCd = 5;
    /** bloco no caminho para quebrar / porta para abrir */
    private float blockX, blockY;
    private boolean blocked;
    /** texto de depuracao (somente testes) */
    public static boolean DEBUG = false;
    public String debug = "";

    public BotBrain(Player p, int difficulty, long seed) {
        this.p = p;
        this.rng = new Random(seed);
        setDifficulty(difficulty);
    }

    public void setDifficulty(int d) {
        difficulty = MathUtils.clamp(d, 0, 2);
        switch (difficulty) {
            case 0:
                reaction = 0.7f;
                aimError = 0.2f;
                turnSpeed = 4.5f;
                lead = 0.25f;
                vision = 950;
                burstRest = 0.45f;
                break;
            case 2:
                reaction = 0.18f;
                aimError = 0.04f;
                turnSpeed = 13f;
                lead = 1f;
                vision = 1500;
                burstRest = 0.08f;
                break;
            default:
                reaction = 0.38f;
                aimError = 0.1f;
                turnSpeed = 7.5f;
                lead = 0.7f;
                vision = 1200;
                burstRest = 0.22f;
                break;
        }
    }

    public void onSpawn() {
        target = null;
        targetVisible = false;
        path.clear();
        repathTimer = 0;
        wanderTimer = 0;
        grenadeTimer = 3 + rng.nextFloat() * 4;
        p.input.aim = p.facing > 0 ? 0 : MathUtils.PI;
    }

    public void onDamaged(Player attacker) {
        if (attacker == null || attacker == p) return;
        if (difficulty > 0 && p.rollCd <= 0 && rng.nextFloat() < 0.22f) p.input.press(PlayerInput.P_ROLL); // esquiva
        if (target == null || !targetVisible) {
            target = attacker;
            lastSeenX = attacker.x;
            lastSeenY = attacker.y;
            lastSeenTime = attacker.alive ? lastSeenTime : -99;
            reactTimer = Math.min(reactTimer, reaction * 0.6f);
        }
    }

    // ------------------------------------------------------------------

    public void think(World w, float dt) {
        PlayerInput in = p.input;
        if (p.dummy) {
            dummyThink(w, dt);
            return;
        }
        in.fire = false;
        in.use = false;

        thinkTimer -= dt;
        if (thinkTimer <= 0) {
            thinkTimer = 0.1f + rng.nextFloat() * 0.06f;
            perceive(w);
        }
        if (target != null && !target.alive) {
            // comemora de vez em quando
            emoteCd -= 1;
            if (emoteCd <= 0 && rng.nextFloat() < 0.35f) {
                emoteCd = 4 + rng.nextInt(6);
                p.input.emote = rng.nextFloat() < 0.5f ? 4 : (C.EMOTE_PHRASES + rng.nextInt(C.EMOTE_DANCE_FIRST - C.EMOTE_PHRASES));
            }
            target = null;
            targetVisible = false;
        }
        useCd -= dt;
        if (boss != null && !boss.alive) boss = null;

        // ---------- escolhe para onde ir
        boolean hasObjective = w.mode.botGoal(p, goal);
        Gun g = p.gun();
        boolean wantGun = g == null || g.type == WeaponType.PISTOL || g.empty();
        Pickup item = null;
        if (!hasObjective || !targetVisible) {
            if (p.health < 40) item = nearestItem(w, Pickup.Type.HEALTH, 900);
            if (item == null && wantGun) item = nearestItem(w, Pickup.Type.WEAPON, 1000);
            if (item == null && p.frags == 0) item = nearestItem(w, Pickup.Type.GRENADES, 500);
        }
        float gx, gy;
        if (item != null && (!targetVisible || p.health < 30)) {
            gx = item.x;
            gy = item.y;
        } else if (hasObjective && (!targetVisible || dist(goal.x, goal.y) > 300 || p.hasBomb || p.carryingFlag != 0)) {
            gx = goal.x;
            gy = goal.y;
        } else if (targetVisible) {
            float range = g != null ? g.type.botRange : 0;
            float side = Math.signum(p.x - target.x);
            if (side == 0) side = 1;
            gx = target.x + side * range * 0.75f + strafeDir * 60;
            gy = target.y + 20;
        } else if (boss != null) {
            float side = Math.signum(p.x - boss.x);
            if (side == 0) side = 1;
            gx = boss.x + side * 380 + strafeDir * 80;
            gy = boss.type == com.brigadajato.world.Boss.HELI ? boss.y - 160 : boss.y;
        } else if (target != null && w.time - lastSeenTime < 4) {
            gx = lastSeenX;
            gy = lastSeenY;
        } else {
            wanderTimer -= dt;
            if (wanderTimer <= 0 || dist(wanderX, wanderY) < 60) pickWander(w);
            gx = wanderX;
            gy = wanderY;
        }

        navigate(w, gx, gy, dt);

        // desvio em combate
        strafeTimer -= dt;
        if (strafeTimer <= 0) {
            strafeTimer = 0.6f + rng.nextFloat() * 1.2f;
            strafeDir = rng.nextBoolean() ? 1 : -1;
        }
        if (targetVisible && difficulty > 0) {
            hopTimer -= dt;
            if (hopTimer <= 0) {
                hopTimer = 1.5f + rng.nextFloat() * 2.5f;
                if (p.fuel > 40) unstuckTimer = Math.max(unstuckTimer, 0.0f);
            }
            if (hopTimer > 0 && hopTimer < 0.35f && p.fuel > 25) in.moveY = 1;
        }

        // ---------- mira e tiro
        aimAndShoot(w, dt, g);

        // ---------- acoes
        if (g != null) {
            if (g.mag == 0 && g.reserve == 0) {
                Gun o = p.otherGun();
                if (o != null && !o.empty()) in.press(PlayerInput.P_SWAP);
            } else if (!targetVisible && g.mag < g.type.mag * 0.4f && g.reserve > 0 && !p.reloading()) {
                in.press(PlayerInput.P_RELOAD);
            }
            if (targetVisible && g.type == WeaponType.PISTOL) {
                Gun o = p.otherGun();
                if (o != null && !o.empty()) in.press(PlayerInput.P_SWAP);
            }
        }
        Pickup near = w.nearestWeaponPickup(p);
        if (near != null && (g == null || near.weapon.tier > g.type.tier || g.empty())) in.press(PlayerInput.P_PICKUP);

        if (w.mode.botUse(p)) {
            in.use = true;
            in.moveX = 0;
            in.moveY = 0;
            if (!targetVisible) in.fire = false;
        }
    }

    private void perceive(World w) {
        if (p.flash > 0.5f) { // cego pela flashbang
            targetVisible = false;
            return;
        }
        Player best = null;
        float bestD = vision;
        float ex = p.x, ey = p.shoulderY();
        for (int i44_ = 0; i44_ < w.players.size; i44_++) {
            Player q = w.players.get(i44_);
            if (!q.alive || !w.enemies(p, q)) continue;
            if (q.dummy && !p.dummy && w.config.mode != com.brigadajato.world.ModeType.TRAINING) continue;
            float d = p.distTo(q);
            if (d > vision) continue;
            if (!w.lineOfSight(ex, ey, q.x, q.centerY()) && !w.lineOfSight(ex, ey, q.x, q.headY())) continue;
            if (w.smokeBlocks(ex, ey, q.x, q.centerY())) continue;
            float score = d - (q == target ? 200 : 0) - (q.carryingFlag != 0 || q.hasBomb ? 250 : 0);
            if (score < bestD) {
                bestD = score;
                best = q;
            }
        }
        boss = null;
        if (best == null && w.canHurtBoss(p.team)) {
            float bd = vision * 1.2f;
            for (int i = 0; i < w.bosses.size; i++) {
                com.brigadajato.world.Boss b = w.bosses.get(i);
                float dx = b.x - p.x, dy = b.centerY() - p.centerY();
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < bd && w.lineOfSight(ex, ey, b.x, b.centerY())) {
                    bd = d;
                    boss = b;
                }
            }
        }
        if (best != null) {
            if (best != target || !targetVisible) reactTimer = reaction * (0.7f + rng.nextFloat() * 0.6f);
            target = best;
            targetVisible = true;
            lastSeenX = best.x;
            lastSeenY = best.y;
            lastSeenTime = w.time;
        } else {
            targetVisible = false;
        }
    }

    private void aimAndShoot(World w, float dt, Gun g) {
        PlayerInput in = p.input;
        noiseTimer -= dt;
        if (noiseTimer <= 0) {
            noiseTimer = 0.25f + rng.nextFloat() * 0.4f;
            aimNoiseTarget = (rng.nextFloat() - 0.5f) * 2 * aimError;
        }
        aimNoise += (aimNoiseTarget - aimNoise) * Math.min(1, dt * 6);
        float desired;
        if (targetVisible && target != null) {
            float d = p.distTo(target);
            float speed = g != null && g.type.speed > 0 ? g.type.speed : 2000;
            float t = d / speed * lead;
            float tx = target.x + target.vx * t, ty = target.centerY() + target.vy * t;
            desired = MathUtils.atan2(ty - p.shoulderY(), tx - p.x) + aimNoise * (0.5f + Math.min(1, d / 800f));
            reactTimer -= dt;
            float diff = angleDiff(in.aim, desired);
            in.aim = rotate(in.aim, desired, turnSpeed * dt);
            float range = g != null ? Math.max(g.type.range * 0.92f, 150) : 400;
            if (g != null && g.type.kind == com.brigadajato.world.Projectile.Kind.SHELL) range = 700;
            boolean inRange = d < range;
            // rajadas: pequenas pausas para nao ser perfeito
            burstTimer -= dt;
            if (burstTimer <= 0) {
                resting = !resting && rng.nextFloat() < 0.5f;
                burstTimer = resting ? burstRest * (0.5f + rng.nextFloat()) : 0.4f + rng.nextFloat() * 0.9f;
            }
            if (reactTimer <= 0 && Math.abs(diff) < 0.22f && inRange && !resting) in.fire = true;
            // nao atira foguete colado em si mesmo
            if (g != null && g.type.isExplosive() && d < 110) in.fire = false;
            if (d < C.MELEE_RANGE + 10) in.press(PlayerInput.P_MELEE);
            grenadeTimer -= dt;
            if (grenadeTimer <= 0 && d > 160 && d < 560 && difficulty > 0) {
                int type = -1;
                if (p.health < 45 && p.smokes > 0) type = Player.NADE_SMOKE;
                else if (d < 420 && p.flashes > 0 && rng.nextFloat() < 0.35f) type = Player.NADE_FLASH;
                else if (p.stickies > 0 && rng.nextFloat() < 0.3f) type = Player.NADE_STICKY;
                else if (p.frags > 0) type = Player.NADE_FRAG;
                else if (p.mines > 0) type = Player.NADE_MINE;
                if (type >= 0) {
                    grenadeTimer = 6 + rng.nextFloat() * 6;
                    float dx = target.x - p.x, dy = target.centerY() - p.shoulderY();
                    in.aim = MathUtils.atan2(dy + d * 0.45f, dx);
                    p.grenadeType = type;
                    in.press(PlayerInput.P_GRENADE);
                }
            }
        } else if (boss != null) {
            float d = (float) Math.hypot(boss.x - p.x, boss.centerY() - p.shoulderY());
            desired = MathUtils.atan2(boss.centerY() - p.shoulderY(), boss.x - p.x) + aimNoise * 0.5f;
            float diff = angleDiff(in.aim, desired);
            in.aim = rotate(in.aim, desired, turnSpeed * dt);
            float range = g != null ? Math.max(g.type.range, 300) : 0;
            if (Math.abs(diff) < 0.3f && d < range + boss.halfW()) in.fire = true;
        } else if (blocked) {
            // quebra a caixa que fecha o caminho
            desired = MathUtils.atan2(blockY - p.shoulderY(), blockX - p.x);
            in.aim = rotate(in.aim, desired, turnSpeed * dt);
            float d = (float) Math.hypot(blockX - p.x, blockY - p.shoulderY());
            if (Math.abs(angleDiff(in.aim, desired)) < 0.25f) {
                if (g != null && !g.empty() && !g.type.isShield() && !(g.type.isExplosive() && d < 130)) in.fire = true;
                else if (d < C.MELEE_RANGE + 30) in.press(PlayerInput.P_MELEE);
            }
        } else {
            if (target != null && w.time - lastSeenTime < 2.5f) {
                desired = MathUtils.atan2(lastSeenY + 22 - p.shoulderY(), lastSeenX - p.x);
            } else {
                desired = p.input.moveX >= 0 ? 0.05f : MathUtils.PI - 0.05f;
                if (Math.abs(p.input.moveX) < 0.1f) desired = in.aim;
            }
            in.aim = rotate(in.aim, desired, turnSpeed * 0.6f * dt);
        }
    }

    private void navigate(World w, float gx, float gy, float dt) {
        PlayerInput in = p.input;
        repathTimer -= dt;
        float gdx = gx - pathGoalX, gdy = gy - pathGoalY;
        if (repathTimer <= 0 || path.size == 0 || gdx * gdx + gdy * gdy > 100 * 100) {
            repathTimer = 0.5f + rng.nextFloat() * 0.35f;
            w.pathfinder.find(p.x, p.y, gx, gy, path);
            pathGoalX = gx;
            pathGoalY = gy;
            pathIdx = 0;
        }
        float tx = gx, ty = gy;
        if (path.size > 0) {
            Pathfinder pf = w.pathfinder;
            while (pathIdx < path.size - 1) {
                float cx = pf.cellX(path.get(pathIdx)), cy = pf.cellY(path.get(pathIdx));
                if (Math.abs(cx - p.x) < 20 && Math.abs(cy - p.y) < 28) pathIdx++;
                else break;
            }
            int look = pathIdx;
            for (int k = pathIdx; k < Math.min(path.size, pathIdx + 7); k++) {
                if (w.lineOfSight(p.x, p.y + 22, pf.cellX(path.get(k)), pf.cellY(path.get(k)) + 22)) look = k;
                else break;
            }
            tx = pf.cellX(path.get(look));
            ty = pf.cellY(path.get(look));
            blocked = false;
            for (int k = pathIdx; k < Math.min(path.size, pathIdx + 3) && !blocked; k++) {
                int idx = path.get(k);
                int cx = idx % w.map.w, cy = idx / w.map.w;
                for (int up = 0; up <= 1; up++) {
                    int t = w.map.get(cx, cy + up);
                    if (t == GameMap.BREAKABLE) {
                        blocked = true;
                        blockX = cx * C.TILE + C.TILE / 2f;
                        blockY = (cy + up) * C.TILE + C.TILE / 2f;
                        tx = blockX;
                        ty = p.y;
                        break;
                    }
                    if (t == GameMap.DOOR) {
                        int lever = leverFor(w, cx + (cy + up) * w.map.w);
                        if (lever >= 0) {
                            Vector2 l = w.map.levers.get(lever);
                            if (w.nearLever(p) == lever) {
                                if (useCd <= 0) {
                                    in.use = true;
                                    useCd = 1.5f;
                                }
                            } else { // vai ate a alavanca
                                tx = l.x;
                                ty = l.y;
                            }
                        }
                        k = 99;
                        break;
                    }
                }
            }
        }
        float dx = tx - p.x, dy = ty - p.y;
        if (DEBUG) debug = "path=" + path.size + " idx=" + pathIdx + " wp=(" + (int) tx + "," + (int) ty + ") goal=(" + (int) gx + "," + (int) gy + ")";
        // centraliza com precisao quando o proximo ponto esta bem acima/abaixo (pocos estreitos)
        boolean vertical = Math.abs(dy) > 20;
        in.moveX = Math.abs(dx) < (vertical ? 2 : 6) ? 0 : MathUtils.clamp(dx / (vertical ? 8f : 28f), -1, 1);
        if (dy > 10) in.moveY = 1;
        else if (dy < -26 && p.onGround && standingOnPlatform(w)) in.moveY = -1;
        else if (!p.onGround && dy > -10 && p.vy < -60) in.moveY = 0.8f;
        else in.moveY = 0;
        if (p.onGround && Math.abs(in.moveX) > 0.3f) {
            float ahead = p.x + Math.signum(in.moveX) * (C.PLAYER_HW + 10);
            if (w.map.solidAt(ahead, p.y + 12) || w.map.solidAt(ahead, p.y + 36)) in.moveY = 1;
        }
        if (p.fuel < 5 && in.moveY > 0 && !p.onGround) in.moveY = 0;
        // no chao, espera o jetpack encher o bastante antes de uma subida grande
        float climb = Math.max(dy, goalClimb(w, gy));
        if (p.onGround && in.moveY > 0 && climb > 40 && p.fuel < Math.min(95f, 22f + climb * 0.3f)) {
            in.moveY = 0;
            if (climb > 60) in.moveX *= 0.2f;
        }

        // preso?
        stuckCheck -= dt;
        if (stuckCheck <= 0) {
            stuckCheck = 1.0f;
            float mdx = p.x - stuckX, mdy = p.y - stuckY;
            float gdist2 = (gx - p.x) * (gx - p.x) + (gy - p.y) * (gy - p.y);
            boolean wanted = Math.abs(in.moveX) > 0.3f || in.moveY > 0.5f || (gdist2 > 80 * 80 && p.fuel > 60);
            if (wanted && mdx * mdx + mdy * mdy < 12 * 12) {
                unstuckTimer = 0.7f;
                unstuckDir = rng.nextBoolean() ? 1 : -1;
                repathTimer = 0;
            }
            stuckX = p.x;
            stuckY = p.y;
        }
        if (unstuckTimer > 0) {
            unstuckTimer -= dt;
            in.moveX = unstuckDir;
            in.moveY = (p.onGround ? p.fuel > 45 : p.fuel > 8) ? 1 : 0;
        }
    }

    /** Alavanca mais perto do bot entre as que abrem essa porta. */
    private int leverFor(World w, int doorIdx) {
        int best = -1;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < w.map.leverDoors.size; i++) {
            if (!w.map.leverDoors.get(i).contains(doorIdx)) continue;
            com.badlogic.gdx.math.Vector2 l = w.map.levers.get(i);
            float d = Math.abs(l.x - p.x) + Math.abs(l.y - p.y) * 1.5f;
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    /** Quanto o caminho ainda sobe nos proximos waypoints (para economizar combustivel). */
    private float goalClimb(World w, float gy) {
        if (path.size == 0) return gy - p.y;
        float maxY = p.y;
        for (int k = pathIdx; k < Math.min(path.size, pathIdx + 10); k++) {
            float cy = w.pathfinder.cellY(path.get(k));
            if (cy > maxY) maxY = cy;
            // para quando o caminho volta a ter chao embaixo (pouso)
        }
        return maxY - p.y;
    }

    private boolean standingOnPlatform(World w) {
        int ty = (int) Math.floor((p.y - 1) / C.TILE);
        return w.map.get((int) Math.floor(p.x / C.TILE), ty) == GameMap.PLATFORM;
    }

    private void pickWander(World w) {
        wanderTimer = 6 + rng.nextFloat() * 6;
        int total = w.map.spawnsFree.size + w.map.weaponSpawns.size + w.map.powerSpawns.size;
        int r = rng.nextInt(Math.max(1, total));
        Vector2 v;
        if (r < w.map.spawnsFree.size) v = w.map.spawnsFree.get(r);
        else if (r < w.map.spawnsFree.size + w.map.weaponSpawns.size) v = w.map.weaponSpawns.get(r - w.map.spawnsFree.size);
        else if (w.map.powerSpawns.size > 0) v = w.map.powerSpawns.get(Math.min(w.map.powerSpawns.size - 1, r - w.map.spawnsFree.size - w.map.weaponSpawns.size));
        else v = w.map.spawnsFree.first();
        wanderX = v.x;
        wanderY = v.y;
    }

    private Pickup nearestItem(World w, Pickup.Type type, float maxDist) {
        Pickup best = null;
        float bd = maxDist;
        for (int i45_ = 0; i45_ < w.pickups.size; i45_++) {
            Pickup pk = w.pickups.get(i45_);
            if (!pk.active || pk.type != type) continue;
            if (type == Pickup.Type.WEAPON && p.has(pk.weapon)) continue;
            float d = dist(pk.x, pk.y);
            if (d < bd) {
                bd = d;
                best = pk;
            }
        }
        return best;
    }

    private void dummyThink(World w, float dt) {
        PlayerInput in = p.input;
        in.fire = false;
        dummyTimer -= dt;
        if (p.spawnIndex % 2 == 1) {
            if (dummyTimer <= 0) {
                dummyTimer = 1.5f + rng.nextFloat() * 1.5f;
                strafeDir = -strafeDir;
            }
            in.moveX = strafeDir * 0.6f;
            in.moveY = (p.spawnIndex % 4 == 3 && p.fuel > 30 && rng.nextFloat() < 0.5f) ? 1 : 0;
        } else {
            in.moveX = 0;
            in.moveY = 0;
        }
        Player near = null;
        float bd = 800;
        for (int i46_ = 0; i46_ < w.players.size; i46_++) {
            Player q = w.players.get(i46_);
            if (!q.alive || q.dummy) continue;
            float d = p.distTo(q);
            if (d < bd) {
                bd = d;
                near = q;
            }
        }
        if (near != null) in.aim = MathUtils.atan2(near.centerY() - p.shoulderY(), near.x - p.x);
    }

    private float dist(float x, float y) {
        float dx = x - p.x, dy = y - p.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    static float angleDiff(float a, float b) {
        float d = b - a;
        while (d > MathUtils.PI) d -= MathUtils.PI2;
        while (d < -MathUtils.PI) d += MathUtils.PI2;
        return d;
    }

    static float rotate(float a, float b, float maxStep) {
        float d = angleDiff(a, b);
        if (Math.abs(d) <= maxStep) return b;
        return a + Math.signum(d) * maxStep;
    }
}
