package com.brigadajato.world;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/** Mapa em grade de blocos. Linha 0 do arquivo = topo; no mundo y cresce para cima. */
public class GameMap {
    public static final byte EMPTY = 0, SOLID = 1, PLATFORM = 2, SPIKES = 3, BREAKABLE = 4, DOOR = 5, DOOR_OPEN = 6;
    public static final int BREAKABLE_HP = 45;

    public final String id;
    public String name = "?", nameEn = "?";
    public Theme theme = Theme.DESERT;
    /** multiplicador da gravidade (Base Lunar = 0.55) */
    public float gravity = 1f;
    /** tema da regiao de baixo (null = mapa de um tema so) e primeira linha (ty) que ja e "de cima" */
    public Theme theme2;
    public int lowerTop = -1;
    /** mapa sempre de noite */
    public boolean night;
    public short[] tileHp;
    public final Array<Vector2> levers = new Array<>();
    public final Array<Vector2> barrels = new Array<>();
    public final Array<Vector2> lamps = new Array<>();
    public final Array<Vector2> hills = new Array<>();
    /** indices das portas de cada alavanca */
    public final Array<com.badlogic.gdx.utils.IntArray> leverDoors = new Array<>();
    public int w, h;
    public byte[] tiles;

    public final Array<Vector2> spawnsFree = new Array<>();
    public final Array<Vector2> spawnsRed = new Array<>();
    public final Array<Vector2> spawnsBlue = new Array<>();
    public final Array<Vector2> weaponSpawns = new Array<>();
    public final Array<Vector2> healthSpawns = new Array<>();
    public final Array<Vector2> grenadeSpawns = new Array<>();
    public final Array<Vector2> powerSpawns = new Array<>();
    public final Array<Vector2> dummySpawns = new Array<>();
    public final Vector2 flagRed = new Vector2(), flagBlue = new Vector2();
    public final Vector2 siteA = new Vector2(), siteB = new Vector2();
    public boolean hasFlags, hasSites;

    public GameMap(String id) {
        this.id = id;
    }

    public float worldW() {
        return w * C.TILE;
    }

    public float worldH() {
        return h * C.TILE;
    }

