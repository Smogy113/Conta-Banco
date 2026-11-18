package com.brigadajato.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.brigadajato.Assets;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.render.Draw;
import com.brigadajato.render.ModeIcons;
import com.brigadajato.render.SoldierPainter;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.WeaponType;

/**
 * Primeiro passo de "criar partida": escolher o modo em cartoes grandes e animados.
 * Depois abre a {@link SetupScreen} so com o resto das configuracoes.
 */
public class ModeSelectScreen extends BaseScreen {
    static final ModeType[] MODES = {ModeType.DEATHMATCH, ModeType.TEAM_DEATHMATCH, ModeType.CTF, ModeType.BOMB, ModeType.SURVIVAL,
            ModeType.GUN_GAME, ModeType.KING_HILL, ModeType.INFECTION, ModeType.BATTLE_ROYALE, ModeType.DUEL, ModeType.HIDE_SEEK, ModeType.JUGGERNAUT};
    private static final int[] COLORS = {0xd84315, 0x1565c0, 0x2e7d32, 0xb71c1c, 0x6d4c41, 0xf9a825, 0x7b1fa2, 0x558b2f,
            0x00838f, 0x455a64, 0xad1457, 0x5d4037};
    private static final String[][] TAGS = {
            {"SOLO", "TIMES", "TIMES", "RODADAS", "CO-OP", "SOLO", "TIMES", "SOLO", "1 VIDA", "1 x 1", "TIMES", "1 x TODOS"},
            {"SOLO", "TEAMS", "TEAMS", "ROUNDS", "CO-OP", "SOLO", "TEAMS", "SOLO", "1 LIFE", "1 v 1", "TEAMS", "1 v ALL"}};

    private final SetupScreen.Kind kind;
    private final Array<ModeCard> cards = new Array<>();
    private final Label infoName, infoDesc;
    private final InfoBar info;
    private int focus;
    private float leaving = -1;
    private ModeCard chosen;
    private float time, flash, shake, descShown;
    private String descFull = "";
    private float glowR = 1, glowG = 0.4f, glowB = 0.1f;
    private final Fx fx = new Fx();
    private final com.badlogic.gdx.math.Vector2 tmpV = new com.badlogic.gdx.math.Vector2();
    private float shx, shy;
    /** teste: escolhe o modo em foco sozinho nesse tempo (segundos) */
    public float autoPickAt = -1;

    public ModeSelectScreen(BrigadaJatoGame game, SetupScreen.Kind kind) {
        super(game);
        this.kind = kind;
        ModeType last = game.settings.lastMatch.mode;
        for (int i = 0; i < MODES.length; i++) if (MODES[i] == last) focus = i;

        Table header = new Table();
        header.add(button(I18n.t("back"), "gray-small", this::onBack)).width(170).height(52).left();
        header.add(new TitleFx(I18n.pt ? "ESCOLHA O MODO" : "CHOOSE A MODE")).expandX().height(56).center();
        Label step = label(kind == SetupScreen.Kind.OFFLINE ? (I18n.pt ? "1/2  CONTRA BOTS" : "1/2  VS BOTS")
                : (I18n.pt ? "1/2  CRIAR SALA" : "1/2  CREATE ROOM"), "small");
        header.add(step).width(170).right();
        root.top().pad(18, 28, 14, 28);
        root.add(header).growX().padBottom(12).row();

        Table grid = new Table();
        for (int i = 0; i < MODES.length; i++) {
            ModeCard c = new ModeCard(i);
            cards.add(c);
            if (i == 6) grid.row();
            grid.add(c).size(188, 222).pad(7);
        }
        root.add(grid).row();

        info = new InfoBar();
        Table infoT = new Table();
        infoName = label("", "yellow");
        infoDesc = label("", "small");
        infoDesc.setWrap(true);
        infoT.add(infoName).left().row();
        infoT.add(infoDesc).width(900).left();
        info.add(infoT).padLeft(26).expandX().left();
        info.add(button(I18n.pt ? "ESCOLHER  >" : "SELECT  >", "green", () -> choose(cards.get(focus)))).width(240).height(60).padRight(18);
        root.add(info).width(1210).height(92).padTop(12);
        setFocus(focus);
    }

