package com.brigadajato.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
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
    private static final String[][] TIPS = {
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
        Table left = new Table();
        left.add(new TitleActor()).size(640, 190).left().padBottom(8).row();
        float bw = 440, bh = 64;
        left.add(button(I18n.t("play"), "default", () -> go(new ModeSelectScreen(game, SetupScreen.Kind.OFFLINE)))).width(bw).height(bh).padBottom(10).row();
        left.add(button(I18n.t("multiplayer"), "blue", () -> go(new MultiplayerScreen(game)))).width(bw).height(bh).padBottom(10).row();
        left.add(button(I18n.t("training"), "green", () -> {
            MatchConfig cfg = new MatchConfig();
            cfg.mode = ModeType.TRAINING;
            cfg.applyModeDefaults();
            cfg.bots = 0;
            go(GameScreen.offline(game, cfg));
        })).width(bw).height(bh).padBottom(10).row();
        Table row = new Table();
        row.add(button(I18n.t("soldier"), "purple-small", () -> go(new CustomizeScreen(game)))).width(bw / 2 - 5).height(bh).padRight(10);
        row.add(button(I18n.t("settings"), "gray-small", () -> go(new SettingsScreen(game)))).width(bw / 2 - 5).height(bh);
        left.add(row).padBottom(10).row();
        left.add(button(I18n.t("achievements") + "  " + game.achievements.unlockedCount() + "/" + com.brigadajato.Achievements.DEFS.length, "default-small",
                () -> go(new AchievementsScreen(game)))).width(bw).height(50).padBottom(10).row();
        left.add(button(I18n.t("quit"), "red-small", () -> Gdx.app.exit())).width(150).height(46).left().row();
        if (notice != null) left.add(label(notice, "yellow")).left().padTop(12).row();

        Table right = new Table();
        SoldierPreview preview = new SoldierPreview(game, game.settings.look);
        preview.autoPose = true;
        right.add(preview).size(240, 290).row();
        Table card = new Table(skin);
        card.setBackground("chip");
        card.pad(8, 18, 8, 18);
        card.add(label(game.settings.name, "big")).row();
        card.add(label(I18n.f("level", game.settings.level()) + (game.settings.level() >= com.brigadajato.Progression.MAX_LEVEL ? " (MAX)" : "")
                + "   ·   " + I18n.f("stat_kills", game.settings.totalKills), "gray")).row();
        if (com.brigadajato.Progression.TEST_BUILD) card.add(label(I18n.t("unlocked_all"), "small")).row();
        right.add(card).padTop(4);

        root.add(left).expand().left().padLeft(70);
        root.add(right).expand().right().padRight(90);
        root.row();
        tip = label("", "small");
        tip.setAlignment(Align.center);
        Table bottom = new Table(skin);
        bottom.setBackground("chip");
        bottom.pad(6, 18, 6, 18);
        bottom.add(tip);
        root.add(bottom).colspan(2).padBottom(14);
        tipIdx = new Random().nextInt(TIPS.length);
        showTip();
        stage.addActor(new FlyBy());
    }

    private void showTip() {
        tip.setText((I18n.pt ? "Dica: " : "Tip: ") + TIPS[tipIdx][I18n.pt ? 0 : 1]);
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

    /** Logo do jogo, levemente inclinado, com faixa de alerta. */
    private class TitleActor extends Actor {
        private final Matrix4 saved = new Matrix4(), tmp = new Matrix4();
        private float time;

        @Override
        public void act(float delta) {
            super.act(delta);
            time += delta;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Draw d = game.draw;
            saved.set(batch.getTransformMatrix());
            float bounce = MathUtils.sin(time * 2f) * 3;
            tmp.set(saved).translate(getX() + 10, getY() + 40 + bounce, 0).rotate(0, 0, 1, 4);
            batch.setTransformMatrix(tmp);
            d.color(1, 1, 1, parentAlpha);
            Draw.tiled(d, game.assets.uiStripes, 0, -6, 560, 14);
            game.assets.huge.getData().setScale(1.25f / game.assets.fontScale);
            game.assets.huge.setColor(0, 0, 0, 0.45f * parentAlpha);
            game.assets.huge.draw(batch, "BRIGADA", 8, 150);
            game.assets.huge.setColor(1f, 0.78f, 0.1f, parentAlpha);
            game.assets.huge.draw(batch, "BRIGADA", 2, 156);
            game.assets.huge.getData().setScale(0.95f / game.assets.fontScale);
            game.assets.huge.setColor(0, 0, 0, 0.45f * parentAlpha);
            game.assets.huge.draw(batch, "JATO", 306, 84);
            game.assets.huge.setColor(1f, 0.42f, 0.13f, parentAlpha);
            game.assets.huge.draw(batch, "JATO", 300, 90);
            game.assets.huge.getData().setScale(1f / game.assets.fontScale);
            game.assets.huge.setColor(1, 1, 1, 1);
            batch.setTransformMatrix(saved);
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
