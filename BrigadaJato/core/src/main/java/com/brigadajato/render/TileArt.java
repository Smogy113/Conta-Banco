package com.brigadajato.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.brigadajato.world.Theme;

import java.util.Random;

/**
 * Texturas dos blocos de um tema, desenhadas pixel a pixel conforme o estilo (tijolo, metal,
 * terra, gelo, concreto, pedra, madeira, azulejo). Inclui plataforma, espinhos (ou lava), caixa
 * quebravel e porta.
 */
public class TileArt implements Disposable {
    public final Theme theme;
    private final Texture tex;
    public final TextureRegion[] solid = new TextureRegion[4];
    public final TextureRegion top, plat, spike, crate, door, doorOpen;
    /** espinhos viram lava nos temas de vulcao */
    public final boolean lava;

    static int rgba(int rgb, float alpha) {
        return Color.rgba8888(((rgb >> 16) & 0xff) / 255f, ((rgb >> 8) & 0xff) / 255f, (rgb & 0xff) / 255f, alpha);
    }

    static int mix(int c1, int c2, float t) {
        t = MathUtils.clamp(t, 0, 1);
        int r = (int) (((c1 >> 16) & 0xff) * (1 - t) + ((c2 >> 16) & 0xff) * t);
        int g = (int) (((c1 >> 8) & 0xff) * (1 - t) + ((c2 >> 8) & 0xff) * t);
        int b = (int) ((c1 & 0xff) * (1 - t) + (c2 & 0xff) * t);
        return (r << 16) | (g << 8) | b;
    }

