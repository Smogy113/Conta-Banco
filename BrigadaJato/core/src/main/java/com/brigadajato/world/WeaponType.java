package com.brigadajato.world;

/** Todas as armas do jogo. */
public enum WeaponType {
    //        pt               en                dmg  rate  mag  res  reload spread speed  range pel kind               zoom  move  len  hgt  color     sound      botR  wt  price recoil tier
    PISTOL("Pistola", "Pistol", 17, 5.0f, 12, 72, 1.0f, 2.5f, 1500, 950, 1, Projectile.Kind.BULLET, 1.0f, 1.0f, 16, 6, 0x5d6d7e, "pistol", 450, 0, 0, 0, 0),
    MAGNUM("Magnum", "Magnum", 42, 2.2f, 6, 36, 1.4f, 1.0f, 1900, 1150, 1, Projectile.Kind.BULLET, 1.1f, 1.0f, 20, 7, 0xb0b7bf, "magnum", 550, 6, 700, 40, 1),
    SMG("Submetralhadora", "SMG", 9, 14f, 32, 192, 1.4f, 7f, 1500, 750, 1, Projectile.Kind.BULLET, 1.0f, 1.0f, 22, 8, 0x2c3e50, "smg", 330, 10, 1250, 0, 1),
    RIFLE("Fuzil", "Assault Rifle", 15, 9f, 30, 180, 1.6f, 3f, 1800, 1150, 1, Projectile.Kind.BULLET, 1.15f, 0.95f, 32, 8, 0x6e4b2a, "rifle", 520, 10, 2700, 0, 2),
    SHOTGUN("Escopeta", "Shotgun", 11, 1.4f, 6, 36, 2.0f, 22f, 1300, 380, 8, Projectile.Kind.BULLET, 1.0f, 0.95f, 30, 8, 0x7b4f2c, "shotgun", 170, 9, 1800, 140, 2),
    SNIPER("Sniper", "Sniper", 95, 0.8f, 5, 25, 2.2f, 0.3f, 3400, 3000, 1, Projectile.Kind.BULLET, 1.8f, 0.9f, 42, 7, 0x2e4d2e, "sniper", 900, 6, 4750, 90, 3),
    LMG("Metralhadora", "Machine Gun", 12, 15f, 100, 200, 3.0f, 5f, 1700, 1050, 1, Projectile.Kind.BULLET, 1.15f, 0.8f, 36, 10, 0x404040, "lmg", 500, 6, 5200, 0, 3),
    ROCKET("Bazuca", "Rocket Launcher", 100, 0.9f, 1, 6, 1.2f, 1f, 820, 2400, 1, Projectile.Kind.ROCKET, 1.25f, 0.85f, 38, 11, 0x556b2f, "rocket", 520, 5, 4000, 120, 3),
    GRENADE_LAUNCHER("Lança-granadas", "Grenade Launcher", 85, 1.2f, 4, 16, 2.2f, 2f, 800, 1800, 1, Projectile.Kind.SHELL, 1.15f, 0.9f, 30, 11, 0x3d5c3d, "rocket", 420, 5, 3500, 60, 3),
    FLAMETHROWER("Lança-chamas", "Flamethrower", 5, 24f, 120, 240, 2.4f, 9f, 560, 270, 1, Projectile.Kind.FLAME, 1.0f, 0.95f, 32, 10, 0xb03a2e, "flame", 160, 5, 2500, 0, 2),
    LASER("Laser", "Laser Rifle", 65, 1.1f, 5, 15, 1.8f, 0f, 0, 2200, 1, Projectile.Kind.BULLET, 1.4f, 0.95f, 36, 8, 0x1abc9c, "laser", 700, 4, 4200, 30, 3),
    SAW("Disco Serra", "Saw Launcher", 50, 1.6f, 5, 20, 1.8f, 1f, 950, 2000, 1, Projectile.Kind.DISC, 1.1f, 0.95f, 28, 10, 0xc0392b, "disc", 450, 5, 3000, 0, 2);

    public final String pt, en;
    public final float damage, rate;
    public final int mag, reserve;
    public final float reload, spreadDeg, speed, range;
    public final int pellets;
    public final Projectile.Kind kind;
    public final float zoom, moveMul;
    public final float gunLen, gunH;
    public final int color;
    public final String sound;
    public final float botRange;
    public final int weight, price;
    public final float recoil;
    public final int tier;

    WeaponType(String pt, String en, float damage, float rate, int mag, int reserve, float reload, float spreadDeg,
               float speed, float range, int pellets, Projectile.Kind kind, float zoom, float moveMul, float gunLen,
               float gunH, int color, String sound, float botRange, int weight, int price, float recoil, int tier) {
        this.pt = pt;
        this.en = en;
        this.damage = damage;
        this.rate = rate;
        this.mag = mag;
        this.reserve = reserve;
        this.reload = reload;
        this.spreadDeg = spreadDeg;
        this.speed = speed;
        this.range = range;
        this.pellets = pellets;
        this.kind = kind;
        this.zoom = zoom;
        this.moveMul = moveMul;
        this.gunLen = gunLen;
        this.gunH = gunH;
        this.color = color;
        this.sound = sound;
        this.botRange = botRange;
        this.weight = weight;
        this.price = price;
        this.recoil = recoil;
        this.tier = tier;
    }

    public boolean isLaser() {
        return this == LASER;
    }

    public boolean isExplosive() {
        return kind == Projectile.Kind.ROCKET || kind == Projectile.Kind.SHELL;
    }

    public float lifetime() {
        return speed <= 0 ? 0 : range / speed;
    }

    private static final WeaponType[] VALUES = values();

    public static WeaponType of(int ordinal) {
        return ordinal < 0 || ordinal >= VALUES.length ? null : VALUES[ordinal];
    }

    /** Sorteia uma arma pelo peso de aparecimento (exclui a pistola). */
    public static WeaponType random(java.util.Random rng) {
        int total = 0;
        for (WeaponType w : VALUES) total += w.weight;
        int r = rng.nextInt(total);
        for (WeaponType w : VALUES) {
            r -= w.weight;
            if (r < 0) return w;
        }
        return RIFLE;
    }
}
