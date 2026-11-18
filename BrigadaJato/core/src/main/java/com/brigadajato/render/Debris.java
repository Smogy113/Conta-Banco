package com.brigadajato.render;

import com.badlogic.gdx.math.MathUtils;
import com.brigadajato.world.C;
import com.brigadajato.world.GameMap;

/**
 * Coisas que ficam no cenario por um tempo: capsulas de bala que caem e quicam no chao, furos de
 * bala e marcas de queimado de explosao nas paredes. Tudo em vetores fixos (sem lixo de memoria).
 */
public class Debris {
    private static final int MAXC = 160, MAXD = 180;
    // capsulas
    private final float[] cx = new float[MAXC], cy = new float[MAXC], cvx = new float[MAXC], cvy = new float[MAXC], crot = new float[MAXC],
            cvr = new float[MAXC], clife = new float[MAXC];
    private final int[] ccol = new int[MAXC];
    private final boolean[] crest = new boolean[MAXC];
    private int cnext;
    // marcas
    private final float[] dx = new float[MAXD], dy = new float[MAXD], dsize = new float[MAXD], dlife = new float[MAXD], dmax = new float[MAXD], drot = new float[MAXD];
    private final float[] dix = new float[MAXD], diy = new float[MAXD];
    private final int[] dtype = new int[MAXD];
    private int dnext;
    public static final int HOLE = 0, SCORCH = 1;

    // ---- pedacos de parede/caixa (pedacos da propria textura do bloco, com fisica)
    private static final int MAXK = 140;
    private final float[] kx = new float[MAXK], ky = new float[MAXK], kvx = new float[MAXK], kvy = new float[MAXK], krot = new float[MAXK],
            kvr = new float[MAXK], klife = new float[MAXK], ksize = new float[MAXK];
    private final com.badlogic.gdx.graphics.g2d.TextureRegion[] kreg = new com.badlogic.gdx.graphics.g2d.TextureRegion[MAXK];
    private final float[] ku = new float[MAXK], kv = new float[MAXK];
    private final boolean[] krest = new boolean[MAXK];
    private final float[] ktint = new float[MAXK], kfrac = new float[MAXK];
    private int knext;
    private final com.badlogic.gdx.graphics.g2d.TextureRegion tmpReg = new com.badlogic.gdx.graphics.g2d.TextureRegion();

    /**
     * Quebra um bloco em pedacos: o bloco (tx, ty) e dividido em n x n pedacos da sua textura, que
     * voam a partir de (fromX, fromY) (de onde veio o golpe) e caem quicando no cenario.
     */
    public void shatter(com.badlogic.gdx.graphics.g2d.TextureRegion tile, int tx, int ty, int n, float fromX, float fromY, float power) {
        float T = C.TILE, cell = T / n;
        float cxT = tx * T + T / 2f, cyT = ty * T + T / 2f;
        for (int gy = 0; gy < n; gy++)
            for (int gx = 0; gx < n; gx++) {
                int i = knext;
                knext = (knext + 1) % MAXK;
                float px = tx * T + (gx + 0.5f) * cell, py = ty * T + (gy + 0.5f) * cell;
                float dx = px - fromX, dy = py - fromY;
                float len = Math.max(1, (float) Math.sqrt(dx * dx + dy * dy));
                float sp = power * MathUtils.random(0.5f, 1.1f);
                kx[i] = px;
                ky[i] = py;
                kvx[i] = dx / len * sp + (px - cxT) * 6 + MathUtils.random(-60, 60);
                kvy[i] = dy / len * sp + (py - cyT) * 4 + MathUtils.random(80, 260);
                krot[i] = 0;
                kvr[i] = MathUtils.random(-720, 720);
                klife[i] = MathUtils.random(5f, 7f);
                ksize[i] = cell * MathUtils.random(0.8f, 1.05f);
                kreg[i] = tile;
                ku[i] = gx / (float) n;
                kv[i] = 1 - (gy + 1) / (float) n;
                krest[i] = false;
                ktint[i] = MathUtils.random(0.82f, 1f);
                kfrac[i] = 1f / n;
            }
    }

