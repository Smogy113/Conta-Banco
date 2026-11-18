package com.brigadajato.world;

import com.badlogic.gdx.utils.Array;
import com.brigadajato.I18n;

/** Medalhas do fim da partida e o MVP, calculados a partir das estatisticas de cada jogador. */
public final class Medals {
    private Medals() {
    }

    public static final int MVP = 0, ACCURACY = 1, DAMAGE = 2, STREAK = 3, HEADSHOT = 4, LONG = 5, MELEE = 6, BOOM = 7, SURVIVOR = 8,
            OBJECTIVE = 9, KAMIKAZE = 10, COMBO = 11, TANK = 12, COUNT = 13;
    public static final int[] COLORS = {0xffc107, 0x29b6f6, 0xff7043, 0xffee58, 0xef5350, 0x26a69a, 0x8d6e63, 0xff9100, 0x66bb6a,
            0xab47bc, 0x78909c, 0xec407a, 0x5d4037};
    private static final String[][] NAMES = {
            {"MVP", "Olho de Águia", "Destruidor", "Imparável", "Na Cabeça", "Longa Distância", "Brigão", "Explosivo", "Sobrevivente",
                    "Estrategista", "Kamikaze", "Combo", "Caça-Tanque"},
            {"MVP", "Eagle Eye", "Destroyer", "Unstoppable", "Headhunter", "Long Shot", "Brawler", "Demolition", "Survivor",
                    "Strategist", "Kamikaze", "Combo", "Tank Hunter"}};

    public static class Award {
        public int medal;
        public Player p;
        public float value;

        Award(int medal, Player p, float value) {
            this.medal = medal;
            this.p = p;
            this.value = value;
        }
    }

    public static String name(int m) {
        return NAMES[I18n.pt ? 0 : 1][m];
    }

    public static String detail(int m, float v) {
        boolean pt = I18n.pt;
        switch (m) {
            case MVP: return pt ? "Melhor da partida" : "Best of the match";
            case ACCURACY: return Math.round(v * 100) + (pt ? "% de precisão" : "% accuracy");
            case DAMAGE: return Math.round(v) + (pt ? " de dano" : " damage");
            case STREAK: return (int) v + (pt ? " abates seguidos" : " kills in a row");
            case HEADSHOT: return (int) v + (pt ? " na cabeça" : " headshots");
            case LONG: return Math.round(v / 32f) + (pt ? " m de distância" : " m away");
            case MELEE: return (int) v + (pt ? " abates no soco" : " melee kills");
            case BOOM: return (int) v + (pt ? " abates explosivos" : " explosive kills");
            case SURVIVOR: return (int) v == 0 ? (pt ? "Nenhuma morte!" : "No deaths!") : (pt ? "Só " : "Only ") + (int) v + ((int) v == 1 ? (pt ? " morte" : " death") : (pt ? " mortes" : " deaths"));
            case OBJECTIVE: return (int) v + (pt ? " objetivos" : " objectives");
            case KAMIKAZE: return (int) v + (pt ? " vezes se matou" : " self-kills");
            case COMBO: return (int) v + (pt ? " abates duplos" : " double kills");
            case TANK: return (int) v + (pt ? " Juggernauts" : " Juggernauts");
            default: return "";
        }
    }

    public static float accuracy(Player p) {
        return p.shotsFired <= 0 ? 0 : Math.min(1f, p.shotsHit / (float) p.shotsFired);
    }

    /** Pontuacao de impacto para o MVP. */
    public static float impact(Player p) {
        return p.kills * 100 + p.objectives * 250 + p.damageDealt * 0.6f + p.score * 40 - p.deaths * 25 + p.headshots * 20 + p.bestStreak * 15;
    }

    public static Array<Award> compute(World w) {
        Array<Award> out = new Array<>();
        Array<Player> ps = new Array<>();
        Array<Player> rank = w.ranking();
        for (int i = 0; i < rank.size; i++) if (!rank.get(i).dummy) ps.add(rank.get(i));
        if (ps.size == 0) return out;
        Player mvp = null;
        float best = -1e9f;
        for (Player p : ps) {
            float v = impact(p);
            if (v > best) {
                best = v;
                mvp = p;
            }
        }
        if (mvp != null && (mvp.kills > 0 || mvp.score > 0 || mvp.objectives > 0)) out.add(new Award(MVP, mvp, best));
        pick(out, ps, ACCURACY, 0.2f, p -> p.shotsFired >= 15 ? accuracy(p) : -1);
        pick(out, ps, DAMAGE, 150, p -> p.damageDealt);
        pick(out, ps, STREAK, 3, p -> p.bestStreak);
        pick(out, ps, HEADSHOT, 1, p -> p.headshots);
        pick(out, ps, LONG, 500, p -> p.longestKill);
        pick(out, ps, MELEE, 1, p -> p.meleeKills);
        pick(out, ps, BOOM, 1, p -> p.explosiveKills);
        pick(out, ps, OBJECTIVE, 1, p -> p.objectives);
        pick(out, ps, COMBO, 1, p -> p.multiKills);
        pick(out, ps, TANK, 1, p -> p.juggKills);
        // sobrevivente: quem morreu menos entre quem abateu alguem
        Player surv = null;
        for (Player p : ps) if (p.kills >= 2 && (surv == null || p.deaths < surv.deaths)) surv = p;
        if (surv != null && ps.size > 1) out.add(new Award(SURVIVOR, surv, surv.deaths));
        pick(out, ps, KAMIKAZE, 2, p -> p.suicides);
        return out;
    }

    private interface Stat {
        float of(Player p);
    }

    private static void pick(Array<Award> out, Array<Player> ps, int medal, float min, Stat st) {
        Player best = null;
        float bv = -1;
        for (Player p : ps) {
            float v = st.of(p);
            if (v > bv) {
                bv = v;
                best = p;
            }
        }
        if (best != null && bv >= min) out.add(new Award(medal, best, bv));
    }
}
