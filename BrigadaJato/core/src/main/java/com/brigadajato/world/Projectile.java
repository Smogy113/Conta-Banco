package com.brigadajato.world;

/** Tudo que voa: balas, chamas, foguetes, granadas, minas e discos. */
public class Projectile {
    public enum Kind {BULLET, FLAME, ROCKET, SHELL, FRAG, MINE, DISC, ARROW, BOOMERANG, FROST, VORTEX, HARPOON, SMOKE, FLASH, STICKY}

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
    /** quem ja foi atingido (bumerangue) */
    public final int[] hitIds = {-9, -9, -9, -9};
    public float age;

    public boolean synced() {
        return kind == Kind.ROCKET || kind == Kind.SHELL || kind == Kind.FRAG || kind == Kind.MINE || kind == Kind.DISC
                || kind == Kind.BOOMERANG || kind == Kind.VORTEX || kind == Kind.HARPOON || kind == Kind.SMOKE || kind == Kind.FLASH || kind == Kind.STICKY;
    }
}
