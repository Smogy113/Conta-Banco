package com.brigadajato.render;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.brigadajato.world.C;
import com.brigadajato.world.Look;
import com.brigadajato.world.WeaponType;

/**
 * Desenha o soldadinho cartoon a partir de formas simples. Cada parte e desenhada duas vezes:
 * primeiro um contorno escuro um pouco maior e depois o preenchimento colorido.
 */
public class SoldierPainter {
    public static final int OUTLINE = 0x15151c;
    public static final int RED = 0xe53935, BLUE = 0x1e88e5;

    private static final int MAX = 48;
    private final int[] type = new int[MAX];
    private final float[] px = new float[MAX], py = new float[MAX], pw = new float[MAX], ph = new float[MAX];
    private final float[] pox = new float[MAX], poy = new float[MAX], prot = new float[MAX];
    private final int[] pcol = new int[MAX];
    private final boolean[] pline = new boolean[MAX];
    private int n;

    private static final int T_RECT = 0, T_CIRCLE = 1, T_HALF = 2, T_TRI = 3, T_ELLIPSE = 4, T_RRECT = 5;

    /** Estado de pose para desenhar. */
    public static class Pose {
        public float x, y, aim, anim, meleeAnim, hurtFlash, time;
        public int facing = 1, team;
        public boolean onGround = true, crouch, jetting, carryingFlag, hasBomb, shield;
        public int flagColor;
        public WeaponType weapon;
        public Look look;
        public float alpha = 1f;
        public float scale = 1f;
    }

    private void add(int t, float x, float y, float w, float h, float ox, float oy, float rot, int col, boolean outline) {
        if (n >= MAX) return;
        type[n] = t;
        px[n] = x;
        py[n] = y;
        pw[n] = w;
        ph[n] = h;
        pox[n] = ox;
        poy[n] = oy;
        prot[n] = rot;
        pcol[n] = col;
        pline[n] = outline;
        n++;
    }

    private void rect(float x, float y, float w, float h, float ox, float oy, float rot, int col) {
        add(T_RECT, x, y, w, h, ox, oy, rot, col, true);
    }

    private void rrect(float x, float y, float w, float h, float ox, float oy, float rot, int col) {
        add(T_RRECT, x, y, w, h, ox, oy, rot, col, true);
    }

    private void circle(float cx, float cy, float r, int col) {
        add(T_CIRCLE, cx, cy, r, r, 0, 0, 0, col, true);
    }

    private void detailRect(float x, float y, float w, float h, float ox, float oy, float rot, int col) {
        add(T_RECT, x, y, w, h, ox, oy, rot, col, false);
    }

    private void detailCircle(float cx, float cy, float r, int col) {
        add(T_CIRCLE, cx, cy, r, r, 0, 0, 0, col, false);
    }

