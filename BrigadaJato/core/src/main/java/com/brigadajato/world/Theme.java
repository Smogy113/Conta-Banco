package com.brigadajato.world;

/** Tema visual de um mapa (cores em 0xRRGGBB). */
public enum Theme {
    DESERT(0x6fb7e8, 0xf6d7a0, 0xc98a4b, 0x8c5a2b, 0xe9c46a, 0xa0522d, 0xd9a066, 0xb7794a, 0x8e5a3a),
    FACTORY(0x3b4a6b, 0x9aa7b8, 0x6b7280, 0x3f4651, 0x9ca3af, 0x7c4a1e, 0x4b5563, 0x374151, 0x1f2937),
    SKY(0x3fa9f5, 0xd6f0ff, 0x7cb342, 0x55833a, 0xa5d65a, 0x8d6e63, 0xffffff, 0xcfe8ff, 0x9cc9f0),
    ICE(0x1e2a4a, 0x7f9cc9, 0x8fb8de, 0x4f7aa8, 0xeef6ff, 0x5b7fa6, 0x34507a, 0x2a4066, 0x1b2d4f),
    TRAINING(0x87c5ea, 0xe8f4fb, 0x9e9e9e, 0x616161, 0xffca28, 0xff8f00, 0xb0bec5, 0x90a4ae, 0x78909c);

    public final int skyTop, skyBottom, tile, tileDark, tileTop, platform, far, mid, near;

    Theme(int skyTop, int skyBottom, int tile, int tileDark, int tileTop, int platform, int far, int mid, int near) {
        this.skyTop = skyTop;
        this.skyBottom = skyBottom;
        this.tile = tile;
        this.tileDark = tileDark;
        this.tileTop = tileTop;
        this.platform = platform;
        this.far = far;
        this.mid = mid;
        this.near = near;
    }

    public static Theme parse(String s) {
        for (Theme t : values()) if (t.name().equalsIgnoreCase(s.trim())) return t;
        if ("training".equalsIgnoreCase(s.trim())) return TRAINING;
        return DESERT;
    }
}
