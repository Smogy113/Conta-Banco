package com.brigadajato.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.brigadajato.BrigadaJatoGame;
import com.brigadajato.I18n;
import com.brigadajato.render.Draw;
import com.brigadajato.render.SoldierPainter;
import com.brigadajato.world.Look;
import com.brigadajato.world.MatchConfig;
import com.brigadajato.world.ModeType;
import com.brigadajato.world.WeaponType;

import java.util.Random;

/** Menu principal. */
public class MenuScreen extends BaseScreen {
    static final String[][] TIPS = {
            {"Agachado você atira com mais precisão.", "Crouching makes your shots more accurate."},
            {"O jetpack trava quando esvazia: pouse para recarregar.", "The jetpack locks when empty: land to refuel."},
            {"Tiros na cabeça dão dano extra (sniper dá o dobro).", "Headshots deal extra damage (sniper: double)."},
            {"Segure o joystick direito na borda para atirar sem parar.", "Hold the right stick at the edge to keep firing."},
            {"Minas grudam nas paredes e explodem quando alguém chega perto.", "Mines stick to walls and blow up when someone gets close."},
            {"Na Base Lunar a gravidade é baixa: dá para voar muito mais longe.", "On the Moon Base gravity is low: you fly much farther."},
            {"Soco perto do inimigo tira mais da metade da vida.", "A melee hit takes more than half of the enemy's health."},
            {"Pegue armas do chão: quem morre derruba a arma.", "Pick up weapons: whoever dies drops their gun."},
            {"Personalize seu soldado em Soldado: chapéus, máscaras, jetpacks e mais.", "Customize your soldier: hats, masks, jetpacks and more."},
            {"Crie uma sala no Wi-Fi e jogue com os amigos sem internet.", "Create a Wi-Fi room and play with friends offline."}};

    private final Label tip;
    private float tipTimer;
    private int tipIdx;

    public MenuScreen(BrigadaJatoGame game) {
        this(game, null);
    }

    public MenuScreen(BrigadaJatoGame game, String notice) {
        super(game);
        game.settings.applyFps();
        stage.addActor(new Shade());
        root.toFront();

        // ---- coluna da esquerda: logo + lista de opcoes no estilo de console
        Table left = new Table();
        left.add(new Logo()).size(465, 165).left().padBottom(18).row();
        float w = 420;
        left.add(new Item(I18n.t("play"), 0, 0xeaa233, true, () -> go(new ModeSelectScreen(game, SetupScreen.Kind.OFFLINE)))).width(w + 30).height(66).left().padBottom(10).row();
        left.add(new Item(I18n.t("multiplayer"), 1, 0x4f9be8, false, () -> go(new MultiplayerScreen(game)))).width(w).height(48).left().padBottom(7).row();
        left.add(new Item(I18n.t("training"), 2, 0x6cc05a, false, () -> {
            MatchConfig cfg = new MatchConfig();
            cfg.mode = ModeType.TRAINING;
            cfg.applyModeDefaults();
            cfg.bots = 0;
            go(GameScreen.offline(game, cfg));
        })).width(w).height(48).left().padBottom(7).row();
        left.add(new Item(I18n.t("soldier"), 3, 0xb07ee0, false, () -> go(new CustomizeScreen(game)))).width(w).height(48).left().padBottom(7).row();
        left.add(new Item(I18n.t("settings"), 4, 0x9fb0bd, false, () -> go(new SettingsScreen(game)))).width(w).height(48).left().padBottom(7).row();
        left.add(new Item(I18n.t("achievements") + "  " + game.achievements.unlockedCount() + "/" + com.brigadajato.Achievements.DEFS.length, 5, 0xffd04a, false,
                () -> go(new AchievementsScreen(game)))).width(w).height(48).left().padBottom(7).row();
        left.add(new Item(I18n.t("quit"), 6, 0xe0594c, false, () -> Gdx.app.exit())).width(190).height(42).left().row();
        if (notice != null) left.add(label(notice, "yellow")).left().padTop(12).row();

        // ---- direita: seu soldado num pedestal com luz, e a ficha
        Table right = new Table();
        Stack podium = new Stack();
        podium.add(new Spot());
        SoldierPreview preview = new SoldierPreview(game, game.settings.look);
        preview.autoPose = true;
        podium.add(preview);
        right.add(podium).size(260, 310).row();
        Table card = new Table(skin);
        card.setBackground("plate");
        card.pad(10, 22, 12, 22);
        card.add(label(game.settings.name, "big")).row();
        card.add(label(I18n.f("level", game.settings.level()) + (game.settings.level() >= com.brigadajato.Progression.MAX_LEVEL ? " (MAX)" : "")
                + "   ·   " + I18n.f("stat_kills", game.settings.totalKills), "gray")).padTop(2).row();
        if (com.brigadajato.Progression.TEST_BUILD) card.add(label(I18n.t("unlocked_all"), "yellow-small")).padTop(2).row();
        right.add(card).padTop(14);

        root.add(left).expand().left().top().padLeft(56).padTop(26);
        root.add(right).expand().right().padRight(80);
        root.row();
        tip = label("", "small");
        Table bottom = new Table(skin);
        bottom.setBackground("chip");
        bottom.pad(6, 14, 6, 16);
        Label tag = label(I18n.pt ? "DICA" : "TIP", "yellow-small");
        bottom.add(tag).padRight(12);
        bottom.add(tip);
        root.add(bottom).colspan(2).left().padLeft(56).padBottom(16);
        tipIdx = new Random().nextInt(TIPS.length);
        showTip();
        stage.addActor(new FlyBy());
    }