    public TileArt(Theme t) {
        this.theme = t;
        this.lava = t.ambient == Theme.Amb.EMBERS;
        Pixmap pm = new Pixmap(256, 256, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        Random r = new Random(t.ordinal() * 31L + 7);
        for (int v = 0; v < 4; v++) solidTile(pm, t, r, v * 64, v);
        topLayer(pm, t, r);
        platform(pm, t);
        if (lava) lavaTile(pm);
        else spikes(pm);
        crate(pm, r);
        door(pm, t);
        tex = new Texture(pm, true);
        tex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        pm.dispose();
        for (int v = 0; v < 4; v++) solid[v] = new TextureRegion(tex, v * 64, 0, 64, 64);
        top = new TextureRegion(tex, 0, 64, 64, 22);
        plat = new TextureRegion(tex, 64, 64, 64, 22);
        spike = new TextureRegion(tex, 128, 64, 64, 64);
        crate = new TextureRegion(tex, 192, 64, 64, 64);
        door = new TextureRegion(tex, 0, 128, 64, 64);
        doorOpen = new TextureRegion(tex, 64, 128, 64, 64);
    }

    private void solidTile(Pixmap pm, Theme t, Random r, int ox, int v) {
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                float shade = 1f - y / 64f * 0.25f;
                int base = t.tile;
                boolean line = false;
                switch (t.tiles) {
                    case BRICK: {
                        int row = y / 16;
                        int bx = (x + (row % 2) * 16 + v * 8) % 32;
                        line = y % 16 == 0 || bx == 0;
                        if (((x / 32 + row + v) * 7) % 5 == 0) base = mix(base, t.tileDark, 0.18f);
                        break;
                    }
                    case METAL: {
                        line = x == 0 || y == 0 || x == 63 || y == 63;
                        int dx = x % 32 - 6, dy = y % 32 - 6;
                        if (dx * dx + dy * dy < 5) base = mix(base, 0xffffff, 0.35f);
                        if ((x + y + v * 5) % 23 == 0) base = mix(base, 0x000000, 0.15f);
                        if (y > 2 && y < 6) base = mix(base, 0xffffff, 0.12f);
                        break;
                    }
                    case ICE: {
                        line = (x + y) % 61 == 0;
                        if ((x - y + 64) % 37 < 3) base = mix(base, 0xffffff, 0.35f);
                        break;
                    }
                    case CONCRETE: {
                        line = x % 32 == 0 || y % 32 == 0;
                        if (r.nextFloat() < 0.03f) base = mix(base, 0x000000, 0.15f);
                        break;
                    }
                    case WOOD: {
                        int plank = (y + v * 5) / 16;
                        line = (y + v * 5) % 16 == 0 || ((x + plank * 23) % 64 == 0);
                        float grain = MathUtils.sin((x + plank * 17) * 0.25f + MathUtils.sin(y * 0.6f + plank) * 1.5f);
                        base = mix(base, t.tileDark, 0.12f + 0.1f * grain);
                        if (((x + plank * 23) % 64 == 3 || (x + plank * 23) % 64 == 60) && (y + v * 5) % 16 == 8) base = 0x3e2a1a;
                        break;
                    }
                    case TILE_FLOOR: {
                        line = x % 16 == 0 || y % 16 == 0;
                        if (((x / 16) + (y / 16) + v) % 2 == 0) base = mix(base, 0xffffff, 0.08f);
                        break;
                    }
                    case ROCK: {
                        if (r.nextFloat() < 0.02f) base = mix(base, t.tileDark, 0.5f);
                        float n = MathUtils.sin(x * 0.21f + v) * MathUtils.cos(y * 0.17f - v * 2) + MathUtils.sin((x + y) * 0.11f);
                        if (Math.abs(n) < 0.05f) line = true;
                        break;
                    }
                    default: { // terra com pedrinhas
                        if (r.nextFloat() < 0.012f) base = mix(base, 0x5d4037, 0.6f);
                        break;
                    }
                }
                float noise = 0.92f + r.nextFloat() * 0.12f;
                int c = mix(0x000000, base, shade * noise);
                if (line) c = mix(c, t.tileDark, 0.85f);
                pm.drawPixel(ox + x, y, rgba(c, 1));
            }
        }
        if (t.tiles == Theme.Tiles.DIRT || t.tiles == Theme.Tiles.ROCK || t.tiles == Theme.Tiles.BRICK && t.ambient == Theme.Amb.DUST) {
            for (int k = 0; k < 3; k++) {
                int cx = ox + 8 + r.nextInt(48), cy = 10 + r.nextInt(44), rr = 3 + r.nextInt(4);
                pm.setColor(new Color(rgba(mix(t.tileDark, t.tile, 0.4f), 1)));
                pm.fillCircle(cx, cy, rr);
            }
        }
    }

    private void topLayer(Pixmap pm, Theme t, Random r) {
        boolean wavy = t.tiles == Theme.Tiles.DIRT || t.tiles == Theme.Tiles.ICE || t.tiles == Theme.Tiles.ROCK
                || (t.tiles == Theme.Tiles.BRICK && t.ambient == Theme.Amb.DUST);
        for (int y = 0; y < 22; y++)
            for (int x = 0; x < 64; x++) {
                float edge = wavy ? 15 + 4 * MathUtils.sin(x * 0.45f) + 2 * MathUtils.sin(x * 1.3f + 1) : 8;
                if (y > edge) continue;
                int c = mix(t.tileTop, 0xffffff, y < (wavy ? 4 : 2) ? 0.25f : 0);
                if (!wavy && y > 6) c = mix(c, 0x000000, 0.3f);
                c = mix(c, 0x000000, y / 40f + (r.nextFloat() * 0.08f));
                pm.drawPixel(x, 64 + y, rgba(c, 1));
            }
    }

    private void platform(Pixmap pm, Theme t) {
        Theme.Tiles st = t.tiles;
        boolean metal = st == Theme.Tiles.METAL || st == Theme.Tiles.CONCRETE || st == Theme.Tiles.TILE_FLOOR;
        boolean rock = st == Theme.Tiles.ROCK || st == Theme.Tiles.ICE;
        Random r = new Random(t.ordinal() * 13L + 5);
        int base = t.platform;
        for (int y = 0; y < 22; y++)
            for (int x = 0; x < 64; x++) {
                float vy = y / 21f; // 0 = topo
                int c;
                if (metal) {
                    c = mix(base, 0x90a4ae, 0.35f);
                    if (y < 3) c = mix(c, 0xffffff, 0.35f);
                    else if (y > 16) c = mix(c, 0x000000, 0.45f);
                    else if ((x + y) % 8 < 2 && y > 5 && y < 15) c = mix(c, 0x000000, 0.4f); // grade
                    int rx = x % 16 - 8, ry = y - 4;
                    if (rx * rx + ry * ry < 3) c = mix(c, 0xffffff, 0.5f); // rebite
                } else if (rock) {
                    c = mix(base, t.tile, 0.4f);
                    c = mix(c, 0x000000, vy * 0.35f + r.nextFloat() * 0.06f);
                    if (y < 3) c = mix(c, 0xffffff, 0.3f);
                    if ((x * 7 + y * 3) % 29 == 0) c = mix(c, 0x000000, 0.4f);
                } else { // tabuas de madeira
                    int plank = y < 11 ? 0 : 1;
                    float grain = (float) Math.sin((x + plank * 23) * 0.35f + Math.sin(y * 0.8f + plank * 3) * 1.4f);
                    c = mix(base, 0x000000, 0.08f + 0.06f * grain + plank * 0.1f);
                    if (y == 0 || y == 1) c = mix(c, 0xffffff, 0.28f);
                    if (y == 10 || y == 11 || y > 19) c = mix(c, 0x000000, 0.45f);
                    if (x % 32 == 0) c = mix(c, 0x000000, 0.4f);
                    if ((x % 32 == 3 || x % 32 == 28) && (y == 5 || y == 15)) c = 0x3a3a3a; // pregos
                }
                pm.drawPixel(64 + x, 64 + y, rgba(c, 1));
            }
    }

    private void spikes(Pixmap pm) {
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                int sx = x % 16;
                float hgt = 40 - Math.abs(sx - 7.5f) * 5;
                int yy = 63 - y;
                if (yy < hgt) {
                    int c = sx < 8 ? 0xb0bec5 : 0x78909c;
                    if (yy < 10) c = 0x546e7a;
                    pm.drawPixel(128 + x, 64 + y, rgba(c, 1));
                } else if (yy < 6) pm.drawPixel(128 + x, 64 + y, rgba(0x37474f, 1));
            }
    }

    private void lavaTile(Pixmap pm) {
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                int yy = 63 - y;
                float surf = 46 + 3 * MathUtils.sin(x * 0.3f) + 2 * MathUtils.sin(x * 0.9f + 2);
                if (yy > surf) continue;
                float k = yy / surf;
                int c = mix(0xffeb3b, 0xd84315, 1 - k);
                if (yy > surf - 4) c = 0xfff59d;
                float blob = MathUtils.sin(x * 0.4f + yy * 0.3f) * MathUtils.cos(x * 0.2f - yy * 0.5f);
                if (blob > 0.6f) c = mix(c, 0x5d1a0a, 0.5f);
                pm.drawPixel(128 + x, 64 + y, rgba(c, 1));
            }
    }

    private void crate(Pixmap pm, Random r) {
        int base = 0xb07a3c, dark = 0x6d4422, edge = 0x4e2f16;
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                int c = base;
                boolean border = x < 6 || x > 57 || y < 6 || y > 57;
                boolean brace = Math.abs(x - y) < 4 || Math.abs(x - (63 - y)) < 4;
                if (border) c = mix(base, dark, 0.45f);
                else if (brace) c = mix(base, dark, 0.3f);
                else if (y % 13 == 0) c = mix(base, dark, 0.25f);
                if (x == 0 || y == 0 || x == 63 || y == 63) c = edge;
                c = mix(c, 0x000000, r.nextFloat() * 0.07f);
                if (border && (x == 3 || x == 60) && (y == 3 || y == 60)) c = 0x9e9e9e;
                pm.drawPixel(192 + x, 64 + y, rgba(c, 1));
            }
    }

    private void door(Pixmap pm, Theme t) {
        int panel = t.tiles == Theme.Tiles.WOOD ? 0x7a4e2a : mix(t.tileDark, 0x607d8b, 0.55f);
        int frame = mix(panel, 0x000000, 0.45f);
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                int c = panel;
                if (x < 7 || x > 56) c = frame;
                else if (x % 14 == 7) c = mix(panel, 0x000000, 0.25f);
                else if (x % 14 == 8) c = mix(panel, 0xffffff, 0.15f);
                if (y < 3 || y > 60) c = frame;
                if ((y == 30 || y == 33) && x > 8 && x < 56) c = mix(panel, 0x000000, 0.35f);
                pm.drawPixel(x, 128 + y, rgba(c, 1));
                // aberta: so os trilhos laterais
                if (x < 7 || x > 56) {
                    int o = mix(frame, 0xffffff, (y % 16 < 2) ? 0.2f : 0);
                    pm.drawPixel(64 + x, 128 + y, rgba(o, 1));
                } else if (y < 6) {
                    // a porta recolhida em cima
                    pm.drawPixel(64 + x, 128 + y, rgba(mix(panel, 0x000000, 0.2f), 1));
                }
            }
        // listras de perigo no rodape da porta fechada
        for (int y = 52; y < 61; y++)
            for (int x = 8; x < 56; x++) {
                boolean yellow = ((x + y) / 5) % 2 == 0;
                pm.drawPixel(x, 128 + y, rgba(yellow ? 0xffc107 : 0x212121, 1));
            }
    }

    @Override
    public void dispose() {
        tex.dispose();
    }
}
