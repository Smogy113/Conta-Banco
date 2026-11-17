package com.brigadajato.world;

/** Acontecimentos do mundo (para efeitos, sons, HUD e rede). */
public class WorldEvent {
    public enum Type {
        SHOT, LASER, EXPLOSION, HIT, KILL, SPARK, SOUND, MELEE, PICKUP, RESPAWN, MESSAGE, THROW, RELOAD,
        BOUNCE, FLAG, BOMB, ROUND, WAVE, STREAK, EMPTY, DEATH, DAMAGE_NUMBER
    }

    public Type type;
    public int a = -1, b = -1, c = -1;
    public float x, y, x2, y2, f;
    public float[] angles;
    public String text;

    public WorldEvent() {
    }

    public WorldEvent(Type type) {
        this.type = type;
    }
}
