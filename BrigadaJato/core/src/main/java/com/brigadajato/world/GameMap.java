package com.brigadajato.world;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/** Mapa em grade de blocos. Linha 0 do arquivo = topo; no mundo y cresce para cima. */
public class GameMap {
    public static final byte EMPTY = 0, SOLID = 1, PLATFORM = 2, SPIKES = 3;

    public final String id;
    public String name = "?", nameEn = "?";
    public Theme theme = Theme.DESERT;
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
        return t == SOLID || t == SPIKES;
    }

    public boolean solidAt(float x, float y) {
        return solid((int) Math.floor(x / C.TILE), (int) Math.floor(y / C.TILE));
    }

    public static GameMap parse(String id, String text) {
        GameMap m = new GameMap(id);
        Array<String> rows = new Array<>();
        for (String raw : text.split("\n")) {
            String line = raw.replace("\r", "");
            if (line.startsWith("@")) {
                int sp = line.indexOf(' ');
                if (sp < 0) continue;
                String key = line.substring(1, sp), val = line.substring(sp + 1).trim();
                if (key.equals("name")) m.name = val;
                else if (key.equals("name_en")) m.nameEn = val;
                else if (key.equals("theme")) m.theme = Theme.parse(val);
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
            for (Vector2 v : m.spawnsFree) if (v.x < m.worldW() / 2) m.spawnsRed.add(v);
        }
        if (m.spawnsBlue.size == 0) {
            for (Vector2 v : m.spawnsFree) if (v.x >= m.worldW() / 2) m.spawnsBlue.add(v);
        }
        if (m.spawnsFree.size == 0) m.spawnsFree.add(new Vector2(m.worldW() / 2, m.worldH() / 2));
        if (m.spawnsRed.size == 0) m.spawnsRed.addAll(m.spawnsFree);
        if (m.spawnsBlue.size == 0) m.spawnsBlue.addAll(m.spawnsFree);
        return m;
    }

    /** Ids dos mapas disponiveis (arquivos em assets/maps). */
    public static final String[] IDS = {"fortaleza", "fabrica", "ilhas", "caverna", "treino"};

    /** Mapas jogaveis nos modos normais (sem o campo de treino). */
    public static final String[] PLAY_IDS = {"fortaleza", "fabrica", "ilhas", "caverna"};
}
