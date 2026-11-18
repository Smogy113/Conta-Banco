package com.brigadajato.world;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.brigadajato.world.ai.BotBrain;
import com.brigadajato.world.ai.Pathfinder;
import com.brigadajato.world.modes.GameMode;

import java.util.Random;

/**
 * Simulacao completa de uma partida. No host (ou offline) e autoritativa; em clientes de rede
 * apenas recebe estados (snapshots) e anima efeitos visuais.
 */
public class World {
    static {
        // permite lacos for-each aninhados nas listas do libGDX
        com.badlogic.gdx.utils.Collections.allocateIterators = true;
    }

    public final GameMap map;
    public final MatchConfig config;
    public final boolean authority;
    public final Random rng;
    public final Pathfinder pathfinder;
    /** gravidade do mapa */
    public final float gravity;

    public final Array<Player> players = new Array<>();
    public final Array<Projectile> projectiles = new Array<>();
    public final Array<Pickup> pickups = new Array<>();
    public final Array<WorldEvent> events = new Array<>();

    public GameMode mode;
    public float time;
    private int nextEntityId = 1;
    private int nextPlayerId = 0;

    // estatisticas usadas pelo modo treino
    public int statShots, statGrenades, statPickups, statSwaps, statMeleeHits, statZooms, statReloads;
    public float statJetTime, statWalk;

    public World(GameMap map, MatchConfig config, boolean authority, long seed) {
        this.map = map;
        this.config = config;
        this.authority = authority;
        this.rng = new Random(seed);
        this.pathfinder = new Pathfinder(map);
        this.gravity = C.GRAVITY * map.gravity;
        this.mode = GameMode.create(this, config.mode);
    }

    // ------------------------------------------------------------------ jogadores

    public Player addPlayer(String name, int team, Look look, boolean bot) {
        return addPlayerWithId(nextPlayerId++, name, team, look, bot);
    }

    public Player addPlayerWithId(int id, String name, int team, Look look, boolean bot) {
        nextPlayerId = Math.max(nextPlayerId, id + 1);
        Player p = new Player(id, name, team, look, bot);
        if (bot) p.bot = new BotBrain(p, config.difficulty, rng.nextLong());
        p.alive = false;
        p.respawnTimer = 0.2f + players.size * 0.05f;
        players.add(p);
        return p;
    }

    public void removePlayer(int id) {
        for (int i = 0; i < players.size; i++) {
            if (players.get(i).id == id) {
                Player p = players.get(i);
                if (p.alive) dropStuff(p);
                players.removeIndex(i);
                return;
            }
        }
    }

    public Player player(int id) {
        for (int i = 0; i < players.size; i++) if (players.get(i).id == id) return players.get(i);
        return null;
    }

    /** Escolhe o time com menos jogadores (modos em equipe). */
    public int balancedTeam() {
        if (!config.mode.teams) return C.TEAM_NONE;
        int r = 0, b = 0;
        for (int i7_ = 0; i7_ < players.size; i7_++) {
            Player p = players.get(i7_);
            if (p.team == C.TEAM_RED) r++;
            else if (p.team == C.TEAM_BLUE) b++;
        }
        return r <= b ? C.TEAM_RED : C.TEAM_BLUE;
    }

    public boolean sameTeam(Player a, Player b) {
        return a.team != C.TEAM_NONE && a.team == b.team;
    }

    /** a pode causar dano em b? */
    public boolean canHurt(int attackerId, int attackerTeam, Player b) {
        if (attackerId == b.id) return false;
        if (attackerTeam != C.TEAM_NONE && attackerTeam == b.team && !config.friendlyFire) return false;
        return true;
    }

    public boolean enemies(Player a, Player b) {
        if (a == b) return false;
        if (a.team == C.TEAM_NONE || b.team == C.TEAM_NONE) return true;
        return a.team != b.team;
    }

    // ------------------------------------------------------------------ inicio

    public void start() {
        mode.init();
        if (mode.usesMapItems()) spawnMapPickups();
    }

    private void spawnMapPickups() {
        int i = 0;
        for (int i8_ = 0; i8_ < map.weaponSpawns.size; i8_++) {
            Vector2 v = map.weaponSpawns.get(i8_);
            Pickup pk = newPickup(Pickup.Type.WEAPON, v.x, v.y);
            pk.respawnTime = 16;
            if (config.mode == ModeType.TRAINING) {
                pk.fixedWeapon = true;
                WeaponType[] all = WeaponType.values();
                pk.weapon = all[1 + (i % (all.length - 1))];
                pk.respawnTime = 3;
            } else {
                pk.weapon = WeaponType.random(rng);
            }
            i++;
        }
        for (int i9_ = 0; i9_ < map.healthSpawns.size; i9_++) {
            Vector2 v = map.healthSpawns.get(i9_); newPickup(Pickup.Type.HEALTH, v.x, v.y).respawnTime = 20;
        }
        for (int i10_ = 0; i10_ < map.grenadeSpawns.size; i10_++) {
            Vector2 v = map.grenadeSpawns.get(i10_); newPickup(Pickup.Type.GRENADES, v.x, v.y).respawnTime = 22;
        }
        for (int i11_ = 0; i11_ < map.powerSpawns.size; i11_++) {
            Vector2 v = map.powerSpawns.get(i11_);
            Pickup pk = newPickup(randomPower(), v.x, v.y);
            pk.randomPower = true;
            pk.respawnTime = 32;
        }
    }

    private Pickup.Type randomPower() {
        Pickup.Type[] t = {Pickup.Type.ARMOR, Pickup.Type.DAMAGE, Pickup.Type.JETPACK, Pickup.Type.SPEED};
        return t[rng.nextInt(t.length)];
    }

    private Pickup newPickup(Pickup.Type type, float x, float y) {
        Pickup pk = new Pickup();
        pk.id = nextEntityId++;
        pk.type = type;
        pk.x = x;
        pk.y = y;
        pk.bob = rng.nextFloat() * 6;
        pickups.add(pk);
        return pk;
    }

    // ------------------------------------------------------------------ eventos

    public WorldEvent emit(WorldEvent.Type type) {
        WorldEvent e = new WorldEvent(type);
        events.add(e);
        return e;
    }

    public void sound(String id, float x, float y) {
        WorldEvent e = emit(WorldEvent.Type.SOUND);
        e.text = id;
        e.x = x;
        e.y = y;
    }

    public void message(String key, int a, int b, int color) {
        WorldEvent e = emit(WorldEvent.Type.MESSAGE);
        e.text = key;
        e.a = a;
        e.b = b;
        e.c = color;
    }

    // ------------------------------------------------------------------ atualizacao

