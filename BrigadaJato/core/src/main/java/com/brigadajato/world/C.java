package com.brigadajato.world;

/** Constantes de jogabilidade. Unidades: pixels de mundo e segundos. */
public final class C {
    private C() {
    }

    public static final float TILE = 32f;
    public static final float STEP = 1f / 60f;

    public static final float GRAVITY = 1250f;
    public static final float WALK_SPEED = 215f;
    public static final float GROUND_ACCEL = 2600f;
    public static final float AIR_ACCEL = 1500f;
    public static final float JET_ACCEL = 2750f;
    public static final float MAX_RISE = 340f;
    public static final float MAX_FALL = 780f;

    public static final float FUEL_MAX = 100f;
    public static final float FUEL_USE = 30f;
    public static final float FUEL_REGEN_GROUND = 42f;
    public static final float FUEL_REGEN_AIR = 16f;
    public static final float FUEL_REGEN_DELAY = 0.35f;
    public static final float FUEL_UNLOCK = 20f;

    public static final float PLAYER_HW = 11f;
    public static final float PLAYER_H = 44f;
    public static final float CROUCH_H = 30f;

    public static final float HEALTH_MAX = 100f;
    public static final float ARMOR_MAX = 100f;
    public static final float REGEN_DELAY = 4.5f;
    public static final float REGEN_RATE = 11f;

    public static final float SPAWN_SHIELD = 2.0f;
    public static final float MELEE_RANGE = 46f;
    public static final float MELEE_DAMAGE = 55f;
    public static final float MELEE_COOLDOWN = 0.55f;

    public static final float PICKUP_RADIUS = 34f;
    public static final int MAX_FRAGS = 4;
    public static final int MAX_MINES = 2;

    public static final float SPIKE_DPS = 45f;

    public static final int TEAM_NONE = 0;
    public static final int TEAM_RED = 1;
    public static final int TEAM_BLUE = 2;

    /** Codigos especiais de "arma" no feed de abates. */
    public static final int KILL_MELEE = 100;
    public static final int KILL_FRAG = 101;
    public static final int KILL_MINE = 102;
    public static final int KILL_SPIKES = 103;
    public static final int KILL_BOMB = 104;
    public static final int KILL_SUICIDE = 105;
    public static final int KILL_BARREL = 106;
    public static final int KILL_BOSS = 107;
    public static final int KILL_ZONE = 108;
    public static final int KILL_INFECT = 109;

    // emotes: 0..5 frases rapidas, 6..9 reacoes, 10..13 dancas
    public static final int EMOTE_PHRASES = 6;
    public static final int EMOTE_DANCE_FIRST = 10;
    public static final int EMOTE_COUNT = 14;

    /** dono dos tiros dos chefoes */
    public static final int BOSS_OWNER = -2;

    public static final float HOOK_SPEED = 1700f, HOOK_RANGE = 520f, HOOK_PULL = 3000f;
}
