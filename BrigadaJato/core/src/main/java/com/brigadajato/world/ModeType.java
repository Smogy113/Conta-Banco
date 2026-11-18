package com.brigadajato.world;

/** Modos de jogo. */
public enum ModeType {
    DEATHMATCH("Mata-mata", "Deathmatch", false, 20, 300),
    TEAM_DEATHMATCH("Times", "Team Deathmatch", true, 40, 420),
    CTF("Capture a Bandeira", "Capture the Flag", true, 3, 480),
    BOMB("Plantar a Bomba", "Bomb Defusal", true, 5, 0),
    SURVIVAL("Sobrevivência", "Survival", true, 0, 0),
    TRAINING("Treino", "Training", true, 0, 0),
    GUN_GAME("Gun Game", "Gun Game", false, 0, 600),
    KING_HILL("Rei do Morro", "King of the Hill", true, 120, 480),
    INFECTION("Zumbis", "Infection", true, 0, 180),
    BATTLE_ROYALE("Battle Royale", "Battle Royale", false, 0, 0),
    DUEL("Duelo", "Duel", false, 3, 0);

    public final String pt, en;
    public final boolean teams;
    public final int defaultScore, defaultTime;

    ModeType(String pt, String en, boolean teams, int defaultScore, int defaultTime) {
        this.pt = pt;
        this.en = en;
        this.teams = teams;
        this.defaultScore = defaultScore;
        this.defaultTime = defaultTime;
    }

    /** Os jogadores escolhem o time? (nos outros modos o proprio modo decide) */
    public boolean chooseTeams() {
        return teams && this != SURVIVAL && this != TRAINING && this != INFECTION;
    }

    public static ModeType of(int ordinal) {
        ModeType[] v = values();
        return ordinal < 0 || ordinal >= v.length ? DEATHMATCH : v[ordinal];
    }
}
