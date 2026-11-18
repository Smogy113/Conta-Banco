package com.brigadajato.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.brigadajato.I18n;
import com.brigadajato.Settings;
import com.brigadajato.render.Draw;
import com.brigadajato.render.Icons;
import com.brigadajato.render.SoldierPainter;
import com.brigadajato.world.Gun;
import com.brigadajato.world.Player;
import com.brigadajato.world.PlayerInput;

/**
 * Controles de toque: joystick esquerdo (andar/jetpack), joystick direito (mirar/atirar) e botoes.
 * Tambem cuida do teclado/mouse no PC. Coordenadas no espaco do HUD (altura virtual 720).
 */
public class TouchControls extends InputAdapter {
    public static final int B_GRENADE = 0, B_GTYPE = 1, B_RELOAD = 2, B_MELEE = 3, B_SWAP = 4, B_ZOOM = 5, B_PICKUP = 6,
            B_USE = 7, B_PAUSE = 8, B_SCORE = 9, B_FIRE = 10, B_HOOK = 11, B_EMOTE = 12, B_COUNT = 13;
    /** nomes salvos no layout personalizado (botoes + "L"/"R" para os joysticks) */
    public static final String[] KEYS = {"gren", "gtype", "reload", "melee", "swap", "zoom", "pickup", "use", "pause", "score", "fire", "hook", "emote"};

    private final Settings settings;
    public final boolean touchUi;
    public float hudW = 1280, hudH = 720;

    // botoes: centro e raio
    public final float[] bx = new float[B_COUNT], by = new float[B_COUNT], br = new float[B_COUNT];
    public final boolean[] visible = new boolean[B_COUNT];
    private final int[] buttonPointer = new int[B_COUNT];
    private int presses;

    // joysticks
    private int leftPointer = -1, rightPointer = -1;
    private float lBaseX, lBaseY, lX, lY, rBaseX, rBaseY, rX, rY;
    private float stickR = 95;
    public float moveX, moveY;
    public float aimAngle = 0;
    public float aimMag;
    public boolean firing;
    public boolean useHeld, scoreHeld;
    public boolean pauseRequested;
    public boolean zoomToggled;
    private boolean fireButtonHeld;
    // toque duplo para o lado = rolamento
    private int lastFlickDir;
    private float lastFlickTime = -9, clock;
    private boolean flickArmed = true;
    private int lastKeyDir;
    private float lastKeyTime = -9;
    /** roda de emotes aberta */
    public boolean wheelOpen;
    private int pendingEmote = -1;
    /** posicao padrao dos joysticks (pode ser personalizada) */
    public float leftStickX = 180, leftStickY = 170, rightStickX = -1, rightStickY = 190;
    /** modo editor de layout: tudo visivel, nada dispara */
    public boolean editMode;

    // teclado / mouse
    public boolean mouseAim;
    public float mouseX, mouseY;
    private boolean mouseFire;
    public boolean keyboardUsed;

    public TouchControls(Settings settings, boolean touchUi) {
        this.settings = settings;
        this.touchUi = touchUi;
        for (int i = 0; i < B_COUNT; i++) buttonPointer[i] = -1;
        mouseAim = !touchUi;
    }

    public void resize(float hudW, float hudH) {
        this.hudW = hudW;
        this.hudH = hudH;
        layout();
    }

    private void place(int b, float x, float y, float r) {
        float s = settings.controlsScale;
        if (settings.leftHanded && b != B_PAUSE && b != B_SCORE && b != B_ZOOM) x = hudW - x;
        bx[b] = x;
        by[b] = y;
        br[b] = r * s;
    }

