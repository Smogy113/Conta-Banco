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
import com.brigadajato.render.Icons;
import com.brigadajato.render.SoldierPainter;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.WeaponType;

/**
 * Primeiro passo de "criar partida": escolher o modo em cartoes grandes e animados.
 * Depois abre a {@link SetupScreen} so com o resto das configuracoes.
 */
public class ModeSelectScreen extends BaseScreen {
    static final ModeType[] MODES = {ModeType.DEATHMATCH, ModeType.TEAM_DEATHMATCH, ModeType.CTF, ModeType.BOMB, ModeType.SURVIVAL,
            ModeType.GUN_GAME, ModeType.KING_HILL, ModeType.INFECTION, ModeType.BATTLE_ROYALE, ModeType.DUEL, ModeType.HIDE_SEEK};
    private static final int[] COLORS = {0xd84315, 0x1565c0, 0x2e7d32, 0xb71c1c, 0x6d4c41, 0xf9a825, 0x7b1fa2, 0x558b2f,
            0x00838f, 0x455a64, 0xad1457};
    private static final String[][] TAGS = {
            {"SOLO", "TIMES", "TIMES", "RODADAS", "CO-OP", "SOLO", "TIMES", "SOLO", "1 VIDA", "1 x 1", "TIMES"},
            {"SOLO", "TEAMS", "TEAMS", "ROUNDS", "CO-OP", "SOLO", "TEAMS", "SOLO", "1 LIFE", "1 v 1", "TEAMS"}};

    private final SetupScreen.Kind kind;
    private final Array<ModeCard> cards = new Array<>();
    private final Label infoName, infoDesc;
    private final InfoBar info;
    private int focus;
    private float leaving = -1;
    private ModeCard chosen;

    public ModeSelectScreen(BrigadaJatoGame game, SetupScreen.Kind kind) {
        super(game);
        this.kind = kind;
        ModeType last = game.settings.lastMatch.mode;
        for (int i = 0; i < MODES.length; i++) if (MODES[i] == last) focus = i;

        Table header = new Table();
        header.add(button(I18n.t("back"), "gray-small", this::onBack)).width(170).height(52).left();
        Label title = label(I18n.pt ? "ESCOLHA O MODO" : "CHOOSE A MODE", "big");
        header.add(title).expandX().center();
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
        if (i != focus) game.audio.ui("ui_click");
        focus = i;
        infoName.setText(I18n.t("mode_" + key(MODES[i])));
        infoDesc.setText(I18n.t("desc_" + key(MODES[i])));
        info.color = COLORS[i];
        info.pulse = 1;
    }

