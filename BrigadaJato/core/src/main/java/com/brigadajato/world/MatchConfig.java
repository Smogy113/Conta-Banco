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
    }
}
