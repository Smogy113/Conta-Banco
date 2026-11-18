package com.brigadajato.world.ai;

import com.badlogic.gdx.utils.IntArray;
import com.brigadajato.world.C;
import com.brigadajato.world.GameMap;

/**
 * Dijkstra na grade de blocos. Todo mundo tem jetpack, entao qualquer celula vazia (com espaco
 * para o corpo de 2 blocos) e alcancavel, mas celulas "no chao" custam menos que celulas no ar
 * para os bots andarem quando possivel e economizarem combustivel.
 */
public class Pathfinder {
    private final GameMap map;
    private final boolean[] passable;
    private final float[] cost;
    private final float[] dist;
    private final int[] cameFrom;
    private final int[] visitMark;
    private final int[] closedMark;
    private int[] heap;
    private float[] heapKey;
    private int heapSize;
    private int mark = 1;
    /** buscas recentes sem caminho (inicio, fim): repetir daria o mesmo resultado enquanto o mapa nao mudar */
    private final long[] failed = new long[32];
    private int failCount, failNext;

    public Pathfinder(GameMap map) {
        this.map = map;
        int n = map.w * map.h;
        passable = new boolean[n];
        cost = new float[n];
        dist = new float[n];
        cameFrom = new int[n];
        visitMark = new int[n];
        closedMark = new int[n];
        heap = new int[n * 4];
        heapKey = new float[n * 4];
        for (int y = 0; y < map.h; y++)
            for (int x = 0; x < map.w; x++) computeCell(x, y);
    }

    private boolean hard(int x, int y) {
        int t = map.get(x, y);
        return map.solid(x, y) && t != GameMap.BREAKABLE && t != GameMap.DOOR;
    }

    private boolean soft(int x, int y) {
        int t = map.get(x, y);
        return t == GameMap.BREAKABLE || t == GameMap.DOOR;
    }

    private void computeCell(int x, int y) {
        int i = x + y * map.w;
        failCount = 0; // o mapa mudou: as buscas que falharam podem dar certo agora
        // caixas e portas fechadas contam como "passaveis caras": o bot quebra a caixa ou abre a porta
        passable[i] = !hard(x, y) && !hard(x, y + 1);
        int below = map.get(x, y - 1);
        boolean ground = below == GameMap.SOLID || below == GameMap.PLATFORM || below == GameMap.BREAKABLE || below == GameMap.DOOR;
        int below2 = map.get(x, y - 2);
        boolean nearGround = ground || below2 == GameMap.SOLID || below2 == GameMap.PLATFORM;
        boolean danger = below == GameMap.SPIKES || below2 == GameMap.SPIKES;
        // paredes dos lados tambem atrapalham (corpo raspa); penaliza levemente
        boolean tight = map.solid(x - 1, y) && map.solid(x + 1, y);
        boolean blocked = soft(x, y) || soft(x, y + 1);
        cost[i] = (ground ? 1f : (nearGround ? 1.6f : 2.4f)) + (danger ? 6f : 0f) + (tight ? 0.6f : 0f) + (blocked ? 9f : 0f);
    }

    /** Um bloco mudou (quebrou, porta abriu): recalcula as celulas vizinhas. */
    public void refresh(int tx, int ty) {
        for (int y = ty - 2; y <= ty + 2; y++)
            for (int x = tx - 1; x <= tx + 1; x++)
                if (x >= 0 && y >= 0 && x < map.w && y < map.h) computeCell(x, y);
    }

    public boolean passable(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= map.w || ty >= map.h) return false;
        return passable[tx + ty * map.w];
    }

    private int nearestPassable(int tx, int ty) {
        for (int r = 0; r < 6; r++)
            for (int dy = -r; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++)
                    if (passable(tx + dx, ty + dy)) return (tx + dx) + (ty + dy) * map.w;
        return -1;
    }

    private void push(int node, float key) {
        if (heapSize >= heap.length) {
            int[] nh = new int[heap.length * 2];
            float[] nk = new float[heap.length * 2];
            System.arraycopy(heap, 0, nh, 0, heapSize);
            System.arraycopy(heapKey, 0, nk, 0, heapSize);
            heap = nh;
            heapKey = nk;
        }
        int i = heapSize++;
        heap[i] = node;
        heapKey[i] = key;
        while (i > 0) {
            int parent = (i - 1) >> 1;
            if (heapKey[parent] <= heapKey[i]) break;
            swap(i, parent);
            i = parent;
        }
    }

    private int pop() {
        int top = heap[0];
        heapSize--;
        if (heapSize > 0) {
            heap[0] = heap[heapSize];
            heapKey[0] = heapKey[heapSize];
            int i = 0;
            while (true) {
                int l = i * 2 + 1, r = l + 1, m = i;
                if (l < heapSize && heapKey[l] < heapKey[m]) m = l;
                if (r < heapSize && heapKey[r] < heapKey[m]) m = r;
                if (m == i) break;
                swap(i, m);
                i = m;
            }
        }
        return top;
    }

    private void swap(int a, int b) {
        int t = heap[a];
        heap[a] = heap[b];
        heap[b] = t;
        float k = heapKey[a];
        heapKey[a] = heapKey[b];
        heapKey[b] = k;
    }

    /** Preenche "out" com indices de celulas do inicio ao fim. Retorna false se nao achou. */
    public boolean find(float sx, float sy, float gx, float gy, IntArray out) {
        out.clear();
        int s = nearestPassable((int) (sx / C.TILE), (int) ((sy + 2) / C.TILE));
        int g = nearestPassable((int) (gx / C.TILE), (int) ((gy + 2) / C.TILE));
        if (s < 0 || g < 0) return false;
        long key = ((long) s << 32) | (g & 0xffffffffL);
        for (int i = 0; i < failCount; i++) if (failed[i] == key) return false;
        mark++;
        heapSize = 0;
        int w = map.w;
        int gxC = g % w, gyC = g / w;
        visitMark[s] = mark;
        dist[s] = 0;
        cameFrom[s] = -1;
        push(s, 0);
        boolean found = false;
        int guard = 0;
        while (heapSize > 0 && guard++ < 40000) {
            int c = pop();
            if (closedMark[c] == mark) continue;
            closedMark[c] = mark;
            if (c == g) {
                found = true;
                break;
            }
            int cx = c % w, cy = c / w;
            for (int k = 0; k < 4; k++) {
                int nx = cx + (k == 0 ? 1 : (k == 1 ? -1 : 0));
                int ny = cy + (k == 2 ? 1 : (k == 3 ? -1 : 0));
                if (nx < 0 || ny < 0 || nx >= w || ny >= map.h) continue;
                int n = nx + ny * w;
                if (!passable[n] || closedMark[n] == mark) continue;
                float nd = dist[c] + cost[n] + (k == 2 ? 0.4f : 0f);
                if (visitMark[n] != mark || nd < dist[n]) {
                    visitMark[n] = mark;
                    dist[n] = nd;
                    cameFrom[n] = c;
                    float h = Math.abs(nx - gxC) + Math.abs(ny - gyC);
                    push(n, nd + h);
                }
            }
        }
        if (!found) {
            failed[failNext] = key;
            failNext = (failNext + 1) % failed.length;
            failCount = Math.min(failCount + 1, failed.length);
            return false;
        }
        for (int c = g; c != -1; c = cameFrom[c]) out.add(c);
        out.reverse();
        return true;
    }

    public float cellX(int idx) {
        return (idx % map.w) * C.TILE + C.TILE / 2f;
    }

    public float cellY(int idx) {
        return (idx / map.w) * C.TILE;
    }
}