    private void choose(ModeCard c) {
        if (leaving >= 0) return;
        game.audio.ui("ui_click");
        chosen = c;
        focus = c.idx;
        leaving = 0;
        root.setTouchable(Touchable.disabled);
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
            default: return "deathmatch";
        }
    }

    static int colorOf(ModeType m) {
        for (int i = 0; i < MODES.length; i++) if (MODES[i] == m) return COLORS[i];
        return COLORS[0];
    }

    /** Desenha o icone animado do modo (centrado em x,y, tamanho s). Usado tambem na tela de configuracao. */
    static void drawModeIcon(Draw d, Assets a, SoldierPainter painter, ModeType m, float x, float y, float s, float t) {
        switch (m) {
            case DEATHMATCH: {
                Icons.crosshair(d, x, y, s * 1.15f, 1, 0xffffff, 0.35f + 0.15f * MathUtils.sin(t * 3));
                Icons.skull(d, x, y + MathUtils.sin(t * 2.4f) * s * 0.04f, s * 0.8f, 0xeceff1);
                break;
            }
            case TEAM_DEATHMATCH: {
                float b = MathUtils.sin(t * 3) * s * 0.05f;
                Icons.shield(d, x - s * 0.24f, y + b, s * 0.7f, 0xe53935);
                Icons.shield(d, x + s * 0.24f, y - b, s * 0.7f, 0x1e88e5);
                break;
            }
            case CTF:
                Icons.flag(d, x, y, s * 0.95f, 0xffffff, t);
                break;
            case BOMB: {
                float pulse = 1 + 0.06f * Math.max(0, MathUtils.sin(t * 7));
                Icons.bomb(d, x, y, s * 0.85f * pulse, ((int) (t * 3)) % 2 == 0);
                break;
            }
            case SURVIVAL: {
                for (int i = 0; i < 3; i++) {
                    float k = ((t * 0.6f + i / 3f) % 1f);
                    d.rgb(0xffffff, (1 - k) * 0.45f);
                    d.region(a.ring, x, y, s * (0.5f + k * 0.9f), s * (0.5f + k * 0.9f), 0);
                }
                float beat = 1 + 0.1f * Math.max(0, MathUtils.sin(t * 6));
                Icons.heart(d, x, y, s * 0.7f * beat, 0xef5350);
                break;
            }
            case GUN_GAME: {
                WeaponType[] ws = {WeaponType.PISTOL, WeaponType.SMG, WeaponType.SHOTGUN, WeaponType.RIFLE, WeaponType.SNIPER, WeaponType.ROCKET};
                int i = ((int) (t / 0.9f)) % ws.length;
                float k = (t / 0.9f) % 1f;
                float pop = k < 0.15f ? Interpolation.swingOut.apply(k / 0.15f) : 1;
                painter.drawGun(d, ws[i], x - s * 0.05f, y, 0, s * 0.045f * pop, 1);
                for (int j = 0; j < ws.length; j++) {
                    d.rgb(j <= i ? 0xffffff : 0x000000, j <= i ? 0.9f : 0.35f);
                    d.circle(x - s * 0.4f + j * s * 0.16f, y - s * 0.46f, s * 0.045f);
                }
                break;
            }
            case KING_HILL: {
                d.rgb(0x000000, 0.35f);
                d.ellipse(x, y - s * 0.32f, s * 0.6f, s * 0.14f, 0);
                float k = 0.5f + 0.5f * MathUtils.sin(t * 3);
                d.rgb(0xffeb3b, 0.25f + 0.35f * k);
                d.region(a.ring, x, y - s * 0.32f, s * (1.0f + 0.15f * k), s * (0.26f + 0.04f * k), 0);
                crown(d, x, y + s * 0.02f + MathUtils.sin(t * 2) * s * 0.04f, s * 0.7f);
                break;
            }
            case INFECTION: {
                float w = MathUtils.sin(t * 5) * 6;
                d.rgb(0x76ff03, 0.25f + 0.15f * MathUtils.sin(t * 4));
                d.soft(x, y, s * 0.55f);
                Icons.skull(d, x + MathUtils.sin(t * 9) * s * 0.015f, y, s * 0.8f, 0x9ccc65);
                d.rgb(0x33691e, 1);
                d.circle(x + s * 0.3f, y - s * 0.3f + w * 0.002f * s, s * 0.06f);
                break;
            }
            case BATTLE_ROYALE: {
                float k = (t * 0.35f) % 1f;
                float r = s * (1.05f - 0.55f * k);
                d.rgb(0x4fc3f7, 0.25f);
                d.circle(x, y, r * 0.5f);
                d.rgb(0x80deea, 0.9f);
                d.region(a.ring, x, y, r, r, 0);
                Icons.person(d, x, y - s * 0.02f, s * 0.42f, 0xffffff);
                break;
            }
            case DUEL: {
                float k = Math.abs(MathUtils.sin(t * 2.5f));
                float off = s * (0.3f - 0.07f * k);
                pistolFacing(d, painter, x - off, y, s, false);
                pistolFacing(d, painter, x + off, y, s, true);
                d.rgb(0xffeb3b, k * 0.8f);
                Icons.bolt(d, x, y + s * 0.3f, s * 0.32f, 0xffeb3b);
                break;
            }
            case HIDE_SEEK: {
                d.rgb(0x15151c, 1);
                d.rect(x - s * 0.28f, y - s * 0.4f, s * 0.56f, s * 0.5f);
                d.rgb(0xb07a3c, 1);
                d.rect(x - s * 0.23f, y - s * 0.35f, s * 0.46f, s * 0.4f);
                d.rgb(0x15151c, 1);
                d.rect(x - s * 0.23f, y - s * 0.17f, s * 0.46f, s * 0.04f);
                d.rgb(0xffffff, 1);
                d.circle(x - s * 0.08f, y - s * 0.02f, s * 0.035f);
                d.circle(x + s * 0.08f, y - s * 0.02f, s * 0.035f);
                float mx = x + MathUtils.sin(t * 1.7f) * s * 0.3f, my = y + s * 0.18f + MathUtils.cos(t * 2.3f) * s * 0.12f;
                Icons.magnifier(d, mx, my, s * 0.5f, 0xffffff, 1);
                break;
            }
            default:
                Icons.star(d, x, y, s * 0.8f, 0xffffff);
        }
    }

    private static void pistolFacing(Draw d, SoldierPainter painter, float x, float y, float s, boolean flip) {
        painter.drawGun(d, WeaponType.MAGNUM, 0, x, y, flip ? 10 : -10, s * 0.032f, 1, flip ? -1 : 1);
    }

    private static void crown(Draw d, float x, float y, float s) {
        for (int pass = 0; pass < 2; pass++) {
            float o = pass == 0 ? s * 0.07f : 0;
            d.rgb(pass == 0 ? 0x15151c : 0xffca28, 1);
            d.rect(x - s * 0.4f - o, y - s * 0.22f - o, s * 0.8f + o * 2, s * 0.24f + o * 2);
            for (int i = -1; i <= 1; i++)
                d.region(d.a.tri, x + i * s * 0.28f, y + s * 0.12f, s * 0.3f + o * 2, s * 0.4f + o * 2, 0);
        }
        d.rgb(0xe53935, 1);
        d.circle(x, y - s * 0.1f, s * 0.06f);
        d.rgb(0xffffff, 0.4f);
        d.rect(x - s * 0.36f, y - s * 0.02f, s * 0.72f, s * 0.04f);
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
        float time, hover, press, launch;
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
                    float k = MathUtils.clamp(leaving / 0.35f, 0, 1);
                    alpha *= 1 - k;
                    cy -= Interpolation.pow2In.apply(k) * 80;
                    scale *= 1 - 0.15f * k;
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
            drawModeIcon(d, a, painter, MODES[idx], cx, iconY, 92 * scale * (1 + hover * 0.08f), it);
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
