package com.brigadajato.world;

/** Tema visual de um mapa (cores em 0xRRGGBB + estilos de fundo, blocos e clima). */
public enum Theme {
    //            ceu topo  ceu base  bloco     escuro    topo      plataf.   longe     meio      perto     fundo                  bloco              clima           escuro
    DESERT(0x6fb7e8, 0xf6d7a0, 0xc98a4b, 0x8c5a2b, 0xe9c46a, 0xa0522d, 0xd9a066, 0xb7794a, 0x8e5a3a, Bg.MOUNTAINS, Tiles.BRICK, Amb.DUST, false),
    FACTORY(0x3b4a6b, 0x9aa7b8, 0x6b7280, 0x3f4651, 0x9ca3af, 0x7c4a1e, 0x4b5563, 0x374151, 0x1f2937, Bg.BUILDINGS, Tiles.METAL, Amb.SOOT, false),
    SKY(0x3fa9f5, 0xd6f0ff, 0x7cb342, 0x55833a, 0xa5d65a, 0x8d6e63, 0xffffff, 0xcfe8ff, 0x9cc9f0, Bg.ISLANDS, Tiles.DIRT, Amb.NONE, false),
    ICE(0x1e2a4a, 0x7f9cc9, 0x8fb8de, 0x4f7aa8, 0xeef6ff, 0x5b7fa6, 0x34507a, 0x2a4066, 0x1b2d4f, Bg.ICE_PEAKS, Tiles.ICE, Amb.SNOW, false),
    TRAINING(0x87c5ea, 0xe8f4fb, 0x9e9e9e, 0x616161, 0xffca28, 0xff8f00, 0xb0bec5, 0x90a4ae, 0x78909c, Bg.MOUNTAINS, Tiles.CONCRETE, Amb.NONE, false),
    JUNGLE(0x7ec8e3, 0xe0f2e9, 0x6d4c41, 0x3e2723, 0x43a047, 0x8d6e63, 0x5e9e6e, 0x3f7f4f, 0x2e5e3a, Bg.FOREST, Tiles.DIRT, Amb.LEAVES, false),
    SPACE(0x02030a, 0x1a1f4a, 0x9e9e9e, 0x5f5f5f, 0xd6d6d6, 0x546e7a, 0x232a5c, 0x3a3f55, 0x4a4f62, Bg.MOON, Tiles.ROCK, Amb.STARS, true),
    // ---- novos
    SHIP(0x4fa3e0, 0xcfeaff, 0x8d5a2b, 0x5d3a1a, 0xc8a165, 0x6d4c41, 0x1565c0, 0x1e88e5, 0x90caf9, Bg.OCEAN, Tiles.WOOD, Amb.NONE, false),
    HOLD(0x2b1d12, 0x4e3420, 0x6d4422, 0x3e2414, 0x8d6e48, 0x5d4037, 0x3e2a1a, 0x4e3524, 0x5d4030, Bg.INDOOR_WOOD, Tiles.WOOD, Amb.DRIPS, true),
    CITY(0x0b1026, 0x2c2f5a, 0x5f6470, 0x30333b, 0x8a8f99, 0x37474f, 0x1c2140, 0x262b52, 0x30365f, Bg.CITY_NIGHT, Tiles.CONCRETE, Amb.RAIN, true),
    SEWER(0x101a14, 0x22352a, 0x4f5d4f, 0x2c362c, 0x6b8f5e, 0x455a45, 0x1d2b22, 0x24362a, 0x2c4233, Bg.CAVE, Tiles.BRICK, Amb.DRIPS, true),
    VOLCANO(0x3b0f0a, 0xd9572b, 0x5a4a45, 0x2e2420, 0x8a6e62, 0x4e342e, 0x4a1c14, 0x6d2a1a, 0x8f3a1e, Bg.VOLCANO, Tiles.ROCK, Amb.EMBERS, false),
    MAGMA(0x1a0503, 0x5a1408, 0x3e2b26, 0x1e1412, 0xff7043, 0x4e342e, 0x3a0d06, 0x55160a, 0x6e1f0c, Bg.CAVE, Tiles.ROCK, Amb.EMBERS, true),
    UNDERSEA(0x062a4a, 0x1678a8, 0x78909c, 0x455a64, 0xb0bec5, 0x546e7a, 0x0b3d63, 0x0e4f80, 0x1565a0, Bg.UNDERWATER, Tiles.METAL, Amb.BUBBLES, false),
    REACTOR(0x07121c, 0x153447, 0x4a5a66, 0x263038, 0x4dd0e1, 0x37474f, 0x0f2433, 0x15303f, 0x1d3d4f, Bg.INDOOR_LAB, Tiles.METAL, Amb.SOOT, true),
    SCHOOL(0xf2e6c9, 0xfdf6e3, 0xb0a08a, 0x7a6a55, 0xd7ccc8, 0x8d6e63, 0xa5d6a7, 0x81c784, 0x66bb6a, Bg.INDOOR_SCHOOL, Tiles.TILE_FLOOR, Amb.NONE, false),
    GYM(0x5d4037, 0x8d6e63, 0xc68c53, 0x8d5a2b, 0xe0b37a, 0x6d4c41, 0x6d4c41, 0x795548, 0x8d6e63, Bg.INDOOR_WOOD, Tiles.WOOD, Amb.NONE, false),
    MARKET(0x8fd3f5, 0xfff3d6, 0xc2a07a, 0x8a6b4a, 0xe8c99b, 0x9c6b3e, 0xe57373, 0xffb74d, 0x81c784, Bg.MARKET, Tiles.BRICK, Amb.DUST, false),
    CELLAR(0x1b1410, 0x3a2b20, 0x6b5a4a, 0x3a2e24, 0x8d7b68, 0x5d4037, 0x2e231b, 0x3b2d22, 0x47362a, Bg.INDOOR_WOOD, Tiles.BRICK, Amb.DRIPS, true);

    /** estilos de fundo */
    public enum Bg {MOUNTAINS, BUILDINGS, ISLANDS, ICE_PEAKS, FOREST, MOON, OCEAN, INDOOR_WOOD, CITY_NIGHT, CAVE, VOLCANO, UNDERWATER, INDOOR_LAB, INDOOR_SCHOOL, MARKET}

    /** estilos de bloco */
    public enum Tiles {BRICK, METAL, DIRT, ICE, CONCRETE, ROCK, WOOD, TILE_FLOOR}

    /** clima / particulas de ambiente */
    public enum Amb {NONE, DUST, SOOT, SNOW, LEAVES, STARS, DRIPS, RAIN, EMBERS, BUBBLES}

    public final int skyTop, skyBottom, tile, tileDark, tileTop, platform, far, mid, near;
    public final Bg bg;
    public final Tiles tiles;
    public final Amb ambient;
    /** ambiente fechado/escuro (fica escuro a noite e usa mais luz) */
    public final boolean dark;

    Theme(int skyTop, int skyBottom, int tile, int tileDark, int tileTop, int platform, int far, int mid, int near,
          Bg bg, Tiles tiles, Amb ambient, boolean dark) {
        this.skyTop = skyTop;
        this.skyBottom = skyBottom;
        this.tile = tile;
        this.tileDark = tileDark;
        this.tileTop = tileTop;
        this.platform = platform;
        this.far = far;
        this.mid = mid;
        this.near = near;
        this.bg = bg;
        this.tiles = tiles;
        this.ambient = ambient;
        this.dark = dark;
    }

    public static Theme parse(String s) {
        String v = s.trim();
        for (Theme t : values()) if (t.name().equalsIgnoreCase(v)) return t;
        return DESERT;
    }
}
