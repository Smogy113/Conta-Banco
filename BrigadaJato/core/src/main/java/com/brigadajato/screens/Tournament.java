package com.brigadajato.screens;

import com.brigadajato.world.GameMap;
import com.brigadajato.world.Look;
import com.brigadajato.world.Names;

import java.util.Random;

/** Estado de um torneio offline: chave de 8 (voce + 7 bots), quartas, semi e final em duelos. */
public class Tournament {
    public static final int SIZE = 8, ROUNDS = 3;
    public final String[] names = new String[SIZE];
    public final Look[] looks = new Look[SIZE];
    /** forca de cada bot (0..1); o jogador e o indice 0 */
    public final float[] rating = new float[SIZE];
    /** vencedores de cada rodada: winners[r][i] = indice do participante */
    public final int[][] winners = new int[ROUNDS][];
    /** placares (vencedor x perdedor) de cada partida */
    public final int[][] scoreW = new int[ROUNDS][], scoreL = new int[ROUNDS][];
    public int round;
    public boolean playerOut, finished;
    /** rodada cujo resultado acabou de sair (para animar) */
    public int justDecided = -1;
    public String[] maps = new String[ROUNDS];
    private final Random rng = new Random();

    private static final String[] POOL = {"fortaleza", "fabrica", "ilhas", "caverna", "selva", "lua", "navio", "cidade", "escola", "mercado", "estacao", "poeira"};

    public Tournament(String playerName, Look playerLook) {
        names[0] = playerName;
        looks[0] = playerLook;
        rating[0] = 0.5f;
        int start = rng.nextInt(Names.BOTS.length);
        for (int i = 1; i < SIZE; i++) {
            names[i] = Names.BOTS[(start + i * 3) % Names.BOTS.length];
            looks[i] = Look.random(rng);
            rating[i] = 0.25f + rng.nextFloat() * 0.6f;
        }
        // embaralha a posicao do jogador na chave
        int pos = rng.nextInt(SIZE);
        swap(0, pos);
        for (int r = 0; r < ROUNDS; r++) {
            int n = SIZE >> (r + 1);
            winners[r] = new int[n];
            scoreW[r] = new int[n];
            scoreL[r] = new int[n];
            java.util.Arrays.fill(winners[r], -1);
            maps[r] = POOL[rng.nextInt(POOL.length)];
        }
    }

    private int playerSlot = 0;

    private void swap(int a, int b) {
        String n = names[a];
        names[a] = names[b];
        names[b] = n;
        Look l = looks[a];
        looks[a] = looks[b];
        looks[b] = l;
        float r = rating[a];
        rating[a] = rating[b];
        rating[b] = r;
        if (a == playerSlot) playerSlot = b;
        else if (b == playerSlot) playerSlot = a;
    }

    public int player() {
        return playerSlot;
    }

    /** Participantes da partida i da rodada r (ou -1 se ainda nao definido). */
    public int entrant(int r, int match, int side) {
        if (r == 0) return match * 2 + side;
        return winners[r - 1][match * 2 + side];
    }

    /** Partida do jogador na rodada atual (indice), ou -1. */
    public int playerMatch() {
        if (playerOut || finished) return -1;
        int n = SIZE >> (round + 1);
        for (int i = 0; i < n; i++) if (entrant(round, i, 0) == playerSlot || entrant(round, i, 1) == playerSlot) return i;
        return -1;
    }

    public int opponent() {
        int m = playerMatch();
        if (m < 0) return -1;
        int a = entrant(round, m, 0), b = entrant(round, m, 1);
        return a == playerSlot ? b : a;
    }

    /** dificuldade do adversario: quartas facil, semi normal, final dificil */
    public int difficulty() {
        return Math.min(2, round);
    }

    public String roundKey(int r) {
        return r == 0 ? "t_quarter" : (r == 1 ? "t_semi" : "t_final");
    }

    /** Resultado da partida do jogador; simula as outras da rodada (e o resto, se ele caiu). */
    public void playerResult(boolean win, int myScore, int theirScore) {
        int m = playerMatch();
        int opp = opponent();
        if (m >= 0) {
            winners[round][m] = win ? playerSlot : opp;
            scoreW[round][m] = win ? Math.max(myScore, 1) : Math.max(theirScore, 1);
            scoreL[round][m] = win ? theirScore : myScore;
        }
        simulateRound(round);
        justDecided = round;
        if (!win) playerOut = true;
        round++;
        if (playerOut) {
            while (round < ROUNDS) {
                simulateRound(round);
                round++;
            }
        }
        if (round >= ROUNDS) finished = true;
    }

    private void simulateRound(int r) {
        int n = SIZE >> (r + 1);
        for (int i = 0; i < n; i++) {
            if (winners[r][i] >= 0) continue;
            int a = entrant(r, i, 0), b = entrant(r, i, 1);
            float pa = 0.5f + (rating[a] - rating[b]) * 0.8f;
            boolean aw = rng.nextFloat() < pa;
            winners[r][i] = aw ? a : b;
            scoreW[r][i] = 3;
            scoreL[r][i] = rng.nextInt(3);
        }
    }

    public int champion() {
        return finished ? winners[ROUNDS - 1][0] : -1;
    }

    public boolean playerChampion() {
        return finished && champion() == playerSlot;
    }

    public String mapFor(int r) {
        String m = maps[Math.min(r, ROUNDS - 1)];
        for (String id : GameMap.PLAY_IDS) if (id.equals(m)) return m;
        return "fortaleza";
    }
}