    public static int darker(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 0xff) * k), g = (int) (((rgb >> 8) & 0xff) * k), b = (int) ((rgb & 0xff) * k);
        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    public void draw(Draw d, Pose s) {
        n = 0;
        Look look = s.look;
        float x = s.x, y = s.y;
        int f = s.facing;
        int body = s.team == C.TEAM_RED ? RED : (s.team == C.TEAM_BLUE ? BLUE : look.body);
        int bodyDark = darker(body, 0.72f);
        int pants = darker(s.team == 0 ? look.body : body, 0.45f);
        boolean crouch = s.crouch;
        float legLen = crouch ? 8 : 13;
        float hipY = y + legLen;
        float torsoH = crouch ? 15 : 17;
        float torsoTop = hipY + torsoH;
        float headR = 8.5f;
        float headX = x + f * 1.5f, headY = torsoTop + 6.5f;
        float shoulderX = x, shoulderY = y + (crouch ? C.CROUCH_H : C.PLAYER_H) * 0.66f;
        float aimDeg = s.aim * MathUtils.radiansToDegrees;

        // ---- pernas
        float swing = 0;
        if (s.onGround && !crouch) swing = MathUtils.sin(s.anim * 3.4f) * 32f;
        else if (!s.onGround) swing = 18f;
        float legA = crouch ? 70 * f : swing, legB = crouch ? 15 * f : -swing;
        if (!s.onGround) {
            legA = 22 * f;
            legB = 8 * f;
        }
        // perna de tras
        rect(x - 3.5f - 3, hipY - legLen, 7, legLen, 3.5f, legLen, legB, darker(pants, 0.8f));
        // perna da frente
        rect(x + 0.5f - 3, hipY - legLen, 7, legLen, 3.5f, legLen, legA, pants);
        // botas
        float bootAX = x + 0.5f + MathUtils.sinDeg(legA) * legLen, bootAY = hipY - MathUtils.cosDeg(legA) * legLen;
        float bootBX = x - 3.5f + MathUtils.sinDeg(legB) * legLen, bootBY = hipY - MathUtils.cosDeg(legB) * legLen;
        rect(bootBX - 4 + f * 1.5f, bootBY - 2, 9, 4.5f, 4.5f, 2.25f, 0, 0x2b2b2b);
        rect(bootAX - 4 + f * 1.5f, bootAY - 2, 9, 4.5f, 4.5f, 2.25f, 0, 0x2b2b2b);

        // ---- jetpack (nas costas)
        float jpX = x - f * 10, jpY = hipY + 1;
        rrect(jpX - 4.5f, jpY, 9, 15, 4.5f, 0, 0, 0x7f8c8d);
        detailRect(jpX - 3, jpY + 10, 6, 2, 0, 0, 0, 0xc0392b);
        rect(jpX - 3, jpY - 3, 6, 4, 0, 0, 0, 0x4a4a4a);

        // ---- bandeira / bomba nas costas
        if (s.carryingFlag) {
            float poleX = x - f * 12;
            rect(poleX - 1, hipY, 2.5f, 34, 0, 0, 0, 0xdddddd);
            float wave = MathUtils.sin(s.time * 9) * 2.5f;
            rect(poleX - (f > 0 ? 20 : 0), hipY + 22 + wave * 0.3f, 20, 12, 0, 0, wave, s.flagColor);
        }
        if (s.hasBomb) {
            rect(x - f * 11 - 5, hipY + 4, 10, 8, 0, 0, 0, 0x8b0000);
            detailCircle(x - f * 11, hipY + 10, 1.8f, ((int) (s.time * 3) % 2 == 0) ? 0xff3333 : 0x550000);
        }

        // ---- braco de tras
        float rad = s.aim;
        float ca = MathUtils.cos(rad), sa = MathUtils.sin(rad);
        float melee = s.meleeAnim > 0 ? MathUtils.sin(s.meleeAnim / 0.25f * MathUtils.PI) : 0;
        rect(shoulderX - f * 2, shoulderY - 2.5f, 13, 5, 0, 2.5f, aimDeg + 18 * f * (1 - melee), bodyDark);

        // ---- corpo
        rrect(x - 8.5f, hipY - 1, 17, torsoH + 2, 8.5f, 0, 0, body);
        detailRect(x - 8, hipY + 1, 16, 3, 0, 0, 0, darker(body, 0.6f)); // cinto
        detailRect(x - 1.5f, hipY + 1, 3, 3, 0, 0, 0, 0xd4ac0d); // fivela

        // ---- cabeca
        circle(headX, headY, headR, look.skin);
        // olhos / oculos
        detailRect(headX + f * 1.5f - (f > 0 ? 0 : 8), headY - 0.5f, 8, 4.5f, 0, 0, 0, look.visor);
        detailRect(headX + f * 3.5f - (f > 0 ? 0 : 2), headY + 2.2f, 2, 1.2f, 0, 0, 0, 0xffffff);
        detailRect(headX + f * 5.5f - 1.5f, headY - 4.5f, 3, 1.2f, 0, 0, 0, darker(look.skin, 0.6f));
        drawHat(look, headX, headY, headR, f, s.time);

        // ---- arma + braco da frente
        if (s.weapon != null) gun(s.weapon, shoulderX + ca * (4 + melee * 6), shoulderY + sa * (4 + melee * 6), aimDeg, f, s.time);
        float armLen = 12 + melee * 8;
        rect(shoulderX + f * 1, shoulderY - 2.5f, armLen, 5, 0, 2.5f, aimDeg, body);
        circle(shoulderX + f * 1 + ca * armLen, shoulderY + sa * armLen, 3.3f, look.skin);

        flush(d, s.alpha, s.hurtFlash > 0);

        // escudo de spawn
        if (s.shield) {
            d.additive(true);
            d.color(0.4f, 0.8f, 1f, 0.35f + 0.15f * MathUtils.sin(s.time * 10));
            d.ring(x, y + 22, 32);
            d.color(0.4f, 0.8f, 1f, 0.12f);
            d.soft(x, y + 22, 34);
            d.additive(false);
        }
    }

    private void drawHat(Look look, float hx, float hy, float r, int f, float time) {
        int c = look.helmet;
        switch (look.hat) {
            case 0: // capacete
                add(T_HALF, hx - r * 1.2f, hy + 0.5f, r * 2.4f, r * 1.25f, 0, 0, 0, c, true);
                rect(hx - r * 1.25f + f * 1.5f, hy - 0.2f, r * 2.5f, 3, 0, 0, 0, darker(c, 0.75f));
                break;
            case 1: // bone
                add(T_HALF, hx - r * 1.05f, hy + 1.5f, r * 2.1f, r * 1.1f, 0, 0, 0, c, true);
                rect(hx + (f > 0 ? r * 0.3f : -r * 0.3f - 10), hy + 1.2f, 10, 3, 0, 0, 0, darker(c, 0.75f));
                break;
            case 2: // bandana
                rect(hx - r - 0.5f, hy + 3, r * 2 + 1, 4.5f, 0, 0, 0, c);
                rect(hx - f * (r + 1) - 1, hy + 2, 7, 3, 1, 1.5f, -f * 30 + MathUtils.sin(time * 8) * 8, c);
                rect(hx - f * (r + 1) - 1, hy + 1, 6, 3, 1, 1.5f, -f * 55 + MathUtils.sin(time * 8 + 1) * 8, c);
                break;
            case 3: // boina
                add(T_ELLIPSE, hx - f * 2, hy + r * 0.75f, r * 1.25f, r * 0.62f, 0, 0, -f * 12, c, true);
                detailCircle(hx - f * 2, hy + r * 1.3f, 1.5f, darker(c, 0.6f));
                break;
            case 4: // cartola
                rect(hx - r * 1.3f, hy + r * 0.55f, r * 2.6f, 3, 0, 0, 0, 0x1b1b1b);
                rect(hx - r * 0.8f, hy + r * 0.55f + 2, r * 1.6f, 13, 0, 0, 0, 0x1b1b1b);
                detailRect(hx - r * 0.8f, hy + r * 0.55f + 4, r * 1.6f, 3, 0, 0, 0, c);
                break;
            case 5: // moicano
                for (int i = 0; i < 4; i++) {
                    float ang = 150 - i * 30;
                    float cx = hx + MathUtils.cosDeg(ang) * r * 0.85f, cy = hy + MathUtils.sinDeg(ang) * r * 0.85f;
                    add(T_TRI, cx - 3.5f, cy - 1, 7, 8, 3.5f, 1, ang - 90, c, true);
                }
                break;
            default:
                break;
        }
    }

    /** Desenha a arma com a coronha em (x, y) apontando no angulo dado. */
    private void gun(WeaponType w, float x, float y, float deg, int f, float time) {
        float len = w.gunLen, h = w.gunH;
        int col = w.color;
        float sy = f > 0 ? 1 : -1; // espelha detalhes quando olha para a esquerda
        float ox = 0, oy = h / 2;
        // corpo
        rect(x, y - h / 2, len * 0.72f, h, ox, oy, deg, col);
        // cano
        float c = MathUtils.cosDeg(deg), s = MathUtils.sinDeg(deg);
        float bx = x + c * len * 0.7f, by = y + s * len * 0.7f;
        float bh = Math.max(3, h * 0.45f);
        if (w == WeaponType.ROCKET) {
            rect(x - c * 6, y - h / 2 - s * 6, len + 6, h, 0, h / 2, deg, col);
            rect(x + c * (len - 2), y + s * (len - 2) - h * 0.65f, 5, h * 1.3f, 0, h * 0.65f, deg, darker(col, 0.6f));
        } else if (w == WeaponType.SAW) {
            rect(bx, by - bh / 2, len * 0.3f, bh, 0, bh / 2, deg, 0x555555);
            circle(x + c * len, y + s * len, 6.5f, 0xbdc3c7);
            detailCircle(x + c * len, y + s * len, 2, 0x555555);
        } else if (w == WeaponType.FLAMETHROWER) {
            rect(bx, by - bh / 2, len * 0.32f, bh, 0, bh / 2, deg, 0x444444);
            circle(x + c * 6 - s * sy * 7, y + s * 6 + c * sy * 7, 4.5f, 0xe67e22);
        } else {
            rect(bx, by - bh / 2, len * 0.32f, bh, 0, bh / 2, deg, darker(col, 0.55f));
        }
        // carregador
        if (w == WeaponType.SMG || w == WeaponType.RIFLE || w == WeaponType.LMG || w == WeaponType.PISTOL || w == WeaponType.MAGNUM) {
            float mx = x + c * len * 0.32f + s * sy * h * 0.5f, my = y + s * len * 0.32f - c * sy * h * 0.5f;
            float mh = w == WeaponType.LMG ? 9 : 7;
            rect(mx, my, 4.5f, mh, 2.25f, mh, deg, darker(col, 0.5f));
        }
        if (w == WeaponType.SNIPER || w == WeaponType.LASER) {
            float mx = x + c * len * 0.25f - s * sy * h * 0.75f, my = y + s * len * 0.25f + c * sy * h * 0.75f;
            rect(mx, my - 2, 14, 4, 0, 2, deg, w == WeaponType.LASER ? 0x0e6655 : 0x1b1b1b);
        }
        if (w == WeaponType.LASER) {
            detailCircle(x + c * len, y + s * len, 2.5f, 0x7fffd4);
        }
        if (w == WeaponType.SHOTGUN || w == WeaponType.RIFLE || w == WeaponType.SNIPER) {
            // coronha de madeira
            rect(x - c * 7, y - s * 7 - h * 0.4f, 9, h * 0.8f, 0, h * 0.4f, deg, 0x8d5524);
        }
    }

    private void flush(Draw d, float alpha, boolean flash) {
        float o = 1.7f;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                if (pass == 0 && !pline[i]) continue;
                int col = pass == 0 ? OUTLINE : pcol[i];
                if (pass == 1 && flash) col = 0xffffff;
                d.rgb(col, alpha);
                float e = pass == 0 ? o : 0;
                switch (type[i]) {
                    case T_RECT:
                        d.batch.draw(d.a.white, px[i] - e, py[i] - e, pox[i] + e, poy[i] + e, pw[i] + e * 2, ph[i] + e * 2, 1, 1, prot[i]);
                        break;
                    case T_RRECT:
                        d.batch.draw(d.a.rrect, px[i] - e, py[i] - e, pox[i] + e, poy[i] + e, pw[i] + e * 2, ph[i] + e * 2, 1, 1, prot[i]);
                        break;
                    case T_CIRCLE:
                        d.circle(px[i], py[i], pw[i] + e);
                        break;
                    case T_HALF:
                        d.batch.draw(d.a.half, px[i] - e, py[i] - e * 0.5f, pw[i] + e * 2, ph[i] + e * 1.5f);
                        break;
                    case T_ELLIPSE:
                        d.ellipse(px[i], py[i], pw[i] + e, ph[i] + e, prot[i]);
                        break;
                    case T_TRI: {
                        TextureRegion t = d.a.tri;
                        d.batch.draw(t, px[i] - e, py[i] - e, pox[i] + e, poy[i] + e, pw[i] + e * 2, ph[i] + e * 2, 1, 1, prot[i]);
                        break;
                    }
                    default:
                        break;
                }
            }
        }
    }

    /** Arma sozinha (itens no chao, HUD). */
    public void drawGun(Draw d, WeaponType w, float cx, float cy, float deg, float scale, float alpha) {
        n = 0;
        float len = w.gunLen;
        gun(w, cx - len / 2 * MathUtils.cosDeg(deg), cy - len / 2 * MathUtils.sinDeg(deg), deg, 1, 0);
        if (scale != 1f) {
            // escala em torno do centro
            for (int i = 0; i < n; i++) {
                px[i] = cx + (px[i] - cx) * scale;
                py[i] = cy + (py[i] - cy) * scale;
                pw[i] *= type[i] == T_CIRCLE ? scale : scale;
                ph[i] *= scale;
                pox[i] *= scale;
                poy[i] *= scale;
            }
        }
        flush(d, alpha, false);
    }
}
