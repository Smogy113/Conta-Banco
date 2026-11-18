package com.brigadajato;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.brigadajato.world.Medals;

/** Conquistas permanentes: progresso salvo, recompensa de XP e aviso na tela ao desbloquear. */
public class Achievements {
    public static class Def {
        public final String id, pt, en, dpt, den;
        public final int target, xp, sym, color;
        /** progresso e um "recorde" (maior valor) em vez de soma */
        public final boolean best, bits;

        Def(String id, int sym, int color, int target, int xp, boolean best, boolean bits, String pt, String en, String dpt, String den) {
            this.id = id;
            this.sym = sym;
            this.color = color;
            this.target = target;
            this.xp = xp;
            this.best = best;
            this.bits = bits;
            this.pt = pt;
            this.en = en;
            this.dpt = dpt;
            this.den = den;
        }

        public String name() {
            return I18n.pt ? pt : en;
        }

        public String desc() {
            return I18n.pt ? dpt : den;
        }
    }

    private static final int GOLD = 0xffc107, RED = 0xef5350, BLUE = 0x29b6f6, GREEN = 0x66bb6a, PURPLE = 0xab47bc, ORANGE = 0xff9100,
            TEAL = 0x26a69a, BROWN = 0x8d6e63, PINK = 0xec407a, GRAY = 0x78909c;

    public static final Def[] DEFS = {
            new Def("first_blood", Medals.HEADSHOT, RED, 1, 50, false, false, "Primeiro Sangue", "First Blood", "Faça seu primeiro abate", "Get your first kill"),
            new Def("kills100", Medals.DAMAGE, ORANGE, 100, 300, false, false, "Centurião", "Centurion", "100 abates no total", "100 total kills"),
            new Def("kills1000", Medals.MVP, GOLD, 1000, 1500, false, false, "Lenda", "Legend", "1000 abates no total", "1000 total kills"),
            new Def("headshot25", Medals.ACCURACY, BLUE, 25, 300, false, false, "Atirador de Elite", "Sharpshooter", "25 abates na cabeça", "25 headshot kills"),
            new Def("streak5", Medals.STREAK, GOLD, 5, 200, true, false, "Imparável", "Unstoppable", "5 abates sem morrer", "5 kills without dying"),
            new Def("streak10", Medals.STREAK, PURPLE, 10, 600, true, false, "Divino", "Godlike", "10 abates sem morrer", "10 kills without dying"),
            new Def("multi3", Medals.COMBO, PINK, 3, 300, true, false, "Massacre", "Massacre", "3 abates em sequência rápida", "3 kills in quick succession"),
            new Def("win1", Medals.MVP, GREEN, 1, 100, false, false, "Primeira Vitória", "First Win", "Vença uma partida", "Win a match"),
            new Def("wins25", Medals.MVP, GOLD, 25, 800, false, false, "Veterano", "Veteran", "Vença 25 partidas", "Win 25 matches"),
            new Def("mvp5", Medals.MVP, ORANGE, 5, 500, false, false, "Craque", "Ace", "Seja MVP 5 vezes", "Be MVP 5 times"),
            new Def("flawless", Medals.SURVIVOR, GREEN, 1, 500, false, false, "Impecável", "Flawless", "Vença sem morrer (com 5+ abates)", "Win without dying (5+ kills)"),
            new Def("melee10", Medals.MELEE, BROWN, 10, 250, false, false, "Mão Pesada", "Heavy Hands", "10 abates no soco", "10 melee kills"),
            new Def("boom25", Medals.BOOM, ORANGE, 25, 300, false, false, "Demolidor", "Demolisher", "25 abates com explosivos", "25 explosive kills"),
            new Def("long", Medals.LONG, TEAL, 1, 200, false, false, "Tiro Longo", "Long Shot", "Abata alguém a mais de 30 m", "Kill someone 30+ m away"),
            new Def("jugg_down", Medals.TANK, BROWN, 1, 200, false, false, "Caça-Tanque", "Tank Hunter", "Derrube o Juggernaut", "Take down the Juggernaut"),
            new Def("jugg_kills", Medals.TANK, RED, 15, 400, false, false, "Rolo Compressor", "Steamroller", "15 abates como Juggernaut", "15 kills as the Juggernaut"),
            new Def("flags5", Medals.OBJECTIVE, GREEN, 5, 300, false, false, "Porta-Bandeira", "Flag Bearer", "Capture 5 bandeiras", "Capture 5 flags"),
            new Def("bombs5", Medals.KAMIKAZE, RED, 5, 300, false, false, "Especialista", "Specialist", "Plante ou desarme 5 bombas", "Plant or defuse 5 bombs"),
            new Def("wave10", Medals.SURVIVOR, PURPLE, 10, 600, true, false, "Resistência", "Endurance", "Chegue na onda 10 da Sobrevivência", "Reach wave 10 in Survival"),
            new Def("champion", Medals.MVP, GOLD, 1, 1000, false, false, "Campeão", "Champion", "Vença um torneio", "Win a tournament"),
            new Def("modes6", Medals.STREAK, BLUE, 6, 600, false, true, "Versátil", "Versatile", "Vença em 6 modos diferentes", "Win in 6 different modes"),
            new Def("maps10", Medals.LONG, GREEN, 10, 400, false, true, "Turista", "Tourist", "Jogue em 10 mapas diferentes", "Play on 10 different maps"),
            new Def("instagib", Medals.ACCURACY, RED, 1, 250, false, false, "Um Tiro, Um Abate", "One Shot, One Kill", "Vença com a regra Um tiro mata", "Win with One shot kills"),
            new Def("spectator", Medals.LONG, GRAY, 1, 100, false, false, "Comentarista", "Commentator", "Assista uma partida inteira", "Watch a full match"),
            new Def("matches50", Medals.OBJECTIVE, TEAL, 50, 500, false, false, "Dedicado", "Dedicated", "Jogue 50 partidas", "Play 50 matches"),
    };

