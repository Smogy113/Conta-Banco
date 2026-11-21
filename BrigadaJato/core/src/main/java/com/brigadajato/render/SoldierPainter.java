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
    /** contorno interno no modo pixel (~1 pixel) */
    private static final float PIX_LINE = 0.95f;

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

    public static final float HEAD_R = 8.2f;
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
        seg(ex, ey, tx, ty, th * 0.92f, sleeve);
        ball(tx, ty, th * 0.5f, glove);
    }

    /** perna do quadril ate o pe, com joelho para a frente */
    private void leg(float hx, float hy, float fx, float fy, int f, int pants, int pad, int boot, float th) {
        // comprimento igual a altura do quadril: em pe a perna fica reta; so dobra andando/agachado/no ar
        ik(hx, hy, fx, fy + 1.2f, 8.3f, 8.1f, f);
        float kx = jx, ky = jy;
        float dx = fx - hx, dy = fy + 1.2f - hy;
        if (dx * dx + dy * dy > 15.6f * 15.6f) { // quase esticada: uma coluna so, sem calombo
            seg(hx, hy, fx, fy + 1.2f, th, pants);
        } else {
            seg(hx, hy, kx, ky, th, pants);
            seg(kx, ky, fx, fy + 1.2f, th * 0.96f, pants);
            add(T_BALL, kx, ky, th * 0.5f, th * 0.5f, 0, 0, 0, pants, false); // junta lisa no joelho
        }
        // tenis: cano escuro, sola clara e detalhe colorido
        float bx = fx - 3.8f + f * 1.4f;
        box(bx, fy - 1.8f, 9.6f, 4.8f, boot);
        add(T_RECT, bx + 0.2f, fy - 1.8f, 9.2f, 1.4f, 0, 0, 0, pad, false);
        add(T_RECT, bx + (f > 0 ? 5.0f : 1.4f), fy + 0.6f, 3.2f, 1.3f, 0, 0, 0, mix(boot, 0xffffff, 0.4f), false);
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

    /** bonecos em pixel art (pintados numa grade pequena e ampliados) */
    public static boolean pixelArt = true;
    /** pintando dentro da grade de pixels agora */
    private boolean pix;

    public void draw(Draw d, Pose s) {
        if (pixelArt && PixelSoldiers.ready()) {
            PixelSoldiers.drawNow(d, this, s);
            return;
        }
        drawShadow(d, s);
        body(d, s);
        drawFx(d, s);
    }

    /** So o corpo (usado pela grade de pixels). */
    public void drawBody(Draw d, Pose s) {
        pix = true;
        body(d, s);
        pix = false;
    }

    /** sombra no chao */
    public void drawShadow(Draw d, Pose s) {
        if (!s.onGround || s.action == 4) return;
        d.color(0, 0, 0, 0.25f * s.alpha);
        d.ellipse(s.x, s.y + 0.5f, 12, 2.6f, 0);
    }

    /** bolha do escudo de nascimento */
    public void drawFx(Draw d, Pose s) {
        if (!s.shield) return;
        d.additive(true);
        d.color(0.4f, 0.8f, 1f, 0.35f + 0.15f * MathUtils.sin(s.time * 10));
        d.ring(s.x, s.y + 23, 32);
        d.color(0.4f, 0.8f, 1f, 0.12f);
        d.soft(s.x, s.y + 23, 34);
        d.additive(false);
    }

    private void body(Draw d, Pose s) {
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
        int glove = skin, boot = mix(0x26262c, look.accessoryColor, 0.3f), pad = 0xe3ddd0; // maos, tenis e sola
        gx0 = PixelSoldiers.snap(s.x);
        gy0 = PixelSoldiers.snap(s.y);
        boolean crouch = s.crouch;
        float walk = s.onGround && !crouch ? s.anim * 3.4f : 0;
        float stepBob = s.onGround && !crouch && Math.abs(s.vx) > 20 ? Math.abs(MathUtils.sin(walk)) * 1.2f : 0;
        float hipY = y + (crouch ? 11.5f : 17.5f) + bob + stepBob;
        float chestTop = hipY + (crouch ? 10.5f : 12f) + breathe;
        float headR = HEAD_R;
        float headX = gsx(x + f * 1.3f), headY = gsy(chestTop + headR - 0.2f);
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

        if (look.accessory == 2) { // capa
            float capeLen = crouch ? 15 : 22;
            rect(x - f * 3 - 6, chestTop - capeLen, 12, capeLen, 6, capeLen, f * (12 + sway * 0.8f), look.accessoryColor);
        }
        int hc = look.hairColor;
        hairBack(look, headX, headY, headR, f, sway);

        // mochila/jetpack
        jetpack(look, x - f * 7.6f, hipY + 3.5f, f);

        // ---- pernas (joelho para a frente, passada alternada)
        float hipBX = x - f * 2.7f, hipAX = x + f * 2.7f;
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
            if (!moving) { // em pe: pernas retas com um vaozinho entre elas
                fAx = hipAX + f * 1.1f;
                fBx = hipBX - f * 1.1f;
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
        leg(hipBX, hipY, fBx, fBy, f, darker(pants, 0.78f), darker(pad, 0.82f), darker(boot, 0.8f), 5.8f);
        leg(hipAX, hipY, fAx, fAy, f, pants, pad, boot, 6.2f);

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

        // ---- tronco: moletom com capuz (barra, bolso canguru, cordoes) ou colete
        box(x - 6, hipY - 2.5f, 12, 6, darker(pants, 0.9f));
        float tH = chestTop - hipY;
        add(T_BOX, x - 7.6f, hipY - 0.6f, 15.2f, tH + 0.6f, 0, 0, 0, body, true);
        pattern(look, x, hipY - 1, tH);
        int hem = darker(body, 0.74f), lite = mix(body, 0xffffff, 0.3f);
        dRect(x - 7.0f, hipY + 1.8f, 1.3f, tH - 4f, 0, 0, 0, mix(body, 0xffffff, 0.14f)); // luz do lado esquerdo
        dRect(x - 7.2f, hipY - 0.6f, 14.4f, 2.4f, 0, 0, 0, hem); // barra
        if (look.accessory == 3) { // colete
            add(T_BOX, x - 6.6f + f * 0.6f, hipY + 1.6f, 13.2f, tH - 4f, 0, 0, 0, look.accessoryColor, true);
            for (int i = 0; i < 3; i++) dRect(x - 5.4f + f * 0.6f + i * 3.8f, hipY + 2.2f, 3.2f, 3.6f, 0, 0, 0, darker(look.accessoryColor, 0.72f));
        } else {
            float pk = x - 3.8f + f * 1.4f;
            dRect(pk, hipY + 2.0f, 7.6f, 3.9f, 0, 0, 0, darker(body, 0.86f)); // bolso canguru
            dRect(pk, hipY + 5.3f, 7.6f, 1.1f, 0, 0, 0, hem);
            dRect(x + f * 2.6f - 0.6f, hipY + 6.4f, 1.2f, tH - 7.6f, 0, 0, 0, darker(body, 0.7f)); // costura
            dRect(x + f * 0.4f - 0.6f, chestTop - 6.6f, 1.2f, 4.4f, 0, 0, 0, lite); // cordoes
            dRect(x + f * 4.2f - 0.6f, chestTop - 5.8f, 1.2f, 3.6f, 0, 0, 0, lite);
            if (look.jetpack != 3) dRect(x - f * 3.4f - 0.7f, hipY + 3.4f, 1.4f, tH - 3.4f, 0, 0, 0, darker(look.jetColor, 0.62f)); // alca da mochila
        }
        // ombro arredondado
        ball(x + f * 0.5f, shoulderY + 0.6f, 3.7f, darker(body, 0.9f));
        if (look.accessory == 4) {
            dRect(x + f * 3 - 1.2f, chestTop - 7, 2.4f, 3, 0, 0, 0, look.accessoryColor);
            dCircle(x + f * 3, chestTop - 8.2f, 1.7f, 0xffc107);
        }
        if (look.accessory == 1) {
            rect(x - 5.5f, chestTop - 2.8f, 11, 3.6f, 0, 0, 0, look.accessoryColor);
            rect(x - f * 5, chestTop - 1.6f, 8, 3, 0, 1.5f, 180 + (f > 0 ? 0 : 180) - f * (10 + sway), look.accessoryColor);
        }

        // ---- capuz, pescoco e cabeca
        if (look.accessory != 3) ball(x - f * 2.2f, chestTop + 0.8f, 4.6f, darker(body, 0.8f));
        rect(headX - f * 0.8f - 2, chestTop - 1.5f, 4, 3.2f, 0, 0, 0, darker(skin, 0.85f));
        ball(headX, headY, headR, skin);
        face(look, headX, headY, headR, f, dancing ? (f > 0 ? 0 : MathUtils.PI) : aim, s.time);
        if (look.costume == 4) dCircle(headX + f * (headR - 0.4f), headY - 2f, 2.2f, 0xff1744);
        if (s.zombie) {
            px(headX, headY, f, 1, 0, 2, 2, 0x33691e);
            px(headX, headY, f, -3, 2, 3, 1, 0x7b1f1f);
        }
        int from = n;
        drawHat(look, headX, headY, 8.5f, f, s.time);
        scaleFrom(from, headX, headY, k);
        goggles(look, headX, headY, f);
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
            default: // mochila-jato: aba, fivela e bocal embaixo
                rect(jx - 2.6f, jy - 2.6f, 5.2f, 3.2f, 0, 0, 0, 0x3a3a40);
                box(jx - 3.6f, jy + 0.5f, 7.6f, 11.5f, c);
                dRect(jx - 3.2f, jy + 7.6f, 6.4f, 3.9f, 0, 0, 0, darker(c, 0.8f));
                dRect(jx - 0.7f, jy + 6.6f, 1.4f, 2.2f, 0, 0, 0, mix(c, 0xffffff, 0.45f));
                dRect(jx - 3.2f, jy + 1.5f, 1.3f, 5f, 0, 0, 0, mix(c, 0xffffff, 0.18f));
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

    // ---- grade de pixels: o rosto e desenhado em pixels inteiros a partir do centro da cabeca
    private float gx0, gy0;
    private static final float T = PixelSoldiers.TEXEL;

    private float gsx(float v) {
        return gx0 + Math.round((v - gx0) / T) * T;
    }

    private float gsy(float v) {
        return gy0 + Math.round((v - gy0) / T) * T;
    }

    /** retangulo de w x h pixels em (tx, ty) pixels a partir do centro da cabeca (espelhado quando f = -1) */
    private void px(float hx, float hy, int f, int tx, int ty, int w, int h, int col) {
        float x0 = f > 0 ? hx + tx * T : hx - (tx + w) * T;
        add(T_RECT, x0, hy + ty * T, w * T, h * T, 0, 0, 0, col, false);
    }

    /** cabelo de tras (volume atras da cabeca, rabo de cavalo, cabelo comprido) */
    private void hairBack(Look look, float hx, float hy, float r, int f, float sway) {
        int hc = look.hairColor, hd = darker(hc, 0.78f);
        switch (look.hair) {
            case 1:
            case 6:
                ball(hx - f * 1.3f, hy + 1.3f, r + 0.9f, hc);
                break;
            case 2:
                for (int i = 0; i < 3; i++) {
                    float ang = 120 + i * 28;
                    float cx = hx - f * 1.5f + f * MathUtils.cosDeg(ang) * r, cy = hy + 1 + MathUtils.sinDeg(ang) * r;
                    add(T_TRI, cx - 3.5f, cy - 2, 7, 8, 3.5f, 2, f * (ang - 90), hc, true);
                }
                ball(hx - f * 1.5f, hy + 1.4f, r + 1.2f, hc);
                break;
            case 3: // comprido ate o ombro
                add(T_BOX, hx - f * 2.4f - r, hy - r - 7, r * 2 - 1, r + 8, 0, 0, 0, hc, true);
                add(T_RECT, hx - f * 2.4f - r * 0.3f, hy - r - 6, 1.3f, r + 4, 0, 0, 0, hd, false);
                ball(hx - f * 1.2f, hy + 1.2f, r + 1.1f, hc);
                break;
            case 4: // black power
                ball(hx - f * 1.2f, hy + 2.6f, r + 4.2f, hc);
                break;
            case 5: { // rabo de cavalo
                ball(hx - f * 1.3f, hy + 1.3f, r + 0.9f, hc);
                float tx = hx - f * (r + 2.5f), ty = hy + 2.5f;
                add(T_ELLIPSE, tx - f * 2.5f, ty - 3.5f, 3.2f, 6f, 0, 0, f * (-25 + sway), hc, true);
                ball(tx, ty, 2.2f, look.accessoryColor);
                break;
            }
            default:
                break;
        }
    }

    /** franja: tufos pontudos caindo na testa */
    private void tuft(float cx, float topY, float w, float h, float deg, int col) {
        add(T_TRI, cx - w / 2, topY - h, w, h, w / 2, h / 2, 180 + deg, col, true);
    }

    private void face(Look look, float hx, float hy, float r, int f, float aim, float time) {
        int ink = 0x1a1418;
        int skin = look.skin;
        int lip = darker(skin, 0.62f), blush = mix(skin, 0xff6f6f, 0.32f);
        int hc = look.hairColor;
        boolean blink = (time % 4f) < 0.12f;
        // barba
        switch (look.beard) {
            case 1:
                add(T_HALF_DOWN, hx - r * 0.95f, hy - r * 0.95f, r * 1.9f, r * 0.8f, 0, 0, 0, mix(skin, hc, 0.3f), false);
                break;
            case 2:
                add(T_HALF_DOWN, hx - r * 1.02f, hy - r * 1.08f, r * 2.04f, r * 1.1f, 0, 0, 0, hc, true);
                break;
            case 4:
                px(hx, hy, f, 2, -6, 2, 2, hc);
                break;
            default:
                break;
        }
        // olhos (pixels escuros, estilo anime) e bochechas
        boolean covered = look.eyewear == 3 || look.eyewear == 5 || look.eyewear == 2;
        int e = look.eyes;
        if (look.eyewear != 5) {
            px(hx, hy, f, 4, -3, 1, 1, blush);
            px(hx, hy, f, -1, -3, 1, 1, blush);
        }
        if (!covered || look.eyewear == 3) {
            boolean showBack = look.eyewear != 3;
            if (e == 4) { // robo
                px(hx, hy, f, -1, -2, 7, 2, 0xff1744);
                px(hx, hy, f, 3, -1, 2, 1, 0xffcdd2);
            } else if (e == 2) { // feliz ^^
                px(hx, hy, f, 2, -2, 1, 1, ink);
                px(hx, hy, f, 3, -1, 1, 1, ink);
                px(hx, hy, f, 4, -2, 1, 1, ink);
                if (showBack) {
                    px(hx, hy, f, -1, -2, 1, 1, ink);
                    px(hx, hy, f, 0, -1, 1, 1, ink);
                    px(hx, hy, f, 1, -2, 1, 1, ink);
                }
            } else if (blink || e == 3) { // piscando / sonolento
                px(hx, hy, f, 3, -2, 2, 1, ink);
                if (showBack) px(hx, hy, f, 0, -2, 2, 1, ink);
                if (e == 3) {
                    px(hx, hy, f, 3, -1, 2, 1, darker(skin, 0.8f));
                    if (showBack) px(hx, hy, f, 0, -1, 2, 1, darker(skin, 0.8f));
                }
            } else {
                int tall = e == 5 ? 3 : 2;
                // olho da frente: escuro com brilho em cima
                px(hx, hy, f, 3, -2, 1, tall, ink);
                px(hx, hy, f, 4, -2, 1, tall - 1, ink);
                px(hx, hy, f, 4, -2 + tall - 1, 1, 1, 0xffffff);
                if (showBack) px(hx, hy, f, 0, -2, 1, tall, ink);
                // sobrancelhas: bravo/surpreso
                int brow = darker(hc, 0.75f);
                if (e == 1) {
                    px(hx, hy, f, 2, 1, 1, 1, brow);
                    px(hx, hy, f, 3, 1, 2, 1, brow);
                    px(hx, hy, f, 2, 0, 1, 1, brow);
                    if (showBack) px(hx, hy, f, -1, 1, 2, 1, brow);
                } else if (e == 5) {
                    px(hx, hy, f, 3, 2, 2, 1, brow);
                    if (showBack) px(hx, hy, f, -1, 2, 2, 1, brow);
                }
            }
        }
        // boca
        if (look.eyewear != 4 && look.eyewear != 5) {
            switch (look.mouth) {
                case 1: // sorriso
                    px(hx, hy, f, 2, -5, 2, 1, lip);
                    px(hx, hy, f, 4, -4, 1, 1, lip);
                    break;
                case 2: // dentes cerrados
                    px(hx, hy, f, 1, -6, 4, 2, 0x4a1c1c);
                    px(hx, hy, f, 1, -5, 4, 1, 0xf2f2f2);
                    break;
                case 3: // grito
                    px(hx, hy, f, 2, -6, 2, 2, 0x4a1418);
                    break;
                case 4: // assobio
                    px(hx, hy, f, 3, -5, 1, 1, lip);
                    break;
                case 5: // sorriso de lado
                    px(hx, hy, f, 1, -5, 2, 1, lip);
                    px(hx, hy, f, 3, -4, 1, 1, lip);
                    break;
                default:
                    px(hx, hy, f, 2, -5, 2, 1, lip);
                    break;
            }
        }
        if (look.beard == 3) px(hx, hy, f, 1, -4, 4, 1, hc); // bigode
        // oculos / mascaras (os de protecao ficam na testa, desenhados depois do chapeu)
        int v = look.visor;
        switch (look.eyewear) {
            case 2: // oculos escuros
                px(hx, hy, f, -1, -2, 7, 1, 0x111111);
                px(hx, hy, f, -1, -3, 2, 1, 0x111111);
                px(hx, hy, f, 3, -3, 3, 1, 0x111111);
                px(hx, hy, f, 4, -2, 1, 1, 0x90a4ae);
                break;
            case 3: // tapa-olho
                dRect(hx - r, hy + 1.6f, r * 2, 1.3f, r, 0.65f, f * -18, 0x111111);
                px(hx, hy, f, 3, -3, 2, 3, 0x111111);
                break;
            case 4: // mascara ninja
                add(T_HALF_DOWN, hx - r * 1.02f, hy - r * 1.04f, r * 2.04f, r - 2.4f, 0, 0, 0, v, false);
                dRect(hx - f * r - (f > 0 ? 4 : 0), hy - 3.2f, 4, 2.2f, 0, 1.1f, f * 25 + MathUtils.sin(time * 9) * 10, v);
                break;
            case 5: // mascara de gas
                px(hx, hy, f, 2, -3, 3, 3, 0x263238);
                px(hx, hy, f, 3, -2, 1, 1, v);
                px(hx, hy, f, -1, -3, 2, 3, 0x263238);
                circle(hx + f * 4.6f, hy - 5.2f, 2.8f, 0x546e7a);
                px(hx, hy, f, 5, -6, 1, 1, 0x263238);
                break;
            case 6: // monoculo
                add(T_RING, hx + f * 4.6f, hy - 1.2f, 2.8f, 2.8f, 0, 0, 0, 0xffc107, false);
                break;
            default:
                break;
        }
        // cabelo da frente: topo com brilho e franja em tufos
        int hl = mix(hc, 0xffffff, 0.28f);
        switch (look.hair) {
            case 1:
            case 3:
            case 5:
            case 6:
            case 2: {
                add(T_HALF, hx - r * 1.08f, hy + r * 0.24f, r * 2.16f, r * 0.98f, 0, 0, 0, hc, true);
                float top = hy + r * 0.36f;
                for (int i = 0; i < 3; i++) tuft(hx + f * (r * 0.02f + i * r * 0.34f), top, 3.6f, look.hair == 2 ? 4.2f : 3.2f, f * (i - 1) * 8, hc);
                // costeleta atras do olho
                add(T_RECT, hx - f * r * 0.45f - 1.3f, hy - 2.6f, 2.6f, r * 0.36f + 2.6f, 0, 0, 0, hc, false);
                if (look.hair == 2) {
                    for (int i = 0; i < 4; i++) {
                        float ang = 150 - i * 32;
                        float cx = hx + f * MathUtils.cosDeg(ang) * r * 0.82f * (f > 0 ? 1 : -1) * f, cy = hy + MathUtils.sinDeg(ang) * r * 0.82f;
                        cx = hx + MathUtils.cosDeg(f > 0 ? ang : 180 - ang) * r * 0.82f;
                        add(T_TRI, cx - 3, cy - 1, 6, 7, 3, 1, (f > 0 ? ang : 180 - ang) - 90, hc, true);
                    }
                }
                if (look.hair == 6) add(T_ELLIPSE, hx + f * 4.5f, hy + r * 0.82f, 5, 2.8f, 0, 0, f * -18, hc, true);
                // reflexo no cabelo
                px(hx, hy, f, -3, 5, 3, 1, hl);
                px(hx, hy, f, -4, 4, 1, 1, hl);
                break;
            }
            case 4:
                px(hx, hy, f, -3, 6, 3, 1, hl);
                break;
            case 0:
                px(hx, hy, f, -2, 4, 2, 1, mix(skin, 0xffffff, 0.35f));
                break;
            default:
                break;
        }
    }

    /** oculos de protecao levantados na testa (por cima do capacete/bone) */
    private void goggles(Look look, float hx, float hy, int f) {
        if (look.eyewear != 0) return;
        int hat = look.hat;
        if (!(hat == 0 || hat == 1 || hat == 2 || hat == 6 || hat == 8 || hat == 11 || hat == 12)) return;
        int v = look.visor;
        int ty = hat == 6 || hat == 2 ? 2 : 1;
        px(hx, hy, f, -6, ty, 13, 1, 0x2a2a30);
        px(hx, hy, f, 0, ty - 1, 3, 3, 0x2a2a30);
        px(hx, hy, f, 3, ty - 1, 3, 3, 0x2a2a30);
        px(hx, hy, f, 1, ty, 1, 1, v);
        px(hx, hy, f, 4, ty, 1, 1, v);
        px(hx, hy, f, 4, ty + 1, 1, 1, 0xffffff);
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
        float o = pix ? PIX_LINE : outlineWidth;
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0 && o <= 0.01f) continue;
            for (int i = 0; i < n; i++) {
                if (pass == 0 && !pline[i]) continue;
                if (pix && type[i] == T_GLOSS) continue;
                int col = pass == 0 ? (pix ? mix(darker(pcol[i], 0.38f), OUTLINE, 0.35f) : OUTLINE) : pcol[i];
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
