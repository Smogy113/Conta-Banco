package com.brigadajato.world;

/** Aparencia do soldado (tudo personalizavel). */
public class Look {
    public static final int[] PALETTE = {
            0x4caf50, 0x2e7d32, 0x8d6e63, 0x795548, 0xff9800, 0xf44336, 0xe91e63, 0x9c27b0,
            0x3f51b5, 0x2196f3, 0x00bcd4, 0x009688, 0xcddc39, 0xffeb3b, 0x607d8b, 0x212121,
            0xffffff, 0x6d4c41, 0xbf360c, 0x1a237e, 0x556b2f, 0xc2b280, 0xff6f00, 0x4a148c};
    public static final int[] SKINS = {0xffdbac, 0xf1c27d, 0xe0ac69, 0xc68642, 0x8d5524, 0x5c3a1e, 0x9ccc65, 0x90caf9};
    public static final int[] HAIR_COLORS = {0x1b1b1b, 0x4e342e, 0x8d6e63, 0xf9a825, 0xd84315, 0xeeeeee, 0x7e57c2, 0x26c6da, 0xec407a, 0x43a047};
    /** cores da chama do jetpack (o ultimo e arco-iris) */
    public static final int[] FLAMES = {0xffab00, 0x40c4ff, 0x76ff03, 0xd500f9, 0xff4081, 0xffffff, -1};

    public static final int HAT_COUNT = 14;
    public static final int HAIR_COUNT = 7;
    public static final int EYES_COUNT = 6;
    public static final int MOUTH_COUNT = 6;
    public static final int BEARD_COUNT = 5;
    public static final int EYEWEAR_COUNT = 7;
    public static final int PATTERN_COUNT = 7;
    public static final int JETPACK_COUNT = 5;
    public static final int GUNSKIN_COUNT = 7;
    public static final int ACCESSORY_COUNT = 5;