    private void showTip() {
        tip.setText(TIPS[tipIdx][I18n.pt ? 0 : 1]);
    }

    @Override
    public void render(float delta) {
        tipTimer += delta;
        if (tipTimer > 6f) {
            tipTimer = 0;
            tipIdx = (tipIdx + 1) % TIPS.length;
            showTip();
        }
        super.render(delta);
    }

    @Override
    protected void onBack() {
        Gdx.app.exit();
    }

    // ------------------------------------------------------------ icones em pixel art (11x11)
    private static final String[][] ICONS = {
            {"##.........", "####.......", "######.....", "########...", "##########.", "###########", "##########.", "########...", "######.....", "####.......", "##........."},
            {"..#######..", ".#.......#.", "#..#####..#", "..#.....#..", ".#..###..#.", "...#...#...", "....###....", "...........", ".....#.....", "....###....", ".....#....."},
            {"...#####...", "..#.....#..", ".#..###..#.", "#..#...#..#", "#.#..#..#.#", "#.#.###.#.#", "#.#..#..#.#", "#..#...#..#", ".#..###..#.", "..#.....#..", "...#####..."},
            {"...#####...", "..#######..", ".#########.", ".##.....##.", "..#.#.#.#..", "..#.....#..", "...#####...", "..#######..", ".#########.", "###########", "###########"},
            {"....###....", ".#.#####.#.", "..#######..", ".####.####.", "####...####", "###.....###", "####...####", ".####.####.", "..#######..", ".#.#####.#.", "....###...."},
            {"###########", "#.#######.#", "#.#######.#", ".#.#####.#.", "...#####...", "....###....", ".....#.....", ".....#.....", "....###....", "...#####...", "...#####..."},
            {"#######....", "#.....#....", "#.....#..#.", "#.....#.##.", "#...######.", "#...#######", "#...######.", "#.....#.##.", "#.....#..#.", "#.....#....", "#######...."}};
    private static Texture iconTex;
    private static TextureRegion[] icons;

    /** monta a textura dos icones: branco com contorno escuro (o desenho e tingido na hora) */
    private static void loadIcons() {
        if (iconTex != null) return;
        int S = 13;
        Pixmap pm = new Pixmap(S * ICONS.length, S, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        for (int i = 0; i < ICONS.length; i++) {
            String[] ic = ICONS[i];
            for (int y = 0; y < 11; y++)
                for (int x = 0; x < 11; x++) {
                    if (ic[y].charAt(x) != '#') continue;
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int px = i * S + 1 + x + dx, py = 1 + y + dy;
                            if ((pm.getPixel(px, py) & 0xff) == 0) pm.drawPixel(px, py, 0x120e18ff);
                        }
                }
            for (int y = 0; y < 11; y++)
                for (int x = 0; x < 11; x++) if (ic[y].charAt(x) == '#') pm.drawPixel(i * S + 1 + x, 1 + y, 0xffffffff);
        }
        iconTex = new Texture(pm);
        iconTex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        icons = new TextureRegion[ICONS.length];
        for (int i = 0; i < ICONS.length; i++) icons[i] = new TextureRegion(iconTex, i * S, 0, S, S);
    }

    /** escurece a esquerda (onde fica o menu) e embaixo, deixando o jogo de fundo aparecer na direita */
    private class Shade extends Actor {
        @Override
        public void draw(Batch batch, float parentAlpha) {
            Draw d = game.draw;
            float W = getStage().getViewport().getWorldWidth(), H = getStage().getViewport().getWorldHeight();
            int n = 24;
            float band = 760f / n;
            for (int i = 0; i < n; i++) {
                float t = i / (float) n;
                d.color(0.03f, 0.03f, 0.06f, 0.85f * (1 - t) * (1 - t) + 0.12f);
                d.rect(i * band, 0, band + 1, H);
            }
            for (int i = 0; i < 10; i++) {
                float t = i / 10f;
                d.color(0.02f, 0.02f, 0.04f, 0.5f * (1 - t) * (1 - t));
                d.rect(0, i * 9, W, 9);
                d.rect(0, H - (i + 1) * 9, W, 9);
            }
            batch.setColor(1, 1, 1, 1);
        }
    }

