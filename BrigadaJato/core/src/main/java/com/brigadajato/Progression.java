package com.brigadajato;

/**
 * XP, niveis e desbloqueios. Cada partida da XP (abates, vitoria, ondas). Itens de visual e
 * armas comecam travados e liberam por nivel. Na versao de teste (TEST_BUILD) todo mundo ja esta
 * no nivel maximo com tudo liberado.
 */
public final class Progression {
    private Progression() {
    }

    /** versao de teste: nivel maximo e tudo liberado */
    public static final boolean TEST_BUILD = true;
    public static final int MAX_LEVEL = 50;

    /** XP total para chegar no nivel n (curva suave). */
    public static int xpFor(int level) {
        int l = Math.max(1, level) - 1;
        return 100 * l + 25 * l * l;
    }

    public static int level(int xp) {
        if (TEST_BUILD) return MAX_LEVEL;
        int lv = 1;
        while (lv < MAX_LEVEL && xp >= xpFor(lv + 1)) lv++;
        return lv;
    }

    /** Progresso (0..1) ate o proximo nivel. */
    public static float progress(int xp) {
        int lv = level(xp);
        if (lv >= MAX_LEVEL) return 1;
        int a = xpFor(lv), b = xpFor(lv + 1);
        return (xp - a) / (float) Math.max(1, b - a);
    }

    /** Nivel pedido por um item de uma categoria (indices altos destravam mais tarde). */
    public static int required(String category, int index) {
        if (index <= 1) return 1;
        switch (category) {
            case "hat": return Math.min(MAX_LEVEL, 2 + index * 3);
            case "pattern": return 3 + index * 4;
            case "jetpack": return 5 + index * 6;
            case "flame": return 4 + index * 5;
            case "gunskin": return 6 + index * 6;
            case "eyewear": return 2 + index * 3;
            case "accessory": return 8 + index * 6;
            default: return 1 + index * 2;
        }
    }

    public static boolean unlocked(Settings s, String category, int index) {
        return TEST_BUILD || level(s.xp) >= required(category, index);
    }

    /** XP de uma partida. */
    public static int matchXp(int kills, boolean win, int wave, int bossKills) {
        return kills * 10 + (win ? 60 : 15) + wave * 20 + bossKills * 100;
    }
}