    public int body = 0x4caf50;
    public int helmet = 0x2e7d32;
    public int visor = 0x00bcd4;
    public int skin = 0xf1c27d;
    /** 0 capacete, 1 bone, 2 bandana, 3 boina, 4 cartola, 5 moicano, 6 nada, 7 cowboy, 8 gorro, 9 coroa, 10 viking, 11 headset, 12 bone virado, 13 capacete espacial */
    public int hat = 0;
    /** 0 careca, 1 curto, 2 espetado, 3 comprido, 4 black power, 5 rabo de cavalo, 6 topete */
    public int hair = 1;
    public int hairColor = 0x4e342e;
    /** 0 normal, 1 bravo, 2 feliz, 3 sonolento, 4 robo, 5 surpreso */
    public int eyes = 0;
    /** 0 neutro, 1 sorriso, 2 dentes cerrados, 3 grito, 4 assobio, 5 lingua */
    public int mouth = 1;
    /** 0 nada, 1 por fazer, 2 cheia, 3 bigode, 4 cavanhaque */
    public int beard = 0;
    /** 0 oculos de protecao, 1 nada, 2 oculos escuros, 3 tapa-olho, 4 mascara ninja, 5 mascara de gas, 6 monoculo */
    public int eyewear = 0;
    /** 0 liso, 1 camuflado, 2 listras, 3 tigre, 4 estrelas, 5 xadrez, 6 raio */
    public int pattern = 0;
    public int patternColor = 0x2e7d32;
    public int pants = 0x37474f;
    /** 0 padrao, 1 foguete duplo, 2 barril, 3 asas, 4 tanque */
    public int jetpack = 0;
    public int jetColor = 0x7f8c8d;
    /** indice em FLAMES */
    public int flame = 0;
    /** 0 padrao, 1 ouro, 2 camuflado, 3 neon, 4 gelo, 5 lava, 6 rosa */
    public int gunSkin = 0;
    /** 0 nada, 1 cachecol, 2 capa, 3 colete, 4 medalha */
    public int accessory = 0;
    public int accessoryColor = 0xc62828;

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
        Look l = new Look();
        l.set(this);
        return l;
    }

    public void set(Look o) {
        body = o.body;
        helmet = o.helmet;
        visor = o.visor;
        skin = o.skin;
        hat = o.hat;
        hair = o.hair;
        hairColor = o.hairColor;
        eyes = o.eyes;
        mouth = o.mouth;
        beard = o.beard;
        eyewear = o.eyewear;
        pattern = o.pattern;
        patternColor = o.patternColor;
        pants = o.pants;
        jetpack = o.jetpack;
        jetColor = o.jetColor;
        flame = o.flame;
        gunSkin = o.gunSkin;
        accessory = o.accessory;
        accessoryColor = o.accessoryColor;
    }

    /** Cor da chama (arco-iris muda com o tempo). */
    public int flameColor(float time) {
        int c = FLAMES[Math.max(0, Math.min(FLAMES.length - 1, flame))];
        if (c != -1) return c;
        int[] rb = {0xff1744, 0xff9100, 0xffea00, 0x00e676, 0x2979ff, 0xd500f9};
        return rb[(int) (time * 10) % rb.length];
    }

    /** Uniformes que combinam: corpo, calca, capacete, cor de destaque. */
    public static final int[][] OUTFITS = {
            {0x5b7a3a, 0x3e4a2c, 0x4f6b33, 0xd4a017}, // verde oliva
            {0xc2a46b, 0x7a6544, 0xa98c55, 0x8d2f23}, // deserto
            {0x3f5873, 0x2b3240, 0x34495e, 0xf2c14e}, // marinha
            {0x6d6f73, 0x34363a, 0x55585c, 0xff7043}, // urbano
            {0x8e3b2e, 0x2f2a28, 0x6e2b22, 0xf5d76e}, // vermelho tijolo
            {0x2f6f6a, 0x23383a, 0x2a5a56, 0xffb74d}, // petroleo
            {0x7d5ba6, 0x3a2f4a, 0x5f4785, 0x80deea}, // roxo
            {0xd9822b, 0x4a3b2e, 0xb86c22, 0x263238}, // laranja
            {0x4e86c4, 0x2c3e57, 0x3d6fa8, 0xffeb3b}, // azul celeste
            {0x8a9a5b, 0x4d5532, 0x6f7d47, 0xe57373}, // sálvia
            {0xb0bec5, 0x455a64, 0x90a4ae, 0x26c6da}, // inverno
            {0x3a3a3a, 0x1f1f1f, 0x2e2e2e, 0xe53935}, // operacoes especiais
    };

    public static Look random(java.util.Random r) {
        int[] o = OUTFITS[r.nextInt(OUTFITS.length)];
        Look l = new Look(o[0], o[2], 0x37474f, SKINS[r.nextInt(6)], r.nextInt(HAT_COUNT));
        l.visor = r.nextFloat() < 0.5f ? 0x263238 : mixc(o[3], 0x263238, 0.3f);
        l.hair = r.nextInt(HAIR_COUNT);
        l.hairColor = HAIR_COLORS[r.nextInt(6)];
        l.eyes = r.nextFloat() < 0.6f ? 0 : r.nextInt(EYES_COUNT - 1);
        l.mouth = r.nextFloat() < 0.5f ? 0 : r.nextInt(MOUTH_COUNT);
        l.beard = r.nextFloat() < 0.65f ? 0 : r.nextInt(BEARD_COUNT);
        l.eyewear = r.nextFloat() < 0.55f ? 0 : r.nextInt(EYEWEAR_COUNT);
        l.pattern = r.nextFloat() < 0.65f ? 0 : 1 + r.nextInt(2); // camuflado ou listras, discreto
        l.patternColor = mixc(o[0], 0x000000, 0.28f);
        l.pants = o[1];
        l.jetpack = r.nextInt(JETPACK_COUNT);
        l.jetColor = mixc(o[2], 0x607d8b, 0.5f);
        l.flame = r.nextFloat() < 0.7f ? 0 : r.nextInt(FLAMES.length);
        l.gunSkin = r.nextFloat() < 0.75f ? 0 : r.nextInt(GUNSKIN_COUNT);
        l.accessory = r.nextFloat() < 0.6f ? 0 : r.nextInt(ACCESSORY_COUNT);
        l.accessoryColor = o[3];
        return l;
    }

    private static int mixc(int a, int b, float t) {
        int rr = (int) (((a >> 16) & 0xff) * (1 - t) + ((b >> 16) & 0xff) * t);
        int gg = (int) (((a >> 8) & 0xff) * (1 - t) + ((b >> 8) & 0xff) * t);
        int bb = (int) ((a & 0xff) * (1 - t) + (b & 0xff) * t);
        return (rr << 16) | (gg << 8) | bb;
    }
}