    public void drawChunks(Draw d, int n, float minX, float minY, float maxX, float maxY) {
        for (int i = 0; i < MAXK; i++) {
            if (klife[i] <= 0 || kreg[i] == null) continue;
            if (kx[i] < minX - 30 || kx[i] > maxX + 30 || ky[i] < minY - 30 || ky[i] > maxY + 30) continue;
            float a = Math.min(1, klife[i] * 1.5f), s = ksize[i];
            com.badlogic.gdx.graphics.g2d.TextureRegion r = kreg[i];
            float u0 = r.getU(), v0 = r.getV(), du = r.getU2() - u0, dv = r.getV2() - v0;
            float k = kfrac[i];
            tmpReg.setTexture(r.getTexture());
            tmpReg.setRegion(u0 + du * ku[i], v0 + dv * kv[i], u0 + du * (ku[i] + k), v0 + dv * (kv[i] + k));
            d.color(0.08f, 0.07f, 0.06f, a * 0.9f);
            d.rect(kx[i] - s / 2 - 1, ky[i] - s / 2 - 1, s + 2, s + 2, s / 2 + 1, s / 2 + 1, krot[i]);
            float t = ktint[i];
            d.color(t, t, t, a);
            d.batch.draw(tmpReg, kx[i] - s / 2, ky[i] - s / 2, s / 2, s / 2, s, s, 1, 1, krot[i]);
        }
    }

    public void clear() {
        for (int i = 0; i < MAXK; i++) klife[i] = 0;
        for (int i = 0; i < MAXC; i++) clife[i] = 0;
        for (int i = 0; i < MAXD; i++) dlife[i] = 0;
    }

    public void casing(float x, float y, float vx, float vy, int color) {
        int i = cnext;
        cnext = (cnext + 1) % MAXC;
        cx[i] = x;
        cy[i] = y;
        cvx[i] = vx;
        cvy[i] = vy;
        crot[i] = MathUtils.random(360);
        cvr[i] = MathUtils.random(-900, 900);
        clife[i] = 7f;
        ccol[i] = color;
        crest[i] = false;
    }

    /** furo de bala; (ix, iy) e um ponto dentro da parede (some se a parede quebrar) */
    public void hole(float x, float y, float ix, float iy) {
        mark(HOLE, x, y, ix, iy, MathUtils.random(1.8f, 2.6f), 14f);
    }

    public void scorch(float x, float y, float size) {
        mark(SCORCH, x, y, x, y, size, 18f);
    }

    private void mark(int type, float x, float y, float ix, float iy, float size, float life) {
        int i = dnext;
        dnext = (dnext + 1) % MAXD;
        dtype[i] = type;
        dx[i] = x;
        dy[i] = y;
        dix[i] = ix;
        diy[i] = iy;
        dsize[i] = size;
        dlife[i] = dmax[i] = life;
        drot[i] = MathUtils.random(360);
    }

