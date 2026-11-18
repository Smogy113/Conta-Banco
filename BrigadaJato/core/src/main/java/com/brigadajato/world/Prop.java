package com.brigadajato.world;

/** Barril explosivo (pode ser atingido; explode e reaparece depois). */
public class Prop {
    public static final float W = 24, H = 32, HP = 40;
    public int id;
    public float x, y;
    public float hp = HP;
    public boolean alive = true;
    public float respawn;
    public int lastAttacker = -1;
    public float hurtFlash;
}