    /** Logo em pixel art com fagulhas saindo do jato. */
    private class Logo extends Actor {
        private float time;
        private final float[] sx = new float[14], sy = new float[14], sl = new float[14];

        @Override
        public void act(float delta) {
            super.act(delta);
            time += delta;
            for (int i = 0; i < sx.length; i++) {
                sl[i] -= delta;
                if (sl[i] <= 0) {
                    sl[i] = 0.35f + MathUtils.random(0.5f);
                    sx[i] = 8 + MathUtils.random(4f);
                    sy[i] = 13 + MathUtils.random(-1.5f, 1.5f);
                }
                sx[i] -= delta * (9 + i * 0.6f);
                sy[i] += MathUtils.sin(time * 9 + i) * delta * 3;
            }
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Texture logo = game.assets.logo;
            if (logo == null) return;
            Draw d = game.draw;
            float k = 5;
            float bob = Math.round(MathUtils.sin(time * 1.8f) * 1.4f) * k * 0.5f;
            float x = getX(), y = getY() + bob;
            // brilho quente atras do jato
            d.additive(true);
            d.color(1f, 0.6f, 0.2f, (0.16f + 0.06f * MathUtils.sin(time * 14)) * parentAlpha);
            d.soft(x + 30 * k, y + 13 * k, 90);
            d.additive(false);
            // fagulhas (quadradinhos do tamanho de um pixel do logo)
            for (int i = 0; i < sx.length; i++) {
                if (sx[i] > 7 || sx[i] < -6 || sl[i] <= 0) continue;
                float t = MathUtils.clamp(sl[i] / 0.6f, 0, 1);
                int c = t > 0.6f ? 0xffe27a : (t > 0.3f ? 0xf6a028 : 0xc8461e);
                d.rgb(c, parentAlpha * Math.min(1, t * 2));
                d.rect(x + Math.round(sx[i]) * k, y + Math.round(sy[i]) * k, k, k);
            }
            batch.setColor(1, 1, 1, parentAlpha);
            batch.draw(logo, x, y, logo.getWidth() * k, logo.getHeight() * k);
            batch.setColor(1, 1, 1, 1);
        }
    }

    /** Opcao do menu: placa escura com faixa colorida, icone e texto; realca e desliza ao tocar. */
    private class Item extends Actor {
        private final String text;
        private final int icon, color;
        private final boolean primary;
        private float hover, press, time;
        private final ClickListener click;

