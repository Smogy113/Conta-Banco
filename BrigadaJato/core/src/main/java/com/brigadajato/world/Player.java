package com.brigadajato.world;

import com.badlogic.gdx.math.MathUtils;
import com.brigadajato.world.ai.BotBrain;

/** Um soldado (humano ou bot). Posicao (x, y) = centro dos pes. */
public class Player {
    public final int id;
    public String name;
    public int team;
    public Look look;
    public boolean isBot;
    public BotBrain bot;
    /** jogador conectado pela rede (indice da conexao) ou -1 */
    public int netClient = -1;
    /** aparelho do jogador (reconexao) e nome original (antes de virar bot) */
    public long netToken;
    public String baseName;
    public boolean dummy; // boneco de treino

    public final PlayerInput input = new PlayerInput();

    public boolean alive;
    public float x, y, vx, vy;
    public boolean onGround, crouching, jetting;
    public int facing = 1;
    public float aim;
    public float health = C.HEALTH_MAX, armor;
    public float fuel = C.FUEL_MAX;
    public float fuelDelay;
    /** combustivel acabou: jetpack trava ate recarregar um pouco */
    public boolean fuelLocked;
    public float dropTimer;

    public final Gun[] guns = new Gun[2];
    public int slot;
    public float reloadTimer, fireCooldown, meleeCooldown, grenadeCooldown;
    public int frags = 2, mines = 0;
    /** granadas extras: fumaca, flashbang e grudenta */
    public int smokes, flashes, stickies;
    /** cego pela flashbang (segundos) */
    public float flash;
    /** disfarce no pique-esconde (-1 = nenhum) */
    public int disguise = -1;
    public float lavaTimer;
    /** rolamento/esquiva */
    public float rollTimer, rollCd;
    public int rollDir = 1;
    /** 0 = granada de fragmentacao, 1 = mina */
    public int grenadeType;

    public int kills, deaths, score, money, lives = 3, streak, assists;
    public float respawnTimer;
    public float spawnShield;
    public float lastDamageTime = -99, lastShotTime = -99;
    public int lastAttacker = -1;

    public float dmgBoost, jetBoost, speedBoost;
    public int carryingFlag = 0; // time da bandeira carregada
    public boolean hasBomb;
    public float actionProgress; // plantar/desarmar (0..1)

    // ---- gancho
    /** 0 = guardado, 1 = voando, 2 = preso na parede */
    public int hookState;
    public float hookX, hookY, hookVX, hookVY, hookTimer, hookCd;
    // ---- efeitos
    /** frio acumulado (congelante); congelado e atordoado (segundos) */
    public float chill, frozen, stun;
    public boolean zombie;
    // ---- emotes
    public int emote = -1;
    public float emoteTimer, emoteCd;
    public boolean prevUse;
    public boolean dualFlip;
    /** nivel no Gun Game */
    public int gunLevel;
    /** Juggernaut (tanque gigante do modo Juggernaut) */
    public boolean juggernaut;
    public int timesJugg, juggKills;
    // ---- estatisticas da partida (medalhas)
    public int shotsFired, shotsHit, headshots, bestStreak, meleeKills, explosiveKills, suicides, objectives, multiKills;
    public float damageDealt, longestKill, timeAlive;
    public float lastKillTime = -99, lastHitTime = -99;
    public int quickKills;

    // ---- somente visual / rede
    public float anim;
    public float netX, netY;
    public float meleeAnim;
    public float hurtFlash;
    /** recuo visual ao levar tiro (so desenho): 1 -> 0 e o lado */
    public float flinch, flinchDir;
    public boolean recentlyFired;
    public float spikeTimer;
    public int spawnIndex = -1;

    public Player(int id, String name, int team, Look look, boolean isBot) {
        this.id = id;
        this.name = name;
        this.team = team;
        this.look = look;
        this.isBot = isBot;
    }

    public float height() {
        return crouching ? C.CROUCH_H : C.PLAYER_H;
    }

    public float centerY() {
        return y + height() * 0.5f;
    }

    public float shoulderY() {
        return y + height() * 0.69f;
    }

    public float headY() {
        return y + height() - 8f;
    }

    public Gun gun() {
        Gun g = guns[slot];
        if (g == null) {
            slot = 1 - slot;
            g = guns[slot];
        }
        return g;
    }

    public Gun otherGun() {
        return guns[1 - slot];
    }

    public boolean has(WeaponType t) {
        return (guns[0] != null && guns[0].type == t) || (guns[1] != null && guns[1].type == t);
    }

    public boolean reloading() {
        return reloadTimer > 0;
    }

    public void resetForSpawn(float sx, float sy) {
        rollTimer = 0;
        rollCd = 0;
        flash = 0;
        alive = true;
        x = sx;
        y = sy;
        vx = vy = 0;
        netX = sx;
        netY = sy;
        health = C.HEALTH_MAX;
        armor = 0;
        fuel = C.FUEL_MAX;
        fuelLocked = false;
        reloadTimer = fireCooldown = meleeCooldown = 0;
        spawnShield = C.SPAWN_SHIELD;
        dmgBoost = jetBoost = speedBoost = 0;
        carryingFlag = 0;
        hasBomb = false;
        actionProgress = 0;
        streak = 0;
        hookState = 0;
        hookCd = 0;
        chill = frozen = stun = 0;
        emote = -1;
        emoteTimer = 0;
        crouching = false;
        lastAttacker = -1;
        input.reset();
    }

    public void giveDefaultLoadout(java.util.Random rng) {
        guns[1] = new Gun(WeaponType.PISTOL);
        WeaponType[] starters = {WeaponType.SMG, WeaponType.RIFLE, WeaponType.SHOTGUN};
        guns[0] = new Gun(starters[rng.nextInt(starters.length)]);
        slot = 0;
        frags = 2;
        mines = 1;
        smokes = 1;
        flashes = 1;
        stickies = 1;
    }

    public float distTo(Player o) {
        float dx = o.x - x, dy = o.centerY() - centerY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public float aimX() {
        return MathUtils.cos(aim);
    }

    public float aimY() {
        return MathUtils.sin(aim);
    }

    public static final int NADE_FRAG = 0, NADE_MINE = 1, NADE_SMOKE = 2, NADE_FLASH = 3, NADE_STICKY = 4, NADE_TYPES = 5;

    public int nades(int type) {
        switch (type) {
            case NADE_MINE: return mines;
            case NADE_SMOKE: return smokes;
            case NADE_FLASH: return flashes;
            case NADE_STICKY: return stickies;
            default: return frags;
        }
    }

    public void addNades(int type, int n) {
        switch (type) {
            case NADE_MINE: mines = Math.max(0, mines + n); break;
            case NADE_SMOKE: smokes = Math.max(0, smokes + n); break;
            case NADE_FLASH: flashes = Math.max(0, flashes + n); break;
            case NADE_STICKY: stickies = Math.max(0, stickies + n); break;
            default: frags = Math.max(0, frags + n); break;
        }
    }
}