    private void setFocus(int i) {
        boolean changed = i != focus;
        if (changed) game.audio.ui("ui_click");
        focus = i;
        infoName.setText(I18n.t("mode_" + key(MODES[i])));
        infoName.clearActions();
        infoName.getColor().a = 0;
        infoName.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(0.25f));
        descFull = I18n.t("desc_" + key(MODES[i]));
        descShown = 0;
        infoDesc.setText("");
        info.color = COLORS[i];
        info.pulse = 1;
        if (cards.size > i) {
            ModeCard c = cards.get(i);
            c.pop = 0;
            if (time > 0.5f) {
                center(c);
                fx.burst(tmpV.x, tmpV.y, 18, COLORS[i], 160, 380, 0.5f);
                fx.ring(tmpV.x, tmpV.y, COLORS[i], 0.45f, 150);
            }
        }
    }

    private void center(Actor c) {
        c.localToStageCoordinates(tmpV.set(c.getWidth() / 2, c.getHeight() / 2));
    }

    private void choose(ModeCard c) {
        if (leaving >= 0) return;
        game.audio.ui("ui_click");
        chosen = c;
        focus = c.idx;
        leaving = 0;
        root.setTouchable(Touchable.disabled);
        game.audio.ui("levelup");
        center(c);
        int col = COLORS[c.idx];
        fx.burst(tmpV.x, tmpV.y, 70, col, 250, 900, 0.9f);
        fx.burst(tmpV.x, tmpV.y, 30, 0xffffff, 200, 700, 0.6f);
        fx.confetti(tmpV.x, tmpV.y, 60, col);
        fx.ring(tmpV.x, tmpV.y, 0xffffff, 0.45f, 420);
        fx.ring(tmpV.x, tmpV.y, col, 0.6f, 300);
        flash = 0.55f;
        shake = 14;
    }

    @Override
    public void show() {
        super.show();
        Gdx.input.setInputProcessor(new com.badlogic.gdx.InputMultiplexer(new com.badlogic.gdx.InputAdapter() {
            @Override
            public boolean keyDown(int k) {
                if (leaving >= 0) return true;
                int cols = 6;
                if (k == Input.Keys.RIGHT || k == Input.Keys.D) setFocus((focus + 1) % MODES.length);
                else if (k == Input.Keys.LEFT || k == Input.Keys.A) setFocus((focus - 1 + MODES.length) % MODES.length);
                else if (k == Input.Keys.DOWN || k == Input.Keys.S) setFocus(Math.min(MODES.length - 1, focus + cols));
                else if (k == Input.Keys.UP || k == Input.Keys.W) setFocus(Math.max(0, focus - cols));
                else if (k == Input.Keys.ENTER || k == Input.Keys.SPACE) choose(cards.get(focus));
                else return false;
                return true;
            }
        }, Gdx.input.getInputProcessor()));
    }

    @Override
    public void render(float delta) {
        time += delta;
        if (autoPickAt > 0 && time >= autoPickAt) {
            autoPickAt = -1;
            choose(cards.get(focus));
        }
        if (descShown < descFull.length()) {
            int before = (int) descShown;
            descShown = Math.min(descFull.length(), descShown + delta * 110);
            if ((int) descShown != before) infoDesc.setText(descFull.substring(0, (int) descShown));
        }
        super.render(delta);
        if (leaving >= 0) {
            leaving += Math.min(delta, 0.1f);
            if (leaving > 0.55f) {
                leaving = -2;
                go(new SetupScreen(game, kind, chosen.mode()));
            }
        }
    }

    @Override
    protected void drawBehind(float dt) {
        fx.update(dt);
        flash = Math.max(0, flash - dt * 1.6f);
        shake = Math.max(0, shake - dt * 40);
        com.badlogic.gdx.graphics.Camera cam = stage.getCamera();
        shx = MathUtils.random(-shake, shake);
        shy = MathUtils.random(-shake, shake);
        cam.position.add(shx, shy, 0);
        cam.update();
        float vw = stage.getViewport().getWorldWidth(), vh = stage.getViewport().getWorldHeight();
        float x0 = cam.position.x - vw / 2, y0 = cam.position.y - vh / 2;
        Draw d = game.draw;
        Assets a = game.assets;
        game.batch.setProjectionMatrix(cam.combined);
        game.batch.begin();
        // escurece o fundo para os cartoes saltarem
        d.rgb(0x05070c, 0.5f);
        d.rect(x0 - 20, y0 - 20, vw + 40, vh + 40);
        // grade tatica andando
        float gsz = 48, off = (time * 12) % gsz;
        d.rgb(0xffffff, 0.035f);
        for (float gx = x0 - gsz + off; gx < x0 + vw; gx += gsz) d.rect(gx, y0, 1.5f, vh);
        for (float gy = y0 - gsz + off; gy < y0 + vh; gy += gsz) d.rect(x0, gy, vw, 1.5f);
        // cor do modo em foco (suave)
        int fc = COLORS[focus];
        float k = Math.min(1, dt * 5);
        glowR += (((fc >> 16) & 0xff) / 255f - glowR) * k;
        glowG += (((fc >> 8) & 0xff) / 255f - glowG) * k;
        glowB += ((fc & 0xff) / 255f - glowB) * k;
        ModeCard fcard = cards.get(focus);
        center(fcard);
        float cx = tmpV.x, cy = tmpV.y;
        d.additive(true);
        d.color(glowR, glowG, glowB, 0.35f);
        d.soft(x0 + vw / 2, y0 + vh * 0.55f, vw * 0.7f);
        d.color(glowR, glowG, glowB, 0.45f);
        d.soft(cx, cy, 300);
        // raios de luz girando atras do cartao em foco
        for (int i = 0; i < 12; i++) {
            float ang = time * 9 + i * 30;
            float len = 520 + 60 * MathUtils.sin(time * 1.3f + i);
            float c = MathUtils.cosDeg(ang), sn = MathUtils.sinDeg(ang);
            d.color(glowR, glowG, glowB, 0.06f + 0.03f * MathUtils.sin(time * 2 + i * 1.7f));
            d.region(a.tri, cx + c * len / 2, cy + sn * len / 2, 90, len, ang + 90);
        }
        d.additive(false);
        // vinheta
        d.rgb(0x000000, 0.55f);
        d.region(a.soft, x0, y0, vw * 0.5f, vh * 0.6f, 0);
        d.region(a.soft, x0 + vw, y0, vw * 0.5f, vh * 0.6f, 0);
        d.region(a.soft, x0, y0 + vh, vw * 0.5f, vh * 0.6f, 0);
        d.region(a.soft, x0 + vw, y0 + vh, vw * 0.5f, vh * 0.6f, 0);
        // brasas subindo
        if (leaving < 0 || leaving < 0.3f) fx.ambient(x0, y0, vw, vh, dt, fc);
        fx.draw(d, a, true);
        game.batch.end();
    }

    @Override
    protected void drawFront(float dt) {
        com.badlogic.gdx.graphics.Camera cam = stage.getCamera();
        float vw = stage.getViewport().getWorldWidth(), vh = stage.getViewport().getWorldHeight();
        Draw d = game.draw;
        Assets a = game.assets;
        game.batch.setProjectionMatrix(cam.combined);
        game.batch.begin();
        // faiscas de borda no cartao em foco
        if (leaving < 0 && time > 0.6f) {
            ModeCard fcard = cards.get(focus);
            if (MathUtils.randomBoolean(Math.min(1, dt * 14))) {
                fcard.localToStageCoordinates(tmpV.set(0, 0));
                float w = fcard.getWidth() * 1.07f, h = fcard.getHeight() * 1.07f;
                float bx = tmpV.x - fcard.getWidth() * 0.035f, by = tmpV.y - fcard.getHeight() * 0.035f + 6;
                float u = MathUtils.random(2 * (w + h)), px, py;
                if (u < w) { px = bx + u; py = by; }
                else if (u < w + h) { px = bx + w; py = by + u - w; }
                else if (u < 2 * w + h) { px = bx + 2 * w + h - u; py = by + h; }
                else { px = bx; py = by + 2 * (w + h) - u; }
                fx.spark(px, py, MathUtils.random(-30, 30), MathUtils.random(20, 80), COLORS[focus], 0.7f);
            }
        }
        fx.draw(d, a, false);
        if (flash > 0) {
            d.rgb(0xffffff, Math.min(1, flash) * 0.8f);
            d.rect(cam.position.x - vw / 2 - 20, cam.position.y - vh / 2 - 20, vw + 40, vh + 40);
        }
        game.batch.end();
        cam.position.add(-shx, -shy, 0);
        cam.update();
    }

    /** Particulas da tela: faiscas, confete, brasas e ondas de choque. */
    private static class Fx {
        static final int MAX = 700;
        final float[] x = new float[MAX], y = new float[MAX], vx = new float[MAX], vy = new float[MAX], life = new float[MAX],
                max = new float[MAX], size = new float[MAX], rot = new float[MAX], vrot = new float[MAX], grav = new float[MAX];
        final int[] col = new int[MAX];
        final byte[] type = new byte[MAX]; // 0 faisca, 1 ponto de luz, 2 confete, 3 brasa (atras)
        int n;
        static final int RMAX = 12;
        final float[] rx = new float[RMAX], ry = new float[RMAX], rt = new float[RMAX], rmax = new float[RMAX], rsize = new float[RMAX];
        final int[] rcol = new int[RMAX];
        int rn;
        float emberAcc;

        int add(float px, float py, float pvx, float pvy, float l, float sz, int c, int t) {
            if (n >= MAX) return -1;
            int i = n++;
            x[i] = px;
            y[i] = py;
            vx[i] = pvx;
            vy[i] = pvy;
            life[i] = max[i] = l;
            size[i] = sz;
            col[i] = c;
            type[i] = (byte) t;
            rot[i] = MathUtils.random(360f);
            vrot[i] = 0;
            grav[i] = 0;
            return i;
        }

        void spark(float px, float py, float pvx, float pvy, int c, float l) {
            add(px, py, pvx, pvy, l, MathUtils.random(2f, 4f), SoldierPainter.mix(c, 0xffffff, 0.5f), 1);
        }

        void burst(float px, float py, int count, int c, float vmin, float vmax, float l) {
            for (int k = 0; k < count; k++) {
                float ang = MathUtils.random(360f), v = MathUtils.random(vmin, vmax);
                int cc = MathUtils.randomBoolean(0.35f) ? 0xffffff : SoldierPainter.mix(c, 0xffffff, MathUtils.random(0.1f, 0.5f));
                int i = add(px, py, MathUtils.cosDeg(ang) * v, MathUtils.sinDeg(ang) * v, l * MathUtils.random(0.6f, 1.2f),
                        MathUtils.random(2f, 4.5f), cc, k % 3 == 0 ? 1 : 0);
                if (i >= 0) grav[i] = -300;
            }
        }

        void confetti(float px, float py, int count, int c) {
            int[] pal = {c, 0xffffff, 0xffd54f, SoldierPainter.mix(c, 0xffffff, 0.5f), 0x4fc3f7};
            for (int k = 0; k < count; k++) {
                float ang = MathUtils.random(20f, 160f), v = MathUtils.random(300, 900);
                int i = add(px, py, MathUtils.cosDeg(ang) * v, MathUtils.sinDeg(ang) * v, MathUtils.random(1.2f, 2f),
                        MathUtils.random(6f, 11f), pal[k % pal.length], 2);
                if (i >= 0) {
                    grav[i] = -900;
                    vrot[i] = MathUtils.random(-720f, 720f);
                }
            }
        }

        void ring(float px, float py, int c, float l, float sz) {
            if (rn >= RMAX) return;
            rx[rn] = px;
            ry[rn] = py;
            rt[rn] = 0;
            rmax[rn] = l;
            rsize[rn] = sz;
            rcol[rn] = c;
            rn++;
        }

        void ambient(float x0, float y0, float w, float h, float dt, int c) {
            emberAcc += dt * 28;
            while (emberAcc >= 1) {
                emberAcc -= 1;
                int i = add(x0 + MathUtils.random(w), y0 - 10, MathUtils.random(-15f, 15f), MathUtils.random(30f, 90f),
                        MathUtils.random(4f, 8f), MathUtils.random(1.5f, 4.5f),
                        SoldierPainter.mix(c, 0xffffff, MathUtils.random(0.2f, 0.7f)), 3);
                if (i >= 0) vrot[i] = MathUtils.random(1f, 3f);
            }
        }

        void update(float dt) {
            for (int i = n - 1; i >= 0; i--) {
                life[i] -= dt;
                if (life[i] <= 0) {
                    int l = --n;
                    x[i] = x[l]; y[i] = y[l]; vx[i] = vx[l]; vy[i] = vy[l]; life[i] = life[l]; max[i] = max[l];
                    size[i] = size[l]; rot[i] = rot[l]; vrot[i] = vrot[l]; grav[i] = grav[l]; col[i] = col[l]; type[i] = type[l];
                    continue;
                }
                float drag = type[i] == 2 ? 1.6f : type[i] == 3 ? 0 : 3.2f;
                vx[i] -= vx[i] * Math.min(1, drag * dt);
                vy[i] -= vy[i] * Math.min(1, drag * dt);
                vy[i] += grav[i] * dt;
                if (type[i] == 3) vx[i] += MathUtils.sin(life[i] * vrot[i] + i) * 20 * dt;
                else rot[i] += vrot[i] * dt;
                x[i] += vx[i] * dt;
                y[i] += vy[i] * dt;
            }
            for (int i = rn - 1; i >= 0; i--) {
                rt[i] += dt;
                if (rt[i] >= rmax[i]) {
                    int l = --rn;
                    rx[i] = rx[l]; ry[i] = ry[l]; rt[i] = rt[l]; rmax[i] = rmax[l]; rsize[i] = rsize[l]; rcol[i] = rcol[l];
                }
            }
        }

        void draw(Draw d, Assets a, boolean behind) {
            if (!behind) {
                for (int i = 0; i < rn; i++) {
                    float k = rt[i] / rmax[i], r = Interpolation.pow2Out.apply(k) * rsize[i] * 2;
                    d.rgb(rcol[i], (1 - k) * 0.9f);
                    d.region(a.ring, rx[i], ry[i], r, r, 0);
                }
            }
            d.additive(true);
            for (int i = 0; i < n; i++) {
                boolean back = type[i] == 3;
                if (back != behind || type[i] == 2) continue;
                float k = life[i] / max[i];
                if (type[i] == 0) {
                    float sp = (float) Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i]);
                    float len = Math.max(size[i] * 2, sp * 0.035f);
                    d.rgb(col[i], k);
                    d.line(x[i], y[i], x[i] - vx[i] / sp * len, y[i] - vy[i] / sp * len, size[i] * 0.8f);
                } else {
                    float al = back ? Math.min(1, (1 - k) * 4) * k * 0.7f : k;
                    d.rgb(col[i], al * 0.5f);
                    d.soft(x[i], y[i], size[i] * 3.5f);
                    d.rgb(col[i], al);
                    d.circle(x[i], y[i], size[i] * 0.6f);
                }
            }
            d.additive(false);
            if (!behind) {
                for (int i = 0; i < n; i++) {
                    if (type[i] != 2) continue;
                    float k = Math.min(1, life[i] / max[i] * 3);
                    float flip = Math.abs(MathUtils.cosDeg(rot[i] * 1.7f));
                    d.rgb(col[i], k);
                    d.region(a.white, x[i], y[i], size[i], size[i] * 0.55f * (0.25f + 0.75f * flip), rot[i]);
                }
            }
        }
    }

    /** Titulo com letras caindo no lugar e brilho passando. */
    private class TitleFx extends Actor {
        private final String text;
        private final float[] adv;
        private final float total;
        private float t;

        TitleFx(String text) {
            this.text = text;
            adv = new float[text.length() + 1];
            BitmapFont f = game.assets.big;
            for (int i = 0; i <= text.length(); i++) adv[i] = textWidth(f, text.substring(0, i));
            total = adv[text.length()];
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            t += delta;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            BitmapFont f = game.assets.big;
            Draw d = game.draw;
            float x0 = getX() + getWidth() / 2 - total / 2, y = getY() + getHeight() / 2 + f.getCapHeight() / 2;
            float shine = (t * 0.7f % 1.8f) * (total + 200) - 100;
            int fc = COLORS[focus];
            // faixa embaixo
            float bw = total * Interpolation.pow3Out.apply(MathUtils.clamp((t - 0.2f) / 0.5f, 0, 1));
            d.rgb(fc, parentAlpha);
            d.rect(getX() + getWidth() / 2 - bw / 2, getY() - 2, bw, 4);
            d.rgb(0xffffff, parentAlpha * 0.8f);
            d.rect(getX() + getWidth() / 2 - bw * 0.15f, getY() - 2, bw * 0.3f, 4);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == ' ') continue;
                float lt = MathUtils.clamp((t - i * 0.035f) / 0.35f, 0, 1);
                if (lt <= 0) continue;
                float drop = (1 - Interpolation.bounceOut.apply(lt)) * 40;
                float wave = MathUtils.sin(t * 3 - i * 0.5f) * 2;
                float lx = x0 + adv[i], ly = y + drop + wave;
                String ch = String.valueOf(c);
                f.setColor(0, 0, 0, 0.6f * lt * parentAlpha);
                f.draw(batch, ch, lx + 3, ly - 3);
                float dist = Math.abs(lx - x0 - shine);
                float k = MathUtils.clamp(1 - dist / 60, 0, 1);
                f.setColor(1, 1 - 0.2f * k, 1 - 0.75f * k, lt * parentAlpha);
                f.draw(batch, ch, lx, ly);
            }
            f.setColor(1, 1, 1, 1);
        }
    }

    @Override
    protected void onBack() {
        if (leaving >= 0) return;
        if (kind == SetupScreen.Kind.HOST) go(new MultiplayerScreen(game));
        else go(new MenuScreen(game));
    }

    static String key(ModeType m) {
        switch (m) {
            case TEAM_DEATHMATCH: return "team";
            case CTF: return "ctf";
            case BOMB: return "bomb";
            case SURVIVAL: return "survival";
            case TRAINING: return "training";
            case GUN_GAME: return "gungame";
            case KING_HILL: return "koth";
            case INFECTION: return "infection";
            case BATTLE_ROYALE: return "br";
            case DUEL: return "duel";
            case HIDE_SEEK: return "hideseek";
            case JUGGERNAUT: return "juggernaut";
            default: return "deathmatch";
        }
    }

    static int colorOf(ModeType m) {
        for (int i = 0; i < MODES.length; i++) if (MODES[i] == m) return COLORS[i];
        return COLORS[0];
    }

    private static ModeIcons icons;

    /** Desenha o icone animado do modo (centrado em x,y, tamanho s). Usado tambem na tela de configuracao. */
    static void drawModeIcon(Draw d, Assets a, SoldierPainter painter, ModeType m, float x, float y, float s, float t) {
        drawModeIcon(d, a, painter, m, x, y, s, t, 1);
    }

    static void drawModeIcon(Draw d, Assets a, SoldierPainter painter, ModeType m, float x, float y, float s, float t, float alpha) {
        if (icons == null) icons = new ModeIcons(d, a, new SoldierPainter());
        icons.draw(m, x, y, s, t, alpha);
    }

    /** Faixa de baixo com o nome e a descricao do modo em foco. */
    private class InfoBar extends Table {
        int color = COLORS[0];
        float pulse, time;

        @Override
        public void act(float delta) {
            super.act(delta);
            time += delta;
            pulse = Math.max(0, pulse - delta * 3);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Draw d = game.draw;
            float x = getX(), y = getY(), w = getWidth(), h = getHeight();
            d.rgb(0x000000, 0.5f * parentAlpha);
            box(d, game.assets, x + w / 2, y + h / 2 - 4, w, h);
            d.rgb(0x10141c, 0.94f * parentAlpha);
            box(d, game.assets, x + w / 2, y + h / 2, w, h);
            // faixa de cor do modo, desbotando para a direita
            int steps = 24;
            for (int i = 0; i < steps; i++) {
                float k = i / (float) steps;
                d.rgb(color, (0.22f + pulse * 0.3f) * (1 - k) * parentAlpha);
                d.rect(x + 16 + k * w * 0.55f, y + 8, w * 0.55f / steps, h - 16);
            }
            d.rgb(color, parentAlpha);
            d.rect(x + 8, y + 8, 8, h - 16);
            batch.setColor(1, 1, 1, 1);
            super.draw(batch, parentAlpha);
        }
    }

    /** Cartao de um modo: entra voando, cresce em foco, salta quando escolhido. */
    private class ModeCard extends Actor {
        final int idx;
        float time, hover, press, launch, pop = 1;
        final ClickListener click;

        ModeCard(int idx) {
            this.idx = idx;
            time = -idx * 0.045f;
            click = new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (leaving >= 0) return;
                    if (focus == idx) choose(ModeCard.this);
                    else setFocus(idx);
                }
            };
            addListener(click);
            addListener(new InputListener() {
                @Override
                public boolean mouseMoved(InputEvent event, float x, float y) {
                    if (focus != ModeCard.this.idx && leaving < 0) setFocus(ModeCard.this.idx);
                    return false;
                }
            });
        }

        ModeType mode() {
            return MODES[idx];
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            time += delta;
            boolean f = focus == idx && leaving < 0;
            hover = MathUtils.lerp(hover, f ? 1 : 0, Math.min(1, delta * 12));
            press = MathUtils.lerp(press, click.isPressed() ? 1 : 0, Math.min(1, delta * 20));
            if (leaving >= 0 && chosen == this) launch = Math.min(1, launch + delta * 3.5f);
            pop = Math.min(1, pop + delta * 1.8f);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Draw d = game.draw;
            Assets a = game.assets;
            float appear = MathUtils.clamp(time / 0.5f, 0, 1);
            if (appear <= 0) return;
            float e = Interpolation.swingOut.apply(appear);
            float alpha = Math.min(1, appear * 2) * parentAlpha;
            float w = getWidth(), h = getHeight();
            float cx = getX() + w / 2, cy = getY() + h / 2 - (1 - e) * 140;
            float scale = 0.7f + 0.3f * e + hover * 0.07f - press * 0.05f;
            if (leaving >= 0) {
                if (chosen == this) scale += Interpolation.pow2Out.apply(launch) * 0.12f;
                else {
                    // empurrados para longe do escolhido
                    float k = MathUtils.clamp(leaving / 0.4f, 0, 1);
                    float dx = getX() - chosen.getX(), dy = getY() - chosen.getY();
                    float len = Math.max(1, (float) Math.sqrt(dx * dx + dy * dy));
                    float push = Interpolation.pow2Out.apply(k) * 160;
                    cx += dx / len * push;
                    cy += dy / len * push;
                    alpha *= 1 - k;
                    scale *= 1 - 0.25f * k;
                }
            }
            if (alpha <= 0.01f) return;
            float bob = hover * MathUtils.sin(time * 4) * 3;
            cy += bob + hover * 6;
            float cw = w * scale, ch = h * scale;
            int col = COLORS[idx];
            // sombra
            d.rgb(0x000000, 0.45f * alpha);
            box(d, a, cx + 4, cy - 7, cw, ch);
            // brilho de foco
            if (hover > 0.02f) {
                d.rgb(col, 0.5f * hover * alpha);
                d.soft(cx, cy, cw * 0.85f);
                d.rgb(0xffffff, hover * alpha);
                box(d, a, cx, cy, cw + 8, ch + 8);
            }
            d.rgb(SoldierPainter.OUTLINE, alpha);
            box(d, a, cx, cy, cw + 2, ch + 2);
            if (hover > 0.05f) {
                // cantoneiras de mira se fechando no cartao
                float gap = 14 * (1 - hover) + 9 + 2 * MathUtils.sin(time * 6);
                float L = 22 * scale, th = 4;
                d.rgb(0xffffff, hover * alpha);
                for (int sx = -1; sx <= 1; sx += 2)
                    for (int sy = -1; sy <= 1; sy += 2) {
                        float bx = cx + sx * (cw / 2 + gap), by = cy + sy * (ch / 2 + gap);
                        d.rect(sx < 0 ? bx : bx - L, by - th / 2, L, th);
                        d.rect(bx - th / 2, sy < 0 ? by : by - L, th, L);
                    }
            }
            // fundo: cor do modo escurecendo para baixo
            float x0 = cx - cw / 2, y0 = cy - ch / 2;
            int dark = SoldierPainter.mix(col, 0x0b0e14, 0.72f);
            d.rgb(dark, alpha);
            box(d, a, cx, cy, cw - 4, ch - 4);
            int bands = 24;
            for (int i = 0; i < bands; i++) {
                float k = i / (float) bands;
                d.rgb(col, alpha * (0.85f * (1 - k) * (1 - k)));
                d.rect(x0 + 4, y0 + ch * 0.36f + k * (ch * 0.6f), cw - 8, ch * 0.6f / bands);
            }
            // brilho que passa pelo cartao (sempre no foco, de vez em quando nos outros)
            float period = hover > 0.5f ? 1.6f : 4.5f;
            float sk = ((time + idx * 0.37f) % period) / 0.9f;
            if (sk < 1) {
                float bx = x0 - 40 + sk * (cw + 80);
                sheen(d, bx, 34 * scale, x0 + 6, x0 + cw - 6, y0 + ch * 0.36f, ch * 0.6f, 0.10f * alpha);
                sheen(d, bx + 30 * scale, 10 * scale, x0 + 6, x0 + cw - 6, y0 + ch * 0.36f, ch * 0.6f, 0.14f * alpha);
            }
            // luz atras do icone
            d.rgb(SoldierPainter.mix(col, 0xffffff, 0.35f), (0.25f + 0.25f * hover) * alpha);
            d.soft(cx, y0 + ch * 0.66f, cw * 0.42f);
            // icone
            float iconY = y0 + ch * 0.64f;
            d.rgb(0x000000, 0.3f * alpha);
            d.ellipse(cx, iconY - ch * 0.2f, cw * 0.28f, ch * 0.03f, 0);
            float it = hover > 0.5f ? time * 1.6f : time;
            float popS = pop < 1 ? 1 + 0.28f * MathUtils.sin(pop * MathUtils.PI * 3) * (1 - pop) : 1;
            // fagulhas subindo dentro do cartao
            d.additive(true);
            for (int m = 0; m < 6; m++) {
                float hu = ((idx * 7 + m * 13) % 17) / 17f;
                float u = (time * (0.25f + 0.1f * hu) + hu) % 1f;
                float mx = x0 + 10 + hu * (cw - 20) + MathUtils.sin(time * 2 + m) * 4;
                float my = y0 + ch * 0.36f + u * ch * 0.6f;
                d.rgb(SoldierPainter.mix(col, 0xffffff, 0.6f), MathUtils.sin(u * MathUtils.PI) * (0.35f + 0.4f * hover) * alpha);
                d.circle(mx, my, 1.6f * scale);
            }
            d.additive(false);
            drawModeIcon(d, a, painter, MODES[idx], cx, iconY, 92 * scale * (1 + hover * 0.08f) * popS, it, alpha);
            // faixa de texto
            d.rgb(0x000000, 0.35f * alpha);
            d.rect(x0 + 4, y0 + 4, cw - 8, ch * 0.33f);
            d.rgb(col, alpha);
            d.rect(x0 + 4, y0 + ch * 0.36f - 4, cw - 8, 4);
            String name = I18n.t("mode_" + key(MODES[idx]));
            BitmapFont f = a.normal;
            float fs = f.getScaleX();
            float target = Math.min(1f, (cw - 18) / textWidth(f, name)) * scale;
            f.getData().setScale(fs * target);
            f.setColor(1, 1, 1, alpha);
            f.draw(batch, name, cx, y0 + ch * 0.27f, 0, Align.center, false);
            f.getData().setScale(fs);
            // etiqueta
            String tag = TAGS[I18n.pt ? 0 : 1][idx];
            BitmapFont sm = a.small;
            float ss = sm.getScaleX();
            sm.getData().setScale(ss * 0.7f * scale);
            float tw = textWidth(sm, tag) + 16 * scale;
            d.rgb(col, alpha);
            box(d, a, cx, y0 + ch * 0.1f, tw, 20 * scale);
            sm.setColor(1, 1, 1, alpha);
            sm.draw(batch, tag, cx, y0 + ch * 0.1f + 7 * scale, 0, Align.center, false);
            sm.getData().setScale(ss);
            sm.setColor(1, 1, 1, 1);
            f.setColor(1, 1, 1, 1);
            // clarao ao escolher
            if (launch > 0) {
                d.rgb(0xffffff, (1 - launch) * 0.7f * alpha);
                box(d, a, cx, cy, cw, ch);
                d.rgb(col, (1 - launch) * 0.6f);
                d.region(a.ring, cx, cy, cw * (1 + launch * 1.4f), cw * (1 + launch * 1.4f), 0);
            }
            batch.setColor(1, 1, 1, 1);
        }

        private void sheen(Draw d, float x, float w, float minX, float maxX, float y, float h, float alpha) {
            float l = Math.max(minX, x), r = Math.min(maxX, x + w);
            if (r <= l) return;
            d.rgb(0xffffff, alpha);
            d.rect(l, y, r - l, h);
        }
    }

    private final SoldierPainter painter = new SoldierPainter();

    /** retangulo arredondado sem esticar os cantos */
    static void box(Draw d, Assets a, float cx, float cy, float w, float h) {
        Draw.patch(d, a.rrect, cx - w / 2, cy - h / 2, w, h, 20);
    }
    private static final com.badlogic.gdx.graphics.g2d.GlyphLayout GL = new com.badlogic.gdx.graphics.g2d.GlyphLayout();

    private static float textWidth(BitmapFont f, String s) {
        GL.setText(f, s);
        return GL.width;
    }
}
