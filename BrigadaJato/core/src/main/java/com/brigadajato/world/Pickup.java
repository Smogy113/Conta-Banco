package com.brigadajato.world;

/** Itens no chao. */
public class Pickup {
    public enum Type {WEAPON, HEALTH, GRENADES, ARMOR, DAMAGE, JETPACK, SPEED}

    public int id;
    public Type type;
    public WeaponType weapon;
    public int mag = -1, reserve = -1;
    public float x, y, vy;
    public boolean active = true;
    public float respawnTimer;
    public float respawnTime;
    /** item largado por alguem (some depois de um tempo) */
    public boolean dropped;
    public float life;
    /** em mapas de treino: rack fixo sempre com a mesma arma */
    public boolean fixedWeapon;
    public boolean randomPower;
    public float bob;
}