    public void layout() {
        float W = hudW, H = hudH, s = settings.controlsScale;
        stickR = 92 * s;
        place(B_GRENADE, W - 92 * s, 300 * s, 44);
        place(B_GTYPE, W - 160 * s, 352 * s, 24);
        place(B_RELOAD, W - 92 * s, 410 * s, 34);
        place(B_MELEE, W - 200 * s, 410 * s, 36);
        place(B_SWAP, W - 170 * s, 52 * s, 44);
        place(B_ZOOM, W - 180, H - 44, 28);
        place(B_PICKUP, W * 0.5f + 150 * s, 120 * s, 46);
        place(B_USE, W * 0.5f - 150 * s, 120 * s, 50);
        place(B_PAUSE, W - 44, H - 44, 28);
        place(B_SCORE, W - 112, H - 44, 28);
        place(B_FIRE, W - 300 * s, 230 * s, 60);
        place(B_HOOK, W - 92 * s, 190 * s, 36);
        place(B_EMOTE, W - 248, H - 44, 28);
        leftStickX = settings.leftHanded ? W - 180 : 180;
        leftStickY = 170;
        rightStickX = settings.leftHanded ? 300 : W - 330;
        rightStickY = 190;
        applyCustom();
    }

    /** Aplica as posicoes salvas pelo editor de layout (x,y normalizados). */
    private void applyCustom() {
        String lay = settings.controlLayout;
        if (lay == null || lay.isEmpty()) return;
        for (String part : lay.split(";")) {
            int c = part.indexOf(':');
            if (c < 0) continue;
            String key = part.substring(0, c);
            String[] xy = part.substring(c + 1).split(",");
            if (xy.length < 2) continue;
            float x, y;
            try {
                x = Float.parseFloat(xy[0]) * hudW;
                y = Float.parseFloat(xy[1]) * hudH;
            } catch (NumberFormatException e) {
                continue;
            }
            if (key.equals("L")) {
                leftStickX = x;
                leftStickY = y;
            } else if (key.equals("R")) {
                rightStickX = x;
                rightStickY = y;
            } else for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) {
                bx[i] = x;
                by[i] = y;
            }
        }
    }

    /** Monta o texto do layout atual (para salvar). */
    public String layoutString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < B_COUNT; i++) sb.append(KEYS[i]).append(':').append(bx[i] / hudW).append(',').append(by[i] / hudH).append(';');
        sb.append("L:").append(leftStickX / hudW).append(',').append(leftStickY / hudH).append(';');
        sb.append("R:").append(rightStickX / hudW).append(',').append(rightStickY / hudH);
        return sb.toString();
    }

    // ---- roda de emotes
    public float wheelX() {
        return hudW / 2;
    }

    public float wheelY() {
        return hudH / 2 + 20;
    }

    public static final float WHEEL_R = 190;

    /** Indice do emote no ponto (x,y) do HUD, ou -1 (centro = fechar). */
    public int wheelSlot(float x, float y) {
        float dx = x - wheelX(), dy = y - wheelY();
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 55 || d > WHEEL_R + 70) return -1;
        float ang = MathUtils.atan2(dy, dx);
        if (ang < 0) ang += MathUtils.PI2;
        int n = com.brigadajato.world.C.EMOTE_COUNT;
        float step = MathUtils.PI2 / n;
        int slot = (int) ((ang + step / 2) / step) % n;
        return slot;
    }

    public void chooseEmote(int e) {
        pendingEmote = e;
        wheelOpen = false;
    }

    private float hx(int sx) {
        return sx / (float) Gdx.graphics.getWidth() * hudW;
    }

    private float hy(int sy) {
        return (1f - sy / (float) Gdx.graphics.getHeight()) * hudH;
    }

    private int hitButton(float x, float y) {
        for (int i = 0; i < B_COUNT; i++) {
            if (!visible[i]) continue;
            float dx = x - bx[i], dy = y - by[i];
            float r = br[i] * 1.15f;
            if (i == B_SWAP) {
                if (Math.abs(dx) < 120 * settings.controlsScale && Math.abs(dy) < 42 * settings.controlsScale) return i;
                continue;
            }
            if (dx * dx + dy * dy < r * r) return i;
        }
        return -1;
    }

    private boolean leftSide(float x) {
        return settings.leftHanded ? x > hudW * 0.5f : x < hudW * 0.5f;
    }

    @Override
    public boolean touchDown(int sx, int sy, int pointer, int button) {
        if (!touchUi) {
            if (wheelOpen) {
                int slot = wheelSlot(hx(sx), hy(sy));
                if (slot >= 0) chooseEmote(slot);
                else wheelOpen = false;
                return true;
            }
            if (button == Input.Buttons.LEFT) mouseFire = true;
            if (button == Input.Buttons.RIGHT) presses |= PlayerInput.P_GRENADE;
            if (button == Input.Buttons.MIDDLE) presses |= PlayerInput.P_HOOK;
            return true;
        }
        float x = hx(sx), y = hy(sy);
        if (wheelOpen) {
            int slot = wheelSlot(x, y);
            if (slot >= 0) chooseEmote(slot);
            else wheelOpen = false;
            return true;
        }
        int b = hitButton(x, y);
        if (b >= 0) {
            buttonPointer[b] = pointer;
            switch (b) {
                case B_GRENADE: presses |= PlayerInput.P_GRENADE; break;
                case B_GTYPE: presses |= PlayerInput.P_GRENADE_TYPE; break;
                case B_RELOAD: presses |= PlayerInput.P_RELOAD; break;
                case B_MELEE: presses |= PlayerInput.P_MELEE; break;
                case B_SWAP: presses |= PlayerInput.P_SWAP; break;
                case B_PICKUP: presses |= PlayerInput.P_PICKUP; break;
                case B_ZOOM:
                    zoomToggled = !zoomToggled;
                    presses |= PlayerInput.P_ZOOM;
                    break;
                case B_PAUSE: pauseRequested = true; break;
                case B_USE: useHeld = true; break;
                case B_SCORE: scoreHeld = true; break;
                case B_FIRE: fireButtonHeld = true; break;
                case B_HOOK: presses |= PlayerInput.P_HOOK; break;
                case B_EMOTE: wheelOpen = !wheelOpen; break;
                default: break;
            }
            return true;
        }
        if (leftSide(x)) {
            if (leftPointer == -1) {
                leftPointer = pointer;
                lBaseX = lX = x;
                lBaseY = lY = y;
            }
        } else if (rightPointer == -1) {
            rightPointer = pointer;
            rBaseX = rX = x;
            rBaseY = rY = y;
        }
        return true;
    }

    @Override
    public boolean touchDragged(int sx, int sy, int pointer) {
        if (!touchUi) {
            mouseX = sx;
            mouseY = sy;
            return false;
        }
        float x = hx(sx), y = hy(sy);
        if (pointer == leftPointer) {
            lX = x;
            lY = y;
            // base acompanha o dedo se ele passar muito do limite
            float dx = lX - lBaseX, dy = lY - lBaseY;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > stickR * 1.4f) {
                lBaseX = lX - dx / d * stickR * 1.4f;
                lBaseY = lY - dy / d * stickR * 1.4f;
            }
        } else if (pointer == rightPointer) {
            rX = x;
            rY = y;
            float dx = rX - rBaseX, dy = rY - rBaseY;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > stickR * 1.3f) {
                rBaseX = rX - dx / d * stickR * 1.3f;
                rBaseY = rY - dy / d * stickR * 1.3f;
            }
        }
        return true;
    }

    @Override
    public boolean touchUp(int sx, int sy, int pointer, int button) {
        if (!touchUi) {
            if (button == Input.Buttons.LEFT) mouseFire = false;
            return true;
        }
        if (pointer == leftPointer) leftPointer = -1;
        if (pointer == rightPointer) rightPointer = -1;
        for (int i = 0; i < B_COUNT; i++) {
            if (buttonPointer[i] == pointer) {
                buttonPointer[i] = -1;
                if (i == B_USE) useHeld = false;
                if (i == B_SCORE) scoreHeld = false;
                if (i == B_FIRE) fireButtonHeld = false;
            }
        }
        return true;
    }

    @Override
    public boolean mouseMoved(int sx, int sy) {
        mouseX = sx;
        mouseY = sy;
        return false;
    }

    @Override
    public boolean keyDown(int keycode) {
        keyboardUsed = true;
        switch (keycode) {
            case Input.Keys.G: presses |= PlayerInput.P_GRENADE; break;
            case Input.Keys.T: presses |= PlayerInput.P_GRENADE_TYPE; break;
            case Input.Keys.R: presses |= PlayerInput.P_RELOAD; break;
            case Input.Keys.F: presses |= PlayerInput.P_MELEE; break;
            case Input.Keys.Q: presses |= PlayerInput.P_SWAP; break;
            case Input.Keys.E: presses |= PlayerInput.P_PICKUP; break;
            case Input.Keys.C: presses |= PlayerInput.P_HOOK; break;
            case Input.Keys.SHIFT_LEFT:
            case Input.Keys.SHIFT_RIGHT:
                presses |= PlayerInput.P_ROLL;
                break;
            case Input.Keys.A:
            case Input.Keys.D:
            case Input.Keys.LEFT:
            case Input.Keys.RIGHT: {
                int dir = keycode == Input.Keys.A || keycode == Input.Keys.LEFT ? -1 : 1;
                if (dir == lastKeyDir && clock - lastKeyTime < 0.28f) presses |= PlayerInput.P_ROLL;
                lastKeyDir = dir;
                lastKeyTime = clock;
                return false;
            }
            case Input.Keys.V: wheelOpen = !wheelOpen; break;
            case Input.Keys.Z:
                zoomToggled = !zoomToggled;
                presses |= PlayerInput.P_ZOOM;
                break;
            case Input.Keys.ESCAPE:
            case Input.Keys.BACK:
            case Input.Keys.P:
                pauseRequested = true;
                break;
            default:
                if (wheelOpen && keycode >= Input.Keys.NUM_0 && keycode <= Input.Keys.NUM_9) {
                    int n = keycode == Input.Keys.NUM_0 ? 9 : keycode - Input.Keys.NUM_1;
                    chooseEmote(n);
                    return true;
                }
                return false;
        }
        return true;
    }

    @Override
    public boolean scrolled(float ax, float ay) {
        presses |= PlayerInput.P_SWAP;
        return true;
    }

    /**
     * Preenche o comando do jogador local. playerScreenX/Y = posicao do ombro do jogador na tela
     * (pixels, y para baixo) para a mira do mouse.
     */
    public void fill(PlayerInput in, float playerScreenX, float playerScreenY) {
        // teclado
        float kx = 0, ky = 0;
        boolean kb = false;
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) { kx -= 1; kb = true; }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) { kx += 1; kb = true; }
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.SPACE)) { ky = 1; kb = true; }
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) { ky = -1; kb = true; }
        boolean keyUse = Gdx.input.isKeyPressed(Input.Keys.E) || Gdx.input.isKeyPressed(Input.Keys.B);
        boolean keyScore = Gdx.input.isKeyPressed(Input.Keys.TAB);

        moveX = 0;
        moveY = 0;
        clock += Gdx.graphics.getDeltaTime();
        if (leftPointer != -1) {
            float dx = (lX - lBaseX) / stickR, dy = (lY - lBaseY) / stickR;
            float m = (float) Math.sqrt(dx * dx + dy * dy);
            if (m > 1) {
                dx /= m;
                dy /= m;
            }
            moveX = Math.abs(dx) < 0.18f ? 0 : dx;
            moveY = Math.abs(dy) < 0.22f ? 0 : dy;
            // duas "batidas" rapidas no mesmo lado = rolamento
            if (Math.abs(dx) > 0.85f && Math.abs(dy) < 0.55f) {
                int dir = dx > 0 ? 1 : -1;
                if (flickArmed) {
                    if (dir == lastFlickDir && clock - lastFlickTime < 0.32f) {
                        presses |= PlayerInput.P_ROLL;
                        lastFlickTime = -9;
                    } else lastFlickTime = clock;
                    lastFlickDir = dir;
                    flickArmed = false;
                }
            } else if (Math.abs(dx) < 0.35f) flickArmed = true;
        } else flickArmed = true;
        if (kb) {
            moveX = kx;
            moveY = ky;
        }
        firing = false;
        aimMag = 0;
        if (rightPointer != -1) {
            float dx = rX - rBaseX, dy = rY - rBaseY;
            float m = (float) Math.sqrt(dx * dx + dy * dy) / stickR;
            if (m > 0.12f) aimAngle = MathUtils.atan2(dy, dx);
            aimMag = Math.min(1, m);
            if (settings.aimToFire && m > 0.72f) firing = true;
        }
        if (fireButtonHeld) firing = true;
        if (!touchUi) {
            float dx = mouseX - playerScreenX, dy = playerScreenY - mouseY;
            if (dx * dx + dy * dy > 16) aimAngle = MathUtils.atan2(dy, dx);
            firing = mouseFire || Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        }
        in.moveX = moveX;
        in.moveY = moveY;
        in.aim = aimAngle;
        in.fire = firing;
        in.use = useHeld || keyUse;
        in.presses |= presses;
        presses = 0;
        if (pendingEmote >= 0) {
            in.emote = pendingEmote;
            pendingEmote = -1;
        }
        if (wheelOpen) in.fire = false;
        scoreHeldKey = keyScore;
    }

    private boolean scoreHeldKey;

    public boolean scoreboardVisible() {
        return scoreHeld || scoreHeldKey;
    }

    public void release() {
        leftPointer = rightPointer = -1;
        for (int i = 0; i < B_COUNT; i++) buttonPointer[i] = -1;
        useHeld = scoreHeld = fireButtonHeld = mouseFire = false;
        presses = 0;
        wheelOpen = false;
    }

    // ------------------------------------------------------------------ desenho

    public void updateVisibility(Player p, boolean nearWeapon, boolean useAvailable) {
        boolean alive = p != null && p.alive;
        visible[B_GRENADE] = alive;
        visible[B_GTYPE] = alive;
        visible[B_RELOAD] = alive;
        visible[B_MELEE] = alive;
        visible[B_SWAP] = alive;
        visible[B_ZOOM] = alive;
        visible[B_PICKUP] = alive && nearWeapon;
        visible[B_USE] = alive && useAvailable;
        visible[B_PAUSE] = true;
        visible[B_SCORE] = true;
        visible[B_FIRE] = alive && !settings.aimToFire;
        visible[B_HOOK] = alive;
        visible[B_EMOTE] = alive;
        if (editMode) for (int i = 0; i < B_COUNT; i++) visible[i] = true;
        if (!touchUi) {
            for (int i = 0; i < B_COUNT; i++) visible[i] = false;
        }
    }

    public void draw(Draw d, BitmapFont small, BitmapFont normal, Player p, SoldierPainter painter) {
        if (!touchUi) return;
        float op = settings.controlsOpacity;
        // joystick esquerdo
        float lbx = leftPointer != -1 ? lBaseX : leftStickX;
        float lby = leftPointer != -1 ? lBaseY : leftStickY;
        stick(d, lbx, lby, leftPointer != -1 ? lX : lbx, leftPointer != -1 ? lY : lby, op, 0xffffff);
        // joystick direito
        float rbx = rightPointer != -1 ? rBaseX : rightStickX;
        float rby = rightPointer != -1 ? rBaseY : rightStickY;
        if (rightPointer != -1) {
            boolean hot = settings.aimToFire && aimMag > 0.72f;
            stick(d, rbx, rby, rX, rY, op, hot ? 0xff5252 : 0xffffff);
            if (settings.aimToFire) {
                d.color(1, 0.3f, 0.3f, op * 0.5f);
                d.ring(rbx, rby, stickR * 0.72f);
            }
        } else {
            stick(d, rbx, rby, rbx, rby, op * 0.6f, 0xffffff);
            small.setColor(1, 1, 1, op);
            if (settings.aimToFire) small.draw(d.batch, I18n.t("fire_auto"), rbx, rby + 6, 0, Align.center, false);
        }
        if (p == null) return;
        for (int i = 0; i < B_COUNT; i++) {
            if (!visible[i] || i == B_SWAP) continue;
            boolean down = buttonPointer[i] != -1;
            int col = 0xffffff;
            if (i == B_FIRE) col = 0xff5252;
            if (i == B_USE) col = 0xffd54f;
            if (i == B_PICKUP) col = 0x81c784;
            if (i == B_ZOOM && zoomToggled) col = 0x4fc3f7;
            d.rgb(0x000000, op * 0.45f);
            d.circle(bx[i], by[i], br[i] + 3);
            d.rgb(col, op * (down ? 0.6f : 0.32f));
            d.circle(bx[i], by[i], br[i]);
            d.rgb(col, op * 0.9f);
            d.ring(bx[i], by[i], br[i]);
            icon(d, small, normal, i, bx[i], by[i], br[i], p, op);
        }
        // painel da arma (toque para trocar)
        if (visible[B_SWAP]) {
            float x = bx[B_SWAP], y = by[B_SWAP];
            float w = 236 * settings.controlsScale, h = 80 * settings.controlsScale;
            d.color(1, 1, 1, Math.min(1, op + 0.25f));
            Draw.patch(d, d.a.uiChip, x - w / 2, y - h / 2, w, h, 14);
            Gun g = p.gun();
            if (g != null) {
                painter.drawGun(d, g.type, p.look.gunSkin, x - w * 0.22f, y + 4, 0, 1.25f * settings.controlsScale, 1);
                normal.setColor(1, g.mag == 0 ? 0.35f : 1, g.mag == 0 ? 0.35f : 1, 1);
                normal.draw(d.batch, ammoText.of(g.mag, g.reserve), x + w * 0.27f, y + 16, 0, Align.center, false);
                small.setColor(0.85f, 0.85f, 0.85f, 1);
                small.draw(d.batch, I18n.pt ? g.type.pt : g.type.en, x + w * 0.27f, y - 10, 0, Align.center, false);
            }
            Gun o = p.otherGun();
            if (o != null) {
                float ow = w * 0.62f, oh = 30 * settings.controlsScale;
                d.color(1, 1, 1, Math.min(1, op + 0.1f));
                Draw.patch(d, d.a.uiChip, x - w / 2, y + h / 2 + 6, ow, oh, 12);
                painter.drawGun(d, o.type, p.look.gunSkin, x - w / 2 + ow * 0.4f, y + h / 2 + 6 + oh / 2, 0, 0.75f * settings.controlsScale, 0.95f);
                Icons.swap(d, x - w / 2 + ow - 16, y + h / 2 + 6 + oh / 2, 22, 0.9f);
            }
            normal.setColor(1, 1, 1, 1);
        }
        small.setColor(1, 1, 1, 1);
    }

    /** Desenha a roda de emotes (no PC e no celular). */
    public void drawWheel(Draw d, BitmapFont small, BitmapFont normal) {
        if (!wheelOpen) return;
        float cx = wheelX(), cy = wheelY();
        d.rgb(0x000000, 0.45f);
        d.circle(cx, cy, WHEEL_R + 62);
        int n = com.brigadajato.world.C.EMOTE_COUNT;
        int hover = -1;
        if (!touchUi) hover = wheelSlot(hx((int) mouseX), hy((int) mouseY));
        for (int i = 0; i < n; i++) {
            float ang = i / (float) n * MathUtils.PI2;
            float x = cx + MathUtils.cos(ang) * WHEEL_R, y = cy + MathUtils.sin(ang) * WHEEL_R;
            boolean dance = i >= com.brigadajato.world.C.EMOTE_DANCE_FIRST;
            boolean phrase = i < com.brigadajato.world.C.EMOTE_PHRASES;
            int col = dance ? 0xb388ff : (phrase ? 0x4fc3f7 : 0xffd740);
            d.rgb(0x15151c, 0.9f);
            d.circle(x, y, 42);
            d.rgb(col, i == hover ? 0.95f : 0.7f);
            d.circle(x, y, 38);
            String label = I18n.t(phrase ? "phrase_" + i : (dance ? "dance_" + (i - com.brigadajato.world.C.EMOTE_DANCE_FIRST) : "react_" + (i - com.brigadajato.world.C.EMOTE_PHRASES)));
            small.setColor(0.05f, 0.05f, 0.08f, 1);
            small.getData().setScale(small.getScaleX() * 0.78f);
            small.draw(d.batch, label, x, y + 8, 0, Align.center, false);
            small.getData().setScale(small.getScaleX() / 0.78f);
            if (!touchUi && i < 10) {
                small.setColor(1, 1, 1, 0.8f);
                small.draw(d.batch, "" + ((i + 1) % 10), x, y - 22, 0, Align.center, false);
            }
        }
        normal.setColor(1, 1, 1, 1);
        normal.draw(d.batch, I18n.t("emotes"), cx, cy + 10, 0, Align.center, false);
        small.setColor(1, 1, 1, 1);
    }

    private final com.brigadajato.render.Num.Pair ammoText = new com.brigadajato.render.Num.Pair("/");

    private void stick(Draw d, float bx, float by, float kx, float ky, float op, int knobCol) {
        float dx = kx - bx, dy = ky - by;
        float m = (float) Math.sqrt(dx * dx + dy * dy);
        if (m > stickR) {
            kx = bx + dx / m * stickR;
            ky = by + dy / m * stickR;
        }
        d.rgb(0x000000, op * 0.35f);
        d.circle(bx, by, stickR + 4);
        d.rgb(0xffffff, op * 0.18f);
        d.circle(bx, by, stickR);
        d.rgb(0xffffff, op * 0.7f);
        d.ring(bx, by, stickR);
        d.rgb(0x000000, op * 0.4f);
        d.circle(kx, ky, stickR * 0.42f + 3);
        d.rgb(knobCol, op * 0.85f);
        d.circle(kx, ky, stickR * 0.42f);
    }

    private void icon(Draw d, BitmapFont small, BitmapFont normal, int b, float x, float y, float r, Player p, float op) {
        d.rgb(0x1a1a1a, Math.min(1, op + 0.3f));
        switch (b) {
            case B_GRENADE: {
                Icons.nade(d, p.grenadeType, x - 4, y, r * 0.9f);
                normal.setColor(1, 1, 1, 1);
                normal.draw(d.batch, com.brigadajato.render.Num.of(p.nades(p.grenadeType)), x + r * 0.55f, y - r * 0.2f, 0, Align.center, false);
                break;
            }
            case B_GTYPE:
                Icons.nade(d, (p.grenadeType + 1) % com.brigadajato.world.Player.NADE_TYPES, x, y, r * 1.1f);
                break;
            case B_RELOAD:
                Icons.reload(d, x, y, r * 1.2f, Math.min(1, op + 0.3f));
                break;
            case B_MELEE:
                Icons.fist(d, x, y, r * 1.05f, p.look.skin);
                break;
            case B_ZOOM:
                Icons.magnifier(d, x, y, r * 1.1f, zoomToggled ? 0x0277bd : 0x1a1a1a, Math.min(1, op + 0.3f));
                break;
            case B_PICKUP:
                small.setColor(1, 1, 1, 1);
                small.draw(d.batch, I18n.t("pickup"), x, y + 8, 0, Align.center, false);
                break;
            case B_USE:
                small.setColor(1, 1, 1, 1);
                small.draw(d.batch, I18n.t("use"), x, y + 8, 0, Align.center, false);
                break;
            case B_PAUSE:
                d.rect(x - 7, y - 9, 5, 18);
                d.rect(x + 2, y - 9, 5, 18);
                break;
            case B_SCORE:
                for (int i = 0; i < 3; i++) d.rect(x - 9, y - 8 + i * 7, 18, 3.5f);
                break;
            case B_FIRE:
                d.ring(x, y, r * 0.45f);
                d.circle(x, y, 4);
                break;
            case B_HOOK: {
                d.rect(x - 1.5f, y - r * 0.5f, 3, r * 0.75f);
                d.region(d.a.tri, x, y + r * 0.35f, r * 0.55f, r * 0.45f, 0);
                d.ring(x, y - r * 0.5f, r * 0.18f);
                break;
            }
            case B_EMOTE:
                d.ring(x, y, r * 0.55f);
                d.circle(x - r * 0.2f, y + r * 0.12f, r * 0.08f);
                d.circle(x + r * 0.2f, y + r * 0.12f, r * 0.08f);
                d.batch.draw(d.a.half, x - r * 0.25f, y - r * 0.05f, r * 0.25f, 0, r * 0.5f, r * 0.25f, 1, 1, 180);
                break;
            default:
                break;
        }
    }
}
