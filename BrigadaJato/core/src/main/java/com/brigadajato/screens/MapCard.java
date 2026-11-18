package com.brigadajato.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.brigadajato.Assets;
import com.brigadajato.I18n;
import com.brigadajato.render.Draw;
import com.brigadajato.render.Icons;
import com.brigadajato.render.SoldierPainter;
import com.brigadajato.world.GameMap;
import com.brigadajato.world.Theme;

/**
 * Cartao do mapa na selecao: miniatura (com as cores do tema) e uma fileira de icones das
 * interacoes do cenario, como na selecao de fases de jogos de luta.
 */
public class MapCard extends Actor {
    public static final int F_BREAK = 0, F_DOORS = 1, F_BARRELS = 2, F_AREAS = 3, F_WATER = 4, F_LAVA = 5, F_COLLAPSE = 6,
            F_NIGHT = 7, F_GRAVITY = 8, F_SITES = 9, F_FLAGS = 10, F_BIG = 11, F_SPIKES = 12, F_LAMPS = 13;
    private static final String[][] NAMES = {
            {"Destrói", "Alavancas", "Barris", "2 áreas", "Maré", "Lava", "Desaba", "Noite", "Gravidade", "Bomba", "Bandeira", "Gigante", "Espinhos", "Luzes"},
            {"Breakable", "Levers", "Barrels", "2 areas", "Tide", "Lava", "Collapse", "Night", "Gravity", "Bomb", "Flags", "Giant", "Spikes", "Lights"}};
    private static final String[][] DESC = {
            {"Caixas quebram com tiros e explosões", "Alavancas abrem portas e escotilhas", "Barris explodem quando atingidos",
                    "Duas áreas ligadas por portas e caixas", "A maré sobe e desce e alaga a parte de baixo", "A lava sobe e queima quem ficar embaixo",
                    "Uma estrutura desaba no meio da partida", "Mapa escuro: luzes e explosões iluminam", "Gravidade baixa: pulos e voos mais longos",
                    "Bombsites A e B para o modo bomba", "Bases com bandeiras para captura", "Mapa enorme, ideal para o Battle Royale",
                    "Espinhos no chão machucam", "Lâmpadas espalhadas pelo cenário"},
            {"Crates break with bullets and explosions", "Levers open doors and hatches", "Barrels explode when hit",
                    "Two areas linked by doors and crates", "The tide rises and floods the lower part", "Lava rises and burns anyone below",
                    "A structure collapses mid-match", "Dark map: lights and explosions light it up", "Low gravity: longer jumps and flights",
                    "Bomb sites A and B for bomb mode", "Flag bases for capture the flag", "Huge map, great for Battle Royale",
                    "Spikes on the floor hurt", "Lamps around the stage"}};
    private static final int[] COLORS = {0xb07a3c, 0x607d8b, 0xc62828, 0x7e57c2, 0x1e88e5, 0xff6d00, 0x8d6e63,
            0x283593, 0x26a69a, 0xe53935, 0x43a047, 0xffb300, 0x90a4ae, 0xfdd835};

    private final Draw d;
    private final Assets a;
    private Texture thumb;
    private String mapId;
    private GameMap map;
    private final Array<Integer> features = new Array<>();
    private float time;

    public MapCard(Draw d, Assets a) {
        this.d = d;
        this.a = a;
        setSize(700, 150);
    }

    public void setMap(GameMap m) {
        if (m.id.equals(mapId)) return;
        mapId = m.id;
        map = m;
        time = 0;
        if (thumb != null) thumb.dispose();
        thumb = buildThumb(m);
        features.clear();
        boolean breaks = false, spikes = false;
        for (byte t : m.tiles) {
            if (t == GameMap.BREAKABLE) breaks = true;
            if (t == GameMap.SPIKES) spikes = true;
        }
        if (breaks) features.add(F_BREAK);
        if (m.levers.size > 0) features.add(F_DOORS);
        if (m.barrels.size > 0) features.add(F_BARRELS);
        if (m.theme2 != null) features.add(F_AREAS);
        if (m.hasFlood()) features.add(m.floodLava ? F_LAVA : F_WATER);
        if (m.collapseTime > 0) features.add(F_COLLAPSE);
        if (m.night || m.theme.dark) features.add(F_NIGHT);
        if (m.gravity < 0.9f) features.add(F_GRAVITY);
        if (m.hasSites && !m.hasFlags) features.add(F_SITES);
        if (m.hasFlags) features.add(F_FLAGS);
        if (m.w >= 150) features.add(F_BIG);
        if (spikes && features.size < 7) features.add(F_SPIKES);
        if (m.lamps.size > 0 && features.size < 7) features.add(F_LAMPS);
    }