    public int get(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= w || ty >= h) return SOLID;
        return tiles[tx + ty * w];
    }

    public boolean solid(int tx, int ty) {
        int t = get(tx, ty);
        return t == SOLID || t == SPIKES || t == BREAKABLE || t == DOOR;
    }

    /** Tema de uma linha do mapa (regiao de baixo usa theme2). */
    public Theme themeAt(int ty) {
        return theme2 != null && ty <= lowerTop ? theme2 : theme;
    }

    public boolean solidAt(float x, float y) {
        return solid((int) Math.floor(x / C.TILE), (int) Math.floor(y / C.TILE));
    }

    public static GameMap parse(String id, String text) {
        GameMap m = new GameMap(id);
        Array<String> rows = new Array<>();
        int splitRow = -1;
        for (String raw : text.split("\n")) {
            String line = raw.replace("\r", "");
            if (line.startsWith("@")) {
                int sp = line.indexOf(' ');
                if (sp < 0) continue;
                String key = line.substring(1, sp), val = line.substring(sp + 1).trim();
                if (key.equals("name")) m.name = val;
                else if (key.equals("name_en")) m.nameEn = val;
                else if (key.equals("theme")) m.theme = Theme.parse(val);
                else if (key.equals("gravity")) m.gravity = Float.parseFloat(val);
                else if (key.equals("theme2")) m.theme2 = Theme.parse(val);
                else if (key.equals("split")) splitRow = Integer.parseInt(val);
                else if (key.equals("night")) m.night = "1".equals(val) || "true".equals(val);
                continue;
            }
            if (line.trim().isEmpty()) continue;
            rows.add(line);
        }
        int width = 0;
        for (String r : rows) width = Math.max(width, r.length());
        m.w = width;
        m.h = rows.size;
        m.tiles = new byte[m.w * m.h];
        m.tileHp = new short[m.w * m.h];
        if (splitRow >= 0 && m.theme2 != null) m.lowerTop = m.h - 1 - splitRow;
        float ax = 0, ay = 0, bx = 0, by = 0, rx = 0, ry = 0, blx = 0, bly = 0;
        int an = 0, bn = 0, rn = 0, bln = 0;
        for (int row = 0; row < rows.size; row++) {
            String r = rows.get(row);
            int ty = m.h - 1 - row;
            for (int tx = 0; tx < m.w; tx++) {
                char ch = tx < r.length() ? r.charAt(tx) : '.';
                byte t = EMPTY;
                float px = tx * C.TILE + C.TILE / 2f, py = ty * C.TILE;
                switch (ch) {
                    case '#': t = SOLID; break;
                    case '=': t = PLATFORM; break;
                    case '^': t = SPIKES; break;
                    case 'D': t = BREAKABLE; m.tileHp[tx + ty * m.w] = BREAKABLE_HP; break;
                    case 'O': t = DOOR; break;
                    case 'L': m.levers.add(new Vector2(px, py)); break;
                    case 'X': m.barrels.add(new Vector2(px, py)); break;
                    case 'M': m.lamps.add(new Vector2(px, py)); break;
                    case 'K': m.hills.add(new Vector2(px, py)); break;
                    case 'S': m.spawnsFree.add(new Vector2(px, py)); break;
                    case 'r': m.spawnsRed.add(new Vector2(px, py)); break;
                    case 'b': m.spawnsBlue.add(new Vector2(px, py)); break;
                    case 'W': m.weaponSpawns.add(new Vector2(px, py)); break;
                    case 'H': m.healthSpawns.add(new Vector2(px, py)); break;
                    case 'G': m.grenadeSpawns.add(new Vector2(px, py)); break;
                    case 'P': m.powerSpawns.add(new Vector2(px, py)); break;
                    case 'T': m.dummySpawns.add(new Vector2(px, py)); break;
                    case 'R': rx += px; ry += py; rn++; break;
                    case 'B': blx += px; bly += py; bln++; break;
                    case 'a': ax += px; ay += py; an++; break;
                    case 'c': bx += px; by += py; bn++; break;
                    default: break;
                }
                m.tiles[tx + ty * m.w] = t;
            }
        }
        if (rn > 0 && bln > 0) {
            m.flagRed.set(rx / rn, ry / rn);
            m.flagBlue.set(blx / bln, bly / bln);
            m.hasFlags = true;
        }
        if (an > 0 && bn > 0) {
            m.siteA.set(ax / an, ay / an);
            m.siteB.set(bx / bn, by / bn);
            m.hasSites = true;
        }
        // todo spawn livre tambem serve como spawn de time (e vice-versa)
        if (m.spawnsFree.size == 0) {
            m.spawnsFree.addAll(m.spawnsRed);
            m.spawnsFree.addAll(m.spawnsBlue);
        }
        if (m.spawnsRed.size == 0) {
            for (int i1_ = 0; i1_ < m.spawnsFree.size; i1_++) {
                Vector2 v = m.spawnsFree.get(i1_); if (v.x < m.worldW() / 2) m.spawnsRed.add(v);
            }
        }
        if (m.spawnsBlue.size == 0) {
            for (int i2_ = 0; i2_ < m.spawnsFree.size; i2_++) {
                Vector2 v = m.spawnsFree.get(i2_); if (v.x >= m.worldW() / 2) m.spawnsBlue.add(v);
            }
        }
        if (m.spawnsFree.size == 0) m.spawnsFree.add(new Vector2(m.worldW() / 2, m.worldH() / 2));
        if (m.spawnsRed.size == 0) m.spawnsRed.addAll(m.spawnsFree);
        if (m.spawnsBlue.size == 0) m.spawnsBlue.addAll(m.spawnsFree);
        // cada porta pertence a alavanca mais proxima
        for (int i = 0; i < m.levers.size; i++) m.leverDoors.add(new com.badlogic.gdx.utils.IntArray());
        if (m.levers.size > 0) {
            for (int i = 0; i < m.tiles.length; i++) {
                if (m.tiles[i] != DOOR) continue;
                float dx0 = (i % m.w) * C.TILE + C.TILE / 2f, dy0 = (i / m.w) * C.TILE;
                int best = 0;
                float bd = Float.MAX_VALUE;
                for (int k = 0; k < m.levers.size; k++) {
                    float ddx = m.levers.get(k).x - dx0, ddy = m.levers.get(k).y - dy0;
                    float dd = ddx * ddx + ddy * ddy;
                    if (dd < bd) {
                        bd = dd;
                        best = k;
                    }
                }
                m.leverDoors.get(best).add(i);
            }
        }
        for (int i = 0; i < m.barrels.size; i++) m.groundSpawn(m.barrels.get(i));
        // ninguem nasce no ar nem em cima de espinhos
        for (int i3_ = 0; i3_ < m.spawnsFree.size; i3_++) {
            Vector2 v = m.spawnsFree.get(i3_); m.groundSpawn(v);
        }
        for (int i4_ = 0; i4_ < m.spawnsRed.size; i4_++) {
            Vector2 v = m.spawnsRed.get(i4_); m.groundSpawn(v);
        }
        for (int i5_ = 0; i5_ < m.spawnsBlue.size; i5_++) {
            Vector2 v = m.spawnsBlue.get(i5_); m.groundSpawn(v);
        }
        for (int i6_ = 0; i6_ < m.dummySpawns.size; i6_++) {
            Vector2 v = m.dummySpawns.get(i6_); m.groundSpawn(v);
        }
        return m;
    }

    /**
     * Desce o ponto de nascimento ate o chao. Se embaixo houver espinhos (ou nada), procura a
     * coluna segura mais proxima.
     */
    public void groundSpawn(Vector2 v) {
        int tx0 = (int) Math.floor(v.x / C.TILE), ty0 = (int) Math.floor(v.y / C.TILE);
        for (int d = 0; d <= 12; d++) {
            for (int sgn = 1; sgn >= -1; sgn -= 2) {
                if (d == 0 && sgn == -1) continue;
                int tx = tx0 + d * sgn;
                if (solid(tx, ty0) || solid(tx, ty0 + 1)) continue;
                int ty = ty0;
                while (ty > 0 && get(tx, ty - 1) == EMPTY) ty--;
                int below = get(tx, ty - 1);
                if ((below == SOLID || below == PLATFORM) && !solid(tx, ty + 1)) {
                    v.set(tx * C.TILE + C.TILE / 2f, ty * C.TILE);
                    return;
                }
            }
        }
    }

    /** O ponto esta apoiado em chao firme (nao espinho)? */
    public boolean safeGround(Vector2 v) {
        int tx = (int) Math.floor(v.x / C.TILE), ty = (int) Math.floor(v.y / C.TILE);
        int below = get(tx, ty - 1);
        return (below == SOLID || below == PLATFORM) && !solid(tx, ty) && !solid(tx, ty + 1)
                && Math.abs(v.y - ty * C.TILE) < 0.5f;
    }

    /** Ids dos mapas disponiveis (arquivos em assets/maps). */
    public static final String[] IDS = {"fortaleza", "fabrica", "ilhas", "caverna", "selva", "lua", "navio", "cidade", "vulcao", "submarina", "escola", "mercado", "treino"};

    /** Mapas jogaveis nos modos normais (sem o campo de treino). */
    public static final String[] PLAY_IDS = {"fortaleza", "fabrica", "ilhas", "caverna", "selva", "lua", "navio", "cidade", "vulcao", "submarina", "escola", "mercado"};
}
