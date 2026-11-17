package com.brigadajato.world;

/** Tudo que voa: balas, chamas, foguetes, granadas, minas e discos. */
public class Projectile {
    public enum Kind {BULLET, FLAME, ROCKET, SHELL, FRAG, MINE, DISC}

    public int id;
    public Kind kind;
    public int owner;
    public int team;
    public WeaponType weapon;
    public float x, y, vx, vy;
    public float startX, startY;
    public float life;
    public float damage;
    public int bounces;
    public boolean dead;
    public float fuse;
    public boolean stuck, armed;
    /** em clientes de rede, so visual (sem dano) */
    public boolean visual;
    public float rot;
    public int lastHit = -1;
    public boolean headshotCapable;

    public boolean synced() {
        return kind == Kind.ROCKET || kind == Kind.SHELL || kind == Kind.FRAG || kind == Kind.MINE || kind == Kind.DISC;
    }
}
