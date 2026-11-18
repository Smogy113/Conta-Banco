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
    /** espessura do contorno (configuravel) */
    public static float outlineWidth = 1.7f;

    private static final int MAX = 110;
    private final int[] type = new int[MAX];
    private final float[] px = new float[MAX], py = new float[MAX], pw = new float[MAX], ph = new float[MAX];
    private final float[] pox = new float[MAX], poy = new float[MAX], prot = new float[MAX];
    private final int[] pcol = new int[MAX];
    private final float[] palpha = new float[MAX];
    private final boolean[] pline = new boolean[MAX];
    private int n;

    private static final int T_RECT = 0, T_CIRCLE = 1, T_HALF = 2, T_TRI = 3, T_ELLIPSE = 4, T_RRECT = 5, T_RING = 6, T_STAR = 7, T_HALF_DOWN = 8;

    /** Estado de pose para desenhar. */
    public static class Pose {
        public float x, y, aim, anim, meleeAnim, hurtFlash, time;
        public int facing = 1, team;
        public boolean onGround = true, crouch, jetting, carryingFlag, hasBomb, shield;
        public int flagColor;
        public WeaponType weapon;
        public Look look;
        public float alpha = 1f;
        /** 0 normal, 1 comemorando, 2..5 dancas */
        public int action;
        public boolean zombie;
        /** velocidade horizontal (capa/cachecol balancam) */
        public float vx;
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
        palpha[n] = 1f;
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

    private void dRect(float x, float y, float w, float h, float ox, float oy, float rot, int col) {
        add(T_RECT, x, y, w, h, ox, oy, rot, col, false);
    }

    private void dCircle(float cx, float cy, float r, int col) {
        add(T_CIRCLE, cx, cy, r, r, 0, 0, 0, col, false);
    }

    private void dEllipse(float cx, float cy, float rx, float ry, float rot, int col) {
        add(T_ELLIPSE, cx, cy, rx, ry, 0, 0, rot, col, false);
    }

    private void alphaLast(float a) {
        if (n > 0) palpha[n - 1] = a;
    }

    public static int darker(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 0xff) * k), g = (int) (((rgb >> 8) & 0xff) * k), b = (int) ((rgb & 0xff) * k);
        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    public static int mix(int c1, int c2, float t) {
        int r = (int) (((c1 >> 16) & 0xff) * (1 - t) + ((c2 >> 16) & 0xff) * t);
        int g = (int) (((c1 >> 8) & 0xff) * (1 - t) + ((c2 >> 8) & 0xff) * t);
        int b = (int) ((c1 & 0xff) * (1 - t) + (c2 & 0xff) * t);
        return (r << 16) | (g << 8) | b;
    }

    public void draw(Draw d, Pose s) {
        n = 0;
        Look look = s.look;
        float x = s.x, y = s.y;
        boolean celebrate = s.action == 1;
        int dance = s.action >= 2 ? s.action - 1 : 0; // 1..4
        if (celebrate) y += Math.abs(MathUtils.sin(s.time * 7)) * 6;
        float dt8 = s.time * 8;
        if (dance == 1) y += Math.abs(MathUtils.sin(s.time * 6)) * 3;
        if (dance == 3) y += Math.max(0, MathUtils.sin(s.time * 7)) * 14;
        int f = s.facing;
        if (dance == 4) f = ((int) (s.time * 2.5f)) % 2 == 0 ? 1 : -1;
        int skin = s.zombie ? 0x9ccc65 : look.skin;
        int body = s.team == C.TEAM_RED ? RED : (s.team == C.TEAM_BLUE ? BLUE : look.body);
        int bodyDark = darker(body, 0.72f);
        int pants = look.pants;
        boolean crouch = s.crouch;
        float legLen = crouch ? 8 : 13;
        float hipY = y + legLen;
        float torsoH = crouch ? 15 : 17;
        float torsoTop = hipY + torsoH;
        float headR = 8.5f;
        float headX = x + f * 1.5f, headY = torsoTop + 6.5f;
        float shoulderX = x, shoulderY = y + (crouch ? C.CROUCH_H : C.PLAYER_H) * 0.66f;
        float aim = celebrate ? MathUtils.PI / 2 - f * 0.7f : s.aim;
        if (s.zombie && s.weapon == null && !celebrate && dance == 0) aim = f > 0 ? 0.05f : MathUtils.PI - 0.05f;
        if (dance == 1) aim = MathUtils.PI / 2 + MathUtils.sin(dt8) * 0.9f;
        else if (dance == 2) aim = -MathUtils.PI / 2 + f * (0.4f + 0.7f * MathUtils.sin(s.time * 9));
        else if (dance == 3) aim = MathUtils.sin(s.time * 7) > 0 ? MathUtils.PI / 2 + f * 0.6f : -MathUtils.PI / 2 + f * 0.9f;
        else if (dance == 4) aim = (((int) (s.time * 4)) % 2 == 0) ? 0 : MathUtils.PI / 2;
        float aimDeg = aim * MathUtils.radiansToDegrees;
        float sway = MathUtils.sin(s.time * 6 + x * 0.01f) * 6 - MathUtils.clamp(s.vx * 0.05f, -20, 20) * f;

        // ---- capa (atras de tudo)
        if (look.accessory == 2) {
            float capeLen = crouch ? 18 : 26;
            rect(x - f * 3 - 6, torsoTop - capeLen, 12, capeLen, 6, capeLen, f * (12 + sway * 0.8f), look.accessoryColor);
        }
        // ---- cabelo de tras (comprido / rabo / black power)
        int hc = look.hairColor;
        if (look.hair == 3) rect(headX - f * 4 - 5, headY - 13, 10, 15, 5, 15, f * 6, hc);
        if (look.hair == 4) circle(headX - f * 1, headY + 3, headR + 4.5f, hc);
        if (look.hair == 5) add(T_ELLIPSE, headX - f * 10, headY + 1, 4.5f, 3, 0, 0, f * (20 + sway), hc, true);

        // ---- pernas
        float swing = 0;
        if (s.onGround && !crouch) swing = MathUtils.sin(s.anim * 3.4f) * 32f;
        float legA = crouch ? 70 * f : swing, legB = crouch ? 15 * f : -swing;
        if (!s.onGround) {
            legA = 22 * f;
            legB = 8 * f;
        }
        if (celebrate) {
            legA = -12 * f;
            legB = 18 * f;
        }
        if (dance == 1) {
            legA = MathUtils.sin(dt8) * 14;
            legB = -legA;
        } else if (dance == 2) {
            legA = MathUtils.sin(s.time * 9) * 18;
            legB = legA * 0.6f;
        } else if (dance == 3) {
            float open = Math.max(0, MathUtils.sin(s.time * 7));
            legA = 28 * open;
            legB = -28 * open;
        } else if (dance == 4) {
            legA = (((int) (s.time * 4)) % 2 == 0) ? 20 : -6;
            legB = -legA * 0.5f;
        }
        boolean dancing = dance > 0;
        WeaponType weapon = dancing || celebrate && s.weapon != null && s.weapon.isShield() ? null : s.weapon;
        rect(x - 3.5f - 3, hipY - legLen, 7, legLen, 3.5f, legLen, legB, darker(pants, 0.8f));
        rect(x + 0.5f - 3, hipY - legLen, 7, legLen, 3.5f, legLen, legA, pants);
        float bootAX = x + 0.5f + MathUtils.sinDeg(legA) * legLen, bootAY = hipY - MathUtils.cosDeg(legA) * legLen;
        float bootBX = x - 3.5f + MathUtils.sinDeg(legB) * legLen, bootBY = hipY - MathUtils.cosDeg(legB) * legLen;
        rect(bootBX - 4 + f * 1.5f, bootBY - 2, 9, 4.5f, 4.5f, 2.25f, 0, 0x2b2b2b);
        rect(bootAX - 4 + f * 1.5f, bootAY - 2, 9, 4.5f, 4.5f, 2.25f, 0, 0x2b2b2b);

        // ---- jetpack
        jetpack(look, x - f * 10, hipY + 1, f);

        // ---- bandeira / bomba nas costas
        if (s.carryingFlag) {
            float poleX = x - f * 12;
            rect(poleX - 1, hipY, 2.5f, 34, 0, 0, 0, 0xdddddd);
            float wave = MathUtils.sin(s.time * 9) * 2.5f;
            rect(poleX - (f > 0 ? 20 : 0), hipY + 22 + wave * 0.3f, 20, 12, 0, 0, wave, s.flagColor);
        }
        if (s.hasBomb) {
            rect(x - f * 11 - 5, hipY + 4, 10, 8, 0, 0, 0, 0x8b0000);
            dCircle(x - f * 11, hipY + 10, 1.8f, ((int) (s.time * 3) % 2 == 0) ? 0xff3333 : 0x550000);
        }

        // ---- braco de tras
        float melee = s.meleeAnim > 0 ? MathUtils.sin(s.meleeAnim / 0.25f * MathUtils.PI) : 0;
        float backArm = celebrate ? 90 + f * 25 + MathUtils.sin(s.time * 8) * 12 : aimDeg + 18 * f * (1 - melee);
        boolean dual = weapon != null && weapon.dual;
        if (dual) backArm = aimDeg - 4 * f;
        if (dance == 1) backArm = 90 - MathUtils.sin(dt8) * 50;
        else if (dance == 2) backArm = -90 - f * (25 + 40 * MathUtils.sin(s.time * 9));
        else if (dance == 3) backArm = aimDeg + 180 - f * 2 * (aimDeg - 90);
        else if (dance == 4) backArm = (((int) (s.time * 4 + 1)) % 2 == 0) ? 180 : 90;
        if (s.zombie && weapon == null && !dancing && !celebrate) backArm = aimDeg + 6 * f;
        if (dual) { // segunda arma na mao de tras
            float bc = MathUtils.cosDeg(backArm), bs = MathUtils.sinDeg(backArm);
            gun(weapon, look.gunSkin, shoulderX - f * 2 + bc * 4, shoulderY + 3 + bs * 4, backArm, f);
        }
        rect(shoulderX - f * 2, shoulderY - 2.5f, 13, 5, 0, 2.5f, backArm, bodyDark);
        if (celebrate || dancing || dual || s.zombie) circle(shoulderX - f * 2 + MathUtils.cosDeg(backArm) * 13, shoulderY + MathUtils.sinDeg(backArm) * 13, 3.3f, skin);

        // ---- corpo
        rrect(x - 8.5f, hipY - 1, 17, torsoH + 2, 8.5f, 0, 0, body);
        pattern(look, x, hipY, torsoH);
        if (look.accessory == 3) { // colete
            dRect(x - 7, hipY + 2, 14, torsoH - 3, 0, 0, 0, darker(look.accessoryColor, 0.8f));
            dRect(x - 5.5f, hipY + 4, 4, 4, 0, 0, 0, darker(look.accessoryColor, 0.55f));
            dRect(x + 1.5f, hipY + 4, 4, 4, 0, 0, 0, darker(look.accessoryColor, 0.55f));
        }
        dRect(x - 8, hipY + 1, 16, 3, 0, 0, 0, darker(body, 0.5f)); // cinto
        dRect(x - 1.5f, hipY + 1, 3, 3, 0, 0, 0, 0xd4ac0d); // fivela
        if (look.accessory == 4) { // medalha
            dRect(x + f * 3 - 1.5f, torsoTop - 7, 3, 4, 0, 0, 0, look.accessoryColor);
            dCircle(x + f * 3, torsoTop - 8.5f, 2.2f, 0xffc107);
        }

        // ---- cachecol
        if (look.accessory == 1) {
            rect(x - 7, torsoTop - 3.5f, 14, 4.5f, 0, 0, 0, look.accessoryColor);
            rect(x - f * 6, torsoTop - 2, 9, 3.5f, 0, 1.75f, 180 + (f > 0 ? 0 : 180) - f * (10 + sway), look.accessoryColor);
        }

        // ---- cabeca
        circle(headX, headY, headR, skin);
        face(look, headX, headY, headR, f, dancing ? (f > 0 ? 0 : MathUtils.PI) : aim, s.time);
        if (s.zombie) { // olheiras e cicatriz
            dCircle(headX + f * 3.5f, headY + 1, 2.6f, 0x33691e);
            alphaLast(0.5f);
            dRect(headX - f * 2 - 3, headY + 4, 6, 1.4f, 0, 0, f * 25, 0x7b1f1f);
        }
        drawHat(look, headX, headY, headR, f, s.time);

        // ---- arma + braco da frente
        float ca = MathUtils.cos(aim), sa = MathUtils.sin(aim);
        boolean shieldW = weapon != null && weapon.isShield();
        if (weapon != null && !shieldW) gun(weapon, look.gunSkin, shoulderX + ca * (4 + melee * 6), shoulderY + sa * (4 + melee * 6), aimDeg, f);
        float armLen = 12 + melee * 8;
        if (s.zombie && weapon == null && !dancing) armLen = 15 + melee * 8;
        rect(shoulderX + f * 1, shoulderY - 2.5f, armLen, 5, 0, 2.5f, aimDeg, body);
        circle(shoulderX + f * 1 + ca * armLen, shoulderY + sa * armLen, 3.3f, skin);
        if (shieldW) { // escudo anti-motim na frente do corpo
            float tilt = MathUtils.clamp(f > 0 ? aimDeg : 180 - aimDeg, -35, 35) * 0.35f * f;
            float sx0 = x + f * (11 + melee * 8), sy0 = hipY - 9;
            rrect(sx0 - 4, sy0, 8, 38, 4, 0, tilt, 0x37474f);
            dRect(sx0 - 2.5f, sy0 + 24, 5, 9, -1.5f + 4, -24, tilt, 0x80deea);
            alphaLast(0.75f);
            dRect(sx0 - 2.5f, sy0 + 5, 5, 2, -1.5f + 4, -5, tilt, 0xffeb3b);
        }

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

    private void jetpack(Look look, float jx, float jy, int f) {
        int c = look.jetColor;
        switch (look.jetpack) {
            case 1: // foguete duplo
                rrect(jx - 5, jy - 1, 4.5f, 17, 0, 0, 0, c);
                rrect(jx + 0.5f, jy - 1, 4.5f, 17, 0, 0, 0, c);
                add(T_TRI, jx - 5, jy + 15, 4.5f, 5, 0, 0, 0, 0xc62828, true);
                add(T_TRI, jx + 0.5f, jy + 15, 4.5f, 5, 0, 0, 0, 0xc62828, true);
                break;
            case 2: // barril
                rrect(jx - 6, jy, 12, 14, 0, 0, 0, c);
                dRect(jx - 6, jy + 3, 12, 1.5f, 0, 0, 0, darker(c, 0.6f));
                dRect(jx - 6, jy + 9.5f, 12, 1.5f, 0, 0, 0, darker(c, 0.6f));
                rect(jx - 3, jy - 3, 6, 4, 0, 0, 0, 0x4a4a4a);
                break;
            case 3: // asas
                add(T_TRI, jx - f * 4 - 9, jy + 8, 18, 12, 9, 0, f * 55, darker(c, 0.85f), true);
                rrect(jx - 4.5f, jy, 9, 14, 0, 0, 0, c);
                rect(jx - 3, jy - 3, 6, 4, 0, 0, 0, 0x4a4a4a);
                break;
            case 4: // tanque
                rrect(jx - 4, jy - 2, 8, 21, 0, 0, 0, c);
                dCircle(jx, jy + 13, 2.2f, 0xffffff);
                dRect(jx - 0.4f, jy + 13, 0.8f, 2, 0, 0, 30, 0xd32f2f);
                rect(jx - 2.5f, jy - 5, 5, 4, 0, 0, 0, 0x4a4a4a);
                break;
            default:
                rrect(jx - 4.5f, jy, 9, 15, 4.5f, 0, 0, c);
                dRect(jx - 3, jy + 10, 6, 2, 0, 0, 0, 0xc0392b);
                rect(jx - 3, jy - 3, 6, 4, 0, 0, 0, 0x4a4a4a);
                break;
        }
    }

    private void pattern(Look look, float x, float hipY, float torsoH) {
        int pc = look.patternColor;
        float t = hipY + torsoH;
        switch (look.pattern) {
            case 1: // camuflado
                dEllipse(x - 3.5f, hipY + 6, 3.5f, 2.2f, 20, pc);
                dEllipse(x + 3.5f, hipY + 11, 3, 2, -15, pc);
                dEllipse(x - 2, t - 3, 3, 1.8f, 0, darker(pc, 0.7f));
                dEllipse(x + 4, hipY + 4, 2, 1.5f, 40, darker(pc, 0.7f));
                break;
            case 2: // listras
                for (int i = 0; i < 3; i++) dRect(x - 8, hipY + 5 + i * 4.2f, 16, 1.8f, 0, 0, 0, pc);
                break;
            case 3: // tigre
                for (int i = 0; i < 3; i++) dRect(x - 6 + i * 4.5f, hipY + 5, 1.6f, 9, 0.8f, 4.5f, 25, pc);
                break;
            case 4: // estrelas
                add(T_STAR, x - 2.5f, hipY + 10, 6, 6, 0, 0, 0, pc, false);
                add(T_STAR, x + 3.5f, hipY + 5, 4, 4, 0, 0, 0, pc, false);
                break;
            case 5: // xadrez
                dRect(x - 8, hipY + 4, 8, 6, 0, 0, 0, pc);
                dRect(x, hipY + 10, 8, 6, 0, 0, 0, pc);
                alphaLast(0.85f);
                break;
            case 6: // raio
                dRect(x - 2, hipY + 9, 2, 8, 1, 4, -25, pc);
                dRect(x + 1, hipY + 4, 2, 8, 1, 4, -25, pc);
                dRect(x - 1, hipY + 7, 4, 1.8f, 0, 0, 0, pc);
                break;
            default:
                break;
        }
    }

    private void face(Look look, float hx, float hy, float r, int f, float aim, float time) {
        int ink = 0x1b1b1b;
        float ex = hx + f * 4.2f, ey = hy + 1.2f;
        float ex2 = hx + f * 0.2f;
        float lx = MathUtils.cos(aim) * 0.9f, ly = MathUtils.sin(aim) * 0.9f;
        boolean blink = (time % 4f) < 0.12f;
        // barba (embaixo da boca)
        int hc = look.hairColor;
        switch (look.beard) {
            case 1:
                add(T_HALF_DOWN, hx - r * 0.95f, hy - r * 0.95f, r * 1.9f, r * 0.85f, 0, 0, 0, darker(look.skin, 0.75f), false);
                alphaLast(0.6f);
                break;
            case 2:
                add(T_HALF_DOWN, hx - r * 1.02f, hy - r * 1.08f, r * 2.04f, r * 1.15f, 0, 0, 0, hc, true);
                break;
            case 4:
                dRect(hx + f * 3.5f - 2, hy - r - 1.5f, 4, 4, 0, 0, 0, hc);
                break;
            default:
                break;
        }
        // olhos
        if (look.eyewear != 3 && look.eyewear != 5) {
            switch (look.eyes) {
                case 2: // feliz ^^
                    dRect(ex - 2, ey - 0.5f, 3, 1.2f, 0, 0.6f, 40, ink);
                    dRect(ex - 0.2f, ey + 1.4f, 3, 1.2f, 0, 0.6f, -40, ink);
                    break;
                case 4: // robo
                    dRect(hx + f * 1, ey - 1, f * 7.5f, 2.4f, 0, 0, 0, 0xff1744);
                    break;
                default: {
                    float big = look.eyes == 5 ? 1.35f : 1f;
                    dEllipse(ex2, ey, 2.2f * big, 2.6f * big, 0, 0xffffff);
                    dEllipse(ex, ey, 2.6f * big, 3f * big, 0, 0xffffff);
                    if (blink) {
                        dRect(ex - 2.6f, ey - 0.6f, 5.2f, 1.2f, 0, 0, 0, ink);
                    } else {
                        float pr = look.eyes == 5 ? 0.9f : 1.5f;
                        dCircle(ex + lx, ey + ly, pr, ink);
                        dCircle(ex2 + lx * 0.8f, ey + ly * 0.8f, pr * 0.85f, ink);
                    }
                    if (look.eyes == 3) { // sonolento: palpebra
                        dRect(ex - 3, ey, 6, 3.2f, 0, 0, 0, darker(look.skin, 0.85f));
                        dRect(ex2 - 2.6f, ey, 5.2f, 3, 0, 0, 0, darker(look.skin, 0.85f));
                    }
                    if (look.eyes == 1) { // bravo: sobrancelhas
                        dRect(ex - 3, ey + 3.4f, 5.5f, 1.4f, 2.75f, 0.7f, f * -20, ink);
                        dRect(ex2 - 2.5f, ey + 3.2f, 4.5f, 1.3f, 2.25f, 0.65f, f * 20, ink);
                    }
                    break;
                }
            }
        }
        // boca
        float mx = hx + f * 3.8f, my = hy - 4.2f;
        if (look.eyewear != 4 && look.eyewear != 5) {
            switch (look.mouth) {
                case 1:
                    add(T_HALF_DOWN, mx - 2.6f, my - 1.6f, 5.2f, 2.6f, 0, 0, 0, ink, false);
                    break;
                case 2:
                    dRect(mx - 2.6f, my - 1.2f, 5.2f, 2.6f, 0, 0, 0, ink);
                    dRect(mx - 2.1f, my - 0.7f, 4.2f, 1.6f, 0, 0, 0, 0xffffff);
                    dRect(mx - 0.2f, my - 0.7f, 0.4f, 1.6f, 0, 0, 0, ink);
                    break;
                case 3:
                    dCircle(mx, my, 2.1f, ink);
                    dCircle(mx, my - 0.8f, 1f, 0xe57373);
                    break;
                case 4:
                    add(T_RING, mx + f * 0.5f, my, 1.4f, 1.4f, 0, 0, 0, ink, false);
                    break;
                case 5:
                    add(T_HALF_DOWN, mx - 2.6f, my - 1.6f, 5.2f, 2.6f, 0, 0, 0, ink, false);
                    dEllipse(mx + f * 0.6f, my - 1.8f, 1.4f, 1.6f, 0, 0xf06292);
                    break;
                default:
                    dRect(mx - 1.8f, my - 0.5f, 3.6f, 1.1f, 0, 0, 0, ink);
                    break;
            }
        }
        if (look.beard == 3) dRect(mx - 3, my + 0.8f, 6, 2.2f, 0, 0, 0, hc); // bigode
        // oculos / mascaras
        int v = look.visor;
        switch (look.eyewear) {
            case 0: // oculos de protecao
                dRect(hx + f * 1.2f - (f > 0 ? 0 : 8.5f), ey - 2.2f, 8.5f, 4.6f, 0, 0, 0, v);
                alphaLast(0.92f);
                dRect(hx + f * 3.5f - (f > 0 ? 0 : 2), ey + 0.6f, 2, 1.2f, 0, 0, 0, 0xffffff);
                dRect(hx - f * r * 0.9f - (f > 0 ? 0 : 3), ey - 1, 3, 1.6f, 0, 0, 0, darker(v, 0.4f));
                break;
            case 2: // oculos escuros
                dRect(hx + f * 0.5f - (f > 0 ? 0 : 9), ey - 1.8f, 9, 3.6f, 0, 0, 0, 0x111111);
                dRect(hx + f * 4.5f - (f > 0 ? 0 : 1.6f), ey + 0.3f, 1.6f, 0.8f, 0, 0, 0, 0x90a4ae);
                break;
            case 3: // tapa-olho
                dRect(hx - r, ey + 2.2f, r * 2, 1.1f, r, 0.55f, f * -18, 0x111111);
                dEllipse(ex, ey, 2.8f, 2.6f, 0, 0x111111);
                dEllipse(ex2, ey, 2f, 2.4f, 0, 0xffffff);
                dCircle(ex2 + lx * 0.8f, ey + ly * 0.8f, 1.2f, 0x111111);
                break;
            case 4: // mascara ninja
                add(T_HALF_DOWN, hx - r * 1.02f, hy - r * 1.04f, r * 2.04f, r - 1.2f, 0, 0, 0, v, false);
                dRect(hx - r, hy - 3.6f, r * 2, 1.8f, 0, 0, 0, v);
                dRect(hx - f * r - (f > 0 ? 4 : 0), hy - 4.5f, 4, 2.2f, 0, 1.1f, f * 25 + MathUtils.sin(time * 9) * 10, v);
                break;
            case 5: // mascara de gas
                dEllipse(ex, ey, 2.6f, 2.6f, 0, 0x263238);
                dEllipse(ex, ey, 1.6f, 1.6f, 0, v);
                dEllipse(ex2, ey, 2.2f, 2.4f, 0, 0x263238);
                circle(hx + f * 5, hy - 4, 3.2f, 0x546e7a);
                dCircle(hx + f * 7, hy - 5, 1.6f, 0x263238);
                break;
            case 6: // monoculo
                add(T_RING, ex, ey, 3.4f, 3.4f, 0, 0, 0, 0xffc107, false);
                dRect(ex, ey - 3, 0.7f, 6, 0, 0, -f * 15, 0xffc107);
                break;
            default:
                break;
        }
        // cabelo da frente (aparece fora do chapeu)
        switch (look.hair) {
            case 1:
            case 3:
            case 5:
                add(T_HALF, hx - r * 1.04f, hy + 1.5f, r * 2.08f, r * 0.95f, 0, 0, 0, hc, true);
                break;
            case 2:
                for (int i = 0; i < 4; i++) {
                    float ang = 150 - i * 32;
                    float cx = hx + MathUtils.cosDeg(ang) * r * 0.8f, cy = hy + MathUtils.sinDeg(ang) * r * 0.8f;
                    add(T_TRI, cx - 3, cy - 1, 6, 7, 3, 1, ang - 90, hc, true);
                }
                break;
            case 6:
                add(T_HALF, hx - r * 1.04f, hy + 1.5f, r * 2.08f, r * 0.95f, 0, 0, 0, hc, true);
                add(T_ELLIPSE, hx + f * 5, hy + r * 0.85f, 5, 3, 0, 0, f * -20, hc, true);
                break;
            default:
                break;
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
                dCircle(hx - f * 2, hy + r * 1.3f, 1.5f, darker(c, 0.6f));
                break;
            case 4: // cartola
                rect(hx - r * 1.3f, hy + r * 0.55f, r * 2.6f, 3, 0, 0, 0, 0x1b1b1b);
                rect(hx - r * 0.8f, hy + r * 0.55f + 2, r * 1.6f, 13, 0, 0, 0, 0x1b1b1b);
                dRect(hx - r * 0.8f, hy + r * 0.55f + 4, r * 1.6f, 3, 0, 0, 0, c);
                break;
            case 5: // moicano
                for (int i = 0; i < 4; i++) {
                    float ang = 150 - i * 30;
                    float cx = hx + MathUtils.cosDeg(ang) * r * 0.85f, cy = hy + MathUtils.sinDeg(ang) * r * 0.85f;
                    add(T_TRI, cx - 3.5f, cy - 1, 7, 8, 3.5f, 1, ang - 90, c, true);
                }
                break;
            case 7: // cowboy
                add(T_ELLIPSE, hx, hy + r * 0.6f, r * 1.75f, 2.4f, 0, 0, f * -4, darker(c, 0.85f), true);
                rrect(hx - r * 0.75f, hy + r * 0.6f, r * 1.5f, 9, 0, 0, 0, c);
                dRect(hx - r * 0.75f, hy + r * 0.6f + 1.5f, r * 1.5f, 2, 0, 0, 0, darker(c, 0.55f));
                break;
            case 8: // gorro
                add(T_HALF, hx - r * 1.12f, hy + 1, r * 2.24f, r * 1.3f, 0, 0, 0, c, true);
                rect(hx - r * 1.15f, hy, r * 2.3f, 3.5f, 0, 0, 0, darker(c, 0.75f));
                circle(hx - f * 1, hy + r * 1.35f + 1.5f, 2.8f, 0xffffff);
                break;
            case 9: // coroa
                rect(hx - r * 0.85f, hy + r * 0.55f, r * 1.7f, 4, 0, 0, 0, 0xffc107);
                for (int i = -1; i <= 1; i++) add(T_TRI, hx + i * r * 0.55f - 2.5f, hy + r * 0.55f + 3.5f, 5, 6, 2.5f, 0, 0, 0xffc107, true);
                dCircle(hx, hy + r * 0.55f + 2, 1.2f, 0xe53935);
                dCircle(hx - r * 0.5f, hy + r * 0.55f + 2, 1, 0x1e88e5);
                dCircle(hx + r * 0.5f, hy + r * 0.55f + 2, 1, 0x43a047);
                break;
            case 10: // viking
                add(T_TRI, hx - f * 2 - r - 4, hy + 3, 6, 10, 3, 0, f * 35, 0xf5f5dc, true);
                add(T_TRI, hx - f * 2 + r - 2, hy + 3, 6, 10, 3, 0, f * -35, 0xf5f5dc, true);
                add(T_HALF, hx - r * 1.15f, hy + 0.5f, r * 2.3f, r * 1.2f, 0, 0, 0, c, true);
                dRect(hx - 1, hy + 0.5f, 2, r * 1.2f, 0, 0, 0, darker(c, 0.7f));
                break;
            case 11: // headset
                add(T_RING, hx - f * 0.5f, hy + 1.5f, r * 1.12f, r * 1.12f, 0, 0, 0, 0x263238, false);
                circle(hx - f * 1.5f, hy, 3.2f, c);
                dRect(hx - f * 1.5f, hy - 1, f * 8, 1.1f, 0, 0, -f * 25, 0x263238);
                dCircle(hx + f * 6, hy - 4.5f, 1.2f, 0x263238);
                break;
            case 12: // bone virado
                add(T_HALF, hx - r * 1.05f, hy + 1.5f, r * 2.1f, r * 1.1f, 0, 0, 0, c, true);
                rect(hx - (f > 0 ? r * 0.3f + 10 : -r * 0.3f), hy + 1.2f, 10, 3, 0, 0, 0, darker(c, 0.75f));
                break;
            case 13: // capacete espacial
                add(T_CIRCLE, hx, hy, r * 1.45f, r * 1.45f, 0, 0, 0, 0xb3e5fc, false);
                alphaLast(0.28f);
                add(T_RING, hx, hy, r * 1.45f, r * 1.45f, 0, 0, 0, 0xeceff1, false);
                dRect(hx - r * 1.1f, hy - r * 1.35f, r * 2.2f, 2.5f, 0, 0, 0, c);
                dEllipse(hx + f * 4, hy + 6, 2.4f, 1.2f, 30, 0xffffff);
                alphaLast(0.6f);
                break;
            default:
                break;
        }
    }

    private static int gunColor(int base, int skin) {
        switch (skin) {
            case 1: return 0xffc107;
            case 2: return 0x556b2f;
            case 3: return 0x00e5ff;
            case 4: return 0xb3e5fc;
            case 5: return 0xff5722;
            case 6: return 0xf06292;
            default: return base;
        }
    }

    /** Desenha a arma com a coronha em (x, y) apontando no angulo dado. */
    private void gun(WeaponType w, int skin, float x, float y, float deg, int f) {
        float len = w.gunLen, h = w.gunH;
        int col = gunColor(w.color, skin);
        float sy = f > 0 ? 1 : -1;
        if (w == WeaponType.BOW) {
            float c = MathUtils.cosDeg(deg), s = MathUtils.sinDeg(deg);
            rect(x + c * 8, y + s * 8, 3.5f, 17, 1.75f, 0, deg + 90 - 18 * sy, skin == 0 ? 0x8d5524 : col);
            rect(x + c * 8, y + s * 8, 3.5f, 17, 1.75f, 0, deg - 90 + 18 * sy, skin == 0 ? 0x8d5524 : col);
            float tx1 = x + c * 8 + MathUtils.cosDeg(deg + 90 - 18 * sy) * 17, ty1 = y + s * 8 + MathUtils.sinDeg(deg + 90 - 18 * sy) * 17;
            float tx2 = x + c * 8 + MathUtils.cosDeg(deg - 90 + 18 * sy) * 17, ty2 = y + s * 8 + MathUtils.sinDeg(deg - 90 + 18 * sy) * 17;
            float ang = MathUtils.atan2(ty2 - ty1, tx2 - tx1) * MathUtils.radiansToDegrees;
            float l2 = (float) Math.sqrt((tx2 - tx1) * (tx2 - tx1) + (ty2 - ty1) * (ty2 - ty1));
            dRect(tx1, ty1 - 0.5f, l2, 1f, 0, 0.5f, ang, 0xeeeeee);
            rect(x, y - 1, 22, 2, 0, 1, deg, 0xa1887f); // flecha
            add(T_TRI, x + c * 22 - 3, y + s * 22 - 3, 6, 6, 3, 3, deg - 90, 0x9e9e9e, true);
            return;
        }
        if (w == WeaponType.BOOMERANG) {
            float c = MathUtils.cosDeg(deg), s = MathUtils.sinDeg(deg);
            int bc = skin == 0 ? 0xffb300 : col;
            rect(x + c * 4, y + s * 4 - 2.5f, 14, 5, 0, 2.5f, deg + 35, bc);
            rect(x + c * 4, y + s * 4 - 2.5f, 14, 5, 0, 2.5f, deg - 35, darker(bc, 0.85f));
            return;
        }
        rect(x, y - h / 2, len * 0.72f, h, 0, h / 2, deg, col);
        if (skin == 1 || skin == 3 || skin == 4) { // brilho
            dRect(x, y + h * 0.1f - h / 2 + h * 0.55f * (sy > 0 ? 1 : 0.2f), len * 0.7f, h * 0.18f, 0, h * 0.09f - h * 0.05f, deg, 0xffffff);
            alphaLast(0.45f);
        }
        if (skin == 2) {
            dEllipse(x + MathUtils.cosDeg(deg) * len * 0.3f, y + MathUtils.sinDeg(deg) * len * 0.3f, len * 0.1f, h * 0.25f, deg, 0x33402a);
        }
        float c = MathUtils.cosDeg(deg), s = MathUtils.sinDeg(deg);
        float bx = x + c * len * 0.7f, by = y + s * len * 0.7f;
        float bh = Math.max(3, h * 0.45f);
        int barrel = skin == 0 ? darker(col, 0.55f) : darker(col, 0.7f);
        if (w == WeaponType.FROST || w == WeaponType.TESLA || w == WeaponType.GRAVITY || w == WeaponType.HARPOON) {
            int accent = w == WeaponType.FROST ? 0x4fc3f7 : (w == WeaponType.TESLA ? 0xffee58 : (w == WeaponType.GRAVITY ? 0xb388ff : 0xbdbdbd));
            if (w == WeaponType.HARPOON) {
                rect(bx, by - bh / 2, len * 0.42f, bh, 0, bh / 2, deg, barrel);
                rect(x + c * len * 0.95f, y + s * len * 0.95f - 1, 12, 2, 0, 1, deg, 0xcfd8dc);
                add(T_TRI, x + c * (len + 11) - 3.5f, y + s * (len + 11) - 3.5f, 7, 7, 3.5f, 3.5f, deg - 90, 0x90a4ae, true);
                return;
            }
            rect(bx, by - bh / 2, len * 0.3f, bh, 0, bh / 2, deg, barrel);
            if (w == WeaponType.FROST) {
                circle(x + c * 7 - s * sy * 6, y + s * 7 + c * sy * 6, 4.8f, accent);
                dCircle(x + c * len, y + s * len, 2.4f, 0xe1f5fe);
            } else if (w == WeaponType.TESLA) {
                for (int k = 0; k < 3; k++) {
                    float kx = x + c * (len * 0.35f + k * 4.5f), ky = y + s * (len * 0.35f + k * 4.5f);
                    add(T_RING, kx, ky, h * 0.55f, h * 0.55f, 0, 0, 0, accent, false);
                }
                dCircle(x + c * (len + 1), y + s * (len + 1), 3, 0xfff9c4);
            } else {
                circle(x + c * (len + 2), y + s * (len + 2), h * 0.55f, 0x311b92);
                dCircle(x + c * (len + 2), y + s * (len + 2), h * 0.3f, accent);
            }
            return;
        }
        if (w == WeaponType.ROCKET) {
            rect(x - c * 6, y - h / 2 - s * 6, len + 6, h, 0, h / 2, deg, col);
            rect(x + c * (len - 2), y + s * (len - 2) - h * 0.65f, 5, h * 1.3f, 0, h * 0.65f, deg, darker(col, 0.6f));
        } else if (w == WeaponType.SAW) {
            rect(bx, by - bh / 2, len * 0.3f, bh, 0, bh / 2, deg, 0x555555);
            circle(x + c * len, y + s * len, 6.5f, 0xbdc3c7);
            dCircle(x + c * len, y + s * len, 2, 0x555555);
        } else if (w == WeaponType.FLAMETHROWER) {
            rect(bx, by - bh / 2, len * 0.32f, bh, 0, bh / 2, deg, 0x444444);
            circle(x + c * 6 - s * sy * 7, y + s * 6 + c * sy * 7, 4.5f, skin == 0 ? 0xe67e22 : col);
        } else {
            rect(bx, by - bh / 2, len * 0.32f, bh, 0, bh / 2, deg, barrel);
        }
        if (w == WeaponType.SMG || w == WeaponType.RIFLE || w == WeaponType.LMG || w == WeaponType.PISTOL || w == WeaponType.MAGNUM
                || w == WeaponType.DUAL_PISTOLS || w == WeaponType.DUAL_SMG) {
            float mx = x + c * len * 0.32f + s * sy * h * 0.5f, my = y + s * len * 0.32f - c * sy * h * 0.5f;
            float mh = w == WeaponType.LMG ? 9 : 7;
            rect(mx, my, 4.5f, mh, 2.25f, mh, deg, darker(col, 0.5f));
        }
        if (w == WeaponType.SNIPER || w == WeaponType.LASER) {
            float mx = x + c * len * 0.25f - s * sy * h * 0.75f, my = y + s * len * 0.25f + c * sy * h * 0.75f;
            rect(mx, my - 2, 14, 4, 0, 2, deg, w == WeaponType.LASER ? 0x0e6655 : 0x1b1b1b);
        }
        if (w == WeaponType.LASER) dCircle(x + c * len, y + s * len, 2.5f, 0x7fffd4);
        if (w == WeaponType.SHOTGUN || w == WeaponType.RIFLE || w == WeaponType.SNIPER) {
            rect(x - c * 7, y - s * 7 - h * 0.4f, 9, h * 0.8f, 0, h * 0.4f, deg, skin == 0 ? 0x8d5524 : darker(col, 0.75f));
        }
    }

    private void flush(Draw d, float alpha, boolean flash) {
        float o = outlineWidth;
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0 && o <= 0.01f) continue;
            for (int i = 0; i < n; i++) {
                if (pass == 0 && !pline[i]) continue;
                int col = pass == 0 ? OUTLINE : pcol[i];
                if (pass == 1 && flash && pline[i]) col = 0xffffff;
                d.rgb(col, alpha * palpha[i]);
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
                    case T_RING:
                        d.ring(px[i], py[i], pw[i] + e);
                        break;
                    case T_HALF:
                        d.batch.draw(d.a.half, px[i] - e, py[i] - e * 0.5f, pw[i] + e * 2, ph[i] + e * 1.5f);
                        break;
                    case T_HALF_DOWN:
                        d.batch.draw(d.a.half, px[i] - e, py[i] - e, (pw[i] + e * 2) / 2, (ph[i] + e * 1.5f) / 2, pw[i] + e * 2, ph[i] + e * 1.5f, 1, 1, 180);
                        break;
                    case T_ELLIPSE:
                        d.ellipse(px[i], py[i], pw[i] + e, ph[i] + e, prot[i]);
                        break;
                    case T_STAR:
                        d.batch.draw(d.a.star, px[i] - pw[i] / 2 - e, py[i] - ph[i] / 2 - e, pw[i] + e * 2, ph[i] + e * 2);
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

    /** Arma sozinha (itens no chao, HUD, feed). */
    public void drawGun(Draw d, WeaponType w, float cx, float cy, float deg, float scale, float alpha) {
        drawGun(d, w, 0, cx, cy, deg, scale, alpha);
    }

    public void drawGun(Draw d, WeaponType w, int skin, float cx, float cy, float deg, float scale, float alpha) {
        n = 0;
        float len = w.gunLen;
        if (w.isShield()) {
            rrect(cx - 8, cy - 16, 16, 32, 8, 16, deg + 8, 0x37474f);
            dRect(cx - 5, cy + 3, 10, 9, 5, -3, deg + 8, 0x80deea);
            alphaLast(0.75f);
            dRect(cx - 5, cy - 10, 10, 2.5f, 5, 10, deg + 8, 0xffeb3b);
        } else {
            if (w.dual) gun(w, skin, cx - len / 2 * MathUtils.cosDeg(deg) - 4, cy - len / 2 * MathUtils.sinDeg(deg) + 6, deg, 1);
            gun(w, skin, cx - len / 2 * MathUtils.cosDeg(deg), cy - len / 2 * MathUtils.sinDeg(deg), deg, 1);
        }
        if (scale != 1f) {
            for (int i = 0; i < n; i++) {
                px[i] = cx + (px[i] - cx) * scale;
                py[i] = cy + (py[i] - cy) * scale;
                pw[i] *= scale;
                ph[i] *= scale;
                pox[i] *= scale;
                poy[i] *= scale;
            }
        }
        float keep = outlineWidth;
        outlineWidth = Math.max(0.8f, Math.min(keep, keep * scale));
        flush(d, alpha, false);
        outlineWidth = keep;
    }
}
