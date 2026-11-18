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

    private static final int MAX = 160;
    private final int[] type = new int[MAX];
    private final float[] px = new float[MAX], py = new float[MAX], pw = new float[MAX], ph = new float[MAX];
    private final float[] pox = new float[MAX], poy = new float[MAX], prot = new float[MAX];
    private final int[] pcol = new int[MAX];
    private final float[] palpha = new float[MAX];
    private final boolean[] pline = new boolean[MAX];
    private int n;

    private static final int T_RECT = 0, T_CIRCLE = 1, T_HALF = 2, T_TRI = 3, T_ELLIPSE = 4, T_RRECT = 5, T_RING = 6, T_STAR = 7, T_HALF_DOWN = 8,
            T_BALL = 9, T_PILL = 10, T_BOX = 11, T_GLOSS = 12;

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

    private void ball(float cx, float cy, float r, int col) {
        add(T_BALL, cx, cy, r, r, 0, 0, 0, col, true);
    }

    /** pilula de (x0,y0) ate o comprimento len no angulo deg, grossura th */
    private void limb(float x0, float y0, float len, float th, float deg, int col, boolean outline) {
        add(T_PILL, x0 - th / 2, y0 - th / 2, len + th, th, th / 2, th / 2, deg, col, outline);
    }

    private void box(float x, float y, float w, float h, int col) {
        add(T_BOX, x, y, w, h, 0, 0, 0, col, true);
    }

    /** escala as pecas [from, n) em volta de (cx, cy) */
    private void scaleFrom(int from, float cx, float cy, float k) {
        for (int i = from; i < n; i++) {
            px[i] = cx + (px[i] - cx) * k;
            py[i] = cy + (py[i] - cy) * k;
            pw[i] *= k;
            ph[i] *= k;
            pox[i] *= k;
            poy[i] *= k;
        }
    }

    public static final float HEAD_R = 6.2f;
    private final Look costumeLook = new Look();

    // articulacao de 2 ossos: cotovelo/joelho
    private float jx, jy;

    /** calcula a junta (jx, jy) entre (sx,sy) e o alvo (tx,ty); bend = +1/-1 escolhe o lado */
    private void ik(float sx, float sy, float tx, float ty, float a, float b, float bend) {
        float dx = tx - sx, dy = ty - sy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        d = MathUtils.clamp(d, Math.abs(a - b) + 0.01f, a + b - 0.01f);
        float base = MathUtils.atan2(dy, dx);
        float cosA = MathUtils.clamp((a * a + d * d - b * b) / (2 * a * d), -1, 1);
        float ang = base + bend * (float) Math.acos(cosA);
        jx = sx + MathUtils.cos(ang) * a;
        jy = sy + MathUtils.sin(ang) * a;
    }

    private void seg(float x0, float y0, float x1, float y1, float th, int col) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        limb(x0, y0, len, th, MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees, col, true);
    }

    /** braco ate a mao (tx,ty): ombro -> cotovelo -> mao, com luva */
    private void arm(float sx, float sy, float tx, float ty, float bend, int sleeve, int glove, float th) {
        ik(sx, sy, tx, ty, 7.6f, 7.4f, bend);
        float ex = jx, ey = jy;
        seg(sx, sy, ex, ey, th, sleeve);
        seg(ex, ey, tx, ty, th * 0.9f, sleeve);
        ball(tx, ty, th * 0.42f + 0.4f, glove);
    }

    /** perna do quadril ate o pe, com joelho para a frente */
    private void leg(float hx, float hy, float fx, float fy, int f, int pants, int pad, int boot, float th) {
        ik(hx, hy, fx, fy, 10.2f, 9.8f, f);
        float kx = jx, ky = jy;
        seg(hx, hy, kx, ky, th, pants);
        seg(kx, ky, fx, fy, th * 0.88f, pants);
        add(T_BOX, kx - 2.8f, ky - 2.6f, 5.6f, 5.2f, 0, 0, 0, pad, false); // joelheira
        box(fx - 4 + f * 1.6f, fy - 2.6f, 9.5f, 5, boot);
    }

    /** onde as maos seguram cada arma (fracao do comprimento: cabo, guarda-mao) */
    private static final float[] G_PISTOL = {0.06f, 0.06f}, G_SMG = {0.2f, 0.48f}, G_ROCKET = {0.3f, 0.5f}, G_BOW = {0.0f, 0.36f},
            G_BOOM = {0.2f, 0.2f}, G_SHOTGUN = {0.12f, 0.6f}, G_DEFAULT = {0.14f, 0.56f}, G_NONE = {0, 0};

    private static float[] grips(WeaponType w) {
        switch (w) {
            case PISTOL: case MAGNUM: case DUAL_PISTOLS: return G_PISTOL;
            case SMG: case DUAL_SMG: return G_SMG;
            case ROCKET: return G_ROCKET;
            case BOW: return G_BOW;
            case BOOMERANG: return G_BOOM;
            case SHOTGUN: return G_SHOTGUN;
            default: return G_DEFAULT;
        }
    }

    public void draw(Draw d, Pose s) {
        ShadeKit.load();
        n = 0;
        Look look = s.look;
        if (look.costume != 0) {
            look.applyCostume(costumeLook);
            look = costumeLook;
        }
        float x = s.x, y = s.y;
        boolean celebrate = s.action == 1;
        int dance = s.action >= 2 ? s.action - 1 : 0;
        float bob = 0;
        if (celebrate) bob = Math.abs(MathUtils.sin(s.time * 7)) * 5;
        float dt8 = s.time * 8;
        if (dance == 1) bob = Math.abs(MathUtils.sin(s.time * 6)) * 3;
        if (dance == 3) bob = Math.max(0, MathUtils.sin(s.time * 7)) * 12;
        float breathe = s.onGround && Math.abs(s.vx) < 20 && dance == 0 && !celebrate ? MathUtils.sin(s.time * 3.2f) * 0.5f : 0;
        int f = s.facing;
        if (dance == 4) f = ((int) (s.time * 2.5f)) % 2 == 0 ? 1 : -1;
        int skin = s.zombie ? 0x8fae6a : look.skin;
        int body = s.team == C.TEAM_RED ? 0xa8372e : (s.team == C.TEAM_BLUE ? 0x2f5f9e : look.body);
        int bodyDark = darker(body, 0.72f);
        int pants = look.pants;
        int gear = mix(darker(body, 0.55f), 0x3a3d33, 0.5f); // colete / equipamento
        int gearL = mix(gear, 0xffffff, 0.12f);
        int glove = 0x2b2926, boot = 0x24201d, pad = mix(gear, 0x000000, 0.2f);
        boolean crouch = s.crouch;
        float walk = s.onGround && !crouch ? s.anim * 3.4f : 0;
        float stepBob = s.onGround && !crouch && Math.abs(s.vx) > 20 ? Math.abs(MathUtils.sin(walk)) * 1.2f : 0;
        float hipY = y + (crouch ? 12.5f : 19.5f) + bob + stepBob;
        float chestTop = hipY + (crouch ? 11.5f : 13.5f) + breathe;
        float headR = HEAD_R;
        float headX = x + f * 1.6f, headY = chestTop + headR + 0.8f;
        float shoulderX = x + f * 0.5f, shoulderY = chestTop - 2.2f;
        float aim = celebrate ? MathUtils.PI / 2 - f * 0.7f : s.aim;
        if (s.zombie && s.weapon == null && !celebrate && dance == 0) aim = f > 0 ? 0.05f : MathUtils.PI - 0.05f;
        if (dance == 1) aim = MathUtils.PI / 2 + MathUtils.sin(dt8) * 0.9f;
        else if (dance == 2) aim = -MathUtils.PI / 2 + f * (0.4f + 0.7f * MathUtils.sin(s.time * 9));
        else if (dance == 3) aim = MathUtils.sin(s.time * 7) > 0 ? MathUtils.PI / 2 + f * 0.6f : -MathUtils.PI / 2 + f * 0.9f;
        else if (dance == 4) aim = (((int) (s.time * 4)) % 2 == 0) ? 0 : MathUtils.PI / 2;
        float aimDeg = aim * MathUtils.radiansToDegrees;
        float ca = MathUtils.cos(aim), sa = MathUtils.sin(aim);
        float sway = MathUtils.sin(s.time * 6 + x * 0.01f) * 6 - MathUtils.clamp(s.vx * 0.05f, -20, 20) * f;
        float k = headR / 8.5f;
        boolean dancing = dance > 0;
        WeaponType weapon = dancing || celebrate && s.weapon != null && s.weapon.isShield() ? null : s.weapon;
        float melee = s.meleeAnim > 0 ? MathUtils.sin(s.meleeAnim / 0.25f * MathUtils.PI) : 0;

        // sombra no chao
        if (s.onGround && bob < 1) {
            add(T_ELLIPSE, x, y + 0.5f, 12, 2.6f, 0, 0, 0, 0x000000, false);
            alphaLast(0.25f);
        }
        if (look.accessory == 2) { // capa
            float capeLen = crouch ? 15 : 22;
            rect(x - f * 3 - 6, chestTop - capeLen, 12, capeLen, 6, capeLen, f * (12 + sway * 0.8f), look.accessoryColor);
        }
        int hc = look.hairColor;
        if (look.hair == 3) rect(headX - f * 4 - 4, headY - 11, 8, 12, 4, 12, f * 6, hc);
        if (look.hair == 4) ball(headX - f, headY + 2, headR + 3.5f, hc);
        if (look.hair == 5) add(T_ELLIPSE, headX - f * 8, headY + 1, 3.5f, 2.5f, 0, 0, f * (20 + sway), hc, true);

        // mochila/jetpack
        jetpack(look, x - f * 9, hipY + 1, f);

        // ---- pernas (joelho para a frente, passada alternada)
        float hipBX = x - f * 2.2f, hipAX = x + f * 2.2f;
        float legReach = crouch ? 13f : 19.5f;
        float fAx, fAy, fBx, fBy;
        if (!s.onGround) { // no ar: pernas dobradas
            fAx = hipAX + f * 4;
            fAy = hipY - 15;
            fBx = hipBX - f * 3;
            fBy = hipY - 13;
        } else if (crouch) {
            fAx = hipAX + f * 7;
            fAy = y;
            fBx = hipBX - f * 5;
            fBy = y;
        } else {
            float sw = MathUtils.sin(walk), cw = MathUtils.cos(walk);
            boolean moving = Math.abs(s.vx) > 20;
            float stride = moving ? 7.5f : 0;
            fAx = hipAX + sw * stride;
            fAy = y + (moving ? Math.max(0, cw) * 4.5f : 0);
            fBx = hipBX - sw * stride;
            fBy = y + (moving ? Math.max(0, -cw) * 4.5f : 0);
            if (!moving) {
                fAx = hipAX + f * 2.5f;
                fBx = hipBX - f * 2f;
            }
        }
        if (celebrate || dancing) {
            float open = dance == 3 ? Math.max(0, MathUtils.sin(s.time * 7)) * 9 : (dance == 1 ? MathUtils.sin(dt8) * 4 : 3);
            fAx = hipAX + f * open;
            fBx = hipBX - f * open;
            fAy = fBy = y + bob * 0.2f;
            if (dance == 2) fAx += MathUtils.sin(s.time * 9) * 5;
            if (dance == 4) fAy += (((int) (s.time * 4)) % 2 == 0) ? 6 : 0;
        }
        leg(hipBX, hipY, fBx, fBy, f, darker(pants, 0.78f), darker(pad, 0.8f), darker(boot, 0.8f), 6.2f);
        leg(hipAX, hipY, fAx, fAy, f, pants, pad, boot, 6.6f);

        if (s.carryingFlag) {
            float poleX = x - f * 12;
            rect(poleX - 1, hipY, 2.5f, 34, 0, 0, 0, 0xdddddd);
            float wave = MathUtils.sin(s.time * 9) * 2.5f;
            rect(poleX - (f > 0 ? 20 : 0), hipY + 22 + wave * 0.3f, 20, 12, 0, 0, wave, s.flagColor);
        }
        if (s.hasBomb) {
            box(x - f * 11 - 5, hipY + 4, 10, 8, 0x8b0000);
            dCircle(x - f * 11, hipY + 10, 1.8f, ((int) (s.time * 3) % 2 == 0) ? 0xff3333 : 0x550000);
        }

        // ---- posicao da arma e das maos
        boolean dual = weapon != null && weapon.dual;
        boolean shieldW = weapon != null && weapon.isShield();
        boolean twoHanded = weapon != null && !dual && !shieldW;
        float gunL = weapon != null ? weapon.gunLen : 0, gunH = weapon != null ? weapon.gunH : 0;
        boolean pistol = weapon == WeaponType.PISTOL || weapon == WeaponType.MAGNUM || dual;
        float reach = pistol ? 9.5f + melee * 6 : (weapon == WeaponType.ROCKET ? -6 : 1.5f + melee * 6);
        float buttX = shoulderX + ca * reach, buttY = shoulderY + sa * reach - 0.8f;
        float[] gp = weapon != null ? grips(weapon) : G_NONE;
        float perpX = sa * (f > 0 ? 1 : -1), perpY = -ca * (f > 0 ? 1 : -1); // "para baixo" da arma
        float gripX = buttX + ca * gunL * gp[0] + perpX * gunH * 0.45f, gripY = buttY + sa * gunL * gp[0] + perpY * gunH * 0.45f;
        float foreX = buttX + ca * gunL * gp[1] + perpX * gunH * 0.35f, foreY = buttY + sa * gunL * gp[1] + perpY * gunH * 0.35f;
        float bend = -f;

        // ---- braco de tras
        float bsx = shoulderX - f * 2.4f, bsy = shoulderY + 0.4f;
        if (twoHanded && !s.zombie) {
            arm(bsx, bsy, foreX, foreY, bend, bodyDark, darker(glove, 0.85f), 4.6f);
        } else {
            float backDeg = celebrate ? 90 + f * 25 + MathUtils.sin(s.time * 8) * 12 : aimDeg + 8 * f;
            if (dual) backDeg = aimDeg - 5 * f;
            if (dance == 1) backDeg = 90 - MathUtils.sin(dt8) * 50;
            else if (dance == 2) backDeg = -90 - f * (25 + 40 * MathUtils.sin(s.time * 9));
            else if (dance == 3) backDeg = aimDeg + 180 - f * 2 * (aimDeg - 90);
            else if (dance == 4) backDeg = (((int) (s.time * 4 + 1)) % 2 == 0) ? 180 : 90;
            else if (weapon == null && !celebrate && !s.zombie) backDeg = -90 + f * 18 + MathUtils.sin(walk) * 20;
            float hx = bsx + MathUtils.cosDeg(backDeg) * 13.5f, hy = bsy + MathUtils.sinDeg(backDeg) * 13.5f;
            if (dual) {
                gun(weapon, look.gunSkin, hx - MathUtils.cosDeg(backDeg) * 1.5f, hy + 1.5f, backDeg, f);
            }
            arm(bsx, bsy, hx, hy, bend, bodyDark, darker(glove, 0.85f), 4.6f);
        }

        // ---- tronco: quadril, peito largo, colete com placas e bolsos
        box(x - 6, hipY - 2.5f, 12, 7, darker(pants, 0.9f));
        add(T_BOX, x - 7.8f, hipY + 2.5f, 15.6f, chestTop - hipY - 2, 0, 0, 0, body, true);
        pattern(look, x, hipY + 1, chestTop - hipY - 2);
        if (look.accessory != 3) {
            add(T_BOX, x - 6.6f + f * 0.6f, hipY + 4.2f, 13.2f, chestTop - hipY - 6.5f, 0, 0, 0, gear, true);
            dRect(x - 5.6f + f * 0.6f, chestTop - 5.8f, 11.2f, 1.6f, 0, 0, 0, gearL);
            for (int i = 0; i < 3; i++) {
                float px = x - 5.4f + f * 0.6f + i * 3.8f;
                dRect(px, hipY + 4.6f, 3.2f, 4.2f, 0, 0, 0, darker(gear, 0.72f));
                dRect(px, hipY + 7.9f, 3.2f, 0.9f, 0, 0, 0, gearL);
            }
        } else {
            add(T_BOX, x - 6.6f, hipY + 4.2f, 13.2f, chestTop - hipY - 6.5f, 0, 0, 0, look.accessoryColor, true);
        }
        dRect(x - 6.5f, hipY + 1.8f, 13, 2.4f, 0, 0, 0, 0x2b2620); // cinto
        dRect(x - 1.3f, hipY + 1.9f, 2.6f, 2.2f, 0, 0, 0, 0x9e8a4a);
        // ombreira
        ball(x + f * 0.5f, shoulderY + 1, 3.6f, darker(body, 0.85f));
        if (look.accessory == 4) {
            dRect(x + f * 3 - 1.2f, chestTop - 7, 2.4f, 3, 0, 0, 0, look.accessoryColor);
            dCircle(x + f * 3, chestTop - 8.2f, 1.7f, 0xffc107);
        }
        if (look.accessory == 1) {
            rect(x - 5.5f, chestTop - 2.8f, 11, 3.6f, 0, 0, 0, look.accessoryColor);
            rect(x - f * 5, chestTop - 1.6f, 8, 3, 0, 1.5f, 180 + (f > 0 ? 0 : 180) - f * (10 + sway), look.accessoryColor);
        }

        // ---- pescoco e cabeca
        rect(headX - f * 0.8f - 2, chestTop - 1.5f, 4, 3.2f, 0, 0, 0, darker(skin, 0.85f));
        ball(headX, headY, headR, skin);
        int from = n;
        face(look, headX, headY, 8.5f, f, dancing ? (f > 0 ? 0 : MathUtils.PI) : aim, s.time);
        if (look.costume == 4) dCircle(headX + f * 7.6f, headY - 1.5f, 2.2f, 0xff1744);
        if (s.zombie) {
            dCircle(headX + f * 3.5f, headY + 1, 2.6f, 0x33691e);
            alphaLast(0.5f);
            dRect(headX - f * 2 - 3, headY + 4, 6, 1.4f, 0, 0, f * 25, 0x7b1f1f);
        }
        drawHat(look, headX, headY, 8.5f, f, s.time);
        scaleFrom(from, headX, headY, k);
        add(T_GLOSS, headX - f * 1.5f - 3, headY + headR * 0.4f - 1.8f, 5, 2.6f, 0, 0, 0, 0xffffff, false);
        alphaLast(0.2f);

        // ---- arma e braco da frente
        if (weapon != null && !shieldW) gun(weapon, look.gunSkin, buttX, buttY, aimDeg, f);
        float fsx = shoulderX + f * 1.2f, fsy = shoulderY;
        if (weapon != null && !shieldW) {
            arm(fsx, fsy, gripX, gripY, bend, body, glove, 5f);
        } else {
            float len = s.zombie ? 15 : 13 + melee * 5;
            float deg = aimDeg;
            if (weapon == null && !celebrate && !dancing && !s.zombie && melee <= 0) deg = -90 - f * 15 - MathUtils.sin(walk) * 20;
            arm(fsx, fsy, fsx + MathUtils.cosDeg(deg) * len, fsy + MathUtils.sinDeg(deg) * len, bend, body, glove, 5f);
        }
        if (shieldW) {
            float tilt = MathUtils.clamp(f > 0 ? aimDeg : 180 - aimDeg, -35, 35) * 0.35f * f;
            float sx0 = x + f * (12 + melee * 8), sy0 = hipY - 12;
            rrect(sx0 - 4, sy0, 8, 38, 4, 0, tilt, 0x37474f);
            dRect(sx0 - 2.5f, sy0 + 24, 5, 9, -1.5f + 4, -24, tilt, 0x80deea);
            alphaLast(0.75f);
            dRect(sx0 - 2.5f, sy0 + 5, 5, 2, -1.5f + 4, -5, tilt, 0xffeb3b);
        }

        flush(d, s.alpha, s.hurtFlash > 0);

        if (s.shield) {
            d.additive(true);
            d.color(0.4f, 0.8f, 1f, 0.35f + 0.15f * MathUtils.sin(s.time * 10));
            d.ring(x, y + 23, 32);
            d.color(0.4f, 0.8f, 1f, 0.12f);
            d.soft(x, y + 23, 34);
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
        float ex = hx + f * 4.4f, ey = hy + 0.6f;
        float ex2 = hx + f * 0.4f;
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
                    // olhos pequenos e escuros, com sobrancelha (cara de adulto)
                    float ey2 = ey + 0.4f;
                    if (blink) {
                        dRect(ex - 1.6f, ey2 - 0.4f, 3.2f, 0.9f, 0, 0, 0, ink);
                        dRect(ex2 - 1.2f, ey2 - 0.4f, 2.4f, 0.9f, 0, 0, 0, ink);
                    } else {
                        float lx2 = lx * 0.35f, ly2 = ly * 0.35f;
                        dEllipse(ex + lx2, ey2 + ly2, 1.15f, 1.55f, 0, ink);
                        dEllipse(ex2 + lx2 * 0.8f, ey2 + ly2 * 0.8f, 0.95f, 1.4f, 0, ink);
                        dCircle(ex + lx2 - 0.35f, ey2 + ly2 + 0.55f, 0.32f, 0xffffff);
                    }
                    if (look.eyes == 3) { // sonolento: palpebra
                        dRect(ex - 1.7f, ey2 + 0.1f, 3.4f, 1.6f, 0, 0, 0, darker(look.skin, 0.8f));
                        dRect(ex2 - 1.4f, ey2 + 0.1f, 2.8f, 1.5f, 0, 0, 0, darker(look.skin, 0.8f));
                    }
                    // sobrancelhas (mais grossas e inclinadas no "bravo" e no "focado")
                    float ang = look.eyes == 1 || look.eyes == 5 ? 18 : 6;
                    int brow = darker(look.hairColor, 0.8f);
                    dRect(ex - 1.9f, ey2 + 2.3f, 3.8f, 1.05f, 1.9f, 0.5f, -f * ang, brow);
                    dRect(ex2 - 1.6f, ey2 + 2.2f, 3.2f, 1.0f, 1.6f, 0.5f, f * ang * 0.6f, brow);
                    // nariz
                    dRect(hx + f * 5.6f - 0.6f, ey2 - 2.6f, 1.4f, 2.2f, 0.7f, 1.1f, -f * 10, darker(look.skin, 0.82f));
                    break;
                }
            }
        }
        // boca
        float mx = hx + f * 3.8f, my = hy - 4.2f;
        if (look.eyewear != 4 && look.eyewear != 5) {
            int lip = darker(look.skin, 0.55f);
            switch (look.mouth) {
                case 1: // meio sorriso
                    dRect(mx - 1.6f, my - 0.4f, 3.2f, 0.9f, 0, 0, 0, lip);
                    dRect(mx + f * 1.2f - 0.5f, my - 0.1f, 1.2f, 0.9f, 0.6f, 0.45f, f * 35, lip);
                    break;
                case 2: // dentes cerrados
                    dRect(mx - 1.8f, my - 0.8f, 3.6f, 1.6f, 0, 0, 0, lip);
                    dRect(mx - 1.4f, my - 0.4f, 2.8f, 0.8f, 0, 0, 0, 0xeeeeee);
                    break;
                case 3: // grito
                    dEllipse(mx, my - 0.3f, 1.2f, 1.4f, 0, 0x3a1010);
                    break;
                case 4: // assobio
                    add(T_RING, mx + f * 0.5f, my, 0.9f, 0.9f, 0, 0, 0, lip, false);
                    break;
                case 5: // sorriso de lado
                    dRect(mx - 1.6f, my - 0.5f, 3.2f, 0.9f, 1.6f, 0.45f, -f * 12, lip);
                    break;
                default:
                    dRect(mx - 1.5f, my - 0.4f, 3f, 0.85f, 0, 0, 0, lip);
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
                dEllipse(ex, ey, 2.6f, 2.4f, 0, 0x111111);
                dEllipse(ex2, ey + 0.4f, 0.95f, 1.4f, 0, 0x111111);
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

    // ---- desenho das armas em coordenadas locais (lx ao longo do cano, ly para cima da arma)
    private float gx, gy, gc, gs, gdeg;
    private int gsy;

    private float wx(float lx, float ly) {
        return gx + gc * lx - gs * ly * gsy;
    }

    private float wy(float lx, float ly) {
        return gy + gs * lx + gc * ly * gsy;
    }

    /** peca arredondada que sai do ponto local (lx, ly) com comprimento len e grossura th, girada angOff graus */
    private void part(float lx, float ly, float len, float th, float angOff, int col) {
        float px0 = wx(lx, ly), py0 = wy(lx, ly);
        add(T_BOX, px0, py0 - th / 2, len, th, 0, th / 2, gdeg + angOff * gsy, col, true);
    }

    private void tube(float lx, float ly, float len, float th, int col) {
        float px0 = wx(lx, ly), py0 = wy(lx, ly);
        add(T_PILL, px0 - th / 2, py0 - th / 2, len + th, th, th / 2, th / 2, gdeg, col, true);
    }

    private void detail(float lx, float ly, float len, float th, float angOff, int col) {
        float px0 = wx(lx, ly), py0 = wy(lx, ly);
        add(T_RECT, px0, py0 - th / 2, len, th, 0, th / 2, gdeg + angOff * gsy, col, false);
    }

    private void dot(float lx, float ly, float r, int col, boolean outline) {
        add(outline ? T_BALL : T_CIRCLE, wx(lx, ly), wy(lx, ly), r, r, 0, 0, 0, col, outline);
    }

    /** Desenha a arma com a coronha em (x, y) apontando no angulo dado. */
    private void gun(WeaponType w, int skin, float x, float y, float deg, int f) {
        gx = x;
        gy = y;
        gdeg = deg;
        gc = MathUtils.cosDeg(deg);
        gs = MathUtils.sinDeg(deg);
        gsy = f > 0 ? 1 : -1;
        float L = w.gunLen, H = w.gunH;
        int col = gunColor(w.color, skin);
        int dark = darker(col, 0.62f);
        int metal = skin == 0 ? 0x3d4448 : darker(col, 0.7f);
        int metalL = skin == 0 ? 0x5f6b72 : col;
        int wood = skin == 0 ? 0x8d5a2b : darker(col, 0.85f);
        int woodD = darker(wood, 0.75f);
        switch (w) {
            case PISTOL:
            case DUAL_PISTOLS:
            case MAGNUM: {
                boolean mag = w == WeaponType.MAGNUM;
                part(1, -1, H * 1.25f, H * 0.62f, -105, mag ? wood : dark); // cabo
                part(0, H * 0.18f, L * 0.82f, H * 0.62f, 0, col); // ferrolho
                if (mag) {
                    dot(L * 0.32f, -H * 0.05f, H * 0.38f, metal, true); // tambor
                    tube(L * 0.6f, H * 0.12f, L * 0.45f, H * 0.32f, metalL);
                } else tube(L * 0.7f, H * 0.05f, L * 0.22f, H * 0.3f, metal);
                detail(L * 0.08f, -H * 0.38f, L * 0.28f, 1.1f, 0, dark); // guarda-mato
                detail(L * 0.7f, H * 0.52f, 2, 1.6f, 90, dark); // mira
                break;
            }
            case SMG:
            case DUAL_SMG: {
                part(-4, H * 0.05f, 7, H * 0.4f, -12, dark); // coronha curta
                part(0, 0, L * 0.6f, H * 0.8f, 0, col);
                part(L * 0.18f, -H * 0.2f, H * 0.9f, H * 0.42f, -105, dark); // cabo
                part(L * 0.4f, -H * 0.25f, H * 1.4f, H * 0.42f, -92, metal); // carregador reto
                tube(L * 0.6f, H * 0.1f, L * 0.32f, H * 0.32f, metal);
                detail(L * 0.3f, H * 0.42f, L * 0.18f, 1.3f, 0, dark);
                break;
            }
            case RIFLE: {
                part(-7, -H * 0.05f, 10, H * 0.72f, -6, skin == 0 ? 0x2f3437 : dark); // coronha
                part(0, 0, L * 0.5f, H * 0.82f, 0, col); // corpo
                part(L * 0.12f, -H * 0.25f, H * 0.95f, H * 0.4f, -108, dark); // cabo
                part(L * 0.3f, -H * 0.25f, H * 1.25f, H * 0.42f, -80, metal); // carregador curvo
                part(L * 0.48f, H * 0.02f, L * 0.3f, H * 0.6f, 0, dark); // guarda-mao
                tube(L * 0.76f, H * 0.08f, L * 0.24f, H * 0.26f, metal); // cano
                part(L * 0.18f, H * 0.48f, L * 0.2f, H * 0.28f, 0, metal); // trilho/mira
                detail(L * 0.98f, H * 0.08f, 2.5f, H * 0.45f, 0, metalL); // quebra-chama
                break;
            }
            case SHOTGUN: {
                part(-8, -H * 0.12f, 11, H * 0.78f, -10, wood);
                part(0, 0, L * 0.38f, H * 0.82f, 0, col);
                part(L * 0.1f, -H * 0.25f, H * 0.85f, H * 0.4f, -112, wood);
                tube(L * 0.36f, H * 0.18f, L * 0.62f, H * 0.32f, metal); // cano
                tube(L * 0.36f, -H * 0.18f, L * 0.5f, H * 0.3f, metalL); // tubo
                part(L * 0.5f, -H * 0.2f, L * 0.2f, H * 0.5f, 0, woodD); // bomba
                break;
            }
            case SNIPER: {
                part(-9, -H * 0.1f, 13, H * 0.85f, -7, wood);
                detail(-6, -H * 0.1f, 5, H * 0.4f, -7, woodD);
                part(0, 0, L * 0.42f, H * 0.78f, 0, col);
                part(L * 0.1f, -H * 0.25f, H * 1.0f, H * 0.42f, -110, wood);
                part(L * 0.24f, -H * 0.25f, H * 0.7f, H * 0.4f, -90, metal);
                tube(L * 0.42f, H * 0.05f, L * 0.58f, H * 0.28f, metal);
                tube(L * 0.08f, H * 0.62f, L * 0.3f, H * 0.5f, 0x1c1f22); // luneta
                dot(L * 0.4f, H * 0.62f, H * 0.28f, 0x4fc3f7, false);
                detail(L * 0.22f, H * 0.42f, 2, H * 0.35f, 90, 0x1c1f22);
                detail(L * 0.7f, -H * 0.12f, H * 1.0f, 1.2f, -120, metal); // bipe
                detail(L * 0.7f, -H * 0.12f, H * 1.0f, 1.2f, -60, metal);
                break;
            }
            case LMG: {
                part(-7, -H * 0.05f, 10, H * 0.7f, -6, dark);
                part(0, 0, L * 0.45f, H * 0.9f, 0, col);
                part(L * 0.1f, -H * 0.3f, H * 0.85f, H * 0.4f, -110, dark);
                part(L * 0.22f, -H * 0.38f, H * 0.95f, H * 1.0f, -90, metalL); // caixa de municao
                part(L * 0.45f, H * 0.05f, L * 0.35f, H * 0.7f, 0, metal); // camisa do cano
                for (int i = 0; i < 3; i++) detail(L * (0.5f + i * 0.09f), H * 0.05f, 2, H * 0.28f, 0, 0x111111);
                tube(L * 0.8f, H * 0.05f, L * 0.2f, H * 0.3f, metal);
                part(L * 0.1f, H * 0.5f, L * 0.3f, H * 0.32f, 0, dark); // alca
                detail(L * 0.75f, -H * 0.3f, H * 1.1f, 1.3f, -125, metal);
                detail(L * 0.75f, -H * 0.3f, H * 1.1f, 1.3f, -55, metal);
                break;
            }
            case ROCKET: {
                tube(-6, 0, L, H * 0.85f, col); // tubo
                part(L * 0.22f, -H * 0.4f, H * 0.9f, H * 0.38f, -100, dark);
                part(L * 0.45f, -H * 0.4f, H * 0.6f, H * 0.36f, -95, dark);
                part(L * 0.25f, H * 0.42f, L * 0.18f, H * 0.36f, 0, metal); // mira
                detail(-4, 0, 3, H * 0.95f, 0, dark);
                detail(L - 8, 0, 3, H * 0.95f, 0, dark);
                add(T_TRI, wx(L - 2, 0) - H * 0.4f, wy(L - 2, 0) - H * 0.4f, H * 0.8f, H * 0.9f, H * 0.4f, H * 0.4f, deg - 90, 0x9e2b25, true);
                break;
            }
            case GRENADE_LAUNCHER: {
                part(-6, -H * 0.1f, 9, H * 0.6f, -8, dark);
                part(0, 0, L * 0.35f, H * 0.7f, 0, col);
                dot(L * 0.38f, -H * 0.05f, H * 0.55f, metal, true); // tambor
                part(L * 0.1f, -H * 0.25f, H * 0.8f, H * 0.4f, -108, dark);
                tube(L * 0.5f, H * 0.05f, L * 0.42f, H * 0.62f, metalL);
                detail(L * 0.88f, H * 0.05f, 2.5f, H * 0.7f, 0, dark);
                break;
            }
            case FLAMETHROWER: {
                dot(L * 0.18f, -H * 0.55f, H * 0.62f, skin == 0 ? 0xd35400 : col, true); // tanque
                part(0, 0, L * 0.45f, H * 0.62f, 0, metal);
                part(L * 0.12f, -H * 0.2f, H * 0.8f, H * 0.4f, -108, dark);
                tube(L * 0.45f, H * 0.02f, L * 0.45f, H * 0.36f, metalL);
                part(L * 0.88f, H * 0.02f, L * 0.1f, H * 0.55f, 0, dark); // bocal
                dot(L * 0.96f, -H * 0.32f, 1.6f, 0x29b6f6, false); // chama piloto
                break;
            }
            case LASER: {
                tube(-2, 0, L * 0.85f, H * 0.78f, col);
                part(L * 0.12f, -H * 0.25f, H * 0.85f, H * 0.4f, -108, dark);
                for (int i = 0; i < 3; i++) dot(L * (0.28f + i * 0.13f), 0, H * 0.18f, 0x7fffd4, false);
                part(L * 0.1f, H * 0.62f, L * 0.32f, H * 0.36f, 0, 0x0e6655);
                tube(L * 0.84f, 0, L * 0.12f, H * 0.42f, metal);
                dot(L * 1.0f, 0, H * 0.22f, 0x7fffd4, false);
                break;
            }
            case SAW: {
                part(0, 0, L * 0.55f, H * 0.75f, 0, col);
                part(L * 0.12f, -H * 0.25f, H * 0.85f, H * 0.4f, -108, dark);
                tube(L * 0.5f, 0, L * 0.25f, H * 0.4f, metal);
                add(T_STAR, wx(L * 0.95f, 0), wy(L * 0.95f, 0), H * 1.5f, H * 1.5f, 0, 0, 0, 0xcfd8dc, true);
                dot(L * 0.95f, 0, H * 0.18f, metal, false);
                break;
            }
            case SHIELD:
                break;
            case BOW: {
                int bc = skin == 0 ? 0x8d5524 : col;
                part(8, 0, 18, 3.5f, 90 - 22, bc);
                part(8, 0, 18, 3.5f, -90 + 22, bc);
                float tx1 = wx(8 + MathUtils.cosDeg(68) * 18, MathUtils.sinDeg(68) * 18), ty1 = wy(8 + MathUtils.cosDeg(68) * 18, MathUtils.sinDeg(68) * 18);
                float tx2 = wx(8 + MathUtils.cosDeg(68) * 18, -MathUtils.sinDeg(68) * 18), ty2 = wy(8 + MathUtils.cosDeg(68) * 18, -MathUtils.sinDeg(68) * 18);
                float ang = MathUtils.atan2(ty2 - ty1, tx2 - tx1) * MathUtils.radiansToDegrees;
                float l2 = (float) Math.sqrt((tx2 - tx1) * (tx2 - tx1) + (ty2 - ty1) * (ty2 - ty1));
                dRect(tx1, ty1 - 0.5f, l2, 1f, 0, 0.5f, ang, 0xeeeeee);
                tube(0, 0, 22, 2, 0xa1887f); // flecha
                add(T_TRI, wx(23, 0) - 3, wy(23, 0) - 3, 6, 6, 3, 3, deg - 90, 0x9e9e9e, true);
                detail(0, 1.5f, 4, 2, 30, 0xef5350);
                detail(0, -1.5f, 4, 2, -30, 0xef5350);
                break;
            }
            case BOOMERANG: {
                int bc = skin == 0 ? 0xffb300 : col;
                part(4, 0, 14, 5, 35, bc);
                part(4, 0, 14, 5, -35, darker(bc, 0.85f));
                break;
            }
            case HARPOON: {
                part(-5, -H * 0.05f, 8, H * 0.6f, -8, wood);
                part(0, 0, L * 0.5f, H * 0.8f, 0, col);
                part(L * 0.12f, -H * 0.25f, H * 0.85f, H * 0.4f, -108, dark);
                tube(L * 0.5f, 0.5f, L * 0.42f, H * 0.4f, metal);
                tube(L * 0.6f, H * 0.45f, L * 0.45f, 1.6f, 0xcfd8dc); // arpao
                add(T_TRI, wx(L * 1.08f, H * 0.45f) - 3.5f, wy(L * 1.08f, H * 0.45f) - 3.5f, 7, 7, 3.5f, 3.5f, deg - 90, 0x90a4ae, true);
                break;
            }
            default: { // FROST, TESLA, GRAVITY
                int accent = w == WeaponType.FROST ? 0x4fc3f7 : (w == WeaponType.TESLA ? 0xffee58 : 0xb388ff);
                part(-4, -H * 0.05f, 7, H * 0.55f, -8, dark);
                part(0, 0, L * 0.55f, H * 0.85f, 0, col);
                part(L * 0.14f, -H * 0.28f, H * 0.85f, H * 0.4f, -108, dark);
                if (w == WeaponType.FROST) {
                    dot(L * 0.3f, H * 0.55f, H * 0.45f, accent, true);
                    tube(L * 0.55f, 0, L * 0.35f, H * 0.42f, metal);
                    dot(L * 0.95f, 0, H * 0.28f, 0xe1f5fe, false);
                } else if (w == WeaponType.TESLA) {
                    tube(L * 0.55f, 0, L * 0.3f, H * 0.3f, metal);
                    for (int k = 0; k < 3; k++) add(T_RING, wx(L * (0.6f + k * 0.11f), 0), wy(L * (0.6f + k * 0.11f), 0), H * 0.5f, H * 0.5f, 0, 0, 0, accent, false);
                    dot(L * 0.95f, 0, H * 0.3f, 0xfff9c4, false);
                } else {
                    tube(L * 0.55f, 0, L * 0.25f, H * 0.5f, metal);
                    dot(L * 0.95f, 0, H * 0.55f, 0x311b92, true);
                    dot(L * 0.95f, 0, H * 0.3f, accent, false);
                }
                break;
            }
        }
        if (skin == 1 || skin == 3 || skin == 4) { // brilho metalico
            detail(L * 0.05f, H * 0.28f, L * 0.4f, H * 0.12f, 0, 0xffffff);
            alphaLast(0.4f);
        }
    }

    private void sbox(float x, float y, float w, float h, float ox, float oy, float rot, int col) {
        add(T_BOX, x, y, w, h, ox, oy, rot, col, true);
    }

    private void flush(Draw d, float alpha, boolean flash) {
        ShadeKit.load();
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
                    case T_BALL:
                        d.batch.draw(ShadeKit.ball, px[i] - pw[i] - e, py[i] - pw[i] - e, (pw[i] + e) * 2, (pw[i] + e) * 2);
                        break;
                    case T_PILL:
                        d.batch.draw(ShadeKit.pill, px[i] - e, py[i] - e, pox[i] + e, poy[i] + e, pw[i] + e * 2, ph[i] + e * 2, 1, 1, prot[i]);
                        break;
                    case T_BOX:
                        d.batch.draw(ShadeKit.box, px[i] - e, py[i] - e, pox[i] + e, poy[i] + e, pw[i] + e * 2, ph[i] + e * 2, 1, 1, prot[i]);
                        break;
                    case T_GLOSS:
                        d.batch.draw(ShadeKit.gloss, px[i], py[i], pw[i], ph[i]);
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
        drawGun(d, w, skin, cx, cy, deg, scale, alpha, 1);
    }

    /** f = -1 desenha a arma virada (apontando para a esquerda, sem ficar de cabeca para baixo). */
    public void drawGun(Draw d, WeaponType w, int skin, float cx, float cy, float deg, float scale, float alpha, int f) {
        n = 0;
        float len = w.gunLen;
        if (w.isShield()) {
            rrect(cx - 8, cy - 16, 16, 32, 8, 16, deg + 8, 0x37474f);
            dRect(cx - 5, cy + 3, 10, 9, 5, -3, deg + 8, 0x80deea);
            alphaLast(0.75f);
            dRect(cx - 5, cy - 10, 10, 2.5f, 5, 10, deg + 8, 0xffeb3b);
        } else {
            float gd = f < 0 ? 180 - deg : deg;
            if (w.dual) gun(w, skin, cx - len / 2 * MathUtils.cosDeg(gd) - 4 * f, cy - len / 2 * MathUtils.sinDeg(gd) + 6, gd, f);
            gun(w, skin, cx - len / 2 * MathUtils.cosDeg(gd), cy - len / 2 * MathUtils.sinDeg(gd), gd, f);
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
