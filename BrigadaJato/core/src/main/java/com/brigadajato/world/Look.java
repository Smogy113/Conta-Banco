package com.brigadajato.world;

/** Aparencia do soldado. */
public class Look {
    public static final int[] PALETTE = {
            0x4caf50, 0x2e7d32, 0x8d6e63, 0x795548, 0xff9800, 0xf44336, 0xe91e63, 0x9c27b0,
            0x3f51b5, 0x2196f3, 0x00bcd4, 0x009688, 0xcddc39, 0xffeb3b, 0x607d8b, 0x212121,
            0xffffff, 0x6d4c41, 0xbf360c, 0x1a237e};
    public static final int[] SKINS = {0xf1c27d, 0xe0ac69, 0xc68642, 0x8d5524, 0xffdbac, 0x5c3a1e};
    public static final int HAT_COUNT = 7;

    public int body = 0x4caf50;
    public int helmet = 0x2e7d32;
    public int visor = 0x00bcd4;
    public int skin = 0xf1c27d;
    /** 0 capacete, 1 bone, 2 bandana, 3 boina, 4 cartola, 5 moicano, 6 nada */
    public int hat = 0;

    public Look() {
    }

    public Look(int body, int helmet, int visor, int skin, int hat) {
        this.body = body;
        this.helmet = helmet;
        this.visor = visor;
        this.skin = skin;
        this.hat = hat;
    }

    public Look copy() {
        return new Look(body, helmet, visor, skin, hat);
    }

    public static Look random(java.util.Random r) {
        return new Look(PALETTE[r.nextInt(PALETTE.length)], PALETTE[r.nextInt(PALETTE.length)],
                PALETTE[r.nextInt(PALETTE.length)], SKINS[r.nextInt(SKINS.length)], r.nextInt(HAT_COUNT));
    }
}