    private static Texture buildThumb(GameMap m) {
        Pixmap pm = new Pixmap(m.w, m.h, Pixmap.Format.RGBA8888);
        Theme top = m.theme;
        for (int y = 0; y < m.h; y++)
            for (int x = 0; x < m.w; x++) {
                int ty = m.h - 1 - y;
                Theme th = m.themeAt(ty);
                int t = m.get(x, ty);
                int c;
                float k = y / (float) m.h;
                switch (t) {
                    case GameMap.SOLID: c = th.tile; break;
                    case GameMap.PLATFORM: c = th.platform; break;
                    case GameMap.SPIKES: c = 0xe53935; break;
                    case GameMap.BREAKABLE: c = 0xb07a3c; break;
                    case GameMap.DOOR: c = 0x607d8b; break;
                    default: c = SoldierPainter.mix(th.skyTop, th.skyBottom, k); break;
                }
                pm.drawPixel(x, y, Color.rgba8888(((c >> 16) & 0xff) / 255f, ((c >> 8) & 0xff) / 255f, (c & 0xff) / 255f, 1));
            }
        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        time += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (map == null) return;
        float x = getX(), y = getY(), h = getHeight();
        // miniatura
        float tw = 230, th = Math.min(h - 10, tw * map.h / (float) map.w);
        tw = th * map.w / (float) map.h;
        if (tw > 240) {
            tw = 240;
            th = tw * map.h / map.w;
        }
        float ty = y + (h - th) / 2;
        d.rgb(SoldierPainter.OUTLINE, parentAlpha);
        d.rect(x - 3, ty - 3, tw + 6, th + 6);
        batch.setColor(1, 1, 1, parentAlpha);
        batch.draw(thumb, x, ty, tw, th);
        // icones
        float ix = x + tw + 22, iy = y + h / 2 + 22;
        int n = features.size;
        float slot = Math.min(66, (getWidth() - tw - 26) / Math.max(1, n));
        int focus = n > 0 ? ((int) (time / 1.6f)) % n : -1;
        for (int i = 0; i < n; i++) {
            int f = features.get(i);
            float appear = MathUtils.clamp((time - i * 0.06f) * 6, 0, 1);
            float cx = ix + i * slot + 24, s = 42 * (0.6f + 0.4f * appear);
            float pulse = i == focus ? 1.18f : 1f;
            if (i == focus) {
                d.color(1, 1, 1, 0.9f * parentAlpha);
                d.region(a.rrect, cx, iy, s * pulse + 10, s * pulse + 10, 0);
            }
            d.rgb(SoldierPainter.OUTLINE, parentAlpha * appear);
            d.region(a.rrect, cx, iy, s * pulse + 4, s * pulse + 4, 0);
            d.rgb(COLORS[f], parentAlpha * appear);
            d.region(a.rrect, cx, iy, s * pulse, s * pulse, 0);
            d.color(1, 1, 1, 0.25f * appear);
            d.region(a.rrect, cx, iy + s * 0.22f, s * 0.8f, s * 0.3f, 0);
            icon(f, cx, iy, s * 0.62f, parentAlpha * appear);
            a.small.getData().setScale(a.small.getScaleX() * 0.62f);
            if (i == focus) a.small.setColor(1, 0.85f, 0.3f, parentAlpha * appear);
            else a.small.setColor(1, 1, 1, parentAlpha * appear * 0.85f);
            a.small.draw(batch, NAMES[I18n.pt ? 0 : 1][f], cx, iy - s / 2 - 6, 0, Align.center, false);
            a.small.getData().setScale(a.small.getScaleX() / 0.62f);
        }
        if (focus >= 0) {
            a.small.setColor(0.85f, 0.85f, 0.85f, parentAlpha);
            a.small.draw(batch, DESC[I18n.pt ? 0 : 1][features.get(focus)], ix, y + 14);
        }
        if (n == 0) {
            a.small.setColor(0.7f, 0.7f, 0.7f, parentAlpha);
            a.small.draw(batch, I18n.pt ? "Mapa clássico" : "Classic map", ix, iy + 6);
        }
        a.small.setColor(1, 1, 1, 1);
        batch.setColor(1, 1, 1, 1);
    }