    public void update(float dt) {
        if (!authority) {
            clientUpdate(dt);
            return;
        }
        time += dt;
        for (int i = 0; i < players.size; i++) {
            Player p = players.get(i);
            if (p.bot != null && p.alive) p.bot.think(this, dt);
        }
        mode.preUpdate(dt);
        for (int i = 0; i < players.size; i++) {
            Player p = players.get(i);
            if (!p.alive) {
                if (mode.canRespawn(p)) {
                    p.respawnTimer -= dt;
                    if (p.respawnTimer <= 0) respawn(p);
                }
                p.input.clearPresses();
                continue;
            }
            updatePlayer(p, dt);
            p.input.clearPresses();
        }
        for (int i = projectiles.size - 1; i >= 0; i--) {
            Projectile pr = projectiles.get(i);
            updateProjectile(pr, dt);
            if (pr.dead) projectiles.removeIndex(i);
        }
        updatePickups(dt);
        mode.update(dt);
    }

    public void respawn(Player p) {
        Vector2 s = mode.chooseSpawn(p);
        p.resetForSpawn(s.x, s.y);
        mode.giveLoadout(p);
        if (p.bot != null) p.bot.onSpawn();
        WorldEvent e = emit(WorldEvent.Type.RESPAWN);
        e.a = p.id;
        e.x = p.x;
        e.y = p.y;
    }

    private void updatePlayer(Player p, float dt) {
        PlayerInput in = p.input;
        boolean frozen = mode.isFrozen(p);
        p.aim = in.aim;
        float ax = MathUtils.cos(p.aim);
        p.facing = ax >= 0 ? 1 : -1;
        p.spawnShield = Math.max(0, p.spawnShield - dt);
        p.dmgBoost = Math.max(0, p.dmgBoost - dt);
        p.jetBoost = Math.max(0, p.jetBoost - dt);
        p.speedBoost = Math.max(0, p.speedBoost - dt);
        p.meleeCooldown = Math.max(0, p.meleeCooldown - dt);
        p.grenadeCooldown = Math.max(0, p.grenadeCooldown - dt);
        p.dropTimer = Math.max(0, p.dropTimer - dt);
        p.meleeAnim = Math.max(0, p.meleeAnim - dt);
        p.hurtFlash = Math.max(0, p.hurtFlash - dt);
        p.recentlyFired = time - p.lastShotTime < 1.0f;
        if (p.spawnShield > 0 && (in.fire || in.pressed(PlayerInput.P_GRENADE))) p.spawnShield = 0;

        // agachar
        boolean wantCrouch = in.moveY < -0.55f && p.onGround && !frozen;
        if (wantCrouch && onPlatform(p)) {
            p.dropTimer = 0.25f; // desce da plataforma
            wantCrouch = false;
        }
        if (wantCrouch) p.crouching = true;
        else if (p.crouching && !overlapsSolid(p.x, p.y, C.PLAYER_HW, C.PLAYER_H)) p.crouching = false;

        // andar
        Gun g = p.gun();
        float speed = C.WALK_SPEED * (g != null ? g.type.moveMul : 1f) * (p.speedBoost > 0 ? 1.35f : 1f)
                * (p.crouching ? 0.45f : 1f) * mode.speedMul(p);
        float target = frozen ? 0 : MathUtils.clamp(in.moveX, -1, 1) * speed;
        float accel = p.onGround ? C.GROUND_ACCEL : C.AIR_ACCEL;
        p.vx = approach(p.vx, target, accel * dt);
        if (p.onGround && Math.abs(p.vx) > 20) {
            p.anim += dt * Math.abs(p.vx) / 38f;
            statWalk += Math.abs(p.vx) * dt;
        }

        // jetpack
        boolean wantJet = !frozen && in.moveY > 0.3f;
        if (p.fuel <= 0 && p.jetBoost <= 0) p.fuelLocked = true;
        if (p.fuelLocked && (p.fuel >= C.FUEL_UNLOCK || p.jetBoost > 0)) p.fuelLocked = false;
        if (wantJet && !p.fuelLocked) {
            float power = MathUtils.clamp((in.moveY - 0.3f) / 0.5f, 0.35f, 1f);
            if (p.vy < C.MAX_RISE) p.vy = Math.min(C.MAX_RISE, p.vy + C.JET_ACCEL * power * dt);
            if (p.jetBoost <= 0) p.fuel = Math.max(0, p.fuel - C.FUEL_USE * dt * (0.4f + 0.6f * power));
            p.jetting = true;
            p.fuelDelay = C.FUEL_REGEN_DELAY;
            p.crouching = false;
            statJetTime += dt;
        } else {
            p.jetting = false;
            if (p.fuelDelay > 0) p.fuelDelay -= dt;
            else p.fuel = Math.min(C.FUEL_MAX, p.fuel + (p.onGround ? C.FUEL_REGEN_GROUND : C.FUEL_REGEN_AIR) * dt);
        }
        p.vy -= gravity * dt;
        if (p.vy < -C.MAX_FALL) p.vy = -C.MAX_FALL;
        if (p.vx > 900) p.vx = 900;
        if (p.vx < -900) p.vx = -900;
        if (p.vy > 900) p.vy = 900;

        movePlayer(p, dt);

        // espinhos
        if (touchingSpikes(p)) {
            p.spikeTimer += dt;
            if (p.spikeTimer > 0.25f) {
                p.spikeTimer = 0;
                damage(p, C.SPIKE_DPS * 0.25f, null, C.KILL_SPIKES, false, 0, 220);
                if (!p.alive) return;
            }
        }

        // vida regenerando
        if (time - p.lastDamageTime > C.REGEN_DELAY && p.health < C.HEALTH_MAX)
            p.health = Math.min(C.HEALTH_MAX, p.health + C.REGEN_RATE * dt);

        if (frozen) {
            handleBuy(p);
            return;
        }

        // armas
        if (g != null) {
            if (p.reloadTimer > 0) {
                p.reloadTimer -= dt;
                if (p.reloadTimer <= 0) finishReload(p, g);
            }
            p.fireCooldown -= dt;
            if (in.pressed(PlayerInput.P_RELOAD) && g.mag < g.type.mag && g.reserve > 0 && p.reloadTimer <= 0)
                startReload(p, g);
            if (in.fire && p.fireCooldown <= 0 && p.reloadTimer <= 0) {
                if (g.mag > 0) {
                    fire(p, g);
                    if (!mode.infiniteAmmo(p)) g.mag--;
                    p.fireCooldown += 1f / g.type.rate;
                    if (p.fireCooldown < 0) p.fireCooldown = 1f / g.type.rate;
                    if (g.mag <= 0 && g.reserve > 0) startReload(p, g);
                } else if (g.reserve > 0) {
                    startReload(p, g);
                } else {
                    p.fireCooldown = 0.35f;
                    WorldEvent e = emit(WorldEvent.Type.EMPTY);
                    e.a = p.id;
                    e.x = p.x;
                    e.y = p.y;
                    Gun o = p.otherGun();
                    if (o != null && !o.empty()) swap(p);
                }
            }
            if (p.fireCooldown < 0) p.fireCooldown = 0;
        }
        if (in.pressed(PlayerInput.P_SWAP)) swap(p);
        if (in.pressed(PlayerInput.P_MELEE)) melee(p);
        if (in.pressed(PlayerInput.P_GRENADE_TYPE)) {
            p.grenadeType = 1 - p.grenadeType;
        }
        if (in.pressed(PlayerInput.P_GRENADE)) throwGrenade(p);
        if (in.pressed(PlayerInput.P_PICKUP)) manualPickup(p);
        if (in.pressed(PlayerInput.P_ZOOM)) statZooms++;
    }

