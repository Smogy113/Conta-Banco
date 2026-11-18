package com.brigadajato.world;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Configuracao de uma partida. */
public class MatchConfig {
    public ModeType mode = ModeType.DEATHMATCH;
    public String mapId = "fortaleza";
    public int bots = 5;
    /** 0 facil, 1 normal, 2 dificil */
    public int difficulty = 1;
    /** segundos (0 = sem limite) */
    public int timeLimit = 300;
    public int scoreLimit = 20;
    public boolean friendlyFire = false;
    /** time preferido do jogador local (0 = automatico) */
    public int playerTeam = 0;

    // ---- regras personalizadas (mutadores)
    public static final float[] GRAVITY_OPTS = {0.5f, 0.75f, 1f, 1.5f}, HEALTH_OPTS = {0.5f, 1f, 2f, 3f}, SPEED_OPTS = {0.8f, 1f, 1.3f};
    public static final int SET_NORMAL = 0, SET_PISTOLS = 1, SET_SHOTGUNS = 2, SET_SNIPERS = 3, SET_EXPLOSIVE = 4, SET_MELEE = 5,
            SET_CRAZY = 6, SET_COUNT = 7;
    public float gravityMul = 1f, healthMul = 1f, speedMul = 1f;
    public int weaponSet = SET_NORMAL;
    public boolean infiniteAmmo, infiniteJet, instagib, noGrenades;
    /** so assistir os bots (offline, nao vai pela rede) */
    public boolean spectate;

    public boolean customRules() {
        return gravityMul != 1f || healthMul != 1f || speedMul != 1f || weaponSet != SET_NORMAL || infiniteAmmo || infiniteJet || instagib || noGrenades;
    }

    public void resetRules() {
        gravityMul = healthMul = speedMul = 1f;
        weaponSet = SET_NORMAL;
        infiniteAmmo = infiniteJet = instagib = noGrenades = false;
    }

    /** Armas do conjunto escolhido (null = todas). */
    public static WeaponType[] setWeapons(int set) {
        switch (set) {
            case SET_PISTOLS: return new WeaponType[]{WeaponType.PISTOL, WeaponType.MAGNUM, WeaponType.DUAL_PISTOLS};
            case SET_SHOTGUNS: return new WeaponType[]{WeaponType.SHOTGUN};
            case SET_SNIPERS: return new WeaponType[]{WeaponType.SNIPER, WeaponType.BOW};
            case SET_EXPLOSIVE: return new WeaponType[]{WeaponType.ROCKET, WeaponType.GRENADE_LAUNCHER};
            case SET_MELEE: return new WeaponType[]{WeaponType.SHIELD};
            case SET_CRAZY: return new WeaponType[]{WeaponType.BOW, WeaponType.BOOMERANG, WeaponType.FROST, WeaponType.TESLA, WeaponType.GRAVITY,
                    WeaponType.HARPOON, WeaponType.SAW, WeaponType.LASER, WeaponType.FLAMETHROWER};
            default: return null;
        }
    }

    public MatchConfig copy() {
        MatchConfig c = new MatchConfig();
        c.mode = mode;
        c.mapId = mapId;
        c.bots = bots;
        c.difficulty = difficulty;
        c.timeLimit = timeLimit;
        c.scoreLimit = scoreLimit;
        c.friendlyFire = friendlyFire;
        c.playerTeam = playerTeam;
        c.gravityMul = gravityMul;
        c.healthMul = healthMul;
        c.speedMul = speedMul;
        c.weaponSet = weaponSet;
        c.infiniteAmmo = infiniteAmmo;
        c.infiniteJet = infiniteJet;
        c.instagib = instagib;
        c.noGrenades = noGrenades;
        c.spectate = spectate;
        return c;
    }

    public void applyModeDefaults() {
        scoreLimit = mode.defaultScore;
        timeLimit = mode.defaultTime;
        if (mode == ModeType.TRAINING) mapId = "treino";
        else if (mode == ModeType.BOMB) mapId = "poeira";
        else if (mode == ModeType.BATTLE_ROYALE) mapId = "arena";
        else if ("treino".equals(mapId) || "arena".equals(mapId)) mapId = "fortaleza";
    }

    public void write(DataOutput o) throws IOException {
        o.writeByte(mode.ordinal());
        o.writeUTF(mapId);
        o.writeByte(bots);
        o.writeByte(difficulty);
        o.writeShort(timeLimit);
        o.writeShort(scoreLimit);
        o.writeBoolean(friendlyFire);
        o.writeByte(playerTeam);
        o.writeFloat(gravityMul);
        o.writeFloat(healthMul);
        o.writeFloat(speedMul);
        o.writeByte(weaponSet);
        o.writeByte((infiniteAmmo ? 1 : 0) | (infiniteJet ? 2 : 0) | (instagib ? 4 : 0) | (noGrenades ? 8 : 0));
    }

    public void read(DataInput in) throws IOException {
        mode = ModeType.of(in.readByte());
        mapId = in.readUTF();
        bots = in.readByte();
        difficulty = in.readByte();
        timeLimit = in.readShort();
        scoreLimit = in.readShort();
        friendlyFire = in.readBoolean();
        playerTeam = in.readByte();
        gravityMul = in.readFloat();
        healthMul = in.readFloat();
        speedMul = in.readFloat();
        weaponSet = in.readByte();
        int f = in.readByte();
        infiniteAmmo = (f & 1) != 0;
        infiniteJet = (f & 2) != 0;
        instagib = (f & 4) != 0;
        noGrenades = (f & 8) != 0;
    }
}