        Item(String text, int icon, int color, boolean primary, Runnable action) {
            this.text = text.toUpperCase(java.util.Locale.ROOT);
            this.icon = icon;
            this.color = color;
            this.primary = primary;
            loadIcons();
            click = new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.audio.ui("ui_click");
                    action.run();
                }
            };
            addListener(click);
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            time += delta;
            boolean on = click.isOver() || click.isPressed();
            hover += ((on ? 1 : 0) - hover) * Math.min(1, delta * 14);
            press += ((click.isPressed() ? 1 : 0) - press) * Math.min(1, delta * 20);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Draw d = game.draw;
            float slide = Math.round(hover * 10), sink = Math.round(press * 3);
            float x = getX() + slide, y = getY() - sink, w = getWidth(), h = getHeight();
            if (primary) {
                Drawable bg = skin.get("default", TextButton.TextButtonStyle.class).up;
                batch.setColor(1, 1, 1, parentAlpha);
                bg.draw(batch, x, y, w, h);
                if (hover > 0.01f) {
                    d.color(1, 1, 1, 0.12f * hover * parentAlpha);
                    d.rect(x + 3, y + 9, w - 6, h - 12);
                }
            } else {
                batch.setColor(1, 1, 1, parentAlpha);
                skin.getDrawable("plate").draw(batch, x, y, w, h);
                d.rgb(color, (0.08f + 0.16f * hover) * parentAlpha);
                d.rect(x + 3, y + 3, w - 6, h - 6);
                d.rgb(color, parentAlpha);
                d.rect(x + 3, y + 6, 6, h - 12);
            }
            // seta piscando quando selecionado
            if (hover > 0.05f) {
                float bl = ((int) (time * 4)) % 2 == 0 ? 1 : 0.6f;
                d.rgb(primary ? 0xffe27a : color, hover * bl * parentAlpha);
                d.region(game.assets.tri, getX() - 14 + slide * 0.4f, y + h / 2, 14, 14, -90);
            }
            // icone
            float is = primary ? 39 : 33;
            float ix = x + (primary ? 16 : 20), iy = y + (h - is) / 2 + (primary ? 2 : 0);
            batch.setColor(1, 1, 1, parentAlpha);
            if (primary) batch.setColor(1f, 0.97f, 0.88f, parentAlpha);
            else {
                int c = SoldierPainter.mix(color, 0xffffff, 0.25f * hover);
                batch.setColor(((c >> 16) & 0xff) / 255f, ((c >> 8) & 0xff) / 255f, (c & 0xff) / 255f, parentAlpha);
            }
            batch.draw(icons[icon], ix, iy, is, is);
            // texto
            BitmapFont f = primary ? game.assets.big : game.assets.normal;
            f.setColor(1, 1, 1, parentAlpha);
            float ty = y + h / 2 + f.getCapHeight() / 2 + (primary ? 3 : 1);
            f.draw(batch, text, ix + is + 16, ty);
            f.setColor(1, 1, 1, 1);
            batch.setColor(1, 1, 1, 1);
        }
    }

    /** Luz de palco e pedestal atras do seu soldado. */
    private class Spot extends Actor {
        private float time;

        @Override
        public void act(float delta) {
            super.act(delta);
            time += delta;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Draw d = game.draw;
            float cx = getX() + getWidth() / 2, by = getY() + 8;
            d.additive(true);
            d.color(1f, 0.85f, 0.55f, (0.18f + 0.03f * MathUtils.sin(time * 2)) * parentAlpha);
            d.soft(cx, by + 120, 170);
            d.color(1f, 0.9f, 0.7f, 0.1f * parentAlpha);
            d.soft(cx, by + 10, 120);
            d.additive(false);
            // pedestal de pedra em pixels (unidade de 3)
            float pw = 192, ph = 18, px = cx - pw / 2, py = by - ph + 4;
            d.rgb(0x120e18, parentAlpha);
            d.rect(px + 3, py - 3, pw - 6, ph + 6);
            d.rect(px, py, pw, ph);
            d.rgb(0x4a5260, parentAlpha);
            d.rect(px + 3, py + 3, pw - 6, ph - 6);
            d.rgb(0x6b7585, parentAlpha);
            d.rect(px + 3, py + ph - 6, pw - 6, 3);
            d.rgb(0x353b46, parentAlpha);
            d.rect(px + 3, py + 3, pw - 6, 3);
            for (int i = 1; i < 6; i++) {
                d.rgb(0x353b46, parentAlpha);
                d.rect(px + i * pw / 6, py + 3, 3, ph - 9);
            }
            batch.setColor(1, 1, 1, 1);
        }
    }

    /** Um soldado aleatorio passa voando de vez em quando. */
    private class FlyBy extends Actor {
        private final SoldierPainter painter = new SoldierPainter();
        private final SoldierPainter.Pose pose = new SoldierPainter.Pose();
        private final Matrix4 saved = new Matrix4(), tmp = new Matrix4();
        private final Random rng = new Random();
        private float x = -200, y, vx, wait = 2f, time;
        private int dir = 1;

        FlyBy() {
            pose.look = Look.random(rng);
        }

        @Override
        public void act(float delta) {
            time += delta;
            if (wait > 0) {
                wait -= delta;
                if (wait <= 0) {
                    dir = rng.nextBoolean() ? 1 : -1;
                    float w = getStage().getViewport().getWorldWidth();
                    x = dir > 0 ? -80 : w + 80;
                    y = 420 + rng.nextFloat() * 200;
                    vx = (260 + rng.nextFloat() * 160) * dir;
                    pose.look = Look.random(rng);
                    WeaponType[] all = WeaponType.values();
                    pose.weapon = all[rng.nextInt(all.length)];
                }
                return;
            }
            x += vx * delta;
            y += MathUtils.sin(time * 3) * 20 * delta;
            float w = getStage().getViewport().getWorldWidth();
            if (x < -150 || x > w + 150) wait = 5 + rng.nextFloat() * 6;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            if (wait > 0) return;
            Draw d = game.draw;
            saved.set(batch.getTransformMatrix());
            tmp.set(saved).translate(x, y, 0).scale(1.6f, 1.6f, 1);
            batch.setTransformMatrix(tmp);
            int fc = pose.look.flameColor(time);
            d.additive(true);
            for (int k = 0; k < 6; k++) {
                d.rgb(fc, 0.8f - k * 0.12f);
                d.soft(-dir * 10 - dir * k * 3, 10 - k * 5, 6 - k * 0.7f);
            }
            d.additive(false);
            pose.x = 0;
            pose.y = 0;
            pose.facing = dir;
            pose.aim = dir > 0 ? 0.15f : MathUtils.PI - 0.15f;
            pose.onGround = false;
            pose.jetting = true;
            pose.time = time;
            pose.vx = vx;
            pose.alpha = parentAlpha;
            painter.draw(d, pose);
            batch.setTransformMatrix(saved);
            batch.setColor(1, 1, 1, 1);
        }
    }
}