    public void update(float dt, GameMap m) {
        for (int i = 0; i < MAXC; i++) {
            if (clife[i] <= 0) continue;
            clife[i] -= dt;
            if (crest[i]) continue;
            cvy[i] -= 1100 * dt;
            float nx = cx[i] + cvx[i] * dt, ny = cy[i] + cvy[i] * dt;
            if (m.solidAt(nx, cy[i])) {
                cvx[i] = -cvx[i] * 0.4f;
                nx = cx[i];
            }
            int ty = (int) Math.floor(ny / C.TILE);
            boolean platform = cvy[i] < 0 && m.get((int) Math.floor(nx / C.TILE), ty) == GameMap.PLATFORM
                    && cy[i] >= (ty + 1) * C.TILE - 1 && ny < (ty + 1) * C.TILE;
            if (m.solidAt(nx, ny) || platform) {
                if (cvy[i] < 0) {
                    ny = (float) (Math.floor(ny / C.TILE) + 1) * C.TILE + 1;
                    if (platform) ny = (ty + 1) * C.TILE + 1;
                    cvy[i] = -cvy[i] * 0.35f;
                    cvx[i] *= 0.55f;
                    cvr[i] *= 0.5f;
                    if (Math.abs(cvy[i]) < 60) {
                        crest[i] = true;
                        crot[i] = MathUtils.randomBoolean() ? 0 : 180;
                    }
                } else {
                    cvy[i] = 0;
                    ny = cy[i];
                }
            }
            cx[i] = nx;
            cy[i] = ny;
            crot[i] += cvr[i] * dt;
        }
        for (int i = 0; i < MAXD; i++) if (dlife[i] > 0) dlife[i] -= dt;
        // pedacos: caem, quicam, rolam ate parar
        for (int i = 0; i < MAXK; i++) {
            if (klife[i] <= 0) continue;
            klife[i] -= dt;
            if (krest[i]) {
                if (!m.solidAt(kx[i], ky[i] - ksize[i] / 2 - 1)) krest[i] = false; // o chao sumiu
                continue;
            }
            kvy[i] -= 1250 * dt;
            float h = ksize[i] / 2;
            float nx = kx[i] + kvx[i] * dt, ny = ky[i] + kvy[i] * dt;
            if (m.solidAt(nx + Math.signum(kvx[i]) * h, ky[i])) {
                kvx[i] = -kvx[i] * 0.35f;
                nx = kx[i];
            }
            if (kvy[i] < 0 && m.solidAt(nx, ny - h)) {
                ny = (float) (Math.floor((ny - h) / C.TILE) + 1) * C.TILE + h;
                kvy[i] = -kvy[i] * 0.3f;
                kvx[i] *= 0.6f;
                kvr[i] *= 0.45f;
                if (Math.abs(kvy[i]) < 70) {
                    kvy[i] = 0;
                    krest[i] = true;
                    krot[i] = Math.round(krot[i] / 90f) * 90f; // deita de lado
                }
            } else if (kvy[i] > 0 && m.solidAt(nx, ny + h)) {
                kvy[i] = 0;
                ny = ky[i];
            }
            kx[i] = nx;
            ky[i] = ny;
            krot[i] += kvr[i] * dt;
        }
    }

    /** marcas (logo depois dos blocos) */
    public void drawMarks(Draw d, GameMap m, float minX, float minY, float maxX, float maxY) {
        for (int i = 0; i < MAXD; i++) {
            if (dlife[i] <= 0) continue;
            float x = dx[i], y = dy[i];
            if (x < minX - 80 || x > maxX + 80 || y < minY - 80 || y > maxY + 80) continue;
            float a = Math.min(1, dlife[i] / 3f);
            if (dtype[i] == HOLE) {
                if (!m.solidAt(dix[i], diy[i])) {
                    dlife[i] = 0;
                    continue;
                }
                d.color(0.05f, 0.04f, 0.03f, 0.75f * a);
                d.circle(x, y, dsize[i]);
                d.color(1, 1, 1, 0.18f * a);
                d.ring(x, y, dsize[i] + 0.8f);
            } else {
                d.color(0.06f, 0.05f, 0.04f, 0.42f * a);
                d.soft(x, y, dsize[i]);
                d.color(0.05f, 0.04f, 0.03f, 0.35f * a);
                d.region(d.a.star, x, y, dsize[i] * 0.9f, dsize[i] * 0.9f, drot[i]);
            }
        }
    }

    public void drawCasings(Draw d, float minX, float minY, float maxX, float maxY) {
        for (int i = 0; i < MAXC; i++) {
            if (clife[i] <= 0) continue;
            if (cx[i] < minX - 20 || cx[i] > maxX + 20 || cy[i] < minY - 20 || cy[i] > maxY + 20) continue;
            float a = Math.min(1, clife[i]);
            int c = ccol[i];
            d.color(0.08f, 0.07f, 0.05f, a);
            d.rect(cx[i] - 2.6f, cy[i] - 1.4f, 5.2f, 2.8f, 2.6f, 1.4f, crot[i]);
            d.color(((c >> 16) & 0xff) / 255f, ((c >> 8) & 0xff) / 255f, (c & 0xff) / 255f, a);
            d.rect(cx[i] - 2f, cy[i] - 0.9f, 4f, 1.8f, 2f, 0.9f, crot[i]);
        }
    }
}