    private void handleBuy(Player p) {
        if (p.input.buy >= 0) mode.buy(p, p.input.buy);
    }

    static float approach(float v, float target, float delta) {
        if (v < target) return Math.min(target, v + delta);
        return Math.max(target, v - delta);
    }

    // ------------------------------------------------------------------ fisica

    public boolean overlapsSolid(float x, float y, float hw, float h) {
        int x0 = (int) Math.floor((x - hw) / C.TILE), x1 = (int) Math.floor((x + hw - 0.01f) / C.TILE);
        int y0 = (int) Math.floor(y / C.TILE), y1 = (int) Math.floor((y + h - 0.01f) / C.TILE);
        for (int ty = y0; ty <= y1; ty++)
            for (int tx = x0; tx <= x1; tx++)
                if (map.solid(tx, ty)) return true;
        return false;
    }

    private boolean platformCross(float x, float hw, float oldY, float newY) {
        int ty = (int) Math.floor(newY / C.TILE);
        float top = (ty + 1) * C.TILE;
        if (oldY < top - 0.5f || newY >= top) return false;
        int x0 = (int) Math.floor((x - hw) / C.TILE), x1 = (int) Math.floor((x + hw - 0.01f) / C.TILE);
        for (int tx = x0; tx <= x1; tx++) if (map.get(tx, ty) == GameMap.PLATFORM) return true;
        return false;
    }

    private boolean onPlatform(Player p) {
        if (!p.onGround) return false;
        int ty = (int) Math.floor((p.y - 1) / C.TILE);
        int x0 = (int) Math.floor((p.x - C.PLAYER_HW) / C.TILE), x1 = (int) Math.floor((p.x + C.PLAYER_HW - 0.01f) / C.TILE);
        boolean plat = false;
        for (int tx = x0; tx <= x1; tx++) {
            int t = map.get(tx, ty);
            if (t == GameMap.SOLID || t == GameMap.SPIKES) return false;
            if (t == GameMap.PLATFORM) plat = true;
        }
        return plat;
    }

    private boolean touchingSpikes(Player p) {
        int x0 = (int) Math.floor((p.x - C.PLAYER_HW + 2) / C.TILE), x1 = (int) Math.floor((p.x + C.PLAYER_HW - 2) / C.TILE);
        int ty = (int) Math.floor((p.y - 2) / C.TILE);
        for (int tx = x0; tx <= x1; tx++) if (map.get(tx, ty) == GameMap.SPIKES) return true;
        return false;
    }

    private void movePlayer(Player p, float dt) {
        float hw = C.PLAYER_HW, h = p.height();
        float nx = p.x + p.vx * dt;
        if (overlapsSolid(nx, p.y, hw, h)) {
            if (p.vx > 0) nx = (float) Math.floor((nx + hw) / C.TILE) * C.TILE - hw - 0.01f;
            else if (p.vx < 0) nx = (float) (Math.floor((nx - hw) / C.TILE) + 1) * C.TILE + hw + 0.01f;
            if (overlapsSolid(nx, p.y, hw, h)) nx = p.x;
            p.vx = 0;
        }
        p.x = nx;
        float ny = p.y + p.vy * dt;
        p.onGround = false;
        if (p.vy <= 0) {
            boolean plat = p.dropTimer <= 0 && platformCross(p.x, hw, p.y, ny);
            if (overlapsSolid(p.x, ny, hw, h) || plat) {
                ny = (float) (Math.floor(ny / C.TILE) + 1) * C.TILE;
                if (overlapsSolid(p.x, ny, hw, h)) ny = p.y;
                if (p.vy < -650) {
                    WorldEvent e = emit(WorldEvent.Type.SOUND);
                    e.text = "step";
                    e.x = p.x;
                    e.y = p.y;
                }
                p.vy = 0;
                p.onGround = true;
            }
        } else if (overlapsSolid(p.x, ny, hw, h)) {
            ny = (float) Math.floor((ny + h) / C.TILE) * C.TILE - h - 0.01f;
            if (overlapsSolid(p.x, ny, hw, h)) ny = p.y;
            p.vy = 0;
        }
        p.y = ny;
        if (!p.onGround && p.vy <= 0) {
            if (overlapsSolid(p.x, p.y - 1.5f, hw, 1f) || (p.dropTimer <= 0 && platformCross(p.x, hw, p.y, p.y - 1.5f)))
                p.onGround = true;
        }
    }

