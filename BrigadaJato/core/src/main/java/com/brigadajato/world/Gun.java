package com.brigadajato.world;

/** Uma arma carregada por um jogador (tipo + municao). */
public class Gun {
    public WeaponType type;
    public int mag, reserve;

    public Gun(WeaponType type) {
        this(type, type.mag, type.reserve);
    }

    public Gun(WeaponType type, int mag, int reserve) {
        this.type = type;
        this.mag = mag;
        this.reserve = reserve;
    }

    public boolean empty() {
        return mag <= 0 && reserve <= 0;
    }
}
