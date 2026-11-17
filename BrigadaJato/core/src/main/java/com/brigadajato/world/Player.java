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

    // ---- somente visual / rede
    public float anim;
    public float netX, netY;
    public float meleeAnim;
    public float hurtFlash;
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
        return y + height() * 0.66f;
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
}
