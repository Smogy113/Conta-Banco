package com.brigadajato.world;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
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
    public final Array<Prop> props = new Array<>();
    public final Array<Boss> bosses = new Array<>();
    /** alavancas ligadas */
    public final boolean[] leverOn;
    private final float[] leverCd;
    /** blocos que mudaram desde o inicio (indice -> tipo), para quem entra no meio */
    public final IntIntMap changedTiles = new IntIntMap();

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
        this.leverOn = new boolean[map.levers.size];
        this.leverCd = new float[map.levers.size];
        this.mode = GameMode.create(this, config.mode);
        for (int i = 0; i < map.barrels.size; i++) {
            Prop pr = new Prop();
            pr.id = nextEntityId++;
            pr.x = map.barrels.get(i).x;
            pr.y = map.barrels.get(i).y;
            props.add(pr);
        }
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

    public Boss boss(int id) {
        for (int i = 0; i < bosses.size; i++) if (bosses.get(i).id == id) return bosses.get(i);
        return null;
    }

    /** Escolhe o time com menos jogadores (modos em equipe). */
    public int balancedTeam() {
        if (!config.mode.teams) return C.TEAM_NONE;
        int r = 0, b = 0;
        for (int i = 0; i < players.size; i++) {
            Player p = players.get(i);
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

    /** Chefoes sao do time azul (inimigos dos humanos). */
    public boolean canHurtBoss(int attackerTeam) {
        return attackerTeam != C.TEAM_BLUE;
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
        for (int k = 0; k < map.weaponSpawns.size; k++) {
            Vector2 v = map.weaponSpawns.get(k);
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
        for (int k = 0; k < map.healthSpawns.size; k++) {
            Vector2 v = map.healthSpawns.get(k);
            newPickup(Pickup.Type.HEALTH, v.x, v.y).respawnTime = 20;
        }
        for (int k = 0; k < map.grenadeSpawns.size; k++) {
            Vector2 v = map.grenadeSpawns.get(k);
            newPickup(Pickup.Type.GRENADES, v.x, v.y).respawnTime = 22;
        }
        for (int k = 0; k < map.powerSpawns.size; k++) {
            Vector2 v = map.powerSpawns.get(k);
            Pickup pk = newPickup(randomPower(), v.x, v.y);
            pk.randomPower = true;
            pk.respawnTime = 32;
        }
    }

    private Pickup.Type randomPower() {
        Pickup.Type[] t = {Pickup.Type.ARMOR, Pickup.Type.DAMAGE, Pickup.Type.JETPACK, Pickup.Type.SPEED};
        return t[rng.nextInt(t.length)];
    }

    public Pickup newPickup(Pickup.Type type, float x, float y) {
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
        for (int i = bosses.size - 1; i >= 0; i--) {
            Boss b = bosses.get(i);
            b.update(this, dt);
            if (!b.alive) bosses.removeIndex(i);
        }
        for (int i = projectiles.size - 1; i >= 0; i--) {
            Projectile pr = projectiles.get(i);
            pr.age += dt;
            updateProjectile(pr, dt);
            if (pr.dead) projectiles.removeIndex(i);
        }
        updatePickups(dt);
        updateProps(dt);
        for (int i = 0; i < leverCd.length; i++) leverCd[i] = Math.max(0, leverCd[i] - dt);
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
        p.chill = Math.max(0, p.chill - dt * 0.45f);
        p.frozen = Math.max(0, p.frozen - dt);
        p.stun = Math.max(0, p.stun - dt);
        boolean disabled = p.frozen > 0 || p.stun > 0;
        boolean frozen = mode.isFrozen(p) || disabled;
        if (!disabled) p.aim = in.aim;
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
        p.hookCd = Math.max(0, p.hookCd - dt);
        p.rollCd = Math.max(0, p.rollCd - dt);
        p.flash = Math.max(0, p.flash - dt);
        p.emoteCd = Math.max(0, p.emoteCd - dt);
        p.emoteTimer = Math.max(0, p.emoteTimer - dt);
        p.recentlyFired = time - p.lastShotTime < 1.0f;
        if (p.spawnShield > 0 && (in.fire || in.pressed(PlayerInput.P_GRENADE))) p.spawnShield = 0;

        // emotes e dancas
        if (in.emote >= 0 && p.emoteCd <= 0 && !disabled) {
            p.emote = in.emote;
            p.emoteTimer = 2.4f;
            p.emoteCd = 1.2f;
            WorldEvent e = emit(WorldEvent.Type.EMOTE);
            e.a = p.id;
            e.b = in.emote;
            e.x = p.x;
            e.y = p.y;
        }
        boolean dancing = p.emoteTimer > 0 && p.emote >= C.EMOTE_DANCE_FIRST;
        if (dancing && (Math.abs(in.moveX) > 0.3f || in.moveY > 0.3f || in.fire)) {
            p.emoteTimer = 0;
            dancing = false;
        }

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
                * (p.crouching ? 0.45f : 1f) * mode.speedMul(p) * (p.chill > 0 ? 1 - Math.min(0.6f, p.chill * 0.6f) : 1f);
        float target = frozen ? 0 : MathUtils.clamp(in.moveX, -1, 1) * speed;
        float accel = p.onGround ? C.GROUND_ACCEL : C.AIR_ACCEL;
        if (p.frozen > 0) accel *= 0.15f; // escorrega no gelo
        // rolamento / esquiva rapida
        if (in.pressed(PlayerInput.P_ROLL) && p.rollCd <= 0 && !frozen && p.hookState == 0) {
            p.rollDir = Math.abs(in.moveX) > 0.2f ? (in.moveX > 0 ? 1 : -1) : p.facing;
            p.rollTimer = p.onGround ? 0.34f : 0.26f;
            p.rollCd = 1.1f;
            p.emoteTimer = 0;
            if (p.onGround) p.vy = Math.max(p.vy, 90);
            sound("roll", p.x, p.y);
        }
        if (p.rollTimer > 0) {
            p.rollTimer -= dt;
            float k = Math.max(0, p.rollTimer) / 0.34f;
            target = p.rollDir * (p.onGround ? 380 + 320 * k : 300 + 260 * k);
            accel = 9000;
            p.crouching = p.onGround;
        }
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
            if (p.jetBoost <= 0) p.fuel = Math.max(0, p.fuel - C.FUEL_USE * dt * (0.4f + 0.6f * power) * mode.fuelMul(p));
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
        updateHook(p, dt, wantJet);
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

        // alavancas (segurar "usar" perto)
        if (in.use && !p.prevUse && !disabled) tryLever(p);
        p.prevUse = in.use;

        if (frozen) {
            if (!disabled) handleBuy(p);
            return;
        }

        // gancho
        if (in.pressed(PlayerInput.P_HOOK)) {
            if (p.hookState == 0) shootHook(p);
            else releaseHook(p);
        }

        // armas
        if (g != null && !dancing) {
            if (p.reloadTimer > 0) {
                p.reloadTimer -= dt;
                if (p.reloadTimer <= 0) finishReload(p, g);
            }
            p.fireCooldown -= dt;
            if (in.pressed(PlayerInput.P_RELOAD) && g.mag < g.type.mag && g.reserve > 0 && p.reloadTimer <= 0)
                startReload(p, g);
            if (in.fire && p.fireCooldown <= 0 && p.reloadTimer <= 0) {
                if (g.type.isShield()) {
                    shieldBash(p);
                    p.fireCooldown = 1f / g.type.rate;
                } else if (g.mag > 0) {
                    fire(p, g);
                    if (!mode.infiniteAmmo(p)) g.mag--;
                    p.fireCooldown += 1f / g.type.rate;
                    if (p.fireCooldown < 0) p.fireCooldown = 1f / g.type.rate;
                    if (g.mag <= 0 && g.reserve > 0 && g.type != WeaponType.BOOMERANG) startReload(p, g);
                } else if (g.reserve > 0 && !(g.type == WeaponType.BOOMERANG && boomerangOut(p))) {
                    startReload(p, g);
                } else if (g.type != WeaponType.BOOMERANG || !boomerangOut(p)) {
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
            // proxima granada que o jogador tem (fragmentacao, mina, fumaca, flash, grudenta)
            for (int k = 1; k <= Player.NADE_TYPES; k++) {
                int t = (p.grenadeType + k) % Player.NADE_TYPES;
                if (p.nades(t) > 0 || k == Player.NADE_TYPES) {
                    p.grenadeType = t;
                    break;
                }
            }
        }
        if (in.pressed(PlayerInput.P_GRENADE) && mode.allowGrenades(p)) throwGrenade(p);
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

    // ------------------------------------------------------------------ gancho

    private void shootHook(Player p) {
        if (p.hookCd > 0) return;
        p.hookState = 1;
        p.hookX = p.x;
        p.hookY = p.shoulderY();
        p.hookVX = MathUtils.cos(p.aim) * C.HOOK_SPEED;
        p.hookVY = MathUtils.sin(p.aim) * C.HOOK_SPEED;
        p.hookTimer = 0;
        WorldEvent e = emit(WorldEvent.Type.HOOK);
        e.a = p.id;
        e.b = 0;
        e.x = p.x;
        e.y = p.y;
    }

    public void releaseHook(Player p) {
        if (p.hookState == 0) return;
        p.hookState = 0;
        p.hookCd = 0.6f;
    }

    private void updateHook(Player p, float dt, boolean jetting) {
        if (p.hookState == 1) {
            p.hookTimer += dt;
            float nx = p.hookX + p.hookVX * dt, ny = p.hookY + p.hookVY * dt;
            float t = rayTile(p.hookX, p.hookY, nx, ny);
            if (t <= 1) {
                p.hookX += (nx - p.hookX) * t;
                p.hookY += (ny - p.hookY) * t;
                p.hookState = 2;
                p.hookTimer = 0;
                WorldEvent e = emit(WorldEvent.Type.HOOK);
                e.a = p.id;
                e.b = 1;
                e.x = p.hookX;
                e.y = p.hookY;
                return;
            }
            p.hookX = nx;
            p.hookY = ny;
            float dx = p.hookX - p.x, dy = p.hookY - p.shoulderY();
            if (dx * dx + dy * dy > C.HOOK_RANGE * C.HOOK_RANGE) releaseHook(p);
        } else if (p.hookState == 2) {
            p.hookTimer += dt;
            float dx = p.hookX - p.x, dy = p.hookY - p.shoulderY();
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            // ancora quebrou?
            float ex = p.hookX + (d > 1 ? dx / d : 0) * 3, ey = p.hookY + (d > 1 ? dy / d : 0) * 3;
            if (d < 30 || p.hookTimer > 2.6f || !map.solidAt(ex, ey)) {
                releaseHook(p);
                return;
            }
            p.vx += dx / d * C.HOOK_PULL * dt;
            p.vy += dy / d * C.HOOK_PULL * dt + gravity * 0.7f * dt;
            float sp = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
            if (sp > 780) {
                p.vx *= 780 / sp;
                p.vy *= 780 / sp;
            }
        }
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
            if (map.solid(tx, ty)) return false;
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

    // ------------------------------------------------------------------ blocos, portas e barris

    /** Muda um bloco (avisa todo mundo e atualiza o caminho dos bots). */
    public void setTile(int tx, int ty, byte type) {
        if (tx < 0 || ty < 0 || tx >= map.w || ty >= map.h) return;
        int idx = tx + ty * map.w;
        if (map.tiles[idx] == type) return;
        map.tiles[idx] = type;
        changedTiles.put(idx, type);
        pathfinder.refresh(tx, ty);
        if (authority) {
            WorldEvent e = emit(WorldEvent.Type.TILE);
            e.a = tx;
            e.b = ty;
            e.c = type;
            e.x = tx * C.TILE + C.TILE / 2f;
            e.y = ty * C.TILE + C.TILE / 2f;
        }
    }

    /** Aplica uma mudanca vinda da rede (cliente). */
    public void applyTile(int idx, int type) {
        if (idx < 0 || idx >= map.tiles.length) return;
        map.tiles[idx] = (byte) type;
        changedTiles.put(idx, type);
        pathfinder.refresh(idx % map.w, idx / map.w);
    }

    public void damageTile(int tx, int ty, float dmg) {
        if (map.get(tx, ty) != GameMap.BREAKABLE) return;
        int idx = tx + ty * map.w;
        map.tileHp[idx] -= (short) Math.max(1, Math.round(dmg));
        if (map.tileHp[idx] <= 0) setTile(tx, ty, GameMap.EMPTY);
    }

    private void breakTilesAround(float x, float y, float radius, float damage) {
        int r = (int) Math.ceil(radius / C.TILE);
        int cx = (int) Math.floor(x / C.TILE), cy = (int) Math.floor(y / C.TILE);
        for (int ty = cy - r; ty <= cy + r; ty++)
            for (int tx = cx - r; tx <= cx + r; tx++) {
                if (map.get(tx, ty) != GameMap.BREAKABLE) continue;
                float dx = tx * C.TILE + C.TILE / 2f - x, dy = ty * C.TILE + C.TILE / 2f - y;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < radius) damageTile(tx, ty, damage * 2.2f * (1 - d / radius) + 10);
            }
    }

    private void tryLever(Player p) {
        for (int i = 0; i < map.levers.size; i++) {
            Vector2 l = map.levers.get(i);
            float dx = p.x - l.x, dy = p.y - l.y;
            if (dx * dx + dy * dy > 50 * 50 || leverCd[i] > 0) continue;
            toggleLever(i);
            return;
        }
    }

    public int nearLever(Player p) {
        for (int i = 0; i < map.levers.size; i++) {
            Vector2 l = map.levers.get(i);
            float dx = p.x - l.x, dy = p.y - l.y;
            if (dx * dx + dy * dy <= 50 * 50) return i;
        }
        return -1;
    }

    public void toggleLever(int i) {
        leverCd[i] = 0.8f;
        IntArray doors = map.leverDoors.get(i);
        // abre se a porta esta fechada (vale para alavancas dos dois lados)
        boolean open = doors.size > 0 ? map.tiles[doors.get(0)] == GameMap.DOOR : !leverOn[i];
        for (int k = 0; k < doors.size; k++) {
            int idx = doors.get(k);
            int tx = idx % map.w, ty = idx / map.w;
            if (!open && playerInside(tx, ty)) continue; // nao fecha em cima de ninguem
            setTile(tx, ty, open ? GameMap.DOOR_OPEN : GameMap.DOOR);
        }
        leverOn[i] = open;
        for (int k = 0; k < leverOn.length; k++) {
            if (k == i || doors.size == 0) continue;
            if (map.leverDoors.get(k).contains(doors.get(0))) {
                leverOn[k] = open;
                leverCd[k] = 0.8f;
            }
        }
        WorldEvent e = emit(WorldEvent.Type.LEVER);
        e.a = i;
        e.b = open ? 1 : 0;
        e.x = map.levers.get(i).x;
        e.y = map.levers.get(i).y;
    }

    private boolean playerInside(int tx, int ty) {
        float x0 = tx * C.TILE, y0 = ty * C.TILE;
        for (int i = 0; i < players.size; i++) {
            Player p = players.get(i);
            if (!p.alive) continue;
            if (p.x + C.PLAYER_HW > x0 && p.x - C.PLAYER_HW < x0 + C.TILE && p.y + p.height() > y0 && p.y < y0 + C.TILE) return true;
        }
        return false;
    }

    private void updateProps(float dt) {
        for (int i = 0; i < props.size; i++) {
            Prop pr = props.get(i);
            pr.hurtFlash = Math.max(0, pr.hurtFlash - dt);
            if (!pr.alive) {
                pr.respawn -= dt;
                if (pr.respawn <= 0) {
                    pr.alive = true;
                    pr.hp = Prop.HP;
                }
            }
        }
    }

    public void damageProp(Prop pr, float dmg, int attackerId) {
        if (!pr.alive) return;
        pr.hp -= dmg;
        pr.hurtFlash = 0.1f;
        if (attackerId >= 0) pr.lastAttacker = attackerId;
        if (pr.hp > 0) return;
        pr.alive = false;
        pr.respawn = 30f;
        WorldEvent e = emit(WorldEvent.Type.PROP);
        e.a = pr.id;
        e.x = pr.x;
        e.y = pr.y;
        Player owner = player(pr.lastAttacker);
        explosion(pr.x, pr.y + 16, 130, 95, pr.lastAttacker, owner != null ? owner.team : 0, C.KILL_BARREL);
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

    private boolean boomerangOut(Player p) {
        for (int i = 0; i < projectiles.size; i++) {
            Projectile pr = projectiles.get(i);
            if (pr.kind == Projectile.Kind.BOOMERANG && pr.owner == p.id && !pr.dead) return true;
        }
        return false;
    }

    private void fire(Player p, Gun g) {
        WeaponType w = g.type;
        float sx = p.x, sy = p.shoulderY();
        if (w.dual) {
            p.dualFlip = !p.dualFlip;
            sy += p.dualFlip ? 3 : -3;
        }
        float dmgMul = (p.dmgBoost > 0 ? 2f : 1f) * mode.damageDealtMul(p);
        p.lastShotTime = time;
        statShots++;
        float ax = MathUtils.cos(p.aim), ay = MathUtils.sin(p.aim);
        if (w == WeaponType.LASER) {
            laserBeam(sx, sy, p.aim, w.damage * dmgMul, w.range, p.id, p.team, w.ordinal());
        } else if (w.isTesla()) {
            tesla(p, sx, sy, w.damage * dmgMul);
        } else if (w.kind == Projectile.Kind.BULLET || w.kind == Projectile.Kind.FLAME || w.kind == Projectile.Kind.FROST
                || w.kind == Projectile.Kind.ARROW) {
            float[] angles = new float[w.pellets];
            float spreadMul = p.crouching ? 0.55f : (p.onGround ? 1f : 1.25f);
            for (int i = 0; i < w.pellets; i++) {
                float sp = (rng.nextFloat() - 0.5f) * w.spreadDeg * MathUtils.degreesToRadians * spreadMul;
                angles[i] = p.aim + sp;
                Projectile pr = spawnBullet(p.id, p.team, w, sx, sy, angles[i], w.damage * dmgMul, false);
                if (w.kind == Projectile.Kind.FLAME || w.kind == Projectile.Kind.FROST) {
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
            if (w.kind == Projectile.Kind.BOOMERANG) pr.life = 3.5f;
            if (w.kind == Projectile.Kind.VORTEX) pr.life = 0.9f;
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
        Projectile.Kind k = w.kind;
        if (k != Projectile.Kind.FLAME && k != Projectile.Kind.FROST && k != Projectile.Kind.ARROW) k = Projectile.Kind.BULLET;
        pr.kind = k;
        pr.owner = owner;
        pr.team = team;
        pr.weapon = w;
        pr.x = pr.startX = x;
        pr.y = pr.startY = y;
        float speed = w.speed > 0 ? w.speed : 1500;
        pr.vx = MathUtils.cos(angle) * speed;
        pr.vy = MathUtils.sin(angle) * speed;
        pr.damage = damage;
        pr.life = w.speed > 0 ? w.lifetime() : 0.7f;
        pr.visual = visual;
        pr.rot = rng.nextFloat() * 360;
        pr.headshotCapable = w == WeaponType.SNIPER || w == WeaponType.MAGNUM || w == WeaponType.RIFLE || w == WeaponType.PISTOL
                || w == WeaponType.BOW || w == WeaponType.DUAL_PISTOLS;
        projectiles.add(pr);
        return pr;
    }

    // ---- tiros dos chefoes
    public void bossShot(Boss b, WeaponType w, float x, float y, float angle, float damage) {
        Projectile pr = spawnBullet(C.BOSS_OWNER, C.TEAM_BLUE, w, x, y, angle, damage, false);
        pr.life = 1.4f;
        WorldEvent e = emit(WorldEvent.Type.SHOT);
        e.a = C.BOSS_OWNER;
        e.b = w.ordinal();
        e.x = x;
        e.y = y;
        e.angles = new float[]{angle};
    }

    public void bossRocket(Boss b, float x, float y, float angle, float damage) {
        Projectile pr = new Projectile();
        pr.id = nextEntityId++;
        pr.kind = Projectile.Kind.ROCKET;
        pr.owner = C.BOSS_OWNER;
        pr.team = C.TEAM_BLUE;
        pr.weapon = WeaponType.ROCKET;
        pr.x = pr.startX = x;
        pr.y = pr.startY = y;
        pr.vx = MathUtils.cos(angle) * 520;
        pr.vy = MathUtils.sin(angle) * 520;
        pr.damage = damage;
        pr.life = 4f;
        projectiles.add(pr);
        sound("rocket", x, y);
    }

    public void bossStomp(Boss b) {
        WorldEvent e = emit(WorldEvent.Type.BOSS);
        e.a = b.id;
        e.b = 3;
        e.x = b.x;
        e.y = b.y;
        explosion(b.x, b.y + 10, 170, 38, C.BOSS_OWNER, C.TEAM_BLUE, C.KILL_BOSS);
    }

    public void robotLanding(Boss b) {
        WorldEvent e = emit(WorldEvent.Type.BOSS);
        e.a = b.id;
        e.b = 4;
        e.x = b.x;
        e.y = b.y;
    }

    public Boss spawnBoss(int type, float hp) {
        Boss b = new Boss();
        b.id = nextEntityId++;
        b.type = type;
        b.hp = b.maxHp = hp;
        float bestX = map.worldW() / 2, bestY = map.worldH() * 0.6f;
        if (type == Boss.ROBOT) {
            // nasce no chao num ponto de spawn
            Vector2 s = map.spawnsFree.get(rng.nextInt(map.spawnsFree.size));
            bestX = s.x;
            bestY = s.y + 40;
        } else bestY = map.worldH() - 220;
        b.x = bestX;
        b.y = bestY;
        bosses.add(b);
        WorldEvent e = emit(WorldEvent.Type.BOSS);
        e.a = b.id;
        e.b = 0;
        e.c = type;
        e.x = b.x;
        e.y = b.y;
        return b;
    }

    public void damageBoss(Boss b, float dmg, int attackerId) {
        if (!b.alive || dmg <= 0) return;
        b.hp -= dmg;
        b.hurtFlash = 0.08f;
        Player a = player(attackerId);
        if (a != null) a.score += 0; // pontos so no abate
        WorldEvent h = emit(WorldEvent.Type.HIT);
        h.a = -1;
        h.b = attackerId;
        h.x = b.x;
        h.y = b.centerY();
        h.f = dmg;
        if (b.hp > 0) return;
        b.alive = false;
        if (a != null) {
            a.score += 10;
            a.kills += 1;
        }
        WorldEvent e = emit(WorldEvent.Type.BOSS);
        e.a = b.id;
        e.b = 1;
        e.c = b.type;
        e.x = b.x;
        e.y = b.centerY();
        for (int i = 0; i < 3; i++) {
            WorldEvent x = emit(WorldEvent.Type.EXPLOSION);
            x.x = b.x + (rng.nextFloat() - 0.5f) * b.halfW() * 1.6f;
            x.y = b.centerY() + (rng.nextFloat() - 0.5f) * b.height();
            x.f = 150;
        }
    }

    private boolean shieldBlocks(Player q, float dirX) {
        Gun g = q.gun();
        return g != null && g.type.isShield() && dirX * q.facing < 0 && q.frozen <= 0 && q.stun <= 0;
    }

    private void blockEffect(Player q, float x, float y, float kx) {
        q.vx += kx * 0.25f;
        WorldEvent e = emit(WorldEvent.Type.BLOCK);
        e.a = q.id;
        e.x = x;
        e.y = y;
    }

    /** Raio continuo (laser do jogador ou do robo). Atravessa jogadores; escudo para o raio. */
    public void laserBeam(float sx, float sy, float angle, float dmg, float range, int ownerId, int team, int code) {
        float ex = sx + MathUtils.cos(angle) * range, ey = sy + MathUtils.sin(angle) * range;
        float tt = rayTile(sx, sy, ex, ey);
        if (tt <= 1) {
            float hx = sx + (ex - sx) * tt, hy = sy + (ey - sy) * tt;
            damageTile((int) Math.floor((hx + MathUtils.cos(angle) * 3) / C.TILE), (int) Math.floor((hy + MathUtils.sin(angle) * 3) / C.TILE), dmg);
            ex = hx;
            ey = hy;
        }
        // escudo mais proximo corta o raio
        float cut = 2;
        Player blocker = null;
        float dirX = MathUtils.cos(angle);
        for (int i = 0; i < players.size; i++) {
            Player q = players.get(i);
            if (!q.alive || q.id == ownerId || !shieldBlocks(q, dirX)) continue;
            float t = segAabb(sx, sy, ex, ey, q.x - C.PLAYER_HW, q.y, q.x + C.PLAYER_HW, q.y + q.height());
            if (t >= 0 && t < cut) {
                cut = t;
                blocker = q;
            }
        }
        if (blocker != null) {
            ex = sx + (ex - sx) * cut;
            ey = sy + (ey - sy) * cut;
            blockEffect(blocker, ex, ey, dirX * 200);
        }
        Player owner = player(ownerId);
        for (int i = 0; i < players.size; i++) {
            Player q = players.get(i);
            if (!q.alive || q == blocker || !canHurt(ownerId, team, q)) continue;
            float t = segAabb(sx, sy, ex, ey, q.x - C.PLAYER_HW, q.y, q.x + C.PLAYER_HW, q.y + q.height());
            if (t >= 0) damage(q, dmg, owner, ownerId == C.BOSS_OWNER ? C.KILL_BOSS : code, false, dirX * 250, 80);
        }
        if (canHurtBoss(team)) for (int i = 0; i < bosses.size; i++) {
            Boss b = bosses.get(i);
            if (b.alive && segAabb(sx, sy, ex, ey, b.x - b.halfW(), b.y, b.x + b.halfW(), b.y + b.height()) >= 0) damageBoss(b, dmg, ownerId);
        }
        for (int i = 0; i < props.size; i++) {
            Prop pr = props.get(i);
            if (pr.alive && segAabb(sx, sy, ex, ey, pr.x - Prop.W / 2, pr.y, pr.x + Prop.W / 2, pr.y + Prop.H) >= 0) damageProp(pr, dmg, ownerId);
        }
        WorldEvent e = emit(WorldEvent.Type.LASER);
        e.a = ownerId;
        e.x = sx;
        e.y = sy;
        e.x2 = ex;
        e.y2 = ey;
    }

    /** Raio em cadeia: acerta o inimigo na mira e pula para ate 3 vizinhos. */
    private void tesla(Player p, float sx, float sy, float dmg) {
        FloatArrayHolder pts = new FloatArrayHolder();
        pts.add(sx, sy);
        float best = Float.MAX_VALUE;
        Player first = null;
        Boss firstBoss = null;
        float range = WeaponType.TESLA.range;
        for (int i = 0; i < players.size; i++) {
            Player q = players.get(i);
            if (!q.alive || !canHurt(p.id, p.team, q)) continue;
            float dx = q.x - sx, dy = q.centerY() - sy;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > range) continue;
            float diff = Math.abs(angleDiff(p.aim, MathUtils.atan2(dy, dx)));
            if (diff > 0.5f || !lineOfSight(sx, sy, q.x, q.centerY())) continue;
            float score = d + diff * 300;
            if (score < best) {
                best = score;
                first = q;
            }
        }
        if (canHurtBoss(p.team)) for (int i = 0; i < bosses.size; i++) {
            Boss b = bosses.get(i);
            if (!b.alive) continue;
            float dx = b.x - sx, dy = b.centerY() - sy;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float diff = Math.abs(angleDiff(p.aim, MathUtils.atan2(dy, dx)));
            if (d < range && diff < 0.6f && d + diff * 300 < best) {
                best = d + diff * 300;
                firstBoss = b;
                first = null;
            }
        }
        if (firstBoss != null) {
            pts.add(firstBoss.x, firstBoss.centerY());
            damageBoss(firstBoss, dmg * 1.2f, p.id);
        } else if (first != null) {
            Player cur = first;
            IntArray hit = new IntArray();
            float d = dmg;
            for (int jump = 0; jump < 4 && cur != null; jump++) {
                pts.add(cur.x, cur.centerY());
                hit.add(cur.id);
                if (jump == 0 && shieldBlocks(cur, cur.x - sx)) {
                    blockEffect(cur, cur.x, cur.centerY(), 0);
                    break;
                }
                damage(cur, d, p, WeaponType.TESLA.ordinal(), false, 0, 60);
                cur.stun = Math.max(cur.stun, 0.25f);
                d *= 0.75f;
                Player next = null;
                float bd = 240;
                for (int i = 0; i < players.size; i++) {
                    Player q = players.get(i);
                    if (!q.alive || hit.contains(q.id) || !canHurt(p.id, p.team, q)) continue;
                    float dx = q.x - cur.x, dy = q.centerY() - cur.centerY();
                    float dd = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dd < bd && lineOfSight(cur.x, cur.centerY(), q.x, q.centerY())) {
                        bd = dd;
                        next = q;
                    }
                }
                cur = next;
            }
        } else {
            float ex = sx + MathUtils.cos(p.aim) * range * 0.8f, ey = sy + MathUtils.sin(p.aim) * range * 0.8f;
            float t = rayTile(sx, sy, ex, ey);
            if (t <= 1) {
                ex = sx + (ex - sx) * t;
                ey = sy + (ey - sy) * t;
            }
            pts.add(ex, ey);
            for (int i = 0; i < props.size; i++) {
                Prop pr = props.get(i);
                if (pr.alive && segAabb(sx, sy, ex, ey, pr.x - Prop.W / 2, pr.y, pr.x + Prop.W / 2, pr.y + Prop.H) >= 0) damageProp(pr, dmg, p.id);
            }
        }
        WorldEvent e = emit(WorldEvent.Type.LIGHTNING);
        e.a = p.id;
        e.x = sx;
        e.y = sy;
        e.angles = pts.toArray();
    }

    /** Lista simples de floats (pontos do raio). */
    private static final class FloatArrayHolder {
        private final com.badlogic.gdx.utils.FloatArray a = new com.badlogic.gdx.utils.FloatArray();

        void add(float x, float y) {
            a.add(x);
            a.add(y);
        }

        float[] toArray() {
            return a.toArray();
        }
    }

    static float angleDiff(float a, float b) {
        float d = b - a;
        while (d > MathUtils.PI) d -= MathUtils.PI2;
        while (d < -MathUtils.PI) d += MathUtils.PI2;
        return d;
    }

    private void melee(Player p) {
        meleeAttack(p, C.MELEE_RANGE, C.MELEE_DAMAGE * (p.zombie ? 1.35f : 1f), 420, C.KILL_MELEE);
    }

    private void shieldBash(Player p) {
        meleeAttack(p, C.MELEE_RANGE + 10, WeaponType.SHIELD.damage, 650, C.KILL_MELEE);
    }

    private void meleeAttack(Player p, float range, float baseDmg, float knock, int code) {
        if (p.meleeCooldown > 0) return;
        p.meleeCooldown = C.MELEE_COOLDOWN;
        p.meleeAnim = 0.25f;
        p.emoteTimer = 0;
        WorldEvent e = emit(WorldEvent.Type.MELEE);
        e.a = p.id;
        e.x = p.x;
        e.y = p.centerY();
        float dmg = baseDmg * (p.dmgBoost > 0 ? 2 : 1) * mode.damageDealtMul(p);
        Player best = null;
        float bestD = 1e9f;
        for (int i = 0; i < players.size; i++) {
            Player q = players.get(i);
            if (!q.alive || !canHurt(p.id, p.team, q)) continue;
            float dx = q.x - p.x, dy = q.centerY() - p.centerY();
            if (Math.abs(dy) > 40) continue;
            if (Math.signum(dx) != p.facing && Math.abs(dx) > 8) continue;
            float d = Math.abs(dx);
            if (d < range + C.PLAYER_HW && d < bestD) {
                bestD = d;
                best = q;
            }
        }
        if (best != null) {
            statMeleeHits++;
            float mul = shieldBlocks(best, p.facing) ? 0.3f : 1f;
            if (mul < 1) blockEffect(best, best.x, best.centerY(), p.facing * 300);
            damage(best, dmg * mul, p, code, false, p.facing * knock, 260);
            return;
        }
        if (canHurtBoss(p.team)) for (int i = 0; i < bosses.size; i++) {
            Boss b = bosses.get(i);
            if (b.alive && Math.abs(b.x - p.x) < b.halfW() + range && p.centerY() > b.y - 10 && p.centerY() < b.y + b.height() + 10) {
                damageBoss(b, dmg, p.id);
                return;
            }
        }
        for (int i = 0; i < props.size; i++) {
            Prop pr = props.get(i);
            if (pr.alive && Math.abs(pr.x - p.x) < range && Math.abs(pr.y + 16 - p.centerY()) < 34) {
                damageProp(pr, dmg, p.id);
                return;
            }
        }
        // soco quebra caixas
        damageTile((int) Math.floor((p.x + p.facing * (C.PLAYER_HW + 12)) / C.TILE), (int) Math.floor(p.centerY() / C.TILE), dmg * 0.8f);
    }

    private void throwGrenade(Player p) {
        if (p.grenadeCooldown > 0) return;
        int type = p.grenadeType;
        if (p.nades(type) <= 0) {
            type = -1;
            for (int t = 0; t < Player.NADE_TYPES; t++) if (p.nades(t) > 0) {
                type = t;
                break;
            }
            if (type < 0) return;
        }
        p.grenadeCooldown = 0.6f;
        if (!mode.infiniteAmmo(p)) p.addNades(type, -1);
        statGrenades++;
        boolean mine = type == Player.NADE_MINE;
        Projectile pr = new Projectile();
        pr.id = nextEntityId++;
        switch (type) {
            case Player.NADE_MINE: pr.kind = Projectile.Kind.MINE; break;
            case Player.NADE_SMOKE: pr.kind = Projectile.Kind.SMOKE; break;
            case Player.NADE_FLASH: pr.kind = Projectile.Kind.FLASH; break;
            case Player.NADE_STICKY: pr.kind = Projectile.Kind.STICKY; break;
            default: pr.kind = Projectile.Kind.FRAG; break;
        }
        pr.owner = p.id;
        pr.team = p.team;
        float ax = MathUtils.cos(p.aim), ay = MathUtils.sin(p.aim);
        float sp = mine ? 480 : 720;
        pr.x = p.x + ax * 8;
        pr.y = p.shoulderY() + ay * 8;
        pr.vx = ax * sp + p.vx * 0.4f;
        pr.vy = ay * sp + p.vy * 0.3f + 90;
        pr.fuse = mine ? 45f : (type == Player.NADE_SMOKE ? 1.2f : (type == Player.NADE_FLASH ? 1.3f : (type == Player.NADE_STICKY ? 2.2f : 2.4f)));
        pr.damage = mine ? 90 : (type == Player.NADE_STICKY ? 110 : 100);
        pr.life = pr.fuse;
        projectiles.add(pr);
        if (mine) {
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

    /** A linha (x0,y0)-(x1,y1) passa por dentro de uma nuvem de fumaca? (bots nao enxergam) */
    public boolean smokeBlocks(float x0, float y0, float x1, float y1) {
        for (int i = 0; i < projectiles.size; i++) {
            Projectile pr = projectiles.get(i);
            if (pr.kind != Projectile.Kind.SMOKE || !pr.armed) continue;
            float r = smokeRadius(pr);
            float dx = x1 - x0, dy = y1 - y0;
            float len2 = dx * dx + dy * dy + 1e-4f;
            float t = MathUtils.clamp(((pr.x - x0) * dx + (pr.y - y0) * dy) / len2, 0, 1);
            float qx = x0 + dx * t - pr.x, qy = y0 + dy * t - pr.y;
            if (qx * qx + qy * qy < r * r) return true;
        }
        return false;
    }

    public static float smokeRadius(Projectile pr) {
        float age = 9f - pr.fuse;
        return Math.min(150, 40 + age * 110) * Math.min(1, pr.fuse / 1.5f + 0.2f);
    }

    private void flashbang(Projectile pr) {
        pr.dead = true;
        WorldEvent e = emit(WorldEvent.Type.FLASHBANG);
        e.x = pr.x;
        e.y = pr.y;
        for (int i = 0; i < players.size; i++) {
            Player q = players.get(i);
            if (!q.alive) continue;
            float dx = pr.x - q.x, dy = pr.y - q.headY();
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 700 || !lineOfSight(pr.x, pr.y, q.x, q.headY())) continue;
            boolean looking = Math.abs(angleDiff(q.aim, MathUtils.atan2(dy, dx))) < 1.4f;
            float amount = (1 - d / 700f) * (looking ? 1f : 0.45f);
            if (q.id == pr.owner) amount *= 0.6f;
            q.flash = Math.max(q.flash, 0.6f + 3.2f * amount);
        }
    }

    private void moveSticky(Projectile pr, float dt) {
        pr.fuse -= dt;
        if (pr.fuse <= 0) {
            explodeProjectile(pr);
            return;
        }
        if (pr.lastHit >= 0) { // grudada num jogador
            Player q = player(pr.lastHit);
            if (q != null && q.alive) {
                pr.x = q.x + pr.startX;
                pr.y = q.y + pr.startY;
            } else {
                pr.lastHit = -1;
                pr.stuck = false;
            }
            return;
        }
        if (pr.stuck) {
            if (!map.solidAt(pr.x + 3, pr.y) && !map.solidAt(pr.x - 3, pr.y) && !map.solidAt(pr.x, pr.y - 3) && !map.solidAt(pr.x, pr.y + 3)) pr.stuck = false;
            return;
        }
        pr.vy -= gravity * 0.85f * dt;
        float nx = pr.x + pr.vx * dt, ny = pr.y + pr.vy * dt;
        for (int i = 0; i < players.size; i++) {
            Player q = players.get(i);
            if (!q.alive || q.id == pr.owner) continue;
            if (segAabb(pr.x, pr.y, nx, ny, q.x - C.PLAYER_HW, q.y, q.x + C.PLAYER_HW, q.y + q.height()) >= 0) {
                pr.lastHit = q.id;
                pr.stuck = true;
                pr.startX = MathUtils.clamp(nx - q.x, -C.PLAYER_HW, C.PLAYER_HW);
                pr.startY = MathUtils.clamp(ny - q.y, 4, q.height() - 4);
                pr.vx = pr.vy = 0;
                sound("stick", pr.x, pr.y);
                return;
            }
        }
        float t = rayTile(pr.x, pr.y, nx, ny);
        if (t <= 1) {
            pr.x += (nx - pr.x) * Math.max(0, t - 0.05f);
            pr.y += (ny - pr.y) * Math.max(0, t - 0.05f);
            pr.stuck = true;
            pr.vx = pr.vy = 0;
            sound("stick", pr.x, pr.y);
            return;
        }
        pr.x = nx;
        pr.y = ny;
        pr.rot += 400 * dt;
    }

    // ------------------------------------------------------------------ projeteis

    private void updateProjectile(Projectile pr, float dt) {
        switch (pr.kind) {
            case BULLET:
            case FLAME:
            case FROST:
            case ARROW:
            case HARPOON:
                moveShot(pr, dt);
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
            case SMOKE:
                if (!pr.armed) {
                    moveBouncy(pr, dt, 0.4f, 0.6f, false);
                    pr.fuse -= dt;
                    if (pr.fuse <= 0) {
                        pr.armed = true;
                        pr.life = 9f;
                        pr.fuse = 9f;
                        pr.vx = pr.vy = 0;
                        sound("smoke", pr.x, pr.y);
                    }
                } else {
                    pr.life -= dt;
                    pr.fuse = pr.life;
                    if (pr.life <= 0) pr.dead = true;
                }
                break;
            case FLASH:
                moveBouncy(pr, dt, 0.45f, 0.65f, false);
                pr.fuse -= dt;
                if (pr.fuse <= 0) flashbang(pr);
                break;
            case STICKY:
                moveSticky(pr, dt);
                break;
            case DISC:
                moveDisc(pr, dt);
                break;
            case BOOMERANG:
                moveBoomerang(pr, dt);
                break;
            case VORTEX:
                moveVortex(pr, dt);
                break;
        }
    }

    // resultado do teste de colisao de um segmento
    private static final int HIT_NONE = 0, HIT_PLAYER = 1, HIT_BOSS = 2, HIT_PROP = 3, HIT_TILE = 4;
    private int hitKind;
    private float hitT;
    private Player hitPlayer;
    private Boss hitBoss;
    private Prop hitProp;

    private boolean alreadyHit(Projectile pr, int id) {
        for (int k = 0; k < pr.hitIds.length; k++) if (pr.hitIds[k] == id) return true;
        return false;
    }

    /** Testa o segmento contra blocos, jogadores, chefoes e barris; guarda o mais proximo. */
    private void castSegment(Projectile pr, float x0, float y0, float x1, float y1, boolean tiles) {
        hitKind = HIT_NONE;
        hitT = 2;
        hitPlayer = null;
        hitBoss = null;
        hitProp = null;
        if (tiles) {
            float t = rayTile(x0, y0, x1, y1);
            if (t <= 1) {
                hitKind = HIT_TILE;
                hitT = t;
            }
        }
        for (int i = 0; i < players.size; i++) {
            Player q = players.get(i);
            if (!q.alive || q.id == pr.owner || q.id == pr.lastHit || alreadyHit(pr, q.id)) continue;
            if (!pr.visual && !canHurt(pr.owner, pr.team, q)) continue;
            if (pr.visual && pr.team != C.TEAM_NONE && pr.team == q.team && !config.friendlyFire) continue;
            float t = segAabb(x0, y0, x1, y1, q.x - C.PLAYER_HW, q.y, q.x + C.PLAYER_HW, q.y + q.height());
            if (t >= 0 && t < hitT) {
                hitT = t;
                hitKind = HIT_PLAYER;
                hitPlayer = q;
            }
        }
        if (canHurtBoss(pr.team)) for (int i = 0; i < bosses.size; i++) {
            Boss b = bosses.get(i);
            if (!b.alive) continue;
            float t = segAabb(x0, y0, x1, y1, b.x - b.halfW(), b.y, b.x + b.halfW(), b.y + b.height());
            if (t >= 0 && t < hitT) {
                hitT = t;
                hitKind = HIT_BOSS;
                hitBoss = b;
            }
        }
        for (int i = 0; i < props.size; i++) {
            Prop p = props.get(i);
            if (!p.alive) continue;
            float t = segAabb(x0, y0, x1, y1, p.x - Prop.W / 2, p.y, p.x + Prop.W / 2, p.y + Prop.H);
            if (t >= 0 && t < hitT) {
                hitT = t;
                hitKind = HIT_PROP;
                hitProp = p;
            }
        }
    }

    private int killCode(Projectile pr) {
        if (pr.owner == C.BOSS_OWNER) return C.KILL_BOSS;
        return pr.weapon != null ? pr.weapon.ordinal() : C.KILL_SUICIDE;
    }

    /** Balas, chamas, gelo, flechas e arpoes. */
    private void moveShot(Projectile pr, float dt) {
        if (pr.stuck) { // flecha/arpao cravado na parede (so visual)
            pr.life -= dt;
            if (pr.life <= 0) pr.dead = true;
            return;
        }
        if (pr.kind == Projectile.Kind.FLAME || pr.kind == Projectile.Kind.FROST) {
            pr.vy += (pr.kind == Projectile.Kind.FLAME ? 140 : 40) * dt;
            pr.vx *= (1 - 1.2f * dt);
        } else if (pr.kind == Projectile.Kind.ARROW) {
            pr.vy -= gravity * 0.45f * dt;
            pr.rot = MathUtils.atan2(pr.vy, pr.vx) * MathUtils.radiansToDegrees;
        }
        float nx = pr.x + pr.vx * dt, ny = pr.y + pr.vy * dt;
        castSegment(pr, pr.x, pr.y, nx, ny, true);
        float hx = pr.x + (nx - pr.x) * Math.min(1, hitT), hy = pr.y + (ny - pr.y) * Math.min(1, hitT);
        float len = (float) Math.sqrt(pr.vx * pr.vx + pr.vy * pr.vy) + 1;
        switch (hitKind) {
            case HIT_PLAYER: {
                Player hit = hitPlayer;
                pr.x = hx;
                pr.y = hy;
                pr.dead = true;
                if (shieldBlocks(hit, pr.vx)) {
                    if (!pr.visual) blockEffect(hit, hx, hy, pr.vx / len * 60);
                    return;
                }
                if (pr.visual) return;
                boolean head = pr.headshotCapable && hy > hit.y + hit.height() - 13;
                float dmg = pr.damage * (head ? (pr.weapon == WeaponType.SNIPER || pr.weapon == WeaponType.BOW ? 2f : 1.5f) : 1f);
                float kb = pr.kind == Projectile.Kind.FLAME || pr.kind == Projectile.Kind.FROST ? 30 : (pr.weapon == WeaponType.SNIPER ? 300 : 90);
                damage(hit, dmg, player(pr.owner), killCode(pr), head, pr.vx / len * kb, pr.vy / len * kb * 0.5f + 20);
                if (pr.kind == Projectile.Kind.FROST && hit.alive) {
                    hit.chill += 0.14f;
                    if (hit.chill >= 1f && hit.frozen <= 0) {
                        hit.frozen = 1.6f;
                        hit.chill = 0;
                        hit.jetting = false;
                        WorldEvent e = emit(WorldEvent.Type.FREEZE);
                        e.a = hit.id;
                        e.x = hit.x;
                        e.y = hit.centerY();
                    }
                }
                if (pr.kind == Projectile.Kind.HARPOON && hit.alive) {
                    Player owner = player(pr.owner);
                    if (owner != null) {
                        float dx = owner.x - hit.x, dy = owner.centerY() - hit.centerY();
                        float d = (float) Math.sqrt(dx * dx + dy * dy) + 1;
                        hit.vx = dx / d * 760;
                        hit.vy = dy / d * 520 + 180;
                        hit.stun = Math.max(hit.stun, 0.4f);
                        sound("harpoon_pull", hit.x, hit.y);
                    }
                }
                return;
            }
            case HIT_BOSS:
                pr.x = hx;
                pr.y = hy;
                pr.dead = true;
                if (!pr.visual) damageBoss(hitBoss, pr.damage, pr.owner);
                return;
            case HIT_PROP:
                pr.x = hx;
                pr.y = hy;
                pr.dead = true;
                if (!pr.visual) damageProp(hitProp, pr.damage, pr.owner);
                return;
            case HIT_TILE: {
                pr.x = hx;
                pr.y = hy;
                if (!pr.visual) {
                    float dmgT = pr.damage * (pr.kind == Projectile.Kind.FLAME || pr.kind == Projectile.Kind.FROST ? 0.4f : 0.8f);
                    damageTile((int) Math.floor((hx + pr.vx / len * 3) / C.TILE), (int) Math.floor((hy + pr.vy / len * 3) / C.TILE), dmgT);
                }
                if (pr.kind == Projectile.Kind.ARROW || pr.kind == Projectile.Kind.HARPOON) {
                    pr.stuck = true;
                    pr.life = 2.5f;
                    pr.vx = pr.vx / len;
                    pr.vy = pr.vy / len;
                    return;
                }
                pr.dead = true;
                if (pr.kind == Projectile.Kind.BULLET && !pr.visual) {
                    WorldEvent e = emit(WorldEvent.Type.SPARK);
                    e.x = pr.x;
                    e.y = pr.y;
                    e.f = MathUtils.atan2(pr.vy, pr.vx);
                }
                return;
            }
            default:
                break;
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
        castSegment(pr, pr.x, pr.y, nx, ny, true);
        if (hitKind != HIT_NONE) {
            float t = hitKind == HIT_TILE ? Math.max(0, hitT - 0.05f) : hitT;
            pr.x += (nx - pr.x) * t;
            pr.y += (ny - pr.y) * t;
            if (hitKind == HIT_PLAYER && shieldBlocks(hitPlayer, pr.vx)) pr.damage *= 0.4f;
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
            for (int i = 0; i < players.size; i++) {
                Player q = players.get(i);
                if (!q.alive || !canHurt(pr.owner, pr.team, q)) continue;
                if (Math.abs(q.x - pr.x) < C.PLAYER_HW + 4 && pr.y > q.y - 4 && pr.y < q.y + q.height() + 4) {
                    explodeProjectile(pr);
                    return;
                }
            }
            if (canHurtBoss(pr.team)) for (int i = 0; i < bosses.size; i++) {
                Boss b = bosses.get(i);
                if (b.alive && Math.abs(b.x - pr.x) < b.halfW() && pr.y > b.y && pr.y < b.y + b.height()) {
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
                pr.life = 0.8f;
            }
            return;
        }
        // a parede onde grudou quebrou: cai
        float cx = pr.x - MathUtils.sinDeg(pr.rot) * -4, cy = pr.y - MathUtils.cosDeg(pr.rot) * 4;
        if (!map.solidAt(cx, cy - 2) && !map.solidAt(pr.x + 4, pr.y) && !map.solidAt(pr.x - 4, pr.y) && !map.solidAt(pr.x, pr.y - 4) && !map.solidAt(pr.x, pr.y + 4)) {
            pr.stuck = false;
            return;
        }
        if (!pr.armed) {
            pr.life -= dt;
            if (pr.life <= 0) {
                pr.armed = true;
                pr.fuse = 45f;
                sound("arm", pr.x, pr.y);
            }
            return;
        }
        pr.fuse -= dt;
        if (pr.fuse <= 0) {
            explodeProjectile(pr);
            return;
        }
        for (int i = 0; i < players.size; i++) {
            Player q = players.get(i);
            if (!q.alive || !canHurt(pr.owner, pr.team, q)) continue;
            float dx = q.x - pr.x, dy = q.centerY() - pr.y;
            if (dx * dx + dy * dy < 48 * 48) {
                explodeProjectile(pr);
                return;
            }
        }
        if (canHurtBoss(pr.team)) for (int i = 0; i < bosses.size; i++) {
            Boss b = bosses.get(i);
            if (b.alive && Math.abs(b.x - pr.x) < b.halfW() + 30 && pr.y > b.y - 30 && pr.y < b.y + b.height() + 30) {
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
            damageTile((int) Math.floor(nx / C.TILE), (int) Math.floor(pr.y / C.TILE), pr.damage * 0.5f);
            pr.vx = -pr.vx;
            nx = pr.x;
            bounced = true;
        }
        if (map.solidAt(nx, ny)) {
            pr.vy = -pr.vy;
            ny = pr.y;
            bounced = true;
        }
        castSegment(pr, pr.x, pr.y, nx, ny, false);
        if (hitKind == HIT_PLAYER) {
            Player hit = hitPlayer;
            float len = (float) Math.sqrt(pr.vx * pr.vx + pr.vy * pr.vy) + 1;
            if (shieldBlocks(hit, pr.vx)) {
                blockEffect(hit, hit.x, hit.centerY(), pr.vx / len * 80);
                pr.vx = -pr.vx;
            } else {
                damage(hit, pr.damage, player(pr.owner), WeaponType.SAW.ordinal(), false, pr.vx / len * 200, 120);
                pr.lastHit = hit.id;
                pr.damage *= 0.7f;
                if (pr.damage < 20) pr.dead = true;
            }
        } else if (hitKind == HIT_BOSS) {
            damageBoss(hitBoss, pr.damage, pr.owner);
            pr.dead = true;
        } else if (hitKind == HIT_PROP) {
            damageProp(hitProp, pr.damage, pr.owner);
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

    /** Bumerangue: vai, curva e volta para a mao do dono (acerta cada inimigo uma vez). */
    private void moveBoomerang(Projectile pr, float dt) {
        pr.rot += 1100 * dt;
        Player owner = player(pr.owner);
        if (pr.age > 0.32f && owner != null && owner.alive) {
            float dx = owner.x - pr.x, dy = owner.shoulderY() - pr.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy) + 1;
            pr.vx += dx / d * 2600 * dt;
            pr.vy += dy / d * 2600 * dt;
            float sp = (float) Math.sqrt(pr.vx * pr.vx + pr.vy * pr.vy);
            if (sp > 1050) {
                pr.vx *= 1050 / sp;
                pr.vy *= 1050 / sp;
            }
            if (pr.age > 0.45f && d < 34) { // pegou de volta
                pr.dead = true;
                Gun g = owner.gun();
                for (int k = 0; k < 2; k++) {
                    Gun gg = owner.guns[k];
                    if (gg != null && gg.type == WeaponType.BOOMERANG) gg.mag = Math.min(gg.type.mag, gg.mag + 1);
                }
                sound("catch", owner.x, owner.y);
                return;
            }
        }
        float nx = pr.x + pr.vx * dt, ny = pr.y + pr.vy * dt;
        if (map.solidAt(nx, pr.y)) {
            pr.vx = -pr.vx * 0.8f;
            nx = pr.x;
        }
        if (map.solidAt(nx, ny)) {
            pr.vy = -pr.vy * 0.8f;
            ny = pr.y;
        }
        castSegment(pr, pr.x, pr.y, nx, ny, false);
        if (hitKind == HIT_PLAYER) {
            Player hit = hitPlayer;
            if (shieldBlocks(hit, pr.vx)) {
                blockEffect(hit, hit.x, hit.centerY(), 0);
                pr.vx = -pr.vx;
            } else {
                damage(hit, pr.damage, owner, WeaponType.BOOMERANG.ordinal(), false, pr.vx * 0.2f, 140);
            }
            for (int k = 0; k < pr.hitIds.length; k++) if (pr.hitIds[k] == -9) {
                pr.hitIds[k] = hit.id;
                break;
            }
        } else if (hitKind == HIT_BOSS) {
            damageBoss(hitBoss, pr.damage, pr.owner);
            pr.vx = -pr.vx;
            pr.vy = -pr.vy;
        } else if (hitKind == HIT_PROP) {
            damageProp(hitProp, pr.damage, pr.owner);
        }
        pr.x = nx;
        pr.y = ny;
        pr.life -= dt;
        if (pr.life <= 0) pr.dead = true;
    }

    /** Gravitacional: orbe que vira um buraco negro, puxa todo mundo e implode. */
    private void moveVortex(Projectile pr, float dt) {
        if (!pr.stuck) {
            float nx = pr.x + pr.vx * dt, ny = pr.y + pr.vy * dt;
            castSegment(pr, pr.x, pr.y, nx, ny, true);
            pr.life -= dt;
            if (hitKind != HIT_NONE || pr.life <= 0) {
                float t = hitKind == HIT_NONE ? 1 : Math.max(0, hitT - 0.1f);
                pr.x += (nx - pr.x) * t;
                pr.y += (ny - pr.y) * t;
                pr.stuck = true;
                pr.armed = true;
                pr.vx = pr.vy = 0;
                pr.life = 2.2f;
                sound("gravity", pr.x, pr.y);
            } else {
                pr.x = nx;
                pr.y = ny;
            }
            return;
        }
        pr.rot += 400 * dt;
        pr.life -= dt;
        float r = 280;
        for (int i = 0; i < players.size; i++) {
            Player q = players.get(i);
            if (!q.alive) continue;
            float dx = pr.x - q.x, dy = pr.y - q.centerY();
            float d = (float) Math.sqrt(dx * dx + dy * dy) + 1;
            if (d > r) continue;
            float f = 1 - d / r;
            q.vx += dx / d * 1900 * f * dt;
            q.vy += dy / d * 1900 * f * dt + gravity * 0.5f * f * dt;
            if (d < 70 && canHurt(pr.owner, pr.team, q)) damage(q, 14 * dt * 4, player(pr.owner), WeaponType.GRAVITY.ordinal(), false, 0, 0);
        }
        for (int i = 0; i < projectiles.size; i++) { // tambem puxa granadas e balas
            Projectile o = projectiles.get(i);
            if (o == pr || o.stuck || o.kind == Projectile.Kind.VORTEX) continue;
            float dx = pr.x - o.x, dy = pr.y - o.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy) + 1;
            if (d < r) {
                o.vx += dx / d * 1500 * dt;
                o.vy += dy / d * 1500 * dt;
            }
        }
        if (pr.life <= 0) {
            pr.damage = 45;
            explodeProjectile(pr);
        }
    }

    private void explodeProjectile(Projectile pr) {
        if (pr.dead) return;
        pr.dead = true;
        float radius;
        int code;
        switch (pr.kind) {
            case ROCKET:
                radius = 115;
                code = pr.owner == C.BOSS_OWNER ? C.KILL_BOSS : WeaponType.ROCKET.ordinal();
                break;
            case SHELL:
                radius = 95;
                code = WeaponType.GRENADE_LAUNCHER.ordinal();
                break;
            case MINE:
                radius = 105;
                code = C.KILL_MINE;
                break;
            case VORTEX:
                radius = 100;
                code = WeaponType.GRAVITY.ordinal();
                break;
            case STICKY:
                radius = 105;
                code = C.KILL_FRAG;
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
            if (shieldBlocks(q, -dx)) dmg *= 0.5f;
            float nx = d > 1 ? dx / d : 0, ny = d > 1 ? dy / d : 1;
            q.vx += nx * 700 * f;
            q.vy += ny * 600 * f + 250 * f;
            if (q.hookState == 2) releaseHook(q);
            if (q.id == ownerId) dmg *= 0.55f;
            else if (!canHurt(ownerId, ownerTeam, q)) continue;
            damage(q, dmg, owner, code, false, 0, 0);
        }
        if (canHurtBoss(ownerTeam)) for (int i = 0; i < bosses.size; i++) {
            Boss b = bosses.get(i);
            if (!b.alive) continue;
            float dx = b.x - x, dy = b.centerY() - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy) - b.halfW() * 0.5f;
            if (d < radius) damageBoss(b, damage * (0.35f + 0.65f * (1 - Math.max(0, d) / radius)), ownerId);
        }
        for (int i = 0; i < props.size; i++) {
            Prop pr = props.get(i);
            if (!pr.alive) continue;
            float dx = pr.x - x, dy = pr.y + 16 - y;
            if (dx * dx + dy * dy < (radius + 16) * (radius + 16)) damageProp(pr, damage, ownerId);
        }
        breakTilesAround(x, y, radius * 0.85f, damage);
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
        if (victim.rollTimer > 0) amount *= 0.5f; // esquiva: metade do dano
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
        if (victim.emote >= C.EMOTE_DANCE_FIRST) victim.emoteTimer = 0;
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
        victim.hookState = 0;
        victim.respawnTimer = mode.respawnDelay(victim);
        if (attacker == null && victim.lastAttacker >= 0 && time - victim.lastDamageTime < 5 && code != C.KILL_BOSS && code != C.KILL_ZONE) {
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
        } else if (code != C.KILL_BOSS && code != C.KILL_ZONE) {
            victim.score -= 1;
            if (code < C.KILL_MELEE) code = C.KILL_SUICIDE;
        }
        dropStuff(victim);
        mode.onKill(victim, attacker, code);
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
        if (g != null && g.type != WeaponType.PISTOL && !g.empty() && mode.usesMapItems() && mode.dropsWeapons()) {
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
                    else if (pk.type == Pickup.Type.WEAPON) { // treino: o rack passa para a proxima arma
                        WeaponType[] all = WeaponType.values();
                        int n = pk.weapon.ordinal() + 12;
                        while (n >= all.length) n -= all.length - 1;
                        pk.weapon = all[Math.max(1, n)];
                    }
                    if (pk.randomPower) pk.type = randomPower();
                    pk.mag = pk.reserve = -1;
                }
                continue;
            }
            for (int j = 0; j < players.size; j++) {
                Player p = players.get(j);
                if (!p.alive || p.dummy || !mode.canPickup(p)) continue;
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
                p.smokes = Math.min(2, p.smokes + 1);
                p.flashes = Math.min(2, p.flashes + 1);
                p.stickies = Math.min(2, p.stickies + 1);
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
                for (int k = 0; k < p.guns.length; k++) {
                    Gun g = p.guns[k];
                    if (g != null && g.type == pk.weapon) {
                        int cap = Math.max(g.type.reserve * 2, 2);
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
        if (!mode.canPickup(p)) return null;
        Pickup best = null;
        float bd = C.PICKUP_RADIUS * C.PICKUP_RADIUS * 1.6f;
        for (int i = 0; i < pickups.size; i++) {
            Pickup pk = pickups.get(i);
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
            p.emoteTimer = Math.max(0, p.emoteTimer - dt);
            p.rollTimer = Math.max(0, p.rollTimer - dt);
        }
        for (int i = 0; i < bosses.size; i++) {
            Boss b = bosses.get(i);
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            b.anim += dt;
            b.hurtFlash = Math.max(0, b.hurtFlash - dt);
        }
        for (int i = projectiles.size - 1; i >= 0; i--) {
            Projectile pr = projectiles.get(i);
            pr.age += dt;
            if (pr.visual) {
                moveShot(pr, dt);
            } else {
                if (pr.kind == Projectile.Kind.FRAG || pr.kind == Projectile.Kind.SHELL || (pr.kind == Projectile.Kind.MINE && !pr.stuck)
                        || pr.kind == Projectile.Kind.FLASH || (pr.kind == Projectile.Kind.SMOKE && !pr.armed) || (pr.kind == Projectile.Kind.STICKY && !pr.stuck)) {
                    pr.vy -= gravity * 0.85f * dt;
                    float nx = pr.x + pr.vx * dt, ny = pr.y + pr.vy * dt;
                    if (!map.solidAt(nx, ny)) {
                        pr.x = nx;
                        pr.y = ny;
                    }
                } else if (!pr.stuck && (pr.kind == Projectile.Kind.ROCKET || pr.kind == Projectile.Kind.DISC || pr.kind == Projectile.Kind.BOOMERANG
                        || pr.kind == Projectile.Kind.VORTEX || pr.kind == Projectile.Kind.HARPOON)) {
                    pr.x += pr.vx * dt;
                    pr.y += pr.vy * dt;
                }
                if (pr.kind == Projectile.Kind.DISC) pr.rot += 900 * dt;
                else if (pr.kind == Projectile.Kind.BOOMERANG) pr.rot += 1100 * dt;
                else if (pr.kind == Projectile.Kind.VORTEX) pr.rot += 400 * dt;
                else if (pr.kind != Projectile.Kind.MINE && pr.kind != Projectile.Kind.HARPOON) pr.rot += pr.vx * dt * 4;
            }
            if (pr.dead) projectiles.removeIndex(i);
        }
        for (int i = 0; i < pickups.size; i++) pickups.get(i).bob += dt;
    }

    /** Em clientes: cria balas visuais a partir de um evento de tiro. */
    public void spawnVisualShot(WorldEvent e) {
        WeaponType w = WeaponType.of(e.b);
        if (w == null || e.angles == null) return;
        if (w.kind != Projectile.Kind.BULLET && w.kind != Projectile.Kind.FLAME && w.kind != Projectile.Kind.FROST && w.kind != Projectile.Kind.ARROW) return;
        if (w.instant() || w.isShield()) return;
        Player owner = player(e.a);
        int team = owner != null ? owner.team : (e.a == C.BOSS_OWNER ? C.TEAM_BLUE : 0);
        for (float ang : e.angles) {
            Projectile pr = spawnBullet(e.a, team, w, e.x, e.y, ang, 0, true);
            if (owner != null && (w.kind == Projectile.Kind.FLAME || w.kind == Projectile.Kind.FROST)) {
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