    /**
     * Fracao (0..1) do segmento ate o primeiro bloco solido; 2 se livre. Percorre a grade bloco a
     * bloco (DDA), bem mais rapido que amostrar o segmento.
     */
    public float rayTile(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        int tx = (int) Math.floor(x0 / C.TILE), ty = (int) Math.floor(y0 / C.TILE);
        if (map.solid(tx, ty)) return 0f;
        int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0), stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        float tDeltaX = stepX != 0 ? C.TILE / Math.abs(dx) : Float.MAX_VALUE;
        float tDeltaY = stepY != 0 ? C.TILE / Math.abs(dy) : Float.MAX_VALUE;
        float tMaxX = stepX > 0 ? ((tx + 1) * C.TILE - x0) / dx : (stepX < 0 ? (tx * C.TILE - x0) / dx : Float.MAX_VALUE);
        float tMaxY = stepY > 0 ? ((ty + 1) * C.TILE - y0) / dy : (stepY < 0 ? (ty * C.TILE - y0) / dy : Float.MAX_VALUE);
        for (int guard = 0; guard < 4096; guard++) {
            float t;
            if (tMaxX < tMaxY) {
                t = tMaxX;
                tx += stepX;
                tMaxX += tDeltaX;
            } else {
                t = tMaxY;
                ty += stepY;
                tMaxY += tDeltaY;
            }
            if (t > 1f) return 2f;
            if (map.solid(tx, ty)) return Math.max(0f, t);
        }
        return 2f;
    }

    public boolean lineOfSight(float x0, float y0, float x1, float y1) {
        return rayTile(x0, y0, x1, y1) > 1f;
    }

    /** Intersecao segmento x caixa; retorna t em [0,1] ou -1. */
    public static float segAabb(float x0, float y0, float x1, float y1, float minX, float minY, float maxX, float maxY) {
        float tmin = 0, tmax = 1;
        float dx = x1 - x0, dy = y1 - y0;
        if (Math.abs(dx) < 1e-6f) {
            if (x0 < minX || x0 > maxX) return -1;
        } else {
            float t1 = (minX - x0) / dx, t2 = (maxX - x0) / dx;
            if (t1 > t2) {
                float s = t1;
                t1 = t2;
                t2 = s;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }
        if (Math.abs(dy) < 1e-6f) {
            if (y0 < minY || y0 > maxY) return -1;
        } else {
            float t1 = (minY - y0) / dy, t2 = (maxY - y0) / dy;
            if (t1 > t2) {
                float s = t1;
                t1 = t2;
                t2 = s;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }
        return tmin;
    }

    // ------------------------------------------------------------------ armas

    private void startReload(Player p, Gun g) {
        if (p.reloadTimer > 0 || g.reserve <= 0 || g.mag >= g.type.mag) return;
        p.reloadTimer = g.type.reload;
        statReloads++;
        WorldEvent e = emit(WorldEvent.Type.RELOAD);
        e.a = p.id;
        e.x = p.x;
        e.y = p.y;
    }

    private void finishReload(Player p, Gun g) {
        int need = g.type.mag - g.mag;
        int take = Math.min(need, g.reserve);
        g.mag += take;
        if (!mode.infiniteAmmo(p)) g.reserve -= take;
        p.reloadTimer = 0;
    }

    private void swap(Player p) {
        Gun o = p.otherGun();
        if (o == null) return;
        p.slot = 1 - p.slot;
        p.reloadTimer = 0;
        p.fireCooldown = Math.max(p.fireCooldown, 0.25f);
        statSwaps++;
    }

    private void fire(Player p, Gun g) {
        WeaponType w = g.type;
        float sx = p.x, sy = p.shoulderY();
        float dmgMul = p.dmgBoost > 0 ? 2f : 1f;
        p.lastShotTime = time;
        statShots++;
        float ax = MathUtils.cos(p.aim), ay = MathUtils.sin(p.aim);
        if (w.isLaser()) {
            laser(p, sx, sy, p.aim, w.damage * dmgMul, w.range);
        } else if (w.kind == Projectile.Kind.BULLET || w.kind == Projectile.Kind.FLAME) {
            float[] angles = new float[w.pellets];
            float spreadMul = p.crouching ? 0.55f : (p.onGround ? 1f : 1.25f);
            for (int i = 0; i < w.pellets; i++) {
                float sp = (rng.nextFloat() - 0.5f) * w.spreadDeg * MathUtils.degreesToRadians * spreadMul;
                angles[i] = p.aim + sp;
                Projectile pr = spawnBullet(p.id, p.team, w, sx, sy, angles[i], w.damage * dmgMul, false);
                if (w.kind == Projectile.Kind.FLAME) {
                    pr.vx += p.vx * 0.5f;
                    pr.vy += p.vy * 0.5f;
                    pr.life *= 0.75f + rng.nextFloat() * 0.4f;
                }
            }
            WorldEvent e = emit(WorldEvent.Type.SHOT);
            e.a = p.id;
            e.b = w.ordinal();
            e.x = sx;
            e.y = sy;
            e.angles = angles;
        } else {
            Projectile pr = new Projectile();
            pr.id = nextEntityId++;
            pr.kind = w.kind;
            pr.owner = p.id;
            pr.team = p.team;
            pr.weapon = w;
            pr.x = pr.startX = sx + ax * 10;
            pr.y = pr.startY = sy + ay * 10;
            pr.vx = ax * w.speed;
            pr.vy = ay * w.speed;
            pr.damage = w.damage * dmgMul;
            pr.life = w.kind == Projectile.Kind.SHELL ? 1.6f : w.lifetime();
            pr.fuse = 1.6f;
            projectiles.add(pr);
            WorldEvent e = emit(WorldEvent.Type.SHOT);
            e.a = p.id;
            e.b = w.ordinal();
            e.x = sx;
            e.y = sy;
            e.angles = new float[]{p.aim};
        }
        if (w.recoil > 0) {
            p.vx -= ax * w.recoil;
            if (!p.onGround) p.vy -= ay * w.recoil * 0.7f;
        }
    }

    public Projectile spawnBullet(int owner, int team, WeaponType w, float x, float y, float angle, float damage, boolean visual) {
        Projectile pr = new Projectile();
        pr.id = visual ? -1 : nextEntityId++;
        pr.kind = w.kind == Projectile.Kind.FLAME ? Projectile.Kind.FLAME : Projectile.Kind.BULLET;
        pr.owner = owner;
        pr.team = team;
        pr.weapon = w;
        pr.x = pr.startX = x;
        pr.y = pr.startY = y;
        pr.vx = MathUtils.cos(angle) * w.speed;
        pr.vy = MathUtils.sin(angle) * w.speed;
        pr.damage = damage;
        pr.life = w.lifetime();
        pr.visual = visual;
        pr.rot = rng.nextFloat() * 360;
        pr.headshotCapable = w == WeaponType.SNIPER || w == WeaponType.MAGNUM || w == WeaponType.RIFLE || w == WeaponType.PISTOL;
        projectiles.add(pr);
        return pr;
    }

    private void laser(Player p, float sx, float sy, float angle, float dmg, float range) {
        float ex = sx + MathUtils.cos(angle) * range, ey = sy + MathUtils.sin(angle) * range;
        float tt = rayTile(sx, sy, ex, ey);
        if (tt <= 1) {
            ex = sx + (ex - sx) * tt;
            ey = sy + (ey - sy) * tt;
        }
        for (int i = 0; i < players.size; i++) {
            Player q = players.get(i);
            if (!q.alive || !canHurt(p.id, p.team, q)) continue;
            float t = segAabb(sx, sy, ex, ey, q.x - C.PLAYER_HW, q.y, q.x + C.PLAYER_HW, q.y + q.height());
            if (t >= 0) damage(q, dmg, p, WeaponType.LASER.ordinal(), false, MathUtils.cos(angle) * 250, 80);
        }
        WorldEvent e = emit(WorldEvent.Type.LASER);
        e.a = p.id;
        e.x = sx;
        e.y = sy;
        e.x2 = ex;
        e.y2 = ey;
    }

    private void melee(Player p) {
        if (p.meleeCooldown > 0) return;
        p.meleeCooldown = C.MELEE_COOLDOWN;
        p.meleeAnim = 0.25f;
        WorldEvent e = emit(WorldEvent.Type.MELEE);
        e.a = p.id;
        e.x = p.x;
        e.y = p.centerY();
        Player best = null;
        float bestD = 1e9f;
        for (int i12_ = 0; i12_ < players.size; i12_++) {
            Player q = players.get(i12_);
            if (!q.alive || !canHurt(p.id, p.team, q)) continue;
            float dx = q.x - p.x, dy = q.centerY() - p.centerY();
            if (Math.abs(dy) > 40) continue;
            if (Math.signum(dx) != p.facing && Math.abs(dx) > 8) continue;
            float d = Math.abs(dx);
            if (d < C.MELEE_RANGE + C.PLAYER_HW && d < bestD) {
                bestD = d;
                best = q;
            }
        }
        if (best != null) {
            statMeleeHits++;
            damage(best, C.MELEE_DAMAGE * (p.dmgBoost > 0 ? 2 : 1), p, C.KILL_MELEE, false, p.facing * 420, 260);
        }
    }

    private void throwGrenade(Player p) {
        if (p.grenadeCooldown > 0) return;
        boolean mine = p.grenadeType == 1;
        if (mine && p.mines <= 0) mine = false;
        if (!mine && p.frags <= 0) {
            if (p.mines > 0) mine = true;
            else return;
        }
        p.grenadeCooldown = 0.6f;
        if (!mode.infiniteAmmo(p)) {
            if (mine) p.mines--;
            else p.frags--;
        }
        statGrenades++;
        Projectile pr = new Projectile();
        pr.id = nextEntityId++;
        pr.kind = mine ? Projectile.Kind.MINE : Projectile.Kind.FRAG;
        pr.owner = p.id;
        pr.team = p.team;
        float ax = MathUtils.cos(p.aim), ay = MathUtils.sin(p.aim);
        float sp = mine ? 480 : 720;
        pr.x = p.x + ax * 8;
        pr.y = p.shoulderY() + ay * 8;
        pr.vx = ax * sp + p.vx * 0.4f;
        pr.vy = ay * sp + p.vy * 0.3f + 90;
        pr.fuse = mine ? 45f : 2.4f;
        pr.damage = mine ? 90 : 100;
        pr.life = pr.fuse;
        projectiles.add(pr);
        if (mine) {
            // limite de minas por jogador
            int count = 0;
            for (int i = projectiles.size - 1; i >= 0; i--) {
                Projectile o = projectiles.get(i);
                if (o.kind == Projectile.Kind.MINE && o.owner == p.id && !o.dead) {
                    count++;
                    if (count > 3) o.dead = true;
                }
            }
        }
        WorldEvent e = emit(WorldEvent.Type.THROW);
        e.a = p.id;
        e.x = p.x;
        e.y = p.y;
    }

    // ------------------------------------------------------------------ projeteis

    private void updateProjectile(Projectile pr, float dt) {
        switch (pr.kind) {
            case BULLET:
            case FLAME:
                moveBullet(pr, dt);
                break;
            case ROCKET:
                moveRocket(pr, dt);
                break;
            case SHELL:
                moveBouncy(pr, dt, 0.5f, 0.7f, true);
                pr.fuse -= dt;
                if (pr.fuse <= 0) explodeProjectile(pr);
                break;
            case FRAG:
                moveBouncy(pr, dt, 0.45f, 0.65f, false);
                pr.fuse -= dt;
                if (pr.fuse <= 0) explodeProjectile(pr);
                break;
            case MINE:
                moveMine(pr, dt);
                break;
            case DISC:
                moveDisc(pr, dt);
                break;
        }
    }

    private Player hitPlayerOnSegment(Projectile pr, float x0, float y0, float x1, float y1, float[] outT) {
        Player hit = null;
        float best = 2;
        for (int i = 0; i < players.size; i++) {
            Player q = players.get(i);
            if (!q.alive || q.id == pr.owner) continue;
            if (!pr.visual && !canHurt(pr.owner, pr.team, q)) continue;
            if (pr.visual && pr.team != C.TEAM_NONE && pr.team == q.team && !config.friendlyFire) continue;
            if (q.id == pr.lastHit) continue;
            float t = segAabb(x0, y0, x1, y1, q.x - C.PLAYER_HW, q.y, q.x + C.PLAYER_HW, q.y + q.height());
            if (t >= 0 && t < best) {
                best = t;
                hit = q;
            }
        }
        outT[0] = best;
        return hit;
    }

    private final float[] tmpT = new float[1];

    private void moveBullet(Projectile pr, float dt) {
        if (pr.kind == Projectile.Kind.FLAME) {
            pr.vy += 140 * dt;
            pr.vx *= (1 - 1.2f * dt);
        }
        float nx = pr.x + pr.vx * dt, ny = pr.y + pr.vy * dt;
        float tileT = rayTile(pr.x, pr.y, nx, ny);
        Player hit = hitPlayerOnSegment(pr, pr.x, pr.y, nx, ny, tmpT);
        if (hit != null && tmpT[0] <= tileT) {
            float hx = pr.x + (nx - pr.x) * tmpT[0], hy = pr.y + (ny - pr.y) * tmpT[0];
            if (!pr.visual) {
                boolean head = pr.headshotCapable && hy > hit.y + hit.height() - 13;
                float dmg = pr.damage * (head ? (pr.weapon == WeaponType.SNIPER ? 2f : 1.5f) : 1f);
                float len = (float) Math.sqrt(pr.vx * pr.vx + pr.vy * pr.vy) + 1;
                float kb = pr.kind == Projectile.Kind.FLAME ? 30 : (pr.weapon == WeaponType.SNIPER ? 300 : 90);
                damage(hit, dmg, player(pr.owner), pr.weapon.ordinal(), head, pr.vx / len * kb, pr.vy / len * kb * 0.5f + 20);
            }
            pr.x = hx;
            pr.y = hy;
            pr.dead = true;
            return;
        }
        if (tileT <= 1) {
            pr.x += (nx - pr.x) * tileT;
            pr.y += (ny - pr.y) * tileT;
            pr.dead = true;
            if (pr.kind == Projectile.Kind.BULLET && !pr.visual) {
                WorldEvent e = emit(WorldEvent.Type.SPARK);
                e.x = pr.x;
                e.y = pr.y;
                e.f = MathUtils.atan2(pr.vy, pr.vx);
            }
            return;
        }
        pr.x = nx;
        pr.y = ny;
        pr.life -= dt;
        if (pr.life <= 0) pr.dead = true;
    }

    private void moveRocket(Projectile pr, float dt) {
        float speed = (float) Math.sqrt(pr.vx * pr.vx + pr.vy * pr.vy);
        if (speed < 1300) {
            float k = 1 + 0.9f * dt;
            pr.vx *= k;
            pr.vy *= k;
        }
        float nx = pr.x + pr.vx * dt, ny = pr.y + pr.vy * dt;
        float tileT = rayTile(pr.x, pr.y, nx, ny);
        Player hit = hitPlayerOnSegment(pr, pr.x, pr.y, nx, ny, tmpT);
        if (hit != null && tmpT[0] <= tileT) {
            pr.x += (nx - pr.x) * tmpT[0];
            pr.y += (ny - pr.y) * tmpT[0];
            explodeProjectile(pr);
            return;
        }
        if (tileT <= 1) {
            pr.x += (nx - pr.x) * Math.max(0, tileT - 0.05f);
            pr.y += (ny - pr.y) * Math.max(0, tileT - 0.05f);
            explodeProjectile(pr);
            return;
        }
        pr.x = nx;
        pr.y = ny;
        pr.life -= dt;
        if (pr.life <= 0) explodeProjectile(pr);
    }

    private void moveBouncy(Projectile pr, float dt, float bounce, float friction, boolean explodeOnPlayer) {
        pr.vy -= gravity * 0.85f * dt;
        boolean bounced = false;
        float nx = pr.x + pr.vx * dt;
        if (map.solidAt(nx, pr.y)) {
            pr.vx = -pr.vx * bounce;
            nx = pr.x;
            bounced = true;
        }
        float ny = pr.y + pr.vy * dt;
        boolean platHit = pr.vy < 0 && crossesPlatform(nx, pr.y, ny);
        if (map.solidAt(nx, ny) || platHit) {
            if (pr.vy < 0) pr.vx *= friction;
            pr.vy = -pr.vy * bounce;
            if (Math.abs(pr.vy) < 40) pr.vy = 0;
            ny = pr.y;
            bounced = true;
        }
        pr.x = nx;
        pr.y = ny;
        pr.rot += pr.vx * dt * 4;
        float sp = Math.abs(pr.vx) + Math.abs(pr.vy);
        if (bounced && sp > 120) {
            WorldEvent e = emit(WorldEvent.Type.BOUNCE);
            e.x = pr.x;
            e.y = pr.y;
        }
        if (explodeOnPlayer) {
            for (int i13_ = 0; i13_ < players.size; i13_++) {
                Player q = players.get(i13_);
                if (!q.alive || !canHurt(pr.owner, pr.team, q)) continue;
                if (Math.abs(q.x - pr.x) < C.PLAYER_HW + 4 && pr.y > q.y - 4 && pr.y < q.y + q.height() + 4) {
                    explodeProjectile(pr);
                    return;
                }
            }
        }
    }

    private boolean crossesPlatform(float x, float oldY, float newY) {
        int ty = (int) Math.floor(newY / C.TILE);
        float top = (ty + 1) * C.TILE;
        if (oldY < top || newY >= top) return false;
        return map.get((int) Math.floor(x / C.TILE), ty) == GameMap.PLATFORM;
    }

    private void moveMine(Projectile pr, float dt) {
        if (!pr.stuck) {
            pr.vy -= gravity * 0.85f * dt;
            float nx = pr.x + pr.vx * dt, ny = pr.y + pr.vy * dt;
            if (map.solidAt(nx, pr.y)) {
                pr.stuck = true;
                pr.rot = pr.vx > 0 ? 90 : -90;
            } else if (map.solidAt(nx, ny) || (pr.vy < 0 && crossesPlatform(nx, pr.y, ny))) {
                pr.stuck = true;
                pr.rot = pr.vy < 0 ? 0 : 180;
                pr.x = nx;
            } else {
                pr.x = nx;
                pr.y = ny;
            }
            if (pr.stuck) {
                pr.vx = pr.vy = 0;
                pr.life = 0.8f; // tempo para armar
            }
            return;
        }
        if (!pr.armed) {
            pr.life -= dt;
            if (pr.life <= 0) {
                pr.armed = true;
                pr.fuse = 45f;
                WorldEvent e = emit(WorldEvent.Type.SOUND);
                e.text = "arm";
                e.x = pr.x;
                e.y = pr.y;
            }
            return;
        }
        pr.fuse -= dt;
        if (pr.fuse <= 0) {
            explodeProjectile(pr);
            return;
        }
        for (int i14_ = 0; i14_ < players.size; i14_++) {
            Player q = players.get(i14_);
            if (!q.alive || !canHurt(pr.owner, pr.team, q)) continue;
            float dx = q.x - pr.x, dy = q.centerY() - pr.y;
            if (dx * dx + dy * dy < 48 * 48) {
                explodeProjectile(pr);
                return;
            }
        }
    }

    private void moveDisc(Projectile pr, float dt) {
        pr.rot += 900 * dt;
        float nx = pr.x + pr.vx * dt, ny = pr.y + pr.vy * dt;
        boolean bounced = false;
        if (map.solidAt(nx, pr.y)) {
            pr.vx = -pr.vx;
            nx = pr.x;
            bounced = true;
        }
        if (map.solidAt(nx, ny)) {
            pr.vy = -pr.vy;
            ny = pr.y;
            bounced = true;
        }
        Player hit = hitPlayerOnSegment(pr, pr.x, pr.y, nx, ny, tmpT);
        if (hit != null) {
            float len = (float) Math.sqrt(pr.vx * pr.vx + pr.vy * pr.vy) + 1;
            damage(hit, pr.damage, player(pr.owner), WeaponType.SAW.ordinal(), false, pr.vx / len * 200, 120);
            pr.lastHit = hit.id;
            pr.damage *= 0.7f;
            if (pr.damage < 20) pr.dead = true;
        }
        pr.x = nx;
        pr.y = ny;
        if (bounced) {
            pr.bounces++;
            WorldEvent e = emit(WorldEvent.Type.BOUNCE);
            e.x = pr.x;
            e.y = pr.y;
            e.a = 1;
            if (pr.bounces > 3) pr.dead = true;
        }
        pr.life -= dt;
        if (pr.life <= 0) pr.dead = true;
    }

    private void explodeProjectile(Projectile pr) {
        if (pr.dead) return;
        pr.dead = true;
        float radius;
        int code;
        switch (pr.kind) {
            case ROCKET:
                radius = 115;
                code = WeaponType.ROCKET.ordinal();
                break;
            case SHELL:
                radius = 95;
                code = WeaponType.GRENADE_LAUNCHER.ordinal();
                break;
            case MINE:
                radius = 105;
                code = C.KILL_MINE;
                break;
            default:
                radius = 125;
                code = C.KILL_FRAG;
                break;
        }
        explosion(pr.x, pr.y + 4, radius, pr.damage, pr.owner, pr.team, code);
    }

    public void explosion(float x, float y, float radius, float damage, int ownerId, int ownerTeam, int code) {
        WorldEvent e = emit(WorldEvent.Type.EXPLOSION);
        e.x = x;
        e.y = y;
        e.f = radius;
        Player owner = player(ownerId);
        for (int i = 0; i < players.size; i++) {
            Player q = players.get(i);
            if (!q.alive) continue;
            float dx = q.x - x, dy = q.centerY() - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > radius + 12) continue;
            float f = 1 - MathUtils.clamp(d / (radius + 12), 0, 1);
            float dmg = damage * (0.25f + 0.75f * f);
            if (!lineOfSight(x, y, q.x, q.centerY())) dmg *= 0.35f;
            float nx = d > 1 ? dx / d : 0, ny = d > 1 ? dy / d : 1;
            q.vx += nx * 700 * f;
            q.vy += ny * 600 * f + 250 * f;
            if (q.id == ownerId) dmg *= 0.55f;
            else if (!canHurt(ownerId, ownerTeam, q)) continue;
            damage(q, dmg, owner, code, false, 0, 0);
        }
        // reacao em cadeia com minas e granadas proximas
        for (int i = 0; i < projectiles.size; i++) {
            Projectile o = projectiles.get(i);
            if (o.dead || (o.kind != Projectile.Kind.MINE && o.kind != Projectile.Kind.FRAG)) continue;
            float dx = o.x - x, dy = o.y - y;
            if (dx * dx + dy * dy < radius * radius * 0.5f) {
                if (o.kind == Projectile.Kind.FRAG) o.fuse = Math.min(o.fuse, 0.15f);
                else {
                    o.stuck = true;
                    o.armed = true;
                    o.fuse = 0.12f;
                }
            }
        }
    }

    // ------------------------------------------------------------------ dano

    public void damage(Player victim, float amount, Player attacker, int code, boolean head, float kx, float ky) {
        if (!victim.alive || amount <= 0) return;
        if (victim.spawnShield > 0) return;
        if (victim.dummy && attacker == null) return;
        amount *= mode.damageMul(victim, attacker);
        if (victim.armor > 0) {
            float absorb = Math.min(victim.armor, amount * 0.6f);
            victim.armor -= absorb;
            amount -= absorb;
        }
        victim.health -= amount;
        victim.lastDamageTime = time;
        victim.hurtFlash = 0.15f;
        if (attacker != null && attacker != victim) victim.lastAttacker = attacker.id;
        victim.vx += kx;
        victim.vy += ky;
        WorldEvent e = emit(WorldEvent.Type.HIT);
        e.a = victim.id;
        e.b = attacker != null ? attacker.id : -1;
        e.x = victim.x;
        e.y = victim.centerY();
        e.f = amount;
        e.c = head ? 1 : 0;
        if (victim.bot != null && attacker != null) victim.bot.onDamaged(attacker);
        if (victim.health <= 0) kill(victim, attacker, code, head);
    }

    private void kill(Player victim, Player attacker, int code, boolean head) {
        victim.alive = false;
        victim.health = 0;
        victim.deaths++;
        victim.streak = 0;
        victim.respawnTimer = mode.respawnDelay(victim);
        if (attacker == null && victim.lastAttacker >= 0 && time - victim.lastDamageTime < 5) {
            attacker = player(victim.lastAttacker); // credito para quem empurrou nos espinhos
        }
        if (attacker != null && attacker != victim) {
            if (sameTeam(attacker, victim)) {
                attacker.score -= 1;
            } else {
                attacker.kills++;
                attacker.score += 1;
                attacker.streak++;
                attacker.money += 300;
                if (attacker.streak == 3 || attacker.streak == 5 || attacker.streak == 8 || attacker.streak == 12) {
                    WorldEvent s = emit(WorldEvent.Type.STREAK);
                    s.a = attacker.id;
                    s.b = attacker.streak;
                }
            }
        } else {
            victim.score -= 1;
            if (code < C.KILL_MELEE) code = C.KILL_SUICIDE;
        }
        dropStuff(victim);
        mode.onKill(victim, attacker);
        WorldEvent e = emit(WorldEvent.Type.KILL);
        e.a = attacker != null ? attacker.id : -1;
        e.b = victim.id;
        e.c = code;
        e.f = head ? 1 : 0;
        e.x = victim.x;
        e.y = victim.centerY();
        e.x2 = victim.vx;
        e.y2 = victim.vy;
        e.d = attacker != null && attacker != victim ? attacker.streak : 0;
    }

    private void dropStuff(Player p) {
        Gun g = p.guns[p.slot];
        if (g == null) g = p.guns[0];
        if (g != null && g.type != WeaponType.PISTOL && !g.empty() && mode.usesMapItems()) {
            Pickup pk = newPickup(Pickup.Type.WEAPON, p.x, p.centerY());
            pk.weapon = g.type;
            pk.mag = g.mag;
            pk.reserve = g.reserve;
            pk.dropped = true;
            pk.life = 18;
            pk.vy = 200;
        }
    }

    // ------------------------------------------------------------------ itens

    private void updatePickups(float dt) {
        for (int i = pickups.size - 1; i >= 0; i--) {
            Pickup pk = pickups.get(i);
            pk.bob += dt;
            if (pk.dropped) {
                pk.life -= dt;
                if (pk.life <= 0 || !pk.active) {
                    pickups.removeIndex(i);
                    continue;
                }
                if (!map.solidAt(pk.x, pk.y - 1) && !crossesPlatform(pk.x, pk.y, pk.y - 1)) {
                    pk.vy -= gravity * dt;
                    float ny = pk.y + pk.vy * dt;
                    if (map.solidAt(pk.x, ny) || (pk.vy < 0 && crossesPlatform(pk.x, pk.y, ny))) {
                        pk.y = (float) (Math.floor(ny / C.TILE) + 1) * C.TILE;
                        pk.vy = 0;
                    } else pk.y = ny;
                } else pk.vy = 0;
            } else if (!pk.active) {
                pk.respawnTimer -= dt;
                if (pk.respawnTimer <= 0) {
                    pk.active = true;
                    if (pk.type == Pickup.Type.WEAPON && !pk.fixedWeapon) pk.weapon = WeaponType.random(rng);
                    if (pk.randomPower) pk.type = randomPower();
                    pk.mag = pk.reserve = -1;
                }
                continue;
            }
            for (int j = 0; j < players.size; j++) {
                Player p = players.get(j);
                if (!p.alive || p.dummy) continue;
                float dx = p.x - pk.x, dy = p.centerY() - (pk.y + 12);
                if (dx * dx + dy * dy > C.PICKUP_RADIUS * C.PICKUP_RADIUS) continue;
                if (autoPickup(p, pk)) break;
            }
        }
    }

    private void consume(Pickup pk, Player p) {
        if (pk.dropped) pk.active = false;
        else {
            pk.active = false;
            pk.respawnTimer = pk.respawnTime;
        }
        statPickups++;
        WorldEvent e = emit(WorldEvent.Type.PICKUP);
        e.a = p.id;
        e.b = pk.type.ordinal();
        e.c = pk.weapon != null ? pk.weapon.ordinal() : -1;
        e.x = pk.x;
        e.y = pk.y;
    }

    private boolean autoPickup(Player p, Pickup pk) {
        switch (pk.type) {
            case HEALTH:
                if (p.health >= C.HEALTH_MAX) return false;
                p.health = Math.min(C.HEALTH_MAX, p.health + 50);
                break;
            case GRENADES:
                if (p.frags >= C.MAX_FRAGS && p.mines >= C.MAX_MINES) return false;
                p.frags = Math.min(C.MAX_FRAGS, p.frags + 2);
                p.mines = Math.min(C.MAX_MINES, p.mines + 1);
                break;
            case ARMOR:
                if (p.armor >= C.ARMOR_MAX) return false;
                p.armor = Math.min(C.ARMOR_MAX, p.armor + 60);
                break;
            case DAMAGE:
                p.dmgBoost = 12;
                break;
            case JETPACK:
                p.jetBoost = 12;
                break;
            case SPEED:
                p.speedBoost = 12;
                break;
            case WEAPON:
                for (Gun g : p.guns) {
                    if (g != null && g.type == pk.weapon) {
                        int cap = g.type.reserve * 2;
                        if (g.reserve >= cap) return false;
                        int add = pk.mag >= 0 ? pk.mag + pk.reserve : g.type.mag + g.type.reserve / 2;
                        g.reserve = Math.min(cap, g.reserve + add);
                        consume(pk, p);
                        return true;
                    }
                }
                if (p.guns[0] == null || p.guns[1] == null) {
                    takeWeapon(p, pk);
                    return true;
                }
                return false;
        }
        consume(pk, p);
        return true;
    }

    /** Pickup mais proximo de armas (para o botao "Pegar"). */
    public Pickup nearestWeaponPickup(Player p) {
        Pickup best = null;
        float bd = C.PICKUP_RADIUS * C.PICKUP_RADIUS * 1.6f;
        for (int i15_ = 0; i15_ < pickups.size; i15_++) {
            Pickup pk = pickups.get(i15_);
            if (!pk.active || pk.type != Pickup.Type.WEAPON) continue;
            if (p.has(pk.weapon)) continue;
            float dx = p.x - pk.x, dy = p.centerY() - (pk.y + 12);
            float d = dx * dx + dy * dy;
            if (d < bd) {
                bd = d;
                best = pk;
            }
        }
        return best;
    }

    private void manualPickup(Player p) {
        Pickup pk = nearestWeaponPickup(p);
        if (pk != null) takeWeapon(p, pk);
    }

    private void takeWeapon(Player p, Pickup pk) {
        int slot = p.guns[0] == null ? 0 : (p.guns[1] == null ? 1 : p.slot);
        Gun old = p.guns[slot];
        Gun g = pk.mag >= 0 ? new Gun(pk.weapon, pk.mag, pk.reserve) : new Gun(pk.weapon);
        p.guns[slot] = g;
        p.slot = slot;
        p.reloadTimer = 0;
        consume(pk, p);
        if (old != null && !old.empty() && mode.usesMapItems()) {
            Pickup d = newPickup(Pickup.Type.WEAPON, p.x, p.centerY());
            d.weapon = old.type;
            d.mag = old.mag;
            d.reserve = old.reserve;
            d.dropped = true;
            d.life = 18;
            d.vy = 150;
        }
    }

    // ------------------------------------------------------------------ cliente de rede

    /** Atualizacao visual em clientes: extrapola e suaviza o que veio do host. */
    private void clientUpdate(float dt) {
        time += dt;
        for (int i = 0; i < players.size; i++) {
            Player p = players.get(i);
            if (!p.alive) continue;
            p.netX += p.vx * dt;
            p.netY += p.vy * dt;
            float dx = p.netX - p.x, dy = p.netY - p.y;
            if (dx * dx + dy * dy > 160 * 160) {
                p.x = p.netX;
                p.y = p.netY;
            } else {
                float k = Math.min(1, dt * 16);
                p.x += dx * k;
                p.y += dy * k;
            }
            if (p.onGround && Math.abs(p.vx) > 20) p.anim += dt * Math.abs(p.vx) / 38f;
            p.meleeAnim = Math.max(0, p.meleeAnim - dt);
            p.hurtFlash = Math.max(0, p.hurtFlash - dt);
        }
        for (int i = projectiles.size - 1; i >= 0; i--) {
            Projectile pr = projectiles.get(i);
            if (pr.visual) {
                moveBullet(pr, dt);
            } else {
                if (pr.kind == Projectile.Kind.FRAG || pr.kind == Projectile.Kind.SHELL || (pr.kind == Projectile.Kind.MINE && !pr.stuck)) {
                    pr.vy -= gravity * 0.85f * dt;
                    float nx = pr.x + pr.vx * dt, ny = pr.y + pr.vy * dt;
                    if (!map.solidAt(nx, ny)) {
                        pr.x = nx;
                        pr.y = ny;
                    }
                } else if (pr.kind == Projectile.Kind.ROCKET || pr.kind == Projectile.Kind.DISC) {
                    pr.x += pr.vx * dt;
                    pr.y += pr.vy * dt;
                }
                if (pr.kind == Projectile.Kind.DISC) pr.rot += 900 * dt;
                else if (pr.kind != Projectile.Kind.MINE) pr.rot += pr.vx * dt * 4;
            }
            if (pr.dead) projectiles.removeIndex(i);
        }
        for (int i16_ = 0; i16_ < pickups.size; i16_++) {
            Pickup pk = pickups.get(i16_); pk.bob += dt;
        }
    }

    /** Em clientes: cria balas visuais a partir de um evento de tiro. */
    public void spawnVisualShot(WorldEvent e) {
        WeaponType w = WeaponType.of(e.b);
        if (w == null || e.angles == null) return;
        if (w.kind != Projectile.Kind.BULLET && w.kind != Projectile.Kind.FLAME) return;
        if (w.isLaser()) return;
        Player owner = player(e.a);
        int team = owner != null ? owner.team : 0;
        for (float ang : e.angles) {
            Projectile pr = spawnBullet(e.a, team, w, e.x, e.y, ang, 0, true);
            if (owner != null && w.kind == Projectile.Kind.FLAME) {
                pr.vx += owner.vx * 0.5f;
                pr.vy += owner.vy * 0.5f;
            }
        }
        if (owner != null) {
            owner.lastShotTime = time;
            owner.recentlyFired = true;
        }
    }

    public int newEntityId() {
        return nextEntityId++;
    }

    /** Pontuacao ordenada para o placar. */
    public Array<Player> ranking() {
        Array<Player> r = new Array<>(players);
        r.sort((a, b) -> {
            if (b.score != a.score) return b.score - a.score;
            if (b.kills != a.kills) return b.kills - a.kills;
            return a.deaths - b.deaths;
        });
        return r;
    }
}