    public interface Listener {
        void unlocked(Def d);
    }

    private final Settings settings;
    private final ObjectIntMap<String> values = new ObjectIntMap<>();
    /** conquistas desbloqueadas que ainda nao foram mostradas na tela */
    public final Array<Def> pending = new Array<>();

    public Achievements(Settings settings) {
        this.settings = settings;
        load();
    }

    private void load() {
        values.clear();
        if (settings.achData == null || settings.achData.isEmpty()) return;
        for (String part : settings.achData.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            try {
                values.put(part.substring(0, eq), Integer.parseInt(part.substring(eq + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void store() {
        StringBuilder sb = new StringBuilder();
        for (ObjectIntMap.Entry<String> e : values) sb.append(e.key).append('=').append(e.value).append(';');
        settings.achData = sb.toString();
    }

    public static Def def(String id) {
        for (Def d : DEFS) if (d.id.equals(id)) return d;
        return null;
    }

    public int progress(Def d) {
        int v = values.get(d.id, 0);
        return d.bits ? Integer.bitCount(v) : v;
    }

    public boolean unlocked(Def d) {
        return values.get(d.id + "!", 0) == 1;
    }

    public int unlockedCount() {
        int n = 0;
        for (Def d : DEFS) if (unlocked(d)) n++;
        return n;
    }

    /** Soma (ou recorde, ou bit) no progresso de uma conquista. */
    public void add(String id, int amount) {
        Def d = def(id);
        if (d == null || unlocked(d)) return;
        int v = values.get(id, 0);
        if (d.bits) v |= 1 << Math.min(30, amount);
        else if (d.best) v = Math.max(v, amount);
        else v += amount;
        values.put(id, v);
        if (progress(d) >= d.target) {
            values.put(id + "!", 1);
            settings.xp += d.xp;
            pending.add(d);
        }
        store();
    }

    public void save() {
        store();
        settings.save();
    }
}