    private void icon(int f, float x, float y, float s, float alpha) {
        int ink = SoldierPainter.OUTLINE;
        switch (f) {
            case F_BREAK: { // caixa rachada
                d.rgb(ink, alpha);
                d.rect(x - s * 0.42f, y - s * 0.42f, s * 0.84f, s * 0.84f);
                d.rgb(0xe0b37a, alpha);
                d.rect(x - s * 0.34f, y - s * 0.34f, s * 0.68f, s * 0.68f);
                d.rgb(ink, alpha);
                d.rect(x - s * 0.05f, y - s * 0.4f, s * 0.1f, s * 0.8f, s * 0.05f, s * 0.4f, 30);
                d.rect(x - s * 0.3f, y, s * 0.3f, s * 0.08f, 0, 0, -30);
                break;
            }
            case F_DOORS: { // alavanca
                d.rgb(ink, alpha);
                d.rect(x - s * 0.36f, y - s * 0.4f, s * 0.72f, s * 0.3f);
                d.rect(x - s * 0.06f, y - s * 0.2f, s * 0.12f, s * 0.6f, s * 0.06f, 0, -30);
                d.rgb(0x69f0ae, alpha);
                d.circle(x + s * 0.18f, y + s * 0.34f, s * 0.14f);
                break;
            }
            case F_BARRELS: {
                d.rgb(ink, alpha);
                d.rect(x - s * 0.3f, y - s * 0.42f, s * 0.6f, s * 0.84f);
                d.rgb(0xffeb3b, alpha);
                d.rect(x - s * 0.18f, y - s * 0.1f, s * 0.36f, s * 0.26f);
                d.rgb(ink, alpha);
                d.region(a.tri, x, y + s * 0.03f, s * 0.2f, s * 0.2f, 0);
                break;
            }
            case F_AREAS: { // duas camadas com seta
                d.rgb(ink, alpha);
                d.rect(x - s * 0.4f, y + s * 0.1f, s * 0.8f, s * 0.12f);
                d.rect(x - s * 0.4f, y - s * 0.3f, s * 0.8f, s * 0.12f);
                d.rgb(0xffffff, alpha);
                d.region(a.arrow, x, y - s * 0.02f, s * 0.5f, s * 0.34f, 90);
                break;
            }
            case F_WATER:
            case F_LAVA: {
                d.rgb(f == F_LAVA ? 0xffe082 : 0xe1f5fe, alpha);
                for (int k = 0; k < 3; k++) {
                    float wy = y + s * 0.25f - k * s * 0.25f;
                    for (int j = 0; j < 4; j++)
                        d.circle(x - s * 0.36f + j * s * 0.24f, wy + MathUtils.sin(time * 4 + j + k) * s * 0.04f, s * 0.09f);
                }
                d.region(a.arrow, x + s * 0.32f, y + s * 0.28f, s * 0.3f, s * 0.22f, 90);
                break;
            }
            case F_COLLAPSE: { // predio caindo
                d.rgb(ink, alpha);
                d.rect(x - s * 0.18f, y - s * 0.42f, s * 0.36f, s * 0.8f, s * 0.18f, 0, -18);
                d.rgb(0xffffff, alpha);
                for (int k = 0; k < 3; k++) d.rect(x - s * 0.08f + k * s * 0.06f, y - s * 0.2f + k * s * 0.18f, s * 0.07f, s * 0.08f, 0, 0, -18);
                d.rgb(ink, alpha);
                d.circle(x + s * 0.32f, y - s * 0.34f, s * 0.07f);
                d.circle(x + s * 0.4f, y - s * 0.22f, s * 0.05f);
                break;
            }
            case F_NIGHT: {
                d.rgb(0xfff59d, alpha);
                d.circle(x, y, s * 0.36f);
                d.rgb(COLORS[F_NIGHT], alpha);
                d.circle(x + s * 0.16f, y + s * 0.1f, s * 0.32f);
                break;
            }
            case F_GRAVITY: {
                d.rgb(0xffffff, alpha);
                d.circle(x, y + s * 0.1f + MathUtils.sin(time * 3) * s * 0.08f, s * 0.2f);
                d.region(a.arrow, x, y - s * 0.28f, s * 0.36f, s * 0.24f, 90);
                break;
            }
            case F_SITES: {
                a.normal.setColor(1, 1, 1, alpha);
                a.normal.draw(d.batch, "A·B", x, y + s * 0.22f, 0, Align.center, false);
                a.normal.setColor(1, 1, 1, 1);
                break;
            }
            case F_FLAGS: {
                d.rgb(ink, alpha);
                d.rect(x - s * 0.28f, y - s * 0.42f, s * 0.08f, s * 0.84f);
                d.rgb(0xffffff, alpha);
                d.rect(x - s * 0.2f, y + s * 0.02f, s * 0.5f, s * 0.36f);
                break;
            }
            case F_BIG: {
                d.rgb(0xffffff, alpha);
                d.region(a.arrow, x - s * 0.22f, y, s * 0.32f, s * 0.26f, 180);
                d.region(a.arrow, x + s * 0.22f, y, s * 0.32f, s * 0.26f, 0);
                break;
            }
            case F_SPIKES: {
                d.rgb(0xeceff1, alpha);
                for (int k = 0; k < 3; k++) d.region(a.tri, x - s * 0.26f + k * s * 0.26f, y - s * 0.05f, s * 0.24f, s * 0.42f, 0);
                break;
            }
            default: { // luzes
                d.rgb(ink, alpha);
                d.rect(x - s * 0.04f, y - s * 0.42f, s * 0.08f, s * 0.6f);
                d.rgb(0xfffde7, alpha);
                d.circle(x, y + s * 0.22f, s * 0.16f);
                break;
            }
        }
    }

    public void dispose() {
        if (thumb != null) thumb.dispose();
    }
}
